package com.example.twobuttons;

/**
 * What each new version of the app adds (Settings > Change log). Newest first.
 * Add a new block at the TOP of ENTRIES with every update that is pushed.
 */
final class ChangeLog {
    static final class Entry {
        final String date, title;
        final String[] items;
        Entry(String date, String title, String... items) {
            this.date = date;
            this.title = title;
            this.items = items;
        }
    }

    private ChangeLog() {}

    static final Entry[] ENTRIES = {
        new Entry("28 Oct 2026", "Check for update from GitHub; new splash screen",
            "Settings > App > Check for update: looks on GitHub for a newer build, downloads it and opens the installer. Shows \"Up to date\" when nothing is newer.",
            "Splash screen: new Lady Justice court scene, faster animation."),
        new Entry("27 Oct 2026", "One Attach to matter (with Create new case); change \"Multiple\" category",
            "Category marker: the Attach to Matter box at the bottom is removed. Attaching is now only in \"Change details\" (details card is always shown on top).",
            "Change details / Document Vault Edit: Attach to Matter now has \"+ Create new case\" (PDF files). OK opens New Case > Upload Legal Document with this file.",
            "Category \"Multiple (n categories)\" is now tappable in the Edit box: pick one category to set it on all marked pages; leave it to keep the page-wise categories."),
        new Entry("27 Oct 2026", "Category marker shows document details; one Edit box everywhere",
            "Category marker: an already marked or saved file now shows a details card on top: Document name, Attached to matter, Stage and Category.",
            "\"Change details\" on that card opens the same Edit box as the Document Vault Edit button: Document name, Category, Stage and Attach to matter. The card and page marks refresh when you press OK.",
            "Attached to matter never shows an empty value or []: it reads General Document Vault when no case is attached."),
        new Entry("27 Oct 2026", "Private data folder, auto backup, restore",
            "Data folder is now private to the app: file managers and other apps cannot see it. Old files move in once; marks, cases and documents follow.",
            "Settings > Save data as zip: whole folder in one zip, to any folder or Google Drive.",
            "Settings > Auto backup folder: choose a folder once; the app saves a fresh zip by itself (two files in turn).",
            "Settings > Restore from backup zip: use after reinstalling or on a new phone.",
            "Main page tells you once a day when the last backup is more than 3 days old. Uninstalling deletes the private folder.",
            "Extracted PDFs now go to PdfFinder/Extracted inside the private folder."),
        new Entry("27 Oct 2026", "Data folder sizes and marks; Document Vault cannot delete files",
            "Data folder: folder size in the top line now counts everything inside the folders.",
            "Data folder: image and Word files show Marked / Unmarked like PDFs, and the Marked / Unmarked filter works on them too.",
            "Document Vault: delete button removed; long-press now only offers Reset marking (the file is kept).",
            "Document Vault: \"Show details\" button is now \"Details\" so it fits."),
        new Entry("27 Oct 2026", "Edit button in Document Vault; no Save on upload card",
            "Document Vault cards now have an Edit button: change the Document name, Category, Stage and Attach to matter. The file itself and its name on disk are never changed.",
            "If a file's pages are marked in one category, changing the category re-marks all its pages; with several categories, change them from Show details.",
            "Upload Legal Document card: the Save button is removed, because the file is already stored in the app folder and Document Vault."),
        new Entry("27 Oct 2026", "Images and Word files now reach the app folder and Document Vault",
            "Fix: a file chosen with Tap to choose (image, PDF, Word) or a photo is now copied into the PdfFinder Documents folder when saved; before, it stayed in its old place and could vanish.",
            "A single image saved to General Document Vault is marked with its category and now shows in Document Vault; tapping it opens the image viewer.",
            "Earlier uploads that kept only the picker's link are copied into the app folder once (if the phone still allows access).",
            "Word files: share a .doc/.docx to the app and it opens Upload Legal Document (name, category, matter); saved Word files are copied to the app folder, marked as one page and listed in Document Vault (tap opens the Word viewer).",
            "Documents > Replace / Manage files: a replacement image or Word file is also copied into the app folder."),
        new Entry("26 Oct 2026", "Marked / unmarked in data folder; Documents is Document Vault",
            "Left menu: Document Vault entry removed.",
            "Data folder explorer: every PDF shows \"Marked file\" or \"Unmarked\".",
            "Data folder explorer: filter All / Marked / Unmarked."),
        new Entry("26 Oct 2026", "Replace resets marks; new files ask category; marks pending tag",
            "Documents > Manage files > Replace: page marks of that file are reset (a warning shows first) and the new PDF opens straight in the Category marker.",
            "Documents > Manage files > add files: asks if they are more files of this document's category or a new category; new category becomes its own document card.",
            "PDF documents with no marked page show \"Marks pending - tap to mark\" on the card."),
        new Entry("26 Oct 2026", "Add Document routes; Find files date sort and filter",
            "Documents > Add Document now asks: Scan and mark, or Add new case. The manual form that made empty records is gone.",
            "Find files: sort by date (newest first / oldest first) for PDFs, images and videos.",
            "Find files: filter by date (today, last 7 days, last 30 days, this year, or a From-To range).",
            "Find files: each row shows the file's last modified date."),
        new Entry("26 Oct 2026", "What's new popup on update",
            "After an update, a What's new popup shows the latest change-log entry once at startup.",
            "Full change log button opens Settings > Change log. Fresh installs show no popup."),
        new Entry("25 Oct 2026", "Find files: PDFs, images, videos; highlight; multi-select",
            "Find PDFs is now Find files: scans PDFs, images and videos, with All / PDF / Images / Videos filter chips.",
            "The scan skips the PdfFinder data folder (those PDFs are managed in Document Vault).",
            "WhatsApp and Telegram folders are scanned, including Android/media (needs All files access).",
            "Search text is highlighted (orange) in the file list and in the data-folder explorer.",
            "Document Vault folder (explorer): long-press > Select multiple, then Share or Delete several files at once; All / Cancel in the bar.",
            "Find files: Share several selected files at once, next to Delete.",
            "Find files: sort by name, size (small to large) or size (large to small).",
            "Find files: small thumbnail for every image and video (PDFs show an icon)."),
        new Entry("25 Oct 2026", "Document Vault, folders and change log",
            "Settings > Change log: this page, listing what every update adds.",
            "No duplicate PDFs: a file that is already in PdfFinder/Documents (same content, same or different name) is never stored again; Share / Open with reuses it.",
            "Marking even one page moves the PDF into PdfFinder/Documents automatically; its marks follow.",
            "Open folder (file information) marks your file in the folder: highlighted row, \"Your file\" tag, scrolled into view.",
            "Files saved while \"All files access\" was off now move from the app's private folder into PdfFinder/Documents once access is on; marks, matter and document records follow.",
            "Vault cards show the Document Name you gave the file, with its file icon; the real file name stays separate in the details.",
            "Type, size and date moved into the (i) File information dialog: type, size, last edited, marked pages, location, full path, Open folder and Copy path.",
            "Vault cards use the same card as Upload Legal Document: category, attached matter, marked / unmarked / total pages, page range per category, Show details, file path button, Share, Delete.",
            "Show details lists categories by first marked page, each with its page range, in smaller cards.",
            "Long-press a vault file: Delete file or Reset marking (the file leaves the vault).",
            "Settings > Open data folder: built-in file explorer for the PdfFinder folder (sort, search, new folder, rename, delete, share, details).",
            "Back up the full data folder to Google Drive, with a reminder notification."),
        new Entry("24 Oct 2026", "Category marker and PdfFinder data folder",
            "Marker: Attach to Matter with OK / Cancel and Create new case; the PDF opens in New Case > Upload Legal Document.",
            "Marker: confirmation when changing or removing a mark; the current page's own mark is listed in the picker.",
            "Upload popup: Document Category shows Multiple for marked pages, with edit, rename and delete on every option.",
            "New Case: \"Referred by\" box in the Client card.",
            "PdfFinder data folder on internal storage: automatic backup and restore of app data; new files are saved there."),
        new Entry("23 Oct 2026", "Share to app, New Case and court pickers",
            "PDF Share / Open with menu now includes this app and opens the PDF in the Category marker.",
            "Search in Sub-case type, Document Category and Stage; same-category upload alert for a client's case.",
            "New Case: Filing / Diary number and year on one row; Sub-case type and Case No. moved under CNR.",
            "Document groups: one card for all files of a save, with add / replace / delete / reorder.",
            "Court picker drills down (High Court list; District Court > State > District > Court complex).",
            "Opposing advocate has its own card (saved list, contacts or visiting card photo)."),
        new Entry("22 Oct 2026", "Camera scan and categories",
            "Camera: take many photos in a row, review with per-photo delete, then save as PDF or image.",
            "Reopen a PDF to remove pages or add photos.",
            "Document categories: Complaint > Return Complaint > Sent via Speed Post, level by level; Stage sits under Document Category.",
            "Colourful icons and one placeholder style across the app."),
    };
}
