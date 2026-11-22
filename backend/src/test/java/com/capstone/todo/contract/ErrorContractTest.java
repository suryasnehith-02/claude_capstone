package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.capstone.todo.support.EvidenceLog;
import com.capstone.todo.support.LogCapture;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;
import com.capstone.todo.support.hooks.TestContainerErrorConfig;
import com.capstone.todo.web.FallbackErrorController;
import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * T-ERR, the TRACE case (DR-16, G2-5 (h)): a container error for a request whose method no route accepts. A valve in
 * front of the filter chain ({@link TestContainerErrorConfig}) calls {@code sendError}, so the container's ERROR
 * dispatch runs for a TRACE request that the gateway never saw as a REQUEST. The answer must be the envelope for the
 * dispatched status (an unmapped status is 500 INTERNAL_ERROR), never an echo of the request, and an unmapped status is
 * logged exactly once. The other T-ERR and T-LOG cases (failing store on every operation, mapped and unmapped statuses
 * from a filter hook, failure during the error dispatch, no entry for 4xx) are in {@code ErrorMappingContractTest} and
 * {@code GatewayFailureContractTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(TestContainerErrorConfig.class)
class ErrorContractTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(ErrorContractTest.class);
    private static final EvidenceLog EVIDENCE = new EvidenceLog();
    private static final String PROBE_NAME = "X-Distinctive";
    private static final String PROBE_VALUE = "ECHO-ME-NOT-9f3a";
    private static final String GATEWAY = ApiGatewayFilter.class.getName();
    private static final String FALLBACK = FallbackErrorController.class.getName();

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApiResponses responses;

    @AfterAll
    static void writeEvidence() {
        EVIDENCE.writeTo("t15-error-evidence.txt");
    }

    @Test
    void aContainerErrorForATraceRequestIsTheEnvelopeNeverAnEchoAndAnUnmappedStatusIsLoggedOnce_dr16() {
        for (String method : new String[] {"TRACE", "GET"}) {
            for (String target : new String[] {"/todos?q=SECRETQUERY", "/nope"}) {
                for (int status : new int[] {404, 405, 500, 418}) {
                    ErrorCode expected = ErrorCode.forContainerStatus(status);
                    String where = method + " " + target + " with a container sendError(" + status + ")";
                    try (LogCapture log = new LogCapture(GATEWAY, FALLBACK)) {
                        RawHttp.Response response = RawHttp.request(method, target)
                                .header(PROBE_NAME, PROBE_VALUE)
                                .header(TestContainerErrorConfig.HEADER, String.valueOf(status)).send(port);

                        assertThat(response.status()).as(where).isEqualTo(expected.status());
                        assertThat(response.body()).as(where).isEqualTo(responses.fixedError(expected));
                        assertThat(response.headers("Content-Type")).as(where).containsExactly("application/json");
                        assertThat(response.headers("X-Content-Type-Options")).as(where).containsExactly("nosniff");
                        assertThat(response.headers()).as(where + ": no echoed header and no CORS header").noneMatch(
                                h -> h[0].equalsIgnoreCase(PROBE_NAME) || h[1].contains(PROBE_VALUE)
                                        || h[0].toLowerCase(Locale.ROOT).startsWith("access-control-"));
                        String text = new String(response.body(), StandardCharsets.UTF_8);
                        assertThat(text).as(where).doesNotContain(PROBE_VALUE).doesNotContain(PROBE_NAME)
                                .doesNotContain("\"trace\"").doesNotContain("\"exception\"").doesNotContain("\"path\"")
                                .doesNotContain("\"timestamp\"").doesNotContain("SECRETQUERY");

                        boolean logged = expected == ErrorCode.INTERNAL_ERROR;
                        assertThat(log.errors()).as(where + ": ERROR entries in the whole JVM log").hasSize(logged ? 1 : 0);
                        String entryText = "no entry";
                        if (logged) {
                            ILoggingEvent entry = log.errors().get(0);
                            entryText = entry.getLoggerName() + ": " + entry.getFormattedMessage();
                            assertThat(entry.getFormattedMessage()).as(where).contains("requestId=", "method=", "route=")
                                    .doesNotContain(PROBE_VALUE).doesNotContain("SECRETQUERY").doesNotContain("?");
                            assertThat(entry.getLoggerName()).isIn(GATEWAY, FALLBACK);
                        }
                        EVIDENCE.row("T-ERR/DR-16", where, expected.status() + " " + expected.code() + " envelope, no echo, "
                                + (logged ? "one ERROR entry" : "no ERROR entry"),
                                response.status() + " " + response.bodyText() + " | " + entryText);
                    }
                }
            }
        }
    }
}
