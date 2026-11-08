package com.kaizen.tool;

/**
 * Theme Ram / Rem — pure mapping logic, NO Android imports so it can be
 * unit-tested on the host JVM (see /tmp/kz-theme/CharacterThemeSpecTest).
 *
 * Ram = hồng (mặc định), Rem = xanh dương nhạt.
 * Mọi ảnh + màu chủ đạo đều resolve qua đây; {@link CharacterTheme} chỉ
 * là lớp glue Android (SharedPreferences + R.drawable.*).
 */
public final class CharacterThemeSpec {

    public static final String RAM = "ram";
    public static final String REM = "rem";

    private CharacterThemeSpec() {}

    /** Null / lạ / viết hoa -> "ram" (safe default). */
    public static String normalize(String character) {
        if (character == null) return RAM;
        String c = character.trim().toLowerCase();
        return REM.equals(c) ? REM : RAM;
    }

    public static boolean isRem(String character) {
        return REM.equals(normalize(character));
    }

    // ---- drawable names (không có đuôi, resolve sang R.drawable.* ở glue) ----
    public static String bgResName(String character) {
        return isRem(character) ? "bg_rem_blue" : "bg_ram";
    }

    public static String folderIconResName(String character) {
        return isRem(character) ? "ic_folder_rem_blue" : "ic_folder_ram";
    }

    public static String dialogChibiResName(String character) {
        return isRem(character) ? "rem_dialog" : "ram_dialog";
    }

    public static String popupBgResName(String character) {
        return isRem(character) ? "popup_bg_rem" : "popup_bg_ram";
    }

    // ---- màu chủ đạo (ARGB) ----
    public static int primaryColor(String character) {
        return isRem(character) ? 0xFF79AEE8 : 0xFFF06292;
    }

    public static int primaryDarkColor(String character) {
        return isRem(character) ? 0xFF5B93D6 : 0xFFC2185B;
    }

    public static int accentColor(String character) {
        return isRem(character) ? 0xFFA9CDF0 : 0xFFFF80AB;
    }

    /** Màu khi bấm (pressed) trong popup đường dẫn — Ram hồng, Rem xanh. */
    public static int selectionPressedColor(String character, boolean night) {
        if (isRem(character)) return night ? 0xFF3E5064 : 0xFFBBD6F0;
        return night ? 0xFF5C3A44 : 0xFFF8BBD0;
    }

    /** Nền + viền dialog tai mèo (CatPopup) — Ram hồng trắng, Rem xanh trắng. */
    public static int dialogBodyColor(String character) {
        return isRem(character) ? 0xFFF0F7FD : 0xFFFFF3F7;
    }

    public static int dialogStrokeColor(String character) {
        return isRem(character) ? 0xFFA9CDF0 : 0xFFE8A0B8;
    }
}
