package me.ifmo.backend.shared.observability;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityConfigTest {

    @Test
    void observedAspectRecordsSuccessfulOperation() {
        TestContext context = createContext();

        assertThat(context.service().succeed()).isEqualTo("result");

        Timer timer = findTimer(context.meterRegistry(), "test.success");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void observedAspectRecordsFailedOperationAndRethrowsException() {
        TestContext context = createContext();

        assertThatThrownBy(context.service()::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        Timer timer = findTimer(context.meterRegistry(), "test.failure");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    private TestContext createContext() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meterRegistry));

        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new TestService());
        proxyFactory.addAspect(new ObservabilityConfig().observedAspect(observationRegistry));

        return new TestContext(proxyFactory.getProxy(), meterRegistry);
    }

    private Timer findTimer(SimpleMeterRegistry meterRegistry, String operation) {
        return meterRegistry.find("library.operation")
                .tag("domain", "test")
                .tag("operation", operation)
                .timer();
    }

    private record TestContext(TestService service, SimpleMeterRegistry meterRegistry) {
    }

    static class TestService {

        @Observed(
                name = "library.operation",
                contextualName = "test.success",
                lowCardinalityKeyValues = {"domain", "test", "operation", "test.success"}
        )
        public String succeed() {
            return "result";
        }

        @Observed(
                name = "library.operation",
                contextualName = "test.failure",
                lowCardinalityKeyValues = {"domain", "test", "operation", "test.failure"}
        )
        public void fail() {
            throw new IllegalStateException("boom");
        }
    }
}
