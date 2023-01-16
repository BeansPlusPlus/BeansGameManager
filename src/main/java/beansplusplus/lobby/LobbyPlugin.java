package beansplusplus.lobby;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public class LobbyPlugin extends Plugin {
  @Override
  public void onEnable() {
    getLogger().info("Enabled LobbyPlugin");

    GameManager.getInstance().registerPlugin(this);

    ProxyServer.getInstance().getPluginManager().registerCommand(this, new GameCommand());
    ProxyServer.getInstance().getPluginManager().registerCommand(this, new LobbyCommand());

    ProxyServer.getInstance().getScheduler().schedule(this, () -> GameManager.getInstance().cleanServers(), 0, 5, TimeUnit.SECONDS);
    ProxyServer.getInstance().getScheduler().schedule(this, () -> GameManager.getInstance().preGenWorld(), 0, 5, TimeUnit.SECONDS);
  }
}