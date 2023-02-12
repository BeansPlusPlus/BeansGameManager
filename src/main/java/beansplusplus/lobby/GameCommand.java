package beansplusplus.lobby;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    } else if (args[0].equalsIgnoreCase("delete")) {
      delete(p, args);
    } else {
      printCommands(p);
    }
  }

  private void printCommands(ProxiedPlayer p) {
    p.sendMessage(new ComponentBuilder("/game list").color(ChatColor.RED).create());
    p.sendMessage(new ComponentBuilder("/game join <game id>").color(ChatColor.RED).create());
    p.sendMessage(new ComponentBuilder("/game create <game type>").color(ChatColor.RED).create());
    p.sendMessage(new ComponentBuilder("/game delete <game type>").color(ChatColor.RED).create());
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

      p.sendMessage(new ComponentBuilder(serverId).color(ChatColor.AQUA).create());
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

    Set<String> games = gameManager.getAvailableGameIds();

    if (!games.contains(id)) {
      p.sendMessage(new ComponentBuilder("No server with that ID.").color(ChatColor.RED).create());
      p.sendMessage(new ComponentBuilder("See all games with /game list").color(ChatColor.RED).create());

      return;
    }

    p.connect(ProxyServer.getInstance().getServerInfo(id));
  }

  private void delete(ProxiedPlayer p, String[] args) {
    if (args.length < 2) {
      p.sendMessage(new ComponentBuilder("/game delete <game id>").color(ChatColor.RED).create());

      return;
    }
    String id = args[1];

    Set<String> games = gameManager.getAvailableGameIds();

    if (!games.contains(id)) {
      p.sendMessage(new ComponentBuilder("No server with that ID.").color(ChatColor.RED).create());
      p.sendMessage(new ComponentBuilder("See all games with /game list").color(ChatColor.RED).create());

      return;
    }

    if (ProxyServer.getInstance().getServers().get(id).getPlayers().size() != 0) {
      p.sendMessage(new ComponentBuilder("This game cant be deleted. There are still players on here").color(ChatColor.RED).create());

      return;
    }

    gameManager.deleteGame(id);
  }

  @Override
  public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
    if (args.length == 1) {
      return List.of("list", "join", "create", "delete").stream().filter((s) -> s.startsWith(args[0].toLowerCase())).toList();
    }

    if (args.length != 2) return Collections.emptyList();

    if (args[0].equalsIgnoreCase("create")) {
      return GameType.allGameStrings().stream().filter((s) -> s.startsWith(args[1].toLowerCase())).toList();
    } else if(args[0].equalsIgnoreCase("join")) {
      return gameManager.getAvailableGameIds().stream().filter((s) -> s.startsWith(args[1].toLowerCase())).toList();
    } else if(args[0].equalsIgnoreCase("delete")) {
      return gameManager.getAvailableGameIds().stream().filter((s) -> s.startsWith(args[1].toLowerCase())).toList();
    }

    return Collections.emptyList();
  }
}