package com.kaizen.tool;

import android.content.Context;

/**
 * Theme Ram / Rem — Android glue (SharedPreferences + R.*).
 * Mọi mapping tên/màu sống ở {@link CharacterThemeSpec} (pure Java,
 * test được trên host); class này chỉ đọc pref + chọn resource.
 *
 * Ram = hồng (mặc định), Rem = xanh dương nhạt.
 * Lưu trong SharedPreferences "kaizen_settings", key "character".
 */
public final class CharacterTheme {

    private static final String KEY_CHARACTER = "character"; // "ram" | "rem"

    private CharacterTheme() {}

    public static String current(Context ctx) {
        return CharacterThemeSpec.normalize(
                AppSettings.prefs(ctx).getString(KEY_CHARACTER, CharacterThemeSpec.RAM));
    }

    public static boolean isRem(Context ctx) {
        return CharacterThemeSpec.isRem(current(ctx));
    }

    public static void setCharacter(Context ctx, String character) {
        AppSettings.prefs(ctx).edit()
                .putString(KEY_CHARACTER, CharacterThemeSpec.normalize(character))
                .apply();
    }

    // ---- theme (chọn trước super.onCreate, thay setTheme(R.style.AppTheme)) ----
    public static int appTheme(Context ctx) {
        return isRem(ctx) ? R.style.AppTheme_Rem : R.style.AppTheme;
    }

    public static int dialogTheme(Context ctx) {
        return isRem(ctx) ? R.style.DialogTheme_Rem : R.style.DialogTheme;
    }

    public static int bottomSheetTheme(Context ctx) {
        return isRem(ctx) ? R.style.BottomSheetDialogTheme_Rem
                          : R.style.BottomSheetDialogTheme;
    }

    // ---- ảnh theo theme ----
    public static int bgRes(Context ctx) {
        return isRem(ctx) ? R.drawable.bg_rem_blue : R.drawable.bg_ram;
    }

    public static int folderIconRes(Context ctx) {
        return isRem(ctx) ? R.drawable.ic_folder_rem_blue : R.drawable.ic_folder_ram;
    }

    public static int dialogChibiRes(Context ctx) {
        return isRem(ctx) ? R.drawable.rem_dialog : R.drawable.ram_dialog;
    }

    public static int popupBgRes(Context ctx) {
        return isRem(ctx) ? R.drawable.popup_bg_rem : R.drawable.popup_bg_ram;
    }

    // ---- màu nền panel/divider theo theme (night-aware qua values-night) ----
    public static int selectedBackgroundRes(Context ctx) {
        return isRem(ctx) ? R.color.selectedBackgroundRem : R.color.selectedBackground;
    }

    public static int pathBarBackgroundRes(Context ctx) {
        return isRem(ctx) ? R.color.pathBarBackgroundRem : R.color.pathBarBackground;
    }

    // ---- màu chủ đạo (int ARGB, dùng khi cần set màu bằng code) ----
    public static int primaryColor(Context ctx) {
        return CharacterThemeSpec.primaryColor(current(ctx));
    }

    public static int primaryDarkColor(Context ctx) {
        return CharacterThemeSpec.primaryDarkColor(current(ctx));
    }

    public static int accentColor(Context ctx) {
        return CharacterThemeSpec.accentColor(current(ctx));
    }
}
