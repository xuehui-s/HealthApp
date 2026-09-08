package Exception;

import constant.BusinessCode;

/**
 * 业务异常类
 * 替代 RuntimeException，携带业务错误码
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final String message;

    public BusinessException(BusinessCode businessCode) {
        super(businessCode.getMessage());
        this.code = businessCode.getCode();
        this.message = businessCode.getMessage();
    }

    public BusinessException(BusinessCode businessCode, String detail) {
        super(detail != null ? businessCode.getMessage() + ": " + detail : businessCode.getMessage());
        this.code = businessCode.getCode();
        this.message = detail != null ? businessCode.getMessage() + ": " + detail : businessCode.getMessage();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    @Override
    public String getMessage() { return message; }
}
