package cdds.service.tc;

import java.util.logging.Logger;

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

    private static final Logger LOG = Logger.getLogger("CDDS Provider");

    /**
     * Intercept the TC service call(s), read the TC_ENDPOINT meta data and store it in the call context
     */
    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        byte[] endpointBytes = headers.get(TC_ENDPOINT_KEY);
        TcServiceEndpoint tcEndPoint = TcServiceEndpoint.newBuilder().build(); // empty default
        try {
            tcEndPoint = TcServiceEndpoint.parseFrom(endpointBytes);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }

        if(tcEndPoint != null) {
            LOG.info("TC service meta data: " + TC_ENDPOINT + ":\n" + tcEndPoint);
        } else {
            LOG.warning("TC service meta data, no TC_ENDPOINT provided");
            call.close(Status.PERMISSION_DENIED.withDescription("no TC_ENDPOINT meta data provided"), new Metadata());
        }

        Context ctx = Context.current().withValue(TC_ENDPOINT_CTX_KEY, tcEndPoint.toByteArray());

        return Contexts.interceptCall(ctx, call, headers, next);        
    }
}
