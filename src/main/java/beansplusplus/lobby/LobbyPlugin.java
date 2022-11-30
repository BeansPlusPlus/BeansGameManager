package beansplusplus.lobby;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

public class LobbyPlugin extends Plugin {
  @Override
  public void onEnable() {
    getLogger().info("Enabled LobbyPlugin");

    GameManager.getInstance().registerPlugin(this);

    ProxyServer.getInstance().getPluginManager().registerCommand(this, new GameCommand());
  }
}