package me.desukillz.antichunk;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Desukillz Anti Chunk: ẩn block, hiệu ứng và sinh vật dưới y=0 cho đến khi người chơi xuống gần. */
public final class AntiChunkPlugin extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private volatile Settings settings;
    private StateManager states;
    private EntityListener entities;
    private final Set<String> failedModules = ConcurrentHashMap.newKeySet();

    static Component text(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig());
        SectionCodec.configure(Bukkit.getMinecraftVersion());

        states = new StateManager(this);
        entities = new EntityListener(this, states);

        ProtocolManager protocol = ProtocolLibrary.getProtocolManager();
        protocol.addPacketListener(new ChunkListener(this, states));
        protocol.addPacketListener(new EffectListener(this, states));
        protocol.addPacketListener(entities);

        getServer().getPluginManager().registerEvents(new PlayerEvents(this, states), this);

        AntiChunkCommand command = new AntiChunkCommand(this, states);
        PluginCommand pluginCommand = getCommand("antichunk");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        // Gửi lại chunk dần dần, không dồn một lúc
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> states.tickQueues(),
                50, 50, TimeUnit.MILLISECONDS);

        // Người chơi đã online (khi dùng /reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            startPlayerTask(player);
        }

        getLogger().info("Đã bật. Ẩn mọi thứ dưới y=" + settings.hideBelowY() + ", mở khi người chơi xuống y<="
                + settings.revealY() + ", ẩn lại khi y>" + settings.hideY()
                + " (Minecraft " + Bukkit.getMinecraftVersion() + ").");
    }

    @Override
    public void onDisable() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(this);
    }

    /** Bắt đầu đồng bộ định kỳ trên thread riêng của người chơi (đúng cách trên Folia). */
    void startPlayerTask(Player player) {
        player.getScheduler().run(this, task -> states.periodic(player), null);
        player.getScheduler().runAtFixedRate(this, task -> states.periodic(player), null, 20L, 20L);
    }

    void reloadAll() {
        reloadConfig();
        settings = Settings.load(getConfig());
        failedModules.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(this, task -> states.periodic(player), null);
        }
    }

    /** Ghi lỗi của một module (chỉ in đầy đủ lần đầu), gói tin lỗi được để nguyên. */
    void fail(String module, Throwable error) {
        if (failedModules.add(module)) {
            getLogger().log(Level.SEVERE, "Lỗi ở phần '" + module + "' (chỉ báo lần đầu, gói tin được để nguyên). "
                    + "Hãy gửi đoạn lỗi này cho người viết plugin.", error);
        } else if (settings != null && settings.debug()) {
            getLogger().log(Level.WARNING, "Lỗi lặp lại ở phần '" + module + "'", error);
        }
    }

    Settings settings() {
        return settings;
    }

    EntityListener entities() {
        return entities;
    }
}
