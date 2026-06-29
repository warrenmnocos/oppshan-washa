package com.oppshan.washa.common;

import io.quarkus.logging.LoggingFilter;
import org.jboss.logmanager.ExtLogRecord;

import java.util.List;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Drops the benign client-disconnect records that Undertow's {@code io.undertow.request.io} logger
 * emits at ERROR when a browser aborts an in-flight POST {@code /api/budget/compute} mid-response. The
 * budget store recomputes a slider drag LIVE, and its switchMap cancels each still-in-flight compute as
 * a newer rate arrives (budget-store.ts); the cancel resets the socket while RESTEasy is still
 * serializing the JSON, so the write fails with {@code UT000127: Response has already been sent}.
 * Nothing is actually wrong: compute is side-effect-free and the client discarded the response on
 * purpose, so the only cost is log noise.
 *
 * <p>A record is dropped only when ALL of these hold, which keeps the suppression narrow: it is on the
 * request-IO logger, its rendered message names the {@code /api/budget/compute} path, and a
 * client-disconnect cause ({@code UT000127}, broken pipe, connection reset) sits somewhere in its
 * chain. So a disconnect on any OTHER endpoint still logs (the path won't match), a genuine
 * non-disconnect failure on compute still logs (no disconnect cause), and no other logger is ever
 * touched. That makes it safe on every profile, unlike muting the whole category. Registered as
 * {@code drop-client-disconnect} and attached to the console handler via
 * {@code quarkus.log.console.filter} in {@code application.properties}.
 */
@LoggingFilter(name = "drop-client-disconnect")
public final class ClientDisconnectLogFilter implements Filter {

    private static final String REQUEST_IO_LOGGER = "io.undertow.request.io";

    // The endpoint the FX sliders hammer: only its client-disconnects are noise worth dropping.
    private static final String COMPUTE_PATH = "/api/budget/compute";

    // Substrings of the messages a client abort produces, matched anywhere in the cause chain: Undertow's
    // "response already sent" code and the JDK socket-reset messages.
    private static final List<String> DISCONNECT_MARKERS = List.of(
            "UT000127",            // Undertow: Response has already been sent
            "Broken pipe",
            "Connection reset");

    @Override
    public boolean isLoggable(LogRecord record) {
        if (!REQUEST_IO_LOGGER.equals(record.getLoggerName()) || !isComputeRequest(record)) {
            return true;
        }

        for (var cause = record.getThrown(); cause != null; cause = cause.getCause()) {
            final var message = cause.getMessage();
            if (message != null && isClientDisconnect(message)) {
                return false;
            }
        }

        return true;
    }

    // The request path lives only in the log message text ("...request <id> to <path>: ..."), so match it
    // against the fully rendered message: the raw getMessage() may still hold an unsubstituted '%s' with
    // the path passed as a separate parameter (the Quarkus log record is a jboss-logmanager ExtLogRecord).
    private static boolean isComputeRequest(LogRecord record) {
        final var message = record instanceof ExtLogRecord extended
                ? extended.getFormattedMessage()
                : record.getMessage();
        return message != null && message.contains(COMPUTE_PATH);
    }

    private static boolean isClientDisconnect(String message) {
        return DISCONNECT_MARKERS.stream().anyMatch(message::contains);
    }
}
