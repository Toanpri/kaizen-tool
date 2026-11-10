# Theme Ram / Rem — plan + execution ledger (2026-11-08)

Kazoi: Ram = hồng (mặc định), Rem = xanh dương nhạt. 2 nút trong Cài đặt.
Bấm Rem: nền Ram → Rem, màu chủ đạo → xanh nhạt, mọi ảnh đổi theo theme.
Icon app GIỮ NGUYÊN (nền hồng + tóc xanh = vừa khít 2 màu 2 bé).

## Spec (nguồn: tin nhắn Kazoi 07:50)
- Mặc định Ram. Nút Ram / Rem trong SettingsActivity, mục "Nhân vật".
- Đổi theme: nền chính, màu chủ đạo (toolbar, FAB, drawer header, tiêu đề...),
  icon folder, chibi ngồi trên dialog tiến trình, nền popup tiến trình.
- Ảnh Ram đã chốt: nền bg_ram.jpg, folder-ram-v2, chibi-ram-sitting-v2 (mắt hồng),
  popup-ram-v2 (bé đứng bên phải, đối xứng Rem bên trái).
- Ảnh Rem xanh nhạt đã chốt 2/3: bg_rem_blue.jpg, ic_folder_rem_blue.png.
  Icon app xanh KHÔNG lấy. Ảnh Rem gốc giữ nguyên.

## Assets (res/drawable-nodpi/)
| Theme | Nền | Folder | Chibi dialog | Popup bg |
|---|---|---|---|---|
| Ram | bg_ram.jpg ✅ | ic_folder_ram.png | ram_dialog.png | popup_bg_ram.png |
| Rem | bg_rem_blue.jpg ✅ | ic_folder_rem_blue.png ✅ | rem_dialog.png ✅ có sẵn | popup_bg_rem.png ✅ có sẵn |

