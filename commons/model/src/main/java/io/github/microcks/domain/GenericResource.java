package io.github.microcks.domain;

import org.bson.Document;
import org.springframework.data.annotation.Id;

/**
 * Domain class representing a GenericResource created for simple CRUD mocking.
 * @author laurent
 */
public class GenericResource {

   @Id
   private String id;
   private String serviceId;
   private Document payload;
   private boolean reference = false;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getServiceId() {
      return serviceId;
   }

   public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
   }

   public Document getPayload() {
      return payload;
   }

   public void setPayload(Document payload) {
      this.payload = payload;
   }

   public boolean isReference() {
      return reference;
   }

   public void setReference(boolean reference) {
      this.reference = reference;
   }
}
