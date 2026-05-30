package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("doctor")
public class Doctor {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;    // 身份证后4位（登录账号）
    private String password;   // 密码 123456
    private String name;
    private Integer departmentId;
    private String title;
    private String phone;
    private String idCard;
    private Integer status;
    private Date createTime;
    private Date updateTime;

    // 登录验证码（数据库不存在）
    @TableField(exist = false)
    private String code;
}