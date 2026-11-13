package pro.fessional.meepo.eval.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author trydofor
 * @since 2021-01-07
 */
class NowTest {

    @Test
    void legacyAndModernDatesUseTheRequestedPattern() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);
            LocalDateTime fixed = LocalDateTime.of(2000, 2, 29, 23, 59, 58);
            Date legacy = Date.from(fixed.atZone(ZoneId.systemDefault()).toInstant());
            assertEquals("2000-02-29 23:59:58", Now.now(legacy, null));
            assertEquals("2000-02-29 23:59:58", Now.now(fixed, null));
            assertEquals("2000.02.29", Now.now(legacy, "yyyy.MM.dd"));
            assertEquals("2000.02.29", Now.now(fixed.toLocalDate(), "yyyy.MM.dd"));
            assertEquals("", Now.now(fixed, ""));
            assertEquals("2000.02.29", Now.funNow.eval(Collections.emptyMap(), fixed,
                new StringBuilder("yyyy.MM.dd"), "ignored"));
            assertThrows(IllegalArgumentException.class, () -> Now.now(fixed, "invalid-pattern"));
            assertThrows(UnsupportedTemporalTypeException.class, () -> Now.now(LocalDate.of(2000, 2, 29), null));
        }
        finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void missingDateValuesUseTheCurrentLocalTime() {
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String[] values = { Now.now(null, null), Now.now(new Object(), null),
                            (String) Now.funNow.eval(Collections.emptyMap(), null, (Object[]) null) };
        LocalDateTime after = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (String value : values) {
            LocalDateTime actual = LocalDateTime.parse(value, format);
            assertFalse(actual.isBefore(before), value);
            assertFalse(actual.isAfter(after), value);
        }
    }
}
