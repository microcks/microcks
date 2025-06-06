package io.github.microcks.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Utf8ContentTypeChecker Tests")
public class Utf8ContentTypeCheckerTest {

   @Nested
   @DisplayName("Valid UTF-8 Encodable Content Types")
   class ValidContentTypes {
      @Test
      @DisplayName("Should match all known valid content types")
      void shouldMatchValidContentTypes() {
         List<String> validTypes = List.of("text/plain", "text/html", "application/json", "application/vnd.api+json",
               "application/soap+xml", "application/javascript", "application/x-www-form-urlencoded");

         for (String type : validTypes) {
            assertTrue(Utf8ContentTypeChecker.isUtf8Encodable(type), "Expected to match: " + type);
         }
      }
   }

   @Nested
   @DisplayName("Invalid UTF-8 Encodable Content Types")
   class InvalidContentTypes {
      @Test
      @DisplayName("Should not match unsupported media types")
      void shouldRejectInvalidContentTypes() {
         List<String> invalidTypes = List.of("application/pdf", "image/png", "video/mp4", "audio/mpeg",
               "multipart/form-data");

         for (String type : invalidTypes) {
            assertFalse(Utf8ContentTypeChecker.isUtf8Encodable(type), "Expected to reject: " + type);
         }
      }
   }

   @Nested
   @DisplayName("Edge Cases")
   class EdgeCases {
      @Test
      @DisplayName("Should reject null, empty, and blank strings")
      void shouldRejectNullOrEmpty() {
         assertAll(() -> assertFalse(Utf8ContentTypeChecker.isUtf8Encodable(null), "Null should return false"),
               () -> assertFalse(Utf8ContentTypeChecker.isUtf8Encodable(""), "Empty string should return false"),
               () -> assertFalse(Utf8ContentTypeChecker.isUtf8Encodable("   "), "Blank string should return false"));
      }

      @Test
      @DisplayName("Should be case insensitive")
      void shouldMatchCaseInsensitive() {
         List<String> mixedCaseTypes = List.of("TEXT/PLAIN", "Application/JSON", "application/XML",
               "Application/Vnd.Api+Json");

         for (String type : mixedCaseTypes) {
            assertTrue(Utf8ContentTypeChecker.isUtf8Encodable(type),
                  "Expected to match case-insensitive type: " + type);
         }
      }

      @Test
      @DisplayName("Should ignore leading/trailing whitespace")
      void shouldTrimWhitespace() {
         assertAll(
               () -> assertTrue(Utf8ContentTypeChecker.isUtf8Encodable(" text/plain "), "Should match with whitespace"),
               () -> assertFalse(Utf8ContentTypeChecker.isUtf8Encodable(" image/png "),
                     "Should reject even with whitespace"));
      }
   }
}
