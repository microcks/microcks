package io.github.microcks.util.script;

import io.roastedroot.quickjs4j.core.ScriptCache;
import org.codehaus.groovy.runtime.memoize.LRUCache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LRUScriptCache implements ScriptCache {
   private final LRUCache<String, byte[]> cache;
   private final MessageDigest messageDigest;

   public LRUScriptCache(int maxSize) {
      cache = new LRUCache<>(maxSize);
      try {
         messageDigest = MessageDigest.getInstance("MD5");
      } catch (NoSuchAlgorithmException e) {
         throw new RuntimeException(e);
      }
   }

   public boolean exists(byte[] code) {
      var key = messageDigest.digest(code);
      return cache.get(new String(key, StandardCharsets.UTF_8)) != null;
   }

   public void set(byte[] code, byte[] compiled) {
      var key = messageDigest.digest(code);
      cache.put(new String(key, StandardCharsets.UTF_8), compiled);
   }

   public byte[] get(byte[] code) {
      var key = messageDigest.digest(code);
      return cache.get(new String(key, StandardCharsets.UTF_8));
   }
}
