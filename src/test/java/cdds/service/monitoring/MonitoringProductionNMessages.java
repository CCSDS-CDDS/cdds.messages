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
 * each message carrying one generic monitoring parameter.
 */
public class MonitoringProductionNMessages implements MonitoringProductionInterface {

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
    public MonitoringProductionNMessages(long numMessagesToSend) {
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
                    .addGenericMonitoringParameters(GenericMonitoringParameter.newBuilder()
                            .setIdentifier("test-monitoring-parameter-" + messageNumber)
                            .setTimeStamp(TimeUtil.now())
                            .setQualifiedValue(QualifiedValue.newBuilder()
                                    .setValue(Value.newBuilder()
                                            .setDoubleValue(47.11)
                                            .build())
                                    .setValueQualifier(ValueQualifier.VALID_VALUE)
                                    .setAlarmQualifier(AlarmQualifier.newBuilder()
                                            .setAlarm(Alarm.NO_ALARM)
                                            .build())
                                    .build())
                            .build())
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
