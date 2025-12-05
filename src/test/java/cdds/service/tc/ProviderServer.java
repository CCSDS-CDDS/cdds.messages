package cdds.service.tc;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * Test service for TC service. Created one TC service.
 */
public class ProviderServer {
    private final int port;
    private final Server gRpcServer;
    private static final Logger LOG = Logger.getLogger("CDDS Provider Server");

    /**
     * Creates a TC server running one TC service on the given port.
     * @param port
     */
    public ProviderServer(int port) {
        this(Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create()), port);
    }

    /**
     * Creates a TC server running one TC service on the given port.
     * @param serverBuilder The server builder to use
     * @param port          The port to use
     */
    public ProviderServer(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;
        TcServiceAuthorization tcAuthorization = new TcServiceAuthorization(); 
        gRpcServer = serverBuilder
            .intercept(tcAuthorization)
            .addService(new TcServiceProvider(tcAuthorization))
            .build();
    }

    /**
     * Start serving requests.      
     */
    public void start() throws IOException {
        gRpcServer.start();
        LOG.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown
                // hook.
                LOG.warning("*** shutting down gRPC server since JVM is shutting down");
                try {
                    ProviderServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
    }

    public void stop() throws InterruptedException {
        if (gRpcServer != null) {
            gRpcServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

}
