package it.guowei.healthapp.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员（admin 表，见 db/upgrade_enterprise.sql，内置账号 admin/123456）
 */
@Data
@TableName("admin")
public class AdminUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文 */
    private String password;

    private String name;

    /** SUPER_ADMIN / ADMIN / OPERATOR */
    private String role;

    private String phone;

    /** 1-正常 0-禁用 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
