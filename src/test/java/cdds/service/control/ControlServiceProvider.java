package cdds.service.control;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.Control.ControlDirective;
import ccsds.cdds.v1.Control.DirectiveReport;
import ccsds.cdds.v1.Types.NoArg;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpoint;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpointList;
import ccsds.cdds.v1.control.ControlServiceProviderGrpc.ControlServiceProviderImplBase;
import cdds.service.common.GrpcUtil;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProtoJsonUtil;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

/**
 * Simple control test server. Delegates the handling of control directives
 * to the control production associated to the opened control endpoint.
 */
public class ControlServiceProvider extends ControlServiceProviderImplBase implements InterceptedService {

    private static final Logger LOG = LogManager.getLogger("cdds.control.provider");

    private final ControlServiceAuthorization controlAuthorization = new ControlServiceAuthorization();

    private final List<ControlServiceEndpoint> controlEndpoints =
            Collections.synchronizedList(new ArrayList<>());

    private final Map<ControlServiceEndpoint, ControlProductionInterface> controlProductions =
            new ConcurrentHashMap<>();

    @Override
    public void getEndpoints(NoArg request, StreamObserver<ControlServiceEndpointList> responseObserver) {
        LOG.info("get endpoints called. Total endpoints: " + controlEndpoints.size());

        ControlServiceEndpointList.Builder endpoints = ControlServiceEndpointList.newBuilder();

        try {
            X509Certificate cert = ControlServiceAuthorization.CLIENT_CERT.get();
            for(ControlServiceEndpoint endpoint : controlEndpoints) {
                if(GrpcUtil.endpointAuthorized(cert, endpoint.getServiceUser())) {
                    LOG.info("Control endpoint authorized for " + cert.getSubjectX500Principal().getName());
                    endpoints.addEndpoints(endpoint);
                } else {
                    LOG.warn("Control endpoint not authorized for cert: " + cert);
                }
            }

            responseObserver.onNext(endpoints.build());
            responseObserver.onCompleted();
        } catch(Exception ex) {
            LOG.warn("Control endpoint authorization: ", ex);
            responseObserver.onError(ex);
        }
    }

    @Override
    public StreamObserver<ControlDirective> openControlEndpoint(StreamObserver<DirectiveReport> controlUserStream) {
        try {
            // get the control-endpoint-bin meta data (JSON)
            byte[] endpointBytes = ControlServiceAuthorization.CONTROL_ENDPOINT_CTX_KEY.get();

            // decode the endpoint from JSON
            ControlServiceEndpoint controlEndpoint =
                    ProtoJsonUtil.fromJson(endpointBytes, ControlServiceEndpoint.newBuilder());

            ControlProductionInterface controlProduction = controlProductions.get(controlEndpoint);

            if(controlProduction != null) {

                try {
                    X509Certificate cert = ControlServiceAuthorization.CLIENT_CERT.get();
                    LOG.info("Open control stream for endpoint, SAN: " + cert.getSubjectX500Principal().getName()
                                + "\n" + controlEndpoint);
                } catch(Exception ex) {
                    // OK for unauthenticated tests
                    LOG.info("Open control stream for endpoint\n" + controlEndpoint);
                }

                return controlProduction.startControlEndpointService(controlEndpoint, controlUserStream);
            } else {
                LOG.warn("Failed to open control stream, non-existing endpoint:\n" + controlEndpoint);
                controlUserStream.onError(new Exception("No control production associated to the control endpoint:\n"
                        + controlEndpoint));
            }

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }

        return new StreamObserver<ControlDirective>() {

            @Override
            public void onNext(ControlDirective value) {
                // no-op, the endpoint could not be opened
            }

            @Override
            public void onError(Throwable t) {
                // no-op, the endpoint could not be opened
            }

            @Override
            public void onCompleted() {
                // no-op, the endpoint could not be opened
            }
        };
    }

    /**
     * Add a control production for a given service endpoint.
     * Any implementation of {@link ControlProductionInterface} can be
     * added to provide different productions for different endpoints.
     * @param controlEndpoint   The endpoint for which control production is added
     * @param controlProduction The control production
     */
    public void addControlProduction(ControlServiceEndpoint controlEndpoint,
            ControlProductionInterface controlProduction) {
        controlProductions.put(controlEndpoint, controlProduction);
    }

    /**
     * Removes the control production available to this control provider.
     * Callers have to ensure the production actually stopped.
     * @param controlEndpoint   The control endpoint identifying the associated control production
     */
    public void removeControlProduction(ControlServiceEndpoint controlEndpoint) {
        controlProductions.remove(controlEndpoint);
    }

    @Override
    public BindableService getBindableService() {
        return this;
    }

    @Override
    public ServerInterceptor getServiceInterceptor() {
        return controlAuthorization;
    }

    /**
     * Add an allowed control endpoint
     * @param controlEndpoint
     */
    public void addAuthorizedControlEndpoint(ControlServiceEndpoint controlEndpoint) {
        controlAuthorization.addAuthorizedControlEndpoint(controlEndpoint);
        controlEndpoints.add(controlEndpoint);
    }

    /**
     * Removes an allowed control endpoint
     * @param controlEndpoint
     */
    public void removeAuthorizedControlEndpoint(ControlServiceEndpoint controlEndpoint) {
       controlAuthorization.removeAuthorizedControlEndpoint(controlEndpoint);
       controlEndpoints.remove(controlEndpoint);
    }
}
