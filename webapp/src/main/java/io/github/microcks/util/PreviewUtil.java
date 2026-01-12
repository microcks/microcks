package io.github.microcks.util;

import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Service;

/**
 * Utility class for building and tagging preview versions of services.
 */
public class PreviewUtil {

   /** Private constructor to hide the implicit one. */
   private PreviewUtil() {
   }

   /**
    * Builds a version string by appending a preview slug to the base version if provided.
    *
    * @param version     The base version string, expected to follow Semantic Versioning.
    * @param previewSlug An optional string representing a preview or additional identifier. If null or blank, it will
    *                    not be appended to the version.
    * @return The constructed version string, with the preview slug appended if applicable.
    */
   public static String buildVersion(String version, String previewSlug) {
      // If a preview slug is provided and not blank, append it to the version string.
      if (previewSlug != null && !previewSlug.isBlank()) {
         return version + "-" + previewSlug;
      }

      // Return the base version string if no preview slug is provided.
      return version;
   }

   /**
    * Tags a service as a preview by appending a preview slug to its version and adding a metadata label.
    *
    * @param service     The service to be tagged as a preview.
    * @param previewSlug An optional string representing a preview or additional identifier. If null or blank, the
    *                    service remains unchanged.
    * @return The updated service with the preview slug appended to its version and a metadata label indicating preview.
    */
   public static Service tagPreviewService(Service service, String previewSlug) {
      // If a preview slug is provided and not blank, append it to the service name.
      if (previewSlug != null && !previewSlug.isBlank()) {
         service.setVersion(buildVersion(service.getVersion(), previewSlug));
         // Add the label to indicate preview.
         Metadata metadata = service.getMetadata();
         if (metadata == null) {
            metadata = new Metadata();
            service.setMetadata(metadata);
         }
         metadata.setLabel("preview", "true");
      }
      // Return the base service if no preview slug is provided.
      return service;
   }
}
