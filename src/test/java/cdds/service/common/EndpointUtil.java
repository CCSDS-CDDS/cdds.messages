package cdds.service.common;

import ccsds.cdds.v1.Types.GvcId;
import ccsds.cdds.v1.Types.GvcIdList;
import ccsds.cdds.v1.tc.CddsTcService.TcServiceEndpoint;

public class EndpointUtil {
    public static final String EQ = "=";
    public static final String DOT = ".";

    public static final String SC = "sc" + EQ;
    public static final String TFV = "tfv" + EQ;
    public static final String VC = "vc" + EQ;

    public static String toString(TcServiceEndpoint ep) {
        return toString(null, ep);
    }

    public static String toString(String prefix, TcServiceEndpoint ep) {
        StringBuilder s = new StringBuilder();

        if(prefix != null) {
            s.append(prefix + DOT);
        }
        
        s.append(ep.getServiceProvider() + DOT);
        if(ep.hasTerminal()) {
            s.append(ep.getTerminal() + DOT);
        }
        s.append(ep.getServiceUser());
        
        if(ep.hasCltu()) {
            s.append(DOT + "cltu=true");
        } else if(ep.hasGvcIds()) {
            s.append(EndpointUtil.toString(ep.getGvcIds()));
        } else {
            s.append("no tc type set" + DOT);
        }

        if(ep.hasEndpointName()) {
            s.append(ep.getEndpointName());
        }

        return s.toString();
    }

    /** 
     * String for given GVC ID List
     */
    public static String toString(GvcIdList gvcIds) {
        StringBuilder s = new StringBuilder();

        for(GvcId gvcId : gvcIds.getGvcIdList()) {
            s.append(DOT + SC + gvcId.getSpacecraftId() + DOT);
            s.append(TFV + gvcId.getVersion() + DOT);
            s.append(VC + gvcId.getVirtualChannelId());
        }

        return s.toString();
    }
}
