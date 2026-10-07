package com.fz.scitech.platform.template;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 人民币大写工具(元 → 壹贰叁…整)。
 * 上限 99999999.99(壹亿元以内,高校横向合同足够;超限/负数抛 IllegalArgumentException);
 * 规则:数字→汉字映射,四位一组千百十个,组间万;跨组不足千补零;有元无角有分补零。
 */
public final class RmbCapitalUtil {

    private static final char[] DIGITS = {'零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'};
    private static final String[] UNITS = {"仟", "佰", "拾", ""};
    private static final BigDecimal MAX = new BigDecimal("99999999.99");

    private RmbCapitalUtil() {
    }

    public static String toCapital(BigDecimal yuan) {
        if (yuan == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        if (yuan.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负:" + yuan);
        }
        if (yuan.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("金额超上限(壹亿元):" + yuan);
        }
        long cents = yuan.multiply(new BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (cents == 0) {
            return "零元整";
        }
        long yuanPart = cents / 100;
        int jiao = (int) (cents % 100 / 10);
        int fen = (int) (cents % 10);

        StringBuilder sb = new StringBuilder();
        if (yuanPart > 0) {
            sb.append(integerToCapital(yuanPart)).append("元");
        }
        if (jiao == 0 && fen == 0) {
            sb.append("整");
        } else {
            if (jiao > 0) {
                sb.append(DIGITS[jiao]).append("角");
            } else if (yuanPart > 0 && fen > 0) {
                sb.append("零"); // 有元无角有分:如 100.05 → 壹佰元零伍分
            }
            if (fen > 0) {
                sb.append(DIGITS[fen]).append("分");
            }
        }
        return sb.toString();
    }

    /** 整数部分(1..99999999):万组+个组拼接,跨组不足千补零(如 10005 → 壹万零伍) */
    private static String integerToCapital(long v) {
        int wan = (int) (v / 10000);
        int rest = (int) (v % 10000);
        StringBuilder b = new StringBuilder();
        if (wan > 0) {
            b.append(fourDigits(wan)).append("万");
        }
        if (rest > 0) {
            if (wan > 0 && rest < 1000) {
                b.append("零");
            }
            b.append(fourDigits(rest));
        }
        return b.toString();
    }

    /** 四位数(1..9999):千百十个,中间连续零合并为一个,尾零不输出 */
    private static String fourDigits(int n) {
        StringBuilder b = new StringBuilder();
        boolean zeroPending = false;
        boolean started = false;
        for (int i = 0; i < 4; i++) {
            int d = (n / POW10[i]) % 10;
            if (d == 0) {
                zeroPending = true;
            } else {
                if (zeroPending && started) {
                    b.append('零');
                }
                b.append(DIGITS[d]).append(UNITS[i]);
                zeroPending = false;
                started = true;
            }
        }
        return b.toString();
    }

    private static final int[] POW10 = {1000, 100, 10, 1};
}
