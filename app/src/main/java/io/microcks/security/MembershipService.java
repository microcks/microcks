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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and maintains group memberships. Group paths of a user are computed from the store on each authenticated
 * request, through a short cache, so that a membership change is effective quickly without reissuing tokens (nothing
 * about groups travels in the Microcks token nor comes from the identity provider).
 * @author laurent
 */
@ApplicationScoped
public class MembershipService {

   /** The prefix of the group paths checked by the {@code AuthorizationChecker}. */
   public static final String GROUP_PATH_PREFIX = "/microcks/";

   private static final long CACHE_TTL_MS = 30_000L;

   @Inject
   MembershipRepository memberships;

   @Inject
   GroupRepository groups;

   @Inject
   UserRepository users;

   private final Map<String, CachedPaths> cache = new ConcurrentHashMap<>();

   private record CachedPaths(long expiresAt, List<String> paths) {
   }

   /**
    * Compute the group paths (<code>/microcks/&lt;role&gt;/&lt;group&gt;</code>) held by a subject. A manager of a
    * group is also a user of it.
    * @param subject The OIDC subject or service account client id
    * @return The paths, possibly empty
    */
   public List<String> groupPaths(String subject) {
      if (subject == null) {
         return List.of();
      }
      CachedPaths cached = cache.get(subject);
      if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
         return cached.paths();
      }
      List<String> paths = new ArrayList<>();
      for (Membership m : memberships.findBySubject(subject)) {
         if (Membership.ROLE_MANAGER.equals(m.role)) {
            paths.add(path(Membership.ROLE_MANAGER, m.groupName));
         }
         paths.add(path(Membership.ROLE_USER, m.groupName));
      }
      List<String> result = paths.stream().distinct().toList();
      cache.put(subject, new CachedPaths(System.currentTimeMillis() + CACHE_TTL_MS, result));
      return result;
   }

   /**
    * Bind the pending memberships pre-provisioned for an email to the subject who just logged in. Only to be called
    * when the identity provider asserts that the email is verified.
    * @param subject The OIDC subject
    * @param email   The verified email
    * @return The number of memberships resolved
    */
   public int claimPending(String subject, String email) {
      if (subject == null || email == null || email.isBlank()) {
         return 0;
      }
      int resolved = 0;
      for (Membership m : memberships.findPendingByEmail(email.toLowerCase(Locale.ROOT))) {
         m.subject = subject;
         memberships.update(m);
         resolved++;
      }
      if (resolved > 0) {
         cache.remove(subject);
      }
      return resolved;
   }

   /**
    * Add a user to an existing group, either by subject or by email.
    * @param groupName The group name
    * @param role      user or manager
    * @param subject   The OIDC subject, or null
    * @param email     The email to pre-provision, used when there is no subject; resolved at once if a known user owns
    *                  this verified email
    * @return The created membership, or the existing one with the same target and role
    * @throws IllegalArgumentException If the group does not exist or the arguments are invalid
    */
   public Membership addMember(String groupName, String role, String subject, String email) {
      if (groups.findByName(groupName) == null) {
         throw new IllegalArgumentException("Unknown group " + groupName);
      }
      if (!Membership.ROLE_USER.equals(role) && !Membership.ROLE_MANAGER.equals(role)) {
         throw new IllegalArgumentException("Role must be user or manager");
      }
      boolean hasSubject = subject != null && !subject.isBlank();
      boolean hasEmail = email != null && !email.isBlank();
      if (hasSubject == hasEmail) {
         throw new IllegalArgumentException("Provide either a subject or an email");
      }
      String normalizedEmail = hasEmail ? email.trim().toLowerCase(Locale.ROOT) : null;
      String normalizedSubject = hasSubject ? subject.trim() : null;
      if (!hasSubject) {
         // The person already logged in: bind to its account right away rather than leaving a pending membership.
         List<User> known = users.findVerifiedByEmail(normalizedEmail);
         if (known.size() == 1) {
            normalizedSubject = known.get(0).username;
            normalizedEmail = null;
            hasSubject = true;
         }
      }

      for (Membership existing : memberships.findByGroupName(groupName)) {
         boolean sameTarget = hasSubject ? normalizedSubject.equals(existing.subject)
               : normalizedEmail.equals(existing.email) && existing.isPending();
         if (sameTarget && role.equals(existing.role)) {
            return existing;
         }
      }
      Membership membership = new Membership();
      membership.groupName = groupName;
      membership.role = role;
      membership.subject = normalizedSubject;
      membership.email = normalizedEmail;
      memberships.persist(membership);
      invalidate(normalizedSubject);
      return membership;
   }

   /**
    * Remove a membership.
    * @param membership The membership to remove
    */
   public void removeMember(Membership membership) {
      memberships.deleteById(membership.id);
      invalidate(membership.subject);
   }

   /**
    * Delete a group and all its memberships.
    * @param groupName The group name
    */
   public void deleteGroup(String groupName) {
      memberships.findByGroupName(groupName).forEach(m -> invalidate(m.subject));
      memberships.deleteByGroupName(groupName);
      Group group = groups.findByName(groupName);
      if (group != null) {
         groups.deleteById(group.id);
      }
   }

   private void invalidate(String subject) {
      if (subject != null) {
         cache.remove(subject);
      }
   }

   private static String path(String role, String groupName) {
      return GROUP_PATH_PREFIX + role + "/" + groupName;
   }
}
