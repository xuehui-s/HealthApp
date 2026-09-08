-- ============================================================
-- 检查并初始化科室数据
-- ============================================================

USE healthapp2;

-- 检查科室表是否存在
SELECT COUNT(*) AS table_exists 
FROM information_schema.tables 
WHERE table_schema = 'healthapp2' 
AND table_name = 'department';

-- 检查是否有科室数据
SELECT COUNT(*) AS department_count FROM department;

-- 如果没有数据，插入初始科室数据
INSERT INTO department (name, description, status) VALUES
('内科', '诊治内科疾病，包括呼吸、消化、心血管等', 1),
('外科', '诊治外科疾病，包括普外、骨科、泌尿等', 1),
('儿科', '诊治儿童疾病', 1),
('妇产科', '妇科疾病诊治与产科保健', 1),
('眼科', '眼部疾病诊治', 1),
('口腔科', '口腔疾病诊治', 1),
('皮肤科', '皮肤疾病诊治', 1),
('中医科', '中医诊疗服务', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 验证数据
SELECT id, name, description, status FROM department ORDER BY id;
