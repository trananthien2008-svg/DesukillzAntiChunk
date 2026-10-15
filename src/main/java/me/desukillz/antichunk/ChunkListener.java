package me.desukillz.antichunk;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import org.bukkit.entity.Player;

/** Ẩn block: cắt chunk section thấp khỏi gói chunk và chặn các gói cập nhật block ở vùng bị ẩn. */
final class ChunkListener extends PacketAdapter {

    private static final PacketType MAP_CHUNK = PacketUtil.type("MAP_CHUNK");
    private static final PacketType UNLOAD_CHUNK = PacketUtil.type("UNLOAD_CHUNK");
    private static final PacketType MULTI_BLOCK_CHANGE = PacketUtil.type("MULTI_BLOCK_CHANGE");

    private final AntiChunkPlugin plugin;
    private final StateManager states;

    ChunkListener(AntiChunkPlugin plugin, StateManager states) {
        super(plugin, ListenerPriority.NORMAL, PacketUtil.typeArray(
                "MAP_CHUNK", "UNLOAD_CHUNK", "BLOCK_CHANGE", "MULTI_BLOCK_CHANGE",
                "TILE_ENTITY_DATA", "BLOCK_ACTION", "BLOCK_BREAK_ANIMATION"));
        this.plugin = plugin;
        this.states = states;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        try {
            Player player = event.getPlayer();
            PacketType type = event.getPacketType();

            if (type.equals(MAP_CHUNK)) {
                onChunk(event, player);
            } else if (type.equals(UNLOAD_CHUNK)) {
                onUnload(event, player);
            } else {
                onBlockUpdate(event, player, type);
            }
        } catch (Throwable t) {
            plugin.fail("blocks", t);
        }
    }

    private void onChunk(PacketEvent event, Player player) throws Exception {
        Settings st = plugin.settings();
        PlayerState s = states.getOrCreate(player);
        PacketContainer packet = event.getPacket();

        long key = PlayerState.key(packet.getIntegers().read(0), packet.getIntegers().read(1));
        s.queuedAt.remove(key);

        if (!s.eligible || !st.blocks()) {
            s.chunks.remove(key);
            return;
        }
        if (!s.hidden()) {
            s.chunks.put(key, PlayerState.FULL);
            return;
        }

        PacketContainer stripped = ChunkEditor.strip(packet, s.minY, s.height, st.hideBelowY());
        if (stripped == null) {
            if (st.debug()) {
                plugin.getLogger().info("[debug] không cắt được chunk " + PlayerState.keyX(key) + ","
                        + PlayerState.keyZ(key) + " (định dạng lạ hoặc không có section thấp)");
            }
            s.chunks.put(key, PlayerState.SKIP);
            return;
        }
        event.setPacket(stripped);
        s.chunks.put(key, PlayerState.STRIPPED);
    }

    private void onUnload(PacketEvent event, Player player) {
        PlayerState s = states.stateOf(player);
        if (s == null) {
            return;
        }
        PacketContainer packet = event.getPacket();
        long key;
        if (packet.getChunkCoordIntPairs().size() > 0) {
            ChunkCoordIntPair pair = packet.getChunkCoordIntPairs().read(0);
            key = PlayerState.key(pair.getChunkX(), pair.getChunkZ());
        } else {
            key = PlayerState.key(packet.getIntegers().read(0), packet.getIntegers().read(1));
        }
        s.chunks.remove(key);
        s.queuedAt.remove(key);
    }

    private void onBlockUpdate(PacketEvent event, Player player, PacketType type) throws Exception {
        Settings st = plugin.settings();
        if (!st.blocks()) {
            return;
        }
        PlayerState s = states.stateOf(player);
        if (s == null || !s.hidden()) {
            return;
        }
        int limit = st.hideBelowY();
        PacketContainer packet = event.getPacket();

        if (type.equals(MULTI_BLOCK_CHANGE)) {
            BlockPosition section = PacketUtil.sectionPos(packet);
            if (section != null && section.getY() * 16 + 16 <= limit) {
                event.setCancelled(true);
            }
            return;
        }
        BlockPosition pos = packet.getBlockPositionModifier().readSafely(0);
        if (pos != null && pos.getY() < limit) {
            event.setCancelled(true);
        }
    }
}
