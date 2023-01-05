package beansplusplus.lobby;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class GameManager {
  private static final GameManager GAME_MANAGER = new GameManager();

  public static GameManager getInstance() {
    return GAME_MANAGER;
  }

  private final Random random = new Random();

  private final Map<String, GameServer> gameServers = new HashMap<>();

  private Plugin plugin;

  public void registerPlugin(Plugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Create a new server by game type
   *
   * @param type
   * @param creator
   * @return
   */
  public void createServerAsync(GameType type, ProxiedPlayer creator) {
    final String creatorUsername = creator.getName();

    ProxyServer.getInstance().getScheduler().schedule(plugin, () -> {
      createServer(type, creatorUsername);
    }, 0, TimeUnit.SECONDS);
  }

  /**
   * Create a new server by game type
   *
   * @param type
   * @param creatorUsername
   * @return
   */
  public void createServer(GameType type, String creatorUsername) {
    GameServer gameServer = new GameServer(type, generateId());

    KubernetesService service = new KubernetesService(gameServer);

    try {
      InetSocketAddress address = service.start();

      registerServer(gameServer, address);

      ProxiedPlayer player = ProxyServer.getInstance().getPlayer(creatorUsername);

      if (player == null) return;

      player.sendMessage(new ComponentBuilder("Server created successfully! ID: " + gameServer.getId()).color(ChatColor.GREEN).create());

      for (ProxiedPlayer lobbyPlayer : ProxyServer.getInstance().getServerInfo("lobby").getPlayers()) {
        lobbyPlayer.sendMessage(new ComponentBuilder(creatorUsername + " started a game of " + gameServer.getType().string() + ". Join by running /game join " + gameServer.getId()).color(ChatColor.GREEN).create());
      }

      player.connect(gameServer.getServerInfo());
    } catch (KubernetesService.KubernetesException e) {
      ProxyServer.getInstance().getLogger().severe("Failed to start kubernetes pod. Printing stacktrace...");

      e.logError(ProxyServer.getInstance().getLogger());

      ProxiedPlayer player = ProxyServer.getInstance().getPlayer(creatorUsername);

      if (player == null) return;

      player.sendMessage(new ComponentBuilder("Server failed to start. Please contact the server administrator.").color(ChatColor.DARK_RED).create());
    }
  }

  public void cleanServers() {
    for (String gameId : gameServers.keySet()) {
      getServer(gameId).getServerInfo().ping((ServerPing p, Throwable t) -> {
        if (t == null) {
          getServer(gameId).pingSuccess();
        } else if (getServer(gameId).pingFail() >= 10) {
          unregisterServer(gameId);
        }
      });
    }
  }

  /**
   * Get a server by id
   *
   * @param id
   * @return
   */
  public GameServer getServer(String id) {
    if (!gameServers.containsKey(id)) {
      return null;
    }

    return gameServers.get(id);
  }

  /**
   * Get all game server ids
   *
   * @return
   */
  public Collection<String> getAvailableGameIds() {
    return gameServers.keySet();
  }

  private void registerServer(GameServer gameServer, InetSocketAddress address) {
    ServerInfo info = ProxyServer.getInstance().constructServerInfo(gameServer.getId(), address, "BeansPlusPlus Server", false);

    ProxyServer.getInstance().getServers().put(gameServer.getId(), info); // register to proxy

    gameServers.put(gameServer.getId(), gameServer); // add to game manager
  }

  private void unregisterServer(String gameId) {
    gameServers.remove(gameId);
    ProxyServer.getInstance().getServers().remove(gameId);
  }

  /**
   * Generate unique 5 digit number
   *
   * @return
   */
  private String generateId() {
    String id = null;

    while (id == null || gameServers.containsKey(id)) {
      id = "" + (random.nextInt(90000) + 10000);
    }

    return id;
  }
}
