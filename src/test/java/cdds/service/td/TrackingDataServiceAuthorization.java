package cdds.service.td;

import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpoint;
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

/**
 * Intercepts the tracking data service calls, reads the td-endpoint-bin meta data
 * (JSON encoded TrackingDataServiceEndpoint) and stores it in the call context,
 * and authorizes the requested endpoint for the authenticated service user.
 */
public class TrackingDataServiceAuthorization implements ServerInterceptor {

    public static final Context.Key<X509Certificate> CLIENT_CERT =
            Context.key("client-cert");

    public static final String TD_ENDPOINT = "td-endpoint-bin";

    public static final String HANDLED_TD_METHOD = "openTrackingDataServiceEndpoint";

    // used by the tracking data user to put the meta data TD_ENDPOINT
    public static final Metadata.Key<byte[]> TD_ENDPOINT_KEY =
            Metadata.Key.of(TD_ENDPOINT, Metadata.BINARY_BYTE_MARSHALLER);

    // used by the tracking data provider to read the intercepted meta data TD_ENDPOINT from the call context
    public static final Context.Key<byte[]> TD_ENDPOINT_CTX_KEY = Context.key(TD_ENDPOINT);

    private static final Logger LOG = LogManager.getLogger("cdds.td.authorization.");

    private final Set<TrackingDataServiceEndpoint> authorizedTdEndpoints = new LinkedHashSet<>();

    /**
     * Intercept the tracking data service call(s), read the TD_ENDPOINT meta data
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
            byte[] endpointBytes = headers.get(TD_ENDPOINT_KEY);

            if(endpointBytes == null) {
                LOG.warn("Failed to read endpoint metadata " + TD_ENDPOINT_KEY);
                call.close(Status.PERMISSION_DENIED.withDescription("No TD_ENDPOINT meta data provided"),
                        new Metadata());
                return null;
            }

            TrackingDataServiceEndpoint tdEndpoint = TrackingDataServiceEndpoint.newBuilder().build(); // empty default
            try {
                tdEndpoint = ProtoJsonUtil.fromJson(endpointBytes, TrackingDataServiceEndpoint.newBuilder());

                // At this point the endpoint is known and can be used for authorization.
                if (isEndpointAuthorized(GrpcUtil.getSan(call), tdEndpoint)) {
                    LOG.info("Authorize tracking data service meta data for \n'" + TD_ENDPOINT + "':\n"
                        + new String(endpointBytes) + "\nSAN: " + GrpcUtil.getSan(call));
                } else {
                    LOG.warn("Tracking data service meta data, invalid TD_ENDPOINT provided:\n" + tdEndpoint
                        + "\nauthorized endpoints:\n" + authorizedTdEndpoints);

                    call.close(Status.PERMISSION_DENIED.withDescription("Invalid TD_ENDPOINT meta data provided"),
                            new Metadata());
                }

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

            ctx = ctx.withValue(TD_ENDPOINT_CTX_KEY, endpointBytes);

            return Contexts.interceptCall(ctx, call, headers, next);
        }

        return Contexts.interceptCall(ctx, call, headers, next);
    }

    /**
     * Check if the given tracking data endpoint is authorized for one of the Subject Alternative Names
     * @param sanList           The list of Subject Alternative Names
     * @param tdEndpoint        The tracking data endpoint requested for authorization
     * @return                  true if the endpoint is authorized
     */
    private boolean isEndpointAuthorized(List<String> sanList, TrackingDataServiceEndpoint tdEndpoint) {
        if(authorizedTdEndpoints.contains(tdEndpoint) == false) {
            return false;
        }

        // for testing only: without security, there is no SAN
        if(sanList.size() == 0) {
            return true;
        }

        for(String san : sanList) {
            if(san.equals(tdEndpoint.getServiceUser())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if this authorization is handling the method
     * @param methodName  The full method name of the call
     * @return            true if this tracking data service authorization is applicable to the call
     */
    private boolean isHandlingCall(String methodName) {
        if(methodName.contains(HANDLED_TD_METHOD)) {
            return true;
        }

        return false;
    }

    /**
     * Add an allowed tracking data endpoint
     * @param tdEndpoint  The endpoint to authorize
     */
    public void addAuthorizedTrackingDataEndpoint(TrackingDataServiceEndpoint tdEndpoint) {
        authorizedTdEndpoints.add(tdEndpoint);
    }

    /**
     * Removes an allowed tracking data endpoint
     * @param tdEndpoint  The endpoint to remove
     */
    public void removeAuthorizedTrackingDataEndpoint(TrackingDataServiceEndpoint tdEndpoint) {
       authorizedTdEndpoints.remove(tdEndpoint);
    }
}
