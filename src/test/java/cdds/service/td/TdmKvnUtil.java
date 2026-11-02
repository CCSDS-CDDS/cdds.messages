package cdds.service.td;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ccsds.cdds.v1.Tracking.AtomicSegment;
import ccsds.cdds.v1.Tracking.Format;
import ccsds.cdds.v1.Tracking.TdmTypeText;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.AngleType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.AngleTypeType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.AngleUnits;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.DataQualityType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.IntegrationRefType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.ModeType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.RangeUnitsType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.RefFrameType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TdmBody;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TdmData;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TdmHeader;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TdmMetadata;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TdmSegment;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TdmType;
import ccsds.schema.ndmxml.CcsdsSchemaNdmxml.TrackingDataObservationType;

/**
 * Utility to read a TDM in KVN format (CCSDS 503.0 Blue Book) from the test
 * resources and to convert it into the two protobuf representations carried by
 * a TrackingDataMessage:
 * <ul>
 *   <li>{@link TdmTypeText} for the tdmText (KVN text) of a tracking data message</li>
 *   <li>{@link TdmType} for the tdmProto (protobuf encoded TDM) of a tracking data message</li>
 * </ul>
 * The parser supports COMMENT lines with and without '=', an optional
 * DATA_START/DATA_STOP block around the data, and both data line formats
 * 'KEY = EPOCH VALUE' and 'EPOCH KEY = VALUE'.
 */
public class TdmKvnUtil {

    /**
     * The test resource containing an example TDM in KVN format
     */
    public static final String TDM_KVN_RESOURCE = "TDM/TDM-ONE-WAY-DATA-EXAMPLE.txt";

    private static final String META_START = "META_START";
    private static final String META_STOP = "META_STOP";
    private static final String DATA_START = "DATA_START";
    private static final String DATA_STOP = "DATA_STOP";
    private static final String COMMENT = "COMMENT";
    private static final String EQ = "=";
    private static final String TDM_ID = "TDM";

