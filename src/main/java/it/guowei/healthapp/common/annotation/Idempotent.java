package it.guowei.healthapp.common.annotation;

import java.lang.annotation.*;

/**
 * 接口幂等性注解
 * 基于Redis + Token机制，防止重复提交
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等key（支持SpEL表达式）
     */
    String key() default "";

    /**
     * 过期时间（秒）
     */
    int expire() default 60;

    /**
     * 提示信息
     */
    String message() default "请勿重复提交";
}
