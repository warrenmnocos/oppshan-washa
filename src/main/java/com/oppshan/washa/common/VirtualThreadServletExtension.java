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
 * Undertow SPI extension that replaces the servlet worker thread pool with a virtual-thread-per-task
 * executor.
 *
 * <p>Registered via {@code META-INF/services/io.undertow.servlet.ServletExtension}. The
 * {@link RegisterForReflection} annotation guarantees that GraalVM keeps the no-arg constructor
 * accessible so {@code ServiceLoader.load(ServletExtension.class)} can instantiate this class at
 * runtime in the native image. Because every handler already runs on a virtual thread, do not add
 * {@code @RunOnVirtualThread} to endpoints.
 */
@RegisterForReflection
public class VirtualThreadServletExtension implements ServletExtension, UncaughtExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadServletExtension.class);

    private final ExecutorService virtualExecutorService;

    public VirtualThreadServletExtension() {
        virtualExecutorService = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("undertow-virtual-thread-", 1)
                        .inheritInheritableThreadLocals(true)
                        .uncaughtExceptionHandler(this)
                        .factory()
        );
    }

    @Override
    public void handleDeployment(DeploymentInfo deploymentInfo,
                                 ServletContext servletContext) {
        deploymentInfo.setExecutor(virtualExecutorService);
        deploymentInfo.setAsyncExecutor(virtualExecutorService);
    }

    @Override
    public void uncaughtException(Thread thread,
                                  Throwable ex) {
        logger.error("Uncaught exception in virtual thread: {}", thread.getName(), ex);
    }
}
