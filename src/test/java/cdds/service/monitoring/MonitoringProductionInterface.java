package cdds.service.monitoring;

import java.util.Map;

import ccsds.cdds.v1.Monitoring.MonitoringMessage;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpoint;

/**
 * A monitoring production provides the monitoring messages of a
 * monitoring service endpoint. Different productions can be added
 * to the {@link MonitoringServiceProvider} for different endpoints.
 */
public interface MonitoringProductionInterface {

    /**
     * Start providing monitoring messages
     * @param monitoringEndpoint    The endpoint governing the provided monitoring
     * @param monitoringUserStream   The user stream to send the monitoring messages
     */
    public void startMonitoringEndpointService(MonitoringServiceEndpoint monitoringEndpoint,
            io.grpc.stub.StreamObserver<MonitoringMessage> monitoringUserStream);

    /**
     * Stop providing the monitoring messages
     * @param monitoringEndpoint    The endpoint for which provision shall be stopped
     */
    public void stopMonitoringEndpointService(MonitoringServiceEndpoint monitoringEndpoint);

    /**
     * The number of parameter updates sent per parameter identifier.
     * The key of the map is the identifier of the parameter.
     * @return  map of parameter identifier to number of parameter updates sent
     */
    public Map<String, Long> getSentParameterUpdates();

    /**
     * The number of events sent per event identifier.
     * The key of the map is the identifier of the event.
     * @return  map of event identifier to number of events sent
     */
    public Map<String, Long> getSentEvents();
}
