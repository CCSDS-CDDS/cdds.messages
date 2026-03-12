package cdds.service.tm;

import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.ByteString;
import ccsds.cdds.Telemetry.TelemetryData;
import ccsds.cdds.Telemetry.TelemetryItem;
import ccsds.cdds.Telemetry.TelemetryMessage;
import ccsds.cdds.Types.Annotation;
import ccsds.cdds.Types.ApertureId;
import ccsds.cdds.Types.ReceptionMetaData;
import ccsds.cdds.Types.Value;
import ccsds.cdds.tm.CddsTmService.TmServiceEndpoint;
import cdds.tm.TestTelemetryFile;
import cdds.util.TimeUtil;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/**
 * Test TM production to send N TM frames
 */
public class TmProductionNFrames implements TmProduction {

    private final long numMessagesToSend;

    private volatile long messagesSent = 0;

    private final long numFramesToBatch;

    private volatile long numBackpressure = 0;

    private volatile boolean completed = false;

    private final int frameLength;

    final ByteString data;

    private static final Logger LOG = LogManager.getLogger("cdds.tm.production");

    /**
     * Constructs the TM production object
     * @param numFramesToSend
     * @param numFramesToBatch
     * @param frameLength
     */
    public TmProductionNFrames(long numFramesToSend, long numFramesToBatch, int frameLength) {
        this.numMessagesToSend = numFramesToSend;
        this.numFramesToBatch = numFramesToBatch;
        this.frameLength = frameLength;

        data = ByteString.copyFrom(TestTelemetryFile.getFrameData(this.frameLength));
    } 

    @Override
    public void startTmEndpointService(TmServiceEndpoint tmEndpoint, StreamObserver<TelemetryMessage> tmUserStream) {
       
/*
        Thread dataProductionThread = new Thread(new Runnable() {

            @Override
            public void run() {
                sendData(tmUserStream);
            }
            
        }, "TM data production thread");
        
        dataProductionThread.start();
*/

        final ServerCallStreamObserver<TelemetryMessage> tmUserStreamObserver = (ServerCallStreamObserver<TelemetryMessage>) tmUserStream;

        // only called when stream is ready
        tmUserStreamObserver.setOnReadyHandler(() -> sendData(tmUserStream));
    }

    /**
     * Sends the remaining data from framesSent to numFramesToSend
     * @param tmUserStream
     */
    private void sendData(StreamObserver<TelemetryMessage> tmUserStream) {
        final ServerCallStreamObserver<TelemetryMessage> tmUserStreamObserver = (ServerCallStreamObserver<TelemetryMessage>) tmUserStream;

        for (long frameNumber=messagesSent; frameNumber<numMessagesToSend; frameNumber+=numFramesToBatch, messagesSent+=numFramesToBatch) {
            TelemetryMessage tmMessage = getTmMessage();

            if (tmUserStreamObserver != null && tmUserStreamObserver.isReady() == false &&
                messagesSent < numMessagesToSend) {
                numBackpressure++;
                break; // leave the for loop
            }

            tmUserStream.onNext(tmMessage);
        }

        if (completed == false && messagesSent >= numMessagesToSend) {
            try {
                Thread.sleep(200, 0); // prevent the next log coming too early (not pretty)
                LOG.info("TM production sent " + messagesSent + " frames in batches of " + numFramesToBatch  + ". Backpressure events: "
                    + numBackpressure);
            } catch (InterruptedException e)   {}

            completed = true;

            tmUserStream.onCompleted();
        }
    }
    
    /**
     * Creates a TelemetryMessage based on the internal data member.
     * @return
     */
    private TelemetryMessage getTmMessage() {
        if(numFramesToBatch == 1) {
            return TelemetryMessage.newBuilder()
                        .setTelemetry(
                                TelemetryData.newBuilder()
                                        .addMetaData(0, ReceptionMetaData.newBuilder()
                                                .setApertureId(ApertureId.newBuilder()
                                                        .setLocalForm("NNO1")
                                                        .build())
                                                .setReceiveTime(TimeUtil.now())
                                                .setDataLinkContinuity(-1)
                                                .build())
                                        .addPrivateAnnotation(Annotation.newBuilder()
                                                .setName("test-anno")
                                                .setValue(Value.newBuilder()
                                                        .setStringValue("test-anno-val")
                                                        .build())
                                                .build())
                                        .setData(data)
                                        .build())
                        .build(); 
        } else {
            List<TelemetryItem> tmItems = new LinkedList<TelemetryItem>();

            for(int idx=0; idx<numFramesToBatch; idx++) {
                tmItems.add(TelemetryItem.newBuilder()
                   .setTelemetry(TelemetryData.newBuilder()
                                        .addMetaData(0, ReceptionMetaData.newBuilder()
                                                .setApertureId(ApertureId.newBuilder()
                                                        .setLocalForm("NNO1")
                                                        .build())
                                                .setReceiveTime(TimeUtil.now())
                                                .setDataLinkContinuity(-1)
                                                .build())
                                        .addPrivateAnnotation(Annotation.newBuilder()
                                                .setName("test-anno")
                                                .setValue(Value.newBuilder()
                                                        .setStringValue("test-anno-val")
                                                        .build())
                                                .build())
                                        .setData(data)
                                        .build()).build());                   
            }
            
            return TelemetryMessage.newBuilder().addAllTelemetryItems(tmItems).build();            
        }
    }

    @Override
    public void stopTmEndpointService(TmServiceEndpoint tmEndpoint) {
    }
}
