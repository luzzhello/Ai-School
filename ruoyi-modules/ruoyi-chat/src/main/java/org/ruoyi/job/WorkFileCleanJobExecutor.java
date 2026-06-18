package org.ruoyi.job;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import org.ruoyi.service.usercenter.IUserWorkFileService;
import org.springframework.stereotype.Component;

/**
 * 定时清理超过 3 个月未更新的云端作品文件
 */
@Component
@JobExecutor(name = "workFileCleanJobExecutor")
@RequiredArgsConstructor
public class WorkFileCleanJobExecutor {

    private static final int RETAIN_MONTHS = 3;

    private final IUserWorkFileService workFileService;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        int monthBefore;
        if (jobArgs != null && jobArgs.getJobParams() instanceof Integer) {
            monthBefore = (Integer) jobArgs.getJobParams();
        } else {
            monthBefore = RETAIN_MONTHS;
        }
        int removed = workFileService.cleanExpiredFiles(monthBefore);
        String message = String.format("云端作品清理完成，删除 %d 条超过 %d 个月未更新的记录", removed, RETAIN_MONTHS);
        SnailJobLog.REMOTE.info(message);
        return ExecuteResult.success(message);
    }
}
