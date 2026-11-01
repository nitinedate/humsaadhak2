package org.humsaadhak.documents.util;

import java.util.Locale;
import java.util.Map;
import org.humsaadhak.shared.exception.DomainException;

/** What may be uploaded: allow-listed types, checked by extension AND by the file's first bytes. Pure. */
public final class UploadPolicy {
    public static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Map<String, String> ALLOWED = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "mp4", "video/mp4");

    private UploadPolicy() {}

    /** @return the content type to store */
    public static String check(String fileName, long size, byte[] head) {
        if (size <= 0) throw new DomainException("empty-file", "The file is empty.");
        if (size > MAX_BYTES) throw new DomainException("too-large", "Files can be up to 20 MB.");
        String ext = fileName == null || !fileName.contains(".") ? "" :
                fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = ALLOWED.get(ext);
        if (type == null) throw new DomainException("type-not-allowed", "Upload a PDF, PNG, JPG or MP4 file.");
        if (!magicMatches(type, head)) throw new DomainException("content-mismatch", "The file content does not match its extension.");
        return type;
    }

    public static boolean magicMatches(String type, byte[] h) {
        if (h == null || h.length < 4) return false;
        return switch (type) {
            case "application/pdf" -> h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            case "image/png" -> (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "image/jpeg" -> (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8;
            case "video/mp4" -> h.length >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
            default -> false;
        };
    }
}
