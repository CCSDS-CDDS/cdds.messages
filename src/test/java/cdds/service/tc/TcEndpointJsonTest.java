package cdds.service.tc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.Types.FrameVersion;
import ccsds.cdds.Types.GvcId;
import ccsds.cdds.Types.GvcIdList;
import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;
import cdds.service.common.ProtoJsonUtil;

public class TcEndpointJsonTest {

    @Test
    public void testJsonEndpoint() throws InvalidProtocolBufferException {
        final TcServiceEndpoint tcEndpointOne = TcServiceEndpoint.newBuilder()
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .setGvcIds(GvcIdList.newBuilder().addGvcId(
                GvcId.newBuilder()
                    .setSpacecraftId(4711)
                    .setVersion(FrameVersion.TM_TC_SDLP)
                    .setVirtualChannelId(0)
                    .build())
                .build())
            .setServiceVersion(1)
            .build();   
            
        String tcEndpointJson = ProtoJsonUtil.toJson(tcEndpointOne);
        
        System.out.println(tcEndpointJson);

        final TcServiceEndpoint tcEndpointTwo = ProtoJsonUtil.fromJson(tcEndpointJson, TcServiceEndpoint.newBuilder());

        assertTrue(tcEndpointOne.equals(tcEndpointTwo));
    }
}
