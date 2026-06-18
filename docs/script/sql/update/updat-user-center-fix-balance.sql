-- 修复历史数据：按流水汇总回填 uc_wallet.balance（充值有流水但余额为 0 时执行）

UPDATE uc_wallet w
INNER JOIN (
    SELECT user_id, COALESCE(SUM(change_amount), 0) AS total
    FROM uc_wallet_log
    GROUP BY user_id
) t ON w.user_id = t.user_id
SET w.balance = t.total,
    w.update_time = NOW();
