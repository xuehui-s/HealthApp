package constant;

/**
 * 企业级业务错误码
 * 统一管理错误码和错误消息，便于前端统一处理和国际化扩展
 */
public enum BusinessCode {

    // ==================== 通用 ====================
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    SYSTEM_ERROR(500, "系统内部错误"),

    // ==================== 缴费相关 ====================
    ORDER_NOT_FOUND(10001, "缴费单不存在"),
    ORDER_ALREADY_PAID(10002, "缴费单已支付"),
    ORDER_ALREADY_INVALID(10003, "缴费单已作废"),
    ORDER_EXPIRED(10004, "缴费单已过期"),
    ORDER_ALREADY_EXISTS(10005, "已存在待缴费单，不允许重复开单"),
    PAY_AMOUNT_ERROR(10006, "支付金额与订单金额不匹配"),
    REFUND_AMOUNT_EXCEED(10007, "退款金额不能超过已支付金额"),
    REFUND_NOT_ALLOWED(10008, "该订单状态不允许退款"),
    PAY_METHOD_INVALID(10009, "无效的支付方式"),

    // ==================== 预约相关 ====================
    APPOINTMENT_NOT_FOUND(20001, "预约单不存在"),
    APPOINTMENT_FULL(20002, "该时段已满员"),
    APPOINTMENT_DUPLICATE(20003, "您已预约过该时段"),
    APPOINTMENT_LIMIT(20004, "预约次数已达上限"),
    CANCEL_LIMIT(20005, "取消次数已达上限"),
    DOCTOR_NOT_AVAILABLE(20006, "该医生此时段不可预约"),
    DOCTOR_ON_LEAVE(20007, "该医生已请假"),

    // ==================== 用户相关 ====================
    LOGIN_CODE_EXPIRED(30001, "验证码已过期"),
    LOGIN_CODE_ERROR(30002, "验证码错误"),
    ACCOUNT_NOT_FOUND(30003, "账号不存在"),
    PASSWORD_ERROR(30004, "密码错误"),
    ACCOUNT_DISABLED(30005, "账号已被禁用"),
    PHONE_REGISTERED(30006, "该手机号已注册"),
    SEND_CODE_FREQUENT(30007, "验证码发送过于频繁"),

    // ==================== 结算相关 ====================
    SETTLEMENT_ALREADY_DONE(40001, "当日已结算，不可重复操作"),
    SETTLEMENT_DATA_EMPTY(40002, "当日无交易数据");

    private final int code;
    private final String message;

    BusinessCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
