# Format `.rem` — Kaizen Tool độc quyền

**Mục đích:** format nén riêng của Kaizen Tool. File `.rem` chỉ Kaizen Tool mở được
(ZArchiver, WinRAR, 7-Zip... không đọc được).

**Cơ chế độc quyền:** XOR obfuscation — mọi byte sau Magic đều XOR với key xoay vòng
`"KaizenRem2026"`. Đây là obfuscation (chống app khác đọc), không phải mã hóa bảo mật cao.

## Spec (v1)

```
[Header]
  Magic:      4 bytes "REM1"      (plain — để nhận diện file)
  Flags:      1 byte              (bit 0 = XOR obfuscation bật; dành chỗ mở rộng)
  Version:    1 byte (0x01)
  EntryCount: 4 bytes int, big-endian

[Mỗi entry]
  NameLen:    2 bytes unsigned short (big-endian)
  Name:       N bytes UTF-8 — đường dẫn tương đối, '/' phân cách;
                              thư mục kết thúc bằng '/'
  OrigSize:   8 bytes long (kích thước gốc)
  CompSize:   8 bytes long (kích thước sau deflate)
  CRC32:      4 bytes int (CRC của dữ liệu GỐC)
  IsDir:      1 byte (0 = file, 1 = thư mục)
  Data:       M bytes (deflate → XOR từng byte với key xoay vòng)
```

- Nén: `java.util.zip.Deflater` (level 0/1/6/9 map trực tiếp từ UI).
- Mọi byte sau Magic (kể cả header entry) đều qua XOR stream liên tục:
  `byte[i] ^= key[i % key.length]`, `i` đếm từ byte đầu sau Magic.
- File lớn: stream qua temp file (ngưỡng 16MB), không load hết vào RAM.
- CRC32 verify khi giải nén; sai CRC → báo lỗi, xóa file dở.
- Zip-Slip protection: loại entry có `..` hoặc đường dẫn tuyệt đối.
- Key nằm trong `RemArchive.java` (`private static final byte[] XOR_KEY`).
  **Đổi key = file .rem cũ không đọc được nữa.**

## Tích hợp

- `RemArchive.java`: `createRem` / `extractRem` / `extractEntries` / `listEntries`
- `ArchiveUtils.KIND_REM = 7` — browsable (mở như thư mục được)
- Dialog "Nén": option **REM** (cạnh ZIP, 7Z)
- Tap file `.rem`: dialog "Mở / Giải nén" (dùng chung dialog tiến trình đẹp)
