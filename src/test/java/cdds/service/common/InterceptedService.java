package cdds.service.common;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;

/**
 * Provides BindableService and corresponding ServerInterceptor
 */
public interface InterceptedService {

    /**
     * Returns the Bindable Service associated to the Service Interceptor
     * @return the service
     */
    public BindableService getBindableService();

    /**
     * Provides the Service Interceptor corresponding to the Bindable Service
     * @return
     */
    public ServerInterceptor getServiceInterceptor();
}
