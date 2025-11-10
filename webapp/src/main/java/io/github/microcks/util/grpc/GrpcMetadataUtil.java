/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.util.grpc;

import io.github.microcks.util.script.HttpHeadersStringToStringsMap;
import io.github.microcks.util.script.StringToStringsMap;
import io.grpc.Context;
import io.grpc.Metadata;

import java.util.Set;

import com.nimbusds.jose.util.StandardCharset;

/**
 * Helper class containing utility to deal with GrpcMetadata.
 */
public class GrpcMetadataUtil {

   /** Key used to pass gRPC metadata from interceptor to server via context */
   public static final Context.Key<Metadata> METADATA_CTX_KEY = Context.key("grpc-metadata");

   /** Key for remote address metadata entry in metadata. */
   public static final Metadata.Key<String> REMOTE_ADDR_METADATA_KEY = Metadata.Key.of("remote-addr",
         Metadata.ASCII_STRING_MARSHALLER);


   private GrpcMetadataUtil() {
      // Private constructor to hide the implicit public one.
   }

   /**
    * Convert Metadata sent with a gRPC requests into a map of key-value pairs with metadata key to list of values.
    * 
    * @param metadata The gRPC Metadata to convert
    * @return A StringToStringsMap containing all Metadata key-value pairs
    */
   public static StringToStringsMap convertToMap(Metadata metadata) {
      StringToStringsMap result = new HttpHeadersStringToStringsMap();

      Set<String> keys = metadata.keys();
      for (String key : keys) {
         // Depending on the suffix of the key we either extract binary values or
         // ASCII string values.
         if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
            // binary metadata entry
            Metadata.Key<byte[]> metadataKey = Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER);
            Iterable<byte[]> values = metadata.getAll(metadataKey);

            for (byte[] value : values) {
               String stringValue = new String(value, StandardCharset.UTF_8);
               result.add(key, stringValue);
            }
         } else {
            Metadata.Key<String> metadataKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
            Iterable<String> values = metadata.getAll(metadataKey);
            for (String value : values) {
               result.add(key, value);
            }
         }
      }

      return result;
   }
}
