package me.ifmo.backend.audit.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuditTransactionListenerTest {

    @Test
    void actorSetupFailureRollsBackAndPropagatesTheErrorBeforeTheTransactionBody() {
        var manager = mock(PlatformTransactionManager.class);
        var jdbc = mock(JdbcTemplate.class);
        var status = mock(TransactionStatus.class);
        var failure = new IllegalStateException("Could not set audit actor");
        when(status.isNewTransaction()).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(String.class), anyString())).thenThrow(failure);

        assertThatThrownBy(() -> new AuditTransactionListener(manager, jdbc).afterBegin(status, null))
                .isSameAs(failure);

        verify(manager).rollback(status);
    }

    @Test
    void failedBeginAndJoinedTransactionsDoNotChangeTheActor() {
        var manager = mock(PlatformTransactionManager.class);
        var jdbc = mock(JdbcTemplate.class);
        var status = mock(TransactionStatus.class);
        var listener = new AuditTransactionListener(manager, jdbc);

        listener.afterBegin(status, new IllegalStateException("Begin failed"));
        listener.afterBegin(status, null);

        verifyNoInteractions(jdbc, manager);
    }
}
