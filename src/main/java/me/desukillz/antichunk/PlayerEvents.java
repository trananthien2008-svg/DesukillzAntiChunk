package me.desukillz.antichunk;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Theo dõi vị trí người chơi để biết khi nào họ xuống gần y=10 hoặc lên cao. */
final class PlayerEvents implements Listener {

    private final AntiChunkPlugin plugin;
    private final StateManager states;

    PlayerEvents(AntiChunkPlugin plugin, StateManager states) {
        this.plugin = plugin;
        this.states = states;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.startPlayerTask(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Player player = event.getPlayer();
        PlayerState s = states.stateOf(player);
        if (s != null && s.y == to.getBlockY() && s.chunkX == (to.getBlockX() >> 4)
                && s.chunkZ == (to.getBlockZ() >> 4) && to.getWorld().getName().equals(s.world)) {
            return; // không có gì thay đổi, thoát nhanh
        }
        update(player, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        update(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        update(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        update(event.getPlayer(), event.getPlayer().getLocation());
    }

    private void update(Player player, Location location) {
        try {
            states.updatePosition(player, location);
        } catch (Throwable t) {
            plugin.fail("events", t);
        }
    }
}
