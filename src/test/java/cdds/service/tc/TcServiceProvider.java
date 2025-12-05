package cdds.service.tc;

import ccsds.cdds.Telecommand.TelecommandMessage;
import ccsds.cdds.Telecommand.TelecommandReport;
import ccsds.cdds.tc.TcServiceProviderGrpc.TcServiceProviderImplBase;
import io.grpc.stub.StreamObserver;

/**
 * Simple TC test server. Responds to TC radiation requests with ACK and RADIATED.
 * Report requests are responded with a report.
 */
public class TcServiceProvider extends TcServiceProviderImplBase {

    private final TcServiceAuthorization tcAuthorization;

    public TcServiceProvider(TcServiceAuthorization tcAuthorization) {
        this.tcAuthorization = tcAuthorization;
    }
    @Override
    public StreamObserver<TelecommandMessage> openTelecommandStream(StreamObserver<TelecommandReport> tcUserStream) {
        return new TcServiceProviderStream(tcUserStream, tcAuthorization);
    }

}
