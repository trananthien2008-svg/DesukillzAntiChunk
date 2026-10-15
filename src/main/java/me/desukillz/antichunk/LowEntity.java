package me.desukillz.antichunk;

import com.comphenix.protocol.events.PacketContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Một sinh vật sinh ra bên dưới ngưỡng ẩn. Giữ lại gói spawn và các gói trạng thái
 * (metadata, trang bị, thuộc tính, hiệu ứng) để gửi lại khi người chơi xuống gần.
 */
final class LowEntity {

    private static final int MAX_PACKETS = 48;

    private final List<PacketContainer> packets = new ArrayList<>();
    private double x;
    private double y;
    private double z;

    /** Client hiện có đang thấy sinh vật này không. Dùng cùng khoá với {@code synchronized (entity)}. */
    boolean visible;

    LowEntity(PacketContainer spawn, double x, double y, double z, boolean visible) {
        this.packets.add(spawn);
        this.x = x;
        this.y = y;
        this.z = z;
        this.visible = visible;
    }

    synchronized void cache(PacketContainer packet) {
        packets.add(packet);
        if (packets.size() > MAX_PACKETS) {
            packets.remove(1); // giữ nguyên gói spawn ở vị trí 0
        }
    }

    synchronized void moveBy(double dx, double dy, double dz) {
        x += dx;
        y += dy;
        z += dz;
    }

    synchronized void moveTo(double nx, double ny, double nz) {
        x = nx;
        y = ny;
        z = nz;
    }

    /** Danh sách gói cần gửi để dựng lại sinh vật, gói spawn đã được cập nhật vị trí mới nhất. */
    synchronized List<PacketContainer> replay() throws Exception {
        List<PacketContainer> out = new ArrayList<>(packets.size());
        PacketContainer spawn = PacketUtil.copy(packets.get(0));
        spawn.getDoubles().write(0, x).write(1, y).write(2, z);
        out.add(spawn);
        out.addAll(packets.subList(1, packets.size()));
        return out;
    }
}
