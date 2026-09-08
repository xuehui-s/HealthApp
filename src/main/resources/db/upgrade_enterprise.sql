-- ============================================================
-- HealthApp 企业版数据库升级脚本
-- 在原有表基础上新增：电子病历、处方、AI对话、操作日志、药品字典等
-- ============================================================

USE healthapp2;

-- ============================================================
-- 11. 电子病历表
-- ============================================================
CREATE TABLE IF NOT EXISTS medical_record (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id           BIGINT       NOT NULL COMMENT '患者ID',
    doctor_id            BIGINT       NOT NULL COMMENT '医生ID',
    appointment_id       BIGINT       DEFAULT NULL COMMENT '关联预约ID',
    dept_id              BIGINT       DEFAULT NULL COMMENT '科室ID',
    chief_complaint      VARCHAR(500) DEFAULT NULL COMMENT '主诉',
    present_illness      TEXT         DEFAULT NULL COMMENT '现病史',
    past_history         TEXT         DEFAULT NULL COMMENT '既往史',
    allergy_history      VARCHAR(500) DEFAULT NULL COMMENT '过敏史',
    physical_exam        TEXT         DEFAULT NULL COMMENT '体格检查',
    auxiliary_exam       TEXT         DEFAULT NULL COMMENT '辅助检查',
    preliminary_diagnosis VARCHAR(500) DEFAULT NULL COMMENT '初步诊断',
    treatment_plan       TEXT         DEFAULT NULL COMMENT '治疗意见',
    doctor_order         TEXT         DEFAULT NULL COMMENT '医嘱',
    status               TINYINT      DEFAULT 0 COMMENT '状态：0-草稿 1-已提交 2-已审核',
    create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_appointment (appointment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子病历表';

-- ============================================================
-- 12. 处方表
-- ============================================================
CREATE TABLE IF NOT EXISTS prescription (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    prescription_no VARCHAR(64)   NOT NULL UNIQUE COMMENT '处方编号',
    patient_id      BIGINT        NOT NULL COMMENT '患者ID',
    doctor_id       BIGINT        NOT NULL COMMENT '开方医生ID',
    appointment_id  BIGINT        DEFAULT NULL COMMENT '关联预约ID',
    dept_id         BIGINT        DEFAULT NULL COMMENT '科室ID',
    type            TINYINT       DEFAULT 1 COMMENT '类型：1-西药 2-中成药 3-中药饮片',
    diagnosis       VARCHAR(500)  DEFAULT NULL COMMENT '诊断',
    total_amount    DECIMAL(10,2) DEFAULT 0.00 COMMENT '总金额',
    status          TINYINT       DEFAULT 0 COMMENT '状态：0-待缴费 1-已缴费 2-已发药 3-已退药',
    auditor_id      BIGINT        DEFAULT NULL COMMENT '审核医生ID',
    audit_time      DATETIME      DEFAULT NULL COMMENT '审核时间',
    dispense_time   DATETIME      DEFAULT NULL COMMENT '发药时间',
    remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT       DEFAULT 0,
    INDEX idx_patient (patient_id),
    INDEX idx_doctor (doctor_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方表';

-- ============================================================
-- 13. 处方明细表
-- ============================================================
CREATE TABLE IF NOT EXISTS prescription_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    prescription_no VARCHAR(64)    NOT NULL COMMENT '处方编号',
    drug_id         BIGINT         DEFAULT NULL COMMENT '药品ID',
    drug_name       VARCHAR(200)   NOT NULL COMMENT '药品名称',
    specification   VARCHAR(200)   DEFAULT NULL COMMENT '规格',
    unit_price      DECIMAL(10,2)  NOT NULL COMMENT '单价',
    quantity        INT            DEFAULT 1 COMMENT '数量',
    unit            VARCHAR(20)    DEFAULT NULL COMMENT '单位',
    dosage          VARCHAR(200)   DEFAULT NULL COMMENT '用法用量',
    frequency       VARCHAR(50)    DEFAULT NULL COMMENT '频次',
    days            INT            DEFAULT NULL COMMENT '天数',
    subtotal        DECIMAL(10,2)  NOT NULL COMMENT '小计',
    remark          VARCHAR(500)   DEFAULT NULL COMMENT '备注',
    create_time     DATETIME       DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_prescription (prescription_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方明细表';

-- ============================================================
-- 14. 药品字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS drug (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_code       VARCHAR(50)    NOT NULL UNIQUE COMMENT '药品编码',
    drug_name       VARCHAR(200)   NOT NULL COMMENT '药品名称',
    generic_name    VARCHAR(200)   DEFAULT NULL COMMENT '通用名',
    specification   VARCHAR(200)   DEFAULT NULL COMMENT '规格',
    unit            VARCHAR(20)    DEFAULT NULL COMMENT '单位',
    manufacturer    VARCHAR(200)   DEFAULT NULL COMMENT '生产厂家',
    category        VARCHAR(50)    DEFAULT NULL COMMENT '分类：西药/中成药/中药饮片',
    price           DECIMAL(10,2)  NOT NULL COMMENT '单价',
    stock           INT            DEFAULT 0 COMMENT '库存',
    usage_dosage    VARCHAR(500)   DEFAULT NULL COMMENT '用法用量',
    contraindication TEXT          DEFAULT NULL COMMENT '禁忌',
    side_effect     TEXT           DEFAULT NULL COMMENT '不良反应',
    status          TINYINT        DEFAULT 1 COMMENT '状态：1-上架 0-下架',
    create_time     DATETIME       DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (drug_name),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品字典表';

-- ============================================================
-- 15. AI对话记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_conversation (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id       VARCHAR(64)   DEFAULT NULL COMMENT '会话ID',
    user_id          BIGINT        NOT NULL COMMENT '用户ID',
    user_type        TINYINT       NOT NULL COMMENT '用户类型：1-患者 2-医生',
    role             VARCHAR(20)   NOT NULL COMMENT '角色：user/assistant/system',
    content          TEXT          NOT NULL COMMENT '内容',
    prompt_tokens    INT           DEFAULT NULL COMMENT '提示tokens',
    completion_tokens INT          DEFAULT NULL COMMENT '补全tokens',
    total_tokens     INT           DEFAULT NULL COMMENT '总tokens',
    latency          BIGINT        DEFAULT NULL COMMENT '耗时(ms)',
    model            VARCHAR(50)   DEFAULT NULL COMMENT '模型名称',
    tools_used       VARCHAR(500)  DEFAULT NULL COMMENT '调用的工具',
    feedback         TINYINT       DEFAULT NULL COMMENT '反馈：1-赞 0-踩',
    create_time      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_user (user_id, user_type),
    INDEX idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话记录表';

-- ============================================================
-- 16. 操作日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS operation_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        DEFAULT NULL COMMENT '用户ID',
    user_type       TINYINT       DEFAULT NULL COMMENT '用户类型',
    username        VARCHAR(50)   DEFAULT NULL COMMENT '用户名',
    module          VARCHAR(50)   DEFAULT NULL COMMENT '操作模块',
    description     VARCHAR(200)  DEFAULT NULL COMMENT '操作描述',
    operation_type  VARCHAR(20)   DEFAULT NULL COMMENT '操作类型',
    method          VARCHAR(200)  DEFAULT NULL COMMENT '请求方法',
    params          TEXT          DEFAULT NULL COMMENT '请求参数',
    result          TEXT          DEFAULT NULL COMMENT '返回结果',
    ip              VARCHAR(50)   DEFAULT NULL COMMENT '操作IP',
    uri             VARCHAR(200)  DEFAULT NULL COMMENT '请求URI',
    cost_time       BIGINT        DEFAULT NULL COMMENT '耗时(ms)',
    status          TINYINT       DEFAULT 0 COMMENT '状态：0-成功 1-失败',
    error_msg       TEXT          DEFAULT NULL COMMENT '错误信息',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_module (module),
    INDEX idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ============================================================
-- 17. 数据字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_dict (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_type   VARCHAR(50)  NOT NULL COMMENT '字典类型',
    dict_label  VARCHAR(100) NOT NULL COMMENT '字典标签',
    dict_value  VARCHAR(100) NOT NULL COMMENT '字典值',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态',
    remark      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_type_value (dict_type, dict_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典表';

-- ============================================================
-- 18. 管理员表
-- ============================================================
CREATE TABLE IF NOT EXISTS admin (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '管理员账号',
    password    VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    name        VARCHAR(50)  NOT NULL COMMENT '姓名',
    role        VARCHAR(20)  DEFAULT 'ADMIN' COMMENT '角色：SUPER_ADMIN/ADMIN/OPERATOR',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status      TINYINT      DEFAULT 1 COMMENT '状态：1-正常 0-禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 默认管理员 admin/123456 (BCrypt加密)
INSERT INTO admin (username, password, name, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '超级管理员', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- 为原有表添加 deleted 字段（逻辑删除）
-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS；
--      若列已存在会报 Duplicate column 错误，忽略该错误即可。
-- ============================================================
ALTER TABLE patient ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE doctor ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE department ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE appointment ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE pay_order ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE refund_order ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除';

-- ============================================================
-- 初始化药品数据
-- ============================================================
INSERT INTO drug (drug_code, drug_name, generic_name, specification, unit, manufacturer, category, price, stock, usage_dosage) VALUES
('DRUG001', '阿莫西林胶囊', '阿莫西林', '0.25g*24粒', '盒', '华北制药', '西药', 15.50, 1000, '口服，一次0.5g，一日3次'),
('DRUG002', '布洛芬缓释胶囊', '布洛芬', '0.3g*20粒', '盒', '中美史克', '西药', 22.00, 800, '口服，一次1粒，一日2次'),
('DRUG003', '感冒灵颗粒', '感冒灵', '10g*9袋', '盒', '三九医药', '中成药', 12.80, 1500, '开水冲服，一次1袋，一日3次'),
('DRUG004', '头孢克肟分散片', '头孢克肟', '0.1g*6片', '盒', '广州白云山', '西药', 35.00, 600, '口服，一次0.1g，一日2次'),
('DRUG005', '奥美拉唑肠溶胶囊', '奥美拉唑', '20mg*14粒', '盒', '阿斯利康', '西药', 45.00, 500, '口服，一次20mg，一日1次')
ON DUPLICATE KEY UPDATE drug_name = VALUES(drug_name);

SELECT '企业版数据库升级完成！' AS message;
