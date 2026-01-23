package cdds.service.tc;

import java.util.logging.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.Telecommand.TelecommandMessage;
import ccsds.cdds.Telecommand.TelecommandProviderStatus;
import ccsds.cdds.Telecommand.TelecommandRadiation;
import ccsds.cdds.Telecommand.TelecommandRadiationRequestAck;
import ccsds.cdds.Telecommand.TelecommandReport;
import ccsds.cdds.Telecommand.UplinkStatus;
import ccsds.cdds.Types.AntennaId;
import ccsds.cdds.Types.ProductionState;
import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;
import cdds.util.TimeUtil;
import io.grpc.stub.StreamObserver;

public class TcServiceProviderStream implements StreamObserver<TelecommandMessage> {

    private final StreamObserver<TelecommandReport> tcUserStream;
    private static final Logger LOG = Logger.getLogger("CDDS TC Provider Stream");
    
    // get from the gRPC call context the meta data SPACECRAFT as provided by the user
    private TcServiceEndpoint tcEndPoint;

    public TcServiceProviderStream(StreamObserver<TelecommandReport> tcUserStream) {
        this.tcUserStream = tcUserStream;
    }

    @Override
    public void onCompleted() {
        LOG.info("TC services stopped on use request");
        tcUserStream.onCompleted();
    }

    @Override
    public void onError(Throwable t) {
        LOG.warning("Error in Tc Provider: " + t);
    }

    @Override
    public void onNext(TelecommandMessage tc) {
        
        try {
            byte[] endpointBytes = TcServiceAuthorization.TC_ENDPOINT_CTX_KEY.get();    // get the tc-endpoint-bin meta data
            tcEndPoint = TcEndpointJson.tcEndpointFromJson(endpointBytes);              // decode the endpoint from JSON
        } catch (InvalidProtocolBufferException e) {
            tcEndPoint = TcServiceEndpoint.newBuilder().build(); // empty
            e.printStackTrace();
        }

        LOG.info("Received TC message for '" + tcEndPoint.getServiceUser() + "'\n" + tc);

        if(tc.hasRadiationRequest()) {

            // send an ACK
            TelecommandReport tcReport = TelecommandReport.newBuilder().setCommandId(tc.getRadiationRequest().getCommandId())
            .setProductionState(ProductionState.OPERATIONAL)
            .setBufferAvailable(4711)
            .setReportGenerationTime(TimeUtil.now())
            .setAntennaId(AntennaId.newBuilder()
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
            .setAntennaId(AntennaId.newBuilder()
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
            .setAntennaId(AntennaId.newBuilder()
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
