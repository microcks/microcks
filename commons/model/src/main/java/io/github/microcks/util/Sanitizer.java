package io.github.microcks.util;

import java.util.Set;
import java.util.stream.Collectors;

public class Sanitizer {
  private static final String ALLOWED_CHARS = "0123456789abcdefghIjklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ$-_.+!*'(),";
  private static Set<Character> allowedCharSet;
  private static final Character REPLACE_CHAR = '-';

  {
    allowedCharSet = ALLOWED_CHARS.chars()
        .mapToObj(c -> (char) c)
        .collect(Collectors.toSet());
  }

  public static String urlSanitize(String name) {
    StringBuilder sanitized = new StringBuilder();

    for (char c : name.toCharArray()) {
      sanitized.append(allowedCharSet.contains(c) ? c : REPLACE_CHAR);
    }

    return sanitized.toString();
  }
}
