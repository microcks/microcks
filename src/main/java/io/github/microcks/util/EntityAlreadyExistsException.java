package io.github.microcks.util;

/**
 * A typed exception telling that en entity with similar properties already exists into datastore.
 * @author laurent
 */
public class EntityAlreadyExistsException extends Exception {

   public EntityAlreadyExistsException(String message) {
      super(message);
   }

   public EntityAlreadyExistsException(String message, Throwable cause) {
      super(message, cause);
   }
}
