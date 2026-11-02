package cdds.service.td;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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

import ccsds.cdds.v1.Tracking.TrackingDataMessage;
import ccsds.cdds.v1.Types.Duration;
import ccsds.cdds.v1.Types.EnumQoS;
import ccsds.cdds.v1.Types.NoArg;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataFormat;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpoint;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpointList;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataType;
import ccsds.cdds.v1.td.TrackingDataServiceProviderGrpc;
import ccsds.cdds.v1.td.TrackingDataServiceProviderGrpc.TrackingDataServiceProviderStub;
import cdds.service.common.ClientMetaDataInterceptor;
import cdds.service.common.ProtoJsonUtil;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.StreamObserver;

/**
 * Simple Tracking Data Service User to open a tracking data endpoint
 * and receive tracking data messages. Allows to wait for responses.
 */
public class TrackingDataServiceUser {

    private final TrackingDataServiceProviderStub tdProviderStub;          // the provider stub to open tracking data streams
    private final StreamObserver<TrackingDataMessage> tdUserStream;       // the stream to observe tracking data messages received
    private final ManagedChannel channel;
    private Throwable lastError;
    private final ClientMetaDataInterceptor interceptor =
            new ClientMetaDataInterceptor(TrackingDataServiceAuthorization.TD_ENDPOINT_KEY);

    private final AtomicLong numTdMessagesReceived = new AtomicLong(0);
    private final AtomicLong numTdMessagesExpected = new AtomicLong(0);

    /**
     * The tracking data messages received from the tracking data provider
     */
    private final List<TrackingDataMessage> receivedTrackingDataMessages =
            Collections.synchronizedList(new ArrayList<>());

    private volatile long firstMessageTime = 0;
    private volatile long lastMessageTime = 0;
    private volatile int protoTdLength = 0;

    private volatile Logger LOG;

    /**
     * Creates a tracking data service user using an mTLS channel with the given arguments
     * @param host                  The host of the tracking data provider service
     * @param port                  The port of the tracking data provider service
     * @param caCertificateFile     The CA certificate to verify the provider certificate
     * @param userCertificateFile   The user certificate presented to the provider
     * @param userKeyFile           The private user key for the mTLS handshake
     * @return                      The created TrackingDataServiceUser object.
     * @throws SSLException
     * @throws InvalidProtocolBufferException
     */
    public static TrackingDataServiceUser buildSecureTrackingDataService(String host,
                                                                         int port,
                                                                         File caCertificateFile,
                                                                         File userCertificateFile,
                                                                         File userKeyFile)
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
                .directExecutor() // improves performance by factor three
                .sslContext(sslContext)
                .build();

        LogManager.getLogger().info("Secure Tracking Data Service User, host: " + host + " port: " + port +
            " created using \n\tCA: " + caCertificateFile + "\n\tuser cert: " + userCertificateFile
            + "\n\tuser key: " + userKeyFile);

