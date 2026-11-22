package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.capstone.todo.domain.TaskRepository;
import com.capstone.todo.support.LogCapture;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.ScriptedIds;
import com.capstone.todo.support.TestDataDir;
import com.capstone.todo.web.ApiExceptionHandler;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * AC-36 / NFR-7 with a failure that comes from the real SQLite engine while request data is in scope (CR-7): the id
 * generator hands out the same id twice, so the second create fails on the UNIQUE constraint of {@code tasks.id} inside
 * the real repository, with the title, description and query string of that request bound or in the URL. The one ERROR
 * entry is then checked as the log would print it, including the throwable's message, causes and stack, for any of the
 * request's values. {@code ErrorMappingContractTest} covers the forced failures of every operation; the control that
 * proves the check can fail is {@code LogCaptureTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ErrorLogContentContractTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(ErrorLogContentContractTest.class);
    private static final UUID REUSED_ID = ScriptedIds.idNumber(7);
    private static final String SECRET_TITLE = "SECRET-TITLE-5c1d";
    private static final String SECRET_DESCRIPTION = "SECRET-DESCRIPTION-8e42";
    private static final String SECRET_QUERY = "SECRETQUERY-31";
    private static final String ADVICE = ApiExceptionHandler.class.getName();

    @TestConfiguration(proxyBeanMethods = false)
    static class SameIdTwice {
        @Bean
        @Primary
        ScriptedIds sameIdTwice() {
            return new ScriptedIds(REUSED_ID, REUSED_ID);
        }
    }

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApiResponses responses;

    @Autowired
    private TaskRepository repository;

    private RawHttp.Response create() {
        return RawHttp.request("POST", "/todos?q=" + SECRET_QUERY).header("Content-Type", "application/json")
                .body("{\"title\":\"" + SECRET_TITLE + "\",\"description\":\"" + SECRET_DESCRIPTION + "\"}").send(port);
    }

    @Test
    void aRealSqliteFailureWithRequestDataInScopeLogsNoRequestDataNotEvenInTheThrowable_ac36() {
        RawHttp.Response first = create();
        assertThat(first.status()).isEqualTo(201);

        try (LogCapture log = new LogCapture(ADVICE)) {
            RawHttp.Response second = create();

            assertThat(second.status()).isEqualTo(500);
            assertThat(second.body()).isEqualTo(responses.fixedError(ErrorCode.INTERNAL_ERROR));
            assertThat(second.bodyText()).doesNotContain(SECRET_TITLE).doesNotContain(SECRET_DESCRIPTION)
                    .doesNotContain(SECRET_QUERY).doesNotContain("UNIQUE").doesNotContain("sqlite");

            assertThat(log.errors()).as("exactly one ERROR entry in the whole log").hasSize(1);
            ILoggingEvent entry = log.errors().get(0);
            assertThat(entry.getLoggerName()).isEqualTo(ADVICE);

            // The engine's failure really is what was logged (otherwise the checks below prove nothing)...
            String throwableText = throwableMessages(entry.getThrowableProxy());
            assertThat(throwableText.toUpperCase(Locale.ROOT)).contains("UNIQUE");

            // ...and nothing of the request is in the event, whatever part of it prints the text.
            assertThat(LogCapture.render(entry)).doesNotContain(SECRET_TITLE).doesNotContain(SECRET_DESCRIPTION)
                    .doesNotContain(SECRET_QUERY);
            assertThat(throwableText).doesNotContain(SECRET_TITLE).doesNotContain(SECRET_DESCRIPTION)
                    .doesNotContain(SECRET_QUERY);
        }

        assertThat(repository.findAllOrdered()).as("the failed create changed nothing").hasSize(1);
    }

    private static String throwableMessages(IThrowableProxy proxy) {
        StringBuilder text = new StringBuilder();
        for (IThrowableProxy current = proxy; current != null; current = current.getCause()) {
            text.append(current.getClassName()).append(": ").append(current.getMessage()).append('\n');
        }
        return text.toString();
    }
}
