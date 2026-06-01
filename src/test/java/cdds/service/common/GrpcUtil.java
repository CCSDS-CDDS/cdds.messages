package cdds.service.common;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import io.grpc.Grpc;
import io.grpc.ServerCall;

public class GrpcUtil {
   private static final int DNS_NAME = 2;
   private static final int URI = 6;

   /**
     * Get the peer certificate from the call and extract the SANs
     * @param <ReqT>
     * @param <RespT>
     * @param call      The gRPC call from which the CERTs are extracted
     * @return          An array of SANs. May be empty.
     */
    public static <ReqT, RespT> List<String> getSan(ServerCall<ReqT, RespT> call) {

        List<String> sanList = new LinkedList<>();

        SSLSession sslSession = call.getAttributes()
                .get(Grpc.TRANSPORT_ATTR_SSL_SESSION);

        try {
            Certificate[] certs = sslSession.getPeerCertificates();
            X509Certificate clientCert = (X509Certificate) certs[0];

            // Extract SANs
            Collection<List<?>> sans = clientCert.getSubjectAlternativeNames();

            if (sans != null) {
                for (List<?> sanItem : sans) {
                    sanList.add(sanItem.get(1).toString());
                }
            }

        } catch (Exception e) {
            // OK, list of SAN potentially empty
        }

        return sanList;
    }

    /**
     * Get the peer certificates of the peer of the call
     * @param <ReqT>
     * @param <RespT>
     * @param call
     * @return Returns: an ordered array of peer certificates, with the peer's own certificate first followed by any certificate authorities.
     * @throws SSLPeerUnverifiedException
     */
    public static  <ReqT, RespT> Certificate[] getClientCert(ServerCall<ReqT, RespT> call) throws SSLPeerUnverifiedException {
        SSLSession sslSession =
                call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
        
        if(sslSession == null) {
            throw new SSLPeerUnverifiedException("No SSL session");
        }

        Certificate[] certs = sslSession.getPeerCertificates();

        if(certs == null) {
            throw new SSLPeerUnverifiedException("No certificates found in SSL session");
        }

        return certs;
    }
    
    /**
     * Checks endpoint authorization
     * @param userCert      The X.509 cert of the service user
     * @param serviceUser   The service user to authorize
     * @return              true is the service user could be authorized
     */
    public static boolean endpointAuthorized(X509Certificate userCert, String serviceUser) {
        try {
            Collection<List<?>> sans = userCert.getSubjectAlternativeNames();

            if (sans != null) {
                for (List<?> san : sans) {
                    Integer type = (Integer) san.get(0);
                    Object value = san.get(1);

                    if((type == DNS_NAME || type == URI) 
                        && value.equals(serviceUser)) {
                        return true;
                    }
                    
                }
            }
        } catch (Exception ex) {

        }
        return false;
    }

}
