package com.oppshan.washa.common;

import io.undertow.servlet.api.DeploymentInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VirtualThreadServletExtensionTest {

    @Mock
    DeploymentInfo deploymentInfo;

    @Test
    void shouldInstallAVirtualThreadExecutorOnTheDeployment() {
        final var extension = new VirtualThreadServletExtension();

        extension.handleDeployment(deploymentInfo, null);

        final var executor = ArgumentCaptor.forClass(Executor.class);
        then(deploymentInfo).should().setExecutor(executor.capture());
        then(deploymentInfo).should().setAsyncExecutor(executor.getValue());

        // The installed executor runs tasks on a virtual thread.
        final var threadIsVirtual = new boolean[1];
        executor.getValue().execute(() -> threadIsVirtual[0] = Thread.currentThread().isVirtual());
        // Give the task a moment to run on its virtual thread.
        await(() -> threadIsVirtual[0]);
        assertThat(threadIsVirtual[0], is(true));
    }

    @Test
    void shouldLogAndSwallowUncaughtExceptions() {
        final var extension = new VirtualThreadServletExtension();
        assertDoesNotThrow(() -> extension.uncaughtException(Thread.currentThread(), new RuntimeException("boom")));
    }

    private void await(BooleanSupplier condition) {
        for (var index = 0; index < 100 && !condition.getAsBoolean(); index++) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
