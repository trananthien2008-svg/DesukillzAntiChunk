package me.desukillz.antichunk;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ẩn sinh vật: chặn gói spawn của sinh vật sinh ra bên dưới ngưỡng ẩn và mọi gói liên quan,
 * đồng thời nhớ lại để dựng lại khi người chơi xuống gần.
 *
 * <p>Việc ẩn quyết định tại thời điểm sinh vật được "spawn" cho người chơi (khi vào tầm nhìn).
 */
final class EntityListener extends PacketAdapter {

    private static final String[] SPAWN_NAMES = {
            "SPAWN_ENTITY", "SPAWN_ENTITY_LIVING", "NAMED_ENTITY_SPAWN", "SPAWN_ENTITY_EXPERIENCE_ORB"};

    /** Gói có entity id ở số nguyên đầu tiên và cần nhớ để dựng lại. */
    private static final String[] CACHED_NAMES = {
            "ENTITY_METADATA", "ENTITY_EQUIPMENT", "UPDATE_ATTRIBUTES", "ENTITY_EFFECT", "REMOVE_ENTITY_EFFECT"};

    private static final PacketType NAMED_ENTITY_SPAWN = PacketUtil.type("NAMED_ENTITY_SPAWN");
    private static final PacketType ENTITY_DESTROY = PacketUtil.type("ENTITY_DESTROY");
    private static final PacketType REL_MOVE = PacketUtil.type("REL_ENTITY_MOVE");
    private static final PacketType REL_MOVE_LOOK = PacketUtil.type("REL_ENTITY_MOVE_LOOK");
    private static final PacketType TELEPORT = PacketUtil.type("ENTITY_TELEPORT");

    private static final Set<PacketType> SPAWN = new HashSet<>(PacketUtil.types(SPAWN_NAMES));
    private static final Set<PacketType> CACHED = new HashSet<>(PacketUtil.types(CACHED_NAMES));

    private final AntiChunkPlugin plugin;
    private final StateManager states;

    EntityListener(AntiChunkPlugin plugin, StateManager states) {
        super(plugin, ListenerPriority.NORMAL, PacketUtil.typeArray(
                "SPAWN_ENTITY", "SPAWN_ENTITY_LIVING", "NAMED_ENTITY_SPAWN", "SPAWN_ENTITY_EXPERIENCE_ORB",
                "ENTITY_DESTROY", "REL_ENTITY_MOVE", "REL_ENTITY_MOVE_LOOK", "ENTITY_TELEPORT",
                "ENTITY_METADATA", "ENTITY_EQUIPMENT", "UPDATE_ATTRIBUTES", "ENTITY_EFFECT",
                "REMOVE_ENTITY_EFFECT", "ENTITY_VELOCITY", "ENTITY_LOOK", "ENTITY_HEAD_ROTATION",
                "ANIMATION", "ENTITY_STATUS", "ENTITY_SOUND", "ENTITY_POSITION_SYNC"));
        this.plugin = plugin;
        this.states = states;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        try {
            Settings st = plugin.settings();
            if (!st.entities()) {
                return;
            }
            Player player = event.getPlayer();
            PacketType type = event.getPacketType();

            if (SPAWN.contains(type)) {
                PlayerState s = states.getOrCreate(player);
                if (s.eligible) {
                    onSpawn(event, s, st, type);
                }
                return;
            }

            PlayerState s = states.stateOf(player);
            if (s == null || !s.eligible || s.lowEntities.isEmpty()) {
                return;
            }
            PacketContainer packet = event.getPacket();

            if (type.equals(ENTITY_DESTROY)) {
                onDestroy(packet, s);
            } else {
                onEntityPacket(event, packet, s, type);
            }
        } catch (Throwable t) {
            plugin.fail("entities", t);
        }
    }

    private void onSpawn(PacketEvent event, PlayerState s, Settings st, PacketType type) {
        PacketContainer packet = event.getPacket();
        if (packet.getDoubles().size() < 3) {
            return;
        }
        double x = packet.getDoubles().read(0);
        double y = packet.getDoubles().read(1);
        double z = packet.getDoubles().read(2);
        if (y >= st.hideBelowY()) {
            return;
        }
        if (!st.hidePlayers() && isPlayerSpawn(packet, type)) {
            return;
        }

        int id = packet.getIntegers().read(0);
        boolean visible = !s.hidden();
        s.lowEntities.put(id, new LowEntity(packet, x, y, z, visible));
        if (!visible) {
            event.setCancelled(true);
        }
    }

    private static boolean isPlayerSpawn(PacketContainer packet, PacketType type) {
        if (type.equals(NAMED_ENTITY_SPAWN)) {
            return true;
        }
        try {
            return packet.getEntityTypeModifier().readSafely(0) == EntityType.PLAYER;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void onDestroy(PacketContainer packet, PlayerState s) {
        List<Integer> ids = packet.getIntLists().readSafely(0);
        if (ids != null) {
            for (Integer id : ids) {
                s.lowEntities.remove(id);
            }
        }
    }

    private void onEntityPacket(PacketEvent event, PacketContainer packet, PlayerState s, PacketType type) {
        int id = packet.getIntegers().read(0);
        LowEntity entity = s.lowEntities.get(id);
        if (entity == null) {
            return;
        }
        synchronized (entity) {
            if (type.equals(REL_MOVE) || type.equals(REL_MOVE_LOOK)) {
                if (packet.getShorts().size() >= 3) {
                    entity.moveBy(packet.getShorts().read(0) / 4096.0,
                            packet.getShorts().read(1) / 4096.0,
                            packet.getShorts().read(2) / 4096.0);
                }
            } else if (type.equals(TELEPORT)) {
                if (packet.getDoubles().size() >= 3) {
                    entity.moveTo(packet.getDoubles().read(0), packet.getDoubles().read(1),
                            packet.getDoubles().read(2));
                }
            } else if (CACHED.contains(type)) {
                entity.cache(packet);
            }
            if (!entity.visible) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Đưa sinh vật về đúng trạng thái: gửi lại cho người chơi khi họ xuống gần,
     * hoặc báo client xoá khi họ lên cao. Gọi từ thread của người chơi.
     */
    void reconcile(Player player, PlayerState s) {
        if (s.lowEntities.isEmpty()) {
            return;
        }
        boolean wantVisible = !(s.hidden() && plugin.settings().entities());
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        List<Integer> toDestroy = new ArrayList<>();

        for (Map.Entry<Integer, LowEntity> entry : s.lowEntities.entrySet()) {
            LowEntity entity = entry.getValue();
            synchronized (entity) {
                if (entity.visible == wantVisible) {
                    continue;
                }
                if (wantVisible) {
                    try {
                        for (PacketContainer packet : entity.replay()) {
                            manager.sendServerPacket(player, packet, false);
                        }
                    } catch (Exception e) {
                        plugin.fail("entity-replay", e);
                    }
                    entity.visible = true;
                } else {
                    toDestroy.add(entry.getKey());
                    entity.visible = false;
                }
            }
        }

        if (!toDestroy.isEmpty()) {
            try {
                PacketContainer destroy = new PacketContainer(ENTITY_DESTROY);
                destroy.getIntLists().write(0, toDestroy);
                manager.sendServerPacket(player, destroy, false);
            } catch (Exception e) {
                plugin.fail("entity-destroy", e);
            }
        }
    }
}
