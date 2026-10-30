package cdds.service.control;

import java.util.List;

import ccsds.cdds.v1.Control.ControlDirective;
import ccsds.cdds.v1.Control.DirectiveReport;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpoint;
import io.grpc.stub.StreamObserver;

/**
 * A control production handles the control directives of a control service
 * endpoint and provides the directive reports. Different productions can be
 * added to the {@link ControlServiceProvider} for different endpoints.
 */
public interface ControlProductionInterface {

    /**
     * Start providing the control endpoint service. The returned stream observer
     * receives the control directives of the control service user.
     * @param controlEndpoint   The endpoint governing the provided control service
     * @param controlUserStream The user stream to send the directive reports
     * @return                  The stream observer receiving the control directives
     */
    public StreamObserver<ControlDirective> startControlEndpointService(ControlServiceEndpoint controlEndpoint,
            StreamObserver<DirectiveReport> controlUserStream);

    /**
     * Stop providing the control endpoint service
     * @param controlEndpoint   The endpoint for which provision shall be stopped
     */
    public void stopControlEndpointService(ControlServiceEndpoint controlEndpoint);

    /**
     * The directive reports sent per invocation, ordered as sent.
     * @return  The list of directive reports sent by this production
     */
    public List<DirectiveReport> getSentDirectiveReports();
}
