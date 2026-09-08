package constant;

/**
 * 费用明细类别常量
 */
public interface BillItemCategory {
    String DRUG = "DRUG";           // 药品费
    String EXAM = "EXAM";           // 检查费
    String CONSULT = "CONSULT";     // 诊查费
    String MATERIAL = "MATERIAL";   // 材料费
    String TREAT = "TREAT";         // 治疗费
    String REGISTRATION = "REGISTRATION"; // 挂号费
    String OTHER = "OTHER";         // 其他

    static String getName(String category) {
        return switch (category) {
            case DRUG -> "药品费";
            case EXAM -> "检查费";
            case CONSULT -> "诊查费";
            case MATERIAL -> "材料费";
            case TREAT -> "治疗费";
            case REGISTRATION -> "挂号费";
            case OTHER -> "其他费用";
            default -> "未知";
        };
    }
}
