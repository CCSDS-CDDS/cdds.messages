package cdds.service.tm;

import ccsds.cdds.tm.CddsTmService.TmServiceEndpoint;

public interface TmProduction {

    /**
     * Start providing TM
     * @param tmEndpoint    The endpoint governing the provided TM
     * @param tmUserStream  The user stream to send the TM
     */
    public void startTmEndpointService(TmServiceEndpoint tmEndpoint, io.grpc.stub.StreamObserver<ccsds.cdds.Telemetry.TelemetryMessage> tmUserStream);

    /**
     * Stop providing the TM
     * @param tmEndpoint    The endpoint for which provision shall be stopped
     */
    public void stopTmEndpointService(TmServiceEndpoint tmEndpoint);
}
