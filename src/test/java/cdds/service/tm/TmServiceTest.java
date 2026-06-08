package cdds.service.tm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.naming.TimeLimitExceededException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ccsds.cdds.Types.FrameVersion;
import ccsds.cdds.tm.CddsTmService.TmServiceEndpoint;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProviderServer;

/**
 * Test the communication among a TM service user and a TM service provider
 */
public class TmServiceTest {

    private static final int PROVIDER_PORT = 7666;

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

    private static final Logger LOG = LogManager.getLogger("cdds.tm.test");    

    @BeforeEach
        void before(TestInfo info) {
        LOG.info("START " + info.getDisplayName());
    }            

    @AfterEach
    void after(TestInfo info) {
            LOG.info("END " + info.getDisplayName());
    }

    @Test
    public void testNTmFramesSecure() throws IOException, TimeLimitExceededException, InterruptedException, ExecutionException, TimeoutException {
        
        // create a TM provider and add a TM frame production for the endpoint producing numFrames
        TmServiceProvider tmProvider = new TmServiceProvider();
        tmProvider.addTmProduction(authorizedTmEndpoint1, new TmProductionNFrames(numFrames, frameLength)); 

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{tmProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        tmProvider.addAuthorizedTmEndpoint(authorizedTmEndpoint1);

        final TmServiceUser tmServiceUser = TmServiceUser.buildSecureTmService("localhost", PROVIDER_PORT, 
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        List<TmServiceEndpoint> endpoints = tmServiceUser.getEndpoints(5000);

        assert(endpoints.get(0).equals(authorizedTmEndpoint1));

        // use the first provided endpoint for this test, wait for numFrames
        tmServiceUser.openTelemetryEndpoint(endpoints.get(0), numFrames, 0);
        
        tmServiceUser.waitForTmFrames(timeout);
        
        tmServiceUser.shutdown();

        server.stop();
    }

    @Test
    public void testNTmFramesUnsecure() throws IOException, TimeLimitExceededException, InterruptedException {

        // create a TM provider and add a TM frame production for the endpoint producing 10 frames
        TmServiceProvider tmProvider = new TmServiceProvider();
        tmProvider.addTmProduction(authorizedTmEndpoint1, new TmProductionNFrames(numFrames, frameLength)); 

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{tmProvider});

        server.start();
        tmProvider.addAuthorizedTmEndpoint(authorizedTmEndpoint1);

        final TmServiceUser tmServiceUser = TmServiceUser.buildUnsecureTmServiceUser("localhost", PROVIDER_PORT);

        tmServiceUser.openTelemetryEndpoint(authorizedTmEndpoint1, numFrames, 0);
        
        tmServiceUser.waitForTmFrames(timeout);

        tmServiceUser.shutdown();

        server.stop();
    }


}