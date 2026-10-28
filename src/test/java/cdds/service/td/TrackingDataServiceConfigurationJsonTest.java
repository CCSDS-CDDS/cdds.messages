package cdds.service.td;

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
import ccsds.cdds.v1.Types.Duration;
import ccsds.cdds.v1.Types.EnumQoS;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataFormat;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataType;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceConfiguration;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpoint;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpointList;
import cdds.service.common.ProtoJsonUtil;

/**
 * Unit test verifying the JSON serialization of the
 * {@link TrackingDataServiceConfiguration} message.
 * <p>
 * The TrackingDataServiceConfiguration is the configuration of a tracking data
 * service shared between CDDS tracking data user and CDDS tracking data
 * provider. The test verifies that a TrackingDataServiceConfiguration example
 * can be written to JSON and read back without loss, both in memory and via
 * a JSON file, that the JSON example in the test resources is equal to the
 * example created by this test, and that the generated JSON is compliant with
 * the JSON schema derived from the Protobuf definition.
 */
public class TrackingDataServiceConfigurationJsonTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies the in-memory JSON round trip of a TrackingDataServiceConfiguration:
     * the message is converted to JSON and parsed back into an equal message.
     * The JSON is printed on the command line.
     */
    @Test
    public void testJsonTrackingDataServiceConfiguration() throws InvalidProtocolBufferException {
        final TrackingDataServiceConfiguration configurationOne = createTrackingDataServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        System.out.println("TrackingDataServiceConfiguration");
        System.out.println(configurationJson);

        final TrackingDataServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJson, TrackingDataServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Verifies the JSON file round trip of a TrackingDataServiceConfiguration:
     * the message is written to a JSON file, the file is read back and the
     * JSON read from the file is parsed into an equal message.
     */
    @Test
    public void testJsonTrackingDataServiceConfigurationFile() throws InvalidProtocolBufferException, IOException {
        final TrackingDataServiceConfiguration configurationOne = createTrackingDataServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        // write the JSON to a file and read it back
        final Path jsonFile = tempDir.resolve("td-service-configuration.json");
        Files.writeString(jsonFile, configurationJson, StandardCharsets.UTF_8);

        final String configurationJsonRead = Files.readString(jsonFile, StandardCharsets.UTF_8);

        assertEquals(configurationJson, configurationJsonRead);

        // parse the JSON read from the file and verify it is the same object as written
        final TrackingDataServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJsonRead, TrackingDataServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Verifies the TrackingDataServiceConfiguration JSON example in the test
     * resources: the example src/test/resources/grpc-config/td-service-configuration.json
     * is read from the classpath, parsed into a TrackingDataServiceConfiguration
     * and verified to be equal to the TrackingDataServiceConfiguration example
     * created by this test.
     */
    @Test
    public void testJsonTrackingDataServiceConfigurationExample() throws InvalidProtocolBufferException, IOException {
        final String exampleJson;
        try (InputStream exampleStream =
                getClass().getResourceAsStream("/grpc-config/td-service-configuration.json")) {
            exampleJson = new String(exampleStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        final TrackingDataServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(exampleJson, TrackingDataServiceConfiguration.newBuilder());

        assertTrue(createTrackingDataServiceConfiguration().equals(configurationTwo));
    }

    /**
     * Verifies that the JSON generated from the TrackingDataServiceConfiguration
     * example is compliant with the JSON schema derived from the Protobuf
     * definition: the JSON serialization of the message is validated against
     * the JSON schema grpc/cdds-td-service-configuration.schema.json
     * referencing the shared types of grpc/cdds-types.schema.json.
     */
    @Test
    public void testJsonSchemaCompliance() throws InvalidProtocolBufferException, IOException {
        final TrackingDataServiceConfiguration configuration = createTrackingDataServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configuration);

        // register the shared schemas so the $refs to cdds-types.schema.json and
        // cdds-grpc-service-provider-address.schema.json resolve offline
        final String typesSchemaJson =
                Files.readString(Path.of("grpc", "cdds-types.schema.json"), StandardCharsets.UTF_8);
        final String providerAddressSchemaJson =
                Files.readString(Path.of("grpc", "cdds-grpc-service-provider-address.schema.json"), StandardCharsets.UTF_8);
        final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                factoryBuilder -> factoryBuilder.schemaLoaders(loaders -> loaders.schemas(java.util.Map.of(
                        "https://ccsds.org/cdds/v1/schemas/cdds-types.schema.json", typesSchemaJson,
                        "https://ccsds.org/cdds/v1/schemas/cdds-grpc-service-provider-address.schema.json", providerAddressSchemaJson))));

        final JsonSchema schema = factory.getSchema(
                Files.readString(Path.of("grpc", "cdds-td-service-configuration.schema.json"), StandardCharsets.UTF_8));

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
     * Creates a TrackingDataServiceConfiguration example consisting of:
     *      1) the gRPC server address of the CDDS tracking data provider including the
     *         file names of the certificates and the private key used for mTLS
     *      2) a list of two tracking data service endpoints; one endpoint delivering
     *         Doppler and range tracking data in KVN format with a latency limit
     *         in seconds and milliseconds, and one endpoint delivering antenna
     *         angles and carrier power in Protobuf format with a latency limit
     *         in seconds and milliseconds
     *
     * @return the TrackingDataServiceConfiguration example
     */
    private static TrackingDataServiceConfiguration createTrackingDataServiceConfiguration() {
        final ServiceProviderAddress gRpcServer = ServiceProviderAddress.newBuilder()
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setAddress("my-provider.example.com")
            .setPort(8443)
            .setRootCertificateFile("cert/cdds-ca.pem")
            .setCertificateFile("cert/cdds-user.pem")
            .setPrivateKeyFile("cert/cdds-user.key")
            .build();

        final TrackingDataServiceEndpoint tdEndpointOne = TrackingDataServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .addTrackingDataType(TrackingDataType.DOPPLER_INSTANTANEOUS)
            .addTrackingDataType(TrackingDataType.DOPPLER_INTEGRATED)
            .addTrackingDataType(TrackingDataType.RANGE)
            .setTrackingDataFormat(TrackingDataFormat.KVN)
            .setLatencyLimit(Duration.newBuilder()
                .setSeconds(1)
                .setMilliseconds(500)
                .build())
            .setQos(EnumQoS.EXACTLY_ONCE)
            .setEndpointName("myGroundStation.mySpacecraft.td.kvn")
            .build();

        final TrackingDataServiceEndpoint tdEndpointTwo = TrackingDataServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .addTrackingDataType(TrackingDataType.ANTENNA_ANGLES)
            .addTrackingDataType(TrackingDataType.CARRIER_POWER)
            .setTrackingDataFormat(TrackingDataFormat.PROTOBUF)
            .setLatencyLimit(Duration.newBuilder()
                .setSeconds(2)
                .setMilliseconds(500)
                .build())
            .setQos(EnumQoS.AT_LEAST_ONCE)
            .setEndpointName("myGroundStation.mySpacecraft.td.protobuf")
            .build();

        return TrackingDataServiceConfiguration.newBuilder()
            .setGRpcServer(gRpcServer)
            .setEndpoints(TrackingDataServiceEndpointList.newBuilder()
                .addEndpoints(tdEndpointOne)
                .addEndpoints(tdEndpointTwo)
                .build())
            .build();
    }
}
