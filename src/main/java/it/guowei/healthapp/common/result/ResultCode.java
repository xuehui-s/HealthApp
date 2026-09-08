package it.guowei.healthapp.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 企业级错误码枚举
 * 规则：1xxxx 系统级，2xxxx 用户级，3xxxx 业务级，4xxxx 第三方
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ========== 1xxxx 系统级 ==========
    SUCCESS(200, "操作成功"),
    FAIL(500, "系统繁忙，请稍后重试"),
    PARAM_ERROR(10001, "参数校验失败"),
    UNAUTHORIZED(10002, "未登录或Token已过期"),
    FORBIDDEN(10003, "无权限访问"),
    NOT_FOUND(10004, "资源不存在"),
    METHOD_NOT_ALLOWED(10005, "请求方法不支持"),
    TOO_MANY_REQUESTS(10006, "请求过于频繁，请稍后再试"),
    SERVICE_UNAVAILABLE(10007, "服务暂时不可用"),
    IDEMPOTENT_REPEAT(10008, "重复提交"),

    // ========== 2xxxx 用户级 ==========
    USER_NOT_FOUND(20001, "用户不存在"),
    USER_PASSWORD_ERROR(20002, "密码错误"),
    USER_ACCOUNT_DISABLED(20003, "账号已被禁用"),
    USER_ALREADY_EXISTS(20004, "用户已存在"),
    TOKEN_INVALID(20005, "Token无效"),
    TOKEN_EXPIRED(20006, "Token已过期"),

    // ========== 3xxxx 业务级 ==========
    APPOINTMENT_NOT_FOUND(30001, "预约记录不存在"),
    APPOINTMENT_FULL(30002, "该时段号源已满"),
    APPOINTMENT_ALREADY_EXISTS(30003, "已预约过该时段"),
    APPOINTMENT_CANCEL_LIMIT(30004, "今日取消次数已达上限"),
    APPOINTMENT_DATE_INVALID(30005, "预约日期不合法"),
    DOCTOR_NOT_FOUND(30006, "医生不存在"),
    DOCTOR_ON_LEAVE(30007, "医生此时段请假"),
    DOCTOR_STOPPED(30008, "医生已停诊"),
    DEPARTMENT_NOT_FOUND(30009, "科室不存在"),

    ORDER_NOT_FOUND(30010, "订单不存在"),
    ORDER_ALREADY_PAID(30011, "订单已支付"),
    ORDER_ALREADY_INVALID(30012, "订单已失效"),
    ORDER_EXPIRED(30013, "订单已超时"),
    ORDER_AMOUNT_ERROR(30014, "订单金额异常"),
    PAY_METHOD_INVALID(30015, "支付方式不支持"),
    REFUND_NOT_ALLOWED(30016, "当前状态不允许退款"),
    REFUND_AMOUNT_EXCEED(30017, "退款金额超出可退金额"),

    // ========== 4xxxx AI/第三方 ==========
    AI_SERVICE_ERROR(40001, "AI服务异常"),
    AI_QUESTION_EMPTY(40002, "问题不能为空"),
    AI_RATE_LIMIT(40003, "AI调用频率超限");

    private final Integer code;
    private final String message;
}
