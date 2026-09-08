package Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 消息通知线程池：事件监听、日志落库等轻量异步任务
     */
    @Bean(name = "messageExecutor")
    public Executor messageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程
        executor.setCorePoolSize(50);

        // 最大线程
        executor.setMaxPoolSize(100);

        // 队列缓冲 10000 条
        executor.setQueueCapacity(10000);

        // 拒绝策略：不抛异常，用主线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setThreadNamePrefix("msg-async-");
        executor.initialize();
        return executor;
    }

    /**
     * AI Agent 线程池：调用 Python Agent 的异步任务与 SSE 转发。
     * 独立于业务线程池，避免 AI 长耗时请求拖垮消息/业务链路（线程池隔离）。
     */
    @Bean(name = "agentExecutor")
    public Executor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("agent-");
        executor.initialize();
        return executor;
    }
}
