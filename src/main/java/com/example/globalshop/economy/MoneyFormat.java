package com.example.globalshop.economy;

import java.util.OptionalLong;

/**
 * Converts between the stored representation of money and human readable text.
 *
 * <p>A balance is always a {@code long} count of the <em>smallest currency unit</em>
 * (minor units). Nothing in this mod ever stores money as a {@code double} or
 * {@code float}, so no rounding error can appear in a balance, and
 * {@code 0.1 + 0.2} style drift is impossible by construction. This class is the
 * single place where that representation meets text.
 *
 * <p>The size of the minor unit comes from {@code economy.minorUnitsPerMajor}.
 * Only powers of ten are honoured; any other value is rounded down to the nearest
 * power of ten so that formatting and parsing stay exact.
 */
public final class MoneyFormat {

    /** Minor units per major unit. 100 means two decimal places. */
    private static volatile int scale = 100;

    /** Number of decimal places shown, derived from {@link #scale}. */
    private static volatile int decimals = 2;

    /** Display name of the currency, e.g. "Gold". */
    private static volatile String currencyName = "Gold";

    private MoneyFormat() {
    }

    /**
     * Applies configuration. Safe to call again at any time (for example from a
     * config reload).
     *
     * @param minorUnitsPerMajor how many minor units make one major unit
     * @param name               display name of the currency
     */
    public static void configure(int minorUnitsPerMajor, String name) {
        int per = Math.max(1, minorUnitsPerMajor);
        int digits = 0;
        long s = 1L;
        while (s * 10L <= per && s <= 1_000_000_000L) {
            s *= 10L;
            digits++;
        }
        scale = (int) s;
        decimals = digits;
        currencyName = (name == null || name.isBlank()) ? "Gold" : name;
    }

    public static int scale() {
        return scale;
    }

    public static int decimals() {
        return decimals;
    }

    public static String currencyName() {
        return currencyName;
    }

    /** Formats minor units as a plain number, e.g. {@code 1234 -> "12.34"}. */
    public static String format(long minorUnits) {
        int s = scale;
        int d = decimals;
        StringBuilder sb = new StringBuilder(24);
        long value = minorUnits;
        if (value < 0) {
            sb.append('-');
            // Long.MIN_VALUE has no positive counterpart. It is far outside any
            // balance this mod allows, but clamp rather than overflow.
            value = (value == Long.MIN_VALUE) ? Long.MAX_VALUE : -value;
        }
        long major = value / s;
        long fraction = value % s;
        sb.append(major);
        if (d > 0) {
            sb.append('.');
            String frac = Long.toString(fraction);
            for (int i = frac.length(); i < d; i++) {
                sb.append('0');
            }
            sb.append(frac);
        }
        return sb.toString();
    }

    /** Formats minor units with the currency name, e.g. {@code "12.34 Gold"}. */
    public static String formatWithName(long minorUnits) {
        return format(minorUnits) + " " + currencyName;
    }

    /**
     * A short form for places with limited room, such as the window header.
     *
     * <p>Everyday amounts are shown exactly. Once the major-unit part reaches a million the
     * value is abbreviated — {@code 1.2M}, {@code 3.4B}, {@code 5.6T} — so the text has a
     * bounded width and cannot grow sideways into whatever sits next to it. That bound is
     * the point: an exact rendering of a large balance is unbounded in width, and a shop
     * window is not.
     *
     * <p>{@link #format(long)} remains the exact form, and is what commands, the trade log
     * and the detail panel use.
     */
    public static String formatCompact(long minorUnits) {
        int s = scale;
        long major = minorUnits / s;
        boolean negative = major < 0;
        long absolute = (major == Long.MIN_VALUE) ? Long.MAX_VALUE : Math.abs(major);
        if (absolute < 1_000_000L) {
            return format(minorUnits);
        }
        long divisor;
        String suffix;
        if (absolute >= 1_000_000_000_000L) {
            divisor = 1_000_000_000_000L;
            suffix = "T";
        } else if (absolute >= 1_000_000_000L) {
            divisor = 1_000_000_000L;
            suffix = "B";
        } else {
            divisor = 1_000_000L;
            suffix = "M";
        }
        long whole = absolute / divisor;
        long tenth = (absolute % divisor) * 10L / divisor;
        return (negative ? "-" : "") + whole + "." + tenth + suffix;
    }

    /**
     * Parses user input into minor units.
     *
     * <p>Accepts {@code "12"}, {@code "12.34"}, {@code ".5"}, {@code "1,234.50"}.
     * Rejects negative values, junk, and any value with more precision than the
     * currency can represent (dropping digits would silently lose money).
     *
     * @return the amount in minor units, or empty if the text is not a valid amount
     */
    public static OptionalLong parse(String raw) {
        if (raw == null) {
            return OptionalLong.empty();
        }
        int s = scale;
        int d = decimals;

        String text = raw.trim().replace(",", "").replace("_", "").replace(" ", "");
        if (text.isEmpty() || text.startsWith("-")) {
            return OptionalLong.empty();
        }
        if (text.startsWith("+")) {
            text = text.substring(1);
            if (text.isEmpty()) {
                return OptionalLong.empty();
            }
        }

        int dot = text.indexOf('.');
        String majorPart = (dot < 0) ? text : text.substring(0, dot);
        String fractionPart = (dot < 0) ? "" : text.substring(dot + 1);
        if (majorPart.isEmpty() && fractionPart.isEmpty()) {
            return OptionalLong.empty();
        }
        // A second '.' or any non-digit is invalid.
        if (!isDigits(majorPart) || !isDigits(fractionPart)) {
            return OptionalLong.empty();
        }
        if (fractionPart.length() > d) {
            for (int i = d; i < fractionPart.length(); i++) {
                if (fractionPart.charAt(i) != '0') {
                    return OptionalLong.empty();
                }
            }
            fractionPart = fractionPart.substring(0, d);
        }

        try {
            long major = majorPart.isEmpty() ? 0L : Long.parseLong(majorPart);
            long minor = 0L;
            if (!fractionPart.isEmpty()) {
                minor = Long.parseLong(fractionPart);
                for (int i = fractionPart.length(); i < d; i++) {
                    minor = Math.multiplyExact(minor, 10L);
                }
            }
            long total = Math.addExact(Math.multiplyExact(major, (long) s), minor);
            return total < 0 ? OptionalLong.empty() : OptionalLong.of(total);
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static boolean isDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
