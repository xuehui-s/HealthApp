package constant;

/** 缴费单状态常量 */
public interface PayOrderStatus {
    int WAIT_PAY = 0;        // 待缴费
    int PAY_SUCCESS = 1;     // 已缴费（终态）
    int DOCTOR_INVALID = 2;  // 医生手动作废（终态）
    int TIME_OUT_INVALID = 3;// 超时自动作废（终态）

}