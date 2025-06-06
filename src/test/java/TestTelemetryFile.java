import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;

import ccsds.cdds.Telemetry.TelemetryData;
import ccsds.cdds.Telemetry.TelemetryMessage;
import ccsds.cdds.Types.Annotation;
import ccsds.cdds.Types.AntennaId;
import ccsds.cdds.Types.DateTime;
import ccsds.cdds.Types.FrameVersion;
import ccsds.cdds.Types.GvcId;
import ccsds.cdds.Types.Value;

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
     * Get the current time
     * @return Current time as CDDS DateTime
     */
    private DateTime now() {
        
          // 1. Define the CCSDS epoch: Jan 1, 1958 at UTC
        LocalDate epochDate = LocalDate.of(1958, 1, 1);
        ZoneId zone = ZoneOffset.UTC;
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate currentDate = now.toLocalDate();

        // 2. Days since epoch
        int daysSinceEpoch = (int) ChronoUnit.DAYS.between(epochDate, currentDate);

        // 3. Nanoseconds since midnight
        long nanoOfDay = now.toLocalTime().toNanoOfDay();

        // 4. Milliseconds of the day (integer)
        int millisOfDay = (int) (nanoOfDay / 1_000_000);

        // 5. Picoseconds of the millisecond
        long nanosWithinMillisecond = nanoOfDay % 1_000_000;
        int picoOfMillisecond = (int) nanosWithinMillisecond * 1_000;

        return DateTime.newBuilder()
            .setDays(daysSinceEpoch)
            .setMsOfDay(millisOfDay)
            .setPicoSecsOfMs(picoOfMillisecond).build();
    }

    /**
     * Convert CDDS DateTime to string
     * @param dt    The DateTime to convert
     * @return      A string representation of the given DateTime
     */
    private String dateTime(DateTime dt) {
        
        // 1. Define the custom epoch: January 1, 1958 UTC
        LocalDate epoch = LocalDate.of(1958, 1, 1);
        LocalDate targetDate = epoch.plusDays(dt.getDays());

        // 2. Break millisOfDay into hours, minutes, seconds, and milliseconds
        int totalSeconds = dt.getMsOfDay() / 1000;
        int millis = dt.getMsOfDay() % 1000;

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        // 3. Convert picoseconds to nanoseconds (truncate)
        int nanos = millis * 1_000_000 + dt.getPicoSecsOfMs() / 1_000;

        // 4. Create LocalDateTime and convert to UTC ZonedDateTime
        LocalTime time = LocalTime.of(hours, minutes, seconds, nanos);
        ZonedDateTime result = ZonedDateTime.of(targetDate, time, ZoneOffset.UTC);

        return result.toString();        
    }

    /**
     * Creates a TelemetryMessage
     * @param data The data element passed to TelemetryMessage TelemetryData
     * @return the created TelemetryMessage
     */
    private TelemetryMessage createTelemtryMessage(byte[] data) {

        TelemetryMessage tmMessage = TelemetryMessage.newBuilder()
            .setTelemetry(
                TelemetryData.newBuilder()
                    .setEarthReceiveTime(now())
                    .setAntennaId(AntennaId.newBuilder()
                        .setGlobalForm("NNO1")
                        .build()
                    )
                    .setDataLinkContinuity(0)
                    .setGvcId(GvcId.newBuilder()
                        .setSpacecraftId(4711)
                        .setVersion(FrameVersion.AOS)
                        .setVirtualChannelId(8)
                        .build()
                    )
                    .addPrivateAnnotation(Annotation.newBuilder()
                        .setName("test-anno")
                        .setValue(Value.newBuilder()
                            .setStringValue("test-anno-val")
                            .build()
                        )
                        .build()
                    )
                    .setData(ByteString.copyFrom(data, 0, TM_FRAME_LENGTH))
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

        final byte[] data = new byte[TM_FRAME_LENGTH];

        String phrase = "the quick brown fox jumps over the lazy dog ";
        byte[] phraseBytes = phrase.getBytes(StandardCharsets.UTF_8);

        int phraseLen = phraseBytes.length;

        // Fill the buffer by repeating the phrase
        for (int i = 0; i < TM_FRAME_LENGTH; i++) {
            data[i] = phraseBytes[i % phraseLen];
        }

        final List<TelemetryMessage> writtenTmMessages = new LinkedList<>();
        try (FileOutputStream output = new FileOutputStream(TELEMETRY_TLM)) {
            for (int idx = 0; idx < NUM_TM_MESSAGES; idx++) {
                TelemetryMessage tmMessage = createTelemtryMessage(data);
                writtenTmMessages.add(tmMessage);

                if(idx == 0) {
                    System.out.println("First written TM frame: " + tmMessage.toString());
                    System.out.println("Earth receive time: " + dateTime(tmMessage.getTelemetry().getEarthReceiveTime()));
                }

                tmMessage.writeDelimitedTo(output);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Wrote " + NUM_TM_MESSAGES + " TelemetryMessage objects to " + TELEMETRY_TLM);

        final List<TelemetryMessage> readTmMessages = new LinkedList<>();
        try (FileInputStream input = new FileInputStream(TELEMETRY_TLM)) { 
            TelemetryMessage tmMessage;
            while((tmMessage = TelemetryMessage.parseDelimitedFrom(input)) != null) {
                readTmMessages.add(tmMessage);

                if(readTmMessages.size() == 1 || readTmMessages.size() == NUM_TM_MESSAGES) {
                    System.out.println("First / last read TM frame: " + tmMessage.toString());
                    System.out.println("Earth receive time: " + dateTime(tmMessage.getTelemetry().getEarthReceiveTime()));
                }

            } 
        } catch (IOException e) {
            e.printStackTrace();
        }

        // compare the two TM lists
        assertEquals(readTmMessages, writtenTmMessages);

        System.out.println("Read " + readTmMessages.size() + " TelemetryMessage objects from " + TELEMETRY_TLM);
    }
}
