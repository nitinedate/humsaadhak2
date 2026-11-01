package org.humsaadhak.shared.util;

import java.util.List;

/**
 * CSV for spreadsheet users (RFC 4180): quoted where needed, CRLF line ends and a UTF-8 byte-order mark so Excel shows
 * ₹ and Indian names correctly. Cells starting with = + - @ are prefixed with ' so a spreadsheet never runs them as
 * formulas (CSV injection).
 */
public final class Csv {
    private Csv() {}

    public static String of(List<List<String>> rows) {
        StringBuilder b = new StringBuilder("\uFEFF");
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) b.append(',');
                b.append(cell(row.get(i)));
            }
            b.append("\r\n");
        }
        return b.toString();
    }

    public static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !isNumber(v)) v = "'" + v;
        boolean quote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        return quote ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }

    private static boolean isNumber(String v) { return v.matches("-?\\d+(\\.\\d+)?"); }
}