## Cơ chế (rulings)
- **R1 — Màu:** layout dùng `?android:attr/colorPrimary|colorPrimaryDark`
  thay `@color/...`; 2 theme trong styles.xml: `AppTheme` (Ram hồng, giữ nguyên)
  + `AppTheme_Rem` (xanh nhạt: primary #79AEE8, dark #5B93D6, accent #A9CDF0).
  Activity chọn theme qua `CharacterTheme.appTheme(ctx)` trước super.onCreate
  (thay 6 chỗ `setTheme(R.style.AppTheme)`). Manifest giữ AppTheme = Ram.
- **R2 — Dialog:** `DialogTheme_Rem` + `BottomSheetDialogTheme_Rem`;
  `CatBuilder` tự đổi `R.style.DialogTheme` → theme theo nhân vật (1 chỗ, ~40 site
  theo luôn); ContextMenuDialog + ProgressDialog dùng helper.
- **R3 — Ảnh theo theme:** `CharacterTheme` resolver: bgRes/folderIconRes/
  dialogChibiRes/popupBgRes. MainActivity set bg trong onCreate + onResume;
  FileAdapter.iconResFor(Context, item); ArchiveDialogs set chibi + popup bg
  (cần thêm id cho 2 ImageView trong dialog_progress.xml).
- **R4 — Dark mode:** primary xanh áp cả 2 mode (hex cứng trong AppTheme_Rem);
  màu nền/chữ/divider vẫn @color → values-night giữ nguyên.
- **R5 — Làm trên main, chưa commit** (flow quen thuộc: build test → Kazoi OK →
  commit/push/release). Không branch.
- **R6 — bottom_sheet_bg giữ @color/selectedBackground** (hồng nhạt ở light mode)
  — ĐÃ XỬ LÝ 8/11 theo lệnh Kazoi: giờ đổi theo theme luôn (xem ledger).

## Slices
1. Assets Ram còn lại vào res/ + host test CharacterThemeSpec (RED→GREEN).
2. CharacterTheme.java + styles.xml (AppTheme_Rem, DialogTheme_Rem, BottomSheetDialogTheme_Rem).
3. XML: @color/colorPrimary* → ?android:attr/* (layouts + fab_pink/badge_red).
4. Activity setTheme + MainActivity bg + onResume recreate + FileAdapter folder icon.
5. SettingsActivity: mục "Nhân vật" + 2 nút Ram/Rem.
6. Progress dialog: id + ArchiveDialogs wiring (chibi + popup bg).
7. Dialog theme plumbing (CatBuilder, ContextMenuDialog, ProgressDialog).
8. Build test APK, verify sha, giao Kazoi test (gofile).

## Ledger
- Slice 1: assets Ram lưu vào res/ (ic_folder_ram 512px, ram_dialog 288x576,
  popup_bg_ram.jpg q85) + Rem blue tối ưu (512px); host test
  CharacterThemeSpecTest 21/21 PASS (RED trước: file chưa có; GREEN sau).
- Slice 2-7: CharacterTheme.java + CharacterThemeSpec.java + styles
  (AppTheme_Rem/DialogTheme_Rem/BottomSheetDialogTheme_Rem, xanh #79AEE8/
  #5B93D6/#A9CDF0); 18 chỗ @color/colorPrimary* → ?android:attr/*;
  setTheme ×6 activity; MainActivity bg + recreate khi đổi nhân vật;
  FileAdapter.iconResFor(Context, item); Settings "Nhân vật" + 2 nút;
  dialog_progress id dialog_bg/dialog_chibi + ArchiveDialogs wiring;
  CatBuilder tự đổi DialogTheme, ContextMenuDialog + ProgressDialog theo theme.
- Slice 8: build OK exit 0 — KaizenTool-1.3-m2-theme-test.apk, 8,223,740 B,
  sha256 86da1fd4bb3cc5b8c1e373ef37f59e259dc35d34e62823ee6f098b9857a24bb5,
  vc 28, v1/v2/v3 OK, signer CN=KaizenTool, 8/8 theme drawables trong APK,
  resources.arsc STORED. CHƯA commit/push — chờ Kazoi test OK.
  Gofile test build: https://gofile.io/d/PcwJvyRF (md5 87ad575cb3f5c20dab4853488f0afcc6).
- R6 ĐÃ XỬ LÝ 8/11 (Kazoi: "phải đổi cho đồng nhất"): bottom sheet + nền chọn +
  popup menu + thanh đường dẫn + divider đều đổi theo theme (attr
  characterSelectedBackground/characterPathBarBackground/characterDivider,
  màu Rem ở colors.xml + values-night; pressed color qua Spec, host test
  25/25 PASS). Build lại OK — KaizenTool-1.3-m2-theme-test.apk,
  sha256 958793a0e940560a4de0c9f904fb37a71f18b8b51bf8d85f459c1a320891092f,
  SIGN OK, gofile mới: https://gofile.io/d/cEdnMKo9 (md5 3460d60d024cc825788eb0a66fa7ce87).
  CHƯA commit/push — chờ Kazoi test OK.
- **Đợt 2 (8/11 ~08:16, theo ảnh test của Kazoi):** (1) folder Ram/Rem xanh có
  góc trắng → đã xoá nền trắng ở 4 góc bằng flood fill (cả ic_folder_ram v3
  mới + ic_folder_rem_blue; góc alpha=0, chibi/apron không bị ăn); (2) 3 chỗ
  màu hồng còn sót → sửa hết: nút settings (btn_pink + banner APK theo attr),
  dialog tai mèo (nền/viền theo Spec + tai mèo xanh cat_ears_rem.png làm bằng
  đảo kênh R↔B), icon popup vị trí + icon/lịch sử tìm kiếm + banner APK +
  notification + chữ pull-refresh + chữ "Đang phát" (theo CharacterTheme);
  host test Spec 29/29 PASS. CatPopupDrawable.java là code CHẾT (không ai
  dùng) nên để nguyên. (3) Ram vẽ lại theo ảnh mẫu anh gửi (ảnh Rem+Ram
  gốc): nơ đen + hoa hồng, mắt đỏ, mặt nghiêm — bộ v4/v3 (bg, folder, chibi,
  popup); chibi giữ mắt hồng theo ý anh đã chốt, nền trắng đã xoá trong
  suốt. Build mới: sha256 a31b2d6c264cfb16fd251b0ea58bdc892a8d818f91ebc8ed518110e9fa3603eb,
  8,391,760 B, SIGN OK, 8 drawable theme đủ, arsc STORED — gửi thẳng vào chat.
- **Đợt 3 (8/11 ~08:22, ảnh test nén ZIP):** popup Ram TO hơn popup Rem — nguyên
  nhân: popup_bg_ram.jpg 1200×783 trong khi popup_bg_rem.png (và ảnh gốc
  popup_bg_rounded.png) đều 800×533; dialog không fix chiều rộng nên đo theo
  kích thước thật của ảnh nền. Sửa: popup Ram về đúng 800×533 (111,160 B).
  Build mới: sha256 15fc0ae486729d5cdc3e6cb00a0575b20190d604492f6173a6dc9fcab07c1cda,
  8,301,648 B, SIGN OK — gửi thẳng vào chat.
- **RELEASE v1.3-m2 (8/11 ~08:31, Kazoi duyệt "oki xong rùi"):** versionName
  1.3-m2 (vc 28 giữ nguyên), build lại OK — KaizenTool-1.3-m2.apk,
  sha256 6e59ee611b3cbbb70a1e9d8ae2825c84ee5c9dda563ca64da104130b4bd140fb,
  SIGN OK. Commit 7ff71ce (46 files, +501/-76), push main OK, tag + GitHub
  Release `v1.3-m2` kèm APK. Issue #1 đã trả lời + đóng (CLOSED).
  KHÔNG commit: theme/ (bản nháp 6.3M), .learnings/, kz-native-test-craft.py,
  kz-native-test.zip. Rollback = cài lại v1.3-m1 (69a8318).
- Ruling R7: test build bump vc 27→28 + versionName 1.3-m2-theme-test
  (tên file khác hẳn v1.3-m1 để không nhầm); release sau chốt tên sau.
