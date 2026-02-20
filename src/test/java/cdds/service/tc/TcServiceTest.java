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
import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;

/**
 * Test the communication among a TC service user and a TC service provider
 * Test case: Send one TC radiation request, receive an ACK and RADIATION
 * report.
 */
public class TcServiceTest {

    private static final int PROVIDER_PORT = 6666;

    final TcServiceEndpoint authorizedTcEndpoint1 = TcServiceUser.getTcEndpoint("myProvider",
            "myGroundStation",
            "theSpacecraft",
            4711,
            1);

    final TcServiceEndpoint authorizedTcEndpoint2 = TcServiceUser.getTcEndpoint("myProvider",
            "myGroundStation",
            "theSpacecraft",
            4711,
            2);


   final TcServiceEndpoint unAuthorizedTcEndpoint = TcServiceUser.getTcEndpoint("myProvider",
            "unAuthorizedGroundStation",
            "theSpacecraft",
            4711,
            1);

    /**
     * Test one TC user and TC provider:
     *          -> one TC radiation request
     *          <- one TC ACK
     *          <- one Radiation report
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeLimitExceededException
     */        
    @Test
    public void testUnsecureTcService() throws IOException, InterruptedException, TimeLimitExceededException {

        ProviderServer server = new ProviderServer(PROVIDER_PORT);
        server.start();
        server.addAuthorizedTcEndpoint(authorizedTcEndpoint1);

        final TcServiceUser tcServiceUser = TcServiceUser.buildUnsecureTcServiceUser("localhost", PROVIDER_PORT,
                authorizedTcEndpoint1);
        tcServiceUser.openTelecommandEndpoint();

        TelecommandMessage tc = getTcRadiationRequestMessage(1);
        tcServiceUser.sendTelecommand(tc);

        tcServiceUser.waitForTcReports(2);
        tcServiceUser.stop();

        server.stop();
    }

    /**
     * Test two TC user and TC provider with certificates for authentication and encryption:
     * Two runs of each TC user sending and expecting
     *          -> one TC radiation request
     *          <- one TC ACK
     *          <- one Radiation report
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeLimitExceededException
     */        

    @Test
    public void testSecureTcServiceTwoUser() throws IOException, InterruptedException, TimeLimitExceededException {
        final ProviderServer server = new ProviderServer(PROVIDER_PORT,
                resourceToFile("cert/cdds-ca.pem"),
                resourceToFile("cert/cdds-provider.pem"),
                resourceToFile("cert/cdds-provider.key"));

        server.start();
        server.addAuthorizedTcEndpoint(authorizedTcEndpoint1);
        server.addAuthorizedTcEndpoint(authorizedTcEndpoint2);

        final TcServiceUser tcServiceUser1 = TcServiceUser.buildSecureTcService("localhost", PROVIDER_PORT,
                authorizedTcEndpoint1,
                resourceToFile("cert/cdds-ca.pem"),
                resourceToFile("cert/cdds-user.pem"),
                resourceToFile("cert/cdds-user.key"));

        final TcServiceUser tcServiceUser2 = TcServiceUser.buildSecureTcService("localhost", PROVIDER_PORT,
                authorizedTcEndpoint2,
                resourceToFile("cert/cdds-ca.pem"),
                resourceToFile("cert/cdds-user.pem"),
                resourceToFile("cert/cdds-user.key"));

        // Do two runs using the TC endpoints
        for (int idx = 1; idx <= 2; idx++) {
            tcServiceUser1.openTelecommandEndpoint();
            tcServiceUser2.openTelecommandEndpoint();

            TelecommandMessage tc1 = getTcRadiationRequestMessage(idx);
            tcServiceUser1.sendTelecommand(tc1);

            TelecommandMessage tc2 = getTcRadiationRequestMessage(idx+10);
            tcServiceUser2.sendTelecommand(tc2);
            
            tcServiceUser1.waitForTcReports(2);
            tcServiceUser1.stop();

            tcServiceUser2.waitForTcReports(2);
            tcServiceUser2.stop();
        }

        server.stop();
    }

    /**
     * Test if a not authorized endpoint fails as expected
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testNotAuthorizedTcEndpoint() throws IOException, InterruptedException {
       final ProviderServer server = new ProviderServer(PROVIDER_PORT,
                resourceToFile("cert/cdds-ca.pem"),
                resourceToFile("cert/cdds-provider.pem"),
                resourceToFile("cert/cdds-provider.key"));
                
        server.start();
        server.addAuthorizedTcEndpoint(authorizedTcEndpoint1);

        // Use an un authorized endpoint
        final TcServiceUser tcServiceUser = TcServiceUser.buildSecureTcService("localhost", PROVIDER_PORT,
                unAuthorizedTcEndpoint,
                resourceToFile("cert/cdds-ca.pem"),
                resourceToFile("cert/cdds-user.pem"),
                resourceToFile("cert/cdds-user.key"));        
        
        tcServiceUser.openTelecommandEndpoint();
        
        Throwable error = tcServiceUser.getLastError(1000);
        assert(error != null);
        System.out.println("Unauthorized TC Service. Got expected error " + error);
        
        server.stop(); 
    }

    /**
     * Test if a not authenticated endpoint fails as expected
     * The key file is not signed, causing authentication to fail.
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testNotAuthenticatedTcEndpoint() throws IOException, InterruptedException {
       final ProviderServer server = new ProviderServer(PROVIDER_PORT,
                resourceToFile("cert/cdds-ca.pem"),
                resourceToFile("cert/cdds-provider.pem"),
                resourceToFile("cert/cdds-provider.key"));
                
        server.start();
        server.addAuthorizedTcEndpoint(authorizedTcEndpoint1);

        // Use an un authorized endpoint
        final TcServiceUser tcServiceUser = TcServiceUser.buildSecureTcService("localhost", PROVIDER_PORT,
                authorizedTcEndpoint1,
                resourceToFile("cert/cdds-ca-not-ok.pem"),
                resourceToFile("cert/cdds-user-not-ok.pem"),
                resourceToFile("cert/cdds-user.key"));        
        
        tcServiceUser.openTelecommandEndpoint();
        
        Throwable error = tcServiceUser.getLastError(1000);
        assert(error != null);
        System.out.println("Unauthenticated TC Service. Got expected error " + error);
        
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

    /**
     * Converts a resource path (directory) to a File
     * 
     * @param resourcePath
     * @return The File representing the resource.
     */
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
