package cdds.service.tm;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.tm.CddsTmService.TmServiceEndpoint;
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

public class TmServiceAuthorization implements ServerInterceptor {

    public static final String TM_ENDPOINT = "tm-endpoint-bin";

    public static final String HANDLED_TM_METHOD = "openTelemetryEndpoint"; 

    // used by the TC User to put meta data TM_ENDPOINT
    public static final Metadata.Key<byte[]> TM_ENDPOINT_KEY = Metadata.Key.of(TM_ENDPOINT, Metadata.BINARY_BYTE_MARSHALLER);

    // used by the TC Provider to read the intercepted meta data TM_ENDPOINT from the call context
    public static final Context.Key<byte[]> TM_ENDPOINT_CTX_KEY = Context.key(TM_ENDPOINT);

    private static final Logger LOG = LogManager.getLogger("cdds.tm.authorization.");

    private final Set<TmServiceEndpoint> authorizedTmEndpoints = new LinkedHashSet<>();

    /**
     * Intercept the TC service call(s), read the TM_ENDPOINT meta data and store it in the call context
     */
    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        if(isHandlingCall(call.getMethodDescriptor().getFullMethodName())) {
            byte[] endpointBytes = headers.get(TM_ENDPOINT_KEY);

            if(endpointBytes == null) {
                LOG.warn("Failed to read endpoint metadata " + TM_ENDPOINT_KEY);
                call.close(Status.PERMISSION_DENIED.withDescription("No TM_ENDPOINT meta data provided"),
                        new Metadata());
                return null;
            }

            TmServiceEndpoint tmEndpoint = TmServiceEndpoint.newBuilder().build(); // empty default
            try {
                tmEndpoint = ProtoJsonUtil.fromJson(endpointBytes, TmServiceEndpoint.newBuilder());

                // At this point the endpoint is known and can be used for authorization. 
                if (authorizedTmEndpoints.contains(tmEndpoint) == true && 
                    (GrpcUtil.getSan(call).size() == 0 || GrpcUtil.getSan(call).contains(tmEndpoint.getServiceUser()))) {    
                LOG.info("Authorize TM service meta data for \n'" + TM_ENDPOINT + "':\n" + new String(endpointBytes)
                        + "\nSAN: " + GrpcUtil.getSan(call));
                } else {
                    LOG.warn("TM service meta data, invalid TM_ENDPOINT provided:\n" + tmEndpoint 
                        + "\nauthorized endpoints:\n" + authorizedTmEndpoints);
                    
                    call.close(Status.PERMISSION_DENIED.withDescription("Invalid TM_ENDPOINT meta data provided"),
                            new Metadata());
                }

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

            Context ctx = Context.current().withValue(TM_ENDPOINT_CTX_KEY, endpointBytes);

            return Contexts.interceptCall(ctx, call, headers, next);        
        }
        
        return next.startCall(call, headers);
    }

    /**
     * Check if this authorization is handling the method
     * @param call  The call object
     * @return      true if this TC service authorization is applicable to the call
     */
    private boolean isHandlingCall(String methodName) {
        if(methodName.contains(HANDLED_TM_METHOD)) {
            return true;
        }

        return false;
    }

    /**
     * Add an allowed TM endpoint
     * @param tmEndpoint
     */
    public void addAuthorizedTmEndpoint(TmServiceEndpoint tmEndpoint) {
        authorizedTmEndpoints.add(tmEndpoint);
    }

    /**
     * Removes an allowed TM endpoint
     * @param tmEndpoint
     */
    public void removeAuthorizedTcEndpoint(TmServiceEndpoint tmEndpoint) {
       authorizedTmEndpoints.remove(tmEndpoint);
    }
}
