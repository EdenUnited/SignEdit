package at.haha007.signedit;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SignEdit extends JavaPlugin {

    private SignCommand signCommand;

    @Override
    public void onEnable() {
        getServer().getCommandMap().getKnownCommands().remove("sign");
        signCommand = new SignCommand(this);
        Command command = new Command("sign") {
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                return signCommand.onCommand(sender, args);
            }

            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
                return signCommand.onTabComplete(sender, args);
            }
        };
        Bukkit.getCommandMap().register("signedit", command);
    }

    public void setEnabled(Player player, boolean enabled) {
        signCommand.setEnabled(player, enabled);
    }

    public boolean isEnabled(Player player) {
        return signCommand.isEnabled(player);
    }

    public void setLines(Player player, Component[] lines) {
        signCommand.setLines(player, lines);
    }

    public Component[] getLines(Player player) {
        return signCommand.getLines(player);
    }
}
