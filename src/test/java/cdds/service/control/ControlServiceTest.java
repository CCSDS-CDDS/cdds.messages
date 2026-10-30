package cdds.service.control;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.naming.TimeLimitExceededException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import ccsds.cdds.v1.Control.ControlDirective;
import ccsds.cdds.v1.Control.DirectiveExecutionDiagnostic;
import ccsds.cdds.v1.Control.DirectiveReport;
import ccsds.cdds.v1.Control.DirectiveResult;
import ccsds.cdds.v1.Control.FunctionalResourceDirective;
import ccsds.cdds.v1.Control.GenericControlDirective;
import ccsds.cdds.v1.Control.GenericQualifier;
import ccsds.cdds.v1.Control.QualifierValue;
import ccsds.cdds.v1.Types.ObjectIdentifier;
import ccsds.cdds.v1.Types.Value;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpoint;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProviderServer;

/**
 * Test the communication among a CDDS control service user and a CDDS
 * control service provider using the gRPC ControlServiceProvider service.
 * <p>
 * Both tests start a secure (mTLS) CDDS control provider on a local port,
 * register a control service endpoint together with a control production
 * and connect a control service user using mutual TLS. The user discovers
 * the endpoints authorized for the authenticated service user by calling
 * getEndpoints, opens the control endpoint by calling openControlEndpoint
 * providing the endpoint as control-endpoint-bin meta data and sends one
 * control directive. Each test verifies that the user receives two directive
 * reports for the directive invocation, one reporting ACCEPTED and one
 * reporting EXECUTED, and that the directive reports received by the user
 * are equal to the directive reports sent by the production.
 */
public class ControlServiceTest {

    private static final int PROVIDER_PORT = 7668;

    /**
     * The identifier of a generic control directive not authorized by the generic directive endpoint
     */
    private static final String UNAUTHORIZED_DIRECTIVE_ID = "test-generic-directive-unauthorized";

    final long timeout = 30_000;

    final ControlServiceEndpoint genericDirectiveEndpoint =
            ControlServiceUser.getControlEndpoint("genericDirectiveEndpoint",
            "myProvider",
            "myGroundStation",
            "theSpacecraft",
            ControlProductionGenericDirectiveAckExec.FIRST_DIRECTIVE_ID,
            ControlProductionGenericDirectiveAckExec.SECOND_DIRECTIVE_ID);

    final ControlServiceEndpoint functionalResourceDirectiveEndpoint =
            ControlServiceUser.getFrControlEndpoint("functionalResourceDirectiveEndpoint",
            "myProvider",
            "myGroundStation",
            "theSpacecraft",
            ControlProductionFunctionalResourceDirectiveAckExec.FR_DIRECTIVE_DESCRIPTION,
            ControlProductionFunctionalResourceDirectiveAckExec.FR_DIRECTIVE_OID,
            ControlProductionFunctionalResourceDirectiveAckExec.FR_INSTANCE);

    private static final Logger LOG = LogManager.getLogger("cdds.control.test");

    @BeforeEach
    void before(TestInfo info) {
        LOG.info("START " + info.getDisplayName());
    }

    @AfterEach
    void after(TestInfo info) {
        LOG.info("END " + info.getDisplayName());
    }

