package cdds.service.common;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import io.grpc.Grpc;
import io.grpc.ServerCall;

public class GrpcUtil {
   /**
     * Get the peer certificate fromt eh call and extract the SANs
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

}
