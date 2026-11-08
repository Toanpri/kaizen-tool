package com.kaizen.tool;

import android.app.AlertDialog;
import android.content.Context;

/**
 * AlertDialog.Builder tự áp nền mèo (CatPopup) mỗi khi show().
 * Dùng thay {@code new AlertDialog.Builder(...)} cho mọi dialog —
 * theme dialog (DialogTheme/DialogTheme_Rem) tự đổi theo nhân vật Ram/Rem,
 * dialog tiến trình (dialog_progress.xml) tự đổi ảnh theo theme.
 */
public class CatBuilder extends AlertDialog.Builder {

    public CatBuilder(Context context, int themeResId) {
        // Theme dialog theo nhân vật Ram/Rem: mọi site truyền R.style.DialogTheme
        // đều tự đổi sang DialogTheme_Rem khi đang ở theme Rem.
        super(context, themeResId == R.style.DialogTheme
                ? CharacterTheme.dialogTheme(context) : themeResId);
    }

    @Override
    public AlertDialog show() {
        AlertDialog d = super.show();
        CatPopup.apply(d);
        return d;
    }
}
