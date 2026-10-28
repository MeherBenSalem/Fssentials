package dev.nightbeam.donutessentials.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyImporterTest {

    @TempDir
    Path folder;

    @Test
    void readsTwoPointXPunishments() throws Exception {
        File file = folder.resolve("punishments.yml").toFile();
        Files.writeString(file.toPath(), """
                punishments:
                  a1b2c3d4:
                    type: BAN
                    targetUuid: 0f8e3b5a-9a52-4e5e-9a2a-2f6f1c2a9b11
                    targetName: Draxen
                    targetIp: 10.0.0.7
                    actor: Moonbeam
                    reason: Using a hacked client
                    createdAt: 1757000000000
                    active: true
                    silent: false
                  E5F6A7B8:
                    type: MUTE
                    targetName: Kiri
                    actor: Console
                    reason: Spamming messages
                    createdAt: 1757000100000
                    expiresAt: 1757003700000
                    active: false
                    silent: true
                  C0FFEE00:
                    type: SOMETHING_ELSE
                    targetName: Willow
                """);
        List<LegacyImporter.LegacyPunishment> rows = LegacyImporter.readPunishments(file);
        assertEquals(3, rows.size());
        LegacyImporter.LegacyPunishment ban = rows.stream().filter(p -> p.id().equals("A1B2C3D4")).findFirst().orElseThrow();
        assertEquals("BAN", ban.type());
        assertEquals(UUID.fromString("0f8e3b5a-9a52-4e5e-9a2a-2f6f1c2a9b11"), ban.targetUuid());
        assertEquals("10.0.0.7", ban.targetIp());
        assertNull(ban.expiresAt());
        assertTrue(ban.active());
        LegacyImporter.LegacyPunishment mute = rows.stream().filter(p -> p.id().equals("E5F6A7B8")).findFirst().orElseThrow();
        assertNull(mute.targetUuid());
        assertEquals(1757003700000L, mute.expiresAt());
        assertFalse(mute.active());
        assertTrue(mute.silent());
        assertEquals("NOTE", rows.stream().filter(p -> p.id().equals("C0FFEE00")).findFirst().orElseThrow().type());
    }

    @Test
    void readsTwoPointXIpLocks() throws Exception {
        File file = folder.resolve("ip-locks.yml").toFile();
        Files.writeString(file.toPath(), """
                locks:
                  0f8e3b5a-9a52-4e5e-9a2a-2f6f1c2a9b11:
                    player: Moonbeam
                    ip: 192.168.1.20
                  not-a-uuid:
                    player: Broken
                    ip: 10.0.0.1
                  1c2d3e4f-0000-4000-8000-000000000001:
                    player: NoIp
                    ip: not.an.address
                """);
        List<LegacyImporter.LegacyLock> locks = LegacyImporter.readLocks(file);
        assertEquals(1, locks.size());
        assertEquals("Moonbeam", locks.get(0).name());
        assertEquals("192.168.1.20", locks.get(0).ip());
    }

    @Test
    void whitelistIsLowerCaseWithoutDuplicates() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                maintenance:
                  enabled: true
                whitelist: [Moonbeam, moonbeam, Kiri, " "]
                """);
        assertEquals(List.of("moonbeam", "kiri"), LegacyImporter.whitelist(config));
    }

    @Test
    void missingFilesReadAsEmpty() {
        assertTrue(LegacyImporter.readPunishments(folder.resolve("nope.yml").toFile()).isEmpty());
        assertTrue(LegacyImporter.readLocks(folder.resolve("nope.yml").toFile()).isEmpty());
        assertNull(LegacyImporter.normalizeIp("example.com"));
        assertEquals("127.0.0.1", LegacyImporter.normalizeIp(" 127.0.0.1 "));
    }
}
