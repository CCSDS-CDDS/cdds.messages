package cdds.service.monitoring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ccsds.cdds.v1.Monitoring.FunctionalResourceEvent;
import ccsds.cdds.v1.Monitoring.FunctionalResourceParameter;
import ccsds.cdds.v1.Monitoring.MonitoringMessage;
import ccsds.cdds.v1.Types.Alarm;
import ccsds.cdds.v1.Types.AlarmQualifier;
import ccsds.cdds.v1.Types.EncodedQualifiedValue;
import ccsds.cdds.v1.Types.ObjectIdentifier;
import ccsds.cdds.v1.Types.ProductionState;
import ccsds.cdds.v1.Types.Value;
import ccsds.cdds.v1.Types.ValueQualifier;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpoint;
import cdds.util.TimeUtil;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/**
 * Test monitoring production to send N monitoring messages,
 * each message carrying two Functional Resource parameters
 * and one Functional Resource event.
 */
public class MonitoringProductionNFunctionalResource implements MonitoringProductionInterface {

    /**
     * The description of the first Functional Resource parameter sent in each monitoring message
     */
    public static final String FIRST_FR_PARAMETER_DESCRIPTION = "my.fr-parameter-id";

    /**
     * The OID of the first Functional Resource parameter sent in each monitoring message
     */
    public static final String FIRST_FR_PARAMETER_OID = "1.2.3.4.5";

    /**
     * The description of the second Functional Resource parameter sent in each monitoring message
     */
    public static final String SECOND_FR_PARAMETER_DESCRIPTION = "my.fr-parameter-name-id";

    /**
     * The OID of the second Functional Resource parameter sent in each monitoring message
     */
    public static final String SECOND_FR_PARAMETER_OID = "1.2.3.4.6";

    /**
     * The instance number of the Functional Resource the parameters are reported for
     */
    public static final long FR_PARAMETER_INSTANCE = 2;

    /**
     * The description of the Functional Resource event sent in each monitoring message
     */
    public static final String FR_EVENT_DESCRIPTION = "my.fr-event-id";

    /**
     * The OID of the Functional Resource event sent in each monitoring message
     */
    public static final String FR_EVENT_OID = "1.2.3.5.1";

    /**
     * The instance number of the Functional Resource the event is reported for
     */
    public static final long FR_EVENT_INSTANCE = 2;

    /**
     * The description of the type of the encoded Functional Resource parameter
     * and event values
     */
    public static final String ENCODED_VALUE_TYPE_DESCRIPTION = "my.encoded-value-type-id";

    /**
     * The OID identifying the type of the encoded Functional Resource parameter
     * and event values
     */
    public static final String ENCODED_VALUE_TYPE_OID = "1.2.3.4.100";

    private final long numMessagesToSend;

    private volatile long messagesSent = 0;

    private volatile long numBackpressure = 0;

    private volatile boolean completed = false;

    /**
     * The number of parameter updates sent per parameter identifier
     */
    private final Map<String, Long> sentParameterUpdates = new ConcurrentHashMap<>();

    /**
     * The number of events sent per event identifier
     */
    private final Map<String, Long> sentEvents = new ConcurrentHashMap<>();

    private static final Logger LOG = LogManager.getLogger("cdds.monitoring.production");

    /**
     * Constructs the monitoring production object
     * @param numMessagesToSend  The number of monitoring messages to send
     */
    public MonitoringProductionNFunctionalResource(long numMessagesToSend) {
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
                    .addFunctionalResourceParameters(FunctionalResourceParameter.newBuilder()
                            .setFunctionalResourceParameterIdentifier(
                                    createObjectIdentifier(FIRST_FR_PARAMETER_DESCRIPTION, FIRST_FR_PARAMETER_OID))
                            .setFunctionalResourceInstance(FR_PARAMETER_INSTANCE)
                            .setTimeStamp(TimeUtil.now())
                            .setParameterValue(createEncodedQualifiedValue(47.11))
                            .build())
                    .addFunctionalResourceParameters(FunctionalResourceParameter.newBuilder()
                            .setFunctionalResourceParameterIdentifier(
                                    createObjectIdentifier(SECOND_FR_PARAMETER_DESCRIPTION, SECOND_FR_PARAMETER_OID))
                            .setFunctionalResourceInstance(FR_PARAMETER_INSTANCE)
                            .setTimeStamp(TimeUtil.now())
                            .setParameterValue(createEncodedQualifiedValue(8.15))
                            .build())
                    .addFunctionalResourceEvents(FunctionalResourceEvent.newBuilder()
                            .setFunctionalResourceEventIdentifier(
                                    createObjectIdentifier(FR_EVENT_DESCRIPTION, FR_EVENT_OID))
                            .setFunctionalResourceInstance(FR_EVENT_INSTANCE)
                            .setTimeStamp(TimeUtil.now())
                            .addEventValue(createEncodedQualifiedValue(47.11))
                            .build())
                    .build();

            if (monitoringUserStreamObserver != null && monitoringUserStreamObserver.isReady() == false &&
                messagesSent < numMessagesToSend) {
                numBackpressure++;
                break; // leave the for loop
            }

            for (final FunctionalResourceParameter frParameter : monitoringMessage.getFunctionalResourceParametersList()) {
                sentParameterUpdates.merge(frParameter.getFunctionalResourceParameterIdentifier().getOidString(),
                        1L, Long::sum);
            }

            for (final FunctionalResourceEvent frEvent : monitoringMessage.getFunctionalResourceEventsList()) {
                sentEvents.merge(frEvent.getFunctionalResourceEventIdentifier().getOidString(), 1L, Long::sum);
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
     * Creates an encoded qualified value carrying a double value
     * @param value  The value of the encoded qualified value
     * @return       The created EncodedQualifiedValue
     */
    private static EncodedQualifiedValue createEncodedQualifiedValue(double value) {
        return EncodedQualifiedValue.newBuilder()
                .setEncodedTypeIdentifier(
                        createObjectIdentifier(ENCODED_VALUE_TYPE_DESCRIPTION, ENCODED_VALUE_TYPE_OID))
                .setEncodedValue(Value.newBuilder()
                        .setDoubleValue(value)
                        .build()
                        .toByteString())
                .setValueQualifier(ValueQualifier.VALID_VALUE)
                .setAlarmQualifier(AlarmQualifier.newBuilder()
                        .setAlarm(Alarm.NO_ALARM)
                        .build())
                .build();
    }

    /**
     * Creates an ObjectIdentifier for the given OID string in dotted notation.
     * The oidString carries a description of the identified object
     * in a human readable way, the arcs carry the OID in dotted notation.
     * @param description  The description of the identified object
     * @param oidString    The OID string in dotted notation
     * @return             The created ObjectIdentifier
     */
    private static ObjectIdentifier createObjectIdentifier(String description, String oidString) {
        final ObjectIdentifier.Builder oid = ObjectIdentifier.newBuilder()
                .setOidString(description);

        for (final String arc : oidString.split("\\.")) {
            oid.addArcs(Long.parseLong(arc));
        }

        return oid.build();
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
