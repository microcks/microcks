package io.github.microcks.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UTF8ContentTypeChecker Tests")
public class UTF8ContentTypeCheckerTest {

   @Nested
   @DisplayName("Valid UTF-8 Encodable Content Types")
   class ValidContentTypes {
      @Test
      @DisplayName("Should match all known valid content types")
      void shouldMatchValidContentTypes() {
         List<String> validTypes = List.of("text/plain", "text/html", "application/json", "application/vnd.api+json",
               "application/soap+xml", "application/javascript", "application/x-www-form-urlencoded", "application/xml",
               "text/xml", "application/json; charset=utf-8");

         for (String type : validTypes) {
            assertTrue(UTF8ContentTypeChecker.isUTF8Encodable(type), "Expected to match: " + type);
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
            assertFalse(UTF8ContentTypeChecker.isUTF8Encodable(type), "Expected to reject: " + type);
         }
      }
   }

   @Nested
   @DisplayName("Edge Cases")
   class EdgeCases {
      @Test
      @DisplayName("Should reject null, empty, and blank strings")
      void shouldRejectNullOrEmpty() {
         assertAll(() -> assertFalse(UTF8ContentTypeChecker.isUTF8Encodable(null), "Null should return false"),
               () -> assertFalse(UTF8ContentTypeChecker.isUTF8Encodable(""), "Empty string should return false"),
               () -> assertFalse(UTF8ContentTypeChecker.isUTF8Encodable("   "), "Blank string should return false"));
      }

      @Test
      @DisplayName("Should be case insensitive")
      void shouldMatchCaseInsensitive() {
         List<String> mixedCaseTypes = List.of("TEXT/PLAIN", "Application/JSON", "application/XML",
               "Application/Vnd.Api+Json");

         for (String type : mixedCaseTypes) {
            assertTrue(UTF8ContentTypeChecker.isUTF8Encodable(type),
                  "Expected to match case-insensitive type: " + type);
         }
      }

      @Test
      @DisplayName("Should ignore leading/trailing whitespace")
      void shouldTrimWhitespace() {
         assertAll(
               () -> assertTrue(UTF8ContentTypeChecker.isUTF8Encodable(" text/plain "), "Should match with whitespace"),
               () -> assertFalse(UTF8ContentTypeChecker.isUTF8Encodable(" image/png "),
                     "Should reject even with whitespace"));
      }
   }

   @Nested
   @DisplayName("Structured Syntax & Parameters")
   class StructuredSyntaxAndParameters {
      @Test
      @DisplayName("Should match +json and +xml structured syntax types")
      void shouldMatchStructuredSyntaxTypes() {
         List<String> structured = List.of("application/hal+json", "application/soap+xml",
               "application/vnd.custom.resource+json", "application/vnd.custom.resource+xml",
               "Application/Vnd.Custom.Resource+Json", // case-insensitive
               "application/problem+json" // RFC 7807 problem details
         );
         for (String type : structured) {
            assertTrue(UTF8ContentTypeChecker.isUTF8Encodable(type), "Expected structured suffix to match: " + type);
         }
      }

      @Test
      @DisplayName("Should match when parameters are present in various forms")
      void shouldMatchWithParameters() {
         List<String> withParams = List.of("application/json;charset=utf-8", "application/json; charset=UTF-8",
               "application/json;version=1; charset=utf-8", "application/hal+json; profile=some; charset=utf-8",
               "application/soap+xml;action=urn:foo;Charset=Utf-8", "text/plain; format=flowed; charset=iso-8859-1" // text/* always accepted even if non-utf charset declared
         );
         for (String type : withParams) {
            assertTrue(UTF8ContentTypeChecker.isUTF8Encodable(type), "Expected to match with parameters: " + type);
         }
      }

      @Test
      @DisplayName("Should reject media types that do not have +json/+xml even if they look similar")
      void shouldRejectInvalidStructuredSyntax() {
         List<String> invalid = List.of("application/hal+jsn", // typo suffix
               "application/soap+xmll", // typo suffix
               "application/vnd.foo+bin", // unsupported +bin suffix
               "application/pdf; charset=utf-8", "image/png; name=image.png");
         for (String type : invalid) {
            assertFalse(UTF8ContentTypeChecker.isUTF8Encodable(type), "Expected to reject invalid: " + type);
         }
      }

      @Test
      @DisplayName("Should handle excess internal whitespace around separators")
      void shouldHandleInternalWhitespace() {
         List<String> types = List.of("  application/json  ;  charset=utf-8  ", "text/plain   ;   format=flowed",
               "application/soap+xml   ;action=urn:foo" // no space before semicolon is fine
         );
         for (String type : types) {
            assertTrue(UTF8ContentTypeChecker.isUTF8Encodable(type), "Expected to match despite whitespace: " + type);
         }
      }
   }
}
