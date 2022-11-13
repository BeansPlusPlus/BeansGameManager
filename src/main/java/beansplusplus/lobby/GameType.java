package beansplusplus.lobby;

public enum GameType {
  HUNTER_VS_SPEEDRUNNER;

  public String string() {
    return toString().toLowerCase();
  }

  /**
   * Get GameType by string
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
