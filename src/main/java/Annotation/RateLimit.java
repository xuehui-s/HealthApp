package Annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis限流注解
 * 使用方式: @RateLimit(key = "sendCode", limit = 1, window = 60, timeUnit = TimeUnit.SECONDS)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流key前缀 */
    String key() default "";

    /** 时间窗口内允许的次数 */
    int limit() default 10;

    /** 时间窗口大小 */
    int window() default 1;

    /** 时间单位 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** 限流提示消息 */
    String message() default "请求过于频繁，请稍后再试";
}
