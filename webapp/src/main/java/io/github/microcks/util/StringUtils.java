package io.github.microcks.util;

public class StringUtils {

  /**
   * Checks if a String is empty (""), null or whitespace only.
   * @param string the String to check, may be null
   * @return true if the String is null, empty or whitespace only
   */
  public static boolean isBlankString(String string) {
    return string == null || string.trim().isEmpty();
  }

  /**
   * Returns either the passed in String, or if the String is whitespace, empty ("") or null, the value of defaultString.
   * @param string the String to check, may be null
   * @param defaultString the default String to return if the input is whitespace, empty ("") or null, may be null
   * @return the passed in String, or the default
   */
  public static String defaultIfBlank(String string, String defaultString) {
    if(isBlankString(string)) {
      return defaultString;
    } else {
      return string;
    }
  }
}
