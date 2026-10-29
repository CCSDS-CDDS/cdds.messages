package cdds.service.monitoring;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.naming.TimeLimitExceededException;
import javax.net.ssl.SSLException;

import com.google.protobuf.InvalidProtocolBufferException;
import ccsds.cdds.v1.Monitoring.FunctionalResourceEvent;
import ccsds.cdds.v1.Monitoring.FunctionalResourceParameter;
import ccsds.cdds.v1.Monitoring.GenericMonitoringParameter;
import ccsds.cdds.v1.Monitoring.MonitoringMessage;
import ccsds.cdds.v1.Types.Duration;
import ccsds.cdds.v1.Types.EnumQoS;
import ccsds.cdds.v1.Types.NoArg;
import ccsds.cdds.v1.Types.ObjectIdentifier;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.EventIdentification;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.FunctionalResourceParameterName;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringMode;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpoint;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpointList;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.ParameterIdentification;
import ccsds.cdds.v1.monitoring.MonitoringServiceProviderGrpc;
import ccsds.cdds.v1.monitoring.MonitoringServiceProviderGrpc.MonitoringServiceProviderStub;
import cdds.service.common.ClientMetaDataInterceptor;
import cdds.service.common.ProtoJsonUtil;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.stub.StreamObserver;

/**
 * Simple Monitoring Service User to open a monitoring endpoint
 * and receive monitoring messages. Allows to wait for responses.
 */
public class MonitoringServiceUser {

    private final MonitoringServiceProviderStub monitoringProviderStub;    // the provider stub to open monitoring streams
    private final StreamObserver<MonitoringMessage> monitoringUserStream;  // the stream to observe monitoring messages received
    private final ManagedChannel channel;
    private Throwable lastError;
    private final ClientMetaDataInterceptor interceptor =
            new ClientMetaDataInterceptor(MonitoringServiceAuthorization.MONITORING_ENDPOINT_KEY);

    private final AtomicLong numMonitoringMessagesReceived = new AtomicLong(0);
    private final AtomicLong numMonitoringMessagesExpected = new AtomicLong(0);

    /**
     * The number of parameter updates received per parameter identifier
     */
    private final Map<String, Long> receivedParameterUpdates = new ConcurrentHashMap<>();

    /**
     * The number of events received per event identifier
     */
    private final Map<String, Long> receivedEvents = new ConcurrentHashMap<>();

    private volatile long firstMessageTime = 0;
    private volatile long lastMessageTime = 0;
    private volatile int protoMonitoringLength = 0;

    private volatile Logger LOG;

    /**
     * Constructs a monitoring service user and connects to the provider, w/o security.
     * @param host  The host of the monitoring provider
     * @param port  The port of the monitoring provider
     */
    public static MonitoringServiceUser buildUnsecureMonitoringServiceUser(String host, int port)
            throws InvalidProtocolBufferException {
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .directExecutor() // improves performance by factor three
            .build();

        return new MonitoringServiceUser(channel);
    }

    /**
     * Creates a monitoring user using an mTLS channel with the given arguments
     * @param host                  The host of the monitoring provider service
     * @param port                  The port of the monitoring provider service
     * @param caCertificateFile     The CA certificate to verify the provider certificate
     * @param userCertificateFile   The user certificate presented to the provider
     * @param userKeyFile           The private user key for the mTLS handshake
     * @return                      The created MonitoringServiceUser object.
     * @throws SSLException
     * @throws InvalidProtocolBufferException
     */
    public static MonitoringServiceUser buildSecureMonitoringService(String host,
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

        LogManager.getLogger().info("Secure Monitoring Service User, host: " + host + " port: " + port +
            " created using \n\tCA: " + caCertificateFile + "\n\tuser cert: " + userCertificateFile
            + "\n\tuser key: " + userKeyFile);

        return new MonitoringServiceUser(channel);
    }

