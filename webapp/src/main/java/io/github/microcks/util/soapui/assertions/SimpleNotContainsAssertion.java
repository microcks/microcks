package io.github.microcks.util.soapui.assertions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple assertion that checks absence of a specified token in response content.
 * @author laurent
 */
public class SimpleNotContainsAssertion extends SimpleContainsAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SimpleNotContainsAssertion.class);


   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {
      log.debug("Asserting Simple not contains for {}. Apply a 'contains' and then revert result", token);
      AssertionStatus containsStatus = super.assertResponse(exchange, context);
      if (containsStatus == AssertionStatus.VALID) {
         errorMessage = "Response contains token [" + token + "]";
         return AssertionStatus.FAILED;
      }
      if (containsStatus == AssertionStatus.FAILED) {
         errorMessage = null;
         return AssertionStatus.VALID;
      }
      errorMessage = null;
      return AssertionStatus.UNKNOWN;
   }
}
