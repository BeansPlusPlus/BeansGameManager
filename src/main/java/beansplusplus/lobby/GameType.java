package beansplusplus.lobby;

public enum GameType {
  HUNTER_VS_SPEEDRUNNER("https://saggyresourcepack.blob.core.windows.net/www/SpeedrunnerVsHunter-1.0.jar");

  private String jarURL;

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
}
