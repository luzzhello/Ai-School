package org.ruoyi.common.core.utils;

import cn.hutool.core.util.RandomUtil;

/**
 * 用户邀请码生成工具
 */
public final class InviteCodeUtils {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private InviteCodeUtils() {
    }

    /**
     * 根据用户 ID 生成 6 位邀请码（同一用户始终相同）
     */
    public static String generateForUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return RandomUtil.randomString(CHARS, 6);
        }
        String hex = Long.toHexString(userId).toUpperCase();
        if (hex.length() >= 6) {
            return hex.substring(hex.length() - 6);
        }
        return hex + RandomUtil.randomString(CHARS, 6 - hex.length());
    }
}
