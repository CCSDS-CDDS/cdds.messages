package cdds.service.tc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.Telecommand.TelecommandMessage;
import ccsds.cdds.v1.Telecommand.TelecommandProviderStatus;
import ccsds.cdds.v1.Telecommand.TelecommandRadiation;
import ccsds.cdds.v1.Telecommand.TelecommandRadiationRequestAck;
import ccsds.cdds.v1.Telecommand.TelecommandReport;
import ccsds.cdds.v1.Telecommand.UplinkStatus;
import ccsds.cdds.v1.Types.ApertureId;
import ccsds.cdds.v1.Types.ProductionState;
import ccsds.cdds.v1.tc.CddsTcService.TcServiceEndpoint;
import cdds.service.common.EndpointUtil;
import cdds.service.common.ProtoJsonUtil;
import cdds.util.TimeUtil;
import io.grpc.stub.StreamObserver;

/**
 * Simple TC endpoint stream. Receives TC from the user;
 * each received TC is responded with an ACK and RADIATION message.
 */
public class TcServiceEndpointStream implements StreamObserver<TelecommandMessage> {

    private final StreamObserver<TelecommandReport> tcUserStream;
    private final Logger LOG;
    
    @SuppressWarnings("unused")
    private final TcServiceEndpoint tcEndPoint;

    public TcServiceEndpointStream(StreamObserver<TelecommandReport> tcUserStream, TcServiceEndpoint tcEndPoint) {
        this.tcUserStream = tcUserStream;
        this.tcEndPoint = tcEndPoint;
        LOG = LogManager.getLogger(EndpointUtil.toString("cdds.tc.provider", tcEndPoint));
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

    @SuppressWarnings("unused")
    @Override
    public void onNext(TelecommandMessage tc) {
        
        try {
            byte[] endpointBytes = TcServiceAuthorization.TC_ENDPOINT_CTX_KEY.get();    // get the tc-endpoint-bin meta data
             TcServiceEndpoint tcEndPointRuntime = ProtoJsonUtil.fromJson(endpointBytes, TcServiceEndpoint.newBuilder());              // decode the endpoint from JSON
             LOG.info("Received TC message\n" + tc);
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
