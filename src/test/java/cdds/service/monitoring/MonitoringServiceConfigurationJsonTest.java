package cdds.service.monitoring;

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
import ccsds.cdds.v1.Types.ObjectIdentifier;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.EventIdentification;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.FunctionalResourceEventName;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.FunctionalResourceParameterName;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringMode;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceConfiguration;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpoint;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.MonitoringServiceEndpointList;
import ccsds.cdds.v1.monitoring.CddsMonitoringService.ParameterIdentification;
import cdds.service.common.ProtoJsonUtil;

/**
 * Unit test verifying the JSON serialization of the
 * {@link MonitoringServiceConfiguration} message.
 * <p>
 * The MonitoringServiceConfiguration is the configuration of a monitoring
 * service shared between CDDS monitoring user and CDDS monitoring provider.
 * The test verifies that a MonitoringServiceConfiguration example can be
 * written to JSON and read back without loss, both in memory and via a JSON
 * file, that the JSON example in the test resources is equal to the example
 * created by this test, and that the generated JSON is compliant with the
 * JSON schema derived from the Protobuf definition.
 */
public class MonitoringServiceConfigurationJsonTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies the in-memory JSON round trip of a MonitoringServiceConfiguration:
     * the message is converted to JSON and parsed back into an equal message.
     * The JSON is printed on the command line.
     */
    @Test
    public void testJsonMonitoringServiceConfiguration() throws InvalidProtocolBufferException {
        final MonitoringServiceConfiguration configurationOne = createMonitoringServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        System.out.println("MonitoringServiceConfiguration");
        System.out.println(configurationJson);

        final MonitoringServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJson, MonitoringServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Verifies the JSON file round trip of a MonitoringServiceConfiguration:
     * the message is written to a JSON file, the file is read back and the
     * JSON read from the file is parsed into an equal message.
     */
    @Test
    public void testJsonMonitoringServiceConfigurationFile() throws InvalidProtocolBufferException, IOException {
        final MonitoringServiceConfiguration configurationOne = createMonitoringServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        // write the JSON to a file and read it back
        final Path jsonFile = tempDir.resolve("monitoring-service-configuration.json");
        Files.writeString(jsonFile, configurationJson, StandardCharsets.UTF_8);

        final String configurationJsonRead = Files.readString(jsonFile, StandardCharsets.UTF_8);

        assertEquals(configurationJson, configurationJsonRead);

        // parse the JSON read from the file and verify it is the same object as written
        final MonitoringServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJsonRead, MonitoringServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Verifies the MonitoringServiceConfiguration JSON example in the test
     * resources: the example src/test/resources/grpc-config/monitoring-service-configuration.json
     * is read from the classpath, parsed into a MonitoringServiceConfiguration
     * and verified to be equal to the MonitoringServiceConfiguration example
     * created by this test.
     */
    @Test
    public void testJsonMonitoringServiceConfigurationExample() throws InvalidProtocolBufferException, IOException {
        final String exampleJson;
        try (InputStream exampleStream =
                getClass().getResourceAsStream("/grpc-config/monitoring-service-configuration.json")) {
            exampleJson = new String(exampleStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        final MonitoringServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(exampleJson, MonitoringServiceConfiguration.newBuilder());

        assertTrue(createMonitoringServiceConfiguration().equals(configurationTwo));
    }

    /**
     * Verifies that the JSON generated from the MonitoringServiceConfiguration
     * example is compliant with the JSON schema derived from the Protobuf
     * definition: the JSON serialization of the message is validated against
     * the JSON schema schema/cdds-monitoring-service-configuration.schema.json
     * referencing the shared types of schema/cdds-types.schema.json.
     */
    @Test
    public void testJsonSchemaCompliance() throws InvalidProtocolBufferException, IOException {
        final MonitoringServiceConfiguration configuration = createMonitoringServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configuration);

        // register the shared schemas so the $refs to cdds-types.schema.json and
        // cdds-grpc-service-provider-address.schema.json resolve offline
        final String typesSchemaJson =
                Files.readString(Path.of("schema", "cdds-types.schema.json"), StandardCharsets.UTF_8);
        final String providerAddressSchemaJson =
                Files.readString(Path.of("schema", "cdds-grpc-service-provider-address.schema.json"), StandardCharsets.UTF_8);
        final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                factoryBuilder -> factoryBuilder.schemaLoaders(loaders -> loaders.schemas(java.util.Map.of(
                        "https://ccsds.org/cdds/v1/schemas/cdds-types.schema.json", typesSchemaJson,
                        "https://ccsds.org/cdds/v1/schemas/cdds-grpc-service-provider-address.schema.json", providerAddressSchemaJson))));

        final JsonSchema schema = factory.getSchema(
                Files.readString(Path.of("schema", "cdds-monitoring-service-configuration.schema.json"), StandardCharsets.UTF_8));

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
     * Creates a MonitoringServiceConfiguration example consisting of:
     *      1) the gRPC server address of the CDDS monitoring provider including the
     *         file names of the certificates and the private key used for mTLS
     *      2) a list of two monitoring service endpoints; one cyclic endpoint
     *         monitoring Functional Resource parameters identified by OID, by
     *         Functional Resource Parameter Name and by parameter group and
     *         reporting Functional Resource events, and one on-change endpoint
     *         monitoring a generic parameter and an event group
     *
     * @return the MonitoringServiceConfiguration example
     */
    private static MonitoringServiceConfiguration createMonitoringServiceConfiguration() {
        final ServiceProviderAddress gRpcServer = ServiceProviderAddress.newBuilder()
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setAddress("my-provider.example.com")
            .setPort(8443)
            .setRootCertificateFile("cert/cdds-ca.pem")
            .setCertificateFile("cert/cdds-user.pem")
            .setPrivateKeyFile("cert/cdds-user.key")
            .build();

        final MonitoringServiceEndpoint monitoringEndpointOne = MonitoringServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .setMonitoringCycle(Duration.newBuilder()
                .setSeconds(10)
                .setMilliseconds(500)
                .build())
            .setMode(MonitoringMode.CYCLIC_MONITORING_MODE)
            .addMonitoringParameters(ParameterIdentification.newBuilder()
                .setFunctionalResourceParameterId(createObjectIdentifier("my.fr-parameter-id", "1.2.3.4.5"))
                .build())
            .addMonitoringParameters(ParameterIdentification.newBuilder()
                .setFunctionalResourceParameterName(FunctionalResourceParameterName.newBuilder()
                    .setFunctionalResourceParameterIdentifier(createObjectIdentifier("my.fr-parameter-name-id", "1.2.3.4.6"))
                    .setFunctionalResourceInstance(2)
                    .build())
                .build())
            .addMonitoringParameters(ParameterIdentification.newBuilder()
                .setParameterGroup("myParameterGroup")
                .build())
            .addReportedEvents(EventIdentification.newBuilder()
                .setFunctionalResourceEventId(createObjectIdentifier("my.fr-event-id", "1.2.3.5.1"))
                .build())
            .addReportedEvents(EventIdentification.newBuilder()
                .setFunctionalResourceEventName(FunctionalResourceEventName.newBuilder()
                    .setFunctionalResourceEventId(createObjectIdentifier("my.fr-event-name-id", "1.2.3.5.2"))
                    .setFunctionalResourceInstance(2)
                    .build())
                .build())
            .setQos(EnumQoS.EXACTLY_ONCE)
            .setEndpointName("myGroundStation.mySpacecraft.mon.cyclic")
            .build();

        final MonitoringServiceEndpoint monitoringEndpointTwo = MonitoringServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .setMonitoringCycle(Duration.newBuilder()
                .setSeconds(1)
                .build())
            .setMode(MonitoringMode.ON_CHANGE_MONITORING_MODE)
            .addMonitoringParameters(ParameterIdentification.newBuilder()
                .setGenericParameterIdentifier("myGenericParameter")
                .build())
            .addReportedEvents(EventIdentification.newBuilder()
                .setEventGroup("myEventGroup")
                .build())
            .setQos(EnumQoS.AT_LEAST_ONCE)
            .setEndpointName("myGroundStation.mySpacecraft.mon.on-change")
            .build();

        return MonitoringServiceConfiguration.newBuilder()
            .setGRpcServer(gRpcServer)
            .setEndpoints(MonitoringServiceEndpointList.newBuilder()
                .addEndpoints(monitoringEndpointOne)
                .addEndpoints(monitoringEndpointTwo)
                .build())
            .build();
    }

    /**
     * Creates an ObjectIdentifier example for the given OID string in
     * dotted notation. The description is not part of the ObjectIdentifier
     * message; it documents the identified parameter or event at the
     * call site.
     *
     * @param description the description of the identified parameter or event
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
