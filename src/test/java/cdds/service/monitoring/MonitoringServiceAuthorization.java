package cdds.service.monitoring;

import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpoint;
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

public class MonitoringServiceAuthorization implements ServerInterceptor {

    public static final Context.Key<X509Certificate> CLIENT_CERT =
            Context.key("client-cert");

    public static final String MONITORING_ENDPOINT = "monitoring-endpoint-bin";

    public static final String HANDLED_MONITORING_METHOD = "openMonitoringEndpoint";

    // used by the Monitoring User to put meta data MONITORING_ENDPOINT
    public static final Metadata.Key<byte[]> MONITORING_ENDPOINT_KEY =
            Metadata.Key.of(MONITORING_ENDPOINT, Metadata.BINARY_BYTE_MARSHALLER);

    // used by the Monitoring Provider to read the intercepted meta data MONITORING_ENDPOINT from the call context
    public static final Context.Key<byte[]> MONITORING_ENDPOINT_CTX_KEY = Context.key(MONITORING_ENDPOINT);

    private static final Logger LOG = LogManager.getLogger("cdds.monitoring.authorization.");

    private final Set<MonitoringServiceEndpoint> authorizedMonitoringEndpoints = new LinkedHashSet<>();

    /**
     * Intercept the monitoring service call(s), read the MONITORING_ENDPOINT meta data
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
            byte[] endpointBytes = headers.get(MONITORING_ENDPOINT_KEY);

            if(endpointBytes == null) {
                LOG.warn("Failed to read endpoint metadata " + MONITORING_ENDPOINT_KEY);
                call.close(Status.PERMISSION_DENIED.withDescription("No MONITORING_ENDPOINT meta data provided"),
                        new Metadata());
                return null;
            }

            MonitoringServiceEndpoint monitoringEndpoint = MonitoringServiceEndpoint.newBuilder().build(); // empty default
            try {
                monitoringEndpoint = ProtoJsonUtil.fromJson(endpointBytes, MonitoringServiceEndpoint.newBuilder());

                // At this point the endpoint is known and can be used for authorization.
                if (isEndpointAuthorized(GrpcUtil.getSan(call), monitoringEndpoint)) {
                    LOG.info("Authorize monitoring service meta data for \n'" + MONITORING_ENDPOINT + "':\n"
                        + new String(endpointBytes) + "\nSAN: " + GrpcUtil.getSan(call));
                } else {
                    LOG.warn("Monitoring service meta data, invalid MONITORING_ENDPOINT provided:\n" + monitoringEndpoint
                        + "\nauthorized endpoints:\n" + authorizedMonitoringEndpoints);

                    call.close(Status.PERMISSION_DENIED.withDescription("Invalid MONITORING_ENDPOINT meta data provided"),
                            new Metadata());
                }

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

            ctx = ctx.withValue(MONITORING_ENDPOINT_CTX_KEY, endpointBytes);

            return Contexts.interceptCall(ctx, call, headers, next);
        }

        return Contexts.interceptCall(ctx, call, headers, next);
    }

    /**
     * Check if the given monitoring endpoint is authorized for one of the Subject Alternative Names
     * @param sanList               The list of Subject Alternative Names
     * @param monitoringEndpoint    The monitoring endpoint requested for authorization
     * @return                      true if the endpoint is authorized
     */
    private boolean isEndpointAuthorized(List<String> sanList, MonitoringServiceEndpoint monitoringEndpoint) {
        if(authorizedMonitoringEndpoints.contains(monitoringEndpoint) == false) {
            return false;
        }

        // for testing only: without security, there is no SAN
        if(sanList.size() == 0) {
            return true;
        }

        for(String san : sanList) {
            if(san.equals(monitoringEndpoint.getServiceUser())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if this authorization is handling the method
     * @param call  The call object
     * @return      true if this monitoring service authorization is applicable to the call
     */
    private boolean isHandlingCall(String methodName) {
        if(methodName.contains(HANDLED_MONITORING_METHOD)) {
            return true;
        }

        return false;
    }

    /**
     * Add an allowed monitoring endpoint
     * @param monitoringEndpoint
     */
    public void addAuthorizedMonitoringEndpoint(MonitoringServiceEndpoint monitoringEndpoint) {
        authorizedMonitoringEndpoints.add(monitoringEndpoint);
    }

    /**
     * Removes an allowed monitoring endpoint
     * @param monitoringEndpoint
     */
    public void removeAuthorizedMonitoringEndpoint(MonitoringServiceEndpoint monitoringEndpoint) {
       authorizedMonitoringEndpoints.remove(monitoringEndpoint);
    }
}
