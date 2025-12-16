package cdds.service.tc;

import java.nio.charset.StandardCharsets;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import ccsds.cdds.tc.CddsTcService.TcServiceEndpoint;

/**
 * Utility to convert TC endpoints to / from JSON
 */
public class TcEndpointJson {

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

}
