package io.github.microcks.util;

import java.util.Set;
import java.util.stream.Collectors;

public class Sanitizer {
  private static final String ALLOWED_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ$-_.+!*'(),";
  private static Set<Character> ALLOWED_CHARS_SET = ALLOWED_CHARS
      .chars()
      .mapToObj(c -> (char) c)
      .collect(Collectors.toSet());
  private static final Character REPLACE_CHAR = '-';

  public static String urlSanitize(String name) {
    StringBuilder sanitized = new StringBuilder();

    for (char c : name.toCharArray()) {
      sanitized.append(ALLOWED_CHARS_SET.contains(c) ? c : REPLACE_CHAR);
    }

    return sanitized.toString();
  }
}
