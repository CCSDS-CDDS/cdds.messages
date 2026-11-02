package cdds.service.td;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.naming.TimeLimitExceededException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ccsds.cdds.v1.Tracking.Format;
import ccsds.cdds.v1.Tracking.TrackingDataMessage;
import ccsds.cdds.v1.Tracking.TrackingDataMessage.MessageCase;
import ccsds.cdds.v1.Types.EnumQoS;
import ccsds.cdds.v1.Types.ProductionState;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpoint;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataServiceEndpointList;
import ccsds.cdds.v1.td.CddsTdService.TrackingDataType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.DataQualityType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.IntegrationRefType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.ModeType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TdmData;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TdmMetadata;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TdmType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TrackingDataObservationType;
import cdds.service.common.InterceptedService;
import cdds.service.common.ProviderServer;
import cdds.service.common.ProtoJsonUtil;

/**
 * Test the communication among a CDDS tracking data service user and a CDDS
 * tracking data service provider using the gRPC TrackingDataServiceProvider service.
 * <p>
 * Both tests start a secure (mTLS) CDDS tracking data provider on a local port,
 * register a tracking data service endpoint together with a tracking data
 * production and connect a tracking data service user using mutual TLS. The
 * user discovers the endpoints authorized for the authenticated service user
 * by calling getEndpoints, opens the tracking data endpoint by calling
 * openTrackingDataServiceEndpoint providing the endpoint as td-endpoint-bin
 * meta data and receives the tracking data messages produced by the production.
 * The first test uses an endpoint delivering the TDM of the example file
 * src/test/resources/TDM/TDM-ONE-WAY-DATA-EXAMPLE.txt as tdmText (KVN format), the second test
 * uses an endpoint delivering the same TDM as tdmProto (protobuf encoded TDM
 * equivalent to the KVN text). At the end of each test, the tracking data
 * messages received by the user are verified to be equal to the tracking data
 * messages sent by the production and to be equivalent to the TDM KVN example
 * file.
 */
public class TrackingDataServiceTest {

    private static final int PROVIDER_PORT = 7669;

    final long numTrackingDataMessages = 3;
    final long timeout = 30_000;

    final TrackingDataServiceEndpoint kvnEndpoint =
            TrackingDataServiceUser.getKvnTrackingDataEndpoint("myGroundStation.mySpacecraft.td.kvn",
            "myProvider",
            "myGroundStation",
            "theSpacecraft",
            1,
            500,
            EnumQoS.EXACTLY_ONCE,
            TrackingDataType.RECEIVE_FREQUENCY,
            TrackingDataType.TRANSMIT_FREQUENCY);

    final TrackingDataServiceEndpoint protobufEndpoint =
            TrackingDataServiceUser.getProtobufTrackingDataEndpoint("myGroundStation.mySpacecraft.td.protobuf",
            "myProvider",
            "myGroundStation",
            "theSpacecraft",
            2,
            500,
            EnumQoS.AT_LEAST_ONCE,
            TrackingDataType.RECEIVE_FREQUENCY,
            TrackingDataType.TRANSMIT_FREQUENCY);

    private static final Logger LOG = LogManager.getLogger("cdds.td.test");

    /**
     * Set once the endpoints have been printed in JSON to print the
     * endpoints only once for all tests of this class
     */
    private static boolean endpointsPrinted = false;

    @BeforeEach
    void before(TestInfo info) {
        LOG.info("START " + info.getDisplayName());
    }

    @AfterEach
    void after(TestInfo info) {
        LOG.info("END " + info.getDisplayName());
    }

