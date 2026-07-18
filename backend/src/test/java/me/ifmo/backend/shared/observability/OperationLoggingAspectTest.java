package me.ifmo.backend.shared.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Operation logging aspect")
@ExtendWith(OutputCaptureExtension.class)
class OperationLoggingAspectTest {

    private TestService createProxy() {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new TestService());
        proxyFactory.addAspect(new OperationLoggingAspect());
        return proxyFactory.getProxy();
    }

    static class TestService {

        @LoggableOperation("test.success")
        public String succeed() {
            return "result";
        }

        @LoggableOperation("test.failure")
        public void fail() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    @DisplayName("Logs successful operation without changing its result")
    void logsSuccessfulOperationWithoutChangingResult(CapturedOutput output) {
        TestService service = createProxy();

        String result = service.succeed();

        assertThat(result).isEqualTo("result");
        assertThat(output)
                .contains("operation=test.success")
                .contains("outcome=success")
                .contains("duration_ms=");
    }

    @Test
    @DisplayName("Logs failed operation and rethrows the exception")
    void logsFailedOperationAndRethrowsException(CapturedOutput output) {
        TestService service = createProxy();

        assertThatThrownBy(service::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(output)
                .contains("operation=test.failure")
                .contains("outcome=failure")
                .contains("error_type=IllegalStateException")
                .contains("duration_ms=");
    }
}
