package dev.nightbeam.donutessentials.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Money amounts typed by players ({@code 2.5k}, {@code 1,000}, {@code 3m}) and short display forms. */
public final class Amounts {

    private static final String[] SUFFIXES = {"", "k", "m", "b", "t"};

    private Amounts() {
    }

    /** Parses a positive amount, or returns -1 when the text is not one. Rounds to cents. */
    public static double parse(String raw) {
        if (raw == null) {
            return -1;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "").replace("$", "");
        if (text.isEmpty()) {
            return -1;
        }
        double multiplier = 1;
        char last = text.charAt(text.length() - 1);
        int power = switch (last) {
            case 'k' -> 1;
            case 'm' -> 2;
            case 'b' -> 3;
            case 't' -> 4;
            default -> 0;
        };
        if (power > 0) {
            text = text.substring(0, text.length() - 1);
            multiplier = Math.pow(1000, power);
        }
        try {
            BigDecimal value = new BigDecimal(text).multiply(BigDecimal.valueOf(multiplier)).setScale(2, RoundingMode.DOWN);
            double result = value.doubleValue();
            return result > 0 && Double.isFinite(result) ? result : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** {@code 12500} → {@code 12.5k}; below 1000 at most two decimals. */
    public static String shortForm(double amount) {
        double value = Math.abs(amount);
        int index = 0;
        while (value >= 1000 && index < SUFFIXES.length - 1) {
            value /= 1000;
            index++;
        }
        DecimalFormat format = new DecimalFormat(index == 0 ? "#,##0.##" : "#,##0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.DOWN);
        return (amount < 0 ? "-" : "") + format.format(value) + SUFFIXES[index];
    }

    /** {@code 12500} → {@code 12,500}, keeping cents when there are any. */
    public static String full(double amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(amount);
    }
}
