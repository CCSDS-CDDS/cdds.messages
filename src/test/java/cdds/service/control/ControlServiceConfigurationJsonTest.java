package cdds.service.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.v1.CddsServiceProvider.ServiceProviderAddress;
import ccsds.cdds.v1.Types.ObjectIdentifier;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceConfiguration;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpoint;
import ccsds.cdds.v1.control.CddsControlService.ControlServiceEndpointList;
import ccsds.cdds.v1.control.CddsControlService.FunctionalResourceDirectiveName;
import cdds.service.common.ProtoJsonUtil;

/**
 * Unit test verifying the JSON serialization of the
 * {@link ControlServiceConfiguration} message.
 * <p>
 * The ControlServiceConfiguration is the configuration of a control service
 * shared between CDDS control user and CDDS control provider. The test
 * verifies that a ControlServiceConfiguration example can be written to
 * JSON and read back without loss, both in memory and via a JSON file.
 */
public class ControlServiceConfigurationJsonTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies the in-memory JSON round trip of a ControlServiceConfiguration:
     * the message is converted to JSON and parsed back into an equal message.
     * The JSON is printed on the command line.
     */
    @Test
    public void testJsonControlServiceConfiguration() throws InvalidProtocolBufferException {
        final ControlServiceConfiguration configurationOne = createControlServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        System.out.println("ControlServiceConfiguration");
        System.out.println(configurationJson);

        final ControlServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJson, ControlServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Verifies the JSON file round trip of a ControlServiceConfiguration:
     * the message is written to a JSON file, the file is read back and the
     * JSON read from the file is parsed into an equal message.
     */
    @Test
    public void testJsonControlServiceConfigurationFile() throws InvalidProtocolBufferException, IOException {
        final ControlServiceConfiguration configurationOne = createControlServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        // write the JSON to a file and read it back
        final Path jsonFile = tempDir.resolve("control-service-configuration.json");
        Files.writeString(jsonFile, configurationJson, StandardCharsets.UTF_8);

        final String configurationJsonRead = Files.readString(jsonFile, StandardCharsets.UTF_8);

        assertEquals(configurationJson, configurationJsonRead);

        // parse the JSON read from the file and verify it is the same object as written
        final ControlServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJsonRead, ControlServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Creates a ControlServiceConfiguration example consisting of:
     *      1) the gRPC server address of the CDDS control provider including the
     *         file names of the certificates and the private key used for mTLS
     *      2) a list of two control service endpoints; one endpoint requesting
     *         functional resource directives identified by directive OID with
     *         and without Functional Resource instance number and reporting
     *         generic directive identifiers, and one endpoint requesting
     *         generic directives only
     *
     * @return the ControlServiceConfiguration example
     */
    private static ControlServiceConfiguration createControlServiceConfiguration() {
        final ServiceProviderAddress gRpcServer = ServiceProviderAddress.newBuilder()
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setAddress("my-provider.example.com")
            .setPort(8443)
            .setRootCertificateFile("cert/cdds-ca.pem")
            .setCertificateFile("cert/cdds-user.pem")
            .setPrivateKeyFile("cert/cdds-user.key")
            .build();

        final ControlServiceEndpoint controlEndpointOne = ControlServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .addFunctionalResourceDirectives(FunctionalResourceDirectiveName.newBuilder()
                .setFunctionalResourceDirective(createObjectIdentifier("my.fr-directive-all-instances", "1.2.3.6.1"))
                .build())
            .addFunctionalResourceDirectives(FunctionalResourceDirectiveName.newBuilder()
                .setFunctionalResourceDirective(createObjectIdentifier("my.fr-directive-instance", "1.2.3.6.2"))
                .setFunctionalResourceInstance(2)
                .build())
            .addGenericDirectiveIds("my-generic-directive-one")
            .addGenericDirectiveIds("my-generic-directive-two")
            .setEndpointName("myGroundStation.mySpacecraft.control.fr-directives")
            .build();

        final ControlServiceEndpoint controlEndpointTwo = ControlServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setServiceUser("mySpacecraft")
            .addGenericDirectiveIds("my-generic-directive-three")
            .setEndpointName("myGroundStation.mySpacecraft.control.generic-directives")
            .build();

        return ControlServiceConfiguration.newBuilder()
            .setGRpcServer(gRpcServer)
            .setEndpoints(ControlServiceEndpointList.newBuilder()
                .addEndpoints(controlEndpointOne)
                .addEndpoints(controlEndpointTwo)
                .build())
            .build();
    }

    /**
     * Creates an ObjectIdentifier example for the given OID string in
     * dotted notation. The description is not part of the ObjectIdentifier
     * message; it documents the identified directive at the call site.
     *
     * @param description the description of the identified directive
     * @param oidString the OID string in dotted notation
     * @return the ObjectIdentifier example
     */
    private static ObjectIdentifier createObjectIdentifier(final String description, final String oidString) {
        final ObjectIdentifier.Builder oid = ObjectIdentifier.newBuilder()
            .setOidString(description);

        for (final String arc : oidString.split("\\.")) {
            oid.addArcs(Long.parseLong(arc));
        }

        return oid.build();
    }
}
