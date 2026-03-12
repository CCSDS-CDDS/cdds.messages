package cdds.service.tm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.tm.CddsTmService.TmServiceEndpoint;
import ccsds.cdds.tm.TmServiceProviderGrpc.TmServiceProviderImplBase;
import cdds.service.common.ProtoJsonUtil;

/**
 * Simple TM test server
 */
public class TmServiceProvider extends TmServiceProviderImplBase {

    private static final Logger LOG = LogManager.getLogger("cdds.tm.provider");

    private final Map<TmServiceEndpoint, TmProduction> tmProductions = new ConcurrentHashMap<>();

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
                LOG.info("Open TM stream for endpoint:\n" + tmEndpoint);
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
}
