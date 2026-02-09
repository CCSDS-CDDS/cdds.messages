package cdds.service.tc;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;

import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;


/**
 * Test service for TC service. Created one TC service.
 */
public class ProviderServer {
    private final int port;
    private final Server gRpcServer;
    private static final Logger LOG = Logger.getLogger("CDDS Provider Server");
    private final TcServiceAuthorization tcAuthorization = new TcServiceAuthorization();

    /**
     * Creates a TC server running one TC service on the given port.
     * @param port
     */
    public ProviderServer(int port) {
        this(Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create()), port);
    }

    /**
     * Creates a TC server running one TC service on the given port using mTLS and SSL .
     * @param port
     * @param caCertificateFile
     * @param providerCertificateFile
     * @param providerKeyFile
     * @throws IOException 
     */
    public ProviderServer(int port, File caCertificateFile, File providerCertificateFile, File providerKeyFile)
            throws IOException {
        this.port = port;
        
        if(caCertificateFile.exists() == false) {
            throw new IOException("CA certificate file not found: " + caCertificateFile);
        }

        if(providerCertificateFile.exists() == false) {
            throw new IOException("Provider certificate file not found: " + providerCertificateFile);
        }

        if(providerKeyFile.exists() == false) {
            throw new IOException("Provider key file not found: " + providerKeyFile);
        }

        try {
                SslContext sslContext = GrpcSslContexts.forServer(
                    providerCertificateFile,
                    providerKeyFile)
                    .trustManager(caCertificateFile) // Trust client certs
                    .clientAuth(ClientAuth.REQUIRE) // Enforce mTLS
                    .build();
            
        gRpcServer = NettyServerBuilder.forPort(port)
                .sslContext(sslContext)
                .intercept(tcAuthorization)           /* call before adding the service to intercept */
                .addService(new TcServiceProvider())
                .build();
        } catch(SSLException sslEx) {
            LOG.warning("Exception creating secure server: " + sslEx);
            throw sslEx;
        }
    
        LOG.info("Secure Server started, listening on " + port + "\n\tCA: " + caCertificateFile + "\n\tserver cert: " + providerCertificateFile
            + "\n\tserver key: " + providerKeyFile);
    
    }

    /**
     * Creates a TC server running one TC service on the given port.
     * @param serverBuilder The server builder to use
     * @param port          The port to use
     */
    public ProviderServer(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;        
        gRpcServer = serverBuilder
            .intercept(tcAuthorization)
            .addService(new TcServiceProvider())
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

    /**
     * Stops the gRPC server within 30s.
     * @throws InterruptedException
     */
    public void stop() throws InterruptedException {
        if (gRpcServer != null) {
            gRpcServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Add an allowed TC endpoint
     * @param tcEndpoint
     */
    public void addAuthorizedTcEndpoint(TcServiceEndpoint tcEndpoint) {
        tcAuthorization.addAuthorizedTcEndpoint(tcEndpoint);
    }

    /**
     * Removes an allowd TC endpoint
     * @param tcEndpoint
     */
    public void removeAuthorizedTcEndpoint(TcServiceEndpoint tcEndpoint) {
       tcAuthorization.removeAuthorizedTcEndpoint(tcEndpoint);
    }

}
