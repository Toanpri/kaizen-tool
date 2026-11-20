package com.kaizen.tool;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Sakuga (Android goc): hang nut cho dialog — thay nut AlertDialog goc
 * (setPositive/Negative/NeutralButton) bang SakugaButton.
 *
 * Cach dung (xem Dialogs.showRename):
 *   LinearLayout content = ...; // layout da co san (EditText, message...)
 *   final SakugaDialogs.Holder holder = SakugaDialogs.holder();
 *   SakugaDialogs.addButtonRow(a, content, holder,
 *       new SakugaDialogs.Action("Lưu", true, new SakugaDialogs.OnAction() {
 *           @Override public boolean run(AlertDialog d) {
 *               ... // tra ve true = thanh cong, false = that bai
 *           }
 *       }),
 *       new SakugaDialogs.Action("Hủy", false, null)); // null = chi dong
 *   AlertDialog dialog = new CatBuilder(a, R.style.DialogTheme)
 *           .setTitle("...").setView(content).show();
 *   holder.dialog = dialog;
 *
 * Hop dong (theo prompt Sakuga: chi goi done() khi hanh dong THAT SU
 * thanh cong):
 * - Bam nut -> burst + ドン -> chay OnAction.run().
 * - run() tra true: dap tick (done()) roi dong dialog sau 320ms.
 * - run() tra false: KHONG dap tick, KHONG dong dialog (giu mo de user
 *   sua lai, vd ten file sai). Day cung la y dinh goc cua cac `return`
 *   trong listener cu — nut AlertDialog goc tu dong dong dialog bat ke
 *   listener tra gi, nen ban cu that ra van dong; ban Sakuga sua dung y.
 * - Nut khong co OnAction (vd "Hủy") luon coi nhu thanh cong -> done + dong.
 *
 * Thu tu nut: nut chinh (primary, hong) dung truoc, nut phu (secondary,
 * giay) dung sau — giong thu tu Positive truoc Negative cua AlertDialog.
 */
public final class SakugaDialogs {

    private SakugaDialogs() {}

    /** 1 nut trong hang nut dialog. */
    public static class Action {
        public final String label;
        public final boolean primary;
        public final OnAction run;
        public Action(String label, boolean primary, OnAction run) {
            this.label = label;
            this.primary = primary;
            this.run = run;
        }
    }

    /**
     * Chay khi bam nut. Tra ve true = hanh dong thanh cong
     * (dap tick + dong dialog); false = that bai (giu dialog mo).
     */
    public interface OnAction {
        boolean run(AlertDialog dialog);
    }

    /** Holder cho AlertDialog — dialog chi co sau khi show(). */
    public static class Holder {
        public AlertDialog dialog;
    }

    public static Holder holder() {
        return new Holder();
    }

    /**
     * Them 1 hang nut Sakuga (can phai) vao cuoi layout. Nut doc dialog
     * tu holder luc bam (luc do holder.dialog da duoc gan sau show()).
     */
    public static LinearLayout addButtonRow(Activity a, LinearLayout layout,
                                            final Holder holder, Action... actions) {
        // test8 (feedback Kazoi 20/11: dialog ZIP 'AI 2028 NEW.zip' nut Huy
        // cung bi cat nhu dialog APK): sua dut diem o day — neu layout
        // chua co padding ngang (vd content trong cua dialog ZIP, APK)
        // thi them 16dp de hang nut khong bao gio cham me phai dialog.
        // Layout da co padding (messageLayout 16dp, dialog_compress.xml
        // 20dp) thi giu nguyen, khong doi.
        if (layout.getPaddingLeft() == 0 && layout.getPaddingRight() == 0) {
            int hPad = SakugaFx.dp(a, 16);
            layout.setPadding(hPad, layout.getPaddingTop(),
                    hPad, layout.getPaddingBottom());
        }
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, SakugaFx.dp(a, 12), 0, 0);
        // test6 (feedback Kazoi: nut Huy bi cat o me phai dialog APK):
        // hang nut rong full dialog; tu 2 nut tro len thi chia deu bang
        // weight de khong bao gio tran me phai (nut WRAP_CONTENT tong
        // rong hon dialog thi nut cuoi bi cat). 1 nut giu WRAP_CONTENT.
        // test7: BO weight — anh bao nut chia deu 'dai qua', khong thich
        // nut bi keo dai. Ve WRAP_CONTENT (rong tu nhien) + thu hep nut
        // compact + them padding ngang cho content (apkContent) de nut
        // khong cham me phai dialog.
        for (int i = 0; i < actions.length; i++) {
            final Action act = actions[i];
            final SakugaButton btn = new SakugaButton(a);
            btn.setText(act.label);
            // test5: nut dialog dung compact (nho gon, 3 nut vua 1 hang).
            btn.setCompact(true);
            btn.setVariant(act.primary
                    ? SakugaButton.VARIANT_PRIMARY
                    : SakugaButton.VARIANT_SECONDARY);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean ok = true;
                    if (act.run != null) {
                        try {
                            ok = act.run.run(holder.dialog);
                        } catch (Exception e) {
                            ok = false;
                        }
                    }
                    if (!ok) return; // that bai: khong dap tick, khong dong
                    btn.done();
                    v.postDelayed(new Runnable() {
                        @Override public void run() {
                            if (holder.dialog != null) holder.dialog.dismiss();
                        }
                    }, 320);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = (i == 0) ? 0 : SakugaFx.dp(a, 6);
            row.addView(btn, lp);
        }
        layout.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** Tao layout content chuan cho dialog chi co message + nut. */
    public static LinearLayout messageLayout(Activity a, String message) {
        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = SakugaFx.dp(a, 16);
        layout.setPadding(p, p, p, p);
        TextView tv = new TextView(a);
        tv.setText(message);
        tv.setTextSize(15);
        tv.setTextColor(SakugaFx.INK);
        layout.addView(tv);
        return layout;
    }
}
