# 🐱 Kaizen Tool

File manager + archiver cho Android, viết từ scratch bằng native Java — không phải mod của app nào.

![Version](https://img.shields.io/badge/version-1.0--m5-pink)
![Platform](https://img.shields.io/badge/platform-Android-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## 📥 Download

Tải APK mới nhất tại: **[Releases](https://github.com/Toanpri/kaizen-tool/releases)**

## Features

### File Manager
- Duyệt, copy/cut/paste, xóa, đổi tên, tạo thư mục/file mới
- Multi-select, sắp xếp, tìm kiếm trong thư mục
- Bookmark thư mục (long-press)
- Hỗ trợ thẻ nhớ SD
- Chế độ xem list/grid
- Pull-to-refresh

### Archive
- **Tạo:** ZIP (AES-256), 7Z, REM (định dạng riêng)
- **Giải nén:** ZIP, 7Z, TAR, TAR.GZ, RAR, REM
- Duyệt trực tiếp trong file nén
- Mã hóa password, chia volume
- Dialog tiến trình với nhân vật Rem

### Search
- Tìm file/thư mục trên toàn bộ storage
- Lưu lịch sử tìm kiếm

### Media Viewer
- **Ảnh:** pinch zoom, double-tap zoom, swipe qua lại
- **Video:** play/pause, seek, fullscreen
- **Audio:** play/pause, next/previous, seek

### APK Installer
- Xem icon, tên, package, version, quyền
- So sánh version đã cài

### Shizuku (WIP)
- Duyệt file hệ thống không cần root

## UI
- Theme Rem (Re:Zero) với popup hình mèo 🐱
- Sáng/tối, tiếng Việt/English

## Build

```bash
cd kaizen-tool
./build.sh
```

Yêu cầu: JDK 17, Android SDK (android-34), `d8`, `aapt`, `apksigner`.

Output: `KaizenTool-m5.apk`

## Project Structure

```
kaizen-tool/
├── src/com/kaizen/tool/    # Source Java
├── res/                    # Resources (layout, drawable, values)
├── libs/                   # Thư viện (commons-compress, xz...)
├── art/                    # Artwork AI-generated
├── docs/plans/             # Kế hoạch milestone
├── build.sh                # Script build
└── AndroidManifest.xml
```

## REM Format

Định dạng nén riêng của Kaizen Tool:
- Magic: `REM1`
- Nén Deflate + CRC check
- XOR obfuscation (không đọc được bằng app khác)
- Hỗ trợ password (SHA-256 key derivation)

Chi tiết: [docs/rem-format-spec.md](docs/rem-format-spec.md)

## License

MIT
