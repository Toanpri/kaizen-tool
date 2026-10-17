package com.kaizen.tool;

import android.app.AlertDialog;
import android.content.Context;

/**
 * AlertDialog.Builder tự áp nền mèo (CatPopup) mỗi khi show().
 * Dùng thay {@code new AlertDialog.Builder(...)} cho mọi dialog —
 * TRỪ dialog tiến trình (dialog_progress.xml giữ nguyên ảnh Rem).
 */
public class CatBuilder extends AlertDialog.Builder {

    public CatBuilder(Context context, int themeResId) {
        super(context, themeResId);
    }

    @Override
    public AlertDialog show() {
        AlertDialog d = super.show();
        CatPopup.apply(d);
        return d;
    }
}