    /**
     * Reads a classpath resource (for example the TDM KVN example) into a String
     * @param resourcePath  The path of the classpath resource
     * @return              The content of the resource as UTF-8 String
     * @throws IOException  Thrown if the resource cannot be read
     */
    public static String readKvnResource(String resourcePath) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if(in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The non-empty lines of a KVN text, trimmed, in order of appearance
     */
    private static List<String> kvnLines(String kvnText) {
        final List<String> lines = new ArrayList<>();
        for(final String line : kvnText.split("\\R")) {
            if(line.trim().isEmpty() == false) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    /**
     * The normalized KVN text: the non-empty lines joined with '\n'
     * @param kvnText  The KVN text to normalize
     * @return         The normalized KVN text
     */
    public static String normalizeKvn(String kvnText) {
        return String.join("\n", kvnLines(kvnText));
    }

    /**
     * Converts a KVN text TDM into a TdmTypeText: the KVN header lines become the
     * header, the META_START..META_STOP block and the remaining lines (data
     * section, including optional DATA_START/DATA_STOP markers) become one
     * atomic segment (metaData and data), the format is KVN.
     * @param kvnText  The TDM in KVN format
     * @return         The TdmTypeText carrying the KVN text TDM
     */
    public static TdmTypeText toTdmTypeText(String kvnText) {
        final List<String> lines = kvnLines(kvnText);
        final int metaStart = lines.indexOf(META_START);
        final int metaStop = lines.indexOf(META_STOP);
        if(metaStart < 0 || metaStop < metaStart) {
            throw new IllegalArgumentException("No META_START/META_STOP block in KVN text");
        }

        final String header = String.join("\n", lines.subList(0, metaStart));
        final String metaData = String.join("\n", lines.subList(metaStart, metaStop + 1));
        final String data = String.join("\n", lines.subList(metaStop + 1, lines.size()));

        return TdmTypeText.newBuilder()
                .setHeader(header)
                .addAtomicSegments(AtomicSegment.newBuilder()
                        .setMetaData(metaData)
                        .setData(data)
                        .build())
                .setFormat(Format.KVN)
                .build();
    }

    /**
     * Reassembles the KVN text from a TdmTypeText: header, then for each atomic
     * segment the metaData block and the data lines, all joined with '\n'.
     * @param tdmText  The TdmTypeText to convert back to KVN text
     * @return         The KVN text
     */
    public static String toKvnText(TdmTypeText tdmText) {
        final StringBuilder kvn = new StringBuilder(tdmText.getHeader());
        for(final AtomicSegment segment : tdmText.getAtomicSegmentsList()) {
            kvn.append("\n").append(segment.getMetaData());
            kvn.append("\n").append(segment.getData());
        }
        return kvn.toString();
    }

    /**
     * Converts a KVN text TDM into a protobuf encoded TDM (TdmType), equivalent
     * to the KVN text: the KVN header keywords become the TdmHeader, the metadata
     * block becomes the TdmMetadata and the data lines become
     * TrackingDataObservationTypes in the TdmData of the (single) TdmSegment.
     * All data values sharing the same epoch form one observation.
     * @param kvnText  The TDM in KVN format
     * @return         The TdmType equivalent to the KVN text
     */
    public static TdmType toTdmType(String kvnText) {
        final List<String> lines = kvnLines(kvnText);
        final int metaStart = lines.indexOf(META_START);
        final int metaStop = lines.indexOf(META_STOP);
        if(metaStart < 0 || metaStop < metaStart) {
            throw new IllegalArgumentException("No META_START/META_STOP block in KVN text");
        }

        // the KVN header
        final TdmHeader.Builder header = TdmHeader.newBuilder();
        String version = "";
        for(final String line : lines.subList(0, metaStart)) {
            if(isComment(line)) {
                header.addComment(commentText(line));
                continue;
            }
            final String[] kv = splitKeyValue(line);
            switch(kv[0]) {
                case "CCSDS_TDM_VERS": version = kv[1]; break;
                case "COMMENT":        header.addComment(kv[1]); break;
                case "CREATION_DATE":  header.setCreationDate(kv[1]); break;
                case "ORIGINATOR":     header.setOriginator(kv[1]); break;
                case "MESSAGE_ID":     header.setMessageId(kv[1]); break;
                default: throw new IllegalArgumentException("Unsupported TDM header key: " + kv[0]);
            }
        }

        // the metadata block
        final TdmMetadata.Builder metadata = TdmMetadata.newBuilder();
        AngleUnits angleUnits = AngleUnits.ANGLE_UNITS_UNSPECIFIED;
        for(final String line : lines.subList(metaStart + 1, metaStop)) {
            if(isComment(line)) {
                metadata.addComment(commentText(line));
                continue;
            }
            final String[] kv = splitKeyValue(line);
            switch(kv[0]) {
                case "TIME_SYSTEM":       metadata.setTimeSystem(kv[1]); break;
                case "START_TIME":        metadata.setStartTime(kv[1]); break;
                case "STOP_TIME":         metadata.setStopTime(kv[1]); break;
                case "TRACK_ID":          metadata.setTrackId(kv[1]); break;
                case "DATA_TYPES":        metadata.setDataTypes(kv[1]); break;
                case "PARTICIPANT_1":     metadata.setParticipant1(kv[1]); break;
                case "PARTICIPANT_2":     metadata.setParticipant2(kv[1]); break;
                case "PARTICIPANT_3":     metadata.setParticipant3(kv[1]); break;
                case "PARTICIPANT_4":     metadata.setParticipant4(kv[1]); break;
                case "PARTICIPANT_5":     metadata.setParticipant5(kv[1]); break;
                case "MODE":              metadata.setMode(modeType(kv[1])); break;
                case "PATH":              metadata.setPath(kv[1]); break;
                case "ANGLE_TYPE":        metadata.setAngleType(angleTypeType(kv[1])); break;
                case "REFERENCE_FRAME":   metadata.setReferenceFrame(refFrameType(kv[1])); break;
                case "ANGLE_UNITS":       angleUnits = angleUnits(kv[1]); break;
                case "RANGE_UNITS":       metadata.setRangeUnits(rangeUnitsType(kv[1])); break;
                case "TRANSMIT_BAND":     metadata.setTransmitBand(kv[1]); break;
                case "RECEIVE_BAND":      metadata.setReceiveBand(kv[1]); break;
                case "INTEGRATION_INTERVAL": metadata.setIntegrationInterval(Double.parseDouble(kv[1])); break;
                case "INTEGRATION_REF":   metadata.setIntegrationRef(integrationRefType(kv[1])); break;
                case "FREQ_OFFSET":       metadata.setFreqOffset(Double.parseDouble(kv[1])); break;
                case "TRANSMIT_DELAY_1":  metadata.setTransmitDelay1(Double.parseDouble(kv[1])); break;
                case "TRANSMIT_DELAY_2":  metadata.setTransmitDelay2(Double.parseDouble(kv[1])); break;
                case "TRANSMIT_DELAY_3":  metadata.setTransmitDelay3(Double.parseDouble(kv[1])); break;
                case "TRANSMIT_DELAY_4":  metadata.setTransmitDelay4(Double.parseDouble(kv[1])); break;
                case "TRANSMIT_DELAY_5":  metadata.setTransmitDelay5(Double.parseDouble(kv[1])); break;
                case "RECEIVE_DELAY_1":   metadata.setReceiveDelay1(Double.parseDouble(kv[1])); break;
                case "RECEIVE_DELAY_2":   metadata.setReceiveDelay2(Double.parseDouble(kv[1])); break;
                case "RECEIVE_DELAY_3":   metadata.setReceiveDelay3(Double.parseDouble(kv[1])); break;
                case "RECEIVE_DELAY_4":   metadata.setReceiveDelay4(Double.parseDouble(kv[1])); break;
                case "RECEIVE_DELAY_5":   metadata.setReceiveDelay5(Double.parseDouble(kv[1])); break;
                case "DATA_QUALITY":      metadata.setDataQuality(dataQualityType(kv[1])); break;
                default: throw new IllegalArgumentException("Unsupported TDM metadata key: " + kv[0]);
            }
        }

        // the data lines (after META_STOP, without the optional DATA_START/DATA_STOP markers):
        // all values sharing the same epoch form one observation
        final TdmData.Builder data = TdmData.newBuilder();
        final Map<String, TrackingDataObservationType.Builder> observationsByEpoch = new LinkedHashMap<>();
        for(final String line : lines.subList(metaStop + 1, lines.size())) {
            if(line.equals(DATA_START) || line.equals(DATA_STOP)) {
                continue;
            }
            if(isComment(line)) {
                data.addComment(commentText(line));
                continue;
            }
            parseDataLine(line, angleUnits, observationsByEpoch);
        }
        for(final TrackingDataObservationType.Builder observation : observationsByEpoch.values()) {
            data.addObservation(observation.build());
        }

        final TdmSegment segment = TdmSegment.newBuilder()
                .setMetadata(metadata)
                .setData(data)
                .build();

        return TdmType.newBuilder()
                .setId(TDM_ID)
                .setVersion(version)
                .setHeader(header)
                .setBody(TdmBody.newBuilder().addSegment(segment))
                .build();
    }

    /**
     * Returns true if the KVN line is a COMMENT line (with or without '=')
     */
    private static boolean isComment(String line) {
        return line.equals(COMMENT)
                || (line.startsWith(COMMENT) && Character.isWhitespace(line.charAt(COMMENT.length())));
    }

    /**
     * The comment text of a COMMENT line, without the COMMENT keyword and an
     * optional '=' following it
     */
    private static String commentText(String line) {
        String text = line.substring(COMMENT.length()).trim();
        if(text.startsWith(EQ)) {
            text = text.substring(1).trim();
        }
        return text;
    }

    /**
     * Splits a KVN 'KEY = VALUE' line into key and value
     */
    private static String[] splitKeyValue(String line) {
        final int eq = line.indexOf(EQ);
        if(eq < 0) {
            throw new IllegalArgumentException("Not a KVN key=value line: " + line);
        }
        return new String[]{ line.substring(0, eq).trim(), line.substring(eq + 1).trim() };
    }

    /**
     * Parses one KVN data line into the observation of its epoch. Supported
     * formats are 'KEY = EPOCH VALUE' and 'EPOCH KEY = VALUE'.
     */
    private static void parseDataLine(String line, AngleUnits angleUnits,
            Map<String, TrackingDataObservationType.Builder> observationsByEpoch) {
        final int eqIdx = line.indexOf(EQ);
        if(eqIdx < 0) {
            throw new IllegalArgumentException("Not a KVN data line: " + line);
        }
        final String left = line.substring(0, eqIdx).trim();
        final String right = line.substring(eqIdx + 1).trim();

        final String key;
        final String epoch;
        final String value;
        if(left.contains(" ")) {
            // format: EPOCH KEY = VALUE
            final String[] leftTokens = left.split("\\s+");
            epoch = leftTokens[0];
            key = leftTokens[1];
            value = right;
        } else {
            // format: KEY = EPOCH VALUE (or KEY = VALUE without epoch)
            key = left;
            final String[] rightTokens = right.split("\\s+");
            if(rightTokens.length >= 2 && isEpoch(rightTokens[0])) {
                epoch = rightTokens[0];
                value = rightTokens[1];
            } else {
                epoch = "";
                value = right;
            }
        }

        final TrackingDataObservationType.Builder observation = observationsByEpoch.computeIfAbsent(
                epoch, e -> TrackingDataObservationType.newBuilder().setEpoch(e));

        applyDataKey(observation, key, value, angleUnits);
    }

    /**
     * Returns true if the token looks like a KVN epoch (for example 2005-159T17:41:00)
     */
    private static boolean isEpoch(String token) {
        return token.contains("T") && token.contains("-");
    }

    /**
     * Applies one KVN data KEY = VALUE pair to a TrackingDataObservationType builder
     */
    private static void applyDataKey(TrackingDataObservationType.Builder observation, String key, String value,
            AngleUnits angleUnits) {
        switch(key) {
            case "ANGLE_1":               observation.setAngle1(angleType(angleUnits, value)); break;
            case "ANGLE_2":               observation.setAngle2(angleType(angleUnits, value)); break;
            case "RANGE":                 observation.setRange(Double.parseDouble(value)); break;
            case "CARRIER_POWER":         observation.setCarrierPower(Double.parseDouble(value)); break;
            case "DOPPLER_INSTANTANEOUS": observation.setDopplerInstantaneous(Double.parseDouble(value)); break;
            case "DOPPLER_INTEGRATED":    observation.setDopplerIntegrated(Double.parseDouble(value)); break;
            case "DOPPLER_COUNT":         observation.setDopplerCount(Double.parseDouble(value)); break;
            case "RECEIVE_FREQ":          observation.setReceiveFreq(Double.parseDouble(value)); break;
            case "RECEIVE_FREQ_1":        observation.setReceiveFreq1(Double.parseDouble(value)); break;
            case "RECEIVE_FREQ_2":        observation.setReceiveFreq2(Double.parseDouble(value)); break;
            case "RECEIVE_FREQ_3":        observation.setReceiveFreq3(Double.parseDouble(value)); break;
            case "RECEIVE_FREQ_4":        observation.setReceiveFreq4(Double.parseDouble(value)); break;
            case "RECEIVE_FREQ_5":        observation.setReceiveFreq5(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_1":       observation.setTransmitFreq1(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_2":       observation.setTransmitFreq2(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_3":       observation.setTransmitFreq3(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_4":       observation.setTransmitFreq4(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_5":       observation.setTransmitFreq5(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_RATE_1":  observation.setTransmitFreqRate1(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_RATE_2":  observation.setTransmitFreqRate2(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_RATE_3":  observation.setTransmitFreqRate3(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_RATE_4":  observation.setTransmitFreqRate4(Double.parseDouble(value)); break;
            case "TRANSMIT_FREQ_RATE_5":  observation.setTransmitFreqRate5(Double.parseDouble(value)); break;
            case "RCS":                   observation.setRcs(Double.parseDouble(value)); break;
            case "MAG":                   observation.setMag(Double.parseDouble(value)); break;
            case "PC_N0":                 observation.setPcN0(Double.parseDouble(value)); break;
            case "PR_N0":                 observation.setPrN0(Double.parseDouble(value)); break;
            default: throw new IllegalArgumentException("Unsupported TDM data key: " + key);
        }
    }

    private static AngleType angleType(AngleUnits units, String value) {
        return AngleType.newBuilder()
                .setUnits(units)
                .setValue(Double.parseDouble(value))
                .build();
    }

    private static ModeType modeType(String value) {
        switch(value) {
            case "SEQUENTIAL":  return ModeType.MODE_TYPE_SEQUENTIAL;
            case "SINGLE_DIFF": return ModeType.MODE_TYPE_SINGLE_DIFF;
            default: throw new IllegalArgumentException("Unsupported MODE: " + value);
        }
    }

    private static AngleTypeType angleTypeType(String value) {
        switch(value) {
            case "AZEL":  return AngleTypeType.ANGLE_TYPE_TYPE_AZEL;
            case "RADEC": return AngleTypeType.ANGLE_TYPE_TYPE_RADEC;
            case "XEYN":  return AngleTypeType.ANGLE_TYPE_TYPE_XEYN;
            case "XSYE":  return AngleTypeType.ANGLE_TYPE_TYPE_XSYE;
            default: throw new IllegalArgumentException("Unsupported ANGLE_TYPE: " + value);
        }
    }

    private static RefFrameType refFrameType(String value) {
        switch(value) {
            case "EME2000":  return RefFrameType.REF_FRAME_TYPE_EME2000;
            case "ICRF":     return RefFrameType.REF_FRAME_TYPE_ICRF;
            case "ITRF2000": return RefFrameType.REF_FRAME_TYPE_ITRF2000;
            case "ITRF93":   return RefFrameType.REF_FRAME_TYPE_ITRF93;
            case "ITRF97":   return RefFrameType.REF_FRAME_TYPE_ITRF97;
            case "TOD":      return RefFrameType.REF_FRAME_TYPE_TOD;
            default: throw new IllegalArgumentException("Unsupported REFERENCE_FRAME: " + value);
        }
    }

    private static RangeUnitsType rangeUnitsType(String value) {
        switch(value) {
            case "km": return RangeUnitsType.RANGE_UNITS_TYPE_KM;
            case "RU": return RangeUnitsType.RANGE_UNITS_TYPE_RU;
            case "s":  return RangeUnitsType.RANGE_UNITS_TYPE_S;
            default: throw new IllegalArgumentException("Unsupported RANGE_UNITS: " + value);
        }
    }

    private static AngleUnits angleUnits(String value) {
        switch(value) {
            case "deg": return AngleUnits.ANGLE_UNITS_DEG;
            default: throw new IllegalArgumentException("Unsupported ANGLE_UNITS: " + value);
        }
    }

    private static IntegrationRefType integrationRefType(String value) {
        switch(value) {
            case "START":  return IntegrationRefType.INTEGRATION_REF_TYPE_START;
            case "MIDDLE": return IntegrationRefType.INTEGRATION_REF_TYPE_MIDDLE;
            case "END":    return IntegrationRefType.INTEGRATION_REF_TYPE_END;
            default: throw new IllegalArgumentException("Unsupported INTEGRATION_REF: " + value);
        }
    }

    private static DataQualityType dataQualityType(String value) {
        switch(value) {
            case "RAW":       return DataQualityType.DATA_QUALITY_TYPE_RAW;
            case "VALIDATED": return DataQualityType.DATA_QUALITY_TYPE_VALIDATED;
            case "DEGRADED":  return DataQualityType.DATA_QUALITY_TYPE_DEGRADED;
            default: throw new IllegalArgumentException("Unsupported DATA_QUALITY: " + value);
        }
    }
}
