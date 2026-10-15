package me.desukillz.antichunk;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý trạng thái từng người chơi: đang ở gần y=10 chưa, đã gửi chunk nào ở dạng nào,
 * và hàng đợi gửi lại chunk khi trạng thái đổi.
 */
final class StateManager {

    /** Khoảng thời gian (ms) không xếp lại cùng một chunk vào hàng đợi. */
    private static final long REQUEUE_MS = 3000;

    private final AntiChunkPlugin plugin;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    StateManager(AntiChunkPlugin plugin) {
        this.plugin = plugin;
    }

    PlayerState stateOf(Player player) {
        return states.get(player.getUniqueId());
    }

    PlayerState getOrCreate(Player player) {
        UUID id = player.getUniqueId();
        PlayerState s = states.get(id);
        if (s != null) {
            return s;
        }
        PlayerState created = new PlayerState(id);
        applyWorld(created, player.getWorld());
        PlayerState previous = states.putIfAbsent(id, created);
        return previous != null ? previous : created;
    }

    void remove(UUID id) {
        states.remove(id);
    }

    Collection<PlayerState> all() {
        return states.values();
    }

    /** @return true nếu người chơi vừa chuyển sang world khác */
    private boolean applyWorld(PlayerState s, World world) {
        String name = world.getName();
        boolean changed = !name.equals(s.world);
        if (changed) {
            s.world = name;
            s.chunks.clear();
            s.queuedAt.clear();
            s.lowEntities.clear();
            s.revealed = false;
            synchronized (s.queue) {
                s.queue.clear();
            }
        }
        s.eligible = plugin.settings().appliesTo(name);
        s.minY = world.getMinHeight();
        s.height = world.getMaxHeight() - world.getMinHeight();
        return changed;
    }

    /**
     * Cập nhật vị trí người chơi. Gọi từ thread của người chơi (sự kiện di chuyển, dịch chuyển, hồi sinh).
     * Khi dịch chuyển, sự kiện chạy TRƯỚC khi chunk ở nơi đến được gửi nên khu vực bên dưới
     * được mở đúng lúc.
     */
    void updatePosition(Player player, Location loc) {
        PlayerState s = getOrCreate(player);
        boolean worldChanged = false;
        if (loc.getWorld() != null) {
            worldChanged = applyWorld(s, loc.getWorld());
        }
        s.chunkX = loc.getBlockX() >> 4;
        s.chunkZ = loc.getBlockZ() >> 4;

        int y = loc.getBlockY();
        s.y = y;

        Settings st = plugin.settings();
        boolean was = s.revealed;
        boolean now = was ? y <= st.hideY() : y <= st.revealY();
        if (now != was) {
            s.revealed = now;
        }
        if (now != was || worldChanged) {
            reconcile(player, s);
        }
    }

    /** Chạy mỗi giây trên thread của người chơi: đồng bộ quyền, vị trí và sửa mọi chỗ lệch. */
    void periodic(Player player) {
        try {
            PlayerState s = getOrCreate(player);
            s.bypass = player.hasPermission("desukillz.bypass");
            updatePosition(player, player.getLocation());
            reconcile(player, s);
        } catch (Throwable t) {
            plugin.fail("sync", t);
        }
    }

    /** Đưa những gì client đang thấy về đúng với trạng thái hiện tại. */
    void reconcile(Player player, PlayerState s) {
        try {
            EntityListener entities = plugin.entities();
            if (entities != null) {
                entities.reconcile(player, s);
            }
            queueMismatches(s);
        } catch (Throwable t) {
            plugin.fail("reconcile", t);
        }
    }

    private boolean wantsStrippedChunks(PlayerState s) {
        return s.hidden() && plugin.settings().blocks();
    }

    /** Xếp hàng gửi lại những chunk mà client đang giữ sai dạng so với trạng thái hiện tại. */
    private void queueMismatches(PlayerState s) {
        boolean wantStripped = wantsStrippedChunks(s);
        long now = System.currentTimeMillis();
        List<Long> todo = new ArrayList<>();

        for (Map.Entry<Long, Byte> e : s.chunks.entrySet()) {
            byte v = e.getValue();
            boolean mismatch = wantStripped ? v == PlayerState.FULL : v == PlayerState.STRIPPED;
            if (!mismatch) {
                continue;
            }
            Long queued = s.queuedAt.get(e.getKey());
            if (queued != null && now - queued < REQUEUE_MS) {
                continue;
            }
            s.queuedAt.put(e.getKey(), now);
            todo.add(e.getKey());
        }
        if (todo.isEmpty()) {
            return;
        }

        int cx = s.chunkX;
        int cz = s.chunkZ;
        todo.sort((a, b) -> Long.compare(distance(a, cx, cz), distance(b, cx, cz)));
        synchronized (s.queue) {
            s.queue.addAll(todo);
        }
    }

    private static long distance(long key, int cx, int cz) {
        long dx = PlayerState.keyX(key) - cx;
        long dz = PlayerState.keyZ(key) - cz;
        return dx * dx + dz * dz;
    }

    /** Chạy mỗi tick (thread bất đồng bộ): gửi lại một ít chunk cho mỗi người chơi đang có hàng đợi. */
    void tickQueues() {
        try {
            int perTick = plugin.settings().chunksPerTick();
            for (PlayerState s : states.values()) {
                for (int i = 0; i < perTick; i++) {
                    Long key;
                    synchronized (s.queue) {
                        key = s.queue.poll();
                    }
                    if (key == null) {
                        break;
                    }
                    resend(s, key);
                }
            }
        } catch (Throwable t) {
            plugin.fail("queue", t);
        }
    }

    @SuppressWarnings("deprecation")
    private void resend(PlayerState s, long key) {
        Byte v = s.chunks.get(key);
        if (v == null) {
            return;
        }
        boolean wantStripped = wantsStrippedChunks(s);
        boolean mismatch = wantStripped ? v == PlayerState.FULL : v == PlayerState.STRIPPED;
        if (!mismatch) {
            return;
        }
        World world = Bukkit.getWorld(s.world);
        if (world == null) {
            return;
        }
        int cx = PlayerState.keyX(key);
        int cz = PlayerState.keyZ(key);
        // Folia: chunk chỉ được thao tác trên thread của vùng sở hữu nó
        Bukkit.getRegionScheduler().execute(plugin, world, cx, cz, () -> {
            try {
                world.refreshChunk(cx, cz);
            } catch (Throwable t) {
                plugin.fail("refresh-chunk", t);
            }
        });
    }
}
