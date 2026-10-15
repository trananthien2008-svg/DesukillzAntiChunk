package me.desukillz.antichunk;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class AntiChunkCommand implements TabExecutor {

    private final AntiChunkPlugin plugin;
    private final StateManager states;

    AntiChunkCommand(AntiChunkPlugin plugin, StateManager states) {
        this.plugin = plugin;
        this.states = states;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("desukillz.admin")) {
            sender.sendMessage(AntiChunkPlugin.text("&cBạn không có quyền dùng lệnh này."));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(AntiChunkPlugin.text("&aĐã tải lại cấu hình Desukillz Anti Chunk."));
            }
            case "status" -> status(sender);
            default -> sender.sendMessage(AntiChunkPlugin.text("&eDùng: /" + label + " <reload|status>"));
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage(AntiChunkPlugin.text("&6&lDesukillz Anti Chunk"));
        SectionCodec.Layout layout = SectionCodec.detectedLayout();
        sender.sendMessage(AntiChunkPlugin.text("&7Định dạng chunk: &f"
                + (layout == null ? "chưa nhận diện (chưa có chunk nào được cắt)" : layout.name())));

        if (!(sender instanceof Player player)) {
            sender.sendMessage(AntiChunkPlugin.text("&7Người chơi đang theo dõi: &f" + states.all().size()));
            return;
        }
        PlayerState s = states.stateOf(player);
        if (s == null) {
            sender.sendMessage(AntiChunkPlugin.text("&cChưa có dữ liệu cho bạn, hãy thử lại sau 1 giây."));
            return;
        }

        int full = 0;
        int stripped = 0;
        int skipped = 0;
        for (byte v : s.chunks.values()) {
            if (v == PlayerState.FULL) {
                full++;
            } else if (v == PlayerState.STRIPPED) {
                stripped++;
            } else {
                skipped++;
            }
        }
        int visible = 0;
        int hiddenEntities = 0;
        for (LowEntity e : s.lowEntities.values()) {
            synchronized (e) {
                if (e.visible) {
                    visible++;
                } else {
                    hiddenEntities++;
                }
            }
        }
        int queued;
        synchronized (s.queue) {
            queued = s.queue.size();
        }

        Settings st = plugin.settings();
        sender.sendMessage(AntiChunkPlugin.text("&7World: &f" + s.world + " &7(áp dụng: "
                + (s.eligible ? "&acó" : "&ckhông") + "&7)"));
        sender.sendMessage(AntiChunkPlugin.text("&7Độ cao của bạn: &f" + s.y
                + " &7| mở khi &f<= " + st.revealY() + "&7, ẩn lại khi &f> " + st.hideY()));
        sender.sendMessage(AntiChunkPlugin.text("&7Trạng thái: "
                + (s.bypass ? "&bBỏ qua (có quyền desukillz.bypass)"
                : s.hidden() ? "&cĐang ẩn khu vực dưới y=" + st.hideBelowY()
                : "&aĐang thấy khu vực dưới y=" + st.hideBelowY())));
        sender.sendMessage(AntiChunkPlugin.text("&7Chunk: &fđầy đủ " + full + "&7, &fđã cắt " + stripped
                + "&7, &fbỏ qua " + skipped + "&7, &fchờ gửi lại " + queued));
        sender.sendMessage(AntiChunkPlugin.text("&7Sinh vật bên dưới: &fđang thấy " + visible
                + "&7, &fđang ẩn " + hiddenEntities));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("desukillz.admin")) {
            return List.of("reload", "status");
        }
        return List.of();
    }
}
