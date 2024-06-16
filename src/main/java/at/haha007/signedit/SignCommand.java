package at.haha007.signedit;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.function.Function;

public class SignCommand implements Listener {
    private static final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    private final Set<Player> enabledPlayers = new HashSet<>();
    private final HashMap<String, String> messages = new HashMap<>();
    private final HashMap<Player, Component[]> signs = new HashMap<>();
    private final Plugin plugin;


    //sign -> toggles enabled
    //sign <line> <text> -> edit line in text

    //left click -> copy
    //if enabled placed -> written automatic

    public SignCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.saveResource("messages.yml", false);

        File file = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            messages.put(key, cfg.getString(key));
        }

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.OPEN_SIGN_EDITOR) {
            public void onPacketSending(PacketEvent event) {
                if (enabledPlayers.contains(event.getPlayer()))
                    event.setCancelled(true);
            }
        });
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return null;
        if (!enabledPlayers.contains(sender)) return List.of();
        if (!sender.hasPermission("signedit.sign.command")) return List.of();

        if (args.length == 1) return List.of("f1", "f2", "f3", "f4", "b1", "b2", "b3", "b4");

        if (!signs.containsKey(player)) return List.of();

        Component[] sign = signs.get(player);
        final Function<Integer, List<String>> f = i -> {
            if (sign[i] instanceof TextComponent tc)
                return List.of(serializer.serialize(tc).replace('§', '&'));
            return null;
        };

        if (args.length == 2) {
            List<String> l = List.of();
            String line = args[0];
            switch (line) {
                case "f1" -> l = f.apply(0);
                case "f2" -> l = f.apply(1);
                case "f3" -> l = f.apply(2);
                case "f4" -> l = f.apply(3);
                case "b1" -> l = f.apply(4);
                case "b2" -> l = f.apply(5);
                case "b3" -> l = f.apply(6);
                case "b4" -> l = f.apply(7);
            }
            return l;
        }
        return List.of();
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by players!", NamedTextColor.RED));
            return true;
        }


        if (args.length == 0) {
            // ENABLE / DISABLE
            if (enabledPlayers.contains(player)) {
                enabledPlayers.remove(player);
                player.sendMessage(messages.get("sign_disable"));
            } else {
                enabledPlayers.add(player);
                player.sendMessage(messages.get("sign_enable"));
            }
            return true;
        }


        int line = switch (args[0]) {
            case "f1" -> 1;
            case "f2" -> 2;
            case "f3" -> 3;
            case "f4" -> 4;
            case "b1" -> 5;
            case "b2" -> 6;
            case "b3" -> 7;
            case "b4" -> 8;
            default -> {
                player.sendMessage(messages.get("line_not_number"));
                yield -1;
            }
        };
        if (line == -1) return true;

        boolean colorPermission = player.hasPermission("signedit.sign.color");

        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        Component[] sign = signs.get(player);
        if (sign == null) {
            sign = new Component[8];
            Arrays.fill(sign, Component.text(""));
        }

        sign[line - 1] = colorPermission ? serializer.deserialize(text) : Component.text(text);

        signs.put(player, sign);
        player.sendMessage(messages.get("sign_edited"));
        return true;
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockPlace(BlockPlaceEvent event) {
        if (!Tag.ALL_SIGNS.isTagged(event.getBlock().getType())) return;

        final Player player = event.getPlayer();

        if (!enabledPlayers.contains(player)) return;
        if (!player.hasPermission("signedit.sign.command")) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(event.getBlock().getState() instanceof Sign sign)) return;

            Component[] lines = signs.get(player);
            if (lines == null) {
                lines = new Component[8];
                Arrays.fill(lines, Component.text(""));
            }

            SignChangeEvent signEvent = new SignChangeEvent(event.getBlock(), player, Arrays.asList(lines), Side.FRONT);
            Bukkit.getServer().getPluginManager().callEvent(signEvent);
            if (signEvent.isCancelled()) return;
            List<Component> l = signEvent.lines();
            for (int i = 0; i < 4; i++) {
                sign.getSide(Side.FRONT).line(i, l.get(i));
            }
            for (int i = 4; i < 8; i++) {
                sign.getSide(Side.BACK).line(i - 4, l.get(i));
            }
            sign.update();
        });
    }

    @EventHandler
    void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!enabledPlayers.contains(player)) return;
        if (!Tag.ALL_SIGNS.isTagged(player.getInventory().getItemInMainHand().getType())) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState(false) instanceof Sign sign)) return;

        //cancel for creative players
        event.setCancelled(true);
        Component[] lines = new Component[8];
        for (int i = 0; i < 4; i++) {
            lines[i] = sign.getSide(Side.FRONT).line(i);
        }
        for (int i = 4; i < 8; i++) {
            lines[i] = sign.getSide(Side.BACK).line(i - 4);
        }
        signs.put(event.getPlayer(), lines);
    }

    @EventHandler
    void onPlayerQuit(PlayerQuitEvent event) {
        enabledPlayers.remove(event.getPlayer());
        signs.remove(event.getPlayer());
    }


    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player);
    }

    public void setEnabled(Player player, boolean enabled) {
        if (enabled) {
            enabledPlayers.add(player);
        } else {
            enabledPlayers.remove(player);
        }
    }

    public void setLines(Player player, Component[] lines) {
        Component[] sign = new Component[8];
        Arrays.fill(sign, Component.text(""));
        System.arraycopy(lines, 0, sign, 0, Math.min(lines.length, 4));
        signs.put(player, sign);
    }

    public Component[] getLines(Player player) {
        return signs.get(player);
    }
}
