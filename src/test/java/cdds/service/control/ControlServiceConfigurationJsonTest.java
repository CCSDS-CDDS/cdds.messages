package cdds.service.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

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
 * verifies that a ControlServiceConfiguration example can be written to JSON
 * and read back without loss, both in memory and via a JSON file, that the
 * JSON example in the test resources is equal to the example created by
 * this test, and that the generated JSON is compliant with the JSON schema
 * derived from the Protobuf definition.
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
     * Verifies the ControlServiceConfiguration JSON example in the test
     * resources: the example src/test/resources/grpc-config/control-service-configuration.json
     * is read from the classpath, parsed into a ControlServiceConfiguration
     * and verified to be equal to the ControlServiceConfiguration example
     * created by this test.
     */
    @Test
    public void testJsonControlServiceConfigurationExample() throws InvalidProtocolBufferException, IOException {
        final String exampleJson;
        try (InputStream exampleStream =
                getClass().getResourceAsStream("/grpc-config/control-service-configuration.json")) {
            exampleJson = new String(exampleStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        final ControlServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(exampleJson, ControlServiceConfiguration.newBuilder());

        assertTrue(createControlServiceConfiguration().equals(configurationTwo));
    }

    /**
     * Verifies that the JSON generated from the ControlServiceConfiguration
     * example is compliant with the JSON schema derived from the Protobuf
     * definition: the JSON serialization of the message is validated against
     * the JSON schema grpc/cdds-control-service-configuration.schema.json
     * referencing the shared types of grpc/cdds-types.schema.json.
     */
    @Test
    public void testJsonSchemaCompliance() throws InvalidProtocolBufferException, IOException {
        final ControlServiceConfiguration configuration = createControlServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configuration);

        // register the shared types schema so the $refs to cdds-types.schema.json resolve offline
        final String typesSchemaJson =
                Files.readString(Path.of("grpc", "cdds-types.schema.json"), StandardCharsets.UTF_8);
        final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                factoryBuilder -> factoryBuilder.schemaLoaders(loaders -> loaders.schemas(java.util.Map.of(
                        "https://ccsds.org/cdds/v1/schemas/cdds-types.schema.json", typesSchemaJson))));

        final JsonSchema schema = factory.getSchema(
                Files.readString(Path.of("grpc", "cdds-control-service-configuration.schema.json"), StandardCharsets.UTF_8));

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode configurationJsonNode = mapper.readTree(configurationJson);

        final Set<ValidationMessage> validationMessages = schema.validate(configurationJsonNode);

        for (final ValidationMessage validationMessage : validationMessages) {
            System.out.println(validationMessage);
        }

        assertTrue(validationMessages.isEmpty(),
                "The generated JSON is not compliant with the JSON schema: " + validationMessages);
    }

    /**
     * Creates a ControlServiceConfiguration example consisting of:
     *      1) the gRPC server address of the CDDS control provider including the
     *         file names of the certificates and the private key used for mTLS
     *      2) a list of two control service endpoints; one endpoint invoking
     *         Functional Resource directives identified by directive OID with
     *         and without Functional Resource instance number, and one
     *         endpoint invoking generic control directives
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
            .setEndpointName("myGroundStation.mySpacecraft.ctl.fr-directives")
            .build();

        final ControlServiceEndpoint controlEndpointTwo = ControlServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .addGenericDirectiveIds("my-generic-directive-one")
            .addGenericDirectiveIds("my-generic-directive-two")
            .setEndpointName("myGroundStation.mySpacecraft.ctl.generic-directives")
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
            .setOidString(oidString);

        for (final String arc : oidString.split("\\.")) {
            oid.addArcs(Long.parseLong(arc));
        }

        return oid.build();
    }
}
