package cdds.service.tc;

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
import ccsds.cdds.v1.Types.FrameVersion;
import ccsds.cdds.v1.Types.GvcId;
import ccsds.cdds.v1.Types.GvcIdList;
import ccsds.cdds.v1.tc.CddsTcService.TcServiceConfiguration;
import ccsds.cdds.v1.tc.CddsTcService.TcServiceEndpoint;
import ccsds.cdds.v1.tc.CddsTcService.TcServiceEndpointList;
import cdds.service.common.ProtoJsonUtil;

/**
 * Unit test verifying the JSON serialization of the
 * {@link TcServiceConfiguration} message.
 * <p>
 * The TcServiceConfiguration is the configuration of a telecommand service
 * shared between CDDS TC user and CDDS TC provider. The test verifies that a
 * TcServiceConfiguration example can be written to JSON and read back without
 * loss, both in memory and via a JSON file.
 */
public class TcServiceConfigurationJsonTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies the in-memory JSON round trip of a TcServiceConfiguration:
     * the message is converted to JSON and parsed back into an equal message.
     * The JSON is printed on the command line.
     */
    @Test
    public void testJsonTcServiceConfiguration() throws InvalidProtocolBufferException {
        final TcServiceConfiguration configurationOne = createTcServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        System.out.println("TcServiceConfiguration");
        System.out.println(configurationJson);

        final TcServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJson, TcServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Verifies the JSON file round trip of a TcServiceConfiguration:
     * the message is written to a JSON file, the file is read back and the
     * JSON read from the file is parsed into an equal message.
     * The JSON is printed on the command line.
     */
    @Test
    public void testJsonTcServiceConfigurationFile() throws InvalidProtocolBufferException, IOException {
        final TcServiceConfiguration configurationOne = createTcServiceConfiguration();

        final String configurationJson = ProtoJsonUtil.toJson(configurationOne);

        System.out.println("TcServiceConfiguration");
        System.out.println(configurationJson);

        // write the JSON to a file and read it back
        final Path jsonFile = tempDir.resolve("tc-service-configuration.json");
        Files.writeString(jsonFile, configurationJson, StandardCharsets.UTF_8);

        final String configurationJsonRead = Files.readString(jsonFile, StandardCharsets.UTF_8);

        assertEquals(configurationJson, configurationJsonRead);

        // parse the JSON read from the file and verify it is the same object as written
        final TcServiceConfiguration configurationTwo =
                ProtoJsonUtil.fromJson(configurationJsonRead, TcServiceConfiguration.newBuilder());

        assertTrue(configurationOne.equals(configurationTwo));
    }

    /**
     * Creates a TcServiceConfiguration example consisting of:
     *      1) the gRPC server address of the CDDS TC provider including the
     *         file names of the certificates and the private key used for mTLS
     *      2) a list of two TC service endpoints; one endpoint for uplink of
     *         CLTUs and one endpoint for uplink of TC frames requested by
     *         GVCID for TC space data link protocol virtual channel 0
     *
     * @return the TcServiceConfiguration example
     */
    private static TcServiceConfiguration createTcServiceConfiguration() {
        final ServiceProviderAddress gRpcServer = ServiceProviderAddress.newBuilder()
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setAddress("my-provider.example.com")
            .setPort(8443)
            .setRootCertificateFile("cert/cdds-ca.pem")
            .setCertificateFile("cert/cdds-user.pem")
            .setPrivateKeyFile("cert/cdds-user.key")
            .build();

        final TcServiceEndpoint tcEndpointOne = TcServiceEndpoint.newBuilder()
            .setServiceVersion(1)
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .setCltu(true)
            .setEndpointName("myGroundStation.mySpacecraft.cltu")
            .build();

        final TcServiceEndpoint tcEndpointTwo = TcServiceEndpoint.newBuilder()
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
            .setEndpointName("myGroundStation.mySpacecraft.vc0")
            .build();

        return TcServiceConfiguration.newBuilder()
            .setGRpcServer(gRpcServer)
            .setEndpoints(TcServiceEndpointList.newBuilder()
                .addEndpoints(tcEndpointOne)
                .addEndpoints(tcEndpointTwo)
                .build())
            .build();
    }
}
