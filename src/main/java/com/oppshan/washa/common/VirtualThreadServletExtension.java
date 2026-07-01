package com.oppshan.washa.common;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Undertow SPI extension that swaps the servlet worker thread pool for a virtual-thread-per-task
 * executor, so every request handler runs on its own virtual thread. That suits this app's blocking
 * style: handlers make blocking JDBC calls to Neon Postgres, and a virtual thread parked on I/O
 * costs almost nothing, so there's no fixed worker pool to size or exhaust. The class also serves as
 * the {@link UncaughtExceptionHandler} for those threads.
 *
 * <p>Registered via {@code META-INF/services/io.undertow.servlet.ServletExtension}. The
 * {@link RegisterForReflection} annotation keeps GraalVM from stripping the no-arg constructor, so
 * {@code ServiceLoader.load(ServletExtension.class)} can still instantiate this class at runtime in
 * the native image.
 */
@RegisterForReflection
public class VirtualThreadServletExtension implements ServletExtension, UncaughtExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadServletExtension.class);

    private final ExecutorService virtualExecutorService;

    /**
     * Builds the virtual-thread executor. Threads are named {@code undertow-virtual-thread-N} so
     * they stand out in logs and thread dumps, inherit inheritable thread-locals from the thread
     * that creates them, and route anything they throw uncaught back to this instance.
     */
    public VirtualThreadServletExtension() {
        virtualExecutorService = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("undertow-virtual-thread-", 1)
                        .inheritInheritableThreadLocals(true)
                        .uncaughtExceptionHandler(this)
                        .factory()
        );
    }

    /**
     * Undertow's per-deployment hook: point both the blocking-request executor and the async
     * executor at the virtual-thread executor. This is where the worker-pool swap actually lands.
     */
    @Override
    public void handleDeployment(DeploymentInfo deploymentInfo,
                                 ServletContext servletContext) {
        deploymentInfo.setExecutor(virtualExecutorService);
        deploymentInfo.setAsyncExecutor(virtualExecutorService);
    }

    /**
     * Last-resort logging for anything a handler thread throws without catching. Without a handler,
     * an uncaught exception on a virtual thread just goes to stderr, which is easy to miss.
     */
    @Override
    public void uncaughtException(Thread thread,
                                  Throwable ex) {
        logger.error("Uncaught exception in virtual thread: {}", thread.getName(), ex);
    }
}
