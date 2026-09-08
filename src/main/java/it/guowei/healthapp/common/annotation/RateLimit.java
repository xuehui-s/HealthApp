package it.guowei.healthapp.common.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解（基于Redisson + 滑动窗口）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流key前缀
     */
    String key() default "rate_limit:";

    /**
     * 时间窗口（秒）
     */
    int time() default 60;

    /**
     * 时间窗口内最大请求数
     */
    int count() default 100;

    /**
     * 限流类型：IP / USER / DEFAULT
     */
    LimitType type() default LimitType.DEFAULT;

    enum LimitType {
        IP, USER, DEFAULT
    }
}
