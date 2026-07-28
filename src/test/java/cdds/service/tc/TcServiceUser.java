package cdds.service.tc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import javax.naming.TimeLimitExceededException;
import javax.net.ssl.SSLException;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.CddsServiceProvider.ServiceProviderAddress;
import ccsds.cdds.v1.Telecommand.TelecommandMessage;
import ccsds.cdds.v1.Telecommand.TelecommandReport;
import ccsds.cdds.v1.Types.FrameVersion;
import ccsds.cdds.v1.Types.GvcId;
import ccsds.cdds.v1.Types.GvcIdList;
import ccsds.cdds.v1.Types.NoArg;
import ccsds.cdds.v1.tc.CddsTcService.TcServiceEndpoint;
import ccsds.cdds.v1.tc.CddsTcService.TcServiceEndpointList;
import ccsds.cdds.v1.tc.TcServiceProviderGrpc;
import ccsds.cdds.v1.tc.TcServiceProviderGrpc.TcServiceProviderStub;
import cdds.service.common.ClientMetaDataInterceptor;
import cdds.service.common.ProtoJsonUtil;
import cdds.service.common.ProviderServer;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.StreamObserver;

/**
 * Simple TC Service User to open a TC endpoint
 * and end TC messages. Allows to wait for responses.
 */
public class TcServiceUser {

    private final TcServiceProviderStub tcProviderStub;             // the provider stub to open TC streams
    private final StreamObserver<TelecommandReport> tcUserStream;   // the stream to observer TC responses
    private StreamObserver<TelecommandMessage> tcProviderStream;    // the stream to send TCs
    private final ManagedChannel channel;
    private Throwable lastError;
    private final ClientMetaDataInterceptor interceptor = new ClientMetaDataInterceptor(TcServiceAuthorization.TC_ENDPOINT_KEY);

    private final AtomicLong numReportsReceived = new AtomicLong(0);
    private volatile Logger LOG;