    /**
     * Tests the delivery of a TDM in KVN text format (tdmText):
     * the user opens the KVN tracking data endpoint kvnEndpoint and receives
     * numTrackingDataMessages tracking data messages, each message carrying
     * the TDM of the example file src/test/resources/TDM/TDM-ONE-WAY-DATA-EXAMPLE.txt as
     * tdmText produced by the {@link TrackingDataProductionKvn} production.
     * <p>
     * The test verifies that getEndpoints returns exactly the endpoint
     * authorized for the authenticated service user, that the tracking data
     * messages received by the user are equal to the tracking data messages
     * sent by the production, and that the received KVN text reassembles to
     * the content of the TDM KVN example file.
     */
    @Test
    public void testKvnTrackingDataEndpoint()
            throws IOException, TimeLimitExceededException, InterruptedException, ExecutionException, TimeoutException {

        // create a tracking data provider and add a KVN tracking data production for the endpoint
        // producing numTrackingDataMessages
        final TrackingDataProductionKvn kvnProduction =
                new TrackingDataProductionKvn(numTrackingDataMessages);
        TrackingDataServiceProvider tdProvider = new TrackingDataServiceProvider();
        tdProvider.addTrackingDataProduction(kvnEndpoint, kvnProduction);

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{tdProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        tdProvider.addAuthorizedTrackingDataEndpoint(kvnEndpoint);

        final TrackingDataServiceUser tdServiceUser =
                TrackingDataServiceUser.buildSecureTrackingDataService("localhost", PROVIDER_PORT,
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        List<TrackingDataServiceEndpoint> endpoints = tdServiceUser.getEndpoints(5000);

        assert(endpoints.get(0).equals(kvnEndpoint));

        printEndpoints(endpoints);

        // use the first provided endpoint for this test, wait for numTrackingDataMessages
        tdServiceUser.openTrackingDataEndpoint(endpoints.get(0), numTrackingDataMessages);

        tdServiceUser.waitForTrackingDataMessages(timeout);

        verifyReceivedTrackingDataMessages(kvnProduction, tdServiceUser);

        // verify that the received KVN text TDMs are equivalent to the TDM KVN example file
        final String expectedKvn = TdmKvnUtil.normalizeKvn(TdmKvnUtil.readKvnResource(TdmKvnUtil.TDM_KVN_RESOURCE));
        for(final TrackingDataMessage message : tdServiceUser.getReceivedTrackingDataMessages()) {
            assert(message.getProductionState() == ProductionState.OPERATIONAL);
            assert(message.getMessageCase() == MessageCase.TDMTEXT);
            assert(message.getTdmText().getFormat() == Format.KVN);
            assert(TdmKvnUtil.toKvnText(message.getTdmText()).equals(expectedKvn));
        }

        LOG.info("Received KVN TDM:\n" + TdmKvnUtil.toKvnText(tdServiceUser.getReceivedTrackingDataMessages().get(0).getTdmText()));

        tdServiceUser.shutdown();

        server.stop();
    }

    /**
     * Tests the delivery of a TDM in protobuf format (tdmProto):
     * the user opens the protobuf tracking data endpoint protobufEndpoint and
     * receives numTrackingDataMessages tracking data messages, each message
     * carrying the TDM of the example file src/test/resources/TDM/TDM-ONE-WAY-DATA-EXAMPLE.txt
     * as tdmProto (protobuf encoded TDM equivalent to the KVN text) produced
     * by the {@link TrackingDataProductionProto} production.
     * <p>
     * The test verifies that getEndpoints returns exactly the endpoint
     * authorized for the authenticated service user, that the tracking data
     * messages received by the user are equal to the tracking data messages
     * sent by the production, and that the received tdmProto is equivalent
     * to the TDM KVN example file.
     */
    @Test
    public void testProtobufTrackingDataEndpoint()
            throws IOException, TimeLimitExceededException, InterruptedException, ExecutionException, TimeoutException {

        // create a tracking data provider and add a protobuf tracking data production for the endpoint
        // producing numTrackingDataMessages
        final TrackingDataProductionProto protoProduction =
                new TrackingDataProductionProto(numTrackingDataMessages);
        TrackingDataServiceProvider tdProvider = new TrackingDataServiceProvider();
        tdProvider.addTrackingDataProduction(protobufEndpoint, protoProduction);

        ProviderServer server = new ProviderServer(PROVIDER_PORT, new InterceptedService[]{tdProvider},
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.pem"),
                ProviderServer.resourceToFile("cert/cdds-provider.key"));

        server.start();
        tdProvider.addAuthorizedTrackingDataEndpoint(protobufEndpoint);

        final TrackingDataServiceUser tdServiceUser =
                TrackingDataServiceUser.buildSecureTrackingDataService("localhost", PROVIDER_PORT,
                ProviderServer.resourceToFile("cert/cdds-ca.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.pem"),
                ProviderServer.resourceToFile("cert/cdds-user.key"));

        List<TrackingDataServiceEndpoint> endpoints = tdServiceUser.getEndpoints(5000);

        assert(endpoints.get(0).equals(protobufEndpoint));

        printEndpoints(endpoints);

        // use the first provided endpoint for this test, wait for numTrackingDataMessages
        tdServiceUser.openTrackingDataEndpoint(endpoints.get(0), numTrackingDataMessages);

        tdServiceUser.waitForTrackingDataMessages(timeout);

        verifyReceivedTrackingDataMessages(protoProduction, tdServiceUser);

        // verify that the received protobuf TDMs are equivalent to the TDM KVN example file
        final TdmType expectedTdm = TdmKvnUtil.toTdmType(TdmKvnUtil.readKvnResource(TdmKvnUtil.TDM_KVN_RESOURCE));
        for(final TrackingDataMessage message : tdServiceUser.getReceivedTrackingDataMessages()) {
            assert(message.getProductionState() == ProductionState.OPERATIONAL);
            assert(message.getMessageCase() == MessageCase.TDMPROTO);
            assert(message.getTdmProto().equals(expectedTdm));
        }

        // spot-check the received protobuf TDM against the values of the TDM KVN example file
        final TdmType tdm = tdServiceUser.getReceivedTrackingDataMessages().get(0).getTdmProto();

        assert(tdm.getId().equals("TDM"));
        assert(tdm.getVersion().equals("2.0"));
        assert(tdm.getHeader().getCommentCount() == 2);
        assert(tdm.getHeader().getComment(0).equals("TDM example created by yyyyy-nnnA Nav Team (NASA/JPL)"));
        assert(tdm.getHeader().getComment(1).equals("StarTrek 1-way data, Ka band down"));
        assert(tdm.getHeader().getCreationDate().equals("2005-160T20:15:00Z"));
        assert(tdm.getHeader().getOriginator().equals("NASA"));

        final TdmMetadata metadata = tdm.getBody().getSegment(0).getMetadata();
        assert(metadata.getCommentCount() == 2);
        assert(metadata.getComment(0).equals("Data quality degraded by antenna pointing problem..."));
        assert(metadata.getComment(1).equals("Slightly noisy data"));
        assert(metadata.getTimeSystem().equals("UTC"));
        assert(metadata.getParticipant1().equals("DSS-25"));
        assert(metadata.getParticipant2().equals("yyyy-nnnA"));
        assert(metadata.getMode() == ModeType.MODE_TYPE_SEQUENTIAL);
        assert(metadata.getPath().equals("2,1"));
        assert(metadata.getIntegrationInterval() == 1.0);
        assert(metadata.getIntegrationRef() == IntegrationRefType.INTEGRATION_REF_TYPE_MIDDLE);
        assert(metadata.getFreqOffset() == 0.0);
        assert(metadata.getTransmitDelay1() == 0.000077);
        assert(metadata.getReceiveDelay1() == 0.000077);
        assert(metadata.getDataQuality() == DataQualityType.DATA_QUALITY_TYPE_DEGRADED);

        final TdmData data = tdm.getBody().getSegment(0).getData();
        assert(data.getCommentCount() == 1);
        assert(data.getComment(0).equals("TRANSMIT_FREQ_2 is spacecraft reference downlink"));
        assert(data.getObservationCount() == 30);

        final TrackingDataObservationType observationOne = data.getObservation(0);
        assert(observationOne.getEpoch().equals("2005-159T17:41:00"));
        assert(observationOne.getTransmitFreq2() == 32023442781.733);
        assert(observationOne.getReceiveFreq1() == 32021034790.7265);

        final TrackingDataObservationType observationTwo = data.getObservation(1);
        assert(observationTwo.getEpoch().equals("2005-159T17:41:01"));
        assert(observationTwo.getReceiveFreq1() == 32021034828.8432);

        final TrackingDataObservationType observationThirty = data.getObservation(29);
        assert(observationThirty.getEpoch().equals("2005-159T17:41:29"));
        assert(observationThirty.getReceiveFreq1() == 32021035894.5601);

        LOG.info("Received protobuf TDM:\n" + tdm);

        tdServiceUser.shutdown();

        server.stop();
    }

    /**
     * Prints the provided endpoints in JSON, only once for all tests
     * of this class
     * @param endpoints  The endpoints to print
     */
    private static void printEndpoints(List<TrackingDataServiceEndpoint> endpoints) throws IOException {
        if(endpointsPrinted == false) {
            endpointsPrinted = true;

            LOG.info("Endpoints:\n" + ProtoJsonUtil.toJson(TrackingDataServiceEndpointList.newBuilder()
                    .addAllEndpoints(endpoints)
                    .build()));
        }
    }

    /**
     * Verifies that the tracking data messages received by the tracking data
     * service user are equal to the tracking data messages sent by the
     * tracking data production and prints the number of received messages.
     * @param production  The tracking data production providing the endpoint data
     * @param user        The tracking data service user receiving the endpoint data
     */
    private static void verifyReceivedTrackingDataMessages(final TrackingDataProductionInterface production,
            final TrackingDataServiceUser user) {

        assert(production.getSentTrackingDataMessages().equals(user.getReceivedTrackingDataMessages()));

        LOG.info("Received tracking data messages: " + user.getReceivedTrackingDataMessages().size());
    }
}
