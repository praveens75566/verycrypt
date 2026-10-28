package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.AppIcons

// Standalone Material icon definitions replacing material-icons-extended (saving ~60MB of DEX bytecode)
val Icons.Filled.Shield: ImageVector
    get() = AppIcons.make("Shield", "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z")

val Icons.Filled.Security: ImageVector
    get() = AppIcons.make("Security", "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z")

val Icons.Filled.Key: ImageVector
    get() = AppIcons.make("Key", "M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z")

val Icons.Filled.Visibility: ImageVector
    get() = AppIcons.make("Visibility", "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z")

val Icons.Filled.VisibilityOff: ImageVector
    get() = AppIcons.make("VisibilityOff", "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z")

val Icons.Filled.Tag: ImageVector
    get() = AppIcons.make("Tag", "M20 10V8h-4V4h-2v4h-4V4H8v4H4v2h4v4H4v2h4v4h2v-4h4v4h2v-4h4v-2h-4v-4h4zm-6 4h-4v-4h4v4z")

val Icons.Filled.Folder: ImageVector
    get() = AppIcons.make("Folder", "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z")

val Icons.Filled.FolderOpen: ImageVector
    get() = AppIcons.make("FolderOpen", "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z")

val Icons.Filled.FolderZip: ImageVector
    get() = AppIcons.make("FolderZip", "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6 10h2v-2h-2v-2h2v-2h-2V8h2V6h-2v2h-2v2h2v2h-2v2h2v2z")

val Icons.Filled.ContentCopy: ImageVector
    get() = AppIcons.make("ContentCopy", "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z")

val Icons.Filled.ContentPaste: ImageVector
    get() = AppIcons.make("ContentPaste", "M19 2h-4.18C14.4 .84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm7 18H5V4h2v3h10V4h2v16z")

