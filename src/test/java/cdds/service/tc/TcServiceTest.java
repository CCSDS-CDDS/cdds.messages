package cdds.service.tc;

import java.io.IOException;

import javax.naming.TimeLimitExceededException;

import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;

import ccsds.cdds.Telecommand.ReportRequest;
import ccsds.cdds.Telecommand.TelecommandMessage;
import ccsds.cdds.Telecommand.TelecommandRadiationRequest;

/**
 * Test the communication among a TC service user and a TC service provider
 * Test case: Send one TC radiation request, receive an ACK and RADIATION
 * report.
 */
public class TcServiceTest {

    private static final int PROVIDER_PORT = 6666;

    @Test
    public void testTcService() throws IOException, InterruptedException, TimeLimitExceededException {

        ProviderServer server = new ProviderServer(PROVIDER_PORT);
        server.start();

        TcServiceUser tcServiceUser = new TcServiceUser("localhost", PROVIDER_PORT);

        TelecommandMessage tc = getTcRadiationRequestMessage(1);

        tcServiceUser.sendTelecommand(tc);

        tcServiceUser.waitForTcReports(2);

        tcServiceUser.stop();

        server.stop();
    }

    /**
     * Creates a dummy TC radiation request with the given command ID
     * 
     * @param commandId
     * @return The Telecommand Message holding a radiation request
     */
    private TelecommandMessage getTcRadiationRequestMessage(int commandId) {
        return TelecommandMessage.newBuilder().setRadiationRequest(
                TelecommandRadiationRequest.newBuilder()
                        .setCommandId(commandId)
                        .setReportRequest(ReportRequest.PRODUCE_REPORT)
                        .setTelecommandFrame(getTcDummy())
                        .build())
                .build();
    }

    /**
     * Provides a real dummy TC as a ByteString
     * 
     * @return A dummy TC
     */
    private static ByteString getTcDummy() {
        return ByteString.copyFromUtf8("Hello TC provider");
    }
}
