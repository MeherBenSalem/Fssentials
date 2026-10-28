package dev.nightbeam.donutessentials.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EssentialsDataTest {

    @TempDir
    Path server;

    private void write(String path, String text) throws IOException {
        Path file = server.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    void readsUsersWarpsSpawnKitsAndBans() throws IOException {
        write("plugins/Essentials/userdata/0f5d0c1e-1111-4a6b-9c2d-123456789abc.yml", """
                money: '1250.5'
                last-account-name: Willow
                nickname: '&bWil'
                timestamps:
                  login: 1700000000000
                  logout: 1700000100000
                ignore:
                - 2a2b2c2d-3333-4444-5555-666666666666
                homes:
                  home:
                    world: 7d1f2a9e-0000-0000-0000-000000000000
                    world-name: world
                    x: 10.5
                    y: 64.0
                    z: -20.5
                    yaw: 90.0
                    pitch: 0.0
                  farm:
                    world-name: world_nether
                    x: 1.0
                    y: 70.0
                    z: 2.0
                """);
        write("plugins/Essentials/userdata/not-a-uuid.yml", "money: '5'\n");
        write("plugins/Essentials/warps/shop.yml", """
                name: Shop
                world: 7d1f2a9e-0000-0000-0000-000000000000
                world-name: world
                x: 100.0
                y: 65.0
                z: 100.0
                yaw: 180.0
                pitch: 10.0
                """);
        write("plugins/Essentials/spawn.yml", """
                spawns:
                  default:
                    world-name: world
                    x: 0.5
                    y: 64.0
                    z: 0.5
                    yaw: 0.0
                    pitch: 0.0
                """);
        write("plugins/Essentials/kits.yml", """
                kits:
                  tools:
                    delay: 600
                    items:
                    - stone_sword 1 sharpness:2 name:&bStarter_Blade lore:Day_one|Keep_it
                    - bread 16
                    - /say hello
                    - $100
                """);
        write("banned-players.json", """
                [{"uuid":"2a2b2c2d-3333-4444-5555-666666666666","name":"Draxen","created":"2026-10-01 12:00:00 +0000",
                  "source":"Moonbeam","expires":"forever","reason":"Griefing"}]
                """);
        write("banned-ips.json", """
                [{"ip":"10.0.0.9","created":"2026-10-01 12:00:00 +0000","source":"Server","expires":"2030-01-01 00:00:00 +0000","reason":"Alts"}]
                """);

        EssentialsData.Plan plan = EssentialsData.read(server.resolve("plugins/Essentials").toFile(), server.toFile());

        assertEquals(1, plan.users().size());
        EssentialsData.User willow = plan.users().get(0);
        assertEquals("Willow", willow.name());
        assertEquals(1250.5, willow.balance());
        assertEquals("&bWil", willow.nickname());
        assertEquals(2, willow.homes().size());
        assertEquals("world", willow.homes().get("home").world());
        assertEquals(10.5, willow.homes().get("home").x());
        assertEquals("world_nether", willow.homes().get("farm").world());
        assertEquals(1, willow.ignores().size());
        assertEquals(2, plan.homes());

        assertEquals(1, plan.warps().size());
        assertEquals("Shop", plan.warps().get(0).name());
        assertEquals(180f, plan.warps().get(0).place().yaw());

        assertNotNull(plan.spawn());
        assertEquals(0.5, plan.spawn().x());

        assertEquals(1, plan.kits().size());
        EssentialsData.Kit tools = plan.kits().get(0);
        assertEquals(600, tools.delaySeconds());
        assertEquals(2, tools.items().size());
        assertEquals(2, tools.skipped().size());
        EssentialsData.ItemSpec blade = tools.items().get(0);
        assertEquals("STONE_SWORD", blade.material());
        assertEquals(2, blade.enchantments().get("sharpness"));
        assertEquals("&bStarter Blade", blade.name());
        assertEquals(2, blade.lore().size());
        assertEquals(16, tools.items().get(1).amount());

        assertEquals(2, plan.bans().size());
        EssentialsData.Ban draxen = plan.bans().get(0);
        assertEquals("Draxen", draxen.name());
        assertNull(draxen.expires());
        EssentialsData.Ban ip = plan.bans().get(1);
        assertEquals("10.0.0.9", ip.ip());
        assertTrue(ip.expires() != null && ip.expires() > draxen.created());
    }

    @Test
    void kitItemsUseEssentialsItemNames() throws IOException {
        write("plugins/Essentials/items.json", """
                #version: 2.22.0
                # This file is for internal EssentialsX usage.
                {
                  "stone_sword": {
                    "material": "STONE_SWORD"
                  },
                  "stonesword": "stone_sword",
                  "diamond_pickaxe": {
                    "material": "DIAMOND_PICKAXE"
                  },
                  "dpickaxe": "diamond_pickaxe",
                  "minecraft:diamond_pickaxe": "diamond_pickaxe"
                }
                """);
        write("plugins/Essentials/custom_items.yml", """
                aliases:
                  drill: dpickaxe
                """);
        write("plugins/Essentials/kits.yml", """
                kits:
                  tools:
                    delay: 10
                    items:
                    - stonesword 1
                    - dpickaxe 1 efficiency:1 name:&4Gigadrill
                    - daxe:780 1
                    - drill 2
                    - minecraft:diamond_pickaxe 1
                """);

        EssentialsData.Plan plan = EssentialsData.read(server.resolve("plugins/Essentials").toFile(), server.toFile());

        List<EssentialsData.ItemSpec> items = plan.kits().get(0).items();
        assertEquals("STONE_SWORD", items.get(0).material());
        assertEquals("DIAMOND_PICKAXE", items.get(1).material());
        assertEquals(1, items.get(1).enchantments().get("efficiency"));
        assertEquals("DAXE", items.get(2).material());
        assertEquals("DIAMOND_PICKAXE", items.get(3).material());
        assertEquals("DIAMOND_PICKAXE", items.get(4).material());
    }

    @Test
    void missingFolderIsEmpty() {
        EssentialsData.Plan plan = EssentialsData.read(new File(server.toFile(), "nothing"), server.toFile());
        assertTrue(plan.users().isEmpty());
        assertTrue(plan.warps().isEmpty());
        assertNull(plan.spawn());
    }
}
