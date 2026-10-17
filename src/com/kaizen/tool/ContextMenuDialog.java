package com.kaizen.tool;

import android.app.Activity;
import android.app.Dialog;
import android.view.*;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

import java.util.List;

/**
 * M4 — Dialog context menu kiểu ZArchiver: danh sách dòng có icon,
 * hiện khi long-press vào file/folder.
 *
 * Bottom sheet trượt từ dưới lên (thay AlertDialog giữa màn hình).
 */
public class ContextMenuDialog {

    public interface OnAction {
        void run();
    }

    public static class Item {
        public final String title;
        public final int iconRes;
        public final OnAction action;

        public Item(String title, int iconRes, OnAction action) {
            this.title = title;
            this.iconRes = iconRes;
            this.action = action;
        }
    }

    /**
     * Hiện bottom sheet tiêu đề `title`, mỗi dòng 1 Item (icon + chữ).
     * Bấm dòng -> chạy action rồi đóng sheet.
     */
    public static void show(Activity a, String title, final List<Item> items) {
        if (a == null || items == null || items.isEmpty()) {
            return;
        }

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Item getItem(int position) {
                return items.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = a.getLayoutInflater().inflate(R.layout.context_menu_row, parent, false);
                }
                Item item = getItem(position);
                ImageView icon = (ImageView) row.findViewById(R.id.cm_icon);
                TextView titleView = (TextView) row.findViewById(R.id.cm_title);
                icon.setImageResource(item.iconRes);
                titleView.setText(item.title);
                return row;
            }
        };

        final Dialog dialog = new Dialog(a, R.style.BottomSheetDialogTheme);
        dialog.setContentView(R.layout.bottom_sheet_menu);
        dialog.setCanceledOnTouchOutside(true);

        TextView titleView = (TextView) dialog.findViewById(R.id.bs_title);
        if (title != null && !title.isEmpty()) {
            titleView.setText(title);
            titleView.setVisibility(View.VISIBLE);
        } else {
            titleView.setVisibility(View.GONE);
        }

        ListView list = (ListView) dialog.findViewById(R.id.bs_list);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final OnAction action = items.get(position).action;
                // Trượt xuống mượt rồi mới đóng + chạy action
                final View rootView = dialog.findViewById(R.id.bs_root);
                if (rootView != null && rootView.getHeight() > 0) {
                    rootView.animate()
                            .translationY(rootView.getHeight())
                            .setDuration(250)
                            .setInterpolator(new AccelerateInterpolator())
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    safeDismiss(dialog);
                                    if (action != null) action.run();
                                }
                            })
                            .start();
                } else {
                    safeDismiss(dialog);
                    if (action != null) action.run();
                }
            }
        });

        Window w = dialog.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            // Tắt window animation (gây giật) — dùng ObjectAnimator trên view thay thế
            w.setWindowAnimations(0);
        }

        // Ẩn content trước khi show để tránh flash 1 frame ở vị trí 0
        final View root = dialog.findViewById(R.id.bs_root);
        if (root != null) {
            root.setVisibility(View.INVISIBLE);
        }
        dialog.show();

        // Trượt lên mượt: animate translationY của content từ height về 0
        // (đảm bảo TẤT CẢ items nằm trong root, animate 1 khối duy nhất)
        if (root != null) {
            root.post(new Runnable() {
                @Override
                public void run() {
                    int h = root.getHeight();
                    if (h > 0) {
                        root.setTranslationY(h);
                        root.setVisibility(View.VISIBLE);
                        root.animate()
                                .translationY(0)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    } else {
                        root.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    }

    private static void safeDismiss(Dialog d) {
        try {
            if (d != null && d.isShowing()) d.dismiss();
        } catch (Exception ignored) {
        }
    }
}
