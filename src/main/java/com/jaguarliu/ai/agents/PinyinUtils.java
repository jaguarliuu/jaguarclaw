package com.jaguarliu.ai.agents;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 中文转拼音工具，用于从 displayName 生成合法的 workspace 目录名。
 *
 * <p>转换规则：
 * <ul>
 *   <li>中文字符 → 拼音（无声调，小写）</li>
 *   <li>英文字母 / 数字 → 原样保留（小写）</li>
 *   <li>其他字符（空格、标点等）→ 连字符 {@code -}</li>
 *   <li>连续连字符合并为一个</li>
 *   <li>首尾连字符去除</li>
 *   <li>超过 50 字符时截断</li>
 *   <li>结果为空时返回 {@code "agent"}</li>
 * </ul>
 */
public final class PinyinUtils {

    private static final HanyuPinyinOutputFormat FORMAT;

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinUtils() {
    }

    /**
     * 将任意 displayName 转换为适合文件系统的 slug（仅含 a-z, 0-9, -）。
     */
    public static String toSlug(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "agent";
        }

        StringBuilder sb = new StringBuilder();
        boolean lastWasLatin = false;
        boolean lastWasChinese = false;

        for (char c : displayName.toCharArray()) {
            if (isChinese(c)) {
                if (lastWasLatin) {
                    sb.append('-');
                }
                try {
                    String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
                    if (pinyins != null && pinyins.length > 0) {
                        sb.append(pinyins[0]);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                    sb.append('-');
                }
                lastWasChinese = true;
                lastWasLatin = false;
            } else if (Character.isLetterOrDigit(c)) {
                if (lastWasChinese) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
                lastWasLatin = true;
                lastWasChinese = false;
            } else {
                sb.append('-');
                lastWasLatin = false;
                lastWasChinese = false;
            }
        }

        String slug = sb.toString()
                .replaceAll("-+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("-+$", "");
        }

        return slug.isEmpty() ? "agent" : slug;
    }

    private static boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fa5';
    }
}
