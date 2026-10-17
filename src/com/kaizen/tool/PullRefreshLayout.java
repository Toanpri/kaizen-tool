package com.kaizen.tool;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Pull-to-refresh đơn giản (không cần AndroidX).
 * Bọc ListView/GridView, kéo xuống từ đầu list để refresh.
 */
public class PullRefreshLayout extends FrameLayout {

    public interface OnRefreshListener {
        void onRefresh();
    }

    private LinearLayout header;
    private TextView headerText;
    private ProgressBar headerProgress;
    private OnRefreshListener listener;

    private float startY = -1;
    private boolean isPulling = false;
    private boolean isRefreshing = false;
    private int headerHeight = 0;

    private static final int PULL_THRESHOLD_DP = 80;

    public PullRefreshLayout(Context context) {
        super(context);
        init(context);
    }

    public PullRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // Header: ẩn phía trên (negative margin)
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);
        int pad = dp(16);
        header.setPadding(pad, pad, pad, pad);

        headerProgress = new ProgressBar(context);
        headerProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(24), dp(24));
        pp.setMarginEnd(dp(8));
        header.addView(headerProgress, pp);

        headerText = new TextView(context);
        headerText.setText("Kéo xuống để làm mới");
        headerText.setTextColor(0xFFC2185B);
        headerText.setTextSize(14);
        header.addView(headerText);

        LayoutParams lp = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        addView(header, 0, lp);

        // Đo header height sau khi layout
        header.post(new Runnable() {
            @Override
            public void run() {
                headerHeight = header.getHeight();
                header.setTranslationY(-headerHeight);
            }
        });
    }

    public void setOnRefreshListener(OnRefreshListener l) {
        listener = l;
    }

    public void setRefreshing(boolean refreshing) {
        isRefreshing = refreshing;
        if (!refreshing) {
            headerText.setText("Kéo xuống để làm mới");
            headerProgress.setVisibility(View.GONE);
            // Trượt header lên
            header.animate().translationY(-headerHeight).setDuration(200).start();
            // Đưa content về vị trí cũ
            for (int i = 1; i < getChildCount(); i++) {
                getChildAt(i).animate().translationY(0).setDuration(200).start();
            }
        } else {
            headerText.setText("Đang làm mới...");
            headerProgress.setVisibility(View.VISIBLE);
        }
    }

    private int dp(int dp) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (dp * d + 0.5f);
    }

    private boolean isAtTop() {
        // Kiểm tra child list có đang ở đầu không
        for (int i = 1; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof AbsListView) {
                AbsListView list = (AbsListView) child;
                if (list.getVisibility() != View.VISIBLE) continue;
                if (list.getChildCount() == 0) return true;
                return list.getFirstVisiblePosition() == 0
                        && list.getChildAt(0).getTop() >= 0;
            }
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isRefreshing) return false;
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isAtTop()) {
                    startY = ev.getY();
                    isPulling = false;
                } else {
                    startY = -1;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (startY >= 0 && !isPulling) {
                    float dy = ev.getY() - startY;
                    if (dy > dp(10) && isAtTop()) {
                        isPulling = true;
                        return true;
                    }
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isPulling && !isRefreshing) return false;
        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE: {
                float dy = ev.getY() - startY;
                if (dy < 0) dy = 0;
                // Damping: kéo càng xa càng khó
                float offset = dy * 0.5f;
                if (offset > headerHeight * 2) offset = headerHeight * 2;
                header.setTranslationY(offset - headerHeight);
                for (int i = 1; i < getChildCount(); i++) {
                    getChildAt(i).setTranslationY(offset);
                }
                int threshold = dp(PULL_THRESHOLD_DP);
                if (offset >= threshold) {
                    headerText.setText("Thả để làm mới");
                } else {
                    headerText.setText("Kéo xuống để làm mới");
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float dy = ev.getY() - startY;
                float offset = dy * 0.5f;
                isPulling = false;
                startY = -1;
                if (offset >= dp(PULL_THRESHOLD_DP)) {
                    // Trigger refresh
                    isRefreshing = true;
                    headerText.setText("Đang làm mới...");
                    headerProgress.setVisibility(View.VISIBLE);
                    header.animate().translationY(0).setDuration(200).start();
                    for (int i = 1; i < getChildCount(); i++) {
                        getChildAt(i).animate().translationY(headerHeight).setDuration(200).start();
                    }
                    if (listener != null) listener.onRefresh();
                } else {
                    // Hủy, trượt về
                    setRefreshing(false);
                }
                return true;
            }
        }
        return false;
    }
}
