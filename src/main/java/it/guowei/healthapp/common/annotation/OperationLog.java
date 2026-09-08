package it.guowei.healthapp.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * 记录用户操作行为，用于审计追溯
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作模块
     */
    String module() default "";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 操作类型：INSERT/UPDATE/DELETE/QUERY/EXPORT/OTHER
     */
    String type() default "OTHER";

    /**
     * 是否记录请求参数
     */
    boolean recordParams() default true;

    /**
     * 是否记录返回结果
     */
    boolean recordResult() default false;
}
