package cdds.service.td;

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
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceConfiguration;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpoint;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpointList;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataFormat;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataType;
import cdds.service.common.ProtoJsonUtil;

/**
 * Unit test verifying the JSON serialization of the
 * {@link TrackingDataServiceConfiguration} message.
 * <p>
 * The TrackingDataServiceConfiguration is the configuration of a tracking
 * data service shared between CDDS tracking data user and CDDS tracking
 * data provider. The test verifies that a TrackingDataServiceConfiguration
 * example can be written to JSON and read back without loss, both in
 * memory and via a JSON file.
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
            .setEndpointName("myGroundStation.mySpacecraft.tracking-data.kvn.s-band")
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
            .build();

        final TrackingDataServiceEndpoint tdEndpointTwo = TrackingDataServiceEndpoint.newBuilder()
            .setEndpointName("myGroundStation.mySpacecraft.tracking-data.proto.s-band")
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
