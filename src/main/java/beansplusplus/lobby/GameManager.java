package beansplusplus.lobby;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class GameManager {
  private static final GameManager GAME_MANAGER = new GameManager();
  private static final KubernetesManager K8S_MANAGER = new KubernetesManager();

  public static GameManager getInstance() {
    return GAME_MANAGER;
  }

  private Plugin plugin;

  /**
   * Set the game manager plugin instance
   * @param plugin
   */
  public void registerPlugin(Plugin plugin) {
    this.plugin = plugin;
  }

  public void tick() {
    try {
      Map<String, InetSocketAddress> k8sGames = K8S_MANAGER.getGames();
      // add new games to bungeecord
      for (String id : k8sGames.keySet()) {
        if (k8sGames.get(id) == null) {
          continue;
        }
        if (!ProxyServer.getInstance().getServers().containsKey(id)) {
          ServerInfo info = ProxyServer.getInstance().constructServerInfo(id, k8sGames.get(id), "BeansPlusPlus Server", false);
          ProxyServer.getInstance().getServers().put(id, info);
        }
      }
      // remove old games from bungeecord
      for (String id : ProxyServer.getInstance().getServers().keySet()) {
        if (id.equals("lobby")) {
          continue;
        }
        if (!k8sGames.containsKey(id) || k8sGames.get(id) == null) {
          ProxyServer.getInstance().getServers().remove(id);
        }
      }

      // tick kubernetes manager
      K8S_MANAGER.tick();
    } catch (GameServerException e) {
      e.printStackTrace();
    }
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
    try {
      ProxiedPlayer player = ProxyServer.getInstance().getPlayer(creatorUsername);
      if (player == null) return;

      // tell everyone a game is being created
      for (ProxiedPlayer lobbyPlayer : ProxyServer.getInstance().getServerInfo("lobby").getPlayers()) {
        lobbyPlayer.sendMessage(new ComponentBuilder(creatorUsername + " is creating a game of " + type.string()).color(ChatColor.GREEN).create());
      }

      // create the game
      String id = K8S_MANAGER.createGame(type.getJarURL());

      // wait for game to create
      boolean found = false;
      for (int i = 0; i < 300; i++) {
        Thread.sleep(1000);
        Map<String, InetSocketAddress> games = K8S_MANAGER.getGames();
        if (ProxyServer.getInstance().getServers().containsKey(id)) {
          found = true;
          break;
        }
      }
      if (!found) {
        throw new GameServerException("World hasn't started after 5 minutes");
      }

      // Tell everyone a game has been created
      player.sendMessage(new ComponentBuilder("Server created successfully! ID: " + id).color(ChatColor.GREEN).create());
      for (ProxiedPlayer lobbyPlayer : ProxyServer.getInstance().getServerInfo("lobby").getPlayers()) {
        lobbyPlayer.sendMessage(new ComponentBuilder(creatorUsername + " started a game of " + type.string() + ". Join by running /game join " + id).color(ChatColor.GREEN).create());
      }

      // Connect the creator to the game
      player.connect(ProxyServer.getInstance().getServerInfo(id));

    } catch (GameServerException e) {
      ProxyServer.getInstance().getLogger().severe("Failed to start kubernetes pod. Printing stacktrace...");

      e.logError();

      ProxiedPlayer player = ProxyServer.getInstance().getPlayer(creatorUsername);

      if (player == null) return;

      player.sendMessage(new ComponentBuilder("Server failed to start. Please contact the server administrator.").color(ChatColor.DARK_RED).create());
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Get all game server ids available to join
   *
   * @return
   */
  public Set<String> getAvailableGameIds() {
    Set<String> ids = new HashSet<>(ProxyServer.getInstance().getServers().keySet());
    ids.remove("lobby");
    return ids;
  }
}
