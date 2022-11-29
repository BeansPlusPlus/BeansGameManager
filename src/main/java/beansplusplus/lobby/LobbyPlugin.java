package beansplusplus.lobby;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

public class LobbyPlugin extends Plugin {
  @Override
  public void onEnable() {
    try {
      KubernetesService.setupClient();
      getLogger().info("Enabled LobbyPlugin successfully");

      ProxyServer.getInstance().getPluginManager().registerCommand(this, new GameCommand());
    } catch (KubernetesService.KubernetesException e) {
      getLogger().severe("Failed to load Kubernetes client...");

      e.logStacktrace(getLogger());

      throw new Error(e); // fail plugin load.
    }
  }
}