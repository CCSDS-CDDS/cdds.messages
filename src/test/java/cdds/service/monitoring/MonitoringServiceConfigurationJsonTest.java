package cdds.service.monitoring;

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
 * written to JSON and read back without loss, both in memory and via a
 * JSON file.
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
     * Creates a MonitoringServiceConfiguration example consisting of:
     *      1) the gRPC server address of the CDDS monitoring provider including the
     *         file names of the certificates and the private key used for mTLS
     *      2) a list of two monitoring service endpoints; one cyclic endpoint
     *         monitoring functional resource parameters identified by OID,
     *         functional resource parameter name and parameter group and
     *         reporting functional resource events, and one on-change endpoint
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
                .setGenericParameterIdentifier("my-generic-param")
                .build())
            .addMonitoringParameters(ParameterIdentification.newBuilder()
                .setParameterGroup("myParameterGroup")
                .build())
            .setMonitoringCycle(Duration.newBuilder()
                .setSeconds(10)
                .setMilliseconds(500)
                .build())
            .setMode(MonitoringMode.ON_CHANGE_MONITORING_MODE)
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
            .setEndpointName("myGroundStation.mySpacecraft.monitoring.on-change")
            .build();

        return MonitoringServiceConfiguration.newBuilder()
            .setGRpcServer(gRpcServer)
            .setEndpoints(MonitoringServiceEndpointList.newBuilder()
                .addEndpoints(monitoringEndpointOne)
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
            .setOidString(oidString);

        for (final String arc : oidString.split("\\.")) {
            oid.addArcs(Long.parseLong(arc));
        }

        return oid.build();
    }
}
