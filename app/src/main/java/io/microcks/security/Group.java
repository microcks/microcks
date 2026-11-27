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

/**
 * A Microcks group, the unit of tenancy. Its name is the value of the repository filter label (by default
 * <code>domain</code>) carried by the services and import jobs it owns. Groups and their memberships are internal to
 * Microcks and independent from the identity provider.
 * @author laurent
 */
@MongoEntity(collection = "userGroup")
public class Group {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   public String id;
   public String name;
   public String description;
}
