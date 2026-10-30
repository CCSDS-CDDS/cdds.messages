package cdds.service.control;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ccsds.cdds.v1.Control.ControlDirective;
import ccsds.cdds.v1.Control.DirectiveReport;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpoint;
import io.grpc.stub.StreamObserver;

/**
 * Test control production not responding to control directives.
 * Each received control directive is logged, but no directive report
 * is sent, so the wait for the directive report times out at the
 * service user within the ack timeout of the control service endpoint.
 */
public class ControlProductionDirectiveNoResponse implements ControlProductionInterface {

    private static final Logger LOG = LogManager.getLogger("cdds.control.production");

    @Override
    public StreamObserver<ControlDirective> startControlEndpointService(ControlServiceEndpoint controlEndpoint,
            StreamObserver<DirectiveReport> controlUserStream) {

        LOG.info("Start control production for endpoint\n" + controlEndpoint);

        return new StreamObserver<ControlDirective>() {

            @Override
            public void onNext(ControlDirective controlDirective) {
                LOG.info("Received control directive, not responding\n" + controlDirective);
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
        return Collections.emptyList();
    }
}
