SELECT loan_id, reason, COUNT(*) AS active_fine_count, ARRAY_AGG(id ORDER BY id) AS fine_ids
    FROM fines WHERE loan_id IS NOT NULL AND status = 'ACTIVE'
               GROUP BY loan_id, reason HAVING count(*) > 1 ORDER BY loan_id, reason;
