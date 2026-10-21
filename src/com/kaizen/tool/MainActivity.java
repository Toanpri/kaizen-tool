package com.kaizen.tool;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListPopupWindow;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * M1 — main file browser activity.
 * Permissions, navigation, selection (ActionMode), clipboard (copy/cut/paste),
 * delete, rename, new folder/file, properties, sort, hidden-file toggle.
 * File work itself is delegated to FileOps / Dialogs (other agents).
 */
public class MainActivity extends Activity {

    private static final int REQ_STORAGE = 100;

    private static final String PREFS_BOOKMARKS = "kaizen_bookmarks";

    private static final String[] SORT_LABELS = {
            "Tên A-Z", "Tên Z-A",
            "Ngày mới nhất", "Ngày cũ nhất",
            "Dung lượng nhỏ trước", "Dung lượng lớn trước",
            "Theo loại"
    };

    private File currentDir;

    // M5 — root browser mode: rootDir != null means browsing the device
    // filesystem via RootFileProvider (Shizuku/su). Absolute path, "/" = top.
    private String rootDir = null;
    /** Cached RootFileProvider.isAvailable() — blocking call, refreshed off-UI. */
    private boolean rootAvailable = false;

    // M5 — Shizuku lifecycle: refresh root availability on binder events.
    private final ShizukuHelper.OnBinderReceivedListener shizukuReceived =
            new ShizukuHelper.OnBinderReceivedListener() {
                @Override public void onBinderReceived() {
                    refreshRootAvailability();
                }
            };
    private final ShizukuHelper.OnBinderDeadListener shizukuDead =
            new ShizukuHelper.OnBinderDeadListener() {
                @Override public void onBinderDead() {
                    rootAvailable = false;
                    if (isRootMode()) {
                        // Mất Shizuku giữa chừng -> thoát root mode về bộ nhớ máy
                        rootDir = null;
                        navigateTo(Environment.getExternalStorageDirectory());
                        toast("Mất kết nối Shizuku");
                    }
                }
            };

    /** M5: đang duyệt filesystem hệ thống qua root/Shizuku. */
    private boolean isRootMode() {
        return rootDir != null && archiveFile == null;
    }

    /** M5: kiểm tra quyền root/Shizuku ở background thread (blocking). */
    private void refreshRootAvailability() {
        new Thread(new Runnable() {
            @Override public void run() {
                rootAvailable = RootFileProvider.isAvailable();
            }
        }).start();
    }

    // M2/M3 — archive browse mode: archiveFile == null means normal filesystem mode.
    // archiveDir is the entry prefix inside the archive ("" = root, ends with "/" when non-empty).
    // archiveKind is one of ArchiveUtils.KIND_*.
    private File archiveFile = null;
    private String archiveDir = "";
    private int archiveKind = ArchiveUtils.KIND_ZIP;
    private ListView listView;
    private GridView gridView;
    private TextView emptyView;
    private ProgressBar progress;
    private View permissionOverlay;

    // M4: custom toolbar (replaces ActionBar)
    private ImageButton navBtn;
    private TextView toolbarTitle;
    private View toolbarNormal;
    private View toolbarSelection;
    private View toolbarSearch;
    private TextView selCount;
    private EditText searchInput;
    private boolean gridMode = false;
    private boolean selectionMode = false;

    // M4: manual sliding drawer (no AndroidX)
    private View drawerScrim;
    private View drawerPanel;
    private ListView drawerList;
    private boolean drawerOpen = false;

    private final List<FileItem> items = new ArrayList<FileItem>();
    private FileAdapter adapter;

    private int sortMode = FileAdapter.SORT_NAME_ASC;
    private boolean showHidden = false;

    private final Set<String> selected = new HashSet<String>();

    // clipboard
    private List<File> clipboard = null;
    private boolean clipboardCut = false;

    // M5: paste mode (FAB dán lơ lửng)
    private View fabPasteContainer;
    private TextView fabPasteBadge;
    private android.widget.ImageButton fabPasteBtn; // v1.1-m2: đổi icon theo mode

    // v1.1-m2: extract-pick mode — chọn thư mục đích giải nén qua FAB
    private File extractPickFile = null;
    private int extractPickKind = -1;
    // v1.1-m3: null = giải nén cả archive; khác null = "Sao chép" các entry trong archive
    private java.util.List<String> extractPickEntries = null;

    // M5: scroll history — vị trí cuộn của mỗi folder khi đi sâu vào,
    // để back ra thì restore đúng chỗ cũ.
    private final Stack<Integer> scrollStack = new Stack<Integer>();

    private boolean listLoaded = false;

    // M5: detect theme change made in SettingsActivity
    private int appliedNightMode = -1;

    // -------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppSettings.apply(this); // M5: theme + locale from settings
        appliedNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ZipUtils.init(this); // 20/10: cho native fallback khi ZIP dùng method lạ (XZ=95...)

        listView = (ListView) findViewById(R.id.file_list);
        emptyView = (TextView) findViewById(R.id.empty_view);
        progress = (ProgressBar) findViewById(R.id.progress);

