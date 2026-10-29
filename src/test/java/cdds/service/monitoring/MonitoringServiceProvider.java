package cdds.service.monitoring;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.Monitoring.MonitoringMessage;
import ccsds.cdds.v1.Types.NoArg;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpoint;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpointList;
import ccsds.cdds.v1.monitoring.MonitoringServiceProviderGrpc.MonitoringServiceProviderImplBase;
import cdds.service.common.GrpcUtil;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProtoJsonUtil;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

/**
 * Simple monitoring test server
 */
public class MonitoringServiceProvider extends MonitoringServiceProviderImplBase implements InterceptedService {

    private static final Logger LOG = LogManager.getLogger("cdds.monitoring.provider");

    private final MonitoringServiceAuthorization monitoringAuthorization = new MonitoringServiceAuthorization();

    private final List<MonitoringServiceEndpoint> monitoringEndpoints =
            Collections.synchronizedList(new ArrayList<>());

    private final Map<MonitoringServiceEndpoint, MonitoringProductionInterface> monitoringProductions =
            new ConcurrentHashMap<>();

    @Override
    public void getEndpoints(NoArg request, StreamObserver<MonitoringServiceEndpointList> responseObserver) {
        LOG.info("get endpoints called. Total endpoints: " + monitoringEndpoints.size());

        MonitoringServiceEndpointList.Builder endpoints = MonitoringServiceEndpointList.newBuilder();

        try {
            X509Certificate cert = MonitoringServiceAuthorization.CLIENT_CERT.get();
            for(MonitoringServiceEndpoint endpoint : monitoringEndpoints) {
                if(GrpcUtil.endpointAuthorized(cert, endpoint.getServiceUser())) {
                    LOG.info("Monitoring endpoint authorized for " + cert.getSubjectX500Principal().getName());
                    endpoints.addEndpoints(endpoint);
                } else {
                    LOG.warn("Monitoring endpoint not authorized for cert: " + cert);
                }
            }

            responseObserver.onNext(endpoints.build());
            responseObserver.onCompleted();
        } catch(Exception ex) {
            LOG.warn("Monitoring endpoint authorization: ", ex);
            responseObserver.onError(ex);
        }
    }

    @Override
    public void openMonitoringEndpoint(NoArg noArg,
            io.grpc.stub.StreamObserver<MonitoringMessage> monitoringUserStream) {
        try {
            // get the monitoring-endpoint-bin meta data (JSON)
            byte[] endpointBytes = MonitoringServiceAuthorization.MONITORING_ENDPOINT_CTX_KEY.get();

            // decode the endpoint from JSON
            MonitoringServiceEndpoint monitoringEndpoint =
                    ProtoJsonUtil.fromJson(endpointBytes, MonitoringServiceEndpoint.newBuilder());

            MonitoringProductionInterface monitoringProduction = monitoringProductions.get(monitoringEndpoint);

            if(monitoringProduction != null) {

                try {
                    X509Certificate cert = MonitoringServiceAuthorization.CLIENT_CERT.get();
                    LOG.info("Open monitoring stream for endpoint, SAN: " + cert.getSubjectX500Principal().getName()
                                + "\n" + monitoringEndpoint);
                } catch(Exception ex) {
                    // OK for unauthenticated tests
                    LOG.info("Open monitoring stream for endpoint\n" + monitoringEndpoint);
                }

                monitoringProduction.startMonitoringEndpointService(monitoringEndpoint, monitoringUserStream);
            } else {
                LOG.warn("Failed to open monitoring stream, non-existing endpoint:\n" + monitoringEndpoint);
                monitoringUserStream.onError(new Exception("No monitoring production associated to the monitoring endpoint:\n"
                        + monitoringEndpoint));
            }

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a monitoring production for a given service endpoint.
     * Any implementation of {@link MonitoringProductionInterface} can be
     * added to provide different productions for different endpoints.
     * @param monitoringEndpoint   The endpoint for which monitoring production is added
     * @param monitoringProduction The monitoring production
     */
    public void addMonitoringProduction(MonitoringServiceEndpoint monitoringEndpoint,
            MonitoringProductionInterface monitoringProduction) {
        monitoringProductions.put(monitoringEndpoint, monitoringProduction);
    }

    /**
     * Removes the monitoring production available to this monitoring provider.
     * Callers have to ensure the production actually stopped.
     * @param monitoringEndpoint   The monitoring endpoint identifying the associated monitoring production
     */
    public void removeMonitoringProduction(MonitoringServiceEndpoint monitoringEndpoint) {
        monitoringProductions.remove(monitoringEndpoint);
    }

    @Override
    public BindableService getBindableService() {
        return this;
    }

    @Override
    public ServerInterceptor getServiceInterceptor() {
        return monitoringAuthorization;
    }

    /**
     * Add an allowed monitoring endpoint
     * @param monitoringEndpoint
     */
    public void addAuthorizedMonitoringEndpoint(MonitoringServiceEndpoint monitoringEndpoint) {
        monitoringAuthorization.addAuthorizedMonitoringEndpoint(monitoringEndpoint);
        monitoringEndpoints.add(monitoringEndpoint);
    }

    /**
     * Removes an allowed monitoring endpoint
     * @param monitoringEndpoint
     */
    public void removeAuthorizedMonitoringEndpoint(MonitoringServiceEndpoint monitoringEndpoint) {
       monitoringAuthorization.removeAuthorizedMonitoringEndpoint(monitoringEndpoint);
       monitoringEndpoints.remove(monitoringEndpoint);
    }
}
