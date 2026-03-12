package cdds.service.tm;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import javax.naming.TimeLimitExceededException;
import javax.net.ssl.SSLException;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.Telemetry.TelemetryData;
import ccsds.cdds.Telemetry.TelemetryItem;
import ccsds.cdds.Telemetry.TelemetryMessage;
import ccsds.cdds.Types.EnumQoS;
import ccsds.cdds.Types.FrameVersion;
import ccsds.cdds.Types.GvcId;
import ccsds.cdds.Types.GvcIdList;
import ccsds.cdds.Types.NoArg;
import ccsds.cdds.tm.CddsTmService.TmServiceEndpoint;
import ccsds.cdds.tm.TmServiceProviderGrpc;
import ccsds.cdds.tm.TmServiceProviderGrpc.TmServiceProviderStub;
import cdds.service.common.ProtoJsonUtil;
import cdds.service.tc.TcEndpointUtil;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;


/**
 * Simple TM Service User to open a TM endpoint
 * and end TM messages. Allows to wait for responses.
 */
public class TmServiceUser {

    private final TmServiceProviderStub tmProviderStub;             // the provider stub to open TM streams
    private final StreamObserver<TelemetryMessage> tmUserStream;   // the stream to observer TM messages received
    private final ManagedChannel channel;
    private Throwable lastError;

    private final AtomicLong numFramesReceived = new AtomicLong(0);
    private final AtomicLong numFramesExpected = new AtomicLong(0);
    private final AtomicLong numSyncNotifyReceived = new AtomicLong(0);
    private final AtomicLong numSyncNotifyExpected = new AtomicLong(0);

    private volatile long firstMessageTime = 0;
    private volatile long lastMessageTime = 0;
    private volatile int frameLength = 0;
    private volatile int protoTmLength = 0;

    private final Logger LOG;

    /**
     * Constructs and TM service user and connects to the provider, w/o security.
     * @param host      The host of the TM provider
     * @param port      The port of the TM provider * @throws InvalidProtocolBufferException
     */
    public static TmServiceUser buildUnsecureTmServiceUser(String host, int port, TmServiceEndpoint tmEndpoint) throws InvalidProtocolBufferException {
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .directExecutor() // improves performance by factor three
            .build();

        return new TmServiceUser(channel, tmEndpoint);
    } 

    /**
     * Created a TM User using an mTLS channel with the given arguments
     * @param host                  The host of the TM provider service
     * @param port                  The port of the TM provider service
     * @param tmEndpoint            The TM endpoint for this TM service user
     * @param caCertificateFile     The CA certificate to verify the provider certificate
     * @param userCertificateFile   The user certificate presented to the provider
     * @param userKeyFile           The private user key for the mTLS handshake
     * @return                      The created TcServiceUser object.
     * @throws SSLException
     * @throws InvalidProtocolBufferException 
     */
    public static TmServiceUser buildSecureTmService(String host, int port, TmServiceEndpoint tmEndpoint,
            File caCertificateFile, File userCertificateFile, File userKeyFile)
            throws SSLException, InvalidProtocolBufferException {

        SslContext sslContext =
            GrpcSslContexts.forClient()
                .keyManager(
                    userCertificateFile,
                    userKeyFile)                    // Client cert
                .trustManager(caCertificateFile)    // Trust server cert
                .build();

        ManagedChannel channel =
            NettyChannelBuilder.forAddress(host, port)
                //.eventLoopGroup(new NioEventLoopGroup(4))
                //.channelType(NioSocketChannel.class)                
                //.flowControlWindow(16 * 1024 * 1024)
                //.withOption(ChannelOption.TCP_NODELAY, true)
                //.withOption(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 32 * 1024 * 1024)
                //.withOption(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 8 * 1024 * 1024)
                .directExecutor() // improves performance by factor three
                .sslContext(sslContext)
                .build();
    
        LogManager.getLogger().info("Secure TM Service User, host: " + host + " port: " + port + 
            " created using \n\tCA: " + caCertificateFile + "\n\tuser cert: " + userCertificateFile + "\n\tuser key: " + userKeyFile);

        return new TmServiceUser(channel, tmEndpoint);            
    }

