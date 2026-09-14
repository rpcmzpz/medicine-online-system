package com.medicine.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medicine.entity.Inventory;
import com.medicine.entity.Medicine;
import com.medicine.entity.Review;
import com.medicine.entity.User;
import com.medicine.mapper.InventoryMapper;
import com.medicine.mapper.MedicineMapper;
import com.medicine.mapper.ReviewMapper;
import com.medicine.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private MedicineMapper medicineMapper;
    @Autowired
    private InventoryMapper inventoryMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initUser("admin", "admin123", 2, "系统管理员");
        initUser("pharmacist", "pharmacist123", 3, "张药师");
        initUser("delivery", "delivery123", 4, "李配送");
        initMedicines();
        initReviews();
    }

    private void initUser(String username, String password, int userType, String realName) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User existing = userMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setPasswordHash(passwordEncoder.encode(password));
            userMapper.updateById(existing);
        } else {
            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setUserType(userType);
            user.setRealName(realName);
            user.setMembershipLevel(0);
            userMapper.insert(user);
        }
    }

    private void initMedicines() {
        updateMedicine(1L, 6, "布洛芬缓释胶囊", "布洛芬", "中美史克", "0.3g×20粒",
            "国药准字H10900089", 0,
            "用于缓解轻至中度疼痛如头痛、关节痛、偏头痛、牙痛、肌肉痛、神经痛、痛经。也用于普通感冒或流行性感冒引起的发热。",
            new BigDecimal("18.50"), new BigDecimal("25.00"), 500, 20);

        updateMedicine(2L, 7, "复方甘草片", "复方甘草", "国药集团", "100片/瓶",
            "国药准字H44023176", 0,
            "用于镇咳祛痰。",
            new BigDecimal("8.80"), new BigDecimal("12.00"), 300, 15);

        updateMedicine(3L, 8, "硝苯地平控释片", "硝苯地平", "拜耳医药", "30mg×7片",
            "国药准字J20180025", 1,
            "用于治疗高血压、冠心病、慢性稳定型心绞痛。",
            new BigDecimal("32.00"), new BigDecimal("38.00"), 200, 10);

        updateMedicine(4L, 9, "盐酸二甲双胍片", "二甲双胍", "中美上海施贵宝", "0.5g×20片",
            "国药准字H20023370", 1,
            "用于单纯饮食控制不满意的2型糖尿病病人，尤其是肥胖和伴高胰岛素血症者。",
            new BigDecimal("22.00"), new BigDecimal("28.00"), 350, 15);

        updateMedicine(5L, 6, "对乙酰氨基酚片", "对乙酰氨基酚", "强生制药", "0.5g×10片",
            "国药准字H12020278", 0,
            "用于普通感冒或流行性感冒引起的发热，也用于缓解轻至中度疼痛。",
            new BigDecimal("6.50"), new BigDecimal("9.00"), 400, 20);

        updateMedicine(6L, 3, "阿莫西林胶囊", "阿莫西林", "联邦制药", "0.5g×24粒",
            "国药准字H20003263", 1,
            "适用于敏感菌所致的各种感染，包括呼吸道感染、泌尿生殖道感染、皮肤软组织感染等。",
            new BigDecimal("15.80"), new BigDecimal("22.00"), 150, 10);

        updateMedicine(7L, 4, "奥美拉唑肠溶胶囊", "奥美拉唑", "阿斯利康", "20mg×14粒",
            "国药准字H20030413", 0,
            "用于胃酸过多引起的烧心和反酸症状的短期缓解。",
            new BigDecimal("28.00"), new BigDecimal("35.00"), 250, 15);

        updateMedicine(8L, 5, "云南白药气雾剂", "云南白药", "云南白药集团", "85g+30g",
            "国药准字Z53021108", 0,
            "活血散瘀，消肿止痛。用于跌打损伤，瘀血肿痛，肌肉酸痛及风湿疼痛。",
            new BigDecimal("35.00"), new BigDecimal("42.00"), 180, 10);
    }

    private void updateMedicine(Long id, int catId, String name, String genericName,
            String brand, String spec, String approval, int drugType,
            String desc, BigDecimal price, BigDecimal origPrice, int stock, int alert) {
        Medicine m = medicineMapper.selectById(id);
        boolean exists = (m != null);
        if (!exists) {
            m = new Medicine();
            m.setMedicineId(id);
            m.setCategoryId(catId);
            m.setStatus(1);
            m.setSalesCount(0);
        }
        m.setName(name);
        m.setGenericName(genericName);
        m.setBrand(brand);
        m.setSpecification(spec);
        m.setApprovalNumber(approval);
        m.setDrugType(drugType);
        m.setDescription(desc);
        m.setPrice(price);
        m.setOriginalPrice(origPrice);
        if (exists) {
            medicineMapper.updateById(m);
        } else {
            medicineMapper.insert(m);
        }

        LambdaQueryWrapper<Inventory> iw = new LambdaQueryWrapper<>();
        iw.eq(Inventory::getMedicineId, id);
        Inventory inv = inventoryMapper.selectOne(iw);
        if (inv == null) {
            inv = new Inventory();
            inv.setMedicineId(id);
            inv.setStockQuantity(stock);
            inv.setAlertThreshold(alert);
            inv.setLockedQuantity(0);
            inventoryMapper.insert(inv);
        }
    }

    private void initReviews() {
        Long count = reviewMapper.selectCount(null);
        if (count > 0) return;

        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        if (admin == null) return;

        Long userId = admin.getUserId();
        Review r;

        r = new Review(); r.setUserId(userId); r.setMedicineId(1L); r.setRating(5); r.setContent("效果很好，退烧快，家庭常备药"); reviewMapper.insert(r);
        r = new Review(); r.setUserId(userId); r.setMedicineId(1L); r.setRating(4); r.setContent("价格实惠，比药店便宜多了"); reviewMapper.insert(r);
        r = new Review(); r.setUserId(userId); r.setMedicineId(3L); r.setRating(5); r.setContent("降压效果好，一直在用这个牌子"); reviewMapper.insert(r);
        r = new Review(); r.setUserId(userId); r.setMedicineId(4L); r.setRating(5); r.setContent("降糖效果显著，副作用小，推荐"); reviewMapper.insert(r);
        r = new Review(); r.setUserId(userId); r.setMedicineId(6L); r.setRating(4); r.setContent("吃了一周好转了，消炎效果不错"); reviewMapper.insert(r);
        r = new Review(); r.setUserId(userId); r.setMedicineId(7L); r.setRating(5); r.setContent("胃不舒服吃一粒就好，家中常备"); reviewMapper.insert(r);
        r = new Review(); r.setUserId(userId); r.setMedicineId(8L); r.setRating(4); r.setContent("扭伤后喷了两天就不疼了，好评"); reviewMapper.insert(r);
        r = new Review(); r.setUserId(userId); r.setMedicineId(2L); r.setRating(4); r.setContent("止咳效果不错，甘草味道能接受"); reviewMapper.insert(r);
        r = new Review(); r.setUserId(userId); r.setMedicineId(5L); r.setRating(3); r.setContent("退烧还可以，就是空腹吃有点伤胃"); reviewMapper.insert(r);
    }
}
