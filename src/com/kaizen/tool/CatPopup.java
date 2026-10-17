package com.kaizen.tool;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Popup tai mèo: tách tai ra làm overlay, thân dialog giữ nền bo tròn thường.
 * Fix lỗi ảnh vuông bị kéo giãn vỡ layout.
 *
 * - Window trong suốt
 * - Root content: nền hồng nhạt bo tròn (scale tốt mọi kích thước)
 * - Tai mèo (cat_ears.png): ImageView đè lên trên, căn giữa
 */
public class CatPopup {

    private static final String TAG = "cat_popup_applied";

    public static void apply(Dialog dialog) {
        if (dialog == null) return;
        Window w = dialog.getWindow();
        if (w == null) return;
        View decor = w.getDecorView();
        if (decor == null || TAG.equals(decor.getTag())) return;
        decor.setTag(TAG);

        w.setBackgroundDrawableResource(android.R.color.transparent);

        // Thu hẹp bề ngang dialog còn 88% (vừa phải, không chật dialog nén)
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        android.util.DisplayMetrics dm = dialog.getContext().getResources().getDisplayMetrics();
        int defaultWidth = lp.width;
        // Chỉ thu hẹp nếu dialog đang full/match_parent width
        if (defaultWidth < 0) {
            lp.width = (int) (dm.widthPixels * 0.88);
            w.setAttributes(lp);
        }

        View content = w.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup contentGroup = (ViewGroup) content;
        if (contentGroup.getChildCount() == 0) return;

        float d = dialog.getContext().getResources().getDisplayMetrics().density;

        // Lấy root view hiện tại ra
        View oldRoot = contentGroup.getChildAt(0);
        contentGroup.removeView(oldRoot);

        // Nền bo tròn hồng nhạt cho thân dialog
        GradientDrawable bodyBg = new GradientDrawable();
        bodyBg.setColor(0xFFFFF3F7);
        bodyBg.setCornerRadius(24 * d);
        bodyBg.setStroke((int) (2 * d), 0xFFE8A0B8);
        oldRoot.setBackground(bodyBg);
        // Padding để chữ không tràn ra ngoài
        int hPad = (int) (8 * d);
        oldRoot.setPadding(
                oldRoot.getPaddingLeft() + hPad,
                oldRoot.getPaddingTop(),
                oldRoot.getPaddingRight() + hPad,
                oldRoot.getPaddingBottom());

        // FrameLayout bọc: tai mèo đội lên trên, chỉ hơi đè xuống thân
        FrameLayout wrapper = new FrameLayout(dialog.getContext());

        // Tai cao tối đa 64dp, thân bắt đầu từ 52dp -> tai chỉ đè 12dp lên thân
        int earSpace = (int) (52 * d);
        FrameLayout.LayoutParams bodyLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = earSpace;
        wrapper.addView(oldRoot, bodyLp);

        // Tai mèo: chiều rộng match parent, cao tự động theo tỉ lệ
        try {
            ImageView ears = new ImageView(dialog.getContext());
            ears.setImageResource(R.drawable.cat_ears);
            ears.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ears.setAdjustViewBounds(true);
            FrameLayout.LayoutParams earLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            earLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            // Giới hạn chiều cao tối đa để tai không quá to
            earLp.height = (int) (64 * d);
            wrapper.addView(ears, earLp);
        } catch (Exception e) {
            // không có ảnh tai thì thôi
        }

        contentGroup.addView(wrapper, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Fix dark mode: nền dialog luôn hồng nhạt -> ép chữ sang màu tối
        // để đọc được (tránh chữ trắng trên nền sáng)
        forceDarkText(oldRoot);
    }

    /**
     * Duyệt view hierarchy, đặt màu chữ tối cho mọi TextView
     * (trừ nút bấm đã có màu riêng).
     */
    private static void forceDarkText(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                forceDarkText(vg.getChildAt(i));
            }
        } else if (v instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) v;
            // Bỏ qua nút (Button) — giữ màu hồng
            if (!(v instanceof android.widget.Button)) {
                tv.setTextColor(0xFF333333);
            }
            // EditText: chữ tối, hint xám
            if (v instanceof android.widget.EditText) {
                tv.setTextColor(0xFF333333);
                ((android.widget.EditText) v).setHintTextColor(0xFF999999);
            }
        }
    }
}
