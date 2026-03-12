package cdds.service.tm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;

import javax.naming.TimeLimitExceededException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ccsds.cdds.Types.FrameVersion;
import ccsds.cdds.tm.CddsTmService.TmServiceEndpoint;
import cdds.service.tc.ProviderServer;
import io.grpc.BindableService;

/**
 * Test the communication among a TM service user and a TM service provider
 */
public class TmServiceTest {

    private static final int PROVIDER_PORT = 6666;

    final long numFrames = 750_000;
    final long timeout = 30_000;
    final int frameLength = 1115;

    final TmServiceEndpoint authorizedTmEndpoint1 = TmServiceUser.getTmEndpoint("myProvider",
            "myGroundStation",
            "theSpacecraft",
            4711,
            FrameVersion.AOS,
            1);

    final TmServiceEndpoint authorizedTmEndpoint2 = TmServiceUser.getTmEndpoint("myProvider",
            "myGroundStation",
            "theSpacecraft",
            4711,
            FrameVersion.AOS,
            2);

   final TmServiceEndpoint unAuthorizedTmEndpoint = TmServiceUser.getTmEndpoint("myProvider",
            "unAuthorizedGroundStation",
            "theSpacecraft",
            4711,
            FrameVersion.AOS,
            1);

    private static final Logger LOG = LogManager.getLogger("cdds.tc.test");    

    @BeforeEach
        void before(TestInfo info) {
        LOG.info("START " + info.getDisplayName());
    }            

    @AfterEach
    void after(TestInfo info) {
            LOG.info("END " + info.getDisplayName());
    }

    @Test
    public void testNTmFramesSecure() throws IOException, TimeLimitExceededException, InterruptedException {
        
        // create a TM provider and add a TM frame production for the endpoint producing 10 frames
        TmServiceProvider tmProvider = new TmServiceProvider();
        tmProvider.addTmProduction(authorizedTmEndpoint1, new TmProductionNFrames(numFrames, 500, frameLength)); 

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new BindableService[]{tmProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        server.addAuthorizedTmEndpoint(authorizedTmEndpoint1);

        final TmServiceUser tmServiceUser = TmServiceUser.buildSecureTmService("localhost", PROVIDER_PORT, 
                authorizedTmEndpoint1,
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        tmServiceUser.openTelemetryEndpoint(numFrames, 0);
        
        tmServiceUser.waitForTmFrames(timeout);
        
        tmServiceUser.shutdown();

        server.stop();
    }

    @Test
    public void testNTmFramesUnsecure() throws IOException, TimeLimitExceededException, InterruptedException {

        // create a TM provider and add a TM frame production for the endpoint producing 10 frames
        TmServiceProvider tmProvider = new TmServiceProvider();
        tmProvider.addTmProduction(authorizedTmEndpoint1, new TmProductionNFrames(numFrames, 1, frameLength)); 

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new BindableService[]{tmProvider});

        server.start();
        server.addAuthorizedTmEndpoint(authorizedTmEndpoint1);

        final TmServiceUser tmServiceUser = TmServiceUser.buildUnsecureTmServiceUser("localhost", PROVIDER_PORT, authorizedTmEndpoint1);

        tmServiceUser.openTelemetryEndpoint(numFrames, 0);
        
        tmServiceUser.waitForTmFrames(timeout);

        tmServiceUser.shutdown();

        server.stop();
    }


}