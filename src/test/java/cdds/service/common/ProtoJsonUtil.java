package cdds.service.common;

import java.nio.charset.StandardCharsets;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

/**
 * Utility to convert Protobuf messages to / from JSON
 */
public class ProtoJsonUtil {

    /**
     * Convert a Protobuf message to JSON UTF-8 bytes
     */
    public static <T extends Message> byte[] toJsonUtf8(T message)
            throws InvalidProtocolBufferException {
        return toJson(message).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Convert a Protobuf message to JSON string
     */
    public static <T extends Message> String toJson(T message)
            throws InvalidProtocolBufferException {

        return JsonFormat.printer()
                .includingDefaultValueFields()
                .preservingProtoFieldNames()
                .print(message);
    }

    /**
     * Convert JSON UTF-8 bytes to a Protobuf message
     */
    public static <T extends Message> T fromJson(byte[] jsonBytes, Message.Builder builder)
            throws InvalidProtocolBufferException {
        
        if(jsonBytes == null) {
            return null;
        }

        return fromJson(new String(jsonBytes, StandardCharsets.UTF_8), builder);
    }

    /**
     * Convert JSON string to a Protobuf message
     */
    @SuppressWarnings("unchecked")
    public static <T extends Message> T fromJson(String jsonString, Message.Builder builder)
            throws InvalidProtocolBufferException {

        JsonFormat.parser()
                .ignoringUnknownFields()
                .merge(jsonString, builder);

        return (T) builder.build();
    }

}