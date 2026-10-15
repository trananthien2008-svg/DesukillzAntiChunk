package me.desukillz.antichunk;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trạng thái của một người chơi. Được đọc từ thread mạng (ProtocolLib) và ghi từ thread
 * của người chơi (Folia), nên các trường đơn giản là volatile còn các map là concurrent.
 */
final class PlayerState {

    /** Chunk client đang giữ bản đầy đủ. */
    static final byte FULL = 0;
    /** Chunk client đang giữ bản đã bị cắt phần dưới. */
    static final byte STRIPPED = 1;
    /** Chunk không thể / không cần cắt, bỏ qua khi gửi lại. */
    static final byte SKIP = 2;

    final UUID id;

    volatile String world = "";
    volatile boolean eligible;
    volatile int minY = -64;
    volatile int height = 384;

    volatile boolean bypass;
    /** true = người chơi đủ gần y=10 để được thấy khu vực bên dưới. */
    volatile boolean revealed;

    volatile int y = Integer.MAX_VALUE;
    volatile int chunkX;
    volatile int chunkZ;

    /** khoá chunk -> FULL / STRIPPED / SKIP */
    final Map<Long, Byte> chunks = new ConcurrentHashMap<>();
    /** khoá chunk -> thời điểm (ms) đã xếp hàng gửi lại, để không xếp trùng */
    final Map<Long, Long> queuedAt = new ConcurrentHashMap<>();
    /** entity id -> thông tin sinh vật sinh ra bên dưới ngưỡng ẩn */
    final Map<Integer, LowEntity> lowEntities = new ConcurrentHashMap<>();
    /** hàng đợi chunk cần gửi lại (khoá chunk), gần người chơi nhất đứng trước */
    final ArrayDeque<Long> queue = new ArrayDeque<>();

    PlayerState(UUID id) {
        this.id = id;
    }

    /** Khu vực bên dưới hiện đang bị ẩn với người chơi này? */
    boolean hidden() {
        return eligible && !bypass && !revealed;
    }

    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyZ(long key) {
        return (int) key;
    }
}
