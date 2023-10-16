package io.github.microcks.util;

import java.util.regex.Pattern;

/**
 * Util class to Match a Absolute URL
 */
public class AbsoluteUrlMatcher {
  static final String regex = ".+://.*";

  static final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);

  public static boolean matches(String url){
    return pattern.matcher(url).matches();
  }
}
