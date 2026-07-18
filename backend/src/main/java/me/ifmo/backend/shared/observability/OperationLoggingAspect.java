package me.ifmo.backend.shared.observability;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class OperationLoggingAspect {

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    @Around("@annotation(loggableOperation)")
    public Object logOperation(ProceedingJoinPoint joinPoint, LoggableOperation loggableOperation) throws Throwable {
        long startedAt = System.nanoTime();
        String operation = loggableOperation.value();
        try {
            Object result = joinPoint.proceed();
            log.info("operation={} outcome=success duration_ms={}", operation, elapsedMillis(startedAt));
            return result;
        } catch (Throwable exception) {
            log.warn("operation={} outcome=failure error_type={} duration_ms={}", operation, exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            throw exception;
        }
    }
}
