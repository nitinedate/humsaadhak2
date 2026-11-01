package org.humsaadhak.shared.util;

import org.humsaadhak.shared.exception.DomainException;

import java.text.Normalizer;
import java.util.Locale;

/** Stable codes for reference data typed by administrators: "Fire-safety certificate" -> "FIRE_SAFETY_CERTIFICATE". */
public final class Codes {
    private Codes() {}

    public static String fromName(String name) {
        if (name == null || name.isBlank()) throw new DomainException("name-required", "Enter a name.");
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String code = ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty()) throw new DomainException("name-invalid", "Use letters or numbers in the name.");
        return code.length() > 40 ? code.substring(0, 40).replaceAll("_+$", "") : code;
    }
}
