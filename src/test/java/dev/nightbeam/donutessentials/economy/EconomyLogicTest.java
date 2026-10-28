package dev.nightbeam.donutessentials.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EconomyLogicTest {

    @Test
    void readsSettingsWithDefaults() {
        EconomySettings settings = EconomySettings.from(new YamlConfiguration());
        assertEquals(100, settings.startingBalance());
        assertEquals(0, settings.maxBalance());
        assertEquals(2, settings.decimals());
        assertEquals("$", settings.symbol());
        assertEquals(60, settings.baltopRefreshSeconds());
    }

    @Test
    void formatsMoney() {
        EconomySettings settings = EconomySettings.defaults();
        assertEquals("$12,500.00", settings.format(12_500));
        assertEquals("$0.50", settings.format(0.5));
        assertEquals("-$3.00", settings.format(-3));
    }

    @Test
    void formatsWithCustomPattern() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("currency:\n  symbol: ''\n  decimals: 0\n  format: '{amount} coins'\n");
        EconomySettings settings = EconomySettings.from(yaml);
        assertEquals("1,250 coins", settings.format(1250));
        assertEquals(3.0, settings.round(2.5) + 1.0);
    }

    @Test
    void roundsToCents() {
        EconomySettings settings = EconomySettings.defaults();
        assertEquals(0.3, settings.round(0.1 + 0.2));
        assertEquals(10.13, settings.round(10.125 + 0.000001));
    }

    @Test
    void capsRiseWithTiers() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("max-balance: 1000000\nbalance-caps:\n  vip: 5000000\n  staff: 0\n");
        EconomySettings settings = EconomySettings.from(yaml);
        assertEquals(1_000_000, settings.cap(tier -> false));
        assertEquals(5_000_000, settings.cap(tier -> tier.equals("vip")));
        assertEquals(0, settings.cap(tier -> tier.equals("staff")));
        assertEquals(0, EconomySettings.defaults().cap(tier -> true));
    }

    @Test
    void ranksRichestFirst() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        Map<UUID, Double> balances = new HashMap<>(Map.of(a, 50.0, b, 900.0, c, 300.0));
        List<Map.Entry<UUID, Double>> ranked = Baltop.rank(balances, 2);
        assertEquals(2, ranked.size());
        assertEquals(b, ranked.get(0).getKey());
        assertEquals(c, ranked.get(1).getKey());
    }

    @Test
    void writesReadableLogLines() {
        String line = EconomyLog.line(LocalDateTime.of(2026, 10, 28, 12, 30, 5), "DEPOSIT", "Kiri (uuid)", 100, 350.5, "/pay from Moonbeam");
        assertEquals("2026-10-28 12:30:05 | DEPOSIT | Kiri (uuid) | +250.50 | 100.00 -> 350.50 | /pay from Moonbeam", line);
        assertTrue(EconomyLog.line(LocalDateTime.now(), "WITHDRAW", "x", 10, 4, null).contains("| -6.00 |"));
    }
}
