package Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    // 成功无数据
    public static Result ok(){
        return new Result(true, null, null, null);
    }

    // 成功带数据（登录用这个！）
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }

    // 成功带消息和数据
    public static Result ok(String message, Object data){
        return new Result(true, message, data, null);
    }

    // 分页成功
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }

    // 失败（登录错误全部用这个）
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}