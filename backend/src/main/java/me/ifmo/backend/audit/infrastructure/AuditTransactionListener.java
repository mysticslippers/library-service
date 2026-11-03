package me.ifmo.backend.audit.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.TransactionStatus;

@RequiredArgsConstructor
public final class AuditTransactionListener implements TransactionExecutionListener {

    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbc;
    private final AuditActorResolver actorResolver = new AuditActorResolver();

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
        if (beginFailure != null || !transaction.isNewTransaction())
            return;

        try {
            jdbc.queryForObject("SELECT set_config('app.current_user_id', ?, true)", String.class, actorResolver.currentActor());
        } catch (RuntimeException | Error exception) {
            try {
                transactionManager.rollback((TransactionStatus) transaction);
            } catch (RuntimeException | Error rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }
}