    /** 
     * Constructs and TM service user and opens a TM stream.
     * Attaches the TM endpoint meta data to the channel.
     * @param channel       The channel to use to access the CDDS TM provider
     * @param tmEndpoint    The TM endpoint for which the stream is created
     * @throws InvalidProtocolBufferException In case the endpoint cannot be encoded
     */
    private TmServiceUser(ManagedChannel channel, TmServiceEndpoint tmEndpoint) throws InvalidProtocolBufferException {
        LOG = LogManager.getLogger("cdds.tm.user." + TcEndpointUtil.getGvcId(tmEndpoint.getGvcIds()) + "");

        this.channel = channel; // needed for later shutdown

        // add an interceptor to the client stream to attach the metadata required for the TM service:
        // - tm-endpoint-bin=<TmEndpoint>
        ClientInterceptor interceptor = MetadataUtils.newAttachHeadersInterceptor(getTmEndpointMetaData(tmEndpoint));
        
        Channel interceptedChannel = ClientInterceptors.intercept(channel, interceptor);
        
        tmProviderStub = TmServiceProviderGrpc.newStub(interceptedChannel);

        tmUserStream = new StreamObserver<TelemetryMessage>() {

            @Override
            public void onNext(TelemetryMessage tmMessage) {

                if(tmMessage.getTelemetryItemsCount() > 0) {                     
                    for(TelemetryItem tmItem : tmMessage.getTelemetryItemsList()) {
                       if(tmItem.hasTelemetry()) {
                            onTelemetryData(tmItem.getTelemetry());
                       }
                    }
                } else if(tmMessage.hasTelemetry()) {
                    onTelemetryData(tmMessage.getTelemetry());
                } else if(tmMessage.hasSyncNotify()) {
                    if(firstMessageTime == 0) {
                        firstMessageTime = System.nanoTime();
                    }
                    
                    long numSyncNotifies = TmServiceUser.this.numSyncNotifyReceived.incrementAndGet(); 
                    LOG.info("TM service user received " + numSyncNotifies + " sync notifies:\n" + tmMessage);

                    if(numSyncNotifies >= TmServiceUser.this.numSyncNotifyReceived.get()) {
                        lastMessageTime = System.nanoTime();
                        synchronized(TmServiceUser.this.numSyncNotifyReceived) {
                            TmServiceUser.this.numSyncNotifyReceived.notifyAll();
                        }
                    }
                } else {
                   LOG.info("TM service user received invalid TM message (not a frame or sync notify):\n" + tmMessage); 
                }
            }

            /**
             * Handle a TelemetryData message. 
             * @param tmData
             */
            private void onTelemetryData(TelemetryData tmData) {
                   if(firstMessageTime == 0) {
                        firstMessageTime = System.nanoTime();
                        frameLength = tmData.getData().size(); // assume fix frame length, read only once
                        protoTmLength = tmData.getSerializedSize();
                    }

                    long numFrames = TmServiceUser.this.numFramesReceived.incrementAndGet();
                    if(numFrames >= TmServiceUser.this.numFramesExpected.get()) {
                        lastMessageTime = System.nanoTime(); 
                        synchronized(TmServiceUser.this.numFramesReceived) {
                            TmServiceUser.this.numFramesReceived.notifyAll();
                        }
                    }

                    if(LOG.isDebugEnabled()) { 
                        LOG.debug("TM service user received " + numFrames + " frames:\n" + tmData);
                    }

            }

            @Override
            public void onError(Throwable err) {
               LOG.info("TM service user error called: " + err);
                synchronized(this) {
                    lastError = err;
                    this.notifyAll();
                }
            }

            @Override
            public void onCompleted() {
                LOG.info("TM service user completed called\"");
            }
        };
    }

    /**
     * Open a telecommand stream to the connected CDDS TM Provider.
     * Errors are reported by calls to onError of the tcUserStream.
     * Call waitFor<TmFrames|SynNotify> to wait until reception.
     * @param   numExpectedFrames       The number of expected frames
     * @param   numExpectedSyncNotify   The number of expected sync notifies     
     */
    public void openTelemetryEndpoint(long numExpectedFrames, long numExpectedSyncNotify) {
        this.numFramesExpected.set(numExpectedFrames);
        this.numSyncNotifyExpected.set(numExpectedSyncNotify);

        tmProviderStub.openTelemetryEndpoint(NoArg.newBuilder().build(), tmUserStream);
        LOG.info("Opened telemetry endpoint called");
    }

