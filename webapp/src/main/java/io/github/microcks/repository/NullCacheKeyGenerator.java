package io.github.microcks.repository;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Component("nullCacheKeyGenerator")
public class NullCacheKeyGenerator implements KeyGenerator {

   @Override
   public Object generate(Object target, Method method, Object... params) {
      return Arrays.stream(params).map(
            param -> param != null ? param.toString() : "null"
         )
         .reduce((a, b) -> a + "::??::" + b)
         .orElseThrow(
            () -> new IllegalArgumentException("At least one parameter is required for key generation")
         )
         ;
   }
}
