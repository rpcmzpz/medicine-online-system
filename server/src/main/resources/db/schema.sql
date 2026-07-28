CREATE DATABASE IF NOT EXISTS medicine_online DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE medicine_online;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
  user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  user_type TINYINT NOT NULL COMMENT '0-普通用户 1-慢病用户 2-管理员 3-药师 4-配送员',
  real_name VARCHAR(50),
  age INT,
  phone VARCHAR(20),
  email VARCHAR(100),
  avatar_url VARCHAR(255),
  membership_level TINYINT DEFAULT 0 COMMENT '0-普通 1-银卡 2-金卡 3-钻石',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收货地址表
CREATE TABLE IF NOT EXISTS addresses (
  address_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  receiver_name VARCHAR(50) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  province VARCHAR(30) NOT NULL,
  city VARCHAR(30) NOT NULL,
  district VARCHAR(30) NOT NULL,
  detail VARCHAR(255) NOT NULL,
  is_default TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 药品分类表
CREATE TABLE IF NOT EXISTS categories (
  category_id INT AUTO_INCREMENT PRIMARY KEY,
  parent_id INT,
  name VARCHAR(50) NOT NULL,
  sort_order INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (parent_id) REFERENCES categories(category_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 药品表
CREATE TABLE IF NOT EXISTS medicines (
  medicine_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_id INT,
  name VARCHAR(200) NOT NULL,
  generic_name VARCHAR(200),
  brand VARCHAR(100),
  specification VARCHAR(100),
  approval_number VARCHAR(50),
  drug_type TINYINT NOT NULL COMMENT '0-OTC 1-处方药',
  description TEXT,
  image_url VARCHAR(255),
  price DECIMAL(10,2) NOT NULL,
  original_price DECIMAL(10,2),
  status TINYINT DEFAULT 1 COMMENT '0-下架 1-上架',
  sales_count INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 库存表
CREATE TABLE IF NOT EXISTS inventory (
  inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  medicine_id BIGINT NOT NULL UNIQUE,
  stock_quantity INT NOT NULL DEFAULT 0,
  alert_threshold INT DEFAULT 10,
  locked_quantity INT DEFAULT 0,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 药品评价表
CREATE TABLE IF NOT EXISTS reviews (
  review_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  medicine_id BIGINT NOT NULL,
  order_id BIGINT,
  rating TINYINT NOT NULL COMMENT '1-5',
  content TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id),
  FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 购物车表
CREATE TABLE IF NOT EXISTS cart_items (
  cart_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  medicine_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
  order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  address_id BIGINT,
  total_amount DECIMAL(10,2) NOT NULL,
  discount_amount DECIMAL(10,2) DEFAULT 0.00,
  actual_amount DECIMAL(10,2) NOT NULL,
  payment_method TINYINT COMMENT '0-微信 1-支付宝 2-银联',
  order_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-待支付 1-待审核 2-待配药 3-待配送 4-配送中 5-已完成 6-已取消 7-已退款',
  has_prescription TINYINT DEFAULT 0,
  remark VARCHAR(500),
  version INT DEFAULT 0 COMMENT '乐观锁',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  paid_at DATETIME,
  completed_at DATETIME,
  FOREIGN KEY (user_id) REFERENCES users(user_id),
  FOREIGN KEY (address_id) REFERENCES addresses(address_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单明细表
CREATE TABLE IF NOT EXISTS order_items (
  item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  medicine_id BIGINT NOT NULL,
  medicine_name VARCHAR(200) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  subtotal DECIMAL(10,2) NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
  FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 支付记录表
CREATE TABLE IF NOT EXISTS payments (
  payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  transaction_no VARCHAR(64) UNIQUE,
  amount DECIMAL(10,2) NOT NULL,
  payment_method TINYINT NOT NULL,
  payment_status TINYINT NOT NULL COMMENT '0-处理中 1-成功 2-失败 3-已退款',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (order_id) REFERENCES orders(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 处方表
CREATE TABLE IF NOT EXISTS prescriptions (
  prescription_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT,
  user_id BIGINT NOT NULL,
  pharmacist_id BIGINT,
  image_urls TEXT NOT NULL,
  review_status TINYINT DEFAULT 0 COMMENT '0-待审核 1-审核通过 2-审核驳回',
  review_remark VARCHAR(500),
  reviewed_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (order_id) REFERENCES orders(order_id),
  FOREIGN KEY (user_id) REFERENCES users(user_id),
  FOREIGN KEY (pharmacist_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 配送表
CREATE TABLE IF NOT EXISTS deliveries (
  delivery_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL UNIQUE,
  delivery_person_id BIGINT,
  delivery_status TINYINT DEFAULT 0 COMMENT '0-待接单 1-已接单 2-配送中 3-已送达',
  pickup_time DATETIME,
  delivered_time DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (order_id) REFERENCES orders(order_id),
  FOREIGN KEY (delivery_person_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 咨询记录表
CREATE TABLE IF NOT EXISTS consultations (
  consultation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  pharmacist_id BIGINT,
  question TEXT NOT NULL,
  answer TEXT,
  status TINYINT DEFAULT 0 COMMENT '0-待回复 1-已回复 2-已关闭',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  answered_at DATETIME,
  FOREIGN KEY (user_id) REFERENCES users(user_id),
  FOREIGN KEY (pharmacist_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用药提醒表
CREATE TABLE IF NOT EXISTS medication_reminders (
  reminder_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  medicine_name VARCHAR(200) NOT NULL,
  dosage VARCHAR(100) NOT NULL,
  frequency VARCHAR(50) NOT NULL,
  remind_times VARCHAR(500) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  is_active TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================
-- 线上购药系统 — 数据库初始化脚本
-- 仅包含建表语句和分类初始数据
-- 用户/药品/库存/评价由 DataInitializer 初始化
-- ============================================

-- 初始分类数据
INSERT INTO categories (category_id, parent_id, name, sort_order) VALUES
(1, NULL, '感冒用药', 1),
(2, NULL, '慢性病用药', 2),
(3, NULL, '抗生素类', 3),
(4, NULL, '消化系统', 4),
(5, NULL, '外用药品', 5),
(6, 1, '解热镇痛', 1),
(7, 1, '止咳化痰', 2),
(8, 2, '降压药', 1),
(9, 2, '降糖药', 2);

-- ============================================
-- 业务索引（MySQL 自动为外键建索引，以下为额外需要的索引）
-- ============================================

-- 管理员按状态筛选订单（高频查询）
CREATE INDEX idx_orders_status ON orders(order_status);

-- 药师审核队列：按审核状态查处方
CREATE INDEX idx_prescriptions_review_status ON prescriptions(review_status);

-- 药师待回复咨询
CREATE INDEX idx_consultations_status ON consultations(status);

-- 药品列表：分类+上架状态联合查询
CREATE INDEX idx_medicines_category_status ON medicines(category_id, status);
