package cdds.service.tc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;
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

public class TcServiceAuthorization implements ServerInterceptor {

    public static final String TC_ENDPOINT = "tc-endpoint-bin";

    public static final String HANDLED_TC_METHOD = "openTelecommandEndpoint"; 

    // used by the TC User to put meta data TC_ENDPOINT
    public static final Metadata.Key<byte[]> TC_ENDPOINT_KEY = Metadata.Key.of(TC_ENDPOINT, Metadata.BINARY_BYTE_MARSHALLER);

    // used by the TC Provider to read the intercepted meta data TC_ENDPOINT from the call context
    public static final Context.Key<byte[]> TC_ENDPOINT_CTX_KEY = Context.key(TC_ENDPOINT);

    private static final Logger LOG = LogManager.getLogger("cdds.tc.authorization.");

    private final Set<TcServiceEndpoint> authorizedTcEndpoints = new LinkedHashSet<>();

    /**
     * Intercept the TC service call(s), read the TC_ENDPOINT meta data and store it in the call context
     */
    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        if(isHandlingCall(call.getMethodDescriptor().getFullMethodName())) {
            byte[] endpointBytes = headers.get(TC_ENDPOINT_KEY);

            if(endpointBytes == null) {
                LOG.warn("Failed to read endpoint metadata " + TC_ENDPOINT_KEY);
                call.close(Status.PERMISSION_DENIED.withDescription("No TC_ENDPOINT meta data provided"),
                        new Metadata());
                return null;
            }

            TcServiceEndpoint tcEndpoint = TcServiceEndpoint.newBuilder().build(); // empty default
            try {
                tcEndpoint = ProtoJsonUtil.fromJson(endpointBytes, TcServiceEndpoint.newBuilder());
                
                // At this point the endpoint is known and can be used for authorization. 
                if (isEndpointAuthorized(GrpcUtil.getSan(call), tcEndpoint) == true) {
                    LOG.info("Authorize TC service meta data for \n'" + TC_ENDPOINT + "':\n" + new String(endpointBytes)
                        + "\nSAN: " + GrpcUtil.getSan(call));
                } else {
                    LOG.warn("TC service meta data, invalid TC_ENDPOINT provided:\n" + tcEndpoint 
                        + "\nauthorized endpoints:\n" + authorizedTcEndpoints);
                    
                    call.close(Status.PERMISSION_DENIED.withDescription("Invalid TC_ENDPOINT meta data provided"),
                            new Metadata());
                }

            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }

            Context ctx = Context.current().withValue(TC_ENDPOINT_CTX_KEY, endpointBytes);

            return Contexts.interceptCall(ctx, call, headers, next);        
        }
        
        return next.startCall(call, headers);
    }

    /**
     * Check if the given TC endpoint is authorized for one of the Subject Alternative Names
     * @param sanList       The list of Subject Alternative Names 
     * @param tcEndpoint    The TC requested for authorization
     * @return              true if the endpoint is authorized
     */
    private boolean isEndpointAuthorized(List<String> sanList, TcServiceEndpoint tcEndpoint) {
        if(authorizedTcEndpoints.contains(tcEndpoint) == false) {
            return false;
        }

        // for testing only: without security, there is no SAN
        if(sanList.size() == 0) {
            return true;
        }
        
        for(String san : sanList) {
            if(san.equals(tcEndpoint.getServiceUser())) {
                return true;
            }                
        }

        return false;
    }

    /**
     * Check if this authorization is handling the method
     * @param call  The call object
     * @return      true if this TC service authorization is applicable to the call
     */
    private boolean isHandlingCall(String methodName) {
        if(methodName.contains(HANDLED_TC_METHOD)) {
            return true;
        }

        return false;
    }

    /**
     * Add an allowed TC endpoint
     * @param tcEndpoint
     */
    public void addAuthorizedTcEndpoint(TcServiceEndpoint tcEndpoint) {
        authorizedTcEndpoints.add(tcEndpoint);
    }

    /**
     * Removes an allowed TC endpoint
     * @param tcEndpoint
     */
    public void removeAuthorizedTcEndpoint(TcServiceEndpoint tcEndpoint) {
       authorizedTcEndpoints.remove(tcEndpoint);
    }
}
