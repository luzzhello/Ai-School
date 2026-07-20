package org.ruoyi.service.paper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时预热 jieba，避免第一次文献检索 / Debug 步进时长时间卡住。
 */
@Slf4j
@Component
@Order(50)
public class LitJiebaWarmup implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        try {
            long t0 = System.currentTimeMillis();
            LitQueryNormalizer.warmup();
            log.info("lit jieba warmup done in {} ms", System.currentTimeMillis() - t0);
        } catch (Throwable e) {
            // NoClassDefFoundError 等属于 Error，不能只 catch Exception，否则拖垮启动
            log.warn("lit jieba warmup skipped: {}", e.toString());
        }
    }
}
