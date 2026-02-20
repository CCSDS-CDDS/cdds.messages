package cdds.service.tc;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;
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

        byte[] endpointBytes = headers.get(TC_ENDPOINT_KEY);
        TcServiceEndpoint tcEndPoint = TcServiceEndpoint.newBuilder().build(); // empty default
        try {
            tcEndPoint = TcEndpointUtil.tcEndpointFromJson(endpointBytes);
            
            // At this point the endpoint is known and can be used for authorization. 
            if (authorizedTcEndpoints.contains(tcEndPoint) == true) {
                LOG.info("Authorize TC service meta data for \n'" + TC_ENDPOINT + "':\n" + new String(endpointBytes));
            } else {
                LOG.warn("TC service meta data, invalid TC_ENDPOINT provided: " + tcEndPoint 
                    + "\nauthorized endpoints: " + authorizedTcEndpoints);
                
                call.close(Status.PERMISSION_DENIED.withDescription("Invalid TC_ENDPOINT meta data provided"),
                        new Metadata());
            }

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }

        Context ctx = Context.current().withValue(TC_ENDPOINT_CTX_KEY, endpointBytes);

        return Contexts.interceptCall(ctx, call, headers, next);        
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
