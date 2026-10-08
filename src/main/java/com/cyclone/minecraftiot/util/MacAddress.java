package com.cyclone.minecraftiot.util;

import java.util.Random;

/**
 * 网络设备 MAC 地址工具。
 * 每台网络设备（路由器/AP/执行器/存储器/接口/发电机）出厂时生成一个固定 MAC，
 * 格式 ie:XXXX-XXXX-XXXX（12 位十六进制）。MAC 不可改，别名可在 GUI 配置。
 */
public class MacAddress {

    private static final Random RND = new Random();

    /** 生成一个新的 MAC 地址字符串 */
    public static String generate() {
        byte[] b = new byte[6];
        RND.nextBytes(b);
        // 确保单播（最低位为0）和本地管理（次低位为1），避免与真实厂商 OUI 冲突
        b[0] = (byte) ((b[0] & 0xFE) | 0x02);
        return String.format("ie:%02X%02X-%02X%02X-%02X%02X",
                b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /** 校验 MAC 格式是否合法 */
    public static boolean isValid(String mac) {
        if (mac == null) return false;
        return mac.matches("ie:[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}");
    }
}
