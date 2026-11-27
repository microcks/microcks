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

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;

import java.util.concurrent.Executor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

/**
 * A GRPC CallCredentials implementation adding token (Bearer or custom type) as call header.
 * @author laurent
 */
public class TokenCallCredentials extends CallCredentials {

   public static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("Authorization",
         ASCII_STRING_MARSHALLER);

   public static final String BEARER_TYPE = "Bearer ";

   private final String token;
   private final String tokenHeader;

   public TokenCallCredentials(String token, String tokenHeader) {
      this.token = token;
      this.tokenHeader = tokenHeader;
   }

   @Override
   public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier metadataApplier) {
      executor.execute(() -> {
         try {
            Metadata headers = new Metadata();
            // If no token header provided, assume it's an Authorization with Bearer type.
            if (tokenHeader == null) {
               headers.put(AUTHORIZATION_METADATA_KEY, BEARER_TYPE + token);
            } else {
               headers.put(Metadata.Key.of(tokenHeader, ASCII_STRING_MARSHALLER), token);
            }
            metadataApplier.apply(headers);
         } catch (Throwable e) {
            metadataApplier.fail(Status.UNAUTHENTICATED.withCause(e));
         }
      });
   }
}
