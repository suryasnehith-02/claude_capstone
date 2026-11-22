package com.capstone.todo.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskStatus;

class TaskJsonTest {

    private static final UUID ID = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    private final ApiResponses responses = new ApiResponses(JsonConfig.newApiJsonMapper());

    private static Task task(String title, String description, TaskStatus status, Instant createdAt, Instant updatedAt) {
        return new Task(42, ID, title, description, status, createdAt, updatedAt);
    }

    @Test
    void formatsMillisecondsWithAZAndPadsAZeroFraction() {
        assertThat(TaskJson.format(Instant.parse("2026-01-02T03:04:05.006Z"))).isEqualTo("2026-01-02T03:04:05.006Z");
        assertThat(TaskJson.format(Instant.parse("2026-01-02T03:04:05Z"))).isEqualTo("2026-01-02T03:04:05.000Z");
        assertThat(TaskJson.format(Instant.parse("2026-01-02T03:04:05.100Z"))).isEqualTo("2026-01-02T03:04:05.100Z");
        assertThat(TaskJson.format(Instant.parse("1999-12-31T23:59:59.999Z"))).isEqualTo("1999-12-31T23:59:59.999Z");
        assertThat(TaskJson.format(Instant.EPOCH)).isEqualTo("1970-01-01T00:00:00.000Z");
    }

    @Test
    void membersAreInTheContractOrderAndSeqIsNeverWritten() {
        byte[] json = responses.task(task("Buy milk", "2 litres", TaskStatus.PENDING,
                Instant.parse("2026-01-02T03:04:05.000Z"), Instant.parse("2026-01-02T03:04:06.007Z")));

        assertThat(new String(json, StandardCharsets.UTF_8)).isEqualTo(
                "{\"data\":{\"id\":\"0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d\",\"title\":\"Buy milk\","
                        + "\"description\":\"2 litres\",\"status\":\"Pending\","
                        + "\"createdAt\":\"2026-01-02T03:04:05.000Z\",\"updatedAt\":\"2026-01-02T03:04:06.007Z\"}}");
        assertThat(new String(json, StandardCharsets.UTF_8)).doesNotContain("seq");
    }

    @Test
    void descriptionIsAlwaysPresentEvenWhenEmpty() {
        byte[] json = responses.task(task("t", "", TaskStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH));

        assertThat(new String(json, StandardCharsets.UTF_8))
                .contains("\"description\":\"\"").contains("\"status\":\"Completed\"");
    }

    @Test
    void hostLocaleAndTimeZoneDoNotChangeTheOutput() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Instant instant = Instant.parse("2026-03-04T05:06:07.008Z");
            String expected = "2026-03-04T05:06:07.008Z";
            for (Locale locale : new Locale[] {Locale.forLanguageTag("en-IN"), Locale.forLanguageTag("ar-SA-u-nu-arab"),
                    Locale.forLanguageTag("th-TH-u-ca-buddhist"), Locale.forLanguageTag("hi-IN-u-nu-deva")}) {
                for (String zone : new String[] {"Asia/Calcutta", "Pacific/Kiritimati", "America/Los_Angeles"}) {
                    Locale.setDefault(locale);
                    TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)));

                    assertThat(TaskJson.format(instant)).as(locale + " " + zone).isEqualTo(expected);
                }
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }
}
