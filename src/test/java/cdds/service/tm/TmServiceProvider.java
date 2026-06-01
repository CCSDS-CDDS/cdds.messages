package cdds.service.tm;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.Types.NoArg;
import ccsds.cdds.tm.CddsTmService.TmServiceEndpoint;
import ccsds.cdds.tm.CddsTmService.TmServiceEndpointList;
import ccsds.cdds.tm.TmServiceProviderGrpc.TmServiceProviderImplBase;
import cdds.service.common.GrpcUtil;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProtoJsonUtil;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

/**
 * Simple TM test server
 */
public class TmServiceProvider extends TmServiceProviderImplBase implements InterceptedService {

    private static final Logger LOG = LogManager.getLogger("cdds.tm.provider");

    private final TmServiceAuthorization tmAuthorization = new TmServiceAuthorization();

    private final List<TmServiceEndpoint> tmEndpoints = Collections.synchronizedList(new ArrayList<>());

    private final Map<TmServiceEndpoint, TmProduction> tmProductions = new ConcurrentHashMap<>();

    @Override
    public void getEndpoints(NoArg request, StreamObserver<TmServiceEndpointList> responseObserver)  {
        LOG.info("get endpoints called. Total endpoints: " + tmEndpoints.size());

        ccsds.cdds.tm.CddsTmService.TmServiceEndpointList.Builder endpoints = TmServiceEndpointList.newBuilder();

        try {
            X509Certificate cert = TmServiceAuthorization.CLIENT_CERT.get();
            for(TmServiceEndpoint endpoint : tmEndpoints) {
                if(GrpcUtil.endpointAuthorized(cert, endpoint.getServiceUser())) {
                    LOG.info("TM endpoint authorized for " + cert.getSubjectX500Principal().getName());
                    endpoints.addEndpoints(endpoint);
                } else {
                    LOG.warn("TM endpoint not authorized for cert: " + cert);
                }
            }

            responseObserver.onNext(endpoints.build());    
            responseObserver.onCompleted();
        } catch(Exception ex) {
            LOG.warn("TM endpoint authorization: ", ex);
            responseObserver.onError(ex);
        }
    }

    @Override
    public void openTelemetryEndpoint(ccsds.cdds.Types.NoArg noArg,
            io.grpc.stub.StreamObserver<ccsds.cdds.Telemetry.TelemetryMessage> tmUserStream) {
        try {
            // get the tm-endpoint-bin meta data (JSON)
            byte[] endpointBytes = TmServiceAuthorization.TM_ENDPOINT_CTX_KEY.get(); 
                                                                                     
            // decode the endpoint from JSON
            TmServiceEndpoint tmEndpoint = ProtoJsonUtil.fromJson(endpointBytes, TmServiceEndpoint.newBuilder()); 
                                                                                             
            TmProduction tmProduction = tmProductions.get(tmEndpoint);
            
            if(tmProduction != null) {

                try {
                    X509Certificate cert = TmServiceAuthorization.CLIENT_CERT.get();
                    LOG.info("Open TM stream for endpoint, SAN: " + cert.getSubjectX500Principal().getName()
                                + "\n" + tmEndpoint);
                } catch(Exception ex) {
                    // OK for unauthenticated tests
                    LOG.info("Open TM stream for endpoint\n" + tmEndpoint);
                }

                tmProduction.startTmEndpointService(tmEndpoint, tmUserStream);
            } else {
                LOG.warn("Failed to open TM stream, non-existing endpoint:\n" + tmEndpoint);
                tmUserStream.onError(new Exception("No TM production associated to the TM endpoint:\n" + tmEndpoint));
            }
            
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a TM production for a given service endpoint
     * @param tmEndpoint    The endpoint for which TM production is added
     * @param tmProduction  The TM production
     */
    public void addTmProduction(TmServiceEndpoint tmEndpoint, TmProduction tmProduction) {
        tmProductions.put(tmEndpoint, tmProduction);
    }

    /**
     * Removes the TM production available to this TM provider.
     * Callers have to ensure the production actually stopped.
     * @param tmEndpoint    The TM endpoint identifying the associated TM production
     */
    public void removeTmProduction(TmServiceEndpoint tmEndpoint) {
       tmProductions.remove(tmEndpoint); 
    }

    @Override
    public BindableService getBindableService() {
        return this;
    }

    @Override
    public ServerInterceptor getServiceInterceptor() {
        return tmAuthorization;
    }

    /**
     * Add an allowed TM endpoint
     * @param tmEndpoint
     */
    public void addAuthorizedTmEndpoint(TmServiceEndpoint tmEndpoint) {
        tmAuthorization.addAuthorizedTmEndpoint(tmEndpoint);
        tmEndpoints.add(tmEndpoint);
    }

    /**
     * Removes an allowed TM endpoint
     * @param tmEndpoint
     */
    public void removeAuthorizedTmEndpoint(TmServiceEndpoint tmEndpoint) {
       tmAuthorization.removeAuthorizedTcEndpoint(tmEndpoint);
       tmEndpoints.remove(tmEndpoint);
    }
}