    /**
     * Tests the invocation of a generic control directive:
     * the user opens the control endpoint genericDirectiveEndpoint
     * listing two generic directive identifiers and sends one
     * GenericControlDirective carrying two directive qualifiers.
     * The {@link ControlProductionGenericDirectiveAckExec}
     * production responds with two directive reports for the directive
     * invocation, one ACCEPTED and one EXECUTED.
     * <p>
     * The test verifies that getEndpoints returns exactly the endpoint
     * authorized for the authenticated service user, that two directive
     * reports arrive, one for ACCEPTED and one for EXECUTED, and that the
     * directive reports received by the user are equal to the directive
     * reports sent by the production.
     */
    @Test
    public void testGenericControlDirective()
            throws IOException, TimeLimitExceededException, InterruptedException,
            ExecutionException, TimeoutException {

        // create a control provider and add a control production for the endpoint
        final ControlProductionGenericDirectiveAckExec genericDirectiveProduction =
                new ControlProductionGenericDirectiveAckExec();
        ControlServiceProvider controlProvider = new ControlServiceProvider();
        controlProvider.addControlProduction(genericDirectiveEndpoint, genericDirectiveProduction);

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{controlProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        controlProvider.addAuthorizedControlEndpoint(genericDirectiveEndpoint);

        final ControlServiceUser controlServiceUser =
                ControlServiceUser.buildSecureControlService("localhost", PROVIDER_PORT,
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        List<ControlServiceEndpoint> endpoints = controlServiceUser.getEndpoints(5000);

        assert(endpoints.get(0).equals(genericDirectiveEndpoint));

        // use the first provided endpoint for this test, wait for two directive reports
        controlServiceUser.openControlEndpoint(endpoints.get(0), 2);

        final int invocationId = controlServiceUser.nextInvocationId();

        controlServiceUser.sendControlDirective(ControlDirective.newBuilder()
                .setGenericControlDirective(GenericControlDirective.newBuilder()
                        .setInvocationId(invocationId)
                        .setDirectiveId(ControlProductionGenericDirectiveAckExec.FIRST_DIRECTIVE_ID)
                        .addDirectiveQualifiers(GenericQualifier.newBuilder()
                                .setQualifierId(ControlProductionGenericDirectiveAckExec.FIRST_DIRECTIVE_QUALIFIER_ID)
                                .setQualifierValue(Value.newBuilder()
                                        .setStringValue("east")
                                        .build())
                                .build())
                        .addDirectiveQualifiers(GenericQualifier.newBuilder()
                                .setQualifierId(ControlProductionGenericDirectiveAckExec.SECOND_DIRECTIVE_QUALIFIER_ID)
                                .setQualifierValue(Value.newBuilder()
                                        .setDoubleValue(47.11)
                                        .build())
                                .build())
                        .build())
                .build());

        controlServiceUser.waitForDirectiveReports(timeout);

        verifyReceivedDirectiveReports(genericDirectiveProduction, controlServiceUser, invocationId);

        controlServiceUser.stop();
        controlServiceUser.shutdown();

        server.stop();
    }

    /**
     * Tests the invocation of a Functional Resource control directive:
     * the user opens the control endpoint functionalResourceDirectiveEndpoint
     * listing one Functional Resource directive identified by its OID and
     * instance number and sends one FunctionalResourceDirective carrying one
     * qualifier value. The
     * {@link ControlProductionFunctionalResourceDirectiveAckExec} production
     * responds with two directive reports for the directive invocation,
     * one ACCEPTED and one EXECUTED.
     * <p>
     * The test verifies that getEndpoints returns exactly the endpoint
     * authorized for the authenticated service user, that two directive
     * reports arrive, one for ACCEPTED and one for EXECUTED, and that the
     * directive reports received by the user are equal to the directive
     * reports sent by the production.
     */
    @Test
    public void testFunctionalResourceControlDirective()
            throws IOException, TimeLimitExceededException, InterruptedException,
            ExecutionException, TimeoutException {

        // create a control provider and add a control production for the endpoint
        final ControlProductionFunctionalResourceDirectiveAckExec frDirectiveProduction =
                new ControlProductionFunctionalResourceDirectiveAckExec();
        ControlServiceProvider controlProvider = new ControlServiceProvider();
        controlProvider.addControlProduction(functionalResourceDirectiveEndpoint, frDirectiveProduction);

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{controlProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        controlProvider.addAuthorizedControlEndpoint(functionalResourceDirectiveEndpoint);

        final ControlServiceUser controlServiceUser =
                ControlServiceUser.buildSecureControlService("localhost", PROVIDER_PORT,
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        List<ControlServiceEndpoint> endpoints = controlServiceUser.getEndpoints(5000);

        assert(endpoints.get(0).equals(functionalResourceDirectiveEndpoint));

        // use the first provided endpoint for this test, wait for two directive reports
        controlServiceUser.openControlEndpoint(endpoints.get(0), 2);

        final int invocationId = controlServiceUser.nextInvocationId();

        controlServiceUser.sendControlDirective(ControlDirective.newBuilder()
                .setFunctionalResourceControlDirective(FunctionalResourceDirective.newBuilder()
                        .setInvocationId(invocationId)
                        .setFunctionalResourceDirectiveIdentifier(createObjectIdentifier(
                                ControlProductionFunctionalResourceDirectiveAckExec.FR_DIRECTIVE_DESCRIPTION,
                                ControlProductionFunctionalResourceDirectiveAckExec.FR_DIRECTIVE_OID))
                        .setFunctionalResourceInstance(
                                ControlProductionFunctionalResourceDirectiveAckExec.FR_INSTANCE)
                        .addQualifierValues(QualifierValue.newBuilder()
                                .setQualifierId(createObjectIdentifier(
                                        ControlProductionFunctionalResourceDirectiveAckExec.FR_DIRECTIVE_QUALIFIER_DESCRIPTION,
                                        ControlProductionFunctionalResourceDirectiveAckExec.FR_DIRECTIVE_QUALIFIER_OID))
                                .setEncodedQualifierValue(Value.newBuilder()
                                        .setBoolValue(true)
                                        .build()
                                        .toByteString())
                                .build())
                        .build())
                .build());

        controlServiceUser.waitForDirectiveReports(timeout);

        verifyReceivedDirectiveReports(frDirectiveProduction, controlServiceUser, invocationId);

        controlServiceUser.stop();
        controlServiceUser.shutdown();

        server.stop();
    }

    /**
     * Tests the directive report timeout when the production is not
     * responding: the user opens the control endpoint genericDirectiveEndpoint
     * with the {@link ControlProductionDirectiveNoResponse} production not
     * responding to control directives and sends one GenericControlDirective.
     * <p>
     * The test verifies that no directive report is received within the ack
     * timeout specified in the endpoint and that waiting for the directive
     * report fails with a timeout.
     */
    @Test
    public void testDirectiveAckTimeout()
            throws IOException, TimeLimitExceededException, InterruptedException,
            ExecutionException, TimeoutException {

        // create a control provider and add the not responding production for the endpoint
        final ControlProductionDirectiveNoResponse noResponseProduction =
                new ControlProductionDirectiveNoResponse();
        ControlServiceProvider controlProvider = new ControlServiceProvider();
        controlProvider.addControlProduction(genericDirectiveEndpoint, noResponseProduction);

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{controlProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        controlProvider.addAuthorizedControlEndpoint(genericDirectiveEndpoint);

        final ControlServiceUser controlServiceUser =
                ControlServiceUser.buildSecureControlService("localhost", PROVIDER_PORT,
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        List<ControlServiceEndpoint> endpoints = controlServiceUser.getEndpoints(5000);

        assert(endpoints.get(0).equals(genericDirectiveEndpoint));

        // use the first provided endpoint for this test, wait for one directive report
        controlServiceUser.openControlEndpoint(endpoints.get(0), 1);

        final int invocationId = controlServiceUser.nextInvocationId();

        controlServiceUser.sendControlDirective(ControlDirective.newBuilder()
                .setGenericControlDirective(GenericControlDirective.newBuilder()
                        .setInvocationId(invocationId)
                        .setDirectiveId(ControlProductionGenericDirectiveAckExec.FIRST_DIRECTIVE_ID)
                        .build())
                .build());

        // the production does not respond: the wait for the directive report
        // must fail with a timeout within the ack timeout of the endpoint
        final long ackTimeout = endpoints.get(0).getAckTimeout() * 1000;

        boolean timedOut = false;
        try {
            controlServiceUser.waitForDirectiveReports(ackTimeout);
        } catch(TimeLimitExceededException expected) {
            timedOut = true;
        }
        assert(timedOut);

        assert(controlServiceUser.getReceivedDirectiveReports().isEmpty());
        assert(noResponseProduction.getSentDirectiveReports().isEmpty());

        controlServiceUser.stop();
        controlServiceUser.shutdown();

        server.stop();
    }

    /**
     * Tests the invocation of a generic control directive not authorized
     * by the endpoint: the user opens the control endpoint
     * genericDirectiveEndpoint listing two generic directive identifiers
     * and sends one GenericControlDirective with a directive identifier
     * not provided by the endpoint. The
     * {@link ControlProductionGenericDirectiveAckExec} production checks
     * the directive against the endpoint and responds with one REJECTED
     * directive report carrying the UNKNOWN_DIRECTIVE diagnostic for
     * the invocation.
     * <p>
     * The test verifies that getEndpoints returns exactly the endpoint
     * authorized for the authenticated service user, that one directive
     * report arrives carrying the REJECTED result and the UNKNOWN_DIRECTIVE
     * diagnostic for the directive invocation, and that the directive
     * report received by the user is equal to the directive report sent
     * by the production.
     */
    @Test
    public void testUnauthorizedDirectiveRejected()
            throws IOException, TimeLimitExceededException, InterruptedException,
            ExecutionException, TimeoutException {

        // create a control provider and add a control production for the endpoint
        final ControlProductionGenericDirectiveAckExec genericDirectiveProduction =
                new ControlProductionGenericDirectiveAckExec();
        ControlServiceProvider controlProvider = new ControlServiceProvider();
        controlProvider.addControlProduction(genericDirectiveEndpoint, genericDirectiveProduction);

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{controlProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        controlProvider.addAuthorizedControlEndpoint(genericDirectiveEndpoint);

        final ControlServiceUser controlServiceUser =
                ControlServiceUser.buildSecureControlService("localhost", PROVIDER_PORT,
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        List<ControlServiceEndpoint> endpoints = controlServiceUser.getEndpoints(5000);

        assert(endpoints.get(0).equals(genericDirectiveEndpoint));

        // use the first provided endpoint for this test, wait for one directive report
        controlServiceUser.openControlEndpoint(endpoints.get(0), 1);

        final int invocationId = controlServiceUser.nextInvocationId();

        controlServiceUser.sendControlDirective(ControlDirective.newBuilder()
                .setGenericControlDirective(GenericControlDirective.newBuilder()
                        .setInvocationId(invocationId)
                        .setDirectiveId(UNAUTHORIZED_DIRECTIVE_ID)
                        .build())
                .build());

        controlServiceUser.waitForDirectiveReports(timeout);

        final List<DirectiveReport> receivedReports = controlServiceUser.getReceivedDirectiveReports();

        assert(receivedReports.size() == 1);
        assert(receivedReports.get(0).getResult() == DirectiveResult.REJECTED);
        assert(receivedReports.get(0).getDiagnostic() == DirectiveExecutionDiagnostic.UNKNOWN_DIRECTIVE);
        assert(receivedReports.get(0).getInvocationId() == invocationId);

        assert(genericDirectiveProduction.getSentDirectiveReports().equals(receivedReports));

        LOG.info("Received rejected directive report for directive '" + UNAUTHORIZED_DIRECTIVE_ID + "'");

        controlServiceUser.stop();
        controlServiceUser.shutdown();

        server.stop();
    }

    /**
     * Verifies that two directive reports arrived, one for ACCEPTED and one
     * for EXECUTED, both carrying the invocation identifier of the invoked
     * directive, and that the directive reports received by the control
     * service user are equal to the directive reports sent by the production.
     * @param production     The control production providing the directive reports
     * @param user           The control service user receiving the directive reports
     * @param invocationId   The invocation identifier of the invoked directive
     */
    private static void verifyReceivedDirectiveReports(final ControlProductionInterface production,
            final ControlServiceUser user, final int invocationId) {

        final List<DirectiveReport> receivedReports = user.getReceivedDirectiveReports();

        assert(receivedReports.size() == 2);
        assert(receivedReports.get(0).getResult() == DirectiveResult.ACK);
        assert(receivedReports.get(1).getResult() == DirectiveResult.EXECUTED);
        assert(receivedReports.get(0).getInvocationId() == invocationId);
        assert(receivedReports.get(1).getInvocationId() == invocationId);

        assert(production.getSentDirectiveReports().equals(receivedReports));

        LOG.info("Received directive reports: " + receivedReports.size());
    }

    /**
     * Creates an ObjectIdentifier for the given OID string in dotted notation.
     * @param description  The description of the identified object
     * @param oidString    The OID string in dotted notation
     * @return             The created ObjectIdentifier
     */
    private static ObjectIdentifier createObjectIdentifier(String description, String oidString) {
        final ObjectIdentifier.Builder oid = ObjectIdentifier.newBuilder()
                .setOidString(description);

        for (final String arc : oidString.split("\\.")) {
            oid.addArcs(Long.parseLong(arc));
        }

        return oid.build();
    }
}
