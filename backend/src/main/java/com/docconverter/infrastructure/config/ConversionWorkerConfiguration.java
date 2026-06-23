package com.docconverter.infrastructure.config;

import com.docconverter.application.processing.ExponentialRetryBackoffPolicy;
import com.docconverter.application.processing.RetryBackoffPolicy;
import com.docconverter.infrastructure.scheduling.ConversionRetryProperties;
import com.docconverter.infrastructure.scheduling.ConversionWorkerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ConversionWorkerConfiguration {

    @Bean
    public RetryBackoffPolicy retryBackoffPolicy(ConversionRetryProperties properties) {
        return new ExponentialRetryBackoffPolicy(
                properties.getInitialDelay(),
                properties.getMaxDelay(),
                properties.getMultiplier()
        );
    }

    @Bean(name = "conversionTaskExecutor")
    public ThreadPoolTaskExecutor conversionTaskExecutor(
            ConversionWorkerProperties properties
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getMaxParallelism());
        executor.setMaxPoolSize(properties.getMaxParallelism());
        executor.setQueueCapacity(0);
        executor.setPrestartAllCoreThreads(true);
        executor.setThreadNamePrefix("conversion-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(properties.getShutdownTimeout().toMillis());
        return executor;
    }
}
