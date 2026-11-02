package cdds.service.td;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ccsds.cdds.v1.Tracking.TrackingDataMessage;
import ccsds.cdds.v1.Types.ProductionState;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpoint;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/**
 * Test tracking data production sending N tracking data messages,
 * each message carrying the TDM of the KVN example file
 * src/test/resources/TDM/TDM-ONE-WAY-DATA-EXAMPLE.txt as tdmText (KVN format).
 */
public class TrackingDataProductionKvn implements TrackingDataProductionInterface {

    private final long numMessagesToSend;

    private final TrackingDataMessage trackingDataMessage;

    private volatile long messagesSent = 0;

    private volatile long numBackpressure = 0;

    private volatile boolean completed = false;

    /**
     * The tracking data messages sent to the tracking data service user
     */
    private final List<TrackingDataMessage> sentTrackingDataMessages = new CopyOnWriteArrayList<>();

    private static final Logger LOG = LogManager.getLogger("cdds.td.production.kvn");

    /**
     * Constructs the tracking data production object and creates the test data
     * from the TDM KVN example in the test resources.
     * @param numMessagesToSend  The number of tracking data messages to send
     * @throws IOException       Thrown if the TDM KVN example cannot be read
     */
    public TrackingDataProductionKvn(long numMessagesToSend) throws IOException {
        this.numMessagesToSend = numMessagesToSend;

        final String kvnText = TdmKvnUtil.readKvnResource(TdmKvnUtil.TDM_KVN_RESOURCE);

        this.trackingDataMessage = TrackingDataMessage.newBuilder()
                .setProductionState(ProductionState.OPERATIONAL)
                .setTdmText(TdmKvnUtil.toTdmTypeText(kvnText))
                .build();
    }

    @Override
    public void startTrackingDataEndpointService(TrackingDataServiceEndpoint tdEndpoint,
            StreamObserver<TrackingDataMessage> tdUserStream) {

        final ServerCallStreamObserver<TrackingDataMessage> tdUserStreamObserver =
                (ServerCallStreamObserver<TrackingDataMessage>) tdUserStream;

        // only called when stream is ready
        tdUserStreamObserver.setOnReadyHandler(() -> sendData(tdUserStreamObserver));
    }

    /**
     * Sends the remaining messages from messagesSent to numMessagesToSend
     * @param tdUserStream  The stream to send the tracking data messages
     */
    private void sendData(StreamObserver<TrackingDataMessage> tdUserStream) {
        final ServerCallStreamObserver<TrackingDataMessage> tdUserStreamObserver =
                (ServerCallStreamObserver<TrackingDataMessage>) tdUserStream;

        for (long messageNumber = messagesSent; messageNumber < numMessagesToSend; messageNumber++, messagesSent++) {

            if (tdUserStreamObserver.isReady() == false && messagesSent < numMessagesToSend) {
                numBackpressure++;
                break; // leave the for loop
            }

            sentTrackingDataMessages.add(trackingDataMessage);

            tdUserStream.onNext(trackingDataMessage);
        }

        if (completed == false && messagesSent == numMessagesToSend) {
            try {
                Thread.sleep(200, 0); // prevent the next log coming too early (not pretty)
                LOG.info("Tracking data production (KVN) sent " + messagesSent + " tracking data messages, backpressure events: "
                    + numBackpressure);
            } catch (InterruptedException e) {}

            completed = true;

            tdUserStream.onCompleted();
        }
    }

    @Override
    public void stopTrackingDataEndpointService(TrackingDataServiceEndpoint tdEndpoint) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TrackingDataMessage> getSentTrackingDataMessages() {
        return sentTrackingDataMessages;
    }
}
