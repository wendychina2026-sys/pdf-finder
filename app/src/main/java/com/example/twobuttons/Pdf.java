package com.example.twobuttons;

/** A file found by the scanner: a PDF, an image or a video (the class keeps its old name). */
class Pdf {
    static final int PDF = 0, IMAGE = 1, VIDEO = 2;

    final String name;
    final String path;
    final long size;   // bytes
    final int kind;    // PDF / IMAGE / VIDEO

    Pdf(String name, String path, long size) {
        this(name, path, size, PDF);
    }

    Pdf(String name, String path, long size, int kind) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.kind = kind;
    }

    /** PDF, image or video from the file name; -1 for anything else. */
    static int kindOf(String fileName) {
        String n = fileName.toLowerCase(java.util.Locale.ROOT);
        int dot = n.lastIndexOf('.');
        if (dot < 0) return -1;
        String e = n.substring(dot + 1);
        switch (e) {
            case "pdf": return PDF;
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "bmp": case "heic": case "heif":
                return IMAGE;
            case "mp4": case "mkv": case "3gp": case "webm": case "avi": case "mov": case "m4v":
                return VIDEO;
            default: return -1;
        }
    }

    String mime() {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String e = dot < 0 ? "" : n.substring(dot + 1);
        String m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
        if (m != null) return m;
        return kind == PDF ? "application/pdf" : kind == IMAGE ? "image/*" : "video/*";
    }

    /** 532 B / 48.3 KB / 2.1 MB */
    static String formatSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", b / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.US, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0));
    }
}
