package cdds.service.tm;

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
import ccsds.cdds.v1.Types.EnumQoS;
import ccsds.cdds.v1.Types.FrameQuality;
import ccsds.cdds.v1.Types.FrameVersion;
import ccsds.cdds.v1.Types.GvcId;
import ccsds.cdds.v1.Types.GvcIdList;
import ccsds.cdds.v1.tm.CddsTmService.TmServiceConfiguration;
import ccsds.cdds.v1.tm.CddsTmService.TmServiceEndpoint;
import ccsds.cdds.v1.tm.CddsTmService.TmServiceEndpointList;
import cdds.service.common.ProtoJsonUtil;

/**
 * Unit test verifying the JSON serialization of the
 * {@link TmServiceConfiguration} message.
 * <p>
 * The TmServiceConfiguration is the configuration of a telemetry service shared
 * between CDDS TM user and CDDS TM provider. The test verifies that a
 * TmServiceConfiguration example can be written to JSON and read back without
 * loss, both in memory and via a JSON file, and that the generated JSON is
 * compliant with the JSON schema derived from the Protobuf definition.
 */
public class TmServiceConfigurationJsonTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies the in-memory JSON round trip of a TmServiceConfiguration:
     * the message is converted to JSON and parsed back into an equal message.
     * The JSON is printed on the command line.
     */
    @Test
    public void testJsonTmServiceConfiguration() throws InvalidProtocolBufferException {
        final TmServiceConfiguration configurationOne = createTmServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        System.out.println("TmServiceConfiguration");
        System.out.println(configurationJson);

        final TmServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJson, TmServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Verifies the JSON file round trip of a TmServiceConfiguration:
     * the message is written to a JSON file, the file is read back and the
     * JSON read from the file is parsed into an equal message.
     * The JSON is printed on the command line.
     */
    @Test
    public void testJsonTmServiceConfigurationFile() throws InvalidProtocolBufferException, IOException {
        final TmServiceConfiguration configurationOne = createTmServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        System.out.println("TmServiceConfiguration");
        System.out.println(configurationJson);

        // write the JSON to a file and read it back
        final Path jsonFile = tempDir.resolve("tm-service-configuration.json");
        Files.writeString(jsonFile, configurationJson, StandardCharsets.UTF_8);

        final String configurationJsonRead = Files.readString(jsonFile, StandardCharsets.UTF_8);

        assertEquals(configurationJson, configurationJsonRead);

        // parse the JSON read from the file and verify it is the same object as written
        final TmServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJsonRead, TmServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Verifies the TmServiceConfiguration JSON example in the test resources:
     * the example src/test/resources/grpc-config/tm-service-configuration.json
     * is read from the classpath, parsed into a TmServiceConfiguration and
     * verified to be equal to the TmServiceConfiguration example created by
     * this test.
     */
    @Test
    public void testJsonTmServiceConfigurationExample() throws InvalidProtocolBufferException, IOException {
        final String exampleJson;
        try (InputStream exampleStream =
                getClass().getResourceAsStream("/grpc-config/tm-service-configuration.json")) {
            exampleJson = new String(exampleStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        final TmServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(exampleJson, TmServiceConfiguration.newBuilder());

        assertTrue(createTmServiceConfiguration().equals(configurationTwo));
    }

    /**
     * Verifies that the JSON generated from the TmServiceConfiguration example
     * is compliant with the JSON schema derived from the Protobuf definition:
     * the JSON serialization of the message is validated against the JSON schema
     * grpc/cdds-tm-service-configuration.schema.json referencing the shared
     * types of grpc/cdds-types.schema.json.
     */
    @Test
    public void testJsonSchemaCompliance() throws InvalidProtocolBufferException, IOException {
        final TmServiceConfiguration configuration = createTmServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configuration);

        // register the shared types schema so the $refs to cdds-types.schema.json resolve offline
        final String typesSchemaJson =
                Files.readString(Path.of("grpc", "cdds-types.schema.json"), StandardCharsets.UTF_8);
        final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                factoryBuilder -> factoryBuilder.schemaLoaders(loaders -> loaders.schemas(java.util.Map.of(
                        "https://ccsds.org/cdds/v1/schemas/cdds-types.schema.json", typesSchemaJson))));

        final JsonSchema schema = factory.getSchema(
                Files.readString(Path.of("grpc", "cdds-tm-service-configuration.schema.json"), StandardCharsets.UTF_8));

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
     * Creates a TmServiceConfiguration example consisting of:
     *      1) the gRPC server address of the CDDS TM provider including the
     *         file names of the certificates and the private key used for mTLS
     *      2) a list of three TM service endpoints; one endpoint requested by
     *         GVCID for USLP virtual channel 0, one endpoint requested by
     *         GVCID for USLP virtual channel 7 and one endpoint requested
     *         by frame quality
     *
     * @return the TmServiceConfiguration example
     */
    private static TmServiceConfiguration createTmServiceConfiguration() {
        final ServiceProviderAddress gRpcServer = ServiceProviderAddress.newBuilder()
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setAddress("my-provider.example.com")
            .setPort(8443)
            .setRootCertificateFile("cert/cdds-ca.pem")
            .setCertificateFile("cert/cdds-user.pem")
            .setPrivateKeyFile("cert/cdds-user.key")
            .build();

        final TmServiceEndpoint tmEndpointOne = TmServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .setGvcIds(GvcIdList.newBuilder().addGvcId(
                GvcId.newBuilder()
                    .setSpacecraftId(4711)
                    .setVersion(FrameVersion.USLP)
                    .setVirtualChannelId(0)
                    .build())
                .build())
            .setQos(EnumQoS.EXACTLY_ONCE)
            .setEndpointName("myGroundStation.mySpacecraft.tm.vc0")
            .build();

        final TmServiceEndpoint tmEndpointTwo = TmServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .setGvcIds(GvcIdList.newBuilder().addGvcId(
                GvcId.newBuilder()
                    .setSpacecraftId(4711)
                    .setVersion(FrameVersion.USLP)
                    .setVirtualChannelId(7)
                    .build())
                .build())
            .setQos(EnumQoS.AT_MOST_ONCE)
            .setEndpointName("myGroundStation.mySpacecraft.tm.vc7")
            .build();

        final TmServiceEndpoint tmEndpointThree = TmServiceEndpoint.newBuilder()
            .setEndpointName("myGroundStation.mySpacecraft.tm.good-frames")
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setServiceUser("mySpacecraft")
            .setFrameQuality(FrameQuality.GOOD)
            .setQos(EnumQoS.AT_LEAST_ONCE)
            .build();

        return TmServiceConfiguration.newBuilder()
            .setGRpcServer(gRpcServer)
            .setEndpoints(TmServiceEndpointList.newBuilder()
                .addEndpoints(tmEndpointOne)
                .addEndpoints(tmEndpointTwo)
                .addEndpoints(tmEndpointThree)
                .build())
            .build();
    }
}
