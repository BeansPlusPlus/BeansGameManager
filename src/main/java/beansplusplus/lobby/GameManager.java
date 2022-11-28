package beansplusplus.lobby;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Yaml;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

public class GameManager {
  private static final GameManager GAME_MANAGER = new GameManager();

  public static GameManager getInstance() {
    return GAME_MANAGER;
  }

  private final Random random = new Random();

  private final Map<String, GameServer> gameServers = new HashMap<>();

  private static V1Pod podTemplate = null;

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

    System.out.println("Starting server...");

    try {

      Yaml.addModelMap("v1", "Pod", V1Pod.class); // idk if this is needed

      if (podTemplate == null) {
        podTemplate = (V1Pod)Yaml.load(new File("pod.yaml"));
      }

      ApiClient client = Config.defaultClient();
      Configuration.setDefaultApiClient(client);

      CoreV1Api api = new CoreV1Api();

      podTemplate.setMetadata(new V1ObjectMetaBuilder().withName("beans-mini-game-1").build());

      System.out.println(Yaml.dump(podTemplate));

      V1Pod createdPod = api.createNamespacedPod("beans-mini-games", podTemplate, null, null, null, null);

      return InetSocketAddress.createUnresolved(createdPod.getStatus().getPodIP(), 25565);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ApiException e) {
      e.printStackTrace();
    }
    System.out.println("Retuning NULL");
    return null;
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
