package com.kaizen.tool;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * M1 — ListView adapter for FileItem rows (R.layout.file_row).
 * Also owns the static sort + icon-mapping helpers.
 */
public class FileAdapter extends BaseAdapter {

    public static final int SORT_NAME_ASC  = 0;
    public static final int SORT_NAME_DESC = 1;
    public static final int SORT_DATE_NEW  = 2;
    public static final int SORT_DATE_OLD  = 3;
    public static final int SORT_SIZE_ASC  = 4;
    public static final int SORT_SIZE_DESC = 5;
    public static final int SORT_TYPE      = 6;

    private final Context context;
    private final List<FileItem> items;
    private Set<String> selected = new HashSet<String>();
    private boolean selectionMode = false;
    private boolean gridMode = false;
    private String filter = null;
    private final List<Integer> visibleIdx = new ArrayList<Integer>();
    private final ThumbnailLoader thumbnails;
    /** v1.1-m1: cache app icon theo package name (cho /Android/data) */
    private final java.util.Map<String, android.graphics.drawable.Drawable> appIconCache =
            new java.util.HashMap<String, android.graphics.drawable.Drawable>();

    public FileAdapter(Context context, List<FileItem> items) {
        this.context = context;
        this.items = items;
        this.thumbnails = new ThumbnailLoader(context);
        rebuildVisible();
    }

    /** v1.1-m1: lấy icon app từ package name (có cache) */
    private android.graphics.drawable.Drawable getAppIcon(String packageName) {
        if (packageName == null) return null;
        android.graphics.drawable.Drawable cached = appIconCache.get(packageName);
        if (cached != null) return cached;
        try {
            android.graphics.drawable.Drawable icon = context.getPackageManager()
                    .getApplicationIcon(packageName);
            // Cache cả null (dùng placeholder để tránh lookup lại)
            appIconCache.put(packageName, icon);
            return icon;
        } catch (Exception e) {
            return null;
        }
    }

    /** Release background threads + bitmap cache (call from Activity.onDestroy). */
    public void shutdown() {
        thumbnails.shutdown();
    }

