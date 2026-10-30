package cdds.service.control;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javax.naming.TimeLimitExceededException;
import javax.net.ssl.SSLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.Control.ControlDirective;
import ccsds.cdds.v1.Control.DirectiveReport;
import ccsds.cdds.v1.Types.NoArg;
import ccsds.cdds.v1.Types.ObjectIdentifier;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpoint;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpointList;
import ccsds.cdds.v1.control.CddsControlService.FunctionalResourceDirectiveName;
import ccsds.cdds.v1.control.ControlServiceProviderGrpc;
import ccsds.cdds.v1.control.ControlServiceProviderGrpc.ControlServiceProviderStub;
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
 * Simple Control Service User to open a control endpoint and send
 * control directives. Allows to wait for directive reports.
 */
public class ControlServiceUser {

    private final ControlServiceProviderStub controlProviderStub;   // the provider stub to open control streams
    private final StreamObserver<DirectiveReport> controlUserStream; // the stream to observe directive reports received
    private StreamObserver<ControlDirective> controlProviderStream;   // the stream to send control directives
    private final ManagedChannel channel;
    private Throwable lastError;
    private final ClientMetaDataInterceptor interceptor =
            new ClientMetaDataInterceptor(ControlServiceAuthorization.CONTROL_ENDPOINT_KEY);

    private final AtomicLong numDirectiveReportsReceived = new AtomicLong(0);
    private final AtomicLong numDirectiveReportsExpected = new AtomicLong(0);

    /**
     * The invocation identifier to use for the next invoked directive,
     * increased for every invoked directive
     */
    private final AtomicLong nextInvocationId = new AtomicLong(0);

    /**
     * The directive reports received per invocation, ordered as received
     */
    private final List<DirectiveReport> receivedDirectiveReports = new CopyOnWriteArrayList<>();

    private volatile Logger LOG;

