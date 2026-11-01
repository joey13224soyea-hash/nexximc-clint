package com.nexxi.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoneyUtil {
    private static final Pattern NUM = Pattern.compile("([\\d,]*\\.?\\d+)\\s*([KkMmBbTt]?)");

    /** Parses things like "1,500", "2.5K", "$3M", "1.2B". Returns NaN if no number found. */
    public static double parse(String s) {
        if (s == null) return Double.NaN;
        Matcher m = NUM.matcher(s.trim());
        if (!m.find()) return Double.NaN;
        double v;
        try {
            v = Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
        switch (m.group(2).toLowerCase()) {
            case "k": v *= 1e3; break;
            case "m": v *= 1e6; break;
            case "b": v *= 1e9; break;
            case "t": v *= 1e12; break;
            default: break;
        }
        return v;
    }

    public static String format(double v) {
        double a = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (a >= 1e12) return sign + "$" + trim(a / 1e12) + "T";
        if (a >= 1e9) return sign + "$" + trim(a / 1e9) + "B";
        if (a >= 1e6) return sign + "$" + trim(a / 1e6) + "M";
        if (a >= 1e3) return sign + "$" + trim(a / 1e3) + "K";
        return sign + "$" + trim(a);
    }

    public static String formatSigned(double v) {
        return (v >= 0 ? "+" : "") + format(v);
    }

    private static String trim(double d) {
        String s = String.format("%.2f", d);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    private static final String SMALL = "ᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";
    private static final String LATIN = "abcdefghijklmnopqrstuvwxyz";

    /** Converts small-caps fonts some servers use into plain letters so regexes match. */
    public static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            int i = SMALL.indexOf(c);
            sb.append(i >= 0 ? LATIN.charAt(i) : c);
        }
        return sb.toString().trim();
    }
}
