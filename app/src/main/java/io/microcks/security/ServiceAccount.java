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
package io.microcks.security;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.util.List;

/**
 * A non-human identity (minion, CI, CLI) authenticating with client credentials. Only a hash of the secret is stored.
 * @author laurent
 */
@MongoEntity(collection = "serviceAccount")
public class ServiceAccount {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   public String id;
   public String clientId;
   public String secretHash;
   public String description;
   public List<String> roles;
}
