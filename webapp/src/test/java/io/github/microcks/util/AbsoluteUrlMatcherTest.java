package io.github.microcks.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AbsoluteUrlMatcherTest {
  @ParameterizedTest
  @ValueSource(strings = {
    "https://www.google.com/search?client=firefox-b-e&q=absolute+urls",
    "https://github.com/apoorva256/microcks",
    "file:///Users/unicorn.jpg"
  })
  void matches_ShouldReturnTrueForAbsoluteUrls(String absoluteUrl){
    assertTrue(AbsoluteUrlMatcher.matches(absoluteUrl));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "//www.google.com/",
    "index.html",
    "../../file.js"
  })
  void matches_ShouldReturnFalseForNonAbsoluteUrls(String nonAbsoluteUrl){
    assertFalse(AbsoluteUrlMatcher.matches(nonAbsoluteUrl));
  }
}