    /**
     * Creates a meta data header TM endpoint encoded in JSON
     * @param serviceProvider   The service provider
     * @param terminal          The terminal supporting the endpoint
     * @param serviceUser       The service user using the service endpoint
     * @param scId              The spacecraft ID
     * @param frameVersion      The frame version of the endpoint 
     * @param tcVcId            The TM VC ID
     * 
     * @return                  The created TmServiceEndpoint
     */
    public static TmServiceEndpoint getTmEndpoint(String serviceProvider, String terminal, String serviceUser, int scId, FrameVersion frameVersion,
            int tmVcId) {

        return TmServiceEndpoint.newBuilder()
                .setServiceProvider(serviceProvider)
                .setTerminal(terminal)
                .setServiceUser(serviceUser)
                .setGvcIds(GvcIdList.newBuilder().addGvcId(
                    GvcId.newBuilder()
                        .setSpacecraftId(scId)
                        .setVersion(frameVersion)
                        .setVirtualChannelId(tmVcId)
                        .build())
                    .build())
                .setQos(EnumQoS.EXACTLY_ONCE)
                .setServiceVersion(1)
                .build();
    }

    /**
     * Creates a meta data header TM endpoint encoded in JSON
     * @param tmEndpoint The TM endpoint
     * @return The meta data with the encoded TM endpoint
     * @throws InvalidProtocolBufferException 
     */
    private Metadata getTmEndpointMetaData(TmServiceEndpoint tmEndpoint) throws InvalidProtocolBufferException {
        Metadata tmEndpointMetaData = new Metadata();

        tmEndpointMetaData.put(TmServiceAuthorization.TM_ENDPOINT_KEY, ProtoJsonUtil.toJsonUtf8(tmEndpoint));

        return tmEndpointMetaData;
    }

    /**
     * Wait for a number of TM frames received or timeout
     * @param numFrames The number of expected frames
     * @param timeout   The timeout in milli seconds to wait for the messages
     * @return  The number of received frames
     * @throws TimeLimitExceededException
     */
    public long waitForTmFrames(long timeout) throws TimeLimitExceededException {
        return waitForTmMessages(numFramesExpected.get(), this.numFramesReceived, timeout, "TM frames");
    }

    /**
     * Wait for a number of TM Sync Notifies received or timeout
     * @param numSyncNotify The number of expected sync notifies
     * @param timeout   The timeout in milli seconds to wait for the messages
     * @return  The number of received frames
     * @throws TimeLimitExceededException
     */
    public long waitForSyncNotify(long timeout) throws TimeLimitExceededException {
        return waitForTmMessages(numSyncNotifyExpected.get(), this.numSyncNotifyReceived, timeout, "TM sync notifies");
    }

    /**
     * Waits until the given number of frames received or timeout occurs
     * @return Returns the number of received reports.
     * @throws TimeLimitExceededException 
     */
    private long waitForTmMessages(long numExpectedMessages, AtomicLong numReceivedTmMessages, long timeout, String messageType) throws TimeLimitExceededException {

        long deadline = System.currentTimeMillis() + timeout + 200; // 200: a margin

        synchronized(numReceivedTmMessages) {
            while(numReceivedTmMessages.get() < numExpectedMessages) {
                try {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("wait for " + numFramesReceived.get() + "/" + numExpectedMessages + " " + messageType + " received");
                    }
                    numReceivedTmMessages.wait(500);
                    System.out.print("\rReceived: " + numReceivedTmMessages.get() 
                        + " / " + numExpectedMessages + " messages");
                    System.out.flush();
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("wait for TM frames returned. Frames: " + numReceivedTmMessages.get());
                    }
                } catch (InterruptedException e) {
                }
                
                if(System.currentTimeMillis() > deadline) {
                    LOG.info("Timeout, did not receive " + numExpectedMessages + " within " + timeout + " ms");
                    throw new TimeLimitExceededException("Did not receive " + numExpectedMessages + " TM messages within " + timeout + " ms");
                }
            }   
            System.out.println();
        }

        final double duration = (lastMessageTime - firstMessageTime) / 1E9;

        final double fps = (numReceivedTmMessages.get() / duration);
        final double bitRate = (fps * frameLength * 8) / 1E6;

        LOG.info("Return from wait for TM messages.\n\t" + numReceivedTmMessages.get() + "/" + numExpectedMessages 
            + " TM messages received, within " + duration + "s"
            + "\n\t{}k frames/s"
            + "\n\t{} Mbit/s (frame length: " + frameLength + ", proto length: " + protoTmLength + ")"
            , String.format("%.2f", (fps/1000)), String.format("%.2f", bitRate));
        
            return numReceivedTmMessages.get();   
    }

    /**
     * Stop using this service endpoint
     */
    public void stop() {
        // TODO how to tell the endpoint at the provider to stop delivering data?
    }

    /**
     * Shuts down the communcation channel
     * @throws InterruptedException
     */
    public void shutdown() throws InterruptedException {
        channel.shutdown();
        channel.awaitTermination(5, TimeUnit.SECONDS);
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
