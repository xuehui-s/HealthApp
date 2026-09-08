package Exception;

import Dto.Result;
import constant.BusinessCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理各种异常，返回标准Result格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("[业务异常] URI: {}, Code: {}, Message: {}", request.getRequestURI(), e.getCode(), e.getMessage());
        Result result = new Result();
        result.setSuccess(false);
        result.setErrorMsg(e.getMessage());
        result.setData(e.getCode());
        return ResponseEntity.ok(result);
    }

    /** 缺少请求头 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result> handleMissingRequestHeader(MissingRequestHeaderException e, HttpServletRequest request) {
        log.warn("[缺少请求头] URI: {}, Header: {}", request.getRequestURI(), e.getHeaderName());
        Result result = new Result();
        result.setSuccess(false);
        result.setErrorMsg("缺少必要的请求头: " + e.getHeaderName());
        result.setData(BusinessCode.BAD_REQUEST.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /** 缺少请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result> handleMissingRequestParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("[缺少请求参数] URI: {}, Param: {}", request.getRequestURI(), e.getParameterName());
        Result result = new Result();
        result.setSuccess(false);
        result.setErrorMsg("缺少必要的请求参数: " + e.getParameterName());
        result.setData(BusinessCode.BAD_REQUEST.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /** 参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("[参数校验失败] URI: {}, Errors: {}", request.getRequestURI(), errors);
        Result result = new Result();
        result.setSuccess(false);
        result.setErrorMsg("参数校验失败: " + errors);
        result.setData(BusinessCode.BAD_REQUEST.getCode());
        return ResponseEntity.ok(result);
    }

    /** 运行时异常 */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("[运行时异常] URI: {}, Message: {}", request.getRequestURI(), e.getMessage(), e);
        Result result = new Result();
        result.setSuccess(false);
        result.setErrorMsg("系统繁忙，请稍后重试");
        result.setData(BusinessCode.SYSTEM_ERROR.getCode());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception e, HttpServletRequest request) {
        log.error("[未知异常] URI: {}, Type: {}", request.getRequestURI(), e.getClass().getName(), e);
        Result result = new Result();
        result.setSuccess(false);
        result.setErrorMsg("系统内部错误");
        result.setData(BusinessCode.SYSTEM_ERROR.getCode());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
