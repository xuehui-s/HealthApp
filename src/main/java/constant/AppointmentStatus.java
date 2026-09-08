package constant;

/**
 * 预约单状态常量
 * 状态流转: 已预约(0) → 签到待就诊(1) → 待缴费(2) → 已缴费诊疗中(3) → 诊疗完成
 *           任意状态 → 患者取消(4) / 医生请假取消(5) / 缴费超时终止(6)
 */
public interface AppointmentStatus {
    int WAIT_SIGN = 0;          // 已预约，待签到
    int WAIT_DIAGNOSE = 1;      // 已签到，待就诊
    int WAIT_PAY = 2;           // 已开单，待缴费
    int PAY_SUCCESS = 3;        // 已缴费，诊疗中（终态）
    int PATIENT_CANCEL = 4;     // 患者主动取消（终态）
    int DOCTOR_LEAVE_CANCEL = 5;// 医生请假，系统自动取消（终态）
    int BILL_EXPIRE = 6;        // 缴费单超时/作废，就诊终止（终态）

    /** 获取状态中文名 */
    static String getName(int status) {
        return switch (status) {
            case WAIT_SIGN -> "已预约";
            case WAIT_DIAGNOSE -> "待就诊";
            case WAIT_PAY -> "待缴费";
            case PAY_SUCCESS -> "已缴费";
            case PATIENT_CANCEL -> "已取消";
            case DOCTOR_LEAVE_CANCEL -> "医生请假取消";
            case BILL_EXPIRE -> "缴费超时终止";
            default -> "未知";
        };
    }
}