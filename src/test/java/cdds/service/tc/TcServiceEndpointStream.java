package cdds.service.tc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.Telecommand.TelecommandMessage;
import ccsds.cdds.Telecommand.TelecommandProviderStatus;
import ccsds.cdds.Telecommand.TelecommandRadiation;
import ccsds.cdds.Telecommand.TelecommandRadiationRequestAck;
import ccsds.cdds.Telecommand.TelecommandReport;
import ccsds.cdds.Telecommand.UplinkStatus;
import ccsds.cdds.Types.ApertureId;
import ccsds.cdds.Types.ProductionState;
import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;
import cdds.util.TimeUtil;
import io.grpc.stub.StreamObserver;

/**
 * Simple TC endpoint stream. Receives TC from the user;
 * each received TC is responded with an ACK and RADIATION message.
 */
public class TcServiceEndpointStream implements StreamObserver<TelecommandMessage> {

    private final StreamObserver<TelecommandReport> tcUserStream;
    private final Logger LOG;
    
    private final TcServiceEndpoint tcEndPoint;

    public TcServiceEndpointStream(StreamObserver<TelecommandReport> tcUserStream, TcServiceEndpoint tcEndPoint) {
        this.tcUserStream = tcUserStream;
        this.tcEndPoint = tcEndPoint;
        LOG = LogManager.getLogger("cdds.tc.provider.endpoint [" + TcEndpointUtil.getEndpointType(tcEndPoint) + "]");
    }

    @Override
    public void onCompleted() {
        LOG.info("stopped on user request");
        tcUserStream.onCompleted();
    }

    @Override
    public void onError(Throwable t) {
        LOG.warn("Error: " + t);
    }

    @Override
    public void onNext(TelecommandMessage tc) {
        
        try {
            byte[] endpointBytes = TcServiceAuthorization.TC_ENDPOINT_CTX_KEY.get();    // get the tc-endpoint-bin meta data
             TcServiceEndpoint tcEndPointRuntime = TcEndpointUtil.tcEndpointFromJson(endpointBytes);              // decode the endpoint from JSON
             LOG.info("Received TC message for '" + TcEndpointUtil.getEndpointType(tcEndPointRuntime) + "'\n" + tc);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }

        
        if(tc.hasRadiationRequest()) {

            // send an ACK
            TelecommandReport tcReport = TelecommandReport.newBuilder().setCommandId(tc.getRadiationRequest().getCommandId())
            .setProductionState(ProductionState.OPERATIONAL)
            .setBufferAvailable(4711)
            .setReportGenerationTime(TimeUtil.now())
            .setApertureId(ApertureId.newBuilder()
                .setLocalForm("NNO1")
                .build())
            .setAck(TelecommandRadiationRequestAck.newBuilder().build())
            .build();
            tcUserStream.onNext(tcReport);

            // send a radiation report
            tcReport = TelecommandReport.newBuilder().setCommandId(tc.getRadiationRequest().getCommandId())
            .setProductionState(ProductionState.OPERATIONAL)
            .setBufferAvailable(4711)
            .setReportGenerationTime(TimeUtil.now())
            .setApertureId(ApertureId.newBuilder()
                .setLocalForm("NNO1")
                .build())
            .setRadiation(TelecommandRadiation.newBuilder()
                .setRadiationStartTime(TimeUtil.now())
                .setRadiationStopTime(TimeUtil.now())
                .build())
            .build();
            tcUserStream.onNext(tcReport);


        } else if(tc.hasReportRequest()) {
            TelecommandReport tcReport = TelecommandReport.newBuilder().setCommandId(tc.getRadiationRequest().getCommandId())
            .setProductionState(ProductionState.OPERATIONAL)
            .setBufferAvailable(4711)
            .setReportGenerationTime(TimeUtil.now())
            .setApertureId(ApertureId.newBuilder()
                .setLocalForm("NNO1")
                .build())
            .setProviderStatus(TelecommandProviderStatus.newBuilder()
                .setUplinkStatus(UplinkStatus.NOMINAL)
                .setNumberOfTelecommandsReceived(10)
                .setNumberOfTelecommandsProcessed(9)
                .setNumberOfTelecommandsRadiated(8)
                .build())
            .build();
            tcUserStream.onNext(tcReport);

        }
    }
                
}
