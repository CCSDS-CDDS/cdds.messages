package cdds.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;

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
 * configuration-service-provider-example.json with cdds-configuration-service-provider.schema.json
 * (ServiceConfiguration) and configuration-endpoints-example.json with
 * cdds-configuration-endpoints.schema.json (CddsEndpoints).
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
