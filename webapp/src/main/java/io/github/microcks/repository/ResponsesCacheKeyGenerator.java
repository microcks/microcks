package io.github.microcks.repository;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component("responsesCacheKeyGenerator")
public class ResponsesCacheKeyGenerator implements KeyGenerator {

   @Override
   public Object generate(Object target, Method method, Object... params) {
      // This is a more robust way to build a key from params.
      String operationId = (String) params[0];
      String dispatchCriteria = (params.length > 1 && params[1] != null) ? (String) params[1] : "NULL"; // Handle null

      // Build the key
      return operationId + "::" + dispatchCriteria;
   }
}
