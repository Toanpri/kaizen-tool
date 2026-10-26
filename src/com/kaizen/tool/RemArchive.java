package com.kaizen.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Format nén ĐỘC QUYỀN ".rem" của Kaizen Tool.
 *
 * <pre>
 * [Header]
 *   Magic:      4 bytes "REM1"      (plain — để nhận diện file)
 *   Flags:      1 byte              (bit 0 = XOR obfuscation bật)
 *   Version:    1 byte (0x01)
 *   EntryCount: 4 bytes int, big-endian
 *
 * [Mỗi entry]
 *   NameLen:    2 bytes unsigned short
 *   Name:       N bytes UTF-8 (đường dẫn tương đối, '/' phân cách;
 *                             thư mục kết thúc bằng '/')
 *   OrigSize:   8 bytes long
 *   CompSize:   8 bytes long
 *   CRC32:      4 bytes int (CRC của dữ liệu GỐC chưa nén)
 *   IsDir:      1 byte (0/1)
 *   Data:       M bytes (deflate → XOR từng byte với key xoay vòng)
 * </pre>
 *
 * Mọi byte sau Magic đều được XOR với key {@link #XOR_KEY} xoay vòng.
 * Đây là obfuscation (chống app khác như ZArchiver/WinRAR đọc được),
 * không phải mã hóa bảo mật cao.
 *
 * Luồng xử lý file lớn: nén/giải nén qua stream, file ≤16MB qua RAM,
 * file lớn hơn qua temp file — không bao giờ load toàn bộ file lớn vào RAM.
 */
public class RemArchive {

    private static final byte[] MAGIC = new byte[]{'R', 'E', 'M', '1'};
    private static final byte VERSION = 0x01;
    private static final byte FLAG_XOR = 0x01;
    /** File được mã hóa bằng mật khẩu user (key = SHA-256(password)) thay vì key cố định. */
    private static final byte FLAG_PW = 0x02;

    /**
     * Key XOR obfuscation. Đổi key này thì file .rem cũ sẽ không đọc được nữa.
     */
    private static final byte[] XOR_KEY = "KaizenRem2026".getBytes(Charset.forName("US-ASCII"));

    private static final Charset UTF8 = Charset.forName("UTF-8");
    /** Buffer 64KB — đủ lớn để syscall hiệu quả, đủ nhỏ cho memory. */
    private static final int BUFFER_SIZE = 65536;
    /** File (gốc hoặc nén) ≤ ngưỡng này thì xử lý qua RAM, lớn hơn qua temp file. */
    private static final long MEM_LIMIT = 16L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000_000; // chống file hỏng gây lặp vô hạn
    private static final String CANCELLED = "Đã hủy";

    // ------------------------------------------------------------------
    // XOR stream wrappers — mọi byte sau Magic đều đi qua đây
    // ------------------------------------------------------------------

    private static class XorOutputStream extends FilterOutputStream {
        private int pos = 0;
        private final byte[] key;
        XorOutputStream(OutputStream out) { this(out, XOR_KEY); }
        XorOutputStream(OutputStream out, byte[] key) {
            super(out);
            this.key = key != null ? key : XOR_KEY;
        }
        @Override public void write(int b) throws IOException {
            out.write(b ^ key[pos++ % key.length]);
        }
        @Override public void write(byte[] b, int off, int len) throws IOException {
            // XOR theo block rồi ghi 1 lần — KHÔNG gọi out.write() từng byte
            // (trước đây mỗi byte = 1 syscall trên FileOutputStream không buffer,
            //  là nguyên nhân chính khiến nén .rem chậm)
            int keyLen = key.length;
            byte[] xored = new byte[len];
            int p = pos;
            for (int i = 0; i < len; i++) {
                xored[i] = (byte) (b[off + i] ^ key[p++ % keyLen]);
            }
            pos = p % keyLen;
            out.write(xored, 0, len);
        }
    }

    private static class XorInputStream extends FilterInputStream {
        private int pos = 0;
        private final byte[] key;
        XorInputStream(InputStream in) { this(in, XOR_KEY); }
        XorInputStream(InputStream in, byte[] key) {
            super(in);
            this.key = key != null ? key : XOR_KEY;
        }
        @Override public int read() throws IOException {
            int b = in.read();
            // Mask key byte với 0xFF: byte Java có dấu, key SHA-256 có byte >= 0x80
            // sẽ bị sign-extend thành int âm nếu không mask (trả về số âm != -1).
            return b < 0 ? -1 : (b ^ (key[pos++ % key.length] & 0xFF));
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0) {
                for (int i = 0; i < n; i++) {
                    b[off + i] = (byte) (b[off + i] ^ key[pos++ % key.length]);
                }
            }
            return n;
        }
        @Override public long skip(long n) throws IOException {
            // Giữ pos đồng bộ với byte đã bỏ qua (nếu không XOR sẽ lệch)
            long s = in.skip(n);
            if (s > 0) pos = (int) ((pos + s) % key.length);
            return s;
        }
    }

    /**
     * Key từ mật khẩu user: SHA-256(password UTF-8). Khác key cố định nên
     * app khác (kể cả Kaizen Tool không có mật khẩu) không đọc được.
     */
    public static byte[] deriveKey(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(password.getBytes(UTF8));
        } catch (Exception e) {
            // SHA-256 luôn có trên Android — fallback không bao giờ chạy
            byte[] b = password.getBytes(UTF8);
            byte[] k = new byte[32];
            for (int i = 0; i < 32; i++) k[i] = b[i % b.length];
            return k;
        }
    }

    private static byte[] keyFor(String password) {
        return (password != null && !password.isEmpty()) ? deriveKey(password) : XOR_KEY;
    }

    private static byte flagsFor(String password) {
        byte f = FLAG_XOR;
        if (password != null && !password.isEmpty()) f |= FLAG_PW;
        return f;
    }

    // ------------------------------------------------------------------
    // Internal holders
    // ------------------------------------------------------------------

    private static class Entry {
        String name; // relative, '/' separated; dirs end with '/'
        File file;
        boolean isDir;
        Entry(String name, File file, boolean isDir) {
            this.name = name;
            this.file = file;
            this.isDir = isDir;
        }
    }

    private static class EntryHeader {
        String name;
        long origSize;
        long compSize;
        int crc;
        boolean isDir;
    }

    private static class CancelledException extends IOException {
        CancelledException() { super(CANCELLED); }
    }

    // ------------------------------------------------------------------
    // Public API — create
    // ------------------------------------------------------------------

    /**
     * Nén sources thành file .rem. Chạy trên background thread riêng,
     * báo tiến trình qua cb (giống ZipUtils.createZip).
     */
    public static void createRem(final List<File> sources, final File destRem, final int level,
                                 final FileOps.OpCallback cb, final CancelToken token) {
        createRem(sources, destRem, level, null, cb, token);
    }

    /**
     * Nén sources thành file .rem. password khác rỗng -> key XOR = SHA-256(password),
     * chỉ Kaizen Tool có mật khẩu mới giải nén được.
     */
    public static void createRem(final List<File> sources, final File destRem, final int level,
                                 final String password,
                                 final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File out = null;
                try {
                    if (sources == null || sources.isEmpty()) {
                        done(cb, false, "Không có mục nào để nén");
                        return;
                    }
                    if (destRem == null) {
                        done(cb, false, "File đích không hợp lệ");
                        return;
                    }
                    for (File s : sources) {
                        if (s == null || !s.exists()) {
                            done(cb, false, "Mục không tồn tại: " + (s != null ? s.getName() : "null"));
                            return;
                        }
                    }
                    File parent = destRem.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        done(cb, false, "Không tạo được thư mục đích");
                        return;
                    }

                    out = uniqueFile(destRem);

                    // Guard: không nén thư mục vào chính nó
                    for (File s : sources) {
                        if (s.isDirectory() && isInside(out, s)) {
                            done(cb, false, "Không thể nén thư mục vào chính nó");
                            return;
                        }
                    }

                    List<Entry> entries = new ArrayList<Entry>();
                    for (File s : sources) collect(s, "", entries);
                    final int total = entries.size();

                    DataOutputStream dos = null;
                    try {
                        // BufferedOutputStream 64KB: gom syscall write, kết hợp với
                        // XorOutputStream ghi theo block -> tốc độ tăng hàng chục lần
                        OutputStream fos = new java.io.BufferedOutputStream(
                                new FileOutputStream(out), BUFFER_SIZE);
                        fos.write(MAGIC); // plain — để nhận diện
                        dos = new DataOutputStream(new XorOutputStream(fos, keyFor(password)));
                        dos.writeByte(flagsFor(password));
                        dos.writeByte(VERSION);
                        dos.writeInt(total);

                        byte[] buf = new byte[BUFFER_SIZE];
                        int doneCount = 0;
                        for (Entry e : entries) {
                            if (cancelled(token)) throw new CancelledException();
                            if (e.isDir) {
                                writeEntryHeader(dos, e.name, 0, 0, 0, true);
                            } else {
                                writeFileEntry(dos, e, out.getParentFile(), level, token, buf);
                            }
                            doneCount++;
                            progress(cb, doneCount, total, displayName(e.name));
                        }
                        dos.flush();
                    } finally {
                        closeQuietly(dos);
                    }

                    done(cb, true, null);
                } catch (CancelledException e) {
                    if (out != null) out.delete();
                    done(cb, false, CANCELLED);
                } catch (Exception e) {
                    if (out != null) out.delete();
                    done(cb, false, msg(e));
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Public API — extract all
    // ------------------------------------------------------------------

    /** Giải nén toàn bộ file .rem vào destDir. */
    public static void extractRem(final File remFile, final File destDir, final boolean overwrite,
                                  final FileOps.OpCallback cb, final CancelToken token) {
        extractRem(remFile, destDir, overwrite, null, cb, token);
    }

    /** Giải nén toàn bộ file .rem; password cho file mã hóa (null = không mật khẩu). */
    public static void extractRem(final File remFile, final File destDir, final boolean overwrite,
                                  final String password,
                                  final FileOps.OpCallback cb, final CancelToken token) {
        extractInternal(remFile, null, destDir, overwrite, password, "", cb, token);
    }

    // ------------------------------------------------------------------
    // Public API — extract selected entries (archive browser mode)
    // ------------------------------------------------------------------

    /**
     * Giải nén các entry được chọn (tên entry dùng '/' phân cách,
     * thư mục có thể có hoặc không có '/' ở cuối).
     */
    public static void extractEntries(final File remFile, final List<String> entryNames,
                                      final File destDir, final boolean overwrite,
                                      final FileOps.OpCallback cb, final CancelToken token) {
        extractEntries(remFile, entryNames, destDir, overwrite, null, "", cb, token);
    }

    /**
     * Giải nén các entry được chọn; password cho file mã hóa (null = không mật khẩu).
     */
    public static void extractEntries(final File remFile, final List<String> entryNames,
                                      final File destDir, final boolean overwrite,
                                      final String password, final String stripPrefix,
                                      final FileOps.OpCallback cb, final CancelToken token) {
        Set<String> sel = new HashSet<String>();
        if (entryNames != null) {
            for (String n : entryNames) {
                if (n != null) sel.add(stripTrailingSlash(n));
            }
        }
        extractInternal(remFile, sel, destDir, overwrite, password, stripPrefix, cb, token);
    }

    // ------------------------------------------------------------------
    // Public API — list (for archive browser)
    // ------------------------------------------------------------------

    /** Liệt kê entry để browse. Trả về list rỗng nếu file lỗi. */
    public static List<ArchiveEntryInfo> listEntries(File remFile) {
        return listEntries(remFile, null);
    }

    /** Liệt kê entry; password cho file mã hóa. */
    public static List<ArchiveEntryInfo> listEntries(File remFile, String password) {
        List<ArchiveEntryInfo> out = new ArrayList<ArchiveEntryInfo>();
        DataInputStream dis = null;
        try {
            dis = openArchive(remFile, password);
            int total = dis.readInt();
            if (total < 0 || total > MAX_ENTRIES) return out;
            byte[] buf = new byte[BUFFER_SIZE];
            for (int i = 0; i < total; i++) {
                EntryHeader h = readEntryHeader(dis);
                skipFully(dis, h.compSize, buf);
                out.add(new ArchiveEntryInfo(h.name, h.isDir, h.origSize, 0));
            }
        } catch (Exception ignored) {
            // file hỏng -> trả về những gì đọc được (hoặc rỗng)
        } finally {
            closeQuietly(dis);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Extract core (shared by extractRem / extractEntries)
    // ------------------------------------------------------------------

    /**
     * @param filter null = giải nén tất cả; khác null = chỉ giải nén entry
     *               có tên (chuẩn hóa) nằm trong set, hoặc nằm dưới thư mục được chọn.
     */
    private static void extractInternal(final File remFile, final Set<String> filter,
                                        final File destDir, final boolean overwrite,
                                        final String password, final String stripPrefix,
                                        final FileOps.OpCallback cb, final CancelToken token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                DataInputStream dis = null;
                try {
                    if (remFile == null || !remFile.isFile()) {
                        done(cb, false, "File .rem không tồn tại");
                        return;
                    }
                    if (destDir != null && !destDir.exists() && !destDir.mkdirs()) {
                        done(cb, false, "Không tạo được thư mục đích");
                        return;
                    }

                    dis = openArchive(remFile, password);
                    int total = dis.readInt();
                    if (total < 0 || total > MAX_ENTRIES) {
                        done(cb, false, "File .rem không hợp lệ");
                        return;
                    }

                    byte[] buf = new byte[BUFFER_SIZE];
                    int doneCount = 0;
                    int extracted = 0;
                    for (int i = 0; i < total; i++) {
                        if (cancelled(token)) throw new CancelledException();
                        EntryHeader h = readEntryHeader(dis);

                        boolean wanted = filter == null || isWanted(h.name, filter);
                        boolean safe = isSafeName(h.name);
                        // v1.2-m1: tên ở đích = tên entry đã bỏ tiền tố thư
                        // mục cha của lựa chọn (xem ArchiveUtils.stripPrefix).
                        String outName = ArchiveUtils.stripPrefix(h.name, stripPrefix);
                        if (outName == null || outName.isEmpty()) outName = h.name;
                        String shown = displayName(h.name);
                        if (!wanted || !safe) {
                            skipFully(dis, h.compSize, buf); // vẫn phải đọc qua để giữ vị trí stream
                        } else if (h.isDir) {
                            File target = new File(destDir, outName);
                            // Pentest 26/10: isSafeName chi chan ten, khong
                            // chan symlink cam san trong dich -> kiem tra
                            // canonical truoc khi mkdirs.
                            if (isWithinDir(target, destDir)) {
                                target.mkdirs();
                                extracted++;
                            } else {
                                shown = "Bỏ qua (không an toàn): " + shown;
                            }
                        } else {
                            File target = new File(destDir, outName);
                            // Pentest 26/10: nhu tren — symlink cam san co
                            // the dan target ra ngoai dich; entry vi pham
                            // thi bo qua (van doc qua stream de giu vi tri).
                            if (!isWithinDir(target, destDir)) {
                                shown = "Bỏ qua (không an toàn): " + shown;
                                skipFully(dis, h.compSize, buf);
                            } else {
                                target.getParentFile().mkdirs();
                                File finalTarget = overwrite ? target : uniqueFile(target);
                                extractOneEntry(dis, h, finalTarget, token, buf);
                                extracted++;
                            }
                        }
                        doneCount++;
                        progress(cb, doneCount, total, shown);
                    }

                    if (filter != null && extracted == 0) {
                        done(cb, false, "Không tìm thấy mục cần giải nén");
                        return;
                    }
                    done(cb, true, null);
                } catch (CancelledException e) {
                    done(cb, false, CANCELLED);
                } catch (Exception e) {
                    done(cb, false, msg(e));
                } finally {
                    closeQuietly(dis);
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // Entry read/write
    // ------------------------------------------------------------------

    private static void writeEntryHeader(DataOutputStream dos, String name,
                                         long origSize, long compSize, int crc,
                                         boolean isDir) throws IOException {
        byte[] nameBytes = name.getBytes(UTF8);
        if (nameBytes.length > 65535) {
            throw new IOException("Tên file quá dài: " + name);
        }
        dos.writeShort(nameBytes.length);
        dos.write(nameBytes);
        dos.writeLong(origSize);
        dos.writeLong(compSize);
        dos.writeInt(crc);
        dos.writeByte(isDir ? 1 : 0);
    }

    private static EntryHeader readEntryHeader(DataInputStream dis) throws IOException {
        EntryHeader h = new EntryHeader();
        int nameLen = dis.readUnsignedShort();
        byte[] nameBytes = new byte[nameLen];
        dis.readFully(nameBytes);
        h.name = new String(nameBytes, UTF8);
        h.origSize = dis.readLong();
        h.compSize = dis.readLong();
        h.crc = dis.readInt();
        h.isDir = dis.readByte() != 0;
        if (h.origSize < 0 || h.compSize < 0) {
            throw new IOException("Header entry không hợp lệ: " + h.name);
        }
        return h;
    }

    /** Nén 1 file: deflate (tính CRC dữ liệu gốc) -> ghi header -> ghi data đã XOR (qua dos). */
    private static void writeFileEntry(DataOutputStream dos, Entry e, File tmpDir, int level,
                                       CancelToken token, byte[] buf) throws IOException {
        CRC32 crc = new CRC32();
        long origSize = e.file.length();

        byte[] memData = null;
        File tmpFile = null;
        long compSize;

        if (origSize <= MEM_LIMIT) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            deflateTo(e.file, crc, baos, level, token, buf);
            memData = baos.toByteArray();
            compSize = memData.length;
        } else {
            tmpFile = File.createTempFile("rem", ".tmp", tmpDir);
            FileOutputStream tfos = new FileOutputStream(tmpFile);
            try {
                deflateTo(e.file, crc, tfos, level, token, buf);
            } finally {
                closeQuietly(tfos);
            }
            compSize = tmpFile.length();
        }

        try {
            writeEntryHeader(dos, e.name, origSize, compSize, (int) crc.getValue(), false);
            if (memData != null) {
                dos.write(memData); // đi qua XorOutputStream
            } else {
                FileInputStream tfis = new FileInputStream(tmpFile);
                try {
                    int n;
                    while ((n = tfis.read(buf)) != -1) {
                        if (cancelled(token)) throw new CancelledException();
                        dos.write(buf, 0, n); // đi qua XorOutputStream
                    }
                } finally {
                    closeQuietly(tfis);
                }
            }
        } finally {
            if (tmpFile != null) tmpFile.delete();
        }
    }

    /** Deflate file nguồn vào out (chưa XOR), đồng thời cập nhật CRC32 của dữ liệu gốc. */
    private static void deflateTo(File src, CRC32 crc, OutputStream out, int level,
                                  CancelToken token, byte[] buf) throws IOException {
        Deflater deflater = new Deflater(clampLevel(level));
        DeflaterOutputStream dout = new DeflaterOutputStream(out, deflater);
        FileInputStream fis = new FileInputStream(src);
        try {
            int n;
            while ((n = fis.read(buf)) != -1) {
                if (cancelled(token)) throw new CancelledException();
                crc.update(buf, 0, n);
                dout.write(buf, 0, n);
            }
            dout.finish();
            out.flush();
        } finally {
            // Không close dout (sẽ close stream ngoài); chỉ giải phóng deflater
            deflater.end();
            closeQuietly(fis);
        }
    }

    /** Giải nén 1 entry: đọc đúng compSize byte (đã XOR-decode) -> inflate -> ghi file + check CRC. */
    private static void extractOneEntry(DataInputStream dis, EntryHeader h, File target,
                                        CancelToken token, byte[] buf) throws IOException {
        InputStream compIn;
        File tmp = null;
        if (h.compSize <= MEM_LIMIT) {
            int len = (int) h.compSize;
            byte[] comp = new byte[len];
            dis.readFully(comp, 0, len);
            compIn = new ByteArrayInputStream(comp);
        } else {
            tmp = File.createTempFile("remx", ".tmp", target.getParentFile());
            FileOutputStream tfos = new FileOutputStream(tmp);
            try {
                copyN(dis, tfos, h.compSize, token, buf);
            } finally {
                closeQuietly(tfos);
            }
            compIn = new FileInputStream(tmp);
        }

        CRC32 crc = new CRC32();
        InflaterInputStream iis = new InflaterInputStream(compIn);
        FileOutputStream fos = new FileOutputStream(target);
        long written = 0;
        try {
            int n;
            while ((n = iis.read(buf)) != -1) {
                if (cancelled(token)) throw new CancelledException();
                crc.update(buf, 0, n);
                fos.write(buf, 0, n);
                written += n;
            }
            fos.flush();
        } finally {
            closeQuietly(fos);
            closeQuietly(iis);
            if (tmp != null) tmp.delete();
        }

        if (written != h.origSize) {
            target.delete();
            throw new IOException("Kích thước giải nén không khớp: " + h.name);
        }
        if ((int) crc.getValue() != h.crc) {
            target.delete();
            throw new IOException("CRC không khớp (file hỏng?): " + h.name);
        }
    }

    // ------------------------------------------------------------------
    // Archive open / validation
    // ------------------------------------------------------------------

    /**
     * Mở file .rem: đọc + kiểm tra Magic (plain), trả về DataInputStream
     * đã bọc XOR, vị trí ngay sau Magic.
     */
    /**
     * File có magic "REM1" không (không quan tâm key/mật khẩu).
     */
    public static boolean hasMagic(File f) {
        java.io.InputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] magic = new byte[MAGIC.length];
            int n = 0;
            while (n < magic.length) {
                int r = in.read(magic, n, magic.length - n);
                if (r < 0) return false;
                n += r;
            }
            for (int i = 0; i < MAGIC.length; i++) {
                if (magic[i] != MAGIC[i]) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            closeQuietly(in);
        }
    }

    /**
     * File .rem có yêu cầu mật khẩu không. Heuristic: magic đúng nhưng
     * không giải mã được bằng key mặc định -> coi như cần mật khẩu.
     */
    public static boolean needsPassword(File remFile) {
        if (!hasMagic(remFile)) return false;
        DataInputStream dis = null;
        try {
            dis = openArchive(remFile, null);
            return false;
        } catch (Exception e) {
            return true;
        } finally {
            closeQuietly(dis);
        }
    }

    private static DataInputStream openArchive(File remFile) throws IOException {
        return openArchive(remFile, null);
    }

    private static DataInputStream openArchive(File remFile, String password) throws IOException {
        // BufferedInputStream 64KB: giảm syscall read khi duyệt entry
        FileInputStream fis = new FileInputStream(remFile);
        java.io.BufferedInputStream bis = new java.io.BufferedInputStream(fis, BUFFER_SIZE);
        boolean ok = false;
        try {
            byte[] magic = new byte[MAGIC.length];
            readFully(bis, magic, 0, magic.length);
            for (int i = 0; i < MAGIC.length; i++) {
                if (magic[i] != MAGIC[i]) {
                    throw new IOException("Không phải file .rem hợp lệ");
                }
            }
            boolean wantPw = password != null && !password.isEmpty();
            DataInputStream dis = new DataInputStream(new XorInputStream(bis, keyFor(password)));
            byte flags = dis.readByte();
            byte ver = dis.readByte();
            if ((flags & FLAG_XOR) == 0) {
                throw new IOException(wantPw ? "Sai mật khẩu" : "File .rem không được hỗ trợ (flags)");
            }
            if (ver != VERSION) {
                // Sai mật khẩu -> flags/version giải mã ra rác
                throw new IOException(wantPw ? "Sai mật khẩu"
                        : "Phiên bản .rem không hỗ trợ: " + ver);
            }
            ok = true;
            return dis;
        } finally {
            if (!ok) closeQuietly(bis);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void collect(File src, String prefix, List<Entry> out) {
        String name = prefix + src.getName();
        if (src.isDirectory()) {
            out.add(new Entry(name + "/", src, true));
            File[] kids = src.listFiles();
            if (kids != null) {
                for (File k : kids) collect(k, name + "/", out);
            }
        } else {
            out.add(new Entry(name, src, false));
        }
    }

    private static String stripTrailingSlash(String n) {
        if (n != null && n.endsWith("/") && n.length() > 1) {
            return n.substring(0, n.length() - 1);
        }
        return n;
    }

    private static boolean isWanted(String entryName, Set<String> filter) {
        String norm = stripTrailingSlash(entryName);
        if (filter.contains(norm)) return true;
        for (String sel : filter) {
            if (norm.startsWith(sel + "/")) return true;
        }
        return false;
    }

    /** Chặn Zip-Slip: tên tuyệt đối hoặc chứa ".." đều bị loại. */
    private static boolean isSafeName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.charAt(0) == '/') return false;
        int start = 0;
        for (int i = 0; i <= name.length(); i++) {
            if (i == name.length() || name.charAt(i) == '/') {
                int len = i - start;
                if (len == 2 && name.charAt(start) == '.' && name.charAt(start + 1) == '.') {
                    return false;
                }
                start = i + 1;
            }
        }
        return true;
    }

    private static String displayName(String entryName) {
        String n = stripTrailingSlash(entryName);
        int slash = n.lastIndexOf('/');
        return slash >= 0 ? n.substring(slash + 1) : n;
    }

    private static int clampLevel(int level) {
        if (level < 0) return 0;
        if (level > 9) return 9;
        return level;
    }

    private static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int n = in.read(b, off, len);
            if (n < 0) throw new IOException("Hết dữ liệu giữa chừng");
            off += n;
            len -= n;
        }
    }

    /**
     * Bỏ qua n byte của stream. KHÔNG dùng dis.skip() vì FileInputStream.skip()
     * bypass XorInputStream làm lệch vị trí key XOR — phải đọc qua readFully.
     */
    private static void skipFully(DataInputStream dis, long n, byte[] buf) throws IOException {
        while (n > 0) {
            int want = (int) Math.min(buf.length, n);
            dis.readFully(buf, 0, want);
            n -= want;
        }
    }

    private static void copyN(DataInputStream dis, OutputStream out, long n,
                              CancelToken token, byte[] buf) throws IOException {
        while (n > 0) {
            if (cancelled(token)) throw new CancelledException();
            int want = (int) Math.min(buf.length, n);
            dis.readFully(buf, 0, want);
            out.write(buf, 0, want);
            n -= want;
        }
    }

    private static boolean cancelled(CancelToken token) {
        return token != null && token.isCancelled();
    }

    private static void progress(FileOps.OpCallback cb, int done, int total, String name) {
        if (cb != null) cb.onProgress(done, total, name);
    }

    private static void done(FileOps.OpCallback cb, boolean ok, String err) {
        if (cb != null) cb.onDone(ok, ok ? null : err);
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try { c.close(); } catch (Exception ignored) { }
        }
    }

    private static String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    /** True nếu child nằm trong (hoặc bằng) ancestor. */
    private static boolean isInside(File child, File ancestor) {
        String a = ancestor.getAbsolutePath() + File.separator;
        String c = child.getAbsolutePath();
        return c.equals(ancestor.getAbsolutePath()) || c.startsWith(a);
    }

    /**
     * Pentest 26/10: true neu target SAU KHI RESOLVE SYMLINK nam trong
     * destDir. Khac isInside (chi dung absolute path, khong thay symlink).
     */
    private static boolean isWithinDir(File target, File destDir) {
        try {
            String base = destDir.getCanonicalPath() + File.separator;
            String canon = target.getCanonicalPath();
            return canon.startsWith(base);
        } catch (Exception e) {
            return false;
        }
    }

    /** Tên file duy nhất nếu đã tồn tại (giống ZipUtils.uniqueFile). */
    static File uniqueFile(File file) {
        File parent = file.getParentFile();
        String name = file.getName();
        File candidate = file;
        if (!candidate.exists()) return candidate;
        String base;
        String ext;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        } else {
            base = name;
            ext = "";
        }
        int i = 1;
        do {
            candidate = new File(parent, base + " (" + i + ")" + ext);
            i++;
        } while (candidate.exists());
        return candidate;
    }
}
