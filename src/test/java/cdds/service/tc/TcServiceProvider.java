package cdds.service.tc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.Telecommand.TelecommandMessage;
import ccsds.cdds.Telecommand.TelecommandReport;
import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;
import ccsds.cdds.tc.TcServiceProviderGrpc.TcServiceProviderImplBase;
import io.grpc.stub.StreamObserver;

/**
 * Simple TC test server. Responds to TC radiation requests with ACK and RADIATED.
 * Report requests are responded with a report.
 */
public class TcServiceProvider extends TcServiceProviderImplBase {

    private static final Logger LOG = LogManager.getLogger("cdds.tc.provider");

    @Override
    public StreamObserver<TelecommandMessage> openTelecommandEndpoint(StreamObserver<TelecommandReport> tcUserStream) {
        
        try {
            byte[] endpointBytes = TcServiceAuthorization.TC_ENDPOINT_CTX_KEY.get();            // get the tc-endpoint-bin meta data
            TcServiceEndpoint tcEndPoint = TcEndpointUtil.tcEndpointFromJson(endpointBytes);    // decode the endpoint from JSON
            LOG.info("Open TC stream for endpoint\n" + tcEndPoint);
            // in this simple example the TC Provider has only one (static) endpoint.
            return new TcServiceEndpointStream(tcUserStream, tcEndPoint);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }

        return null;
    }

}
