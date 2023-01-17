package beansplusplus.lobby;

import io.kubernetes.client.openapi.ApiException;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

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

  private Queue<KubernetesWorld> preGenWorlds = new LinkedList<>();
  private KubernetesWorld currentlyGeneratingWorld;

  public void registerPlugin(Plugin plugin) {
    this.plugin = plugin;
  }

  public void preGenWorld() {
    // to be run on a schedule
    try {
      if (currentlyGeneratingWorld == null) {
        if (preGenWorlds.size() < 5 && gameServers.size() == 0) {
          System.out.println("No world currently generating, queue less and desired, no game running. Starting world pre-generation");
          currentlyGeneratingWorld = new KubernetesWorld(generateId(), false);
        }
        return;
      }
      if (currentlyGeneratingWorld.isPreGenPaused() && gameServers.size() == 0) {
        System.out.println("No games running. Un-pausing world pre-generation");
        currentlyGeneratingWorld.resumePreGen();
      }
      if (currentlyGeneratingWorld.isPreGenFinished()) {
        System.out.println("World pre-generation finished");
        preGenWorlds.add(currentlyGeneratingWorld);
        currentlyGeneratingWorld = null;
      }
    } catch (ApiException e) {
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
      // get player
      ProxiedPlayer player = ProxyServer.getInstance().getPlayer(creatorUsername);
      if (player == null) return;

      // get world
      KubernetesWorld k8s;
      if (preGenWorlds.size() != 0) {
        k8s = preGenWorlds.poll();
      } else {
        player.sendMessage(new ComponentBuilder("Pre-generated world not available. Creating new world").color(ChatColor.RED).create());
        k8s = new KubernetesWorld(generateId(), true);
      }

      // create game
      GameServer gameServer = new GameServer(type, k8s);
      gameServers.put(gameServer.getId(), gameServer); // add to game manager

      // stop pregen
      /**
      if (currentlyGeneratingWorld != null) {
        System.out.println("New game starting. Pausing world pre-generation");
        currentlyGeneratingWorld.pausePreGen();
      }
       */

      // start and register game
      gameServer.start();
      ServerInfo info = ProxyServer.getInstance().constructServerInfo(gameServer.getId(), gameServer.getAddress(), "BeansPlusPlus Server", false);
      ProxyServer.getInstance().getServers().put(gameServer.getId(), info); // register to proxy

      // tell players about game
      player.sendMessage(new ComponentBuilder("Server created successfully! ID: " + gameServer.getId()).color(ChatColor.GREEN).create());
      for (ProxiedPlayer lobbyPlayer : ProxyServer.getInstance().getServerInfo("lobby").getPlayers()) {
        lobbyPlayer.sendMessage(new ComponentBuilder(creatorUsername + " started a game of " + gameServer.getType().string() + ". Join by running /game join " + gameServer.getId()).color(ChatColor.GREEN).create());
      }

      // move game creator to game
      player.connect(gameServer.getServerInfo());

    } catch (KubernetesWorld.KubernetesException e) {
      ProxyServer.getInstance().getLogger().severe("Failed to start kubernetes pod. Printing stacktrace...");

      e.logError(ProxyServer.getInstance().getLogger());

      ProxiedPlayer player = ProxyServer.getInstance().getPlayer(creatorUsername);

      if (player == null) return;

      player.sendMessage(new ComponentBuilder("Server failed to start. Please contact the server administrator.").color(ChatColor.DARK_RED).create());
    } catch (ApiException e) {
      e.printStackTrace();
    }
  }

  public void cleanServers() {
    for (GameServer gameServer : gameServers.values()) {
      if (gameServer.isFinished()) {
        unregisterServer(gameServer.getId());
      }
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

  private void unregisterServer(String gameId) {
    gameServers.remove(gameId);
    ProxyServer.getInstance().getServers().remove(gameId);
  }

  /**
   * Generate unique 3 digit number
   *
   * @return
   */
  private String generateId() {
    String id = null;

    while (id == null || gameServers.containsKey(id)) {
      id = "" + (random.nextInt(900) + 100);
    }

    return id;
  }
}
