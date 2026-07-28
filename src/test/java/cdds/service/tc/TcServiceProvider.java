package cdds.service.tc;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.Telecommand.TelecommandMessage;
import ccsds.cdds.v1.Telecommand.TelecommandReport;
import ccsds.cdds.v1.Types.NoArg;
import ccsds.cdds.v1.tc.CddsTcService;
import ccsds.cdds.v1.tc.CddsTcService.TcServiceEndpoint;
import ccsds.cdds.v1.tc.CddsTcService.TcServiceEndpointList;
import ccsds.cdds.v1.tc.TcServiceProviderGrpc.TcServiceProviderImplBase;
import cdds.service.common.GrpcUtil;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProtoJsonUtil;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

/**
 * Simple TC test server. Responds to TC radiation requests with ACK and RADIATED.
 * Report requests are responded with a report.
 */
public class TcServiceProvider extends TcServiceProviderImplBase implements InterceptedService {

    private static final Logger LOG = LogManager.getLogger("cdds.tc.provider");

    private final TcServiceAuthorization tcAuthorization = new TcServiceAuthorization();

    private final List<TcServiceEndpoint> tcEndpoints = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void getEndpoints(NoArg request, StreamObserver<TcServiceEndpointList> responseObserver) {
        LOG.info("get endpoints called. Total endpoints: " + tcEndpoints.size());

        CddsTcService.TcServiceEndpointList.Builder endpoints = TcServiceEndpointList.newBuilder();

        try {
            X509Certificate cert = TcServiceAuthorization.CLIENT_CERT.get();
            for(TcServiceEndpoint endpoint : tcEndpoints) {
                if(GrpcUtil.endpointAuthorized(cert, endpoint.getServiceUser())) {
                    endpoints.addEndpoints(endpoint);
                    LOG.info("TC endpoint authorized for " + cert.getSubjectX500Principal().getName());
                } else {
                    LOG.warn("TC endpoint not authorized for cert: " + cert);
                }
            }    
            
            responseObserver.onNext(endpoints.build());
            responseObserver.onCompleted();
        } catch(Exception ex) {
            LOG.warn("TM Endpoint authorization: ", ex);
            responseObserver.onError(ex);
        }
    }

    @Override
    public StreamObserver<TelecommandMessage> openTelecommandEndpoint(StreamObserver<TelecommandReport> tcUserStream) {
        
        try {
            byte[] endpointBytes = TcServiceAuthorization.TC_ENDPOINT_CTX_KEY.get();            // get the tc-endpoint-bin meta data
            TcServiceEndpoint tcEndpoint = ProtoJsonUtil.fromJson(endpointBytes, TcServiceEndpoint.newBuilder());    // decode the endpoint from JSON

            try {
                X509Certificate cert = TcServiceAuthorization.CLIENT_CERT.get();
                LOG.info("Open TC stream for endpoint, SAN: " + cert.getSubjectX500Principal().getName()
                            + "\n" + tcEndpoint);
            } catch(Exception ex) {
                // OK for unauthenticated tests
                LOG.info("Open TC stream for endpoint\n" + tcEndpoint);
            }
            // in this simple example the TC Provider has only one (static) endpoint.
            return new TcServiceEndpointStream(tcUserStream, tcEndpoint);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public BindableService getBindableService() {
        return this;
    }

    @Override
    public ServerInterceptor getServiceInterceptor() {
        return tcAuthorization;
    }

   /**
     * Add an allowed TC endpoint
     * @param tcEndpoint
     */
    public void addAuthorizedTcEndpoint(TcServiceEndpoint tcEndpoint) {
        tcAuthorization.addAuthorizedTcEndpoint(tcEndpoint);
        tcEndpoints.add(tcEndpoint);
    }

    /**
     * Removes an allowed TC endpoint
     * @param tcEndpoint
     */
    public void removeAuthorizedTcEndpoint(TcServiceEndpoint tcEndpoint) {
       tcAuthorization.removeAuthorizedTcEndpoint(tcEndpoint);
       tcEndpoints.remove(tcEndpoint);
    }

}
