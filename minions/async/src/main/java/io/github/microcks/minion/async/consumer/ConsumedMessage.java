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
package io.github.microcks.minion.async.consumer;

import io.github.microcks.domain.Header;
import org.apache.avro.generic.GenericRecord;

import java.util.Set;

/**
 * This is a simple wrapper allowing to track a consumed messages from endpoint.
 * @author laurent
 */
public class ConsumedMessage {

   private long receivedAt;
   private byte[] payload;
   private GenericRecord payloadRecord;
   private Set<Header> headers;

   public long getReceivedAt() {
      return receivedAt;
   }

   public void setReceivedAt(long receivedAt) {
      this.receivedAt = receivedAt;
   }

   public byte[] getPayload() {
      return payload;
   }

   public void setPayload(byte[] payload) {
      this.payload = payload;
   }

   public GenericRecord getPayloadRecord() {
      return payloadRecord;
   }

   public void setPayloadRecord(GenericRecord payloadRecord) {
      this.payloadRecord = payloadRecord;
   }

   public Set<Header> getHeaders() {
      return headers;
   }

   public void setHeaders(Set<Header> headers) {
      this.headers = headers;
   }
}