    /**
     * Constructs a control service user and connects to the provider, w/o security.
     * @param host  The host of the control provider
     * @param port  The port of the control provider
     */
    public static ControlServiceUser buildUnsecureControlServiceUser(String host, int port)
            throws InvalidProtocolBufferException {
        return new ControlServiceUser(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
    }

    /**
     * Creates a control user using an mTLS channel with the given arguments
     * @param host                  The host of the control provider service
     * @param port                  The port of the control provider service
     * @param caCertificateFile     The CA certificate to verify the provider certificate
     * @param userCertificateFile   The user certificate presented to the provider
     * @param userKeyFile           The private user key for the mTLS handshake
     * @return                      The created ControlServiceUser object.
     * @throws SSLException
     * @throws InvalidProtocolBufferException
     */
    public static ControlServiceUser buildSecureControlService(String host,
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
                .sslContext(sslContext)
                .build();

        LogManager.getLogger().info("Secure Control Service User, host: " + host + " port: " + port +
            " created using \n\tCA: " + caCertificateFile + "\n\tuser cert: " + userCertificateFile
            + "\n\tuser key: " + userKeyFile);

        return new ControlServiceUser(channel);
    }

    /**
     * Constructs a control service user and opens a control stream.
     * Attaches the control endpoint meta data to the channel.
     * @param channel   The channel to use to access the CDDS control provider
     */
    private ControlServiceUser(ManagedChannel channel) {
        LOG = LogManager.getLogger("cdds.control.user");

        this.channel = channel; // needed for later shutdown

        // this interceptor is updated on openControlEndpoint with the endpoint to add as meta data
        Channel interceptedChannel = ClientInterceptors.intercept(channel, interceptor);

        controlProviderStub = ControlServiceProviderGrpc.newStub(interceptedChannel);
        controlUserStream = new StreamObserver<DirectiveReport>() {

            @Override
            public void onCompleted() {
                LOG.info("Control service user completed called");
            }

            @Override
            public void onError(Throwable err) {
                LOG.info("Control service user error called: " + err);
                synchronized(this) {
                    lastError = err;
                    this.notifyAll();
                }
            }

            @Override
            public void onNext(DirectiveReport directiveReport) {
                LOG.info("Control service user directive report " + directiveReport.getInvocationId()
                    + " " + directiveReport.getResult() + " received:\n" + directiveReport);

                receivedDirectiveReports.add(directiveReport);

                if(numDirectiveReportsReceived.incrementAndGet()
                        >= ControlServiceUser.this.numDirectiveReportsExpected.get()) {
                    synchronized(ControlServiceUser.this.numDirectiveReportsReceived) {
                        ControlServiceUser.this.numDirectiveReportsReceived.notifyAll();
                    }
                }
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
    public List<ControlServiceEndpoint> getEndpoints(long timeoutMs)
            throws InterruptedException, ExecutionException, TimeoutException {

        final List<ControlServiceEndpoint> controlEndpoints = new ArrayList<>();

        CompletableFuture<List<ControlServiceEndpoint>> future =
            new CompletableFuture<>();
        controlProviderStub.getEndpoints(NoArg.newBuilder().build(),
            new StreamObserver<ControlServiceEndpointList>() {

            @Override
            public void onNext(ControlServiceEndpointList value) {
                LOG.info("getEndpoints onNext");
                for(ControlServiceEndpoint endpoint : value.getEndpointsList()) {
                    controlEndpoints.add(endpoint);
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
                future.complete(controlEndpoints);
            }

        });

        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Open a control stream to the connected CDDS Control Provider.
     * Errors are reported by calls to onError of the controlUserStream.
     * @param controlEndpoint                The control endpoint to open
     * @param numExpectedDirectiveReports    The number of expected directive reports
     * @throws InvalidProtocolBufferException
     */
    public void openControlEndpoint(ControlServiceEndpoint controlEndpoint,
            long numExpectedDirectiveReports) throws InvalidProtocolBufferException {
        LOG = LogManager.getLogger("cdds.control.user." + controlEndpoint.getEndpointName());

        // set the endpoint meta data into the interceptor
        interceptor.setMetaData(ProtoJsonUtil.toJsonUtf8(controlEndpoint));

        this.numDirectiveReportsExpected.set(numExpectedDirectiveReports);
        this.numDirectiveReportsReceived.set(0);

        controlProviderStream = controlProviderStub.openControlEndpoint(controlUserStream);
        LOG.info("Opened control endpoint called");
    }

    /**
     * Returns the invocation identifier to use for the next invoked control
     * directive. The invocation identifier is increased for every invoked
     * directive.
     * @return  The next invocation identifier
     */
    public int nextInvocationId() {
        return (int)nextInvocationId.incrementAndGet();
    }

    /**
     * Sends a control directive to the control provider.
     * @param controlDirective   The control directive to be sent.
     */
    public void sendControlDirective(ControlDirective controlDirective) {
        LOG.info("Send control directive:\n" + controlDirective);
        controlProviderStream.onNext(controlDirective);
    }

    /**
     * Creates a control service endpoint supporting generic control directives
     * @param endpointName        The name of the endpoint
     * @param serviceProvider     The service provider
     * @param terminal            The terminal supporting the endpoint
     * @param serviceUser         The service user using the service endpoint
     * @param genericDirectiveIds The identifiers of the generic control directives
     *                            provided by this endpoint
     * @return                    The created ControlServiceEndpoint
     */
    public static ControlServiceEndpoint getControlEndpoint(String endpointName, String serviceProvider,
            String terminal, String serviceUser, String... genericDirectiveIds) {

        ControlServiceEndpoint.Builder controlEndpoint = ControlServiceEndpoint.newBuilder()
                .setEndpointName(endpointName)
                .setServiceProvider(serviceProvider)
                .setTerminal(terminal)
                .setServiceUser(serviceUser)
                .setServiceVersion(1)
                .setAckTimeout(1);

        for(String genericDirectiveId : genericDirectiveIds) {
            controlEndpoint.addGenericDirectiveIds(genericDirectiveId);
        }

        return controlEndpoint.build();
    }

    /**
     * Creates a control service endpoint supporting Functional Resource control directives
     * identified by their OID.
     * @param endpointName               The name of the endpoint
     * @param serviceProvider            The service provider
     * @param terminal                   The terminal supporting the endpoint
     * @param serviceUser                The service user using the service endpoint
     * @param frDirectiveDescription    The description of the Functional Resource directive
     * @param frDirectiveOid             The OID of the Functional Resource directive
     * @param frDirectiveInstance       The instance number of the Functional Resource
     * @return                           The created ControlServiceEndpoint
     */
    public static ControlServiceEndpoint getFrControlEndpoint(String endpointName, String serviceProvider,
            String terminal, String serviceUser, String frDirectiveDescription, String frDirectiveOid,
            long frDirectiveInstance) {

        return ControlServiceEndpoint.newBuilder()
                .setEndpointName(endpointName)
                .setServiceProvider(serviceProvider)
                .setTerminal(terminal)
                .setServiceUser(serviceUser)
                .setServiceVersion(1)
                .setAckTimeout(1)
                .addFunctionalResourceDirectives(FunctionalResourceDirectiveName.newBuilder()
                    .setFunctionalResourceDirective(createObjectIdentifier(frDirectiveDescription, frDirectiveOid))
                    .setFunctionalResourceInstance(frDirectiveInstance)
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
     * Wait for the number of expected directive reports received or timeout occurs
     * @param timeout  The timeout in milli seconds to wait for the reports
     * @return         The number of received directive reports
     * @throws TimeLimitExceededException
     */
    public long waitForDirectiveReports(long timeout) throws TimeLimitExceededException {

        final long numExpectedReports = numDirectiveReportsExpected.get();
        long deadline = System.currentTimeMillis() + timeout + 200; // 200: a margin

        synchronized(numDirectiveReportsReceived) {
            while(numDirectiveReportsReceived.get() < numExpectedReports) {
                try {
                    LOG.info("wait for directive reports. " + numDirectiveReportsReceived.get()
                        + "/" + numExpectedReports + " directive reports received");
                    numDirectiveReportsReceived.wait(500);
                } catch (InterruptedException e) {
                }

                if(System.currentTimeMillis() > deadline) {
                    LOG.info("Timeout, did not receive " + numExpectedReports + " within " + timeout + " ms");
                    throw new TimeLimitExceededException("Did not receive " + numExpectedReports
                        + " directive reports within " + timeout + " ms");
                }
            }
        }

        LOG.info("Return from wait for directive reports. " + numDirectiveReportsReceived.get()
            + "/" + numExpectedReports + " directive reports received");

        return numDirectiveReportsReceived.get();
    }

    /**
     * The directive reports received, ordered as received.
     * @return  The list of directive reports received by this user
     */
    public List<DirectiveReport> getReceivedDirectiveReports() {
        return receivedDirectiveReports;
    }

    /**
     * Stop using this service endpoint.
     * The channel remains connected
     */
    public void stop() {
        controlProviderStream.onCompleted();
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
