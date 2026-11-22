package com.capstone.todo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The structural rules of architecture 7.2, checked on the production source (DR-16, DR-13): no {@code sendError}, no
 * {@code @RequestBody}, {@code @PathVariable}, {@code @Valid}, {@code consumes}, {@code produces}, {@code @CrossOrigin},
 * {@code @ResponseStatus}, and no class that extends {@code ResponseEntityExceptionHandler}.
 *
 * <p>Only {@code src/main/java} is scanned, so this class and the test fixtures (which name these things on purpose) can
 * never trip it. Comments and string literals are removed first, so documentation that explains the rule does not
 * count; the checker is proven on inline sources that break each rule.
 */
class SourceRulesTest {

    private record Rule(String name, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("sendError", Pattern.compile("\\bsendError\\b")),
            new Rule("@RequestBody", Pattern.compile("@RequestBody\\b")),
            new Rule("@PathVariable", Pattern.compile("@PathVariable\\b")),
            new Rule("@Valid", Pattern.compile("@Valid\\b")),
            new Rule("consumes", Pattern.compile("\\bconsumes\\s*=")),
            new Rule("produces", Pattern.compile("\\bproduces\\s*=")),
            new Rule("@CrossOrigin", Pattern.compile("@CrossOrigin\\b")),
            new Rule("@ResponseStatus", Pattern.compile("@ResponseStatus\\b")),
            new Rule("extends ResponseEntityExceptionHandler", Pattern.compile("extends\\s+ResponseEntityExceptionHandler\\b")));

    /** Replaces comments, string literals, text blocks and char literals by spaces (newlines are kept). */
    static String code(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (source.startsWith("//", i)) {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                blank(out, source, i, stop);
                i = stop;
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                int stop = end < 0 ? n : end + 3;
                blank(out, source, i, stop);
                i = stop;
            } else if (c == '"' || c == '\'') {
                int stop = i + 1;
                while (stop < n && source.charAt(stop) != c && source.charAt(stop) != '\n') {
                    stop += source.charAt(stop) == '\\' ? 2 : 1;
                }
                stop = Math.min(n, stop + 1);
                blank(out, source, i, stop);
                i = stop;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void blank(StringBuilder out, String source, int from, int to) {
        for (int k = from; k < to && k < source.length(); k++) {
            out.append(source.charAt(k) == '\n' ? '\n' : ' ');
        }
    }

    static List<String> violations(String source) {
        String code = code(source);
        List<String> found = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(code).find()) {
                found.add(rule.name());
            }
        }
        return found;
    }

    private static Path mainSources() {
        return Path.of(System.getProperty("basedir", ".")).resolve("src").resolve("main").resolve("java")
                .toAbsolutePath().normalize();
    }

    @Test
    void theProductionSourceBreaksNoStructuralRule() throws IOException {
        List<String> problems = new ArrayList<>();
        int files = 0;
        try (Stream<Path> paths = Files.walk(mainSources())) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                files++;
                for (String rule : violations(Files.readString(path, StandardCharsets.UTF_8))) {
                    problems.add(mainSources().relativize(path) + ": " + rule);
                }
            }
        }

        assertThat(files).as("the scan must really see the production classes").isGreaterThan(25);
        assertThat(problems).isEmpty();
    }

    @Test
    void theScanCoversTheGatewayFilterAndTheConfigurationClasses() throws IOException {
        try (Stream<Path> paths = Files.walk(mainSources())) {
            List<String> names = paths.map(p -> p.getFileName().toString()).toList();

            assertThat(names).contains("ApiGatewayFilter.java", "TomcatConfig.java", "WebConfig.java", "RouteTable.java");
        }
    }

    @Test
    void eachRuleReportsASourceThatBreaksIt() {
        assertThat(violations("class A { void f(jakarta.servlet.http.HttpServletResponse r) throws Exception {"
                + " r.sendError(500); } }")).containsExactly("sendError");
        assertThat(violations("class A { void f(@RequestBody String s) { } }")).containsExactly("@RequestBody");
        assertThat(violations("class A { void f(@PathVariable String s) { } }")).containsExactly("@PathVariable");
        assertThat(violations("class A { void f(@Valid Object s) { } }")).containsExactly("@Valid");
        assertThat(violations("@PostMapping(path = \"/x\", consumes = \"application/json\") class A { }"))
                .containsExactly("consumes");
        assertThat(violations("@GetMapping(produces = \"text/plain\") class A { }")).containsExactly("produces");
        assertThat(violations("@CrossOrigin class A { }")).containsExactly("@CrossOrigin");
        assertThat(violations("@ResponseStatus(HttpStatus.OK) class A { }")).containsExactly("@ResponseStatus");
        assertThat(violations("class A extends ResponseEntityExceptionHandler { }"))
                .containsExactly("extends ResponseEntityExceptionHandler");
    }

    @Test
    void documentationAndStringsThatNameTheRulesAreNotViolations() {
        String source = """
                /** Never calls sendError, @RequestBody or @PathVariable; consumes = nothing. */
                class A {
                    // @CrossOrigin and @ResponseStatus are not used; sendError(500) is forbidden
                    /* @Valid produces = "x" */
                    String text = "response.sendError(404) and @RequestBody";
                    char quote = '"';
                    String block = \"\"\"
                            @PathVariable consumes = "x"
                            \"\"\";
                    void ok(@Validated Object o) { }
                }
                """;

        assertThat(violations(source)).isEmpty();
    }

    @Test
    void aViolationAfterATrickyLiteralIsStillFound() {
        String source = "class A { String s = \"\\\"\"; char c = '\\''; void f(jakarta.servlet.http.HttpServletResponse r)"
                + " throws Exception { r.sendError(1); } }";

        assertThat(violations(source)).containsExactly("sendError");
    }
}
