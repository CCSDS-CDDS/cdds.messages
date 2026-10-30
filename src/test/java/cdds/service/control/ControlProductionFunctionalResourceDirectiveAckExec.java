package cdds.service.control;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ccsds.cdds.v1.Control.ControlDirective;
import ccsds.cdds.v1.Control.DirectiveExecutionDiagnostic;
import ccsds.cdds.v1.Control.DirectiveReport;
import ccsds.cdds.v1.Control.DirectiveResult;
import ccsds.cdds.v1.Control.FunctionalResourceDirective;
import ccsds.cdds.v1.Types.ProductionState;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpoint;
import ccsds.cdds.v1.control.CddsControlService.FunctionalResourceDirectiveName;
import io.grpc.stub.StreamObserver;

/**
 * Test control production handling Functional Resource control directives.
 * Each received Functional Resource control directive authorized by the
 * endpoint, that is the directive identifier and instance number are listed
 * in the endpoint, is responded with one {@link DirectiveResult#ACK}
 * directive report and one {@link DirectiveResult#EXECUTED} directive
 * report for the invocation. Each received Functional Resource control
 * directive not authorized by the endpoint is responded with one
 * {@link DirectiveResult#REJECTED} directive report carrying the
 * {@link DirectiveExecutionDiagnostic#UNKNOWN_DIRECTIVE} diagnostic
 * for the invocation.
 */
public class ControlProductionFunctionalResourceDirectiveAckExec implements ControlProductionInterface {

    /**
     * The description of the Functional Resource directive supported by this production
     */
    public static final String FR_DIRECTIVE_DESCRIPTION = "test-functional-resource-directive";

    /**
     * The OID of the Functional Resource directive supported by this production
     */
    public static final String FR_DIRECTIVE_OID = "2.1.3.4.5.6";

    /**
     * The instance number of the Functional Resource the directive is invoked for
     */
    public static final long FR_INSTANCE = 1;

    /**
     * The description of the qualifier of the Functional Resource directive
     */
    public static final String FR_DIRECTIVE_QUALIFIER_DESCRIPTION = "test-functional-resource-directive.qualifier-0";

    /**
     * The OID of the qualifier of the Functional Resource directive
     */
    public static final String FR_DIRECTIVE_QUALIFIER_OID = "2.1.3.4.5.6.7";

    /**
     * The directive reports sent by this production, ordered as sent
     */
    private final List<DirectiveReport> sentDirectiveReports = new CopyOnWriteArrayList<>();

    private static final Logger LOG = LogManager.getLogger("cdds.control.production");

    @Override
    public StreamObserver<ControlDirective> startControlEndpointService(ControlServiceEndpoint controlEndpoint,
            StreamObserver<DirectiveReport> controlUserStream) {

        LOG.info("Start control production for endpoint\n" + controlEndpoint);

        return new StreamObserver<ControlDirective>() {

            @Override
            public void onNext(ControlDirective controlDirective) {
                LOG.info("Received control directive\n" + controlDirective);

                if(controlDirective.hasFunctionalResourceControlDirective()) {
                    final FunctionalResourceDirective frDirective =
                            controlDirective.getFunctionalResourceControlDirective();
                    final int invocationId = frDirective.getInvocationId();

                    if(isDirectiveAuthorized(controlEndpoint, frDirective)) {

                        // send the ACK directive report
                        final DirectiveReport acceptedReport = DirectiveReport.newBuilder()
                                .setProductionState(ProductionState.OPERATIONAL)
                                .setInvocationId(invocationId)
                                .setResult(DirectiveResult.ACK)
                                .build();
                        controlUserStream.onNext(acceptedReport);
                        sentDirectiveReports.add(acceptedReport);

                        // send the EXECUTED directive report
                        final DirectiveReport executedReport = DirectiveReport.newBuilder()
                                .setProductionState(ProductionState.OPERATIONAL)
                                .setInvocationId(invocationId)
                                .setResult(DirectiveResult.EXECUTED)
                                .build();
                        controlUserStream.onNext(executedReport);
                        sentDirectiveReports.add(executedReport);
                    } else {
                        LOG.warn("Directive " + frDirective.getFunctionalResourceDirectiveIdentifier()
                                + " instance " + frDirective.getFunctionalResourceInstance()
                                + " not authorized by the endpoint");

                        // send the REJECTED directive report
                        final DirectiveReport rejectedReport = DirectiveReport.newBuilder()
                                .setProductionState(ProductionState.OPERATIONAL)
                                .setInvocationId(invocationId)
                                .setResult(DirectiveResult.REJECTED)
                                .setDiagnostic(DirectiveExecutionDiagnostic.UNKNOWN_DIRECTIVE)
                                .build();
                        controlUserStream.onNext(rejectedReport);
                        sentDirectiveReports.add(rejectedReport);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                LOG.warn("Control production error called: " + t);
            }

            @Override
            public void onCompleted() {
                LOG.info("Control production completed called");
                controlUserStream.onCompleted();
            }
        };
    }

    @Override
    public void stopControlEndpointService(ControlServiceEndpoint controlEndpoint) {
    }

    /**
     * Checks if the given Functional Resource directive is authorized by
     * the endpoint: the directive OID arcs match one of the Functional
     * Resource directive OIDs of the endpoint and, if the endpoint names a
     * Functional Resource instance, the directive instance number matches
     * the instance number of the endpoint.
     * @param controlEndpoint   The control endpoint authorizing the directive
     * @param frDirective       The invoked Functional Resource directive
     * @return                  true if the directive is authorized by the endpoint
     */
    private static boolean isDirectiveAuthorized(ControlServiceEndpoint controlEndpoint,
            FunctionalResourceDirective frDirective) {

        for(final FunctionalResourceDirectiveName directiveName :
                controlEndpoint.getFunctionalResourceDirectivesList()) {

            if(frDirective.getFunctionalResourceDirectiveIdentifier().getArcsList()
                    .equals(directiveName.getFunctionalResourceDirective().getArcsList())
                    && (directiveName.hasFunctionalResourceInstance() == false
                        || directiveName.getFunctionalResourceInstance()
                            == frDirective.getFunctionalResourceInstance())) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DirectiveReport> getSentDirectiveReports() {
        return sentDirectiveReports;
    }
}
