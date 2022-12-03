package beansplusplus.lobby;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class LobbyCommand extends Command {
  public LobbyCommand() {
    super("lobby", null);
  }

  public void execute(CommandSender sender, String[] args) {
    if (!(sender instanceof ProxiedPlayer)) {
      return;
    }

    ProxiedPlayer p = (ProxiedPlayer) sender;

    p.connect(ProxyServer.getInstance().getServerInfo("lobby"));
  }
}
