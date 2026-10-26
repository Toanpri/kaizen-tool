package com.kaizen.tool;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * M5 — privileged shell execution for the Root browser.
 *
 * <p>Two backends, tried in order by {@link #execPreferred}:</p>
 * <ol>
 *   <li>Shizuku (if {@link ShizukuHelper#hasPermission()}) — no root needed</li>
 *   <li>{@code su} binary (rooted devices)</li>
 * </ol>
 *
 * <p>All methods are blocking with a timeout — call from a background thread.</p>
 */
public class RootShell {

    /** Result of a shell command. */
    public static class ShellResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
        }

        public boolean ok() {
            return exitCode == 0;
        }
    }

    private static final long DEFAULT_TIMEOUT_MS = 15000;

    private static final String[] SU_PATHS = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
            "/data/adb/magisk/busybox/su",
    };

    private static Boolean sHasRoot; // cached

    // ------------------------------------------------------------------
    // Capability probes
    // ------------------------------------------------------------------

    /** True if a working {@code su} binary exists. Result is cached. */
    public static synchronized boolean hasRoot() {
        if (sHasRoot != null) return sHasRoot;
        boolean found = false;
        // 1) known paths
        for (String p : SU_PATHS) {
            if (new java.io.File(p).exists()) {
                found = true;
                break;
            }
        }
        // 2) PATH lookup
        if (!found) {
            ShellResult r = execRaw(new String[]{"sh", "-c", "command -v su"},
                    DEFAULT_TIMEOUT_MS);
            found = r.ok() && !r.stdout.trim().isEmpty();
        }
        // 3) functional test
        if (found) {
            ShellResult r = execSu(new String[]{"id"}, 5000);
            found = r.ok() && r.stdout.contains("uid=0");
        }
        sHasRoot = found;
        return found;
    }

    /** Forget the cached {@link #hasRoot()} result (e.g. after user roots). */
    public static synchronized void invalidateRootCache() {
        sHasRoot = null;
    }

    /** True if either Shizuku permission or root is available. */
    public static boolean canElevate() {
        return ShizukuHelper.hasPermission() || hasRoot();
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    /** Run via Shizuku if permitted, else via su. Never throws. */
    public static ShellResult execPreferred(String[] cmd) {
        return execPreferred(cmd, DEFAULT_TIMEOUT_MS);
    }

    public static ShellResult execPreferred(String[] cmd, long timeoutMs) {
        if (ShizukuHelper.hasPermission()) {
            ShellResult r = execShizuku(cmd, timeoutMs);
            if (r.ok() || r.exitCode != -1) return r;
            // fall through to su on transport failure
        }
        return execSu(cmd, timeoutMs);
    }

    /** Run a command through Shizuku's privileged process. */
    public static ShellResult execShizuku(String[] cmd, long timeoutMs) {
        ShizukuHelper.ShizukuProcess proc = ShizukuHelper.newProcess(cmd);
        if (proc == null) {
            return new ShellResult(-1, "", "Shizuku not available");
        }
        try {
            StreamGobbler out = new StreamGobbler(proc.getInputStream());
            StreamGobbler err = new StreamGobbler(proc.getErrorStream());
            out.start();
            err.start();
            // Pentest 26/10: waitFor() cu khong co timeout — lenh ket
            // thi treo vinh vien, cancel cung khong ngat duoc. Khong
            // poll exitValue() duoc vi ban Shizuku tra -1 ca khi dang
            // chay (khong nem IllegalThreadStateException) — nen chay
            // waitFor() tren thread rieng va join co deadline, het han
            // thi destroy va tra -1 (giong execRaw).
            final int[] codeBox = new int[]{-2}; // -2 = chua xong
            Thread waiter = new Thread(new Runnable() {
                @Override public void run() {
                    codeBox[0] = proc.waitFor();
                }
            });
            waiter.setDaemon(true);
            waiter.start();
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (codeBox[0] == -2) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) {
                    try { proc.destroy(); } catch (Exception ignored) {}
                    joinQuietly(out, 1000);
                    joinQuietly(err, 1000);
                    return new ShellResult(-1, out.result(), "timeout");
                }
                try {
                    waiter.join(remain);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    try { proc.destroy(); } catch (Exception ignored) {}
                    joinQuietly(out, 1000);
                    joinQuietly(err, 1000);
                    return new ShellResult(-1, out.result(), "interrupted");
                }
            }
            joinQuietly(out, Math.max(1, deadline - System.currentTimeMillis()));
            joinQuietly(err, Math.max(1, deadline - System.currentTimeMillis()));
            return new ShellResult(codeBox[0], out.result(), err.result());
        } finally {
            proc.destroy();
        }
    }

    /** Run a command through {@code su -c}. */
    public static ShellResult execSu(String[] cmd, long timeoutMs) {
        // su -c takes a single shell string
        String[] full = new String[]{"su", "-c", join(cmd)};
        return execRaw(full, timeoutMs);
    }

    /** Run a command directly (no elevation). */
    public static ShellResult execRaw(String[] cmd, long timeoutMs) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            StreamGobbler out = new StreamGobbler(p.getInputStream());
            StreamGobbler err = new StreamGobbler(p.getErrorStream());
            out.start();
            err.start();
            boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                return new ShellResult(-1, out.result(), "timeout");
            }
            out.join(2000);
            err.join(2000);
            return new ShellResult(p.exitValue(), out.result(), err.result());
        } catch (Exception e) {
            return new ShellResult(-1, "",
                    e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            if (p != null) {
                try { p.getInputStream().close(); } catch (Exception ignored) { }
                try { p.getErrorStream().close(); } catch (Exception ignored) { }
                try { p.getOutputStream().close(); } catch (Exception ignored) { }
            }
        }
    }

    /** Join argv into one shell string with proper quoting. */
    public static String join(String[] cmd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmd.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(quote(cmd[i]));
        }
        return sb.toString();
    }

    /** Join a thread, swallowing interruption. */
    private static void joinQuietly(Thread t, long timeoutMs) {
        try {
            t.join(timeoutMs);
        } catch (InterruptedException ignored) {
            // ignore
        }
    }

    /** Single-quote a shell argument. */
    public static String quote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Reads an InputStream fully on a background thread. */
    private static class StreamGobbler extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        StreamGobbler(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            if (in == null) return;
            byte[] tmp = new byte[8192];
            try {
                int n;
                while ((n = in.read(tmp)) != -1) {
                    buf.write(tmp, 0, n);
                }
            } catch (Exception ignored) {
                // ignore
            } finally {
                try { in.close(); } catch (Exception ignored) { }
            }
        }

        String result() {
            try {
                return buf.toString("UTF-8");
            } catch (Exception e) {
                return buf.toString();
            }
        }
    }
}
