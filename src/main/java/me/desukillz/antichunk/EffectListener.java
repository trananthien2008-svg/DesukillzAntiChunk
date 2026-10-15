package me.desukillz.antichunk;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;

/** Ẩn hiệu ứng: hạt, âm thanh, hiệu ứng thế giới (mở rương, phá block...) và vụ nổ ở vùng bị ẩn. */
final class EffectListener extends PacketAdapter {

    private static final PacketType PARTICLES = PacketUtil.type("WORLD_PARTICLES");
    private static final PacketType SOUND = PacketUtil.type("NAMED_SOUND_EFFECT");
    private static final PacketType WORLD_EVENT = PacketUtil.type("WORLD_EVENT");
    private static final PacketType EXPLOSION = PacketUtil.type("EXPLOSION");

    private final AntiChunkPlugin plugin;
    private final StateManager states;

    EffectListener(AntiChunkPlugin plugin, StateManager states) {
        super(plugin, ListenerPriority.NORMAL, PacketUtil.typeArray(
                "WORLD_PARTICLES", "NAMED_SOUND_EFFECT", "WORLD_EVENT", "EXPLOSION"));
        this.plugin = plugin;
        this.states = states;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        try {
            Settings st = plugin.settings();
            if (!st.effects()) {
                return;
            }
            PlayerState s = states.stateOf(event.getPlayer());
            if (s == null || !s.hidden()) {
                return;
            }

            PacketType type = event.getPacketType();
            PacketContainer packet = event.getPacket();
            double y;

            if (type.equals(PARTICLES) || type.equals(EXPLOSION)) {
                if (packet.getDoubles().size() < 3) {
                    return; // phiên bản này lưu toạ độ kiểu khác, để nguyên
                }
                y = packet.getDoubles().read(1);
            } else if (type.equals(SOUND)) {
                // toạ độ âm thanh là số nguyên nhân 8
                y = packet.getIntegers().read(1) / 8.0;
            } else if (type.equals(WORLD_EVENT)) {
                BlockPosition pos = packet.getBlockPositionModifier().readSafely(0);
                if (pos == null) {
                    return;
                }
                y = pos.getY();
            } else {
                return;
            }

            if (y < st.hideBelowY()) {
                event.setCancelled(true);
            }
        } catch (Throwable t) {
            plugin.fail("effects", t);
        }
    }
}
