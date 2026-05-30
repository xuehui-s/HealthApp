package constant;

public interface MessageType {
    int SYSTEM = 1;          // 系统通知
    int REGISTER_SUCCESS = 2;// 挂号成功
    int PAY_SUCCESS = 3;     // 缴费成功 <-- 我们用的就是这个
    int CLOSE_ORDER = 4;     // 订单关闭/超时
}