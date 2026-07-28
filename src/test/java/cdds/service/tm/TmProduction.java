package cdds.service.tm;

import ccsds.cdds.v1.Telemetry;
import ccsds.cdds.v1.tm.CddsTmService.TmServiceEndpoint;

public interface TmProduction {

    /**
     * Start providing TM
     * @param tmEndpoint    The endpoint governing the provided TM
     * @param tmUserStream  The user stream to send the TM
     */
    public void startTmEndpointService(TmServiceEndpoint tmEndpoint, io.grpc.stub.StreamObserver<Telemetry.TelemetryMessage> tmUserStream);

    /**
     * Stop providing the TM
     * @param tmEndpoint    The endpoint for which provision shall be stopped
     */
    public void stopTmEndpointService(TmServiceEndpoint tmEndpoint);
}
