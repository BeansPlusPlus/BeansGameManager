package beansplusplus.lobby;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum GameType {
  HUNTER_VS_SPEEDRUNNER("https://saggyresourcepack.blob.core.windows.net/www/SpeedrunnerVsHunter-1.0.jar"),
  BLOCK_SHUFFLE("https://saggyresourcepack.blob.core.windows.net/www/BlockShuffle-1.0.jar");

  private final String jarURL;

  GameType(String jarURL) {
    this.jarURL = jarURL;
  }

  public String string() {
    return toString().toLowerCase();
  }

  public String getJarURL() {
    return jarURL;
  }

  /**
   * Get GameType by string
   *
   * @param s
   * @return
   */
  public static GameType byString(String s) {
    try {
      return valueOf(s.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * A string list of all game types
   * @return
   */
  public static List<String> allGameStrings() {
    return Stream.of(GameType.values()).map((t) -> t.string()).collect(Collectors.toList());
  }
}
