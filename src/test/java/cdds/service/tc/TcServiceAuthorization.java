package cdds.service.tc;

import java.util.logging.Logger;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class TcServiceAuthorization implements ServerInterceptor {

    public static final String SPACECRAFT = "SPACECRAFT";

    // used by the TC User to put meta data SPACECRAFT
    public static final Metadata.Key<String> SPACECRAFT_KEY = Metadata.Key.of(SPACECRAFT, Metadata.ASCII_STRING_MARSHALLER);

    // used by the TC Provider to read the intercepted meta data SPACECRAFT from the call context
    public static final Context.Key<String> SPACECRAFT_CTX_KEY = Context.key(SPACECRAFT);

    private static final Logger LOG = Logger.getLogger("CDDS Provider");

    /**
     * Intercept the TC service call(s), read the SPACECRAFT meta data and store it in the call context
     */
    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String spacecraft = headers.get(SPACECRAFT_KEY);

        if(spacecraft != null) {
            LOG.info("TC service meta data: " + SPACECRAFT + ": " + spacecraft);
        } else {
            LOG.warning("TC service meta data, no SPACECRAFT provided");
            call.close(Status.PERMISSION_DENIED.withDescription("no SPACECRAFT meta data provided"), new Metadata());
        }

        Context ctx = Context.current().withValue(SPACECRAFT_CTX_KEY, spacecraft);

        return Contexts.interceptCall(ctx, call, headers, next);        
    }
}
