package at.haha007.signedit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.SneakyThrows;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import net.minecraft.network.protocol.game.PacketPlayOutOpenSignEditor;
import net.minecraft.server.level.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.function.Function;

public class SignCommand implements CommandExecutor, TabCompleter, Listener {
    private static final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    private final Set<Player> enabledPlayers = new HashSet<>();
    private final HashMap<String, String> messages = new HashMap<>();
    private final HashMap<Player, Component[]> signs = new HashMap<>();


    //sign -> toggles enabled
    //sign <line> <text> -> edit line in text

    //left click -> copy
    //if enabled placed -> written automatic

    public SignCommand(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.saveResource("messages.yml", false);

        File file = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            messages.put(key, cfg.getString(key));
        }
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return null;
        if (!enabledPlayers.contains(sender)) return List.of();
        if (!sender.hasPermission("signedit.sign.command")) return List.of();

        if (args.length == 1) return List.of("1", "2", "3", "4");

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
                case "1" -> l = f.apply(0);
                case "2" -> l = f.apply(1);
                case "3" -> l = f.apply(2);
                case "4" -> l = f.apply(3);
            }
            return l;
        }
        return List.of();
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be executed by players!");
            return true;
        }


        if (args.length == 0) {
            // ENABLE / DISABLE
            if (enabledPlayers.contains(player)) {
                enabledPlayers.remove(player);
                onDisable(player);
                player.sendMessage(messages.get("sign_disable"));
            } else {
                enabledPlayers.add(player);
                onEnable(player);
                player.sendMessage(messages.get("sign_enable"));
            }
            return true;
        }


        int line;
        try {
            line = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(messages.get("line_not_number"));
            return true;
        }
        if (line < 1 || line > 4) {
            player.sendMessage(messages.get("line_out_of_bounds"));
            return true;
        }

        boolean colorPermission = player.hasPermission("signedit.sign.color");

        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        Component[] sign = signs.get(player);
        if (sign == null) {
            sign = new Component[4];
            Arrays.fill(sign, Component.text(""));
        }

        sign[line - 1] = colorPermission ? serializer.deserialize(text) : Component.text(text);

        signs.put(player, sign);
        player.sendMessage(messages.get("sign_edited"));
        return true;
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockPlace(BlockPlaceEvent event) {
        if (!Tag.SIGNS.isTagged(event.getBlock().getType())) return;

        final Player player = event.getPlayer();

        if (!enabledPlayers.contains(player)) return;
        if (!player.hasPermission("signedit.sign.command")) return;

        Sign sign = ((Sign) event.getBlock().getState());

        Component[] lines = signs.get(player);
        if (lines == null) {
            lines = new Component[4];
            Arrays.fill(lines, Component.text(""));
        }


        sign.setEditable(false);
        SignChangeEvent signEvent = new SignChangeEvent(event.getBlock(), player, Arrays.asList(lines));
        Bukkit.getServer().getPluginManager().callEvent(signEvent);
        if (signEvent.isCancelled()) return;
        List<Component> l = signEvent.lines();
        for (int i = 0; i < l.size(); i++) {
            sign.line(i, l.get(i));
        }
        sign.update();
    }

    @EventHandler
    void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!enabledPlayers.contains(player)) return;
        if(!Tag.SIGNS.isTagged(player.getInventory().getItemInMainHand().getType())) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState(false) instanceof Sign sign)) return;

        //cancel for creative players
        event.setCancelled(true);
        signs.put(event.getPlayer(), sign.lines().toArray(new Component[0]));
    }

    @EventHandler
    void onPlayerQuit(PlayerQuitEvent event) {
        enabledPlayers.remove(event.getPlayer());
        signs.remove(event.getPlayer());
    }

    private void onEnable(Player player) {
        Channel channel = getChannel(player);
        Objects.requireNonNull(channel).pipeline().addBefore("packet_handler", "sign_packet_handler", new SignPacketRemover());
    }

    private void onDisable(Player player) {
        Channel channel = getChannel(player);
        Objects.requireNonNull(channel).pipeline().remove("sign_packet_handler");
    }

    @SneakyThrows
    private Channel getChannel(Player player) {
        EntityPlayer nmsPlayer = (EntityPlayer) player.getClass().getDeclaredMethod("getHandle").invoke(player);
        return nmsPlayer.b.a.m;
    }


    private static class SignPacketRemover extends ChannelDuplexHandler {
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof PacketPlayOutOpenSignEditor) return;
            super.write(ctx, msg, promise);
        }
    }

}
