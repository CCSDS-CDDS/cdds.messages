package cdds.service.monitoring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ccsds.cdds.v1.Monitoring.GenericMonitoringParameter;
import ccsds.cdds.v1.Monitoring.MonitoringMessage;
import ccsds.cdds.v1.Types.Alarm;
import ccsds.cdds.v1.Types.AlarmQualifier;
import ccsds.cdds.v1.Types.ProductionState;
import ccsds.cdds.v1.Types.QualifiedValue;
import ccsds.cdds.v1.Types.Value;
import ccsds.cdds.v1.Types.ValueQualifier;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpoint;
import cdds.util.TimeUtil;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/**
 * Test monitoring production to send N monitoring messages,
 * each message carrying two generic monitoring parameters.
 */
public class MonitoringProductionNGenericParam implements MonitoringProductionInterface {

    /**
     * The identifier of the first generic monitoring parameter sent in each monitoring message
     */
    public static final String FIRST_PARAMETER_IDENTIFIER = "test-monitoring-parameter-0";

    /**
     * The identifier of the second generic monitoring parameter sent in each monitoring message
     */
    public static final String SECOND_PARAMETER_IDENTIFIER = "test-monitoring-parameter-1";

    private final long numMessagesToSend;

    private volatile long messagesSent = 0;

    private volatile long numBackpressure = 0;

    private volatile boolean completed = false;

    /**
     * The number of parameter updates sent per parameter identifier
     */
    private final Map<String, Long> sentParameterUpdates = new ConcurrentHashMap<>();

    /**
     * The number of events sent per event identifier;
     * this production does not send events
     */
    private final Map<String, Long> sentEvents = new ConcurrentHashMap<>();

    private static final Logger LOG = LogManager.getLogger("cdds.monitoring.production");

    /**
     * Constructs the monitoring production object
     * @param numMessagesToSend  The number of monitoring messages to send
     */
    public MonitoringProductionNGenericParam(long numMessagesToSend) {
        this.numMessagesToSend = numMessagesToSend;
    }

    @Override
    public void startMonitoringEndpointService(MonitoringServiceEndpoint monitoringEndpoint,
            StreamObserver<MonitoringMessage> monitoringUserStream) {

        final ServerCallStreamObserver<MonitoringMessage> monitoringUserStreamObserver =
                (ServerCallStreamObserver<MonitoringMessage>) monitoringUserStream;

        // only called when stream is ready
        monitoringUserStreamObserver.setOnReadyHandler(() -> sendData(monitoringUserStreamObserver));
    }

    /**
     * Sends the remaining messages from messagesSent to numMessagesToSend
     * @param monitoringUserStream  The stream to send the monitoring messages
     */
    private void sendData(StreamObserver<MonitoringMessage> monitoringUserStream) {
        final ServerCallStreamObserver<MonitoringMessage> monitoringUserStreamObserver =
                (ServerCallStreamObserver<MonitoringMessage>) monitoringUserStream;

        for (long messageNumber = messagesSent; messageNumber < numMessagesToSend; messageNumber++, messagesSent++) {
            MonitoringMessage monitoringMessage = MonitoringMessage.newBuilder()
                    .setProductionState(ProductionState.OPERATIONAL)
                    .addGenericMonitoringParameters(createGenericMonitoringParameter(FIRST_PARAMETER_IDENTIFIER, 47.11))
                    .addGenericMonitoringParameters(createGenericMonitoringParameter(SECOND_PARAMETER_IDENTIFIER, 8.15))
                    .build();

            if (monitoringUserStreamObserver != null && monitoringUserStreamObserver.isReady() == false &&
                messagesSent < numMessagesToSend) {
                numBackpressure++;
                break; // leave the for loop
            }

            for (final GenericMonitoringParameter genericParameter : monitoringMessage.getGenericMonitoringParametersList()) {
                sentParameterUpdates.merge(genericParameter.getIdentifier(), 1L, Long::sum);
            }

            monitoringUserStream.onNext(monitoringMessage);
        }

        if (completed == false && messagesSent == numMessagesToSend) {
            try {
                Thread.sleep(200, 0); // prevent the next log coming too early (not pretty)
                LOG.info("Monitoring production sent " + messagesSent + " backpressure events: "
                    + numBackpressure);
            } catch (InterruptedException e) {}

            completed = true;

            monitoringUserStream.onCompleted();
        }
    }

    /**
     * Creates a generic monitoring parameter
     * @param identifier  The identifier of the parameter
     * @param value       The value of the parameter
     * @return            The created GenericMonitoringParameter
     */
    private static GenericMonitoringParameter createGenericMonitoringParameter(String identifier, double value) {
        return GenericMonitoringParameter.newBuilder()
                .setIdentifier(identifier)
                .setTimeStamp(TimeUtil.now())
                .setQualifiedValue(QualifiedValue.newBuilder()
                        .setValue(Value.newBuilder()
                                .setDoubleValue(value)
                                .build())
                        .setValueQualifier(ValueQualifier.VALID_VALUE)
                        .setAlarmQualifier(AlarmQualifier.newBuilder()
                                .setAlarm(Alarm.NO_ALARM)
                                .build())
                        .build())
                .build();
    }

    @Override
    public void stopMonitoringEndpointService(MonitoringServiceEndpoint monitoringEndpoint) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Long> getSentParameterUpdates() {
        return sentParameterUpdates;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Long> getSentEvents() {
        return sentEvents;
    }
}
