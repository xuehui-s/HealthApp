package PoJo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@Data
@TableName("department")
public class Department {
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 科室名称 - 数据库列名为 name */
    @TableField("name")
    private String name;

    private String description;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}