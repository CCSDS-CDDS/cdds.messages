package cdds.service.tc;

import java.nio.charset.StandardCharsets;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import ccsds.cdds.Types.GvcId;
import ccsds.cdds.Types.GvcIdList;
import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;

/**
 * Utility to convert TC endpoints to / from JSON
 */
public class TcEndpointUtil {

    /**
     * Convert a Protobuf TC Endpoint to JSON in UTF-8
     * @param tcEndpoint    The TC Endpoint to convert
     * @return              The UTF-8 bytes holding the JSON representation of the string
     * @throws InvalidProtocolBufferException
     */
    public static byte[] tcEndpointToJsonUtf8(TcServiceEndpoint tcEndpoint) throws InvalidProtocolBufferException {
        return tcEndpointToJson(tcEndpoint).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Convert the Protobuf TC endpoint to a JSON string
     * 
     * @param tcEndpoint The TC endpoint to convert
     * @return a JSON string representing the TC endpoint
     * @throws InvalidProtocolBufferException Thrown when the input contains unknown
     *                                        Any values.
     */
    public static String tcEndpointToJson(TcServiceEndpoint tcEndpoint) throws InvalidProtocolBufferException {
        String jsonString = JsonFormat.printer()
                .includingDefaultValueFields() // optional: include default values
                .preservingProtoFieldNames() // keep proto field names as-is
                .print(tcEndpoint);

        return jsonString;
    }

    /**
     * Convert from JSON to a TC Endpoint
     * @param endpointBytes     The bytes holding the JSON TC Endpoint
     * @return The converted    TC Endpoint
     * @throws InvalidProtocolBufferException If the input is not valid a JSON representation of a TC Endpoint
     */
    public static TcServiceEndpoint tcEndpointFromJson(byte[] endpointBytes) throws InvalidProtocolBufferException {
        return tcEndpointFromJson(new String(endpointBytes));
    }

    /**
     * Convert from JSON to a TC Endpoint
     * @param jsonString    The JSON representation of the TC Endpoint 
     * @return The converted TC Endpoint
     * @throws InvalidProtocolBufferException If the input is not valid a JSON representation of a TC Endpoint
     */
    public static TcServiceEndpoint tcEndpointFromJson(String jsonString) throws InvalidProtocolBufferException {
        TcServiceEndpoint.Builder tcEndpointBuilder = TcServiceEndpoint.newBuilder();
        JsonFormat.parser()
                .ignoringUnknownFields() // optional, ignore unknown JSON fields to be robust against changes
                .merge(jsonString, tcEndpointBuilder);

        return tcEndpointBuilder.build();
    }

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
