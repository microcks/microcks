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
package io.github.microcks.util.el;

import io.github.microcks.domain.Request;
import io.github.microcks.util.el.function.ELFunction;
import io.github.microcks.util.el.function.NowELFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for EvaluationContext class.
 * @author laurent
 */
class EvaluationContextTest {

   @Test
   void testVariableRegistrationAndLookup() {
      // Create a context, register and retrieve a variable.
      EvaluationContext context = new EvaluationContext();
      context.setVariable("request", new Request());

      Object requestObj = context.lookupVariable("request");
      assertTrue(requestObj instanceof Request);
   }

   @Test
   void testFunctionRegistrationAndLookup() throws Exception {
      // Create a context, register and retrieve a function.
      EvaluationContext context = new EvaluationContext();
      context.registerFunction("now", NowELFunction.class);

      Class<ELFunction> functionClazz = context.lookupFunction("now");
      ELFunction function = functionClazz.newInstance();

      assertEquals(NowELFunction.class, function.getClass());

      // Test failure case.
      functionClazz = context.lookupFunction("never");
      assertNull(functionClazz);
   }
}
