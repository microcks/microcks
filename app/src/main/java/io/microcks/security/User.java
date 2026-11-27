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

import java.util.Date;
import java.util.List;

/**
 * A person known to Microcks, created the first time they log in through the identity provider and refreshed at every
 * login. The identity provider remains the source of truth for the identity and the roles: they are recomputed from the
 * asserted claims at each login. Only {@code disabled} is owned by Microcks.
 * @author laurent
 */
@MongoEntity(collection = "user")
public class User {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   public String id;
   /** The identifier used as token subject and by memberships (eg. the preferred username). Unique. */
   public String username;
   /** The <code>sub</code> claim of the identity provider. */
   public String idpSubject;
   public String name;
   public String givenName;
   public String familyName;
   public String email;
   /** True when the identity provider asserted that the email is verified at last login. */
   public boolean emailVerified;
   /** Microcks roles (user, manager, admin) mapped from the claims at last login. */
   public List<String> roles;
   public Date createdAt;
   public Date lastLoginAt;
   /** A disabled user can no longer use tokens already issued, nor log in. */
   public boolean disabled;
}
