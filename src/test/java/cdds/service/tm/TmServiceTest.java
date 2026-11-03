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

import ccsds.cdds.v1.Types.FrameVersion;
import ccsds.cdds.v1.tm.CddsTmService.TmServiceEndpoint;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProviderServer;

/**
 * Test the communication among a CDDS TM service user and a CDDS TM service
 * provider using the gRPC CddsTmServiceProvider service.
 * <p>
 * Both tests start a CDDS TM provider on a local port, register the TM
 * service endpoint authorizedTmEndpoint1 together with a TM frame production
 * and connect a TM service user. The secure test connects the user using
 * mutual TLS and discovers the endpoints authorized for the authenticated
 * service user by calling getEndpoints before opening the telemetry endpoint
 * by calling openTelemetryEndpoint providing the endpoint as tm-endpoint-bin
 * meta data. The unsecure test connects the user without TLS and opens the
 * telemetry endpoint directly. Both tests wait for the numFrames TM frames
 * produced by the production.
 */
public class TmServiceTest {

    private static final int PROVIDER_PORT = 7666;

    final long numFrames = 750_000;
    final long timeout = 30_000;
    final int frameLength = 1115;

    final TmServiceEndpoint authorizedTmEndpoint1 = TmServiceUser.getTmEndpoint("authorizedTmEndpoint1",
            "myProvider",
            "myGroundStation",
            "theSpacecraft",
            4711,
            FrameVersion.AOS,
            1);

    final TmServiceEndpoint authorizedTmEndpoint2 = TmServiceUser.getTmEndpoint("authorizedTmEndpoint2",
            "myProvider",
            "myGroundStation",
            "theSpacecraft",
            4711,
            FrameVersion.AOS,
            2);

   final TmServiceEndpoint unAuthorizedTmEndpoint = TmServiceUser.getTmEndpoint("unAuthorizedTmEndpoint",
            "myProvider",
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

    /**
     * Tests the delivery of TM frames over a secure (mTLS) connection:
     * the test starts a secure CDDS TM provider on a local port, registers
     * the TM service endpoint authorizedTmEndpoint1 together with a TM frame
     * production producing numFrames frames of frameLength bytes, and
     * connects a TM service user using mutual TLS. The user discovers the
     * endpoints authorized for the authenticated service user by calling
     * getEndpoints, opens the telemetry endpoint by calling
     * openTelemetryEndpoint providing the endpoint as tm-endpoint-bin meta
     * data and waits for the numFrames TM frames produced by the
     * {@link TmProductionNFrames} production.
     * <p>
     * The test verifies that getEndpoints returns exactly the endpoint
     * authorized for the authenticated service user.
     */
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

    /**
     * Tests the delivery of TM frames over an unsecure (plaintext) connection:
     * the test starts a CDDS TM provider on a local port without TLS,
     * registers the TM service endpoint authorizedTmEndpoint1 together with a
     * TM frame production producing numFrames frames of frameLength bytes, and
     * connects a TM service user without TLS. The user opens the telemetry
     * endpoint by calling openTelemetryEndpoint with authorizedTmEndpoint1
     * and waits for the numFrames TM frames produced by the
     * {@link TmProductionNFrames} production.
     */
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