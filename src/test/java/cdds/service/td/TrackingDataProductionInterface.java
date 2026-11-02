package cdds.service.td;

import java.util.List;

import ccsds.cdds.v1.Tracking.TrackingDataMessage;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpoint;

/**
 * A tracking data production provides the tracking data messages of a
 * tracking data service endpoint. Different productions can be added
 * to the {@link TrackingDataServiceProvider} for different endpoints.
 */
public interface TrackingDataProductionInterface {

    /**
     * Start providing tracking data messages
     * @param tdEndpoint    The endpoint governing the provided tracking data
     * @param tdUserStream  The user stream to send the tracking data messages
     */
    public void startTrackingDataEndpointService(TrackingDataServiceEndpoint tdEndpoint,
            io.grpc.stub.StreamObserver<TrackingDataMessage> tdUserStream);

    /**
     * Stop providing the tracking data messages
     * @param tdEndpoint    The endpoint for which provision shall be stopped
     */
    public void stopTrackingDataEndpointService(TrackingDataServiceEndpoint tdEndpoint);

    /**
     * The tracking data messages sent to the tracking data service user.
     * @return  The list of sent tracking data messages
     */
    public List<TrackingDataMessage> getSentTrackingDataMessages();
}
