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
package io.github.microcks.util.script;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for the equals/hashCode contract of StringToStringsMap, in particular the mode switch driven by
 * the equalsOnThis flag.
 * @author laurent
 */
class StringToStringsMapTest {

   @Test
   void testEqualsAndHashCodeAreContentBasedByDefault() {
      StringToStringsMap a = new StringToStringsMap();
      a.add("foo", "bar");
      StringToStringsMap b = new StringToStringsMap();
      b.add("foo", "bar");

      // Default mode delegates to HashMap, which compares entry sets by content.
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());

      // Two content-equal instances collapse to a single HashSet entry.
      Set<StringToStringsMap> set = new HashSet<>();
      set.add(a);
      assertTrue(set.contains(b));
      set.add(b);
      assertEquals(1, set.size());
   }

   @Test
   void testEqualsOnThisTrueUsesIdentitySemantics() {
      StringToStringsMap a = new StringToStringsMap();
      a.add("foo", "bar");
      a.setEqualsOnThis(true);
      StringToStringsMap b = new StringToStringsMap();
      b.add("foo", "bar");
      b.setEqualsOnThis(true);

      // Reference mode: two distinct instances are not equal even though content matches.
      assertNotEquals(a, b);

      // hashCode must mirror the equals semantics; in reference mode that means returning
      // the identity hash, which is what the override is supposed to do. Asserting against
      // System.identityHashCode directly avoids relying on identity-hash collision rates.
      assertEquals(System.identityHashCode(a), a.hashCode());
      assertEquals(System.identityHashCode(b), b.hashCode());

      // The two distinct instances must remain separate entries in a HashSet, otherwise
      // a content-based hash would collide them and contains() would lie.
      Set<StringToStringsMap> set = new HashSet<>();
      set.add(a);
      assertFalse(set.contains(b));
      set.add(b);
      assertEquals(2, set.size());
   }

   @Test
   void testEqualsOnThisCanBeToggledBackToContentMode() {
      StringToStringsMap a = new StringToStringsMap();
      a.add("foo", "bar");
      StringToStringsMap b = new StringToStringsMap();
      b.add("foo", "bar");

      // Toggle on: distinct references, identity-based hashing.
      a.setEqualsOnThis(true);
      b.setEqualsOnThis(true);
      assertNotEquals(a, b);
      assertEquals(System.identityHashCode(a), a.hashCode());
      assertEquals(System.identityHashCode(b), b.hashCode());

      // Toggle back off: content-based equality and hashing are restored.
      a.setEqualsOnThis(false);
      b.setEqualsOnThis(false);
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
   }
}
