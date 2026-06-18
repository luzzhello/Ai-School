-- SnailJob：定时清理 3 个月前未更新的云端作品（uc_work_file）
-- 执行器：workFileCleanJobExecutor（见 WorkFileCleanJobExecutor）
-- 调度：每天凌晨 3 点；需在 application.yml 中开启 snail-job.enabled=true 并启动 snailjob-server
-- 若任务已存在请勿重复执行本脚本

INSERT INTO `sj_job` (
    `namespace_id`, `group_name`, `job_name`, `args_str`, `args_type`, `next_trigger_at`,
    `job_status`, `task_type`, `route_key`, `executor_type`, `executor_info`,
    `trigger_type`, `trigger_interval`, `block_strategy`, `executor_timeout`, `max_retry_times`,
    `parallel_num`, `retry_interval`, `bucket_index`, `resident`, `notify_ids`, `owner_id`,
    `labels`, `description`, `ext_attrs`, `deleted`
)
SELECT
    ns.`unique_id`, 'ruoyi_group', 'work-file-clean', NULL, 1,
    UNIX_TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 3 HOUR) * 1000,
    1, 1, 4, 1, 'workFileCleanJobExecutor',
    1, '0 0 3 * * ?', 1, 300, 3,
    1, 60, 0, 0, '', NULL,
    '', '清理3个月前未更新的云端作品文件', '', 0
FROM `sj_namespace` ns
WHERE ns.`unique_id` IN ('dev', 'prod')
  AND NOT EXISTS (
      SELECT 1 FROM `sj_job` j
      WHERE j.`namespace_id` = ns.`unique_id`
        AND j.`group_name` = 'ruoyi_group'
        AND j.`job_name` = 'work-file-clean'
        AND j.`deleted` = 0
  );
