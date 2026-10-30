package cdds.service.control;

import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpoint;
import cdds.service.common.GrpcUtil;
import cdds.service.common.ProtoJsonUtil;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class ControlServiceAuthorization implements ServerInterceptor {

    public static final Context.Key<X509Certificate> CLIENT_CERT =
            Context.key("client-cert");

    public static final String CONTROL_ENDPOINT = "control-endpoint-bin";

    public static final String HANDLED_CONTROL_METHOD = "openControlEndpoint";

    // used by the Control User to put meta data CONTROL_ENDPOINT
    public static final Metadata.Key<byte[]> CONTROL_ENDPOINT_KEY =
            Metadata.Key.of(CONTROL_ENDPOINT, Metadata.BINARY_BYTE_MARSHALLER);

    // used by the Control Provider to read the intercepted meta data CONTROL_ENDPOINT from the call context
    public static final Context.Key<byte[]> CONTROL_ENDPOINT_CTX_KEY = Context.key(CONTROL_ENDPOINT);

    private static final Logger LOG = LogManager.getLogger("cdds.control.authorization.");

    private final Set<ControlServiceEndpoint> authorizedControlEndpoints = new LinkedHashSet<>();

    /**
     * Intercept the control service call(s), read the CONTROL_ENDPOINT meta data
     * and store it in the call context
     */
    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        Context ctx = Context.current();
        // set the X509 certificate into the call to be available (SAN)
        try {
            X509Certificate cert = (X509Certificate)GrpcUtil.getClientCert(call)[0];
            LOG.info("adding cert in context: " + cert.getSubjectX500Principal().getName());
            ctx = ctx.withValue(CLIENT_CERT, cert);
        } catch(Exception ex) {
            LOG.warn("SSL Peer unverified for call " + call.getMethodDescriptor().getBareMethodName() + ex);
        }

        if(isHandlingCall(call.getMethodDescriptor().getFullMethodName())) {
            byte[] endpointBytes = headers.get(CONTROL_ENDPOINT_KEY);

            if(endpointBytes == null) {
                LOG.warn("Failed to read endpoint metadata " + CONTROL_ENDPOINT_KEY);
                call.close(Status.PERMISSION_DENIED.withDescription("No CONTROL_ENDPOINT meta data provided"),
                        new Metadata());
                return null;
            }

            ControlServiceEndpoint controlEndpoint = ControlServiceEndpoint.newBuilder().build(); // empty default
            try {
                controlEndpoint = ProtoJsonUtil.fromJson(endpointBytes, ControlServiceEndpoint.newBuilder());

                // At this point the endpoint is known and can be used for authorization.
                if (isEndpointAuthorized(GrpcUtil.getSan(call), controlEndpoint)) {
                    LOG.info("Authorize control service meta data for \n'" + CONTROL_ENDPOINT + "':\n"
                        + new String(endpointBytes) + "\nSAN: " + GrpcUtil.getSan(call));
                } else {
                    LOG.warn("Control service meta data, invalid CONTROL_ENDPOINT provided:\n" + controlEndpoint
                        + "\nauthorized endpoints:\n" + authorizedControlEndpoints);

                    call.close(Status.PERMISSION_DENIED.withDescription("Invalid CONTROL_ENDPOINT meta data provided"),
                            new Metadata());
                }

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

            ctx = ctx.withValue(CONTROL_ENDPOINT_CTX_KEY, endpointBytes);

            return Contexts.interceptCall(ctx, call, headers, next);
        }

        return Contexts.interceptCall(ctx, call, headers, next);
    }

    /**
     * Check if the given control endpoint is authorized for one of the Subject Alternative Names
     * @param sanList           The list of Subject Alternative Names
     * @param controlEndpoint   The control endpoint requested for authorization
     * @return                  true if the endpoint is authorized
     */
    private boolean isEndpointAuthorized(List<String> sanList, ControlServiceEndpoint controlEndpoint) {
        if(authorizedControlEndpoints.contains(controlEndpoint) == false) {
            return false;
        }

        // for testing only: without security, there is no SAN
        if(sanList.size() == 0) {
            return true;
        }

        for(String san : sanList) {
            if(san.equals(controlEndpoint.getServiceUser())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if this authorization is handling the method
     * @param call  The call object
     * @return      true if this control service authorization is applicable to the call
     */
    private boolean isHandlingCall(String methodName) {
        if(methodName.contains(HANDLED_CONTROL_METHOD)) {
            return true;
        }

        return false;
    }

    /**
     * Add an allowed control endpoint
     * @param controlEndpoint
     */
    public void addAuthorizedControlEndpoint(ControlServiceEndpoint controlEndpoint) {
        authorizedControlEndpoints.add(controlEndpoint);
    }

    /**
     * Removes an allowed control endpoint
     * @param controlEndpoint
     */
    public void removeAuthorizedControlEndpoint(ControlServiceEndpoint controlEndpoint) {
       authorizedControlEndpoints.remove(controlEndpoint);
    }
}
