package dev.nightbeam.donutessentials.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/** The {@code economy:} section of config.yml, read once per (re)load. Pure: no Bukkit calls, so it is unit-tested. */
record EconomySettings(double startingBalance, double maxBalance, Map<String, Double> balanceCaps, double minPayment, String symbol,
                       int decimals, String format, String nameSingular, String namePlural, boolean log, int baltopRefreshSeconds,
                       int baltopSize) {

    static EconomySettings defaults() {
        return new EconomySettings(100, 0, Map.of(), 0.01, "$", 2, "{symbol}{amount}", "dollar", "dollars", true, 60, 100);
    }

    static EconomySettings from(ConfigurationSection section) {
        EconomySettings fallback = defaults();
        if (section == null) {
            return fallback;
        }
        Map<String, Double> caps = new LinkedHashMap<>();
        ConfigurationSection tiers = section.getConfigurationSection("balance-caps");
        if (tiers != null) {
            for (String tier : tiers.getKeys(false)) {
                caps.put(tier.toLowerCase(Locale.ROOT), Math.max(0, tiers.getDouble(tier)));
            }
        }
        int decimals = Math.max(0, Math.min(4, section.getInt("currency.decimals", fallback.decimals())));
        return new EconomySettings(
                Math.max(0, section.getDouble("starting-balance", fallback.startingBalance())),
                Math.max(0, section.getDouble("max-balance", fallback.maxBalance())),
                Map.copyOf(caps),
                Math.max(0, section.getDouble("min-payment", fallback.minPayment())),
                section.getString("currency.symbol", fallback.symbol()),
                decimals,
                section.getString("currency.format", fallback.format()),
                section.getString("currency.name-singular", fallback.nameSingular()),
                section.getString("currency.name-plural", fallback.namePlural()),
                section.getBoolean("log", fallback.log()),
                Math.max(10, section.getInt("baltop.refresh-seconds", fallback.baltopRefreshSeconds())),
                Math.max(10, Math.min(1000, section.getInt("baltop.size", fallback.baltopSize()))));
    }

    /** Rounds to the currency's decimals (half-even, so repeated rounding does not drift). */
    double round(double amount) {
        if (!Double.isFinite(amount)) {
            return 0;
        }
        return BigDecimal.valueOf(amount).setScale(decimals, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** {@code $12,500.00} with the configured symbol, decimals and pattern. */
    String format(double amount) {
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.').append("0".repeat(decimals));
        }
        DecimalFormat number = new DecimalFormat(pattern.toString(), DecimalFormatSymbols.getInstance(Locale.ROOT));
        number.setRoundingMode(RoundingMode.HALF_EVEN);
        String text = number.format(Math.abs(amount));
        String result = format.replace("{symbol}", symbol == null ? "" : symbol).replace("{amount}", text);
        return amount < 0 ? "-" + result : result;
    }

    /**
     * The highest balance allowed for a player with the given tiers (0 = no limit). A tier raises the base limit; a
     * tier of 0 removes it.
     */
    double cap(java.util.function.Predicate<String> hasTier) {
        if (maxBalance <= 0) {
            return 0;
        }
        double best = maxBalance;
        for (Map.Entry<String, Double> tier : balanceCaps.entrySet()) {
            if (hasTier.test(tier.getKey())) {
                if (tier.getValue() <= 0) {
                    return 0;
                }
                best = Math.max(best, tier.getValue());
            }
        }
        return best;
    }
}
