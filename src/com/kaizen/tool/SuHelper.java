package com.kaizen.tool;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Chạy lệnh qua SU (root) — giống ZArchiver.
 * Đơn giản hơn Shizuku nhiều: chỉ cần `su -c "lệnh"`.
 */
public class SuHelper {

    private static final String TAG = "SuHelper";

    private static Boolean sAvailable = null;

    /** Kiểm tra máy có root (su) không. */
    public static boolean isAvailable() {
        if (sAvailable != null) return sAvailable;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "echo", "su-test"});
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            p.waitFor();
            sAvailable = "su-test".equals(line != null ? line.trim() : null);
            Log.d(TAG, "SU available: " + sAvailable);
        } catch (Exception e) {
            Log.d(TAG, "SU not available: " + e.getMessage());
            sAvailable = false;
        }
        return sAvailable;
    }

    /**
     * Chạy lệnh qua su, trả về stdout.
     * Trả về chuỗi "ERROR: ..." nếu thất bại.
     */
    public static String run(String... cmd) {
        if (!isAvailable()) {
            return "ERROR: SU not available";
        }
        // Ghép lệnh thành 1 chuỗi cho `su -c`
        StringBuilder sb = new StringBuilder();
        for (String s : cmd) {
            if (sb.length() > 0) sb.append(' ');
            // Escape đơn giản: bọc trong nháy đơn nếu có khoảng trắng
            if (s.contains(" ")) {
                sb.append('\'').append(s.replace("'", "'\\''")).append('\'');
            } else {
                sb.append(s);
            }
        }
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", sb.toString()});
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append('\n');
            }
            br.close();
            p.waitFor();
            String result = out.toString();
            return result.isEmpty() ? "ERROR: empty output" : result;
        } catch (Exception e) {
            Log.e(TAG, "SU run failed", e);
            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
