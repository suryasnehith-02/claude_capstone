package com.capstone.todo.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * The control for the leak assertions of the contract tests: {@link LogCapture#render} must show request data that sits
 * only in a throwable message, in a cause or in the MDC, which {@code getFormattedMessage()} never does. If it did not,
 * a "no request data in the log" assertion could not fail.
 */
class LogCaptureTest {

    private static final String NAME = LogCaptureTest.class.getName();
    private static final Logger LOG = LoggerFactory.getLogger(NAME);

    @Test
    void renderShowsRequestDataThatIsOnlyInAThrowableMessageOrACauseOrTheMdc() {
        try (LogCapture log = new LogCapture(NAME)) {
            MDC.put("probe", "MDC-SECRET");
            try {
                LOG.error("Request failed: requestId={}", "r-1",
                        new IllegalStateException("outer", new IllegalArgumentException("title=THROWABLE-SECRET")));

                // Read while the MDC is still set: Logback reads it from the thread when asked.
                ILoggingEvent event = log.errors().get(0);
                assertThat(event.getFormattedMessage()).doesNotContain("THROWABLE-SECRET").doesNotContain("MDC-SECRET");
                assertThat(LogCapture.render(event)).contains("Request failed: requestId=r-1", "THROWABLE-SECRET",
                        "MDC-SECRET", "IllegalStateException", "IllegalArgumentException");
            } finally {
                MDC.remove("probe");
            }
        }
    }

    @Test
    void renderOfAnEventWithoutAThrowableIsTheFormattedMessage() {
        try (LogCapture log = new LogCapture(NAME)) {
            LOG.error("plain {}", "entry");

            assertThat(LogCapture.render(log.errors().get(0))).isEqualTo("plain entry");
        }
    }
}