    public void setSelected(Set<String> paths) {
        this.selected = paths;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    /** M4: toggle list/grid presentation. */
    public void setGridMode(boolean gridMode) {
        this.gridMode = gridMode;
        notifyDataSetChanged();
    }

    public boolean isGridMode() {
        return gridMode;
    }

    /** M4: live name filter (null/empty = no filter). */
    public void setFilter(String q) {
        if (q == null || q.trim().isEmpty()) {
            filter = null;
        } else {
            filter = q.trim().toLowerCase();
        }
        notifyDataSetChanged();
    }

    private void rebuildVisible() {
        visibleIdx.clear();
        for (int i = 0; i < items.size(); i++) {
            FileItem it = items.get(i);
            if (filter == null || it.name.toLowerCase().contains(filter)) {
                visibleIdx.add(i);
            }
        }
    }

    @Override
    public void notifyDataSetChanged() {
        rebuildVisible();
        super.notifyDataSetChanged();
    }

    public FileItem getItemAt(int position) {
        return items.get(visibleIdx.get(position));
    }

    @Override public int getCount() { return visibleIdx.size(); }

    @Override public Object getItem(int position) { return items.get(visibleIdx.get(position)); }

    @Override public long getItemId(int position) { return position; }

    // ---------- sorting ----------

    public static void sortFiles(List<FileItem> items, int mode) {
        Collections.sort(items, new FileItemComparator(mode));
    }

    private static class FileItemComparator implements Comparator<FileItem> {
        private final int mode;
        FileItemComparator(int mode) { this.mode = mode; }

        @Override
        public int compare(FileItem a, FileItem b) {
            // folders always first, except when the mode itself is type-based
            // (SORT_TYPE also puts folders first, then sorts by extension/name)
            if (a.isDir != b.isDir) {
                return a.isDir ? -1 : 1;
            }
            switch (mode) {
                case SORT_NAME_ASC:
                    return compareNames(a, b);
                case SORT_NAME_DESC:
                    return compareNames(b, a);
                case SORT_DATE_NEW:
                    return Long.compare(b.lastModified, a.lastModified);
                case SORT_DATE_OLD:
                    return Long.compare(a.lastModified, b.lastModified);
                case SORT_SIZE_ASC:
                    return Long.compare(a.size, b.size);
                case SORT_SIZE_DESC:
                    return Long.compare(b.size, a.size);
                case SORT_TYPE:
                default: {
                    int e = extension(a.name).compareTo(extension(b.name));
                    if (e != 0) return e;
                    return compareNames(a, b);
                }
            }
        }

        private static int compareNames(FileItem a, FileItem b) {
            return a.name.compareToIgnoreCase(b.name);
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    // ---------- icon mapping ----------

    public static int iconResFor(FileItem item) {
        if (item.isDir) {
            return R.drawable.ic_folder_rem;
        }
        String ext = extension(item.name);
        if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                || ext.equals("gif") || ext.equals("webp") || ext.equals("bmp")) {
            return R.drawable.ic_file_image;
        }
        if (ext.equals("mp4") || ext.equals("mkv") || ext.equals("avi")
                || ext.equals("mov")) {
            return R.drawable.ic_file_video;
        }
        if (ext.equals("mp3") || ext.equals("wav") || ext.equals("flac")
                || ext.equals("ogg")) {
            return R.drawable.ic_file_audio;
        }
        if (ext.equals("apk") || ext.equals("apks")
                || ext.equals("xapk") || ext.equals("apkm")) {
            return R.drawable.ic_file_apk;
        }
        if (ext.equals("7z")) {
            return R.drawable.ic_file_7z;
        }
        if (ext.equals("tar") || ext.equals("tgz")
                || item.name.toLowerCase().endsWith(".tar.gz")) {
            return R.drawable.ic_file_tar;
        }
        if (ext.equals("zip") || ext.equals("rar")
                || ext.equals("gz") || ext.equals("bz2") || ext.equals("xz")) {
            return R.drawable.ic_file_archive;
        }
        if (ext.equals("txt") || ext.equals("md")) {
            return R.drawable.ic_file_text;
        }
        return R.drawable.ic_file_default;
    }

    // ---------- rows ----------

    private static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView info;
        CheckBox check;
    }

    /** v1.1-m2: bấm vào icon để chọn nhanh (không vào folder/mở file). */
    public interface OnIconClickListener {
        void onIconClick(FileItem item);
    }

    private OnIconClickListener iconClickListener;

    public void setOnIconClickListener(OnIconClickListener l) {
        this.iconClickListener = l;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final FileItem item = items.get(visibleIdx.get(position));
        if (gridMode) {
            return getGridView(item, convertView, parent);
        }
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.file_row, parent, false);
            holder = new ViewHolder();
            holder.icon = (ImageView) convertView.findViewById(R.id.icon);
            holder.name = (TextView) convertView.findViewById(R.id.name);
            holder.info = (TextView) convertView.findViewById(R.id.info);
            holder.check = (CheckBox) convertView.findViewById(R.id.check);
            // row click is handled by the ListView listener; the checkbox
            // itself must not steal taps in selection mode
            holder.check.setFocusable(false);
            holder.check.setClickable(false);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        // v1.1-m2: bấm icon = chọn nhanh
        holder.icon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (iconClickListener != null) iconClickListener.onIconClick(item);
            }
        });

        // v1.1-m1: nếu là folder app (/Android/data), hiện icon app + tên app
        if (item.packageName != null) {
            android.graphics.drawable.Drawable appIcon = getAppIcon(item.packageName);
            if (appIcon != null) {
                holder.icon.setImageDrawable(appIcon);
            } else {
                thumbnails.load(item, holder.icon, iconResFor(item));
            }
        } else {
            // Async thumbnail for image/video/apk, fallback icon otherwise
            thumbnails.load(item, holder.icon, iconResFor(item));
        }
        holder.name.setText(item.name);
        if (item.appLabel != null) {
            // Hiện tên app như MT Manager (thay vì "Trống")
            holder.info.setText(item.appLabel);
        } else if (item.isDir) {
            holder.info.setText(childCountText(item.childCount));
        } else {
            holder.info.setText(
                    FileOps.formatSize(item.size) + "  •  "
                            + FileOps.formatDate(item.lastModified));
        }
        holder.check.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.check.setChecked(selected.contains(item.path()));

        // pink highlight for selected rows ("full hồng")
        if (selected.contains(item.path())) {
            convertView.setBackgroundColor(
                    context.getResources().getColor(R.color.selectedBackground));
        } else {
            convertView.setBackgroundColor(0x00000000);
        }

        return convertView;
    }

    private String childCountText(int childCount) {
        if (childCount < 0) return "—";
        if (childCount == 0) return "Trống";
        if (childCount == 1) return "1 mục";
        return childCount + " mục";
    }

    // ---------- M4: grid rows ----------

    private static class GridHolder {
        View root;
        ImageView icon;
        TextView name;
    }

    private View getGridView(FileItem item, View convertView, ViewGroup parent) {
        GridHolder h;
        if (convertView == null || !(convertView.getTag() instanceof GridHolder)) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.grid_item, parent, false);
            h = new GridHolder();
            h.root = convertView.findViewById(R.id.grid_root);
            h.icon = (ImageView) convertView.findViewById(R.id.icon);
            h.name = (TextView) convertView.findViewById(R.id.name);
            convertView.setTag(h);
        } else {
            h = (GridHolder) convertView.getTag();
        }
        // v1.1-m2: bấm icon = chọn nhanh
        final FileItem gridItem = item;
        h.icon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (iconClickListener != null) iconClickListener.onIconClick(gridItem);
            }
        });
        // v1.1-m1: nếu là folder app, hiện icon app
        if (item.packageName != null) {
            android.graphics.drawable.Drawable appIcon = getAppIcon(item.packageName);
            if (appIcon != null) {
                h.icon.setImageDrawable(appIcon);
            } else {
                thumbnails.load(item, h.icon, iconResFor(item));
            }
        } else {
            thumbnails.load(item, h.icon, iconResFor(item));
        }
        h.name.setText(item.name);
        if (selected.contains(item.path())) {
            h.root.setBackgroundColor(
                    context.getResources().getColor(R.color.selectedBackground));
        } else {
            h.root.setBackgroundColor(0x00000000);
        }
        return convertView;
    }
}
