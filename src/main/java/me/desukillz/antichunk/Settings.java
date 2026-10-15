package me.desukillz.antichunk;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;

/** Cấu hình đã đọc từ config.yml (bất biến, thay cả object khi reload). */
record Settings(Set<String> worlds, int hideBelowY, int revealY, int hideY, int chunksPerTick,
                boolean blocks, boolean effects, boolean entities, boolean hidePlayers, boolean debug) {

    static Settings load(FileConfiguration c) {
        int hideBelow = Math.floorDiv(c.getInt("hide-below-y", 0), 16) * 16;
        int reveal = c.getInt("reveal-y", 10);
        int hide = Math.max(reveal + 1, c.getInt("hide-y", reveal + 4));
        return new Settings(
                Set.copyOf(c.getStringList("worlds")),
                hideBelow,
                reveal,
                hide,
                Math.max(1, c.getInt("chunks-per-tick", 4)),
                c.getBoolean("modules.blocks", true),
                c.getBoolean("modules.effects", true),
                c.getBoolean("modules.entities", true),
                c.getBoolean("hide-players", true),
                c.getBoolean("debug", false));
    }

    boolean appliesTo(String world) {
        return worlds.contains("*") || worlds.contains(world);
    }
}
