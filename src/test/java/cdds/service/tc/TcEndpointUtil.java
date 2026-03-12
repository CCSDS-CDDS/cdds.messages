package cdds.service.tc;

import ccsds.cdds.Types.GvcId;
import ccsds.cdds.Types.GvcIdList;
import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;

/**
 * Utility for TC endpoints
 */
public class TcEndpointUtil {

    /**
     * Get CLTU/GVCID string of that endpoint
     * @param tcEndpoint
     * @return the string with the endpoint
     */
    public static String getEndpointType(TcServiceEndpoint tcEndpoint) {
        if(tcEndpoint.hasGvcIds()) {
            return getGvcId(tcEndpoint.getGvcIds());
        } else if(tcEndpoint.hasCltu()) {
            return "cltu";
        }

        return "unknown endpoint type";
    }

    /**
     * Get the string of a GVCID list
     * 
     * @param gvcIdList
     * @return the string
     */
    public static String getGvcId(GvcIdList gvcIdList) {
        StringBuilder gvcId = new StringBuilder();
        try {
            for (int idx = 0; idx < gvcIdList.getGvcIdCount(); idx++) {
                gvcId.append(getGvcId(gvcIdList.getGvcId(idx)));

                if (idx == gvcIdList.getGvcIdCount() - 2 && idx > 0) {
                    gvcId.append(",");
                }
            }
        } catch (Exception e) {
            return "Error reading GVCID list: " + e;
        }

        return gvcId.toString();
    }

    /**
     * Returns a String representing the given GVCID
     * @param gvcId
     * @return the string
     */
    public static String getGvcId(GvcId gvcId) {
        String vc = "";
        if(gvcId.hasVirtualChannelId()) {
            vc = ".vc=" + gvcId.getVirtualChannelId();
        }
        return "sc=" + gvcId.getSpacecraftId() + ".frame-version=" + gvcId.getVersion()+ vc;
    }
}