    /**
     * Constructs a monitoring service user and opens a monitoring stream.
     * Attaches the monitoring endpoint meta data to the channel.
     * @param channel    The channel to use to access the CDDS monitoring provider
     * @throws InvalidProtocolBufferException In case the endpoint cannot be encoded
     */
    private MonitoringServiceUser(ManagedChannel channel) throws InvalidProtocolBufferException {
        LOG = LogManager.getLogger("cdds.monitoring.user");

        this.channel = channel; // needed for later shutdown

        // this interceptor is updated on openMonitoringEndpoint with the endpoint to add as meta data
        Channel interceptedChannel = ClientInterceptors.intercept(channel, interceptor);

        monitoringProviderStub = MonitoringServiceProviderGrpc.newStub(interceptedChannel);

        monitoringUserStream = new StreamObserver<MonitoringMessage>() {

            @Override
            public void onNext(MonitoringMessage monitoringMessage) {

                for (final GenericMonitoringParameter genericParameter :
                        monitoringMessage.getGenericMonitoringParametersList()) {
                    receivedParameterUpdates.merge(genericParameter.getIdentifier(), 1L, Long::sum);
                }

                for (final FunctionalResourceParameter frParameter :
                        monitoringMessage.getFunctionalResourceParametersList()) {
                    receivedParameterUpdates.merge(
                            frParameter.getFunctionalResourceParameterIdentifier().getOidString(), 1L, Long::sum);
                }

                for (final FunctionalResourceEvent frEvent :
                        monitoringMessage.getFunctionalResourceEventsList()) {
                    receivedEvents.merge(
                            frEvent.getFunctionalResourceEventIdentifier().getOidString(), 1L, Long::sum);
                }

                if(firstMessageTime == 0) {
                    firstMessageTime = System.nanoTime();
                    protoMonitoringLength = monitoringMessage.getSerializedSize();
                }

                long numMessages = MonitoringServiceUser.this.numMonitoringMessagesReceived.incrementAndGet();

                if(numMessages == 1) {
                    LOG.info("Monitoring service user received first monitoring message:\n" + monitoringMessage);
                }

                if(numMessages >= MonitoringServiceUser.this.numMonitoringMessagesExpected.get()) {
                    lastMessageTime = System.nanoTime();
                    synchronized(MonitoringServiceUser.this.numMonitoringMessagesReceived) {
                        MonitoringServiceUser.this.numMonitoringMessagesReceived.notifyAll();
                    }
                }

                if(LOG.isDebugEnabled()) {
                    LOG.debug("Monitoring service user received " + numMessages + " monitoring messages:\n"
                        + monitoringMessage);
                }
            }

            @Override
            public void onError(Throwable err) {
                LOG.info("Monitoring service user error called: " + err);
                synchronized(this) {
                    lastError = err;
                    this.notifyAll();
                }
            }

            @Override
            public void onCompleted() {
                LOG.info("Monitoring service user completed called\"");
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
    public List<MonitoringServiceEndpoint> getEndpoints(long timeoutMs)
            throws InterruptedException, ExecutionException, TimeoutException {

        final List<MonitoringServiceEndpoint> monitoringEndpoints = new ArrayList<>();

        CompletableFuture<List<MonitoringServiceEndpoint>> future =
            new CompletableFuture<>();
        monitoringProviderStub.getEndpoints(NoArg.newBuilder().build(),
            new StreamObserver<MonitoringServiceEndpointList>() {

            @Override
            public void onNext(MonitoringServiceEndpointList value) {
                LOG.info("getEndpoints onNext");
                for(MonitoringServiceEndpoint endpoint : value.getEndpointsList()) {
                    monitoringEndpoints.add(endpoint);
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
                future.complete(monitoringEndpoints);
            }

        });

        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Open a monitoring stream to the connected CDDS Monitoring Provider.
     * Errors are reported by calls to onError of the monitoringUserStream.
     * Call waitForMonitoringMessages to wait until reception.
     * @param monitoringEndpoint            The monitoring endpoint to open
     * @param numExpectedMonitoringMessages The number of expected monitoring messages
     * @throws InvalidProtocolBufferException
     */
    public void openMonitoringEndpoint(MonitoringServiceEndpoint monitoringEndpoint,
            long numExpectedMonitoringMessages) throws InvalidProtocolBufferException {
        LOG = LogManager.getLogger("cdds.monitoring.user." + monitoringEndpoint.getEndpointName());

        // set the endpoint meta data into the interceptor
        interceptor.setMetaData(ProtoJsonUtil.toJsonUtf8(monitoringEndpoint));

        this.numMonitoringMessagesExpected.set(numExpectedMonitoringMessages);

        monitoringProviderStub.openMonitoringEndpoint(NoArg.newBuilder().build(), monitoringUserStream);
        LOG.info("Opened monitoring endpoint called");
    }

    /**
     * Creates a monitoring service endpoint
     * @param endpointName                  The name of the endpoint
     * @param serviceProvider               The service provider
     * @param terminal                      The terminal supporting the endpoint
     * @param serviceUser                   The service user using the service endpoint
     * @param monitoringCycleSecs           The monitoring cycle in seconds
     * @param mode                          The monitoring mode of the endpoint
     * @param genericParameterIdentifiers   The identifiers of the generic monitoring
     *                                      parameters provided by this endpoint
     * @return                              The created MonitoringServiceEndpoint
     */
    public static MonitoringServiceEndpoint getMonitoringEndpoint(String endpointName, String serviceProvider,
            String terminal, String serviceUser, int monitoringCycleSecs, MonitoringMode mode,
            String... genericParameterIdentifiers) {

        MonitoringServiceEndpoint.Builder monitoringEndpoint = MonitoringServiceEndpoint.newBuilder()
                .setEndpointName(endpointName)
                .setServiceProvider(serviceProvider)
                .setTerminal(terminal)
                .setServiceUser(serviceUser)
                .setMonitoringCycle(Duration.newBuilder()
                    .setSeconds(monitoringCycleSecs)
                    .build())
                .setMode(mode)
                .setQos(EnumQoS.EXACTLY_ONCE)
                .setServiceVersion(1);

        for(String genericParameterIdentifier : genericParameterIdentifiers) {
            monitoringEndpoint.addMonitoringParameters(ParameterIdentification.newBuilder()
                    .setGenericParameterIdentifier(genericParameterIdentifier)
                    .build());
        }

        return monitoringEndpoint.build();
    }

    /**
     * Creates a monitoring service endpoint monitoring Functional Resource parameters
     * and reporting Functional Resource events, both identified by their OID.
     * @param endpointName            The name of the endpoint
     * @param serviceProvider         The service provider
     * @param terminal                The terminal supporting the endpoint
     * @param serviceUser             The service user using the service endpoint
     * @param monitoringCycleSecs     The monitoring cycle in seconds
     * @param mode                    The monitoring mode of the endpoint
     * @param firstFrParameterDescription    The description of the first Functional Resource parameter monitored
     * @param firstFrParameterOid            The OID of the first Functional Resource parameter monitored
     * @param secondFrParameterDescription  The description of the second Functional Resource parameter monitored
     * @param secondFrParameterOid           The OID of the second Functional Resource parameter monitored
     * @param secondFrParameterInstance     The instance number of the Functional Resource the second
     *                                      parameter is monitored for
     * @param frEventDescription            The description of the Functional Resource event reported
     * @param frEventOid                    The OID of the Functional Resource event reported
     * @return                              The created MonitoringServiceEndpoint
     */
    public static MonitoringServiceEndpoint getFrMonitoringEndpoint(String endpointName, String serviceProvider,
            String terminal, String serviceUser, int monitoringCycleSecs, MonitoringMode mode,
            String firstFrParameterDescription, String firstFrParameterOid,
            String secondFrParameterDescription, String secondFrParameterOid, long secondFrParameterInstance,
            String frEventDescription, String frEventOid) {

        return MonitoringServiceEndpoint.newBuilder()
                .setEndpointName(endpointName)
                .setServiceProvider(serviceProvider)
                .setTerminal(terminal)
                .setServiceUser(serviceUser)
                .setMonitoringCycle(Duration.newBuilder()
                    .setSeconds(monitoringCycleSecs)
                    .build())
                .setMode(mode)
                .setQos(EnumQoS.EXACTLY_ONCE)
                .setServiceVersion(1)
                .addMonitoringParameters(ParameterIdentification.newBuilder()
                    .setFunctionalResourceParameterId(
                            createObjectIdentifier(firstFrParameterDescription, firstFrParameterOid))
                    .build())
                .addMonitoringParameters(ParameterIdentification.newBuilder()
                    .setFunctionalResourceParameterName(FunctionalResourceParameterName.newBuilder()
                        .setFunctionalResourceParameterIdentifier(
                                createObjectIdentifier(secondFrParameterDescription, secondFrParameterOid))
                        .setFunctionalResourceInstance(secondFrParameterInstance)
                        .build())
                    .build())
                .addReportedEvents(EventIdentification.newBuilder()
                    .setFunctionalResourceEventId(createObjectIdentifier(frEventDescription, frEventOid))
                    .build())
                .build();
    }

    /**
     * Creates an ObjectIdentifier for the given OID string in dotted notation.
     * The oidString carries a description of the identified object
     * in a human readable way, the arcs carry the OID in dotted notation.
     * @param description  The description of the identified object
     * @param oidString    The OID string in dotted notation
     * @return             The created ObjectIdentifier
     */
    private static ObjectIdentifier createObjectIdentifier(String description, String oidString) {
        final ObjectIdentifier.Builder oid = ObjectIdentifier.newBuilder()
                .setOidString(description);

        for (final String arc : oidString.split("\\.")) {
            oid.addArcs(Long.parseLong(arc));
        }

        return oid.build();
    }

    /**
     * Wait for a number of monitoring messages received or timeout
     * @param timeout  The timeout in milli seconds to wait for the messages
     * @return          The number of received monitoring messages
     * @throws TimeLimitExceededException
     */
    public long waitForMonitoringMessages(long timeout) throws TimeLimitExceededException {
        return waitForMonitoringMessages(numMonitoringMessagesExpected.get(),
            this.numMonitoringMessagesReceived, timeout, "monitoring messages");
    }

    /**
     * Waits until the given number of monitoring messages is received or timeout occurs
     * @param numExpectedMessages         The number of expected monitoring messages
     * @param numReceivedMonitoringMsgs   The counter of received monitoring messages
     * @param timeout                     The timeout in milli seconds
     * @param messageType                 The type of messages waited for (for logging)
     * @return                            Returns the number of received monitoring messages.
     * @throws TimeLimitExceededException
     */
    private long waitForMonitoringMessages(long numExpectedMessages,
            AtomicLong numReceivedMonitoringMsgs, long timeout, String messageType)
            throws TimeLimitExceededException {

        long deadline = System.currentTimeMillis() + timeout + 200; // 200: a margin

        synchronized(numReceivedMonitoringMsgs) {
            while(numReceivedMonitoringMsgs.get() < numExpectedMessages) {
                try {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("wait for " + numReceivedMonitoringMsgs.get() + "/" + numExpectedMessages
                            + " " + messageType + " received");
                    }
                    numReceivedMonitoringMsgs.wait(500);
                    System.out.print("\rReceived: " + numReceivedMonitoringMsgs.get()
                        + " / " + numExpectedMessages + " messages");
                    System.out.flush();
                } catch (InterruptedException e) {
                }

                if(System.currentTimeMillis() > deadline) {
                    LOG.info("Timeout, did not receive " + numExpectedMessages + " within " + timeout + " ms");
                    throw new TimeLimitExceededException("Did not receive " + numExpectedMessages
                        + " monitoring messages within " + timeout + " ms");
                }
            }
            System.out.println();
        }

        final double duration = (lastMessageTime - firstMessageTime) / 1E9;

        final double mps = numReceivedMonitoringMsgs.get() / duration;

        LOG.info("Return from wait for monitoring messages.\n\t" + numReceivedMonitoringMsgs.get()
            + "/" + numExpectedMessages + " monitoring messages received, within " + duration + "s"
            + "\n\t{} monitoring messages/s (proto length: " + protoMonitoringLength + ")"
            , String.format("%.2f", mps));

        return numReceivedMonitoringMsgs.get();
    }

    /**
     * The number of parameter updates received per parameter identifier.
     * The key of the map is the identifier of the parameter.
     * @return  map of parameter identifier to number of parameter updates received
     */
    public Map<String, Long> getReceivedParameterUpdates() {
        return receivedParameterUpdates;
    }

    /**
     * The number of events received per event identifier.
     * The key of the map is the identifier of the event.
     * @return  map of event identifier to number of events received
     */
    public Map<String, Long> getReceivedEvents() {
        return receivedEvents;
    }

    /**
     * Stop using this service endpoint
     */
    public void stop() {
        // TODO how to tell the endpoint at the provider to stop delivering data?
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
