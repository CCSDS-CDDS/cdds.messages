package cdds.service.monitoring;

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

import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringMode;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpoint;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpointList;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProviderServer;
import cdds.service.common.ProtoJsonUtil;

/**
 * Test the communication among a CDDS monitoring service user and a CDDS
 * monitoring service provider using the gRPC MonitoringServiceProvider service.
 * <p>
 * Both tests start a secure (mTLS) CDDS monitoring provider on a local port,
 * register a monitoring service endpoint together with a monitoring production
 * and connect a monitoring service user using mutual TLS. The user discovers
 * the endpoints authorized for the authenticated service user by calling
 * getEndpoints, opens the monitoring endpoint by calling openMonitoringEndpoint
 * providing the endpoint as monitoring-endpoint-bin meta data and receives the
 * monitoring messages produced by the production. At the end of each test, the
 * parameter updates and events received by the user are verified to be equal
 * to the parameter updates and events sent by the production and the maps of
 * the received parameter updates and events are printed.
 */
public class MonitoringServiceTest {

    private static final int PROVIDER_PORT = 7667;

    final long numMonitoringMessages = 100_000;
    final long numFrMonitoringMessages = 10_000;
    final long timeout = 30_000;

    final MonitoringServiceEndpoint genericParameterEndpoint =
            MonitoringServiceUser.getMonitoringEndpoint("genericParameterEndpoint",
            "myProvider",
            "myGroundStation",
            "theSpacecraft",
            1,
            MonitoringMode.CYCLIC_MONITORING_MODE,
            MonitoringProductionNGenericParam.FIRST_PARAMETER_IDENTIFIER,
            MonitoringProductionNGenericParam.SECOND_PARAMETER_IDENTIFIER);

    final MonitoringServiceEndpoint functionalResourceParameterEndpoint =
            MonitoringServiceUser.getFrMonitoringEndpoint("functionalResourceParameterEndpoint",
            "myProvider",
            "myGroundStation",
            "theSpacecraft",
            1,
            MonitoringMode.ON_CHANGE_MONITORING_MODE,
            MonitoringProductionNFunctionalResource.FIRST_FR_PARAMETER_DESCRIPTION,
            MonitoringProductionNFunctionalResource.FIRST_FR_PARAMETER_OID,
            MonitoringProductionNFunctionalResource.SECOND_FR_PARAMETER_DESCRIPTION,
            MonitoringProductionNFunctionalResource.SECOND_FR_PARAMETER_OID,
            MonitoringProductionNFunctionalResource.FR_PARAMETER_INSTANCE,
            MonitoringProductionNFunctionalResource.FR_EVENT_DESCRIPTION,
            MonitoringProductionNFunctionalResource.FR_EVENT_OID);

    final MonitoringServiceEndpoint noParameterEndpoint =
            MonitoringServiceUser.getMonitoringEndpoint("noParameterEndpoint",
            "myProvider",
            "unAuthorizedGroundStation",
            "theSpacecraft",
            1,
            MonitoringMode.CYCLIC_MONITORING_MODE);

    private static final Logger LOG = LogManager.getLogger("cdds.monitoring.test");

    /**
     * Set once the endpoints have been printed in JSON to print the
     * endpoints only once for all tests of this class
     */
    private static boolean endpointsPrinted = false;

    @BeforeEach
    void before(TestInfo info) {
        LOG.info("START " + info.getDisplayName());
    }

    @AfterEach
    void after(TestInfo info) {
        LOG.info("END " + info.getDisplayName());
    }

