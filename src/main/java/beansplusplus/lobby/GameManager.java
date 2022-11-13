package beansplusplus.lobby;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.net.InetSocketAddress;
import java.util.*;

public class GameManager {
  private static final GameManager GAME_MANAGER = new GameManager();

  public static GameManager getInstance() {
    return GAME_MANAGER;
  }

  private final Random random = new Random();

  private final Map<String, GameServer> gameServers = new HashMap<>();

  /**
   * Create a new server by game type
   *
   * @param type
   * @return
   */
  public GameServer createServer(GameType type) {
    GameServer gameServer = new GameServer(type, generateId());

    InetSocketAddress address = startServer();

    registerServer(gameServer, address);

    return gameServer;
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

  /**
   * Start up a new server
   *
   * @return
   */
  private InetSocketAddress startServer() {
    // TODO: api call to create server? - returns something to allow us to connect to it.

    // at the moment this is just a dummy IP

    return InetSocketAddress.createUnresolved("localhost", 6662);
  }

  private void registerServer(GameServer gameServer, InetSocketAddress address) {
    ServerInfo info = ProxyServer.getInstance().constructServerInfo(gameServer.getId(), address, "BeansPlusPlus Server", false);

    ProxyServer.getInstance().getServers().put(gameServer.getId(), info); // register to proxy

    gameServers.put(gameServer.getId(), gameServer); // add to game manager
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
