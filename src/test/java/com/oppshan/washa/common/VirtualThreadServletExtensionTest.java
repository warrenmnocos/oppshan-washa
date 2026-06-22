package com.oppshan.washa.common;

import io.undertow.servlet.api.DeploymentInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VirtualThreadServletExtensionTest {

    @Test
    void shouldInstallAVirtualThreadExecutorOnTheDeployment() {
        final var extension = new VirtualThreadServletExtension();
        final var deploymentInfo = mock(DeploymentInfo.class);

        extension.handleDeployment(deploymentInfo, null);

        final var executor = ArgumentCaptor.forClass(Executor.class);
        verify(deploymentInfo).setExecutor(executor.capture());
        verify(deploymentInfo).setAsyncExecutor(executor.getValue());

        // The installed executor runs tasks on a virtual thread.
        final var threadIsVirtual = new boolean[1];
        executor.getValue().execute(() -> threadIsVirtual[0] = Thread.currentThread().isVirtual());
        // Give the task a moment to run on its virtual thread.
        await(() -> threadIsVirtual[0]);
        assertThat(threadIsVirtual[0]).isTrue();
    }

    @Test
    void shouldLogAndSwallowUncaughtExceptions() {
        final var extension = new VirtualThreadServletExtension();
        assertThatCode(() -> extension.uncaughtException(Thread.currentThread(), new RuntimeException("boom")))
                .doesNotThrowAnyException();
    }

    private void await(java.util.function.BooleanSupplier condition) {
        for (var i = 0; i < 100 && !condition.getAsBoolean(); i++) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
