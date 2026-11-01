package org.humsaadhak.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** ₹ with Indian digit grouping (₹12,40,000). Shared so every module formats money the same way. */
public final class IndianRupees {
    private IndianRupees() {}

    public static String format(BigDecimal amount) {
        long v = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        String s = Long.toString(Math.abs(v));
        if (s.length() > 3) {
            String last3 = s.substring(s.length() - 3);
            String rest = s.substring(0, s.length() - 3);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                if (i > 0 && (rest.length() - i) % 2 == 0) b.append(',');
                b.append(rest.charAt(i));
            }
            s = b + "," + last3;
        }
        return (v < 0 ? "-₹" : "₹") + s;
    }

    public static String format(double amount) { return format(BigDecimal.valueOf(amount)); }
}