    /**
     * Tests the delivery of generic monitoring parameters:
     * the user opens the cyclic monitoring endpoint genericParameterEndpoint
     * listing two generic parameters and receives numMonitoringMessages
     * monitoring messages, each message carrying the two generic parameter
     * updates produced by the {@link MonitoringProductionNGenericParam}
     * production.
     * <p>
     * The test verifies that getEndpoints returns exactly the endpoint
     * authorized for the authenticated service user and that the parameter
     * updates received by the user are equal to the parameter updates sent
     * by the production.
     */
    @Test
    public void testNGenericParameter()
            throws IOException, TimeLimitExceededException, InterruptedException, ExecutionException, TimeoutException {

        // create a monitoring provider and add a monitoring production for the endpoint
        // producing numMonitoringMessages
        final MonitoringProductionNGenericParam genericParameterProduction =
                new MonitoringProductionNGenericParam(numMonitoringMessages);
        MonitoringServiceProvider monitoringProvider = new MonitoringServiceProvider();
        monitoringProvider.addMonitoringProduction(genericParameterEndpoint, genericParameterProduction);

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{monitoringProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        monitoringProvider.addAuthorizedMonitoringEndpoint(genericParameterEndpoint);

        final MonitoringServiceUser monitoringServiceUser =
                MonitoringServiceUser.buildSecureMonitoringService("localhost", PROVIDER_PORT,
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        List<MonitoringServiceEndpoint> endpoints = monitoringServiceUser.getEndpoints(5000);

        assert(endpoints.get(0).equals(genericParameterEndpoint));

        printEndpoints(endpoints);

        // use the first provided endpoint for this test, wait for numMonitoringMessages
        monitoringServiceUser.openMonitoringEndpoint(endpoints.get(0), numMonitoringMessages);

        monitoringServiceUser.waitForMonitoringMessages(timeout);

        verifyReceivedParametersAndEvents(genericParameterProduction, monitoringServiceUser);

        monitoringServiceUser.shutdown();

        server.stop();
    }

    /**
     * Tests the delivery of Functional Resource monitoring parameters and events:
     * the user opens the on-change monitoring endpoint
     * functionalResourceParameterEndpoint listing two Functional Resource
     * parameters, one identified by its OID and one identified by its OID
     * and Functional Resource instance, and one Functional Resource event,
     * and receives numFrMonitoringMessages monitoring messages, each message
     * carrying the two Functional Resource parameter updates and the
     * Functional Resource event produced by the
     * {@link MonitoringProductionNFunctionalResource} production.
     * <p>
     * The test verifies that getEndpoints returns exactly the endpoint
     * authorized for the authenticated service user and that the parameter
     * updates and events received by the user are equal to the parameter
     * updates and events sent by the production.
     */
    @Test
    public void testNFunctionalResourceParameter()
            throws IOException, TimeLimitExceededException, InterruptedException, ExecutionException, TimeoutException {

        // create a monitoring provider and add a Functional Resource production
        // for the endpoint producing numFrMonitoringMessages
        final MonitoringProductionNFunctionalResource frParameterProduction =
                new MonitoringProductionNFunctionalResource(numFrMonitoringMessages);
        MonitoringServiceProvider monitoringProvider = new MonitoringServiceProvider();
        monitoringProvider.addMonitoringProduction(functionalResourceParameterEndpoint, frParameterProduction);

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{monitoringProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        monitoringProvider.addAuthorizedMonitoringEndpoint(functionalResourceParameterEndpoint);

        final MonitoringServiceUser monitoringServiceUser =
                MonitoringServiceUser.buildSecureMonitoringService("localhost", PROVIDER_PORT,
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        List<MonitoringServiceEndpoint> endpoints = monitoringServiceUser.getEndpoints(5000);

        assert(endpoints.get(0).equals(functionalResourceParameterEndpoint));

        printEndpoints(endpoints);

        // use the first provided endpoint for this test, wait for numFrMonitoringMessages
        monitoringServiceUser.openMonitoringEndpoint(endpoints.get(0), numFrMonitoringMessages);

        monitoringServiceUser.waitForMonitoringMessages(timeout);

        verifyReceivedParametersAndEvents(frParameterProduction, monitoringServiceUser);

        monitoringServiceUser.shutdown();

        server.stop();
    }

    /**
     * Prints the provided endpoints in JSON, only once for all tests
     * of this class
     * @param endpoints  The endpoints to print
     */
    private static void printEndpoints(List<MonitoringServiceEndpoint> endpoints) throws IOException {
        if(endpointsPrinted == false) {
            endpointsPrinted = true;

            LOG.info("Endpoints:\n" + ProtoJsonUtil.toJson(MonitoringServiceEndpointList.newBuilder()
                    .addAllEndpoints(endpoints)
                    .build()));
        }
    }

    /**
     * Verifies that the parameter updates and events received by the monitoring
     * service user are equal to the parameter updates and events sent by the
     * monitoring production and prints the maps of the received parameter
     * updates and events.
     * @param production  The monitoring production providing the endpoint data
     * @param user        The monitoring service user receiving the endpoint data
     */
    private static void verifyReceivedParametersAndEvents(final MonitoringProductionInterface production,
            final MonitoringServiceUser user) {

        assert(production.getSentParameterUpdates().equals(user.getReceivedParameterUpdates()));
        assert(production.getSentEvents().equals(user.getReceivedEvents()));

        LOG.info("Received parameter updates: " + user.getReceivedParameterUpdates());
        LOG.info("Received events: " + user.getReceivedEvents());
    }
}
