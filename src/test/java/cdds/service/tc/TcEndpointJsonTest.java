package cdds.service.tc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.protobuf.InvalidProtocolBufferException;

import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;

public class TcEndpointJsonTest {

    @Test
    public void testJsonEndpoint() throws InvalidProtocolBufferException {
        final TcServiceEndpoint tcEndpointOne = TcServiceEndpoint.newBuilder()
            .setServiceProvider("myProvider")
            .setTerminal("myGroundStation")
            .setServiceUser("mySpacecraft")
            .setTcVcId(0)
            .build();   
            
        String tcEndpointJson = TcEndpointJson.tcEndpointToJson(tcEndpointOne);
        
        System.out.println(tcEndpointJson);

        final TcServiceEndpoint tcEndpointTwo = TcEndpointJson.tcEndpointFromJson(tcEndpointJson);

        assertTrue(tcEndpointOne.equals(tcEndpointTwo));
    }
}
