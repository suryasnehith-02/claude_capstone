package com.capstone.todo.support;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

/**
 * Collects every Logback event of the JVM while open (the appender sits on the root logger), so a test can assert how
 * many entries a request produced and what they contain. The level of the named loggers is raised to DEBUG while open
 * and restored on close. Use it in a try-with-resources block; do not run tests that share it in parallel.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final List<Logger> raised;
    private final List<Level> levelsBefore;

    public LogCapture(String... debugLoggers) {
        raised = java.util.Arrays.stream(debugLoggers).map(name -> (Logger) LoggerFactory.getLogger(name)).toList();
        levelsBefore = raised.stream().map(Logger::getLevel).toList();
        raised.forEach(logger -> logger.setLevel(Level.DEBUG));
        appender.start();
        root.addAppender(appender);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public List<ILoggingEvent> errors() {
        return events().stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    public List<ILoggingEvent> debugs() {
        return events().stream().filter(event -> event.getLevel() == Level.DEBUG).toList();
    }

    /**
     * Everything one event would write to a log: the formatted message, the MDC values, and the whole throwable (class,
     * message, stack, and every cause and suppressed exception, exactly as Logback prints it). Leak assertions use this,
     * not {@code getFormattedMessage()}, which leaves the throwable out.
     */
    public static String render(ILoggingEvent event) {
        StringBuilder text = new StringBuilder(event.getFormattedMessage());
        event.getMDCPropertyMap().forEach((key, value) -> text.append('\n').append(key).append('=').append(value));
        if (event.getThrowableProxy() != null) {
            text.append('\n').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
        }
        return text.toString();
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
        for (int i = 0; i < raised.size(); i++) {
            raised.get(i).setLevel(levelsBefore.get(i));
        }
    }
}
