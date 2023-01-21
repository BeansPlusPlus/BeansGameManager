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

  private static final int MAX_QUEUE_SIZE = 5;

  public static GameManager getInstance() {
    return GAME_MANAGER;
  }

  private final Random random = new Random();

  private final Map<String, GameServer> gameServers = new HashMap<>();

  private Plugin plugin;

  private Queue<KubernetesWorld> preGenWorlds = new LinkedList<>();
  private KubernetesWorld currentlyGeneratingWorld;

  /**
   * Set the game manager plugin instance
   * @param plugin
   */
  public void registerPlugin(Plugin plugin) {
    this.plugin = plugin;
  }

  /**
   * Pre-generate kubernetes pods and load chunks within the world border
   */
  public void preGenWorld() {
    try {
      if (currentlyGeneratingWorld == null) {
        if (gameServers.size() > 0 || preGenWorlds.size() >= MAX_QUEUE_SIZE) return;

        currentlyGeneratingWorld = createPreGen();
      }
      if (currentlyGeneratingWorld.isPreGenPaused() && gameServers.size() == 0) {
        ProxyServer.getInstance().getLogger().info("No games running. Un-pausing world pre-generation");
        currentlyGeneratingWorld.resumePreGen();
      }
      if (currentlyGeneratingWorld.isPreGenFinished()) {
        ProxyServer.getInstance().getLogger().info("World pre-generation finished");
        preGenWorlds.add(currentlyGeneratingWorld);
        currentlyGeneratingWorld = null;
      }
    } catch (GameServerException e) {
      e.logError();
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

      GameServer gameServer = new GameServer(type, getNextKubernetesWorld());

      pausePreGen();

      gameServer.start();

      registerServer(gameServer);

      player.sendMessage(new ComponentBuilder("Server created successfully! ID: " + gameServer.getId()).color(ChatColor.GREEN).create());
      for (ProxiedPlayer lobbyPlayer : ProxyServer.getInstance().getServerInfo("lobby").getPlayers()) {
        lobbyPlayer.sendMessage(new ComponentBuilder(creatorUsername + " started a game of " + gameServer.getType().string() + ". Join by running /game join " + gameServer.getId()).color(ChatColor.GREEN).create());
      }

      gameServer.connectPlayerToServer(player);
    } catch (GameServerException e) {
      ProxyServer.getInstance().getLogger().severe("Failed to start kubernetes pod. Printing stacktrace...");

      e.logError();

      ProxiedPlayer player = ProxyServer.getInstance().getPlayer(creatorUsername);

      if (player == null) return;

      player.sendMessage(new ComponentBuilder("Server failed to start. Please contact the server administrator.").color(ChatColor.DARK_RED).create());
    }
  }

  /**
   * Remove unregister servers that are turned off
   */
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
   * Get all game server ids available to join
   *
   * @return
   */
  public Collection<String> getAvailableGameIds() {
    return gameServers.keySet();
  }

  private KubernetesWorld createPreGen() throws GameServerException {
    ProxyServer.getInstance().getLogger().info("No world currently generating, queue less and desired, no game running. Starting world pre-generation");
    currentlyGeneratingWorld = new KubernetesWorld(generateId());
    currentlyGeneratingWorld.createPersistentVolumeClaim();
    currentlyGeneratingWorld.createPreGen();
    return currentlyGeneratingWorld;
  }

  private KubernetesWorld getNextKubernetesWorld() throws GameServerException {
    if (preGenWorlds.size() > 0) {
      return preGenWorlds.poll();
    } else {
      KubernetesWorld k8s = new KubernetesWorld(generateId());
      k8s.createPersistentVolumeClaim();

      return k8s;
    }
  }

  private void pausePreGen() throws GameServerException {
    if (currentlyGeneratingWorld != null) {
      ProxyServer.getInstance().getLogger().info("New game starting. Pausing world pre-generation");
      currentlyGeneratingWorld.pausePreGen();
    }
  }

  private void registerServer(GameServer gameServer) {
    gameServers.put(gameServer.getId(), gameServer);

    ServerInfo info = ProxyServer.getInstance().constructServerInfo(gameServer.getId(), gameServer.getAddress(), "BeansPlusPlus Server", false);
    ProxyServer.getInstance().getServers().put(gameServer.getId(), info);
  }

  private void unregisterServer(String gameId) {
    gameServers.remove(gameId);

    ProxyServer.getInstance().getServers().remove(gameId);
  }

  private String generateId() {
    String id = null;

    while (id == null || gameServers.containsKey(id)) {
      id = "" + (random.nextInt(900) + 100);
    }

    return id;
  }
}
