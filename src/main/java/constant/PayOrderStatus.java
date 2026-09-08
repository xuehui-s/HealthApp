package constant;

/**
 * 缴费单状态常量（企业级扩展版）
 */
public interface PayOrderStatus {
    int WAIT_PAY = 0;            // 待缴费
    int PAY_SUCCESS = 1;         // 已缴费（终态）
    int DOCTOR_INVALID = 2;      // 医生手动作废（终态）
    int TIME_OUT_INVALID = 3;    // 超时自动作废（终态）
    int REFUNDED = 4;            // 已全额退款（终态）
    int PARTIAL_REFUND = 5;      // 部分退款（终态）

    /** 获取状态中文名 */
    static String getName(int status) {
        return switch (status) {
            case WAIT_PAY -> "待缴费";
            case PAY_SUCCESS -> "已缴费";
            case DOCTOR_INVALID -> "已作废";
            case TIME_OUT_INVALID -> "超时作废";
            case REFUNDED -> "已退款";
            case PARTIAL_REFUND -> "部分退款";
            default -> "未知";
        };
    }
}
