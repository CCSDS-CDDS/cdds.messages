package cdds.service.td;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.Tracking.TrackingDataMessage;
import ccsds.cdds.v1.Types.NoArg;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpoint;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpointList;
import ccsds.cdds.v1.td.TrackingDataServiceProviderGrpc.TrackingDataServiceProviderImplBase;
import cdds.service.common.GrpcUtil;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProtoJsonUtil;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

/**
 * Simple tracking data test server
 */
public class TrackingDataServiceProvider extends TrackingDataServiceProviderImplBase implements InterceptedService {

    private static final Logger LOG = LogManager.getLogger("cdds.td.provider");

    private final TrackingDataServiceAuthorization tdAuthorization = new TrackingDataServiceAuthorization();

    private final List<TrackingDataServiceEndpoint> tdEndpoints =
            Collections.synchronizedList(new ArrayList<>());

    private final Map<TrackingDataServiceEndpoint, TrackingDataProductionInterface> tdProductions =
            new ConcurrentHashMap<>();

    @Override
    public void getEndpoints(NoArg request, StreamObserver<TrackingDataServiceEndpointList> responseObserver) {
        LOG.info("get endpoints called. Total endpoints: " + tdEndpoints.size());

        TrackingDataServiceEndpointList.Builder endpoints = TrackingDataServiceEndpointList.newBuilder();

        try {
            X509Certificate cert = TrackingDataServiceAuthorization.CLIENT_CERT.get();
            for(TrackingDataServiceEndpoint endpoint : tdEndpoints) {
                if(GrpcUtil.endpointAuthorized(cert, endpoint.getServiceUser())) {
                    LOG.info("Tracking data endpoint authorized for " + cert.getSubjectX500Principal().getName());
                    endpoints.addEndpoints(endpoint);
                } else {
                    LOG.warn("Tracking data endpoint not authorized for cert: " + cert);
                }
            }

            responseObserver.onNext(endpoints.build());
            responseObserver.onCompleted();
        } catch(Exception ex) {
            LOG.warn("Tracking data endpoint authorization: ", ex);
            responseObserver.onError(ex);
        }
    }

    @Override
    public void openTrackingDataServiceEndpoint(NoArg noArg,
            StreamObserver<TrackingDataMessage> tdUserStream) {
        try {
            // get the td-endpoint-bin meta data (JSON)
            byte[] endpointBytes = TrackingDataServiceAuthorization.TD_ENDPOINT_CTX_KEY.get();

            // decode the endpoint from JSON
            TrackingDataServiceEndpoint tdEndpoint =
                    ProtoJsonUtil.fromJson(endpointBytes, TrackingDataServiceEndpoint.newBuilder());

            TrackingDataProductionInterface tdProduction = tdProductions.get(tdEndpoint);

            if(tdProduction != null) {

                try {
                    X509Certificate cert = TrackingDataServiceAuthorization.CLIENT_CERT.get();
                    LOG.info("Open tracking data stream for endpoint, SAN: " + cert.getSubjectX500Principal().getName()
                                + "\n" + tdEndpoint);
                } catch(Exception ex) {
                    // OK for unauthenticated tests
                    LOG.info("Open tracking data stream for endpoint\n" + tdEndpoint);
                }

                tdProduction.startTrackingDataEndpointService(tdEndpoint, tdUserStream);
            } else {
                LOG.warn("Failed to open tracking data stream, non-existing endpoint:\n" + tdEndpoint);
                tdUserStream.onError(new Exception("No tracking data production associated to the tracking data endpoint:\n"
                        + tdEndpoint));
            }

        } catch (InvalidProtocolBufferException e) {
            tdUserStream.onError(e);
        }
    }

    /**
     * Add a tracking data production for a given service endpoint.
     * Any implementation of {@link TrackingDataProductionInterface} can be
     * added to provide different productions for different endpoints.
     * @param tdEndpoint      The endpoint for which tracking data production is added
     * @param tdProduction    The tracking data production
     */
    public void addTrackingDataProduction(TrackingDataServiceEndpoint tdEndpoint,
            TrackingDataProductionInterface tdProduction) {
        tdProductions.put(tdEndpoint, tdProduction);
    }

    /**
     * Removes the tracking data production available to this tracking data provider.
     * Callers have to ensure the production actually stopped.
     * @param tdEndpoint   The tracking data endpoint identifying the associated tracking data production
     */
    public void removeTrackingDataProduction(TrackingDataServiceEndpoint tdEndpoint) {
        tdProductions.remove(tdEndpoint);
    }

    @Override
    public BindableService getBindableService() {
        return this;
    }

    @Override
    public ServerInterceptor getServiceInterceptor() {
        return tdAuthorization;
    }

    /**
     * Add an allowed tracking data endpoint
     * @param tdEndpoint  The endpoint to add
     */
    public void addAuthorizedTrackingDataEndpoint(TrackingDataServiceEndpoint tdEndpoint) {
        tdAuthorization.addAuthorizedTrackingDataEndpoint(tdEndpoint);
        tdEndpoints.add(tdEndpoint);
    }

    /**
     * Removes an allowed tracking data endpoint
     * @param tdEndpoint  The endpoint to remove
     */
    public void removeAuthorizedTrackingDataEndpoint(TrackingDataServiceEndpoint tdEndpoint) {
       tdAuthorization.removeAuthorizedTrackingDataEndpoint(tdEndpoint);
       tdEndpoints.remove(tdEndpoint);
    }
}
