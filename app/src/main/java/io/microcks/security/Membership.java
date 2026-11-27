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
 * The membership of a user in a group, with the role held in this group. It targets a user either by the OIDC
 * <code>sub</code> (or the client id of a service account), or, as long as the user never logged in, by a verified
 * email: such a pending membership has no {@code subject} and is resolved on first login.
 * @author laurent
 */
@MongoEntity(collection = "membership")
public class Membership {

   /** Role of a plain member of a group. */
   public static final String ROLE_USER = "user";
   /** Role of a member managing the group content and memberships. */
   public static final String ROLE_MANAGER = "manager";

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   public String id;
   public String groupName;
   /** The group role: user or manager. */
   public String role;
   /** The OIDC subject; null while the membership is pending. */
   public String subject;
   /** Lower-cased email used to pre-provision a user; null for memberships created by subject. */
   public String email;

   /** @return True if the user is not known yet. */
   public boolean isPending() {
      return subject == null;
   }
}
