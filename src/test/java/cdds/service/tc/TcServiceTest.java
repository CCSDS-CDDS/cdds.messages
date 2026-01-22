package cdds.service.tc;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

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
    public void testUnsecureTcService() throws IOException, InterruptedException, TimeLimitExceededException {

        ProviderServer server = new ProviderServer(PROVIDER_PORT);
        server.start();

        final TcServiceUser tcServiceUser = TcServiceUser.buildUnsecureTcServiceUser("localhost", PROVIDER_PORT);

        tcServiceUser.openTelecommandStream();

        TelecommandMessage tc = getTcRadiationRequestMessage(1);

        tcServiceUser.sendTelecommand(tc);

        tcServiceUser.waitForTcReports(2);

        tcServiceUser.stop();

        server.stop();
    }

    @Test
    public void testSecureTcService() throws IOException, InterruptedException, TimeLimitExceededException {
        final ProviderServer server = new ProviderServer(PROVIDER_PORT, 
                                                        resourceToFile("cert/cdds-ca.pem"),
                                                        resourceToFile("cert/cdds-provider.pem"), 
                                                        resourceToFile("cert/cdds-provider.key"));

        server.start();

        final TcServiceUser tcServiceUser = TcServiceUser.buildSecureTcService("localhost", PROVIDER_PORT, 
                                                                                resourceToFile("cert/cdds-ca.pem"), 
                                                                                resourceToFile("cert/cdds-user.pem"), 
                                                                                resourceToFile("cert/cdds-user.key"));

        // Do two runs using the TC endpoint                                                                        
        for(int idx=1; idx<=2; idx++) {                                                                                
            tcServiceUser.openTelecommandStream();                                                                                    
            
            TelecommandMessage tc = getTcRadiationRequestMessage(idx);

            tcServiceUser.sendTelecommand(tc);

            tcServiceUser.waitForTcReports(2);

            tcServiceUser.stop();
        }

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

        public static File resourceToFile(String resourcePath) {
        URL url = Thread.currentThread()
                .getContextClassLoader()
                .getResource(resourcePath);

        if (url == null) {
            throw new IllegalArgumentException(
                "Resource not found: " + resourcePath);
        }

        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URI for resource: " + resourcePath, e);
        }
    }
}
