package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("patient")
public class Patient {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String password;
    private String name;
    private String gender;
    private Integer age;
    private String phone;
    private Integer status;
    private Date createTime;
    private Date updateTime;

    // ====== 关键在这里！加这个注解就不报错了 ======
    @TableField(exist = false)
    private String code;   // 登录用的验证码

    @TableField(exist = false)
    private String uuid;   // 登录用的唯一标识
}