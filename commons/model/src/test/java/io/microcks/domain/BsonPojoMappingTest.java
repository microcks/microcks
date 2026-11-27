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
package io.microcks.domain;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonType;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.EnumCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the Spring-free domain model can be persisted through the BSON POJO codec, keeping the document shape
 * (ObjectId under {@code _id}, plain field names) that Spring Data produced in Microcks 1.x.
 * @author laurent
 */
class BsonPojoMappingTest {

   private final CodecRegistry registry = CodecRegistries.fromProviders(new ValueCodecProvider(),
         new BsonValueCodecProvider(), new EnumCodecProvider(), PojoCodecProvider.builder().automatic(true).build());

   private <T> BsonDocument encode(T entity, Class<T> type) {
      BsonDocument document = new BsonDocument();
      registry.get(type).encode(new BsonDocumentWriter(document), entity, EncoderContext.builder().build());
      return document;
   }

   private <T> T decode(BsonDocument document, Class<T> type) {
      return registry.get(type).decode(new BsonDocumentReader(document), DecoderContext.builder().build());
   }

   @Test
   void serviceRoundTripKeepsObjectIdAndFields() {
      Service service = new Service();
      service.setId(new ObjectId().toHexString());
      service.setName("Petstore API");
      service.setVersion("1.0");
      service.setType(ServiceType.REST);
      Metadata metadata = new Metadata();
      metadata.setLabels(Map.of("domain", "pets"));
      service.setMetadata(metadata);
      Operation operation = new Operation();
      operation.setName("GET /pets");
      operation.setMethod("GET");
      service.addOperation(operation);

      BsonDocument document = encode(service, Service.class);

      assertEquals(BsonType.OBJECT_ID, document.get("_id").getBsonType());
      assertFalse(document.containsKey("id"));
      assertEquals("REST", document.getString("type").getValue());

      Service decoded = decode(document, Service.class);
      assertEquals(service.getId(), decoded.getId());
      assertEquals("Petstore API", decoded.getName());
      assertEquals(ServiceType.REST, decoded.getType());
      assertEquals("pets", decoded.getMetadata().getLabels().get("domain"));
      assertEquals(1, decoded.getOperations().size());
      assertEquals("GET /pets", decoded.getOperations().get(0).getName());
   }

   @Test
   void allPersistedEntitiesAreEncodableAndDecodable() throws Exception {
      for (Class<?> type : new Class<?>[] { Service.class, Resource.class, Request.class, Response.class,
            EventMessage.class, Secret.class, TestResult.class, WebhookRegistration.class, ImportJob.class,
            GenericResource.class, DailyStatistic.class, TestConformanceMetric.class, ServiceState.class }) {
         Object entity = type.getDeclaredConstructor().newInstance();
         BsonDocument document = new BsonDocument();
         @SuppressWarnings("unchecked")
         org.bson.codecs.Codec<Object> codec = (org.bson.codecs.Codec<Object>) registry.get(type);
         codec.encode(new BsonDocumentWriter(document), entity, EncoderContext.builder().build());
         Object decoded = codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
         assertNotNull(decoded, type.getSimpleName());
      }
   }

   @Test
   void collectionNamesMatchMicrocks1xDefaults() throws Exception {
      for (String name : new String[] { "Service", "Resource", "Request", "Response", "EventMessage", "Secret",
            "TestResult", "WebhookRegistration", "ImportJob", "GenericResource", "DailyStatistic",
            "TestConformanceMetric", "ServiceState" }) {
         Class<?> type = Class.forName("io.microcks.domain." + name);
         String expected = Character.toLowerCase(name.charAt(0)) + name.substring(1);
         assertEquals(expected, type.getAnnotation(MongoEntity.class).collection(), name);
      }
   }

   @Test
   void legacyClassFieldIsIgnoredOnRead() {
      BsonDocument legacy = new BsonDocument();
      legacy.put("_id", new BsonObjectId(new ObjectId()));
      legacy.put("_class", new BsonString("io.github.microcks.domain.Service"));
      legacy.put("name", new BsonString("Legacy"));

      Service decoded = decode(legacy, Service.class);

      assertNotNull(decoded.getId());
      assertTrue(ObjectId.isValid(decoded.getId()));
      assertEquals("Legacy", decoded.getName());
   }
}
