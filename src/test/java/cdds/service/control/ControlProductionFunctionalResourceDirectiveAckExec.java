package cdds.service.control;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ccsds.cdds.v1.Control.ControlDirective;
import ccsds.cdds.v1.Control.DirectiveReport;
import ccsds.cdds.v1.Types.ProductionState;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpoint;
import io.grpc.stub.StreamObserver;

/**
 * Test control production handling Functional Resource control directives.
 * Each received Functional Resource control directive is responded with one
 * {@link ccsds.cdds.v1.Control.DirectiveResult#ACCEPTED} directive report
 * and one {@link ccsds.cdds.v1.Control.DirectiveResult#EXECUTED}
 * directive report for the invocation.
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
                    final int invocationId =
                            controlDirective.getFunctionalResourceControlDirective().getInvocationId();

                    // send the ACCEPTED directive report
                    final DirectiveReport acceptedReport = DirectiveReport.newBuilder()
                            .setProductionState(ProductionState.OPERATIONAL)
                            .setInvocationId(invocationId)
                            .setResult(ccsds.cdds.v1.Control.DirectiveResult.ACK)
                            .build();
                    controlUserStream.onNext(acceptedReport);
                    sentDirectiveReports.add(acceptedReport);

                    // send the EXECUTED directive report
                    final DirectiveReport executedReport = DirectiveReport.newBuilder()
                            .setProductionState(ProductionState.OPERATIONAL)
                            .setInvocationId(invocationId)
                            .setResult(ccsds.cdds.v1.Control.DirectiveResult.EXECUTED)
                            .build();
                    controlUserStream.onNext(executedReport);
                    sentDirectiveReports.add(executedReport);
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
     * {@inheritDoc}
     */
    @Override
    public List<DirectiveReport> getSentDirectiveReports() {
        return sentDirectiveReports;
    }
}
