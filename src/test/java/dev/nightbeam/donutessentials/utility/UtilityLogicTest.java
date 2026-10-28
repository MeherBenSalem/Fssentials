package dev.nightbeam.donutessentials.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightbeam.donutessentials.player.PlayerData;
import dev.nightbeam.donutessentials.player.PlayerFlag;
import java.util.UUID;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class UtilityLogicTest {

    @Test
    void speedLevelsRunFromNormalToMaximum() {
        assertEquals(0.2f, Speeds.walk(1), 1e-6);
        assertEquals(1.0f, Speeds.walk(10), 1e-6);
        assertEquals(0.1f, Speeds.fly(1), 1e-6);
        assertEquals(1.0f, Speeds.fly(10), 1e-6);
        assertTrue(Speeds.walk(5) > Speeds.walk(4));
    }

    @Test
    void speedLevelMustBeOneToTen() {
        assertEquals(1, Speeds.parseLevel("1"));
        assertEquals(10, Speeds.parseLevel(" 10 "));
        assertEquals(-1, Speeds.parseLevel("0"));
        assertEquals(-1, Speeds.parseLevel("11"));
        assertEquals(-1, Speeds.parseLevel("fast"));
        assertEquals(-1, Speeds.parseLevel(null));
    }

    @Test
    void gameModesByNameShortNameAndNumber() {
        assertEquals(GameMode.CREATIVE, Speeds.gameMode("c"));
        assertEquals(GameMode.CREATIVE, Speeds.gameMode("1"));
        assertEquals(GameMode.SURVIVAL, Speeds.gameMode("Survival"));
        assertEquals(GameMode.ADVENTURE, Speeds.gameMode("adv"));
        assertEquals(GameMode.SPECTATOR, Speeds.gameMode("sp"));
        assertNull(Speeds.gameMode("hardcore"));
        assertEquals("spectator", Speeds.key(GameMode.SPECTATOR));
    }

    @Test
    void hubButtonsMatchTheirCommandName() {
        assertEquals("mail", HubMenu.commandName("mail read"));
        assertEquals("homes", HubMenu.commandName("/homes".substring(1)));
        assertEquals("kit", HubMenu.commandName("donutessentials:kit"));
    }

    @Test
    void settingsSwitchesFlipTheirFlag() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "Moonbeam");
        SettingsMenu.Toggle requests = new SettingsMenu.Toggle("tpa", PlayerFlag.TPA_OFF, true);
        SettingsMenu.Toggle auto = new SettingsMenu.Toggle("tpa-auto", PlayerFlag.TPA_AUTO, false);
        assertTrue(requests.on(data), "requests are on by default");
        assertFalse(auto.on(data), "auto-accept is off by default");
        data.set(requests.flag(), requests.flagFor(false));
        assertTrue(data.has(PlayerFlag.TPA_OFF));
        assertFalse(requests.on(data));
        data.set(auto.flag(), auto.flagFor(true));
        assertTrue(auto.on(data));
    }

    @Test
    void godModeStillDiesInTheVoid() {
        assertTrue(UtilityListener.lethalByDesign("VOID"));
        assertTrue(UtilityListener.lethalByDesign("KILL"));
        assertFalse(UtilityListener.lethalByDesign("FALL"));
    }
}
