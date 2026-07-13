package org.ruoyi.service.paper;

import org.ruoyi.common.core.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 表名核心词 → 中文模块名（与 {@link SqlParserService} 推断逻辑一致）。
 */
final class PaperModuleDictionary {

    private static final Map<String, String> MODULE_DICT = buildModuleDict();

    private PaperModuleDictionary() {
    }

    static String inferModuleName(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return null;
        }
        String core = stripPrefix(tableName.toLowerCase(Locale.ROOT));
        if (MODULE_DICT.containsKey(core)) {
            return MODULE_DICT.get(core);
        }
        for (Map.Entry<String, String> entry : MODULE_DICT.entrySet()) {
            if (core.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String stripPrefix(String table) {
        String[] prefixes = {"sys_", "tb_", "t_", "biz_", "b_", "base_", "ums_", "pms_", "oms_", "wms_"};
        for (String prefix : prefixes) {
            if (table.startsWith(prefix)) {
                return table.substring(prefix.length());
            }
        }
        return table;
    }

    private static Map<String, String> buildModuleDict() {
        Map<String, String> dict = new LinkedHashMap<>();
        put(dict, "用户管理", "user", "users", "member", "members", "account", "accounts", "customer", "customers");
        put(dict, "管理员管理", "admin", "admins", "manager");
        put(dict, "角色管理", "role", "roles");
        put(dict, "权限管理", "permission", "permissions", "perm", "perms", "auth");
        put(dict, "菜单管理", "menu", "menus");
        put(dict, "部门管理", "dept", "department", "departments");
        put(dict, "员工管理", "employee", "employees", "staff", "worker");
        put(dict, "订单管理", "order", "orders");
        put(dict, "商品管理", "goods", "product", "products", "item", "items", "commodity", "spu", "sku");
        put(dict, "分类管理", "category", "categories", "cate", "classify", "type", "types");
        put(dict, "购物车管理", "cart", "carts", "shopcart");
        put(dict, "支付管理", "pay", "payment", "payments", "payorder", "bill", "bills");
        put(dict, "库存管理", "stock", "inventory", "warehouse", "repository");
        put(dict, "图书管理", "book", "books");
        put(dict, "借阅管理", "borrow", "borrows", "lend", "loan");
        put(dict, "骑行管理", "riding", "ride", "rides", "route", "routes", "bike", "bikes", "bicycle");
        put(dict, "学生管理", "student", "students");
        put(dict, "教师管理", "teacher", "teachers");
        put(dict, "课程管理", "course", "courses");
        put(dict, "班级管理", "clazz", "classes", "grade");
        put(dict, "成绩管理", "score", "scores", "result", "achievement");
        put(dict, "选课管理", "selection", "elective", "choose");
        put(dict, "文章管理", "article", "articles", "news", "post", "posts", "blog", "blogs");
        put(dict, "评论管理", "comment", "comments", "reply", "replies");
        put(dict, "收藏管理", "favorite", "favorites", "collection", "collect");
        put(dict, "消息管理", "message", "messages", "msg");
        put(dict, "通知管理", "notice", "notices", "notification", "notify");
        put(dict, "资讯管理", "information", "info", "cms");
        put(dict, "日志管理", "log", "logs");
        put(dict, "文件管理", "file", "files", "attachment", "upload");
        put(dict, "地址管理", "address", "addresses");
        put(dict, "供应商管理", "supplier", "suppliers", "vendor");
        put(dict, "客户管理", "client", "clients");
        put(dict, "考勤管理", "attendance", "checkin", "clock");
        put(dict, "薪资管理", "salary", "salaries", "wage");
        put(dict, "房间管理", "room", "rooms", "hotel");
        put(dict, "车辆管理", "car", "cars", "vehicle", "vehicles");
        put(dict, "票务管理", "ticket", "tickets");
        put(dict, "预约管理", "appointment", "reservations", "booking", "bookings");
        put(dict, "座位预约管理", "seat", "seats", "seat_reservation");
        put(dict, "医生管理", "doctor", "doctors");
        put(dict, "患者管理", "patient", "patients");
        put(dict, "活动管理", "activity", "activities", "event", "events");
        put(dict, "轮播图管理", "banner", "banners", "carousel", "slide");
        put(dict, "钱包管理", "wallet", "wallets");
        put(dict, "反馈管理", "feedback", "feedbacks", "suggestion", "suggestions", "complaint");
        put(dict, "首页管理", "home", "homepage", "home_page", "home_page_config", "index_page");
        put(dict, "个人中心", "profile", "profiles", "my_profile");
        return dict;
    }

    private static void put(Map<String, String> dict, String module, String... keys) {
        for (String key : keys) {
            dict.put(key, module);
        }
    }
}