        return new TrackingDataServiceUser(channel);
    }

    /**
     * Constructs a tracking data service user and opens a tracking data stream.
     * Attaches the tracking data endpoint meta data to the channel.
     * @param channel    The channel to use to access the CDDS tracking data provider
     * @throws InvalidProtocolBufferException In case the endpoint cannot be encoded
     */
    private TrackingDataServiceUser(ManagedChannel channel) throws InvalidProtocolBufferException {
        LOG = LogManager.getLogger("cdds.td.user");

        this.channel = channel; // needed for later shutdown

        // this interceptor is updated on openTrackingDataEndpoint with the endpoint to add as meta data
        Channel interceptedChannel = ClientInterceptors.intercept(channel, interceptor);

        tdProviderStub = TrackingDataServiceProviderGrpc.newStub(interceptedChannel);

        tdUserStream = new StreamObserver<TrackingDataMessage>() {

            @Override
            public void onNext(TrackingDataMessage trackingDataMessage) {

                receivedTrackingDataMessages.add(trackingDataMessage);

                if(firstMessageTime == 0) {
                    firstMessageTime = System.nanoTime();
                    protoTdLength = trackingDataMessage.getSerializedSize();
                }

                long numMessages = TrackingDataServiceUser.this.numTdMessagesReceived.incrementAndGet();

                if(numMessages == 1) {
                    LOG.info("Tracking data service user received first tracking data message:\n" + trackingDataMessage);
                }

                if(numMessages >= TrackingDataServiceUser.this.numTdMessagesExpected.get()) {
                    lastMessageTime = System.nanoTime();
                    synchronized(TrackingDataServiceUser.this.numTdMessagesReceived) {
                        TrackingDataServiceUser.this.numTdMessagesReceived.notifyAll();
                    }
                }

                if(LOG.isDebugEnabled()) {
                    LOG.debug("Tracking data service user received " + numMessages + " tracking data messages:\n"
                        + trackingDataMessage);
                }
            }

            @Override
            public void onError(Throwable err) {
                LOG.info("Tracking data service user error called: " + err);
                synchronized(this) {
                    lastError = err;
                    this.notifyAll();
                }
            }

            @Override
            public void onCompleted() {
                LOG.info("Tracking data service user completed called");
            }
        };
    }

    /**
     * Requests endpoints from the CDDS provider applicable to this authenticated CDDS user.
     * @param timeoutMs The timeout for the request in milli seconds
     * @return          The list of authorized endpoints, potentially an empty list
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws TimeoutException Thrown if the request did not complete with time out
     */
    public List<TrackingDataServiceEndpoint> getEndpoints(long timeoutMs)
            throws InterruptedException, ExecutionException, TimeoutException {

        final List<TrackingDataServiceEndpoint> tdEndpoints = new ArrayList<>();

        CompletableFuture<List<TrackingDataServiceEndpoint>> future =
            new CompletableFuture<>();
        tdProviderStub.getEndpoints(NoArg.newBuilder().build(),
            new StreamObserver<TrackingDataServiceEndpointList>() {

            @Override
            public void onNext(TrackingDataServiceEndpointList value) {
                LOG.info("getEndpoints onNext");
                for(TrackingDataServiceEndpoint endpoint : value.getEndpointsList()) {
                    tdEndpoints.add(endpoint);
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
                future.complete(tdEndpoints);
            }

        });

        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Open a tracking data stream to the connected CDDS Tracking Data Provider.
     * Errors are reported by calls to onError of the tdUserStream.
     * Call waitForTrackingDataMessages to wait until reception.
     * @param tdEndpoint                The tracking data endpoint to open
     * @param numExpectedTdMessages     The number of expected tracking data messages
     * @throws InvalidProtocolBufferException
     */
    public void openTrackingDataEndpoint(TrackingDataServiceEndpoint tdEndpoint,
            long numExpectedTdMessages) throws InvalidProtocolBufferException {
        LOG = LogManager.getLogger("cdds.td.user." + tdEndpoint.getEndpointName());

        // set the endpoint meta data into the interceptor
        interceptor.setMetaData(ProtoJsonUtil.toJsonUtf8(tdEndpoint));

        this.numTdMessagesExpected.set(numExpectedTdMessages);

        tdProviderStub.openTrackingDataServiceEndpoint(NoArg.newBuilder().build(), tdUserStream);
        LOG.info("Opened tracking data endpoint called");
    }

    /**
     * Creates a tracking data service endpoint
     * @param endpointName          The name of the endpoint
     * @param serviceProvider       The service provider
     * @param terminal              The terminal supporting the endpoint
     * @param serviceUser           The service user using the service endpoint
     * @param latencyLimitSeconds   The seconds of the latency limit of the endpoint
     * @param latencyLimitMillis    The milliseconds of the latency limit of the endpoint
     * @param qos                   The QoS of the endpoint
     * @param format                The tracking data format of the endpoint (KVN, XML or PROTOBUF)
     * @param trackingDataTypes     The tracking data types provided by this endpoint
     * @return                      The created TrackingDataServiceEndpoint
     */
    public static TrackingDataServiceEndpoint getTrackingDataEndpoint(String endpointName, String serviceProvider,
            String terminal, String serviceUser, int latencyLimitSeconds, int latencyLimitMillis, EnumQoS qos,
            TrackingDataFormat format, TrackingDataType... trackingDataTypes) {

        TrackingDataServiceEndpoint.Builder tdEndpoint = TrackingDataServiceEndpoint.newBuilder()
                .setEndpointName(endpointName)
                .setServiceProvider(serviceProvider)
                .setTerminal(terminal)
                .setServiceUser(serviceUser)
                .setLatencyLimit(Duration.newBuilder()
                    .setSeconds(latencyLimitSeconds)
                    .setMilliseconds(latencyLimitMillis)
                    .build())
                .setQos(qos)
                .setTrackingDataFormat(format)
                .setServiceVersion(1);

        for(TrackingDataType trackingDataType : trackingDataTypes) {
            tdEndpoint.addTrackingDataType(trackingDataType);
        }

        return tdEndpoint.build();
    }

    /**
     * Creates a tracking data service endpoint delivering the tracking data in KVN format
     * @param endpointName          The name of the endpoint
     * @param serviceProvider       The service provider
     * @param terminal              The terminal supporting the endpoint
     * @param serviceUser           The service user using the service endpoint
     * @param latencyLimitSeconds   The seconds of the latency limit of the endpoint
     * @param latencyLimitMillis    The milliseconds of the latency limit of the endpoint
     * @param qos                   The QoS of the endpoint
     * @param trackingDataTypes     The tracking data types provided by this endpoint
     * @return                      The created TrackingDataServiceEndpoint with format KVN
     */
    public static TrackingDataServiceEndpoint getKvnTrackingDataEndpoint(String endpointName, String serviceProvider,
            String terminal, String serviceUser, int latencyLimitSeconds, int latencyLimitMillis, EnumQoS qos,
            TrackingDataType... trackingDataTypes) {
        return getTrackingDataEndpoint(endpointName, serviceProvider, terminal, serviceUser,
            latencyLimitSeconds, latencyLimitMillis, qos, TrackingDataFormat.KVN, trackingDataTypes);
    }

    /**
     * Creates a tracking data service endpoint delivering the tracking data in Protobuf format
     * @param endpointName          The name of the endpoint
     * @param serviceProvider       The service provider
     * @param terminal              The terminal supporting the endpoint
     * @param serviceUser           The service user using the service endpoint
     * @param latencyLimitSeconds   The seconds of the latency limit of the endpoint
     * @param latencyLimitMillis    The milliseconds of the latency limit of the endpoint
     * @param qos                   The QoS of the endpoint
     * @param trackingDataTypes     The tracking data types provided by this endpoint
     * @return                      The created TrackingDataServiceEndpoint with format PROTOBUF
     */
    public static TrackingDataServiceEndpoint getProtobufTrackingDataEndpoint(String endpointName, String serviceProvider,
            String terminal, String serviceUser, int latencyLimitSeconds, int latencyLimitMillis, EnumQoS qos,
            TrackingDataType... trackingDataTypes) {
        return getTrackingDataEndpoint(endpointName, serviceProvider, terminal, serviceUser,
            latencyLimitSeconds, latencyLimitMillis, qos, TrackingDataFormat.PROTOBUF, trackingDataTypes);
    }

    /**
     * Wait for a number of tracking data messages received or timeout
     * @param timeout  The timeout in milli seconds to wait for the messages
     * @return          The number of received tracking data messages
     * @throws TimeLimitExceededException
     */
    public long waitForTrackingDataMessages(long timeout) throws TimeLimitExceededException {
        return waitForTrackingDataMessages(numTdMessagesExpected.get(),
            this.numTdMessagesReceived, timeout, "tracking data messages");
    }

    /**
     * Waits until the given number of tracking data messages is received or timeout occurs
     * @param numExpectedMessages     The number of expected tracking data messages
     * @param numReceivedTdMsgs       The counter of received tracking data messages
     * @param timeout                 The timeout in milli seconds
     * @param messageType             The type of messages waited for (for logging)
     * @return                        Returns the number of received tracking data messages.
     * @throws TimeLimitExceededException
     */
    private long waitForTrackingDataMessages(long numExpectedMessages,
            AtomicLong numReceivedTdMsgs, long timeout, String messageType)
            throws TimeLimitExceededException {

        long deadline = System.currentTimeMillis() + timeout + 200; // 200: a margin

        synchronized(numReceivedTdMsgs) {
            while(numReceivedTdMsgs.get() < numExpectedMessages) {
                try {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("wait for " + numReceivedTdMsgs.get() + "/" + numExpectedMessages
                            + " " + messageType + " received");
                    }
                    numReceivedTdMsgs.wait(500);
                    System.out.print("\rReceived: " + numReceivedTdMsgs.get()
                        + " / " + numExpectedMessages + " messages");
                    System.out.flush();
                } catch (InterruptedException e) {
                }

                if(System.currentTimeMillis() > deadline) {
                    LOG.info("Timeout, did not receive " + numExpectedMessages + " within " + timeout + " ms");
                    throw new TimeLimitExceededException("Did not receive " + numExpectedMessages
                        + " tracking data messages within " + timeout + " ms");
                }
            }
            System.out.println();
        }

        final double duration = (lastMessageTime - firstMessageTime) / 1E9;

        final double mps = numReceivedTdMsgs.get() / duration;

        LOG.info("Return from wait for tracking data messages.\n\t" + numReceivedTdMsgs.get()
            + "/" + numExpectedMessages + " tracking data messages received, within " + duration + "s"
            + "\n\t{} tracking data messages/s (proto length: " + protoTdLength + ")"
            , String.format("%.2f", mps));

        return numReceivedTdMsgs.get();
    }

    /**
     * The tracking data messages received from the tracking data provider.
     * @return  The list of received tracking data messages
     */
    public List<TrackingDataMessage> getReceivedTrackingDataMessages() {
        return receivedTrackingDataMessages;
    }

    /**
     * Shuts down the communication channel
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
