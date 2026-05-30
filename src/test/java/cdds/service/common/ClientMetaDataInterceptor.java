package cdds.service.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Intercept client calls and puts in the header the meta data with the given endpoint key
 */
public class ClientMetaDataInterceptor implements ClientInterceptor {

    private final Metadata.Key<byte[]> ENDPOINT_KEY;

    private static final Logger LOG = LogManager.getLogger("cdds.user.metadata");    

    private byte[] metaData;

    /**
     * Constructs an client interceptor putting for the given key.
     * @param endpointKey
     */
    public ClientMetaDataInterceptor(Metadata.Key<byte[]>  endpointKey) {
        this.ENDPOINT_KEY = endpointKey;
    }

    /**
     * The meta data to put into the the headers of the intercepted call
     * @param metaData
     */
    public synchronized void setMetaData(byte[] metaData) {
        this.metaData = metaData;
    }

    @Override
    public synchronized <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions, Channel next) {

        ClientCall<ReqT, RespT> delegate = next.newCall(method, callOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {

            @Override
            public void start(
                    Listener<RespT> responseListener,
                    Metadata headers) {

                if(metaData != null) {
                    LOG.info("attach meta data to client call " + method.getFullMethodName());
                    headers.put(ENDPOINT_KEY, metaData);
                } else {
                    LOG.info("do not attach meta data to client call " + method.getFullMethodName());
                }

                super.start(responseListener, headers);
            }
        };
    }

}
