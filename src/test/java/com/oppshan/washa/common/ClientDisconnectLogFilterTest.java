package com.oppshan.washa.common;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ClientDisconnectLogFilterTest {

    private static final String REQUEST_IO_LOGGER = "io.undertow.request.io";
    private static final String COMPUTE_PATH = "/api/budget/compute";

    private final ClientDisconnectLogFilter filter = new ClientDisconnectLogFilter();

    @Test
    void shouldDropResponseAlreadySentForAComputeRequest() {
        // The exact shape a switchMap-cancelled /compute produces: the UT000127 sits in a nested cause.
        final var dropped = filter.isLoggable(computeRecord(
                new IOException(new IllegalStateException("UT000127: Response has already been sent"))));

        assertThat(dropped, is(false));
    }

    @Test
    void shouldDropConnectionResetForAComputeRequest() {
        assertThat(filter.isLoggable(computeRecord(new IOException("Connection reset by peer"))), is(false));
    }

    @Test
    void shouldDropBrokenPipeForAComputeRequest() {
        assertThat(filter.isLoggable(computeRecord(new IOException("Broken pipe"))), is(false));
    }

    @Test
    void shouldDropComputeDisconnectFromARealExtLogRecord() {
        // Exercises the formatted-message path with the production record type (jboss-logmanager).
        final var record = new ExtLogRecord(Level.SEVERE,
                "Exception handling request abc-1 to " + COMPUTE_PATH, ClientDisconnectLogFilter.class.getName());
        record.setLoggerName(REQUEST_IO_LOGGER);
        record.setThrown(new IOException(new IllegalStateException("UT000127: Response has already been sent")));

        assertThat(filter.isLoggable(record), is(false));
    }

    @Test
    void shouldKeepDisconnectForANonComputeEndpoint() {
        // A disconnect on any other endpoint must still log: only /compute's slider noise is dropped.
        assertThat(filter.isLoggable(requestIoRecord("/api/budget/month/2026-07",
                new IOException("Connection reset by peer"))), is(true));
    }

    @Test
    void shouldKeepGenuineIoErrorForAComputeRequest() {
        // Not a disconnect: a real write failure on compute must still surface.
        assertThat(filter.isLoggable(computeRecord(new IOException("disk full while writing response"))), is(true));
    }

    @Test
    void shouldKeepDisconnectFromAnotherLogger() {
        // A datasource "Connection reset" logged elsewhere must NOT be swallowed, even if its message
        // happened to mention the compute path: only the request-IO channel is scrutinized.
        assertThat(filter.isLoggable(record("io.agroal.pool",
                "Exception to " + COMPUTE_PATH, new IOException("Connection reset"))), is(true));
    }

    @Test
    void shouldKeepComputeRecordWhoseCauseHasNoMessage() {
        assertThat(filter.isLoggable(computeRecord(new IOException())), is(true));
    }

    @Test
    void shouldKeepComputeRecordWithoutAThrown() {
        assertThat(filter.isLoggable(computeRecord(null)), is(true));
    }

    private static LogRecord computeRecord(Throwable thrown) {
        return requestIoRecord(COMPUTE_PATH, thrown);
    }

    private static LogRecord requestIoRecord(String path,
                                             Throwable thrown) {
        return record(REQUEST_IO_LOGGER, "Exception handling request abc-1 to " + path, thrown);
    }

    private static LogRecord record(String loggerName,
                                    String message,
                                    Throwable thrown) {
        final var logRecord = new LogRecord(Level.SEVERE, message);
        logRecord.setLoggerName(loggerName);
        logRecord.setThrown(thrown);
        return logRecord;
    }
}
