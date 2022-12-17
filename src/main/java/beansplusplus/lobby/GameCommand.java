package beansplusplus.lobby;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameCommand extends Command implements TabExecutor {
  private final GameManager gameManager = GameManager.getInstance();

  public GameCommand() {
    super("game", null);
  }

  public void execute(CommandSender sender, String[] args) {
    if (!(sender instanceof ProxiedPlayer)) {
      return;
    }

    ProxiedPlayer p = (ProxiedPlayer) sender;

    if (args.length == 0) {
      printCommands(p);
    } else if (args[0].equalsIgnoreCase("list")) {
      printServers(p);
    } else if (args[0].equalsIgnoreCase("join")) {
      join(p, args);
    } else if (args[0].equalsIgnoreCase("create")) {
      create(p, args);
    } else {
      printCommands(p);
    }
  }

  private void printCommands(ProxiedPlayer p) {
    p.sendMessage(new ComponentBuilder("/game list").color(ChatColor.RED).create());
    p.sendMessage(new ComponentBuilder("/game join <game id>").color(ChatColor.RED).create());
    p.sendMessage(new ComponentBuilder("/game create <game type>").color(ChatColor.RED).create());
  }

  private void printTypes(ProxiedPlayer p) {
    for (GameType gameType : GameType.values()) {
      p.sendMessage(new ComponentBuilder("/game create " + gameType.string()).color(ChatColor.RED).create());
    }
  }

  private void printServers(ProxiedPlayer p) {
    if (gameManager.getAvailableGameIds().size() == 0) {
      p.sendMessage(new ComponentBuilder("No servers currently...").color(ChatColor.RED).create());
    }

    for (String serverId : gameManager.getAvailableGameIds()) {
      GameServer server = gameManager.getServer(serverId);

      p.sendMessage(new ComponentBuilder(serverId + " - " + server.getType().string()).color(ChatColor.AQUA).create());
    }
  }

  private void create(ProxiedPlayer p, String[] args) {
    if (args.length < 2) {
      p.sendMessage(new ComponentBuilder("Create takes one argument. For example:").color(ChatColor.RED).create());
      printTypes(p);

      return;
    }
    GameType type = GameType.byString(args[1]);

    if (type == null) {
      p.sendMessage(new ComponentBuilder("Game type is needed. For example:").color(ChatColor.RED).create());
      printTypes(p);

      return;
    }

    gameManager.createServerAsync(type, p);

    p.sendMessage(new ComponentBuilder("Creating server... Please wait...").color(ChatColor.AQUA).create());
  }

  private void join(ProxiedPlayer p, String[] args) {
    if (args.length < 2) {
      p.sendMessage(new ComponentBuilder("/game join <game id>").color(ChatColor.RED).create());

      return;
    }
    String id = args[1];

    GameServer server = gameManager.getServer(id);

    if (server == null) {
      p.sendMessage(new ComponentBuilder("No server with that ID.").color(ChatColor.RED).create());
      p.sendMessage(new ComponentBuilder("See all games with /game list").color(ChatColor.RED).create());

      return;
    }

    p.connect(server.getServerInfo());
  }

  @Override
  public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      return List.of("list", "join", "create");
    }

    if (args.length != 2) return Collections.emptyList();

    if (args[0].equalsIgnoreCase("create")) {
      return GameType.allGameStrings();
    } else if(args[0].equalsIgnoreCase("join")) {
      return gameManager.getAvailableGameIds();
    }

    return Collections.emptyList();
  }
}