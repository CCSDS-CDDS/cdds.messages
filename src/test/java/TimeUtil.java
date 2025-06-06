import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import ccsds.cdds.Types.DateTime;

/**
 * Provides some DateTime related functions
 */
public class TimeUtil {
    /**
     * Get the current time
     * @return Current time as CDDS DateTime
     */
    public static DateTime now() {
        
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
    public static String dateTime(DateTime dt) {
        
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

}
