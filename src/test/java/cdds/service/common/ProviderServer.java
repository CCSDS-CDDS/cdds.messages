package cdds.service.common;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;

import ccsds.cdds.CddsServiceProvider.ServiceProviderAddress;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;


/**
 * Test service provider for CDDS services. 
 */
public class ProviderServer {
    private final int port;
    private final Server gRpcServer;
    private static final Logger LOG = LogManager.getLogger("cdds.provider.server.");

    /**
     * Creates a TC server running one TC service on the given port.
     * @param port
     * @param services      The services to provide
     */
    public ProviderServer(int port, InterceptedService[] services) {
        this(Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create()), port, services);
    }

    /**
     * Creates a CDDS service provider
     * @param address       The address to use (port)
     * @param services      The services to provide
     * @throws IOException  Thrown if the certificate files are not found
     */
    public ProviderServer(ServiceProviderAddress address, InterceptedService[] services) throws IOException {
        this(address.getPort(),
             services,
             resourceToFile(address.getRootCertificateFile()),
             resourceToFile(address.getCertificateFile()),
             resourceToFile(address.getPrivateKeyFile())
        );
    } 

    /**
     * Creates a  CDDS server running CDDS services on the given port using mTLS and SSL .
     * @param port
     * @param caCertificateFile
     * @param providerCertificateFile
     * @param providerKeyFile
     * @throws IOException 
     */
    public ProviderServer(int port, InterceptedService[] services, File caCertificateFile, File providerCertificateFile, File providerKeyFile)
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
            
        NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(port)
                .sslContext(sslContext);
        
        // call intercept before adding the service to intercept
        Arrays.stream(services)
            .filter(service -> service.getServiceInterceptor() != null) // allow services w/o meta data
            .forEach(service -> {serverBuilder.intercept(service.getServiceInterceptor());});

        // add the services, interceptors are added just above
        Arrays.stream(services).forEach(service -> {serverBuilder.addService(service.getBindableService());});

        gRpcServer = serverBuilder.build();        
                
        } catch(SSLException sslEx) {
            LOG.warn("Exception creating secure server: " + sslEx);
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
    public ProviderServer(ServerBuilder<?> serverBuilder, int port, InterceptedService[] services) {
        this.port = port;
        
        //
        Arrays.stream(services).forEach(service -> {serverBuilder.intercept(service.getServiceInterceptor());});
        
        Arrays.stream(services).forEach(service -> {serverBuilder.addService(service.getBindableService());});
        
        gRpcServer = serverBuilder.build();
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
                LOG.warn("*** shutting down gRPC server since JVM is shutting down");
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
     * Converts a resource path (directory) to a File
     * 
     * @param resourcePath
     * @return The File representing the resource.
     */
    public static File resourceToFile(String resourcePath) {
        URL url = Thread.currentThread()
                .getContextClassLoader()
                .getResource(resourcePath);

        if (url == null) {
            throw new IllegalArgumentException(
                    "Resource not found: " + resourcePath);
        }

        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URI for resource: " + resourcePath, e);
        }
    }    

}
