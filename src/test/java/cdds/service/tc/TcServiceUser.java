package cdds.service.tc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.naming.TimeLimitExceededException;

import com.google.protobuf.InvalidProtocolBufferException;
import ccsds.cdds.Telecommand.TelecommandMessage;
import ccsds.cdds.Telecommand.TelecommandReport;
import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;
import ccsds.cdds.tc.TcServiceProviderGrpc;
import ccsds.cdds.tc.TcServiceProviderGrpc.TcServiceProviderStub;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

public class TcServiceUser {

    private final Channel channel;
    private final TcServiceProviderStub tcProviderStub;
    private final StreamObserver<TelecommandReport> tcUserStream;
    private final StreamObserver<TelecommandMessage> tcProviderStream;

    private final AtomicLong numReportsReceived = new AtomicLong(0);
    private static final Logger LOG = Logger.getLogger("CDDS TC User");

    /** 
     * Constructs and TC service user and connects to the provider.
     * @param host      The host of the TC provider
     * @param port      The port of the TC provider 
     * @throws InvalidProtocolBufferException In case the endpoint cannot be encoded
     */
    public TcServiceUser(String host, int port) throws InvalidProtocolBufferException {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();

        // add an interceptor to the client stream to attach the metadata required for the TC service:
        // - tc-endpoint-bin=<TcEndpoint>
        ClientInterceptor interceptor = MetadataUtils.newAttachHeadersInterceptor(getTcEndpoint("theProvider",
                "theStation",
                "theSpacecraft", 1));
        Channel interceptedChannel = ClientInterceptors.intercept(channel, interceptor);

        tcProviderStub = TcServiceProviderGrpc.newStub(interceptedChannel);

        tcUserStream = new StreamObserver<TelecommandReport>() {

            @Override
            public void onCompleted() {
                LOG.info("TC service user completed called");
            }

            @Override
            public void onError(Throwable err) {
                LOG.info("TC service user error called: " + err);
            }

            @Override
            public void onNext(TelecommandReport tcReport) {
                long numReceived = numReportsReceived.incrementAndGet();
                LOG.info("TC service user report " + numReceived + " received:\n" + tcReport);
                synchronized(TcServiceUser.this.numReportsReceived) {
                    TcServiceUser.this.numReportsReceived.notifyAll();
                }
            }
            
        };
        
        
        
        tcProviderStream = tcProviderStub.openTelecommandStream(tcUserStream);
    }

    /**
     * Creates a meta data header TC endpoint encoded in JSON
     * @param provider
     * @param groundStation
     * @param spacecraft 
     * @return The meta data with the encoded TC endpoint
     * @throws InvalidProtocolBufferException 
     */
    private Metadata getTcEndpoint(String provider, String groundStation, String spacecraft, long tcVcId) throws InvalidProtocolBufferException {
        Metadata spacecraftHeader = new Metadata();

        TcServiceEndpoint tcEndpoint = TcServiceEndpoint.newBuilder()
            .setProvider(provider)
            .setGroundStation(groundStation)
            .setSpacecraft(spacecraft)
            .setTcVcId(tcVcId)
            .build();
                    
        spacecraftHeader.put(TcServiceAuthorization.TC_ENDPOINT_KEY, TcEndpointJson.tcEndpointToJsonUtf8(tcEndpoint));

        return spacecraftHeader;
    }

    /**
     * Sends a TC message to the TC provider.
     * @param tc    The TC message to be sent.
     */
    public void sendTelecommand(TelecommandMessage tc) {
        LOG.info("Send TC with command ID: " + tc.getRadiationRequest().getCommandId());
        tcProviderStream.onNext(tc);
    }

    /**
     * Stop using this service instance
     */
    public void stop() {
        tcProviderStream.onCompleted();
    }

    /**
     * Waits until the given number of reports received or timeout occurs
     * @return Returns the number of received reports.
     * @throws TimeLimitExceededException 
     */
    public long waitForTcReports(long numReports) throws TimeLimitExceededException {

        final long timeout = 1000;
        long deadline = System.currentTimeMillis() + timeout; 

        synchronized(numReportsReceived) {
            while(this.numReportsReceived.get() < numReports) {
                try {
                    LOG.info("wait for " + numReports + "/" + numReportsReceived.get() + " reports");
                    this.numReportsReceived.wait(1000);
                    LOG.info("wait for TC reports returned. reports: " + numReportsReceived.get());
                } catch (InterruptedException e) {
                }
                
                if(System.currentTimeMillis() > deadline) {
                    LOG.info("Timeout, did not receive " + numReports + " within " + timeout + " ms");
                    throw new TimeLimitExceededException("Did not receive " + numReports + " TC reports within " + timeout + " ms");
                }
            }    
        }

        LOG.info("Return from wait for reports. " + numReports + "/" + numReportsReceived.get() + " TC reports received");
        return numReportsReceived.get();   
    }
}