val Icons.Filled.CheckCircle: ImageVector
    get() = AppIcons.make("CheckCircle", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z")

val Icons.Filled.Autorenew: ImageVector
    get() = AppIcons.make("Autorenew", "M12 6v3l4-4-4-4v3c-4.42 0-8 3.58-8 8 0 1.57.46 3.03 1.24 4.26L6.7 14.8c-.45-.83-.7-1.79-.7-2.8 0-3.31 2.69-6 6-6zm6.76 1.74L17.3 9.2c.44.84.7 1.79.7 2.8 0 3.31-2.69 6-6 6v-3l-4 4 4 4v-3c4.42 0 8-3.58 8-8 0-1.57-.46-3.03-1.24-4.26z")

val Icons.Filled.Download: ImageVector
    get() = AppIcons.make("Download", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")

val Icons.Filled.FileUpload: ImageVector
    get() = AppIcons.make("FileUpload", "M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z")

val Icons.Filled.FileOpen: ImageVector
    get() = AppIcons.make("FileOpen", "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 14.5l-3.5 3.5-1.42-1.42L12.67 17H7v-2h5.67l-1.59-1.58L12.5 12l3.5 3.5v1zM13 9V3.5L18.5 9H13z")

val Icons.Filled.BookmarkAdd: ImageVector
    get() = AppIcons.make("BookmarkAdd", "M21 7h-2v2h-2V7h-2V5h2V3h2v2h2v2zm-4 4.5V19l-7-3-7 3V5c0-1.1.9-2 2-2h7c-.63.84-1 1.87-1 3 0 2.76 2.24 5 5 5 .74 0 1.43-.16 2.06-.45l.94-.05z")

val Icons.Filled.Speed: ImageVector
    get() = AppIcons.make("Speed", "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.43zM10.59 15.41a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z")

val Icons.Filled.PushPin: ImageVector
    get() = AppIcons.make("PushPin", "M16 9V4l1 0c.55 0 1-.45 1-1s-.45-1-1-1H7c-.55 0-1 .45-1 1s.45 1 1 1l1 0v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3z")

val Icons.Filled.SelectAll: ImageVector
    get() = AppIcons.make("SelectAll", "M18 5h2V3h-2v2zm0 4h2V7h-2v2zm0 8h2v-2h-2v2zm-8 4h2v-2h-2v2zm0-16h2V3h-2v2zm8 8h2v-2h-2v2zm-4 8h2v-2h-2v2zm4 0c1.1 0 2-.9 2-2h-2v2zm-8-8h4v-4h-4v4zm0 8h2v-2h-2v2zM3 5c0-1.1.9-2 2-2h2v2H5v2H3V5zm0 8h2v-2H3v2zm0 8c0 1.1.9 2 2 2h2v-2H5v-2H3v2zm0-4h2v-2H3v2zm4 4h2v-2H7v2zm-4-8h2V9H3v2zm14 12h2v-2h-2v2zm0-16h2V3c-1.1 0-2 .9-2 2z")

val Icons.Filled.Archive: ImageVector
    get() = AppIcons.make("Archive", "M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z")

val Icons.Filled.Unarchive: ImageVector
    get() = AppIcons.make("Unarchive", "M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 9.5l5.5 5.5H14v2h-4v-2H6.5L12 9.5zM5.12 5l.81-1h12l.94 1H5.12z")

val Icons.Filled.LockOpen: ImageVector
    get() = AppIcons.make("LockOpen", "M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm0 12H6V10h12v10z")

val Icons.Filled.AutoAwesome: ImageVector
    get() = AppIcons.make("AutoAwesome", "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z")

val Icons.Filled.CleaningServices: ImageVector
    get() = AppIcons.make("CleaningServices", "M16 11h-1V3c0-.55-.45-1-1-1h-4c-.55 0-1 .45-1 1v8H8c-2.76 0-5 2.24-5 5v5c0 .55.45 1 1 1h16c.55 0 1-.45 1-1v-5c0-2.76-2.24-5-5-5zm-5-7h2v7h-2V4zm9 16H4v-4c0-1.65 1.35-3 3-3h10c1.65 0 3 1.35 3 3v4z")

val Icons.Filled.Contrast: ImageVector
    get() = AppIcons.make("Contrast", "M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10zm1-17.93c3.94.49 7 3.85 7 7.93s-3.06 7.44-7 7.93V4.07z")

val Icons.Filled.Brightness4: ImageVector
    get() = AppIcons.make("Brightness4", "M20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 20H20v-4.69L23.31 12 20 8.69zM12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 6-2.69 6-6 6zm0-10c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4z")

val Icons.Filled.CheckBox: ImageVector
    get() = AppIcons.make("CheckBox", "M19 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.11 0 2-.9 2-2V5c0-1.1-.89-2-2-2zm-9 14l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z")

val Icons.Filled.CheckBoxOutlineBlank: ImageVector
    get() = AppIcons.make("CheckBoxOutlineBlank", "M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z")

val Icons.Filled.Checklist: ImageVector
    get() = AppIcons.make("Checklist", "M22 7h-9v2h9V7zm0 8h-9v2h9v-2zM5.54 11L2 7.46l1.41-1.41 2.12 2.12 4.24-4.24 1.41 1.41L5.54 11zm0 8L2 15.46l1.41-1.41 2.12 2.12 4.24-4.24 1.41 1.41L5.54 19z")

val Icons.Filled.ChevronRight: ImageVector
    get() = AppIcons.make("ChevronRight", "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z")

val Icons.Filled.Description: ImageVector
    get() = AppIcons.make("Description", "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z")

val Icons.Filled.DriveFileMove: ImageVector
    get() = AppIcons.make("DriveFileMove", "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6 12v-3h-4v-2h4V8l5 5-5 5z")

val Icons.Filled.Error: ImageVector
    get() = AppIcons.make("Error", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z")

val Icons.Filled.ErrorOutline: ImageVector
    get() = AppIcons.make("ErrorOutline", "M11 15h2v2h-2zm0-8h2v6h-2zm1-5C6.47 2 2 6.5 2 12a10 10 0 0 0 10 10 10 10 0 0 0 10-10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8.01 8.01 0 0 1-8 8z")

val Icons.Filled.ExpandLess: ImageVector
    get() = AppIcons.make("ExpandLess", "M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z")

val Icons.Filled.ExpandMore: ImageVector
    get() = AppIcons.make("ExpandMore", "M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z")

val Icons.Filled.Fingerprint: ImageVector
    get() = AppIcons.make("Fingerprint", "M17.81 4.47c-.08 0-.16-.02-.23-.06C15.66 3.42 14 3 12.01 3c-1.98 0-3.86.47-5.57 1.41-.24.13-.54.04-.68-.2-.13-.24-.04-.55.2-.68C7.82 2.52 9.86 2 12.01 2c2.13 0 3.99.47 6.03 1.52.25.13.34.43.21.67-.09.18-.26.28-.44.28zM3.5 9.72c-.1 0-.2-.03-.29-.09-.23-.16-.28-.47-.12-.7.99-1.4 2.25-2.5 3.75-3.27C9.9 4.04 12.07 4 14.24 4.54c1.86.46 3.51 1.42 4.91 2.87.19.2.19.51-.01.71-.2.19-.51.19-.71-.01-1.28-1.32-2.81-2.2-4.52-2.62-1.99-.49-3.99-.46-5.87.52-1.37.71-2.53 1.72-3.44 3-.1.14-.26.21-.42.21zm7.04 12.06c-.15 0-.29-.06-.39-.17-.18-.21-.16-.53.05-.71 2.21-1.94 3.44-4.53 3.44-7.29 0-2.55-1.92-4.63-4.28-4.63-2.36 0-4.28 2.08-4.28 4.63 0 1.25.43 2.45 1.22 3.4.17.21.14.52-.07.69-.21.17-.52.14-.69-.07C4.6 16.48 4.1 15.08 4.1 13.61c0-3.1 2.37-5.63 5.28-5.63 2.91 0 5.28 2.53 5.28 5.63 0 3.14-1.4 6.09-3.92 8.3-.1.08-.23.13-.37.13zM12 18c-.28 0-.5-.22-.5-.5 0-1.89.85-3.65 2.33-4.83.21-.17.53-.14.7.07.17.21.14.53-.07.7-1.25 1-1.96 2.49-1.96 4.06 0 .28-.22.5-.5.5z")

val Icons.Filled.FormatSize: ImageVector
    get() = AppIcons.make("FormatSize", "M9 4v3h5v12h3V7h5V4H9zm-6 8h3v7h3v-7h3V9H3v3z")

val Icons.Filled.TextFields: ImageVector
    get() = AppIcons.make("TextFields", "M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z")

val Icons.Filled.Timer: ImageVector
    get() = AppIcons.make("Timer", "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z")

val Icons.Filled.Tune: ImageVector
    get() = AppIcons.make("Tune", "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z")

val Icons.Filled.VerifiedUser: ImageVector
    get() = AppIcons.make("VerifiedUser", "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-2 16l-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z")

val Icons.Filled.Vibration: ImageVector
    get() = AppIcons.make("Vibration", "M0 15h2V9H0v6zm3 2h2V7H3v10zm19-8v6h2V9h-2zm-3 8h2V7h-2v10zM16.5 3h-9C6.67 3 6 3.67 6 4.5v15c0 .83.67 1.5 1.5 1.5h9c.83 0 1.5-.67 1.5-1.5v-15c0-.83-.67-1.5-1.5-1.5zM16 19H8V5h8v14z")

val Icons.Filled.WrapText: ImageVector
    get() = AppIcons.make("WrapText", "M4 19h6v-2H4v2zM20 5H4v2h16V5zm-3 6H4v2h13.25c1.1 0 2 .9 2 2s-.9 2-2 2H15v-2l-3 3 3 3v-2h2c2.21 0 4-1.79 4-4s-1.79-4-4-4z")

val Icons.Filled.Assessment: ImageVector
    get() = AppIcons.make("Assessment", "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z")
