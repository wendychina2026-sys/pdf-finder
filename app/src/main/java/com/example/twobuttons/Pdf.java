package com.example.twobuttons;

class Pdf {
    final String name;
    final String path;
    final long size;   // bytes

    Pdf(String name, String path, long size) {
        this.name = name;
        this.path = path;
        this.size = size;
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
