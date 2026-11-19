package cdds.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import cdds.cdds.v1.CddsConfigurationEndpoints.CddsEndpoints;
import ccsds.cdds.v1.CddsConfigurationServiceProvider.ServiceConfiguration;
import cdds.service.common.ProtoJsonUtil;

/**
 * Unit test verifying the JSON round trip of the example files in
 * src/test/resources/grpc-config: each example file is read into the
 * corresponding Protobuf message, written back out to JSON and checked
 * to be the same as the JSON read from the file.
 * <p>
 * The examples are compliant with the JSON schemas in schema/:
 * cdds-configuration-service-provider-example.json with cdds-configuration-service-provider.schema.json
 * (ServiceConfiguration) and cdds-configuration-endpoints-example.json with
 * cdds-configuration-endpoints.schema.json (CddsEndpoints). The schema compliance
 * of the example files is verified with the JSON schema validator; the schemas
 * are loaded from schema/ and registered under their $id so that the relative
 * $refs between the schemas resolve offline.
 */
public class GrpcConfigJsonTest {

    /**
     * Reads the service provider example grpc-config/cdds-configuration-service-provider-example.json
     * into a ServiceConfiguration message, writes the message back to JSON and
     * verifies that the JSON round trip is lossless: the re-serialized JSON is
     * equal to the JSON of the file and parses back into an equal message.
     */
    @Test
    public void testServiceProviderExampleJsonRoundTrip() throws InvalidProtocolBufferException, IOException {
        System.out.println("Testing JSON round trip of cdds-configuration-service-provider-example.json into ServiceConfiguration and back");

        final String exampleJson = readResource("/grpc-config/cdds-configuration-service-provider-example.json");

        final ServiceConfiguration configuration =
                ProtoJsonUtil.fromJson(exampleJson, ServiceConfiguration.newBuilder());

        final String roundTripJson = ProtoJsonUtil.toJson(configuration);

        System.out.println("ServiceConfiguration");
        System.out.println(roundTripJson);

        assertEquals(readTree(exampleJson), readTree(roundTripJson),
                "The JSON written out differs from the example file JSON");

        final ServiceConfiguration configurationRoundTrip =
                ProtoJsonUtil.fromJson(roundTripJson, ServiceConfiguration.newBuilder());

        assertEquals(configuration, configurationRoundTrip);
    }

    /**
     * Reads the endpoints example grpc-config/cdds-configuration-endpoints-example.json into a
     * CddsEndpoints message, writes the message back to JSON and verifies that
     * the JSON round trip is lossless: the re-serialized JSON is equal to the
     * JSON of the file and parses back into an equal message.
     */
    @Test
    public void testEndpointsExampleJsonRoundTrip() throws InvalidProtocolBufferException, IOException {
        System.out.println("Testing JSON round trip of cdds-configuration-endpoints-example.json into CddsEndpoints and back");

        final String exampleJson = readResource("/grpc-config/cdds-configuration-endpoints-example.json");

        final CddsEndpoints endpoints =
                ProtoJsonUtil.fromJson(exampleJson, CddsEndpoints.newBuilder());

        final String roundTripJson = ProtoJsonUtil.toJson(endpoints);

        System.out.println("CddsEndpoints");
        System.out.println(roundTripJson);

        assertEquals(readTree(exampleJson), readTree(roundTripJson),
                "The JSON written out differs from the example file JSON");

        final CddsEndpoints endpointsRoundTrip =
                ProtoJsonUtil.fromJson(roundTripJson, CddsEndpoints.newBuilder());

        assertEquals(endpoints, endpointsRoundTrip);
    }

    /**
     * Verifies that the service provider example
     * grpc-config/cdds-configuration-service-provider-example.json is compliant
     * with the JSON schema schema/cdds-configuration-service-provider.schema.json.
     */
    @Test
    public void testServiceProviderExampleJsonSchemaCompliance() throws IOException {
        System.out.println("Testing schema compliance of cdds-configuration-service-provider-example.json with cdds-configuration-service-provider.schema.json");

        final String exampleJson = readResource("/grpc-config/cdds-configuration-service-provider-example.json");

        assertCompliantWithSchema(exampleJson, "cdds-configuration-service-provider.schema.json");
    }

    /**
     * Verifies that the endpoints example
     * grpc-config/cdds-configuration-endpoints-example.json is compliant
     * with the JSON schema schema/cdds-configuration-endpoints.schema.json.
     */
    @Test
    public void testEndpointsExampleJsonSchemaCompliance() throws IOException {
        System.out.println("Testing schema compliance of cdds-configuration-endpoints-example.json with cdds-configuration-endpoints.schema.json");

        final String exampleJson = readResource("/grpc-config/cdds-configuration-endpoints-example.json");

        assertCompliantWithSchema(exampleJson, "cdds-configuration-endpoints.schema.json");
    }

    /**
     * Validates a JSON string against a JSON schema file of the schema directory.
     *
     * @param exampleJson the JSON to validate
     * @param schemaFileName the schema file name in the schema directory
     */
    private static void assertCompliantWithSchema(final String exampleJson, final String schemaFileName)
            throws IOException {
        final JsonSchema schema = schemaFactory().getSchema(
                Files.readString(Path.of("schema", schemaFileName), StandardCharsets.UTF_8));

        final Set<ValidationMessage> validationMessages = schema.validate(readTree(exampleJson));

        for (final ValidationMessage validationMessage : validationMessages) {
            System.out.println(validationMessage);
        }

        assertTrue(validationMessages.isEmpty(),
                "The example JSON is not compliant with " + schemaFileName + ": " + validationMessages);
    }

    /**
     * Creates a JSON schema factory with all schemas of the schema directory
     * registered under their $id, so that the relative $refs between the
     * schemas (e.g. to cdds-types.schema.json or cdds-endpoint-tc.schema.json)
     * resolve offline.
     */
    private static JsonSchemaFactory schemaFactory() throws IOException {
        final Map<String, String> schemas = new HashMap<>();
        final ObjectMapper mapper = new ObjectMapper();

        try (Stream<Path> files = Files.list(Path.of("schema"))) {
            for (final Path schemaFile : files
                    .filter(f -> f.getFileName().toString().endsWith(".schema.json"))
                    .collect(Collectors.toList())) {
                final String schemaJson = Files.readString(schemaFile, StandardCharsets.UTF_8);
                schemas.put(mapper.readTree(schemaJson).get("$id").asText(), schemaJson);
            }
        }

        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                factoryBuilder -> factoryBuilder.schemaLoaders(loaders -> loaders.schemas(schemas)));
    }

    /**
     * Reads a classpath resource into a string.
     *
     * @param resourceName the classpath resource name, e.g. /grpc-config/cdds-configuration-endpoints-example.json
     * @return the resource content as UTF-8 string
     */
    private static String readResource(final String resourceName) throws IOException {
        try (InputStream in = GrpcConfigJsonTest.class.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourceName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Parses a JSON string into a Jackson tree for structural comparison.
     */
    private static JsonNode readTree(final String json) throws IOException {
        return new ObjectMapper().readTree(json);
    }
}