    /**
     * Constructs and TC service user and connects to the provider, w/o security.
     * @param host      The host of the TC provider
     * @param port      The port of the TC provider * @throws InvalidProtocolBufferException
     */
    public static TcServiceUser buildUnsecureTcServiceUser(String host, int port) throws InvalidProtocolBufferException {
        return new TcServiceUser(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
    } 

    /**
     * Creates a TC User using an mTLS channel with the given address and configuration 
     * @param address       The address of the TC provider
     * @return the created service user
     */
    public static TcServiceUser buildSecureTcService(ServiceProviderAddress address) throws SSLException, InvalidProtocolBufferException {
        return buildSecureTcService(address.getAddress(),
                                    address.getPort(),
                                    ProviderServer.resourceToFile(address.getRootCertificateFile()),
                                    ProviderServer.resourceToFile(address.getCertificateFile()),
                                    ProviderServer.resourceToFile(address.getPrivateKeyFile())); 
    } 

    /**
     * Creates a TC User using an mTLS channel with the given arguments
     * @param host                  The host of the TC provider service
     * @param port                  The port of the TC provider service
     * @param caCertificateFile     The CA certificate to verify the provider certificate
     * @param userCertificateFile   The user certificate presented to the provider
     * @param userKeyFile           The private user key for the mTLS handshake
     * @return                      The created TcServiceUser object.
     * @throws SSLException
     * @throws InvalidProtocolBufferException 
     */
    public static TcServiceUser buildSecureTcService(String host,
                                                     int port,
                                                     File caCertificateFile,
                                                     File userCertificateFile,
                                                     File userKeyFile) throws SSLException, InvalidProtocolBufferException {

        SslContext sslContext =
            GrpcSslContexts.forClient()
                .keyManager(
                    userCertificateFile,
                    userKeyFile)                    // Client cert
                .trustManager(caCertificateFile)    // Trust server cert
                .build();

        ManagedChannel channel =
            NettyChannelBuilder.forAddress(host, port)
                .sslContext(sslContext)
                .build();
    
        LogManager.getLogger().info("Secure TC Service User, host: " + host + " port: " + port + 
            " created using \n\tCA: " + caCertificateFile + "\n\tuser cert: " + userCertificateFile + "\n\tuser key: " + userKeyFile);

        return new TcServiceUser(channel);            
    }

    /** 
     * Constructs and TC service user and opens a TC stream.
     * Attaches the TC endpoint meta data to the channel.
     * @param channel       The channel to use to access the CDDS TC provider
     * @param tcEndpoint    The TC endpoint for which the stream is created
     * @throws InvalidProtocolBufferException In case the endpoint cannot be encoded
     */
    private TcServiceUser(ManagedChannel channel) throws InvalidProtocolBufferException {
        LOG = LogManager.getLogger("cdds.tc.user");

        this.channel = channel; // needed for later shutdown
        
        // this interceptor is updated on openTelecommandEndpoint with the endpoint to add as meta data
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
                synchronized(this) {
                    lastError = err;
                    this.notifyAll();
                }
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
    }

   /**
     * Requests endpoints from the CDDS provider applicable to this authenticated CDDS user.
     * @param timeoutMs                 The timeout for the request in milli seconds
     * @return                          The list of authorized endpoints, potentially an empty list
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws TimeoutException         Thrown if the request did not complete with time out
     */
    public List<TcServiceEndpoint> getEndpoints(long timeoutMs) throws InterruptedException, ExecutionException, TimeoutException {

        final List<TcServiceEndpoint> tcEndpoints = new ArrayList<>();

        CompletableFuture<List<TcServiceEndpoint>> future =
            new CompletableFuture<>();
        tcProviderStub.getEndpoints(NoArg.newBuilder().build(), new StreamObserver<TcServiceEndpointList>() {

            @Override
            public void onNext(TcServiceEndpointList value) {
                LOG.info("getEndpoints onNext");
                for(TcServiceEndpoint endpoint : value.getEndpointsList()) {
                    tcEndpoints.add(endpoint);
                }
            }

            @Override
            public void onError(Throwable t) {
                LOG.warn("getEndpoints onError");
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                LOG.info("getEndpoints onCompleted");
                future.complete(tcEndpoints);
            }
            
        });

        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Open a telecommand stream to the connected CDDS TC Provider.
     * Errors are reported by calls to onError of the tcUserStream.
     * @throws InvalidProtocolBufferException 
     */
    public void openTelecommandEndpoint(TcServiceEndpoint tcEndpoint) throws InvalidProtocolBufferException {
        LOG = LogManager.getLogger("cdds.tc.user." + TcEndpointUtil.getEndpointType(tcEndpoint) + "");

        // set the endpoint meta data into the interceptor
        interceptor.setMetaData(ProtoJsonUtil.toJsonUtf8(tcEndpoint));

        // call the gRPC method with set meta data
        tcProviderStream = tcProviderStub.openTelecommandEndpoint(tcUserStream);
        numReportsReceived.set(0);        
        LOG.info("Opened telecommand endpoint called");
    }

    /**
     * Creates a meta data header TC endpoint encoded in JSON
     * @param serviceProvider   The service provider
     * @param terminal          The terminal supporting the endpoint
     * @param serviceUser       The service user using the service endpoint
     * @param scId              The spacecraft ID 
     * @param tcVcId            The TC VC ID
     * 
     * @return                  The created TcServiceEndpoint
     */
    public static TcServiceEndpoint getTcEndpoint(String serviceProvider, String terminal, String serviceUser, int scId,
            int tcVcId) {

        return TcServiceEndpoint.newBuilder()
                .setServiceProvider(serviceProvider)
                .setTerminal(terminal)
                .setServiceUser(serviceUser)
                .setGvcIds(GvcIdList.newBuilder().addGvcId(
                    GvcId.newBuilder()
                        .setSpacecraftId(scId)
                        .setVersion(FrameVersion.USLP)
                        .setVirtualChannelId(tcVcId)
                        .build())
                    .build())
                .setServiceVersion(1)
                .build();
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
     * Stop using this service endpoint.
     * The cannel remains connected
     */
    public void stop(){
        tcProviderStream.onCompleted();
    }

    /**
     * Shuts down the communcation channel
     * @throws InterruptedException
     */
    public void shutdown() throws InterruptedException {
        channel.shutdown();
        channel.awaitTermination(5, TimeUnit.SECONDS);
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
                    LOG.info("wait for reports. " + numReportsReceived.get() + "/" + numReports + " TC reports received");
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

        LOG.info("Return from wait for reports. " + numReportsReceived.get() + "/" + numReports + " TC reports received");
        return numReportsReceived.get();   
    }

    public Throwable getLastError(long timeout) {
        synchronized(this) {
            if(lastError == null) {
                try {
                    this.wait(timeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                return lastError;
            }
        }

        return null;
    }
}
