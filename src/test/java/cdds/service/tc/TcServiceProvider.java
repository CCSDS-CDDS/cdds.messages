package cdds.service.tc;

import java.util.logging.Logger;

import ccsds.cdds.Telecommand.TelecommandMessage;
import ccsds.cdds.Telecommand.TelecommandReport;
import ccsds.cdds.tc.TcServiceProviderGrpc.TcServiceProviderImplBase;
import io.grpc.stub.StreamObserver;

/**
 * Simple TC test server. Responds to TC radiation requests with ACK and RADIATED.
 * Report requests are responded with a report.
 */
public class TcServiceProvider extends TcServiceProviderImplBase {

    private static final Logger LOG = Logger.getLogger("CDDS TC Provider");

    @Override
    public StreamObserver<TelecommandMessage> openTelecommandStream(StreamObserver<TelecommandReport> tcUserStream) {
        String spacecraft = TcServiceAuthorization.SPACECRAFT_CTX_KEY.get();
        LOG.info("Open TC stream for TC user " + spacecraft);
        return new TcServiceProviderStream(tcUserStream);
    }

}
