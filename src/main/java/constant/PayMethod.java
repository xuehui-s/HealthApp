package constant;

/**
 * 支付方式常量
 */
public interface PayMethod {
    String CASH = "CASH";           // 现金
    String WECHAT = "WECHAT";       // 微信支付
    String ALIPAY = "ALIPAY";       // 支付宝
    String BANK_CARD = "BANK_CARD"; // 银行卡
    String MEDICARE = "MEDICARE";   // 医保结算

    /** 获取支付方式中文名 */
    static String getName(String method) {
        return switch (method) {
            case CASH -> "现金";
            case WECHAT -> "微信支付";
            case ALIPAY -> "支付宝";
            case BANK_CARD -> "银行卡";
            case MEDICARE -> "医保结算";
            default -> "未知";
        };
    }
}
