package constant;

/** 预约单状态常量 */
public interface AppointmentStatus {
    int WAIT_SIGN = 0;       // 已预约，待签到
    int WAIT_DIAGNOSE = 1;   // 已签到，待就诊
    int WAIT_PAY = 2;        // 已开单，待缴费
    int PAY_SUCCESS = 3;     // 已缴费，诊疗中
    int PATIENT_CANCEL = 4;  // 患者主动取消
    int DOCTOR_LEAVE_CANCEL =5;// 医生请假，系统取消
    int BILL_EXPIRE = 6;     // 缴费单超时/作废，本次就诊终止
}