        // Pull-to-refresh
        final PullRefreshLayout pullRefresh = (PullRefreshLayout) findViewById(R.id.pull_refresh);
        if (pullRefresh != null) {
            pullRefresh.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadDir();
                    // Tắt indicator sau khi load xong (loadDir chạy nhanh, delay 500ms cho mượt)
                    pullRefresh.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            pullRefresh.setRefreshing(false);
                        }
                    }, 600);
                }
            });
        }

        adapter = new FileAdapter(this, items);
        // v1.1-m2: bấm vào icon = chọn nhanh (folder: chọn thay vì vào; file: chọn thay vì mở)
        adapter.setOnIconClickListener(new FileAdapter.OnIconClickListener() {
            @Override public void onIconClick(FileItem item) {
                if (isRootMode()) return; // root: không chọn nhanh
                if (!selectionMode) enterSelectionMode();
                if (selectionMode) toggleSelected(item);
            }
        });
        listView.setAdapter(adapter);
        gridView = (GridView) findViewById(R.id.file_grid);

        AdapterView.OnItemClickListener clickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                FileItem item = adapter.getItemAt(position);
                if (selectionMode) {
                    toggleSelected(item);
                } else if (archiveFile != null) {
                    onArchiveEntryClick(item);
                } else if (isRootMode()) {
                    onRootItemClick(item); // M5
                } else if (item.isDir) {
                    navigateTo(item.file);
                } else if (ArchiveUtils.kindForName(item.name) >= 0) {
                    // M2/M3: tap archive -> dialog Mở / Giải nén
                    final File af = item.file;
                    final int kind = ArchiveUtils.kindForName(item.name);
                    final boolean browsable = ArchiveUtils.isBrowsable(kind);
                    final FileItem archItem = item;
                    ArchiveDialogs.showArchiveOptions(MainActivity.this, af, kind,
                            browsable ? new Runnable() {
                                @Override public void run() {
                                    openArchiveForBrowse(archItem, kind);
                                }
                            } : null,
                            new Runnable() {
                                @Override public void run() {
                                    // v1.1-m2: 3 kiểu giải nén kiểu ZArchiver
                                    ArchiveDialogs.showExtractChoices(MainActivity.this,
                                            af, kind, currentDir,
                                            new Runnable() {
                                                @Override public void run() {
                                                    enterExtractPickMode(af, kind);
                                                }
                                            },
                                            new Runnable() {
                                                @Override public void run() { loadDir(); }
                                            });
                                }
                            });
                } else if (item.name.toLowerCase().endsWith(".apk")) {
                    // v1.1-m2: tap APK -> chọn Xem hoặc Cài đặt
                    final FileItem apkItem = item;
                    new CatBuilder(MainActivity.this, R.style.DialogTheme)
                            .setTitle(item.name)
                            .setPositiveButton("Cài đặt",
                                    new DialogInterface.OnClickListener() {
                                        @Override public void onClick(
                                                DialogInterface d, int which) {
                                            ApkDialogs.showInstall(
                                                    MainActivity.this, apkItem.file);
                                        }
                                    })
                            .setNeutralButton("Xem",
                                    new DialogInterface.OnClickListener() {
                                        @Override public void onClick(
                                                DialogInterface d, int which) {
                                            viewApk(apkItem);
                                        }
                                    })
                            .setNegativeButton("Hủy", null)
                            .show();
                } else {
                    // Trình xem media trong app (ảnh/video/nhạc)
                    int mt = MediaHelper.mediaTypeOf(item.name);
                    // v1.1-m1: dùng openFileItem để hỗ trợ file từ Shizuku
                    openFileItem(item);
                }
            }
        };
        listView.setOnItemClickListener(clickListener);
        gridView.setOnItemClickListener(clickListener);

        AdapterView.OnItemLongClickListener longListener = new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                // M4: long-press -> context menu kiểu ZArchiver
                // M5: root mode -> menu riêng cho file hệ thống
                FileItem it = adapter.getItemAt(position);
                if (isRootMode()) {
                    showRootContextMenu(it);
                    return true;
                }
                showContextMenu(it);
                return true;
            }
        };
        listView.setOnItemLongClickListener(longListener);
        gridView.setOnItemLongClickListener(longListener);

        setupToolbar();
        setupDrawer();

        // M5: FAB dán (paste) lơ lửng
        fabPasteContainer = findViewById(R.id.fab_paste_container);
        fabPasteBadge = (TextView) findViewById(R.id.fab_paste_badge);
        fabPasteBtn = (android.widget.ImageButton) findViewById(R.id.fab_paste);
        findViewById(R.id.fab_paste).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onFabClick(); }
        });

        currentDir = Environment.getExternalStorageDirectory();
        if (!hasPermission()) {
            showPermissionOverlay();
            requestPermission();
        }

        // M5: Shizuku lifecycle
        ShizukuHelper.addOnBinderReceivedListener(shizukuReceived);
        ShizukuHelper.addOnBinderDeadListener(shizukuDead);
        refreshRootAvailability();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // M5: refresh quyền root/Shizuku (user có thể vừa cấp quyền xong)
        refreshRootAvailability();
        // M5: theme changed in Settings -> recreate to apply
        int now = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (appliedNightMode != -1 && now != appliedNightMode) {
            recreate();
            return;
        }
        if (hasPermission()) {
            hidePermissionOverlay();
            if (!listLoaded) {
                loadDir();
            }
        } else {
            showPermissionOverlay();
        }
    }

    // -------------------------------------------------- M4: toolbar

    private void setupToolbar() {
        navBtn = (ImageButton) findViewById(R.id.btn_nav);
        toolbarTitle = (TextView) findViewById(R.id.toolbar_title);
        toolbarNormal = findViewById(R.id.toolbar_normal);
        toolbarSelection = findViewById(R.id.toolbar_selection);
        toolbarSearch = findViewById(R.id.toolbar_search);
        selCount = (TextView) findViewById(R.id.sel_count);
        searchInput = (EditText) findViewById(R.id.search_input);

        navBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isExtractPickMode()) {
                    exitExtractPickMode(); // v1.1-m2: nút X = hủy chọn chỗ giải nén
                } else if (isPasteMode()) {
                    exitPasteMode(); // M5: nút X = hủy dán
                } else if (isAtRoot()) {
                    toggleDrawer();
                } else {
                    goUp();
                }
            }
        });
        // M5: bấm vào thanh đường dẫn -> popup chọn khu vực (lúc nào cũng dùng được)
        toolbarTitle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLocationPopup(); }
        });
        findViewById(R.id.btn_search).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSearchScopeDialog(); }
        });
        findViewById(R.id.btn_sort).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSortDialog(); }
        });
        findViewById(R.id.btn_view).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleViewMode(); }
        });
        findViewById(R.id.btn_overflow).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showOverflowPopup(v); }
        });

        // selection bar buttons
        findViewById(R.id.sel_close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exitSelectionMode(); }
        });
        findViewById(R.id.sel_copy).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selCopy(false); }
        });
        findViewById(R.id.sel_cut).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selCopy(true); }
        });
        findViewById(R.id.sel_delete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmDelete(selectedFiles()); }
        });
        findViewById(R.id.sel_compress).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selCompress(); }
        });
        findViewById(R.id.sel_share).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shareFiles(selectedFiles()); }
        });
        findViewById(R.id.sel_all).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectAll(); }
        });

        // search bar
        findViewById(R.id.search_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hideSearchBar(); }
        });
        findViewById(R.id.search_clear).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { searchInput.setText(""); }
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.setFilter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private boolean isAtRoot() {
        if (archiveFile != null) return false;
        if (rootDir != null) return false; // M5: root mode luôn hiện nút back
        try {
            return currentDir.getCanonicalPath().equals(
                    Environment.getExternalStorageDirectory().getCanonicalPath());
        } catch (Exception e) {
            return false;
        }
    }

    /** Refresh toolbar title + nav icon after each navigation. */
    private void updateToolbar() {
        if (toolbarTitle == null) return;
        if (archiveFile != null) {
            toolbarTitle.setText(archiveFile.getName() + "!/" + archiveDir);
        } else if (rootDir != null) {
            toolbarTitle.setText(rootDir); // M5: hiện đường dẫn hệ thống
        } else if (currentDir != null) {
            toolbarTitle.setText(currentDir.getAbsolutePath());
        }
        navBtn.setImageResource(isAnyPickMode() ? R.drawable.ic_close
                : (isAtRoot() ? R.drawable.ic_hamburger : R.drawable.ic_back));
    }

    /** v1.1-m2: có mode FAB nào đang hoạt động (dán / chọn chỗ giải nén / chọn chỗ nén). */
    private boolean isAnyPickMode() {
        return isPasteMode() || isExtractPickMode();
    }

    private void toggleViewMode() {
        gridMode = !gridMode;
        adapter.setGridMode(gridMode);
        ((ImageButton) findViewById(R.id.btn_view)).setImageResource(
                gridMode ? R.drawable.ic_grid : R.drawable.ic_list);
        if (gridMode) {
            listView.setAdapter(null);
            gridView.setAdapter(adapter);
            listView.setVisibility(View.GONE);
            gridView.setVisibility(View.VISIBLE);
        } else {
            gridView.setAdapter(null);
            listView.setAdapter(adapter);
            gridView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

    private void showSearchBar() {
        toolbarNormal.setVisibility(View.GONE);
        toolbarSearch.setVisibility(View.VISIBLE);
        searchInput.setText("");
        searchInput.requestFocus();
    }

    private void hideSearchBar() {
        toolbarSearch.setVisibility(View.GONE);
        toolbarNormal.setVisibility(View.VISIBLE);
        adapter.setFilter(null);
    }

    private boolean isSearchOpen() {
        return toolbarSearch != null
                && toolbarSearch.getVisibility() == View.VISIBLE;
    }

    // -------------------------------------------------- M5: scroll helpers

    /** List đang hiển thị (list hoặc grid mode). */
    private AbsListView activeList() {
        return gridMode ? gridView : listView;
    }

    /** Vị trí cuộn hiện tại (first visible position). */
    private int getScrollPos() {
        AbsListView av = activeList();
        return av != null ? av.getFirstVisiblePosition() : 0;
    }

    // -------------------------------------------------- M5: global search

    /** M5: bấm nút search -> chọn "Trong thư mục này" / "Tìm toàn máy". */
    private void showSearchScopeDialog() {
        final String[] opts = {"Trong th\u01b0 m\u1ee5c n\u00e0y", "T\u00ecm to\u00e0n m\u00e1y"};
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("T\u00ecm ki\u1ebfm")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            showSearchBar();
                        } else {
                            showGlobalSearchInput();
                        }
                    }
                })
                .show();
    }

    /** M5: nhập từ khóa cho tìm kiếm toàn máy — dialog redesign (icon + radio + lịch sử). */
    private void showGlobalSearchInput() {
        final android.view.View v =
                getLayoutInflater().inflate(R.layout.dialog_global_search, null);
        // Popup tai mèo: CatPopup.apply() xử lý sau khi show().
        final android.widget.EditText input =
                (android.widget.EditText) v.findViewById(R.id.gs_input);
        final android.widget.RadioGroup modeGroup =
                (android.widget.RadioGroup) v.findViewById(R.id.gs_mode);
        android.widget.ImageView icon =
                (android.widget.ImageView) v.findViewById(R.id.gs_icon);
        icon.setColorFilter(0xFFF06292);

        final AlertDialog dlg =
                new CatBuilder(this, R.style.DialogTheme).setView(v).create();
        // Window trong suốt để hiện hình mèo (tai không bị cắt bởi nền dialog mặc định).
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
        }

        // Lịch sử: tối đa 5 từ khóa gần nhất, tap để tìm lại.
        android.widget.LinearLayout histWrap =
                (android.widget.LinearLayout) v.findViewById(R.id.gs_history);
        android.widget.TextView histLabel =
                (android.widget.TextView) v.findViewById(R.id.gs_history_label);
        final java.util.List<String> hist = getSearchHistory();
        if (hist.isEmpty()) {
            histLabel.setVisibility(android.view.View.GONE);
        } else {
            for (final String h : hist) {
                android.widget.TextView tv = new android.widget.TextView(this);
                tv.setText(h);
                tv.setTextColor(0xFFF06292);
                tv.setTextSize(15);
                int hp = dp(4), vp = dp(10);
                tv.setPadding(hp, vp, hp, vp);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tv.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override public void onClick(android.view.View vv) {
                        dlg.dismiss();
                        saveSearchHistory(h);
                        startGlobalSearch(h, modeFromChecked(modeGroup));
                    }
                });
                histWrap.addView(tv);
            }
        }

        v.findViewById(R.id.gs_cancel).setOnClickListener(
                new android.view.View.OnClickListener() {
                    @Override public void onClick(android.view.View vv) { dlg.dismiss(); }
                });
        final Runnable doSearch = new Runnable() {
            @Override public void run() {
                String kw = input.getText().toString().trim();
                if (kw.isEmpty()) {
                    toast("Nh\u1eadp t\u1eeb kh\u00f3a \u0111\u1ec3 t\u00ecm");
                    return;
                }
                dlg.dismiss();
                saveSearchHistory(kw);
                startGlobalSearch(kw, modeFromChecked(modeGroup));
            }
        };
        v.findViewById(R.id.gs_go).setOnClickListener(
                new android.view.View.OnClickListener() {
                    @Override public void onClick(android.view.View vv) { doSearch.run(); }
                });
        input.setOnEditorActionListener(
                new android.widget.TextView.OnEditorActionListener() {
                    @Override public boolean onEditorAction(android.widget.TextView tv,
                                                           int actionId,
                                                           android.view.KeyEvent e) {
                        if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                            doSearch.run();
                            return true;
                        }
                        return false;
                    }
                });
        dlg.show();
        CatPopup.apply(dlg);
    }

    /** Map radio đã chọn -> GlobalSearch.MODE_*. */
    private int modeFromChecked(android.widget.RadioGroup g) {
        int id = g.getCheckedRadioButtonId();
        if (id == R.id.gs_mode_files) return GlobalSearch.MODE_FILES;
        if (id == R.id.gs_mode_dirs) return GlobalSearch.MODE_DIRS;
        return GlobalSearch.MODE_BOTH;
    }

    private static final String PREFS_SEARCH = "kaizen_search_hist";
    private static final int MAX_HISTORY = 5;

    /** Lịch sử tìm kiếm: tối đa 5 từ khóa gần nhất, mới nhất đầu. */
    private java.util.List<String> getSearchHistory() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String raw = getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE)
                .getString("kw", "");
        if (!raw.isEmpty()) {
            for (String s : raw.split("\u0001")) {
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    private void saveSearchHistory(String kw) {
        java.util.List<String> hist = getSearchHistory();
        hist.remove(kw);
        hist.add(0, kw);
        while (hist.size() > MAX_HISTORY) hist.remove(hist.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hist.size(); i++) {
            if (i > 0) sb.append('\u0001');
            sb.append(hist.get(i));
        }
        getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE)
                .edit().putString("kw", sb.toString()).apply();
    }

    /** M5: chạy tìm kiếm đệ quy trên background thread, progress có nút Hủy. */
    private void startGlobalSearch(final String keyword, final int mode) {
        final CancelToken token = new CancelToken();
        final ProgressDialog pd = new ProgressDialog(this, R.style.DialogTheme);
        pd.setTitle("\u0110ang t\u00ecm ki\u1ebfm to\u00e0n m\u00e1y...");
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.setMessage(keyword);
        pd.setCancelable(false);
        pd.setButton(DialogInterface.BUTTON_NEGATIVE, "H\u1ee7y",
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        token.cancel();
                    }
                });
        pd.show();
        CatPopup.apply(pd); // nền mèo cho dialog progress tìm kiếm (không phải dialog Rem)

        final File root = Environment.getExternalStorageDirectory();
        new Thread(new Runnable() {
            @Override public void run() {
                GlobalSearch.run(root, keyword, mode, token, new GlobalSearch.Listener() {
                    @Override public void onProgress(final String path) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (pd.isShowing()) pd.setMessage(path);
                            }
                        });
                    }
                    @Override public void onDone(final List<File> results,
                                                final boolean cancelled) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                try {
                                    pd.dismiss();
                                } catch (Exception e) {
                                    // ignore
                                }
                                if (isFinishing()) return;
                                if (cancelled) {
                                    toast("\u0110\u00e3 h\u1ee7y t\u00ecm ki\u1ebfm");
                                    return;
                                }
                                showSearchResults(keyword, results);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    /** M5: hiện kết quả tìm kiếm; tap -> mở thư mục chứa. */
    private void showSearchResults(String keyword, final List<File> results) {
        if (results.isEmpty()) {
            toast("Kh\u00f4ng t\u00ecm th\u1ea5y \"" + keyword + "\"");
            return;
        }
        final ListView lv = new ListView(this);
        final BaseAdapter ra = new BaseAdapter() {
            @Override public int getCount() { return results.size(); }
            @Override public Object getItem(int p) { return results.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                File f = results.get(position);
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                int p = dp(12);
                row.setPadding(p, dp(10), p, dp(10));
                TextView tvName = new TextView(MainActivity.this);
                tvName.setText((f.isDirectory() ? "[DIR] " : "") + f.getName());
                tvName.setTextSize(15);
                tvName.setTextColor(getResources().getColor(R.color.textPrimary));
                row.addView(tvName);
                TextView tvPath = new TextView(MainActivity.this);
                tvPath.setText(f.getAbsolutePath());
                tvPath.setTextSize(12);
                tvPath.setTextColor(0xFF888888);
                tvPath.setSingleLine(true);
                tvPath.setEllipsize(android.text.TextUtils.TruncateAt.START);
                row.addView(tvPath);
                return row;
            }
        };
        lv.setAdapter(ra);
        // Giới hạn chiều cao để dialog không tràn màn hình khi nhiều kết quả
        LinearLayout lvWrap = new LinearLayout(this);
        lvWrap.setOrientation(LinearLayout.VERTICAL);
        lvWrap.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360)));
        final AlertDialog dlg = new CatBuilder(this, R.style.DialogTheme)
                .setTitle("K\u1ebft qu\u1ea3 (" + results.size() + ")")
                .setView(lvWrap)
                .setNegativeButton("\u0110\u00f3ng", null)
                .create();
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                                              int position, long id) {
                File f = results.get(position);
                dlg.dismiss();
                File target = f.isDirectory() ? f : f.getParentFile();
                if (target != null && target.isDirectory()) {
                    navigateTo(target);
                } else {
                    toast("Kh\u00f4ng m\u1edf \u0111\u01b0\u1ee3c v\u1ecb tr\u00ed n\u00e0y");
                }
            }
        });
        dlg.show();
        CatPopup.apply(dlg); // nền mèo cho dialog kết quả tìm kiếm
    }

    // -------------------------------------------------- M4: selection mode (custom bar, replaces ActionMode)

    private void enterSelectionMode() {
        if (selectionMode) return;
        if (isRootMode()) {
            // M5: root mode dùng menu nhấn giữ riêng, không chọn nhiều
            toast("Chế độ root: nhấn giữ từng mục để thao tác");
            return;
        }
        selectionMode = true;
        hideSearchBar();
        toolbarNormal.setVisibility(View.GONE);
        toolbarSelection.setVisibility(View.VISIBLE);
        // M2/M3: trong archive chỉ giải nén được
        boolean inArchive = archiveFile != null;
        findViewById(R.id.sel_copy).setVisibility(inArchive ? View.GONE : View.VISIBLE);
        findViewById(R.id.sel_cut).setVisibility(inArchive ? View.GONE : View.VISIBLE);
        findViewById(R.id.sel_delete).setVisibility(inArchive ? View.GONE : View.VISIBLE);
        findViewById(R.id.sel_share).setVisibility(inArchive ? View.GONE : View.VISIBLE);
        adapter.setSelectionMode(true);
        updateSelectionBar();
    }

    private void exitSelectionMode() {
        if (!selectionMode) {
            selected.clear();
            adapter.setSelected(selected);
            adapter.setSelectionMode(false);
            return;
        }
        selectionMode = false;
        selected.clear();
        adapter.setSelected(selected);
        adapter.setSelectionMode(false);
        toolbarSelection.setVisibility(View.GONE);
        toolbarNormal.setVisibility(View.VISIBLE);
    }

    private void updateSelectionBar() {
        if (selCount != null) {
            selCount.setText(selected.size() + " đã chọn");
        }
    }

    private void toggleSelected(FileItem item) {
        String p = item.path();
        if (selected.contains(p)) {
            selected.remove(p);
        } else {
            selected.add(p);
        }
        adapter.setSelected(selected);
        updateSelectionBar();
        if (selected.isEmpty()) {
            exitSelectionMode();
        }
    }

    private void selectAll() {
        for (int i = 0; i < adapter.getCount(); i++) {
            selected.add(adapter.getItemAt(i).path());
        }
        adapter.setSelected(selected);
        updateSelectionBar();
    }

    private void selCopy(boolean cut) {
        List<File> files = selectedFiles();
        if (files.isEmpty()) return;
        clipboard = files;
        clipboardCut = cut;
        exitSelectionMode();
        enterPasteMode(); // M5: hiện FAB dán
    }

    private void selCompress() {
        final List<File> srcs = selectedFiles();
        if (srcs.isEmpty()) return;
        if (archiveFile != null) {
            // M2/M3: trong archive -> giải nén các mục đã chọn
            extractSelectedArchiveEntries();
            return;
        }
        exitSelectionMode();
        // v1.1-m1: nếu file từ Shizuku -> copy ra staging trong cache rồi nén
        if (needsShizukuOp(srcs)) {
            toast("Đang chuẩn bị file qua Shizuku...");
            new Thread(new Runnable() {
                @Override public void run() {
                    final java.io.File staging =
                            new java.io.File(getCacheDir(), "compress_staging");
                    deleteRecursive(staging);
                    staging.mkdirs();
                    final List<File> staged = new ArrayList<File>();
                    boolean ok = true;
                    for (File src : srcs) {
                        String dstPath = staging.getAbsolutePath() + "/"
                                + src.getName();
                        if (!ShizukuFileHelper.copy(src.getAbsolutePath(), dstPath)) {
                            ok = false;
                            break;
                        }
                        staged.add(new java.io.File(dstPath));
                    }
                    final boolean stagedOk = ok;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (stagedOk && !staged.isEmpty()) {
                                ArchiveDialogs.showCompress(MainActivity.this,
                                        staged, staging,
                                        new Runnable() {
                                            @Override public void run() {
                                                toast("Đã nén vào "
                                                        + staging.getAbsolutePath()
                                                        + " — dùng Dán để di chuyển");
                                                loadDir();
                                            }
                                        });
                            } else {
                                toast("Không chuẩn bị được file: "
                                        + ShizukuFileHelper.getLastError());
                            }
                        }
                    });
                }
            }).start();
            return;
        }
        ArchiveDialogs.showCompress(MainActivity.this, srcs, currentDir,
                new Runnable() {
                    @Override public void run() { loadDir(); }
                });
    }

    private static void deleteRecursive(java.io.File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            java.io.File[] kids = f.listFiles();
            if (kids != null) for (java.io.File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    // -------------------------------------------------- M4: drawer

    // M5: drawer entries can be dirs, section headers, or actions
    private static class DrawerEntry {
        static final int KIND_DIR = 0;
        static final int KIND_HEADER = 1;
        static final int KIND_SETTINGS = 2;
        static final int KIND_ABOUT = 3;
        static final int KIND_ROOT = 4; // M5: duyệt filesystem hệ thống (/)
        final int kind;
        final String label;
        final int icon;
        final File dir;
        final String bmPath; // bookmark path, null for fixed entries
        private DrawerEntry(int kind, String label, int icon, File dir, String bmPath) {
            this.kind = kind; this.label = label; this.icon = icon;
            this.dir = dir; this.bmPath = bmPath;
        }
        static DrawerEntry dir(String label, int icon, File dir) {
            return new DrawerEntry(KIND_DIR, label, icon, dir, null);
        }
        static DrawerEntry bookmark(File f) {
            return new DrawerEntry(KIND_DIR, f.getName(),
                    R.drawable.ic_bookmark, f, f.getAbsolutePath());
        }
        static DrawerEntry header(String label) {
            return new DrawerEntry(KIND_HEADER, label, 0, null, null);
        }
        static DrawerEntry settings() {
            return new DrawerEntry(KIND_SETTINGS, "Cài đặt",
                    R.drawable.ic_settings, null, null);
        }
        static DrawerEntry about() {
            return new DrawerEntry(KIND_ABOUT, "Giới thiệu",
                    R.drawable.ic_info, null, null);
        }
        /** M5: mục "Thiết bị này (/)" — duyệt root qua Shizuku/su. */
        static DrawerEntry root() {
            return new DrawerEntry(KIND_ROOT, "Thiết bị này (/)",
                    R.drawable.ic_root, null, null);
        }
    }

    private void setupDrawer() {
        drawerScrim = findViewById(R.id.drawer_scrim);
        drawerPanel = findViewById(R.id.drawer_panel);
        drawerList = (ListView) findViewById(R.id.drawer_list);
        drawerPanel.setVisibility(View.GONE);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); }
        });
        refreshDrawer();
        drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                DrawerEntry e = (DrawerEntry) drawerList.getAdapter().getItem(pos);
                if (e.kind == DrawerEntry.KIND_HEADER) return;
                closeDrawer();
                if (e.kind == DrawerEntry.KIND_SETTINGS) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    return;
                }
                if (e.kind == DrawerEntry.KIND_ABOUT) {
                    startActivity(new Intent(MainActivity.this, AboutActivity.class));
                    return;
                }
                if (e.kind == DrawerEntry.KIND_ROOT) {
                    enterRootMode(); // M5: duyệt filesystem hệ thống
                    return;
                }
                if (e.dir != null && e.dir.exists()) {
                    exitSelectionMode();
                    if (archiveFile != null) exitArchive();
                    navigateTo(e.dir);
                } else {
                    toast("Thư mục không tồn tại");
                }
            }
        });
        // M5: long-press a bookmark in the drawer -> delete it
        drawerList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v,
                                                     int pos, long id) {
                DrawerEntry e = (DrawerEntry) drawerList.getAdapter().getItem(pos);
                if (e.kind == DrawerEntry.KIND_DIR && e.bmPath != null) {
                    confirmRemoveBookmark(e.bmPath);
                    return true;
                }
                return false;
            }
        });
    }

    /** Quick locations for the path popup: fixed dirs + root + bookmarks. */
    /** Tìm các thẻ nhớ SD gắn ngoài (removable storage). */
    private java.util.List<java.io.File> getSdCardRoots() {
        java.util.List<java.io.File> result = new java.util.ArrayList<java.io.File>();
        try {
            java.io.File[] dirs = getExternalFilesDirs(null);
            if (dirs == null) return result;
            String primary = null;
            try {
                primary = android.os.Environment.getExternalStorageDirectory()
                        .getCanonicalPath();
            } catch (Exception ignored) {}
            for (java.io.File d : dirs) {
                if (d == null) continue;
                try {
                    String p = d.getCanonicalPath();
                    // /storage/XXXX-XXXX/Android/data/<pkg>/files -> /storage/XXXX-XXXX
                    int idx = p.indexOf("/Android/data");
                    if (idx > 0) {
                        String root = p.substring(0, idx);
                        // Bỏ qua bộ nhớ trong (primary)
                        if (primary != null && root.equals(primary)) continue;
                        // Chỉ lấy removable (thường có dạng XXXX-XXXX)
                        java.io.File f = new java.io.File(root);
                        if (f.exists() && f.canRead() && !result.contains(f)) {
                            result.add(f);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return result;
    }

    private java.util.List<DrawerEntry> locationEntries() {
        final java.util.List<DrawerEntry> entries = new java.util.ArrayList<DrawerEntry>();
        // M5: "Thiết bị này (/)" lên đầu khi có quyền root/Shizuku
        if (rootAvailable) {
            entries.add(DrawerEntry.root());
        }
        File ext = Environment.getExternalStorageDirectory();
        entries.add(DrawerEntry.dir("Trang chủ", R.drawable.ic_home, ext));
        // Thẻ nhớ SD (nếu có)
        for (File sd : getSdCardRoots()) {
            entries.add(DrawerEntry.dir("Thẻ SD",
                    R.drawable.ic_sdcard, sd));
        }
        entries.add(DrawerEntry.dir("Download", R.drawable.ic_download,
                new File(ext, "Download")));
        entries.add(DrawerEntry.dir("Ảnh", R.drawable.ic_gallery,
                new File(ext, "DCIM")));
        entries.add(DrawerEntry.dir("Nhạc", R.drawable.ic_music,
                new File(ext, "Music")));
        entries.add(DrawerEntry.dir("Tài liệu", R.drawable.ic_doc,
                new File(ext, "Documents")));
        for (String p : getBookmarks()) {
            entries.add(DrawerEntry.bookmark(new File(p)));
        }
        return entries;
    }

    private void refreshDrawer() {
        final java.util.List<DrawerEntry> entries = locationEntries();
        java.util.Set<String> bms = getBookmarks();
        if (!bms.isEmpty()) {
            // insert "Bookmarks" header before the first bookmark entry
            int idx = entries.size() - bms.size();
            entries.add(idx, DrawerEntry.header("Bookmarks"));
        }
        entries.add(DrawerEntry.settings());
        entries.add(DrawerEntry.about());
        BaseAdapter da = new BaseAdapter() {
            @Override public int getCount() { return entries.size(); }
            @Override public Object getItem(int p) { return entries.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int p) {
                return entries.get(p).kind != DrawerEntry.KIND_HEADER;
            }
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                DrawerEntry e = entries.get(pos);
                if (e.kind == DrawerEntry.KIND_HEADER) {
                    if (cv == null || cv.findViewById(R.id.drawer_section_label) == null) {
                        cv = getLayoutInflater().inflate(
                                R.layout.drawer_section, parent, false);
                    }
                    ((TextView) cv.findViewById(R.id.drawer_section_label))
                            .setText(e.label);
                    return cv;
                }
                if (cv == null || cv.findViewById(R.id.drawer_label) == null) {
                    cv = getLayoutInflater().inflate(R.layout.drawer_item, parent, false);
                }
                ((ImageView) cv.findViewById(R.id.drawer_icon)).setImageResource(e.icon);
                ((TextView) cv.findViewById(R.id.drawer_label)).setText(e.label);
                return cv;
            }
        };
        drawerList.setAdapter(da);
    }

    private java.util.Set<String> getBookmarks() {
        return getSharedPreferences(PREFS_BOOKMARKS, MODE_PRIVATE)
                .getStringSet("paths", new java.util.HashSet<String>());
    }

    private void toggleBookmark(File dir) {
        SharedPreferences sp = getSharedPreferences(PREFS_BOOKMARKS, MODE_PRIVATE);
        java.util.Set<String> set =
                new java.util.HashSet<String>(getBookmarks());
        String p = dir.getAbsolutePath();
        boolean added;
        if (set.contains(p)) {
            set.remove(p);
            added = false;
        } else {
            set.add(p);
            added = true;
        }
        sp.edit().putStringSet("paths", set).apply();
        refreshDrawer();
        toast(added ? "Đã thêm bookmark" : "Đã xóa bookmark");
    }

    /** M5: remove a bookmark (from drawer long-press). */
    private void removeBookmark(String path) {
        SharedPreferences sp = getSharedPreferences(PREFS_BOOKMARKS, MODE_PRIVATE);
        java.util.Set<String> set =
                new java.util.HashSet<String>(getBookmarks());
        set.remove(path);
        sp.edit().putStringSet("paths", set).apply();
        refreshDrawer();
        toast("Đã xóa bookmark");
    }

    private void confirmRemoveBookmark(final String path) {
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Xóa bookmark")
                .setMessage("Xóa bookmark \"" + new File(path).getName() + "\"?")
                .setPositiveButton("Xóa", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        removeBookmark(path);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void toggleDrawer() {
        if (drawerOpen) closeDrawer(); else openDrawer();
    }

    private void openDrawer() {
        drawerOpen = true;
        refreshDrawer();
        drawerPanel.setVisibility(View.VISIBLE);
        drawerScrim.setVisibility(View.VISIBLE);
        int w = dp(280);
        drawerPanel.setTranslationX(-w);
        ObjectAnimator.ofFloat(drawerPanel, "translationX", -w, 0)
                .setDuration(220).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        int w = dp(280);
        ObjectAnimator anim = ObjectAnimator.ofFloat(
                drawerPanel, "translationX", 0, -w).setDuration(200);
        anim.start();
        drawerPanel.postDelayed(new Runnable() {
            @Override public void run() {
                if (!drawerOpen) {
                    drawerPanel.setVisibility(View.GONE);
                    drawerScrim.setVisibility(View.GONE);
                }
            }
        }, 200);
    }

    // -------------------------------------------------- M4: long-press context menu (ZArchiver style)

    private void showContextMenu(final FileItem item) {
        final java.util.List<ContextMenuDialog.Item> rows =
                new java.util.ArrayList<ContextMenuDialog.Item>();
        final boolean inArchive = archiveFile != null;

        if (!inArchive && item.isDir) {
            rows.add(new ContextMenuDialog.Item("Mở", R.drawable.ic_open,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() { navigateTo(item.file); }
                    }));
        }
        if (!inArchive && ArchiveUtils.kindForName(item.name) >= 0) {
            final int kind = ArchiveUtils.kindForName(item.name);
            // M5: RAR không browse được — chỉ hiện "Mở" khi browsable (ZIP/7Z/TAR).
            if (ArchiveUtils.isBrowsable(kind)) {
                rows.add(new ContextMenuDialog.Item("Mở", R.drawable.ic_open,
                        new ContextMenuDialog.OnAction() {
                            @Override public void run() {
                                enterArchive(item.file, kind);
                            }
                        }));
            }
            rows.add(new ContextMenuDialog.Item("Giải nén tại đây", R.drawable.ic_extract,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            ArchiveDialogs.extractDirect(MainActivity.this, item.file, kind,
                                    currentDir, new Runnable() {
                                        @Override public void run() { loadDir(); }
                                    });
                        }
                    }));
            // v1.1-m2: 2 kiểu giải nén còn lại kiểu ZArchiver
            final String archBase = ArchiveDialogs.stripExtension(item.name);
            rows.add(new ContextMenuDialog.Item("Giải nén vào ./ " + archBase + "/",
                    R.drawable.ic_extract,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            ArchiveDialogs.extractDirect(MainActivity.this, item.file, kind,
                                    new File(currentDir, archBase), new Runnable() {
                                        @Override public void run() { loadDir(); }
                                    });
                        }
                    }));
            rows.add(new ContextMenuDialog.Item("Giải nén...", R.drawable.ic_extract,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            enterExtractPickMode(item.file, kind);
                        }
                    }));
        }
        if (!inArchive && item.name.toLowerCase().endsWith(".apk")) {
            // v1.1-m2: xem nội dung APK như archive
            rows.add(new ContextMenuDialog.Item("Xem", R.drawable.ic_open,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            viewApk(item);
                        }
                    }));
            rows.add(new ContextMenuDialog.Item("Cài đặt", R.drawable.ic_file_apk,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            ApkDialogs.showInstall(MainActivity.this, item.file);
                        }
                    }));
        }
        if (!inArchive && !item.isDir && ArchiveUtils.kindForName(item.name) < 0
                && !item.name.toLowerCase().endsWith(".apk")) {
            final String openLabel = MediaHelper.mediaTypeOf(item.name) >= 0
                    ? "Mở bằng app khác" : "Mở";
            rows.add(new ContextMenuDialog.Item(openLabel, R.drawable.ic_open,
                    new ContextMenuDialog.OnAction() {
                        // v1.1-m1: dùng openFileItem để hỗ trợ file từ Shizuku
                        @Override public void run() { openFileItem(item); }
                    }));
        }
        if (!inArchive) {
            rows.add(new ContextMenuDialog.Item("Nén thành...", R.drawable.ic_compress,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            java.util.List<File> one =
                                    java.util.Collections.singletonList(item.file);
                            ArchiveDialogs.showCompress(MainActivity.this, one, currentDir,
                                    new Runnable() {
                                        @Override public void run() { loadDir(); }
                                    });
                        }
                    }));
            rows.add(new ContextMenuDialog.Item("Sao chép", R.drawable.ic_copy,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            clipboard = java.util.Collections.singletonList(item.file);
                            clipboardCut = false;
                            enterPasteMode(); // M5: hiện FAB dán
                        }
                    }));
            rows.add(new ContextMenuDialog.Item("Cắt", R.drawable.ic_cut,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            clipboard = java.util.Collections.singletonList(item.file);
                            clipboardCut = true;
                            enterPasteMode(); // M5: hiện FAB dán
                        }
                    }));
            if (clipboard != null && !clipboard.isEmpty()) {
                rows.add(new ContextMenuDialog.Item("Dán tại đây", R.drawable.ic_paste,
                        new ContextMenuDialog.OnAction() {
                            @Override public void run() { paste(); }
                        }));
            }
            rows.add(new ContextMenuDialog.Item("Xóa", R.drawable.ic_delete,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            confirmDelete(java.util.Collections.singletonList(item.file));
                        }
                    }));
            rows.add(new ContextMenuDialog.Item("Đổi tên", R.drawable.ic_edit,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            Dialogs.showRename(MainActivity.this, item.file,
                                    new Runnable() {
                                        @Override public void run() { loadDir(); }
                                    });
                        }
                    }));
            if (item.isDir) {
                final boolean hasBm = getBookmarks()
                        .contains(item.file.getAbsolutePath());
                rows.add(new ContextMenuDialog.Item(
                        hasBm ? "Xóa bookmark" : "Thêm bookmark",
                        R.drawable.ic_bookmark_add,
                        new ContextMenuDialog.OnAction() {
                            @Override public void run() { toggleBookmark(item.file); }
                        }));
            }
        } else {
            // v1.1-m3: menu trong archive kiểu ZArchiver: Thông tin / Sao chép / Đổi tên / Xóa
            rows.add(new ContextMenuDialog.Item("Thông tin", R.drawable.ic_info,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() { showEntryInfo(item); }
                    }));
            rows.add(new ContextMenuDialog.Item("Sao chép", R.drawable.ic_copy,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            final File af = archiveFile;
                            final int kind = archiveKind;
                            final java.util.List<String> one =
                                    java.util.Collections.singletonList(
                                            archiveDir + item.name + (item.isDir ? "/" : ""));
                            enterEntryCopyPickMode(af, kind, one, item.name);
                        }
                    }));
            if (archiveKind == ArchiveUtils.KIND_ZIP || archiveKind == ArchiveUtils.KIND_7Z) {
                rows.add(new ContextMenuDialog.Item("Đổi tên", R.drawable.ic_edit,
                        new ContextMenuDialog.OnAction() {
                            @Override public void run() { renameEntryInArchive(item); }
                        }));
                rows.add(new ContextMenuDialog.Item("Xóa", R.drawable.ic_delete,
                        new ContextMenuDialog.OnAction() {
                            @Override public void run() { deleteEntryInArchive(item); }
                        }));
            }
        }
        if (!inArchive) {
            rows.add(new ContextMenuDialog.Item("Thuộc tính", R.drawable.ic_info,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            Dialogs.showProperties(
                                    MainActivity.this, item.file,
                                    item.viaShizuku, item.isDir);
                        }
                    }));
        }
        if (!inArchive) {
            rows.add(new ContextMenuDialog.Item("Chia sẻ", R.drawable.ic_share,
                    new ContextMenuDialog.OnAction() {
                        @Override public void run() {
                            shareFiles(java.util.Collections.singletonList(item.file));
                        }
                    }));
        }
        rows.add(new ContextMenuDialog.Item("Chọn", R.drawable.ic_select_all,
                new ContextMenuDialog.OnAction() {
                    @Override public void run() {
                        enterSelectionMode();
                        toggleSelected(item);
                    }
                }));

        ContextMenuDialog.show(this, item.name, rows);
    }

    // -------------------------------------------------- M4: share + open file

    private void shareFiles(List<File> files) {
        if (files == null || files.isEmpty()) return;
        try {
            Intent it = new Intent(Intent.ACTION_SEND);
            if (files.size() == 1) {
                File f = files.get(0);
                it.setType(mimeOf(f));
                it.putExtra(Intent.EXTRA_STREAM, GenericFileProvider.uriFor(f));
            } else {
                it.setAction(Intent.ACTION_SEND_MULTIPLE);
                it.setType("*/*");
                java.util.ArrayList<Uri> uris = new java.util.ArrayList<Uri>();
                for (File f : files) uris.add(GenericFileProvider.uriFor(f));
                it.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                // Một số app chỉ nhận URI permission qua ClipData (không qua EXTRA_STREAM)
                if (!uris.isEmpty()) {
                    android.content.ClipData clip = android.content.ClipData.newRawUri(
                            "files", uris.get(0));
                    for (int i = 1; i < uris.size(); i++) {
                        clip.addItem(new android.content.ClipData.Item(uris.get(i)));
                    }
                    it.setClipData(clip);
                }
            }
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, "Chia sẻ"));
        } catch (Exception e) {
            toast("Không chia sẻ được: " + e.getMessage());
        }
        if (selectionMode) exitSelectionMode();
    }

    /** Mở trình xem media trong app (ảnh/video/nhạc). */
    private void openMediaViewer(File f, int mediaType) {
        Intent it;
        if (mediaType == MediaHelper.TYPE_IMAGE) {
            it = new Intent(this, ImageViewerActivity.class);
        } else if (mediaType == MediaHelper.TYPE_VIDEO) {
            it = new Intent(this, VideoPlayerActivity.class);
        } else {
            it = new Intent(this, AudioPlayerActivity.class);
        }
        it.putExtra("path", f.getAbsolutePath());
        it.putExtra("dir", currentDir != null
                ? currentDir.getAbsolutePath() : f.getParent());
        try {
            startActivity(it);
        } catch (Exception e) {
            toast("Không mở được file này");
        }
    }

    /**
     * v1.1-m1: Mở FileItem. Nếu file từ Shizuku (không đọc trực tiếp được),
     * copy ra cache trước rồi mở bản copy.
     */
    private void openFileItem(final FileItem item) {
        if (item.viaShizuku) {
            // v1.1-m1: copy qua Shizuku ra cache (không toast, mở luôn)
            new Thread(new Runnable() {
                @Override public void run() {
                    final java.io.File cached = ShizukuFileHelper.copyToCache(
                            MainActivity.this, item.file.getAbsolutePath(), item.name);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (cached != null && cached.exists()) {
                                int mt = MediaHelper.mediaTypeOf(item.name);
                                if (mt >= 0) {
                                    openMediaViewer(cached, mt);
                                } else {
                                    openFile(cached);
                                }
                            } else {
                                toast("Không đọc được file: "
                                        + ShizukuFileHelper.getLastError());
                            }
                        }
                    });
                }
            }).start();
        } else {
            int mt = MediaHelper.mediaTypeOf(item.name);
            if (mt >= 0) {
                openMediaViewer(item.file, mt);
            } else {
                openFile(item.file);
            }
        }
    }

    private void openFile(File f) {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(GenericFileProvider.uriFor(f), mimeOf(f));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(it);
        } catch (Exception e) {
            toast("Không có app nào mở được file này");
        }
    }

    private static String mimeOf(File f) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(f.getName());
        String m = ext == null ? null
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return m == null ? "application/octet-stream" : m;
    }

    // -------------------------------------------------- permission

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            new CatBuilder(this, R.style.DialogTheme)
                    .setTitle("Quyền truy cập file")
                    .setMessage("Kaizen Tool cần quyền \"Tất cả file\" để quản lý file của bạn.")
                    .setPositiveButton("Mở cài đặt", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            openAllFilesSettings();
                        }
                    })
                    .setNegativeButton("Để sau", null)
                    .show();
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    private void openAllFilesSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQ_STORAGE && hasPermission()) {
            hidePermissionOverlay();
            loadDir();
        }
    }

    private void showPermissionOverlay() {
        if (permissionOverlay != null) {
            permissionOverlay.setVisibility(View.VISIBLE);
            return;
        }
        // Full-screen overlay: message + "Mở cài đặt" button
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundResource(R.color.windowBackground);
        layout.setPadding(48, 48, 48, 48);

        TextView msg = new TextView(this);
        msg.setText("Cần quyền truy cập file");
        msg.setTextSize(20);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 0, 0, 32);
        layout.addView(msg);

        Button btn = new Button(this);
        btn.setText("Mở cài đặt");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 30) {
                    openAllFilesSettings();
                } else {
                    requestPermission();
                }
            }
        });
        layout.addView(btn);

        android.widget.FrameLayout root =
                (android.widget.FrameLayout) findViewById(android.R.id.content);
        root.addView(layout, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        permissionOverlay = layout;
    }

    private void hidePermissionOverlay() {
        if (permissionOverlay != null) {
            permissionOverlay.setVisibility(View.GONE);
        }
    }

    // -------------------------------------------------- navigation

    private void navigateTo(File dir) {
        rootDir = null; // M5: rời root mode khi về filesystem thường
        // M5: đi sâu vào folder con -> nhớ vị trí cuộn hiện tại;
        // nhảy sang chỗ khác (drawer, kết quả tìm kiếm...) -> reset lịch sử.
        if (archiveFile == null && currentDir != null && dir != null) {
            try {
                File curCanon = currentDir.getCanonicalFile();
                File newCanon = dir.getCanonicalFile();
                File newParent = newCanon.getParentFile();
                if (newParent != null && newParent.equals(curCanon)) {
                    scrollStack.push(getScrollPos());
                } else {
                    scrollStack.clear();
                }
            } catch (Exception e) {
                scrollStack.clear();
            }
        }
        currentDir = dir;
        exitSelectionMode();
        if (isSearchOpen()) hideSearchBar();
        else adapter.setFilter(null);
        loadDir();
    }

    /** M5: back ra folder cha, restore vị trí cuộn lúc trước khi vào. */
    private void goBackDir() {
        if (archiveFile != null) {
            goUp();
            return;
        }
        // M5: back trong root mode
        if (rootDir != null) {
            String parent = RootFileProvider.parentOf(rootDir);
            int pos = scrollStack.isEmpty() ? 0 : scrollStack.pop();
            exitSelectionMode();
            if (isSearchOpen()) hideSearchBar();
            else adapter.setFilter(null);
            if (parent == null) {
                // Đã ở / -> thoát root mode, về bộ nhớ máy
                rootDir = null;
                currentDir = Environment.getExternalStorageDirectory();
            } else {
                rootDir = parent;
            }
            loadDir(pos);
            return;
        }
        File parent = currentDir != null ? currentDir.getParentFile() : null;
        if (parent == null) return;
        int pos = scrollStack.isEmpty() ? 0 : scrollStack.pop();
        currentDir = parent;
        exitSelectionMode();
        if (isSearchOpen()) hideSearchBar();
        else adapter.setFilter(null);
        loadDir(pos);
    }

    /**
     * M5: vào chế độ duyệt root — kiểm tra quyền ở background thread
     * (RootFileProvider.isAvailable() là blocking).
     */
    private void enterRootMode() {
        toast("Đang kiểm tra quyền...");
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean ok = RootFileProvider.isAvailable();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        rootAvailable = ok;
                        if (!ok) {
                            toast("Cần Shizuku hoặc root để dùng mục này\n"
                                    + "(Cài đặt → Shizuku)");
                            return;
                        }
                        exitSelectionMode();
                        if (archiveFile != null) exitArchive();
                        if (isSearchOpen()) hideSearchBar();
                        else adapter.setFilter(null);
                        scrollStack.clear();
                        rootDir = "/";
                        loadDir();
                    }
                });
            }
        }).start();
    }

    /** M5: di chuyển tới thư mục con trong root mode. */
    private void navigateToRoot(String absPath) {
        scrollStack.push(getScrollPos());
        rootDir = absPath;
        exitSelectionMode();
        if (isSearchOpen()) hideSearchBar();
        else adapter.setFilter(null);
        loadDir();
    }

    private void goUp() {
        if (archiveFile != null) {
            // M2/M3: navigate inside the archive
            if (archiveDir.isEmpty()) {
                exitArchive();
            } else {
                exitSelectionMode();
                archiveDir = archiveParent(archiveDir);
                loadDir();
            }
            return;
        }
        goBackDir();
    }

    // -------------------------------------------------- M2/M3: archive browse mode

    /** v1.1-m2: xem nội dung APK như archive ZIP (copy ra cache nếu từ Shizuku). */
    private void viewApk(final FileItem item) {
        if (item.viaShizuku) {
            new Thread(new Runnable() {
                @Override public void run() {
                    final java.io.File cached = ShizukuFileHelper.copyToCache(
                            MainActivity.this, item.file.getAbsolutePath(), item.name);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (cached != null && cached.exists()) {
                                enterArchive(cached, ArchiveUtils.KIND_ZIP);
                            } else {
                                toast("Không đọc được APK: "
                                        + ShizukuFileHelper.getLastError());
                            }
                        }
                    });
                }
            }).start();
        } else {
            enterArchive(item.file, ArchiveUtils.KIND_ZIP);
        }
    }

    private void enterArchive(File af, int kind) {
        exitSelectionMode();
        if (isPasteMode()) exitPasteMode(); // M5: không dán được vào archive
        exitExtractPickMode(); // v1.1-m2: không chọn đích giải nén trong archive
        if (isSearchOpen()) hideSearchBar();
        else adapter.setFilter(null);
        archiveFile = af;
        archiveKind = kind;
        archiveDir = "";
        loadDir();
    }

    /**
     * Mở archive để browse. Nếu file nằm ở Shizuku path (/Android/data...),
     * copy ra cache trước vì không đọc trực tiếp được (fix bug 19/10).
     */
    private void openArchiveForBrowse(final FileItem item, final int kind) {
        if (!item.viaShizuku) {
            enterArchive(item.file, kind);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                final java.io.File cached = ShizukuFileHelper.copyToCache(
                        MainActivity.this, item.file.getAbsolutePath(), item.name);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (cached != null && cached.exists()) {
                            enterArchive(cached, kind);
                        } else {
                            toast("Không đọc được file nén: "
                                    + ShizukuFileHelper.getLastError());
                        }
                    }
                });
            }
        }).start();
    }

    private void exitArchive() {
        exitSelectionMode();
        if (isSearchOpen()) hideSearchBar();
        else adapter.setFilter(null);
        archiveFile = null;
        archiveDir = "";
        loadDir();
    }

    /** "a/b/" -> "a/", "a/" -> "" */
    private static String archiveParent(String prefix) {
        String t = prefix.endsWith("/") && prefix.length() > 1
                ? prefix.substring(0, prefix.length() - 1) : prefix;
        int i = t.lastIndexOf('/');
        return i < 0 ? "" : t.substring(0, i + 1);
    }

    /** Tap handler for entries while browsing inside an archive. */
    private void onArchiveEntryClick(final FileItem item) {
        if (item.isDir) {
            exitSelectionMode();
            archiveDir = archiveDir + item.name + "/";
            loadDir();
            return;
        }
        // Tap file trong archive -> giải nén ra cache rồi mở bằng trình xem
        // (fix bug 19/10: trước đây hiện dialog giải nén như ảnh Kazoi gửi).
        final File af = archiveFile;
        final int kind = archiveKind;
        final String entryName = archiveDir + item.name;
        final String simpleName = item.name;
        toast("Đang mở " + simpleName + "...");
        new Thread(new Runnable() {
            @Override public void run() {
                java.io.File outFile = null;
                try {
                    java.io.File cacheDir = new java.io.File(getCacheDir(), "archive_view");
                    if (!cacheDir.exists()) cacheDir.mkdirs();
                    // Dọn file xem cũ (>1 ngày) cho đỡ đầy cache
                    try {
                        long cutoff = System.currentTimeMillis() - 24L * 3600 * 1000;
                        java.io.File[] olds = cacheDir.listFiles();
                        if (olds != null) {
                            for (java.io.File f : olds) {
                                if (f.lastModified() < cutoff) deleteRecursive(f);
                            }
                        }
                    } catch (Exception ignored) {}
                    final java.util.List<String> one =
                            java.util.Collections.singletonList(entryName);
                    final java.util.concurrent.CountDownLatch latch =
                            new java.util.concurrent.CountDownLatch(1);
                    final boolean[] ok = new boolean[1];
                    ArchiveUtils.extractEntries(af, kind, one, cacheDir, true,
                            new FileOps.OpCallback() {
                                @Override public void onProgress(int done, int total, String name) {}
                                @Override public void onDone(boolean ok2, String err) {
                                    ok[0] = ok2;
                                    latch.countDown();
                                }
                            }, new CancelToken());
                    latch.await();
                    if (ok[0]) {
                        java.io.File cand = new java.io.File(cacheDir, entryName);
                        if (cand.exists()) outFile = cand;
                    }
                } catch (Exception e) {
                    outFile = null;
                }
                final java.io.File out = outFile;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (out != null && out.exists()) {
                            int mt = MediaHelper.mediaTypeOf(simpleName);
                            if (mt >= 0) {
                                openMediaViewer(out, mt);
                            } else {
                                openFile(out);
                            }
                        } else {
                            toast("Không mở được file trong archive");
                        }
                    }
                });
            }
        }).start();
    }

    /** Extract the currently selected archive entries (files and/or folders). */
    private void extractSelectedArchiveEntries() {
        if (archiveFile == null) return;
        final File af = archiveFile;
        final int kind = archiveKind;
        final String prefix = archiveDir;
        final java.util.Set<String> sel = new java.util.HashSet<String>(selected);
        final java.util.List<FileItem> snapshot = new java.util.ArrayList<FileItem>(items);
        new Thread(new Runnable() {
            @Override public void run() {
                final java.util.List<String> names = new java.util.ArrayList<String>();
                java.util.List<ArchiveEntryInfo> all = ArchiveUtils.listEntries(af, kind);
                for (FileItem it : snapshot) {
                    if (!sel.contains(it.path())) continue;
                    String entry = prefix + it.name + (it.isDir ? "/" : "");
                    if (it.isDir) {
                        for (ArchiveEntryInfo e : all) {
                            if (e.name.startsWith(entry) && !e.name.equals(entry)) {
                                names.add(e.name);
                            }
                        }
                    } else {
                        names.add(entry);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (names.isEmpty()) {
                            toast("Không có gì để giải nén");
                            return;
                        }
                        exitSelectionMode();
                        ArchiveDialogs.showExtractEntries(MainActivity.this, af, kind, names,
                                af.getParentFile(),
                                new Runnable() {
                                    @Override public void run() { loadDir(); }
                                });
                    }
                });
            }
        }).start();
    }

    /** List the current ZIP directory (group flat entries into direct children). */
    private void loadArchiveDir() {
        listLoaded = true;
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        final File af = archiveFile;
        final int kind = archiveKind;
        final String prefix = archiveDir;
        new Thread(new Runnable() {
            @Override public void run() {
                java.util.List<ArchiveEntryInfo> all = ArchiveUtils.listEntries(af, kind);
                java.util.Map<String, ArchiveEntryInfo> dirs =
                        new java.util.LinkedHashMap<String, ArchiveEntryInfo>();
                java.util.List<ArchiveEntryInfo> files =
                        new java.util.ArrayList<ArchiveEntryInfo>();
                java.util.Map<String, Integer> dirCounts =
                        new java.util.HashMap<String, Integer>();
                for (ArchiveEntryInfo e : all) {
                    if (!e.name.startsWith(prefix)) continue;
                    String rest = e.name.substring(prefix.length());
                    if (rest.isEmpty()) continue;
                    int slash = rest.indexOf('/');
                    if (slash < 0) {
                        if (e.isDir) {
                            if (!dirs.containsKey(rest)) dirs.put(rest, e);
                        } else {
                            files.add(e);
                        }
                    } else if (slash == rest.length() - 1) {
                        String d = rest.substring(0, rest.length() - 1);
                        if (!dirs.containsKey(d)) dirs.put(d, e);
                    } else {
                        String d = rest.substring(0, slash);
                        if (!dirs.containsKey(d)) dirs.put(d, null);
                        Integer c = dirCounts.get(d);
                        dirCounts.put(d, c == null ? 1 : c + 1);
                    }
                }
                final java.util.List<FileItem> fresh = new java.util.ArrayList<FileItem>();
                for (java.util.Map.Entry<String, ArchiveEntryInfo> en : dirs.entrySet()) {
                    String seg = en.getKey();
                    if (!showHidden && seg.startsWith(".")) continue;
                    ArchiveEntryInfo info = en.getValue();
                    long t = info != null ? info.time : 0;
                    Integer c = dirCounts.get(seg);
                    fresh.add(new FileItem("archive:" + af.getAbsolutePath() + "!/" + prefix + seg,
                            seg, true, 0, t, c == null ? 0 : c));
                }
                for (ArchiveEntryInfo e : files) {
                    String seg = e.name.substring(prefix.length());
                    if (!showHidden && seg.startsWith(".")) continue;
                    fresh.add(new FileItem("archive:" + af.getAbsolutePath() + "!/" + e.name,
                            seg, false, e.size, e.time, -1));
                }
                FileAdapter.sortFiles(fresh, sortMode);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (archiveFile != af || !archiveDir.equals(prefix)) return; // stale
                        items.clear();
                        items.addAll(fresh);
                        adapter.notifyDataSetChanged();
                        listView.post(new Runnable() {
                            @Override public void run() {
                                listView.setSelection(0);
                            }
                        });
                        progress.setVisibility(View.GONE);
                        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        updateToolbar();
                    }
                });
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (isSearchOpen()) {
            hideSearchBar();
            return;
        }
        if (selectionMode) {
            exitSelectionMode();
            return;
        }
        if (archiveFile != null) {
            goUp(); // M2/M3: navigate up inside archive / exit at root
            return;
        }
        if (rootDir != null) {
            goBackDir(); // M5: back trong root mode
            return;
        }
        File parent = currentDir.getParentFile();
        File root = Environment.getExternalStorageDirectory();
        boolean atRoot = false;
        try {
            atRoot = currentDir.getCanonicalPath().equals(root.getCanonicalPath());
        } catch (Exception e) {
            // fall through
        }
        if (!atRoot && parent != null) {
            goBackDir(); // M5: restore vị trí cuộn cũ
        } else {
            super.onBackPressed();
        }
    }

    // -------------------------------------------------- loading

    private void loadDir() {
        loadDir(0);
    }

    /**
     * M5: loadDir(restorePos) — sau khi refresh, cuộn tới restorePos
     * (0 = về đầu). Dùng khi back ra folder hoặc sau khi xóa/paste
     * để giữ nguyên vị trí cuộn.
     */
    private void loadDir(final int restorePos) {
        if (!hasPermission()) {
            return;
        }
        if (archiveFile != null) {
            loadArchiveDir(); // M2
            return;
        }
        if (rootDir != null) {
            loadRootDir(restorePos); // M5: duyệt qua Shizuku/su
            return;
        }
        listLoaded = true;
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        final File dir = currentDir;
        new Thread(new Runnable() {
            @Override
            public void run() {
                File[] raw = dir.listFiles();
                final List<FileItem> fresh = new ArrayList<FileItem>();
                if (raw != null) {
                    for (File f : raw) {
                        if (!showHidden && f.getName().startsWith(".")) {
                            continue;
                        }
                        fresh.add(new FileItem(f));
                    }
                } else {
                    // v1.1-m1: File API không đọc được (vd: /Android/data) -> thử Shizuku
                    if (ShizukuHelper.isRunning() && ShizukuHelper.hasPermission()) {
                        android.util.Log.d("MainActivity",
                                "listFiles null, trying Shizuku for: " + dir.getAbsolutePath());
                        List<FileItem> shizukuFiles =
                                ShizukuFileHelper.listFiles(dir.getAbsolutePath());
                        if (shizukuFiles != null) {
                            // v1.1-m1: tra tên app + icon cho /Android/data, /Android/obb
                            ShizukuFileHelper.enrichWithAppInfo(MainActivity.this,
                                    shizukuFiles, dir.getAbsolutePath());
                            for (FileItem fi : shizukuFiles) {
                                if (!showHidden && fi.name.startsWith(".")) {
                                    continue;
                                }
                                fresh.add(fi);
                            }
                            android.util.Log.d("MainActivity",
                                    "Shizuku listed " + fresh.size() + " items");
                        } else {
                            // Hiện lỗi để diagnostic
                            final String shizukuErr = ShizukuFileHelper.getLastError();
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    Toast.makeText(MainActivity.this,
                                            "Shizuku không đọc được: " + shizukuErr,
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }
                FileAdapter.sortFiles(fresh, sortMode);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // ignore stale results if user navigated meanwhile
                        if (currentDir != dir) {
                            return;
                        }
                        items.clear();
                        items.addAll(fresh);
                        adapter.notifyDataSetChanged();
                        // M5: restore vị trí cuộn (clamp phòng khi list ngắn lại sau xóa)
                        final int target = Math.max(0,
                                Math.min(restorePos, items.size() - 1));
                        final AbsListView av = activeList();
                        if (av != null) {
                            av.post(new Runnable() {
                                @Override public void run() {
                                    av.setSelection(target);
                                }
                            });
                        }
                        progress.setVisibility(View.GONE);
                        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        updateToolbar();
                    }
                });
            }
        }).start();
    }

    /**
     * M5: load danh sách file trong root mode qua RootFileProvider
     * (Shizuku/su). Kết quả là FileItem ảo với scheme "root:".
     */
    private void loadRootDir(final int restorePos) {
        if (!hasPermission()) {
            return;
        }
        listLoaded = true;
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        final String dir = rootDir;
        RootFileProvider.listDirAsync(dir, new RootFileProvider.ListCallback() {
            @Override public void onResult(java.util.List<FileItem> fresh, String err) {
                if (!dir.equals(rootDir)) {
                    return; // stale — user đã điều hướng chỗ khác
                }
                progress.setVisibility(View.GONE);
                items.clear();
                if (err != null) {
                    // v1.1-m1: chỉ log, không toast (tránh báo lỗi giả khi list vẫn ok)
                    android.util.Log.w("MainActivity", "loadRootDir err: " + err);
                }
                if (fresh != null) {
                    for (FileItem it : fresh) {
                        if (!showHidden && it.name.startsWith(".")) continue;
                        items.add(it);
                    }
                    FileAdapter.sortFiles(items, sortMode);
                }
                adapter.notifyDataSetChanged();
                // M5: restore vị trí cuộn
                final int target = Math.max(0,
                        Math.min(restorePos, items.size() - 1));
                final AbsListView av = activeList();
                if (av != null) {
                    av.post(new Runnable() {
                        @Override public void run() {
                            av.setSelection(target);
                        }
                    });
                }
                emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                updateToolbar();
            }
        });
    }

    // -------------------------------------------------- M5: root browser actions

    /** M5: tap mục trong root mode — thư mục thì vào, file thì copy ra rồi mở. */
    private void onRootItemClick(FileItem item) {
        String real = RootFileProvider.realPath(item.file.getPath());
        if (item.isDir) {
            navigateToRoot(real);
        } else {
            openRootFile(real, item.name);
        }
    }

    /** M5: đọc file hệ thống ra cache qua shell rồi mở bằng app khác. */
    private void openRootFile(final String realPath, final String name) {
        toast("Đang đọc " + name + "...");
        new Thread(new Runnable() {
            @Override public void run() {
                File dst = new File(getCacheDir(),
                        "root_" + System.currentTimeMillis() + "_" + name);
                RootShell.ShellResult r = RootShell.execPreferred(new String[]{
                        "sh", "-c",
                        "cat " + RootShell.quote(realPath)
                                + " > " + RootShell.quote(dst.getAbsolutePath())});
                final boolean ok = r.ok() && dst.exists() && dst.length() > 0;
                final String err = r.stderr.trim();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ok) {
                            openFile(dst);
                        } else {
                            toast("Không mở được file"
                                    + (err.isEmpty() ? "" : ": " + err));
                        }
                    }
                });
            }
        }).start();
    }

    /** M5: menu nhấn giữ cho file/thư mục hệ thống. */
    private void showRootContextMenu(final FileItem item) {
        final String[] opts = {"Mở", "Sao chép ra Download", "Xóa",
                "Sao chép đường dẫn", "Thuộc tính"};
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle(item.name)
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0:
                                onRootItemClick(item);
                                break;
                            case 1:
                                copyRootOut(item);
                                break;
                            case 2:
                                deleteRootItem(item);
                                break;
                            case 3:
                                copyPathToClipboard(
                                        RootFileProvider.realPath(item.file.getPath()));
                                break;
                            default:
                                showRootProperties(item);
                                break;
                        }
                    }
                })
                .show();
    }

    /** M5: sao chép file/thư mục hệ thống ra Download qua shell. */
    private void copyRootOut(final FileItem item) {
        final String real = RootFileProvider.realPath(item.file.getPath());
        final File dl = new File(Environment.getExternalStorageDirectory(), "Download");
        toast("Đang sao chép ra Download...");
        new Thread(new Runnable() {
            @Override public void run() {
                RootShell.ShellResult r = RootShell.execPreferred(new String[]{
                        "sh", "-c", "cp -r " + RootShell.quote(real)
                                + " " + RootShell.quote(dl.getAbsolutePath() + "/")});
                final boolean ok = r.ok();
                final String err = r.stderr.trim();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        toast(ok ? "Đã sao chép ra Download"
                                : "Sao chép thất bại"
                                        + (err.isEmpty() ? "" : ": " + err));
                    }
                });
            }
        }).start();
    }

    /** M5: xóa file/thư mục hệ thống qua shell (có xác nhận + cảnh báo). */
    private void deleteRootItem(final FileItem item) {
        final String real = RootFileProvider.realPath(item.file.getPath());
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Xóa mục hệ thống?")
                .setMessage("\"" + item.name + "\"\n" + real
                        + "\n\nCẩn thận: xóa nhầm file hệ thống có thể làm hỏng máy!")
                .setPositiveButton("Xóa", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        runOpWithProgress("Đang xóa...", "Đã xóa", 1,
                                new OpRunner() {
                                    @Override public void run(FileOps.OpCallback cb) {
                                        cb.onProgress(0, 1, item.name);
                                        RootShell.ShellResult r =
                                                RootShell.execPreferred(new String[]{
                                                        "sh", "-c", "rm -rf "
                                                                + RootShell.quote(real)});
                                        if (r.ok()) {
                                            cb.onProgress(1, 1, item.name);
                                            cb.onDone(true, null);
                                        } else {
                                            String e = r.stderr.trim();
                                            cb.onDone(false, e.isEmpty()
                                                    ? "exit " + r.exitCode : e);
                                        }
                                    }
                                });
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** M5: copy đường dẫn tuyệt đối vào clipboard. */
    private void copyPathToClipboard(String path) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager)
                            getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("path", path));
            toast("Đã sao chép đường dẫn");
        } catch (Exception e) {
            toast("Không sao chép được");
        }
    }

    /** M5: xem thuộc tính file hệ thống. */
    private void showRootProperties(FileItem item) {
        String real = RootFileProvider.realPath(item.file.getPath());
        String info = "Tên: " + item.name
                + "\nĐường dẫn: " + real
                + "\nLoại: " + (item.isDir ? "Thư mục" : "File");
        if (!item.isDir) {
            info += "\nKích thước: " + FileOps.formatSize(item.size);
        }
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Thuộc tính")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show();
    }

    /** M5: tạo thư mục mới trong root mode qua shell. */
    private void rootMkdir() {
        final EditText input = new EditText(this);
        input.setHint("Tên thư mục");
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Thư mục mới")
                .setView(input)
                .setPositiveButton("Tạo", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        final String name = input.getText().toString().trim();
                        if (name.isEmpty() || rootDir == null) return;
                        final String target = RootFileProvider.join(rootDir, name);
                        toast("Đang tạo...");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                final RootShell.ShellResult r =
                                        RootShell.execPreferred(new String[]{"sh", "-c",
                                                "mkdir -p " + RootShell.quote(target)});
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (r.ok()) {
                                            toast("Đã tạo thư mục");
                                            loadDir(getScrollPos());
                                        } else {
                                            toast("Tạo thất bại");
                                        }
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** M5: tạo file mới trong root mode qua shell. */
    private void rootTouch() {
        final EditText input = new EditText(this);
        input.setHint("Tên file");
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("File mới")
                .setView(input)
                .setPositiveButton("Tạo", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        final String name = input.getText().toString().trim();
                        if (name.isEmpty() || rootDir == null) return;
                        final String target = RootFileProvider.join(rootDir, name);
                        toast("Đang tạo...");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                final RootShell.ShellResult r =
                                        RootShell.execPreferred(new String[]{"sh", "-c",
                                                "touch " + RootShell.quote(target)});
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (r.ok()) {
                                            toast("Đã tạo file");
                                            loadDir(getScrollPos());
                                        } else {
                                            toast("Tạo thất bại");
                                        }
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // -------------------------------------------------- selection helpers

    private List<File> selectedFiles() {
        List<File> out = new ArrayList<File>();
        for (FileItem item : items) {
            if (selected.contains(item.path())) {
                out.add(item.file);
            }
        }
        return out;
    }

    // -------------------------------------------------- delete / paste with progress

    private void confirmDelete(final List<File> files) {
        if (files.isEmpty()) {
            return;
        }
        Dialogs.showDeleteConfirm(this, files.size(), new Runnable() {
            @Override
            public void run() {
                runOpWithProgress("Đang xóa...", "Đã xóa", files.size(),
                        new OpRunner() {
                            @Override
                            public void run(FileOps.OpCallback cb) {
                                // v1.1-m1: nếu file trong vùng Shizuku -> xóa qua shell
                                if (needsShizukuOp(files)) {
                                    deleteViaShizuku(files, cb);
                                } else {
                                    FileOps.deleteFiles(files, cb);
                                }
                            }
                        });
            }
        });
    }

    /** v1.1-m1: kiểm tra có file nào cần Shizuku không */
    private boolean needsShizukuOp(List<File> files) {
        if (!ShizukuHelper.isRunning()) return false;
        for (File f : files) {
            if (f != null && ShizukuFileHelper.needsShizuku(f.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    /** v1.1-m1: xóa qua Shizuku shell */
    private void deleteViaShizuku(final List<File> files, final FileOps.OpCallback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                final android.os.Handler ui =
                        new android.os.Handler(android.os.Looper.getMainLooper());
                String firstError = null;
                int done = 0;
                for (File f : files) {
                    if (!ShizukuFileHelper.delete(f.getAbsolutePath())) {
                        firstError = ShizukuFileHelper.getLastError();
                        break;
                    }
                    done++;
                    final int d = done;
                    final String n = f.getName();
                    ui.post(new Runnable() {
                        @Override public void run() { cb.onProgress(d, files.size(), n); }
                    });
                }
                final boolean ok = firstError == null;
                final String err = firstError;
                ui.post(new Runnable() {
                    @Override public void run() { cb.onDone(ok, err); }
                });
            }
        }).start();
    }

    // -------------------------------------------------- M5: paste mode (FAB dán lơ lửng)

    /** Đang giữ clipboard để dán (chế độ copy/cut). */
    private boolean isPasteMode() {
        return clipboard != null && !clipboard.isEmpty();
    }

    /** Vào chế độ dán: hiện FAB + nút nav thành X. */
    private void enterPasteMode() {
        exitExtractPickMode(); // v1.1-m2: các mode loại trừ nhau
        updateFab();
        updateToolbar();
        int n = clipboard.size();
        toast(n + (clipboardCut ? " mục đã cắt" : " mục đã sao chép")
                + " — chọn thư mục rồi bấm nút Dán");
    }

    /** Hủy chế độ dán: ẩn FAB, trả nút nav về bình thường. */
    private void exitPasteMode() {
        clipboard = null;
        clipboardCut = false;
        updateFab();
        updateToolbar();
    }

    /** v1.1-m2: FAB hiện khi có mode nào hoạt động (dán / giải nén / nén). */
    private void updateFab() {
        if (fabPasteContainer == null) return;
        boolean show = isAnyPickMode();
        fabPasteContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && fabPasteBadge != null) {
            int n = isPasteMode() ? clipboard.size() : 1;
            fabPasteBadge.setText(n > 99 ? "99+" : String.valueOf(n));
        }
        // v1.1-m2: đổi icon FAB theo mode (không phải lúc nào cũng hình dán)
        if (fabPasteBtn != null) {
            if (isExtractPickMode()) {
                fabPasteBtn.setImageResource(R.drawable.ic_extract);
                fabPasteBtn.setContentDescription("Giải nén tại đây");
            } else {
                fabPasteBtn.setImageResource(R.drawable.ic_paste);
                fabPasteBtn.setContentDescription("Dán");
            }
        }
    }

    /** v1.1-m2: bấm FAB — điều hướng theo mode đang hoạt động. */
    private void onFabClick() {
        if (isExtractPickMode()) {
            confirmExtractPick();
        } else {
            paste();
        }
    }

    /** Bấm FAB (hoặc menu Dán): hiện dialog xác nhận rồi mới dán. */
    private void paste() {
        if (!isPasteMode()) {
            return;
        }
        final int n = clipboard.size();
        // M5: trong root mode hiện đường dẫn hệ thống
        String dstName;
        if (isRootMode() && rootDir != null) {
            dstName = rootDir;
        } else {
            dstName = (currentDir != null && !currentDir.getName().isEmpty())
                    ? currentDir.getName() : currentDir.getAbsolutePath();
        }
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle(clipboardCut ? "Di chuyển " + n + " mục" : "Dán " + n + " mục")
                .setMessage("Dán " + n + " mục vào \"" + dstName + "\"?")
                .setPositiveButton("Dán", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        doPaste(n);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** Thực hiện copy/cut sau khi user xác nhận. */
    private void doPaste(final int count) {
        if (isRootMode()) {
            doPasteRoot(count, clipboardCut);
            return;
        }
        final List<File> srcs = new ArrayList<File>(clipboard);
        final File dst = currentDir;
        final boolean cut = clipboardCut;
        String title = cut ? "Đang di chuyển..." : "Đang sao chép...";
        runOpWithProgress(title, cut ? "Đã di chuyển" : "Đã sao chép",
                srcs.size(), new OpRunner() {
            @Override
            public void run(FileOps.OpCallback cb) {
                // v1.1-m1: nếu src hoặc dst cần Shizuku -> dùng shell
                if (needsShizukuOp(srcs)
                        || ShizukuFileHelper.needsShizuku(dst.getAbsolutePath())) {
                    pasteViaShizuku(srcs, dst, cut, cb);
                } else if (cut) {
                    FileOps.moveFiles(srcs, dst, cb);
                } else {
                    FileOps.copyFiles(srcs, dst, cb);
                }
            }
        }, new Runnable() {
            @Override public void run() {
                exitPasteMode();
                toast("Đã dán " + count + " mục");
            }
        });
    }

    // -------------------------------------------------- v1.1-m2: extract-pick mode

    /** Đang chọn thư mục đích để giải nén (qua FAB). */
    private boolean isExtractPickMode() {
        return extractPickFile != null;
    }

    /** Vào chế độ chọn thư mục giải nén: hiện FAB, nút nav thành X. */
    private void enterExtractPickMode(File archiveFile, int kind) {
        exitPasteMode();
        if (isSearchOpen()) hideSearchBar();
        extractPickFile = archiveFile;
        extractPickKind = kind;
        extractPickEntries = null; // cả archive
        updateFab();
        updateToolbar();
        toast("Chọn thư mục rồi bấm nút để giải nén \"" + archiveFile.getName() + "\"");
    }

    /**
     * v1.1-m3: "Sao chép" entry trong archive kiểu ZArchiver — thoát archive,
     * chọn thư mục đích bằng FAB (cơ chế giống hệt "Giải nén...").
     */
    private void enterEntryCopyPickMode(File af, int kind,
                                        java.util.List<String> entries, String label) {
        exitArchive();
        exitPasteMode();
        if (isSearchOpen()) hideSearchBar();
        extractPickFile = af;
        extractPickKind = kind;
        extractPickEntries = entries;
        updateFab();
        updateToolbar();
        toast("Chọn thư mục rồi bấm nút để sao chép \"" + label + "\"");
    }

    /** Hủy chế độ chọn thư mục giải nén. */
    private void exitExtractPickMode() {
        if (extractPickFile == null) return;
        extractPickFile = null;
        extractPickKind = -1;
        extractPickEntries = null;
        updateFab();
        updateToolbar();
    }

    /** Bấm FAB khi đang chọn thư mục giải nén: xác nhận rồi giải nén tại đây. */
    private void confirmExtractPick() {
        if (!isExtractPickMode()) return;
        final File af = extractPickFile;
        final int kind = extractPickKind;
        final java.util.List<String> entries = extractPickEntries;
        final File pickedDir = isRootMode() ? new File(rootDir != null ? rootDir : "/") : currentDir;
        String dstName = pickedDir.getName().isEmpty()
                ? pickedDir.getAbsolutePath() : pickedDir.getName();
        if (entries != null) {
            // v1.1-m3: "Sao chép" entry — bung thẳng các mục vào thư mục đã chọn.
            final String label = entries.size() + " mục";
            new CatBuilder(this, R.style.DialogTheme)
                    .setTitle("Sao chép " + label)
                    .setMessage("Sao chép vào \"" + dstName + "\"?")
                    .setPositiveButton("Sao chép", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int which) {
                            exitExtractPickMode();
                            ArchiveDialogs.extractEntriesDirect(MainActivity.this, af, kind,
                                    entries, pickedDir, new Runnable() {
                                        @Override public void run() { loadDir(); }
                                    });
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
            return;
        }
        // v1.1-m3 (fix theo yêu cầu): "Giải nén..." — bung vào folder cùng tên file nén.
        final String base = ArchiveDialogs.stripExtension(af.getName());
        final File destDir = new File(pickedDir, base);
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Giải nén \"" + af.getName() + "\"")
                .setMessage("Giải nén vào \"./" + base + "/\"?")
                .setPositiveButton("Giải nén", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        exitExtractPickMode();
                        ArchiveDialogs.extractDirect(MainActivity.this, af, kind,
                                destDir, new Runnable() {
                                    @Override public void run() { loadDir(); }
                                });
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // -------------------------------------------------- v1.1-m3: Thông tin / Đổi tên / Xóa entry trong archive

    /** Dialog "Thông tin" cho entry trong archive (kiểu ZArchiver). */
    private void showEntryInfo(FileItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tên: ").append(item.name).append("\n");
        sb.append("Loại: ").append(item.isDir ? "Thư mục" : "Tệp").append("\n");
        if (item.isDir) {
            sb.append("Số mục: ").append(item.childCount).append("\n");
        } else {
            sb.append("Kích thước: ").append(FileOps.formatSize(item.size)).append("\n");
        }
        if (item.lastModified > 0) {
            sb.append("Ngày sửa: ").append(FileOps.formatDate(item.lastModified));
        }
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Thông tin")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Đóng", null)
                .show();
    }

    /** Đổi tên 1 entry trong archive (ZIP/7Z) qua 7zz rn. */
    private void renameEntryInArchive(final FileItem item) {
        final File af = archiveFile;
        final int kind = archiveKind;
        if (af == null) return;
        final String oldEntry = archiveDir + item.name + (item.isDir ? "/" : "");

        final EditText input = new EditText(this);
        input.setText(item.name);
        input.setSingleLine(true);
        // Bôi đen phần tên trước đuôi mở rộng (giống Dialogs.showRename)
        int dot = item.name.lastIndexOf('.');
        int end = (!item.isDir && dot > 0) ? dot : item.name.length();
        input.setSelection(0, end);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(p, (int) (p * 0.5f), p, (int) (p * 0.25f));
        layout.addView(input,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Đổi tên")
                .setView(layout)
                .setPositiveButton("Đổi tên", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String newName = input.getText().toString().trim();
                        if (newName.isEmpty() || newName.contains("/")
                                || newName.contains("\\")) {
                            toast("Tên không hợp lệ");
                            return;
                        }
                        if (newName.equals(item.name)) return;
                        final String newEntry =
                                archiveDir + newName + (item.isDir ? "/" : "");
                        if (ArchiveUtils.needsPassword(af, kind)) {
                            ArchiveDialogs.askPassword(MainActivity.this,
                                    new ArchiveDialogs.PasswordListener() {
                                        @Override public void onPassword(String pw) {
                                            if (pw == null) return;
                                            runRenameEntryOp(af, kind, oldEntry, newEntry, pw);
                                        }
                                    });
                        } else {
                            runRenameEntryOp(af, kind, oldEntry, newEntry, null);
                        }
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void runRenameEntryOp(final File af, final int kind,
                                  final String oldEntry, final String newEntry,
                                  final String pw) {
        final int fmt = kind == ArchiveUtils.KIND_7Z ? Native7z.FMT_7Z : Native7z.FMT_ZIP;
        ArchiveDialogs.runOpWithProgress(this, "Đang đổi tên...",
                new ArchiveDialogs.OpWithToken() {
                    @Override public void start(FileOps.OpCallback cb, CancelToken token) {
                        if (!Native7z.renameEntry(MainActivity.this, af, fmt,
                                oldEntry, newEntry, pw, cb, token)) {
                            cb.onDone(false, "Máy này chưa hỗ trợ đổi tên trong archive");
                        }
                    }
                }, "Đã đổi tên xong", new Runnable() {
                    @Override public void run() { loadDir(); }
                });
    }

    /** Xóa 1 entry khỏi archive (ZIP/7Z) qua 7zz d. */
    private void deleteEntryInArchive(final FileItem item) {
        final File af = archiveFile;
        final int kind = archiveKind;
        if (af == null) return;
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Xóa")
                .setMessage("Xóa \"" + item.name + "\" khỏi archive?")
                .setPositiveButton("Xóa", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        collectEntriesAndDelete(af, kind, item);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** Gom entry (mở rộng thư mục thành toàn bộ entry con) rồi xóa. */
    private void collectEntriesAndDelete(final File af, final int kind, final FileItem item) {
        final String prefix = archiveDir;
        final String entry = prefix + item.name + (item.isDir ? "/" : "");
        new Thread(new Runnable() {
            @Override public void run() {
                final java.util.List<String> names = new java.util.ArrayList<String>();
                if (item.isDir) {
                    java.util.List<ArchiveEntryInfo> all =
                            ArchiveUtils.listEntries(af, kind);
                    for (ArchiveEntryInfo e : all) {
                        if (e.name.startsWith(entry)) names.add(e.name);
                    }
                } else {
                    names.add(entry);
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (names.isEmpty()) {
                            toast("Không tìm thấy mục trong archive");
                            return;
                        }
                        if (ArchiveUtils.needsPassword(af, kind)) {
                            ArchiveDialogs.askPassword(MainActivity.this,
                                    new ArchiveDialogs.PasswordListener() {
                                        @Override public void onPassword(String pw) {
                                            if (pw == null) return;
                                            runDeleteEntriesOp(af, kind, names, pw);
                                        }
                                    });
                        } else {
                            runDeleteEntriesOp(af, kind, names, null);
                        }
                    }
                });
            }
        }).start();
    }

    private void runDeleteEntriesOp(final File af, final int kind,
                                    final java.util.List<String> entries, final String pw) {
        final int fmt = kind == ArchiveUtils.KIND_7Z ? Native7z.FMT_7Z : Native7z.FMT_ZIP;
        ArchiveDialogs.runOpWithProgress(this, "Đang xóa...",
                new ArchiveDialogs.OpWithToken() {
                    @Override public void start(FileOps.OpCallback cb, CancelToken token) {
                        if (!Native7z.deleteEntries(MainActivity.this, af, fmt,
                                entries, pw, cb, token)) {
                            cb.onDone(false, "Máy này chưa hỗ trợ xóa trong archive");
                        }
                    }
                }, "Đã xóa xong", new Runnable() {
                    @Override public void run() { loadDir(); }
                });
    }

    // -------------------------------------------------- v1.1-m2: compress-pick mode

    /** v1.1-m1: copy/move qua Shizuku shell */
    private void pasteViaShizuku(final List<File> srcs, final File dst,
                                final boolean cut, final FileOps.OpCallback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                final android.os.Handler ui =
                        new android.os.Handler(android.os.Looper.getMainLooper());
                String firstError = null;
                // Đảm bảo thư mục đích tồn tại (qua Shizuku nếu cần)
                String dstPath = dst.getAbsolutePath();
                if (ShizukuFileHelper.needsShizuku(dstPath)) {
                    ShizukuFileHelper.mkdir(dstPath);
                } else if (!dst.exists()) {
                    dst.mkdirs();
                }
                int done = 0;
                for (File src : srcs) {
                    String srcPath = src.getAbsolutePath();
                    String dstFile = dstPath + "/" + src.getName();
                    boolean ok = cut
                            ? ShizukuFileHelper.move(srcPath, dstFile)
                            : ShizukuFileHelper.copy(srcPath, dstFile);
                    if (!ok) {
                        firstError = ShizukuFileHelper.getLastError();
                        break;
                    }
                    done++;
                    final int d = done;
                    final String n = src.getName();
                    ui.post(new Runnable() {
                        @Override public void run() { cb.onProgress(d, srcs.size(), n); }
                    });
                }
                final boolean ok = firstError == null;
                final String err = firstError;
                ui.post(new Runnable() {
                    @Override public void run() { cb.onDone(ok, err); }
                });
            }
        }).start();
    }

    private interface OpRunner {
        void run(FileOps.OpCallback cb);
    }

    /**
     * M5: dán (copy/cut) các file thường vào thư mục hệ thống qua shell
     * (cp -r / mv với quyền Shizuku/su).
     */
    private void doPasteRoot(final int count, final boolean cut) {
        final java.util.List<File> srcs = new java.util.ArrayList<File>(clipboard);
        final String dst = rootDir;
        runOpWithProgress(cut ? "Đang di chuyển..." : "Đang sao chép...",
                cut ? "Đã di chuyển" : "Đã sao chép", srcs.size(),
                new OpRunner() {
                    @Override public void run(FileOps.OpCallback cb) {
                        int done = 0;
                        for (File s : srcs) {
                            cb.onProgress(done, srcs.size(), s.getName());
                            String cmd = (cut ? "mv " : "cp -r ")
                                    + RootShell.quote(s.getAbsolutePath()) + " "
                                    + RootShell.quote(dst + "/");
                            RootShell.ShellResult r = RootShell.execPreferred(
                                    new String[]{"sh", "-c", cmd});
                            if (!r.ok()) {
                                String e = r.stderr.trim();
                                cb.onDone(false, e.isEmpty() ? s.getName() : e);
                                return;
                            }
                            done++;
                            cb.onProgress(done, srcs.size(), s.getName());
                        }
                        cb.onDone(true, null);
                    }
                },
                new Runnable() {
                    @Override public void run() {
                        exitPasteMode();
                        toast("Đã dán " + count + " mục");
                    }
                });
    }

    private void runOpWithProgress(String title, int total, OpRunner runner) {
        runOpWithProgress(title, "Hoàn tất", total, runner, null);
    }

    private void runOpWithProgress(String title, String successMsg, int total,
                                   OpRunner runner) {
        runOpWithProgress(title, successMsg, total, runner, null);
    }

    /**
     * Chạy op file (xóa / sao chép / di chuyển) với dialog tiến trình đẹp
     * (Rem ngồi trên mép + LED RGB), giống hệt dialog nén/giải nén.
     */
    private void runOpWithProgress(String title, final String successMsg, int total,
                                   OpRunner runner, final Runnable onComplete) {
        final boolean wasSelection = selectionMode;
        final int keepPos = getScrollPos(); // M5: giữ vị trí cuộn sau khi xóa/paste
        final OpRunner opRunner = runner;
        ArchiveDialogs.showFileOpProgress(this, title,
                new ArchiveDialogs.FileOpStarter() {
                    @Override
                    public void start(FileOps.OpCallback cb) {
                        opRunner.run(cb);
                    }
                },
                successMsg,
                new Runnable() {
                    @Override
                    public void run() {
                        // v1.1-m4 fix: KHÔNG quét clipboard ở đây. Handler này
                        // dùng chung cho MỌI op (xóa/dán/...) — trước đây nó xóa
                        // luôn clipboard đang chờ dán mỗi khi một op bất kỳ xong:
                        // xóa 1 file xong là Dán còn hiện nút nhưng bấm không ăn
                        // (clipboard đã null) và nút X trên toolbar biến mất.
                        // Luồng dán tự dọn clipboard bằng exitPasteMode() trong
                        // onComplete của riêng nó (doPaste/doPasteRoot).
                        loadDir(keepPos);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                });
        if (wasSelection) {
            exitSelectionMode();
        }
    }

    // -------------------------------------------------- overflow popup (M4: anchored to toolbar button)
    private static class OverflowItem {
        static final int SORT = 0, HIDDEN = 1, NEW_FOLDER = 2, NEW_FILE = 3, PASTE = 4;
        int id;
        String title;
        boolean hasCheck;
        boolean checked;
        OverflowItem(int id, String title) { this(id, title, false, false); }
        OverflowItem(int id, String title, boolean hasCheck, boolean checked) {
            this.id = id; this.title = title;
            this.hasCheck = hasCheck; this.checked = checked;
        }
    }

    private void showOverflowPopup(View anchorView) {
        final boolean inZip = archiveFile != null;
        final boolean canPaste = !inZip && clipboard != null && !clipboard.isEmpty();

        final boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final int bgColor = getResources().getColor(night
                ? R.color.pathBarBackground : R.color.selectedBackground);
        final int pressedColor = night ? 0xFF5c3a44 : 0xFFF8BBD0;
        final int textColor = getResources().getColor(R.color.textPrimary);

        final java.util.List<OverflowItem> items = new java.util.ArrayList<OverflowItem>();
        items.add(new OverflowItem(OverflowItem.SORT, "S\u1eafp x\u1ebfp"));
        items.add(new OverflowItem(OverflowItem.HIDDEN,
                showHidden ? "\u1ea8n file \u1ea9n" : "Hi\u1ec7n file \u1ea9n",
                true, showHidden));
        if (!inZip) {
            items.add(new OverflowItem(OverflowItem.NEW_FOLDER, "Th\u01b0 m\u1ee5c m\u1edbi"));
            items.add(new OverflowItem(OverflowItem.NEW_FILE, "File m\u1edbi"));
        }
        if (canPaste) {
            items.add(new OverflowItem(OverflowItem.PASTE, "D\u00e1n"));
        }

        final BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int p) { return items.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                OverflowItem it = items.get(position);
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = dp(16);
                row.setPadding(pad, dp(14), pad, dp(14));
                StateListDrawable sel = new StateListDrawable();
                sel.addState(new int[]{android.R.attr.state_pressed},
                        new ColorDrawable(pressedColor));
                sel.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
                row.setBackground(sel);

                TextView tv = new TextView(MainActivity.this);
                tv.setText(it.title);
                tv.setTextSize(16);
                tv.setTextColor(textColor);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tv.setLayoutParams(tlp);
                row.addView(tv);

                if (it.hasCheck) {
                    CheckBox cb = new CheckBox(MainActivity.this);
                    cb.setChecked(it.checked);
                    cb.setClickable(false);
                    cb.setFocusable(false);
                    row.addView(cb);
                }
                return row;
            }
        };

        final ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAdapter(adapter);
        // Anchor to the ⋮ button itself so the popup drops right below it,
        // aligned to the button's right edge (standard overflow menu look).
        popup.setAnchorView(anchorView);
        popup.setDropDownGravity(Gravity.END);
        popup.setHorizontalOffset(0);
        popup.setVerticalOffset(dp(4));
        popup.setWidth(dp(230));
        popup.setModal(true);
        popup.setBackgroundDrawable(new ColorDrawable(bgColor));
        popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                                              int position, long id) {
                OverflowItem it = items.get(position);
                switch (it.id) {
                    case OverflowItem.SORT:
                        popup.dismiss();
                        showSortDialog();
                        break;
                    case OverflowItem.HIDDEN:
                        // Toggle in place, keep popup open.
                        showHidden = !showHidden;
                        it.checked = showHidden;
                        it.title = showHidden ? "\u1ea8n file \u1ea9n" : "Hi\u1ec7n file \u1ea9n";
                        adapter.notifyDataSetChanged();
                        loadDir();
                        break;
                    case OverflowItem.NEW_FOLDER:
                        popup.dismiss();
                        if (isRootMode()) {
                            rootMkdir(); // M5: tạo thư mục hệ thống qua shell
                        } else {
                            Dialogs.showNewFolder(MainActivity.this, currentDir,
                                    new Runnable() {
                                        @Override public void run() { loadDir(); }
                                    });
                        }
                        break;
                    case OverflowItem.NEW_FILE:
                        popup.dismiss();
                        if (isRootMode()) {
                            rootTouch(); // M5: tạo file hệ thống qua shell
                        } else {
                            Dialogs.showNewFile(MainActivity.this, currentDir,
                                    new Runnable() {
                                        @Override public void run() { loadDir(); }
                                    });
                        }
                        break;
                    case OverflowItem.PASTE:
                        popup.dismiss();
                        paste();
                        break;
                }
            }
        });
        popup.show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showSortDialog() {
        new CatBuilder(this, R.style.DialogTheme)
                .setTitle("Sắp xếp")
                .setSingleChoiceItems(SORT_LABELS, sortMode,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                sortMode = which;
                                d.dismiss();
                                FileAdapter.sortFiles(items, sortMode);
                                adapter.notifyDataSetChanged();
                            }
                        })
                .show();
    }

    // -------------------------------------------------- M5: path bar tap -> location popup

    /** Tap the toolbar path -> quick-jump popup. Works any time. */
    private void showLocationPopup() {
        final java.util.List<DrawerEntry> entries = locationEntries();

        final boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final int bgColor = getResources().getColor(night
                ? R.color.pathBarBackground : R.color.selectedBackground);
        final int pressedColor = night ? 0xFF5c3a44 : 0xFFF8BBD0;
        final int textColor = getResources().getColor(R.color.textPrimary);

        BaseAdapter la = new BaseAdapter() {
            @Override public int getCount() { return entries.size(); }
            @Override public Object getItem(int p) { return entries.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int position, View cv, ViewGroup parent) {
                DrawerEntry e = entries.get(position);
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = dp(12);
                row.setPadding(pad, dp(10), pad, dp(10));
                StateListDrawable sel = new StateListDrawable();
                sel.addState(new int[]{android.R.attr.state_pressed},
                        new ColorDrawable(pressedColor));
                sel.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
                row.setBackground(sel);

                ImageView iv = new ImageView(MainActivity.this);
                iv.setImageResource(e.icon);
                iv.setColorFilter(getResources().getColor(R.color.colorPrimary));
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        dp(28), dp(28));
                ilp.setMarginEnd(dp(12));
                iv.setLayoutParams(ilp);
                row.addView(iv);

                // Cột chữ: tên + đường dẫn
                LinearLayout textCol = new LinearLayout(MainActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                textCol.setLayoutParams(tlp);

                TextView tv = new TextView(MainActivity.this);
                tv.setText(e.label);
                tv.setTextSize(16);
                tv.setTextColor(textColor);
                textCol.addView(tv);

                if (e.dir != null) {
                    TextView pv = new TextView(MainActivity.this);
                    try {
                        pv.setText(e.dir.getCanonicalPath());
                    } catch (Exception ex) {
                        pv.setText(e.dir.getAbsolutePath());
                    }
                    pv.setTextSize(12);
                    pv.setTextColor(0xFF999999);
                    pv.setSingleLine(true);
                    pv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                    textCol.addView(pv);
                }
                row.addView(textCol);

                // Dung lượng cho bộ nhớ (Trang chủ / Thẻ SD)
                if (e.dir != null && isStorageRoot(e)) {
                    TextView sv = new TextView(MainActivity.this);
                    sv.setText(storageStats(e.dir));
                    sv.setTextSize(12);
                    sv.setTextColor(0xFF999999);
                    sv.setGravity(Gravity.END);
                    sv.setLineSpacing(0, 1.1f);
                    row.addView(sv);
                }
                return row;
            }
        };

        final ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAdapter(la);
        popup.setAnchorView(toolbarTitle);
        popup.setDropDownGravity(Gravity.START);
        popup.setVerticalOffset(dp(4));
        popup.setWidth(dp(320));
        popup.setModal(true);
        popup.setBackgroundDrawable(new ColorDrawable(bgColor));
        popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                                              int position, long id) {
                DrawerEntry e = entries.get(position);
                popup.dismiss();
                if (e.kind == DrawerEntry.KIND_ROOT) {
                    enterRootMode();
                    return;
                }
                if (e.dir != null && e.dir.exists()) {
                    exitSelectionMode();
                    if (archiveFile != null) exitArchive();
                    navigateTo(e.dir);
                } else {
                    toast("Thư mục không tồn tại");
                }
            }
        });
        popup.show();
    }

    /** True nếu entry là gốc bộ nhớ (bộ nhớ trong / thẻ SD) -> hiện dung lượng. */
    private boolean isStorageRoot(DrawerEntry e) {
        if (e == null || e.dir == null) return false;
        String label = e.label;
        return label.startsWith("Bộ nhớ") || label.startsWith("Thẻ SD")
                || label.startsWith("Trang chủ");
    }

    /** Chuỗi "đã dùng/tổng" cho một gốc bộ nhớ, vd "114.22GB/223.02GB". */
    private String storageStats(java.io.File dir) {
        try {
            android.os.StatFs sf = new android.os.StatFs(dir.getAbsolutePath());
            long blockSize, totalBlocks, availBlocks;
            if (android.os.Build.VERSION.SDK_INT >= 18) {
                blockSize = sf.getBlockSizeLong();
                totalBlocks = sf.getBlockCountLong();
                availBlocks = sf.getAvailableBlocksLong();
            } else {
                blockSize = sf.getBlockSize();
                totalBlocks = sf.getBlockCount();
                availBlocks = sf.getAvailableBlocks();
            }
            long total = totalBlocks * blockSize;
            long used = (totalBlocks - availBlocks) * blockSize;
            return formatSizeShort(used) + "/" + formatSizeShort(total);
        } catch (Exception e) {
            return "";
        }
    }

    private String formatSizeShort(long bytes) {
        if (bytes >= 1073741824L) return String.format("%.2fGB", bytes / 1073741824.0);
        if (bytes >= 1048576L) return String.format("%.2fMB", bytes / 1048576.0);
        if (bytes >= 1024L) return String.format("%.2fKB", bytes / 1024.0);
        return bytes + "B";
    }

    // -------------------------------------------------- util

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        ShizukuHelper.removeOnBinderReceivedListener(shizukuReceived);
        ShizukuHelper.removeOnBinderDeadListener(shizukuDead);
        if (adapter != null) adapter.shutdown();
        super.onDestroy();
    }
}
