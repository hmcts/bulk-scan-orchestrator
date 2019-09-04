package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    private static final int POOL_SIZE = 10;
    private static AtomicInteger errorCount = new AtomicInteger(0);
    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(orchestratorTaskScheduler());
    }

    @Bean
    public TaskScheduler orchestratorTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("BSO-");
        scheduler.setErrorHandler(t -> {
            log.error("Unhandled exception during task. {}: {}", t.getClass(), t.getMessage(), t);
            errorCount.incrementAndGet();
        });
        scheduler.initialize();

        return scheduler;
    }
}
