package cdds.tm;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;

import ccsds.cdds.Telemetry.TelemetryData;
import ccsds.cdds.Telemetry.TelemetryMessage;
import ccsds.cdds.Types.Annotation;
import ccsds.cdds.Types.ApertureId;
import ccsds.cdds.Types.FrameVersion;
import ccsds.cdds.Types.GvcId;
import ccsds.cdds.Types.ReceptionMetaData;
import ccsds.cdds.Types.Value;
import cdds.util.TimeUtil;

/**
 * Test for CDDS TelemetryMessage
 * 
 * 1) testTmFileWriteRead - Writes TelemetryMessage object to a file, reads them out and compares for equality.
 * 
 */
public class TestTelemetryFile {

    private static final int TM_FRAME_LENGTH = 1115;
    private static final int NUM_TM_MESSAGES = 1000;
    private static String TM_OUT_DIR = "target" + File.separatorChar + "tm-files" + File.separatorChar ;
    private static final String TELEMETRY_TLM = TM_OUT_DIR + "telemetry.tlm";

    @BeforeAll
    public static void createOutputDir() throws IOException {
        Files.createDirectories(Paths.get(TM_OUT_DIR));
    }
 
    /**
     * Creates a TelemetryMessage
     * @param data The data element passed to TelemetryMessage TelemetryData
     * @return the created TelemetryMessage
     */
    private TelemetryMessage createTelemetryMessage(byte[] data) {

        TelemetryMessage tmMessage = TelemetryMessage.newBuilder()
            .setTelemetry(
                TelemetryData.newBuilder()
                    .addMetaData(0, ReceptionMetaData.newBuilder()
                        .setApertureId(ApertureId.newBuilder()
                            .setLocalForm("NNO1")
                            .build())
                        .setReceiveTime(TimeUtil.now())
                        .setDataLinkContinuity(-1)
                        .build())
                    .addPrivateAnnotation(Annotation.newBuilder()
                        .setName("test-anno")
                        .setValue(Value.newBuilder()
                            .setStringValue("test-anno-val")
                            .build()
                        )
                        .build()
                    )
                    .setData(ByteString.copyFrom(data, 0, data.length))
                    .build()
                ).build();

        return tmMessage;
    }

    /**
     * Writes TelemetryMessage object to a file, reads them out and compares for equality.
     */
    @Test
    public void testTmFileWriteRead() {
        System.out.println("Test TM file creation");

        final byte[] data = getFrameData(TM_FRAME_LENGTH);

        final List<TelemetryMessage> writtenTmMessages = new LinkedList<>();
        try (FileOutputStream output = new FileOutputStream(TELEMETRY_TLM)) {
            for (int idx = 0; idx < NUM_TM_MESSAGES; idx++) {
                TelemetryMessage tmMessage = createTelemetryMessage(data);
                writtenTmMessages.add(tmMessage);

                if(idx == 0) {
                    System.out.println("Earth receive time: " + 
                        TimeUtil.dateTime(tmMessage.getTelemetry().getMetaData(0).getReceiveTime()));
                    System.out.println("First written TM frame: " + tmMessage.toString());
                }

                tmMessage.writeDelimitedTo(output);
            }
            System.out.println("Wrote " + NUM_TM_MESSAGES + " TelemetryMessage objects to " + TELEMETRY_TLM);
        } catch (IOException e) {
            e.printStackTrace();
        }

        final List<TelemetryMessage> readTmMessages = new LinkedList<>();
        try (FileInputStream input = new FileInputStream(TELEMETRY_TLM)) { 
            TelemetryMessage tmMessage;
            while((tmMessage = TelemetryMessage.parseDelimitedFrom(input)) != null) {
                readTmMessages.add(tmMessage);

                if(readTmMessages.size() == 1) {
                    System.out.println("Earth receive time: "
                        + TimeUtil.dateTime(tmMessage.getTelemetry().getMetaData(0).getReceiveTime()));
                    System.out.println("First read TM frame: " + tmMessage.toString());
                } else if(readTmMessages.size() == NUM_TM_MESSAGES) {
                    System.out.println("Earth receive time: "
                         + TimeUtil.dateTime(tmMessage.getTelemetry().getMetaData(0).getReceiveTime()));
                    System.out.println("Last read TM frame: " + tmMessage.toString());
                }

            } 
            System.out.println("Read " + readTmMessages.size() + " TelemetryMessage objects from " + TELEMETRY_TLM);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // compare the two TM lists
        assertEquals(readTmMessages, writtenTmMessages);
    }

    /**
     * Returns test frame data 
     * @return  test data of length 
     */
    private byte[] getFrameData(int length) {
       final byte[] data = new byte[length];

        String phrase = "the quick brown fox jumps over the lazy dog ";
        byte[] phraseBytes = phrase.getBytes(StandardCharsets.UTF_8);

        int phraseLen = phraseBytes.length;

        // Fill the buffer by repeating the phrase
        for (int i = 0; i < data.length; i++) {
            data[i] = phraseBytes[i % phraseLen];
        }

        return data;
    }
}
