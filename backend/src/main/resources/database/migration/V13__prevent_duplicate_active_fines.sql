LOCK TABLE fines IN SHARE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM fines
        WHERE loan_id IS NOT NULL AND status = 'ACTIVE'
        GROUP BY loan_id, reason
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot apply V13: duplicate active fines exist for the same loan and reason'
            USING ERRCODE = '23505',
                  HINT = 'Review duplicates with scripts/database/check_active_fine_duplicates.sql before retrying the migration.';
    END IF;
END;
$$;

CREATE UNIQUE INDEX uq_fines_one_active_per_loan_reason
    ON fines (loan_id, reason)
    WHERE loan_id IS NOT NULL AND status = 'ACTIVE';
