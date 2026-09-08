-- ============================================================
-- HealthApp 医疗预约系统 - 数据库初始化脚本
-- 适用于 MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS healthapp2
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE healthapp2;

-- ============================================================
-- 1. 患者表
-- ============================================================
CREATE TABLE IF NOT EXISTS patient (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(20)  NOT NULL UNIQUE COMMENT '手机号（登录账号）',
    password    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    name        VARCHAR(50)  DEFAULT NULL COMMENT '姓名',
    gender      VARCHAR(5)   DEFAULT NULL COMMENT '性别',
    age         INT          DEFAULT NULL COMMENT '年龄',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status      TINYINT      DEFAULT 1 COMMENT '状态：1-正常，0-禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者表';

-- ============================================================
-- 2. 科室表
-- ============================================================
CREATE TABLE IF NOT EXISTS department (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL COMMENT '科室名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '科室描述',
    status      TINYINT      DEFAULT 1 COMMENT '状态：1-正常，0-停用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科室表';

-- 初始科室数据
INSERT INTO department (name, description) VALUES
('内科', '诊治内科疾病，包括呼吸、消化、心血管等'),
('外科', '诊治外科疾病，包括普外、骨科、泌尿等'),
('儿科', '诊治儿童疾病'),
('妇产科', '妇科疾病诊治与产科保健'),
('眼科', '眼部疾病诊治'),
('口腔科', '口腔疾病诊治'),
('皮肤科', '皮肤疾病诊治'),
('中医科', '中医诊疗服务')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============================================================
-- 3. 医生表
-- ============================================================
CREATE TABLE IF NOT EXISTS doctor (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(20)  NOT NULL UNIQUE COMMENT '身份证后4位（登录账号）',
    password       VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    name           VARCHAR(50)  NOT NULL COMMENT '医生姓名',
    department_id  INT          NOT NULL COMMENT '所属科室ID',
    title          VARCHAR(50)  DEFAULT NULL COMMENT '职称',
    phone          VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    id_card        VARCHAR(20)  DEFAULT NULL COMMENT '身份证号',
    status         TINYINT      DEFAULT 1 COMMENT '状态：1-正常，0-停诊',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生表';

-- ============================================================
-- 4. 预约表
-- ============================================================
CREATE TABLE IF NOT EXISTS appointment (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    patient_id   INT          NOT NULL COMMENT '患者ID',
    dept_id      INT          NOT NULL COMMENT '科室ID',
    doctor_id    INT          NOT NULL COMMENT '医生ID',
    appoint_date DATE         NOT NULL COMMENT '预约日期',
    time_period  VARCHAR(10)  NOT NULL COMMENT '时段：上午/下午',
    queue_num    INT          DEFAULT 0 COMMENT '排队序号',
    front_count  INT          DEFAULT 0 COMMENT '前方等待人数',
    status       TINYINT      DEFAULT 0 COMMENT '状态：0-已预约待签到，1-已签到待就诊，2-已开单待缴费，3-已缴费诊疗中，4-患者取消，5-医生请假取消，6-缴费超时终止',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_date_period (appoint_date, time_period),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约表';

-- ============================================================
-- 5. 患者预约限制表
-- ============================================================
CREATE TABLE IF NOT EXISTS patient_limit (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    patient_id    INT  NOT NULL COMMENT '患者ID',
    date          DATE NOT NULL COMMENT '日期',
    appoint_count INT  DEFAULT 0 COMMENT '当日预约次数',
    cancel_count  INT  DEFAULT 0 COMMENT '当日取消次数',
    UNIQUE KEY uk_patient_date (patient_id, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者预约限制表';

-- ============================================================
-- 6. 医生请假表
-- ============================================================
CREATE TABLE IF NOT EXISTS doctor_leave (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    doctor_id   INT         NOT NULL COMMENT '医生ID',
    dept_id     INT         DEFAULT NULL COMMENT '科室ID',
    leave_date  DATE        NOT NULL COMMENT '请假日期',
    time_period VARCHAR(10) NOT NULL COMMENT '时段：上午/下午/全天',
    type        TINYINT     DEFAULT 1 COMMENT '类型：1-常规，2-紧急',
    reason      VARCHAR(500) DEFAULT NULL COMMENT '请假原因',
    status      TINYINT     DEFAULT 1 COMMENT '状态：1-生效中，0-已取消',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doctor_date (doctor_id, leave_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生请假表';

-- ============================================================
-- 7. 缴费单表（企业增强版）
-- ============================================================
CREATE TABLE IF NOT EXISTS pay_order (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no         VARCHAR(64)   NOT NULL UNIQUE COMMENT '缴费单号（雪花ID）',
    transaction_no   VARCHAR(64)   DEFAULT NULL COMMENT '交易流水号（支付成功后生成）',
    appointment_id   BIGINT        NOT NULL COMMENT '关联预约ID',
    patient_id       BIGINT        NOT NULL COMMENT '患者ID',
    doctor_id        BIGINT        NOT NULL COMMENT '开单医生ID',
    dept_id          BIGINT        NOT NULL COMMENT '科室ID',
    total_amount     DECIMAL(10,2) NOT NULL COMMENT '总金额',
    pay_method       VARCHAR(20)   DEFAULT NULL COMMENT '支付方式：CASH/WECHAT/ALIPAY/BANK_CARD/MEDICARE',
    status           TINYINT       DEFAULT 0 COMMENT '状态：0-待缴费，1-已缴费，2-医生作废，3-超时作废，4-已退款，5-部分退款',
    pay_time         DATETIME      DEFAULT NULL COMMENT '支付时间',
    payer_id         BIGINT        DEFAULT NULL COMMENT '收费员ID',
    expire_time      DATETIME      DEFAULT NULL COMMENT '过期时间',
    doctor_remark    VARCHAR(500)  DEFAULT NULL COMMENT '医生备注',
    pay_remark       VARCHAR(500)  DEFAULT NULL COMMENT '收费备注',
    receipt_printed  TINYINT       DEFAULT 0 COMMENT '收据是否已打印：0-未打印，1-已打印',
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_appointment (appointment_id),
    INDEX idx_status (status),
    INDEX idx_pay_time (pay_time),
    INDEX idx_transaction (transaction_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缴费单表';

-- ============================================================
-- 8. 费用明细表（新增）
-- ============================================================
CREATE TABLE IF NOT EXISTS bill_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no      VARCHAR(64)    NOT NULL COMMENT '关联缴费单号',
    category      VARCHAR(30)    NOT NULL COMMENT '费用类别：DRUG/EXAM/CONSULT/MATERIAL/TREAT/REGISTRATION/OTHER',
    item_name     VARCHAR(200)   NOT NULL COMMENT '项目名称',
    specification VARCHAR(200)   DEFAULT NULL COMMENT '规格说明',
    unit_price    DECIMAL(10,2)  NOT NULL COMMENT '单价',
    quantity      INT            DEFAULT 1 COMMENT '数量',
    subtotal      DECIMAL(10,2)  NOT NULL COMMENT '小计金额',
    remark        VARCHAR(500)   DEFAULT NULL COMMENT '备注',
    create_time   DATETIME       DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用明细表';

-- ============================================================
-- 9. 退款单表（新增）
-- ============================================================
CREATE TABLE IF NOT EXISTS refund_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_no       VARCHAR(64)    NOT NULL UNIQUE COMMENT '退款流水号',
    order_no        VARCHAR(64)    NOT NULL COMMENT '关联原缴费单号',
    appointment_id  BIGINT         NOT NULL COMMENT '关联预约ID',
    patient_id      BIGINT         NOT NULL COMMENT '患者ID',
    original_amount DECIMAL(10,2)  NOT NULL COMMENT '原缴费金额',
    refund_amount   DECIMAL(10,2)  NOT NULL COMMENT '退款金额',
    refund_method   VARCHAR(20)    DEFAULT NULL COMMENT '退款方式',
    refund_reason   VARCHAR(500)   DEFAULT NULL COMMENT '退款原因',
    status          TINYINT        DEFAULT 0 COMMENT '状态：0-待审核，1-已退款，2-已拒绝',
    operator_id     BIGINT         DEFAULT NULL COMMENT '操作人ID',
    auditor_id      BIGINT         DEFAULT NULL COMMENT '审核人ID',
    audit_remark    VARCHAR(500)   DEFAULT NULL COMMENT '审核备注',
    refund_time     DATETIME       DEFAULT NULL COMMENT '退款时间',
    audit_time      DATETIME       DEFAULT NULL COMMENT '审核时间',
    create_time     DATETIME       DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no),
    INDEX idx_patient (patient_id),
    INDEX idx_status (status),
    INDEX idx_refund_time (refund_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';

-- ============================================================
-- 10. 系统消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL COMMENT '用户ID',
    user_type   TINYINT       NOT NULL COMMENT '用户类型：1-患者，2-医生',
    title       VARCHAR(200)  NOT NULL COMMENT '消息标题',
    content     TEXT          NOT NULL COMMENT '消息内容',
    msg_type    TINYINT       DEFAULT 0 COMMENT '消息类型：1-预约成功，2-预约取消，3-缴费成功，4-就诊提醒，5-退款通知，6-新预约提醒，7-预约被取消，8-订单超时',
    relation_id BIGINT        DEFAULT NULL COMMENT '关联业务ID',
    is_read     TINYINT       DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id, user_type, is_read),
    INDEX idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息表';

-- ============================================================
-- 完成提示
-- ============================================================
SELECT 'HealthApp 数据库初始化完成！' AS message;
