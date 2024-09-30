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
package io.github.microcks.minion.async;

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is a simple and local registry for Async APIs schemas. It is used as a local proxy to Services Resources that
 * are available within Microcks instance. This registry uses the <code>MicrocksAPIConnector</code> to retrieve
 * resources/schemas from instance.
 * @author laurent
 */
@ApplicationScoped
public class SchemaRegistry {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private final MicrocksAPIConnector microcksAPIConnector;

   /**
    * Create a new SchemaRegistry instance.
    * @param microcksAPIConnector The MicrocksAPIConnector to use for retrieving resources
    */
   public SchemaRegistry(@RestClient MicrocksAPIConnector microcksAPIConnector) {
      this.microcksAPIConnector = microcksAPIConnector;
   }

   /** The internal map backing schemas storage. This is an in-memory map. */
   private Map<String, List<SchemaEntry>> schemaEntries = new HashMap<>();

   /**
    * Updating the registry with resources attached to specified service.
    * @param service The Service to retrieve resources for and update registry with
    */
   public void updateRegistryForService(Service service) {
      clearRegistryForService(service);
      List<Resource> resources = microcksAPIConnector.getResources(service.getId());
      logger.infof("Updating schema registry for '%s - %s' with %d entries", service.getName(), service.getVersion(),
            resources.size());
      schemaEntries.put(service.getId(), resources.stream().map(SchemaEntry::new).toList());
   }

   /**
    * Updating the registry with resources attached to specified service.
    * @param serviceId The Id of service to retrieve resources for and update registry with
    */
   public void updateRegistryForService(String serviceId) {
      clearRegistryForService(serviceId);
      List<Resource> resources = microcksAPIConnector.getResources(serviceId);
      logger.infof("Updating schema registry for Service '%s' with %d entries", serviceId, resources.size());
      schemaEntries.put(serviceId, resources.stream().map(SchemaEntry::new).toList());
   }

   /**
    * Retrieve the schema entries for a specified Service.
    * @param service The Service to get schema entries for
    * @return A list of {@code SchemaEntry}
    */
   public List<SchemaEntry> getSchemaEntries(Service service) {
      return getSchemaEntries(service.getId());
   }

   /**
    * Retrieve the schema entries for a specified Service.
    * @param serviceId The Id of service to get schema entries for
    * @return A list of {@code SchemaEntry}
    */
   public List<SchemaEntry> getSchemaEntries(String serviceId) {
      // Do we have local entries already ? If not, retrieve them.
      List<SchemaEntry> entries = schemaEntries.get(serviceId);
      if (entries == null) {
         updateRegistryForService(serviceId);
         entries = schemaEntries.get(serviceId);
      }
      return entries;
   }

   /**
    * Get the content (ie. actual schema specification) of a Schema entry in the scope of a specified service.
    * @param service    The Service to get schema for
    * @param schemaName The name of the Schema entry to retrieve content for
    * @return The schema entry content or null if not found.
    */
   public String getSchemaEntryContent(Service service, String schemaName) {
      // Do we have local entries already ? If not, retrieve them.
      List<SchemaEntry> entries = schemaEntries.get(service.getId());
      if (entries == null) {
         updateRegistryForService(service);
         entries = schemaEntries.get(service.getId());
      }
      // We have to look for entry matching schemaName.
      SchemaEntry correspondingEntry = null;
      for (SchemaEntry entry : entries) {
         if (entry.getName().equals(schemaName)) {
            correspondingEntry = entry;
            break;
         }
      }
      // Return the entry content if found.
      if (correspondingEntry != null) {
         return correspondingEntry.getContent();
      }
      return null;
   }

   /**
    * Clear the registry for specified service.
    * @param service The Service whose schema entries should be removed from registry.
    */
   public void clearRegistryForService(Service service) {
      schemaEntries.remove(service.getId());
   }

   /**
    * Clear the registry for specified service.
    * @param serviceId The identifier of Service whose schema entries should be removed from registry.
    */
   public void clearRegistryForService(String serviceId) {
      schemaEntries.remove(serviceId);
   }

   /**
    * SchemaEntry represents an image of remote resources and allow access to content. For now, content remains
    * in-memory but a future direction for handling load could be to manage overflow to local files.
    */
   public class SchemaEntry {
      private String name;
      private String serviceId;
      private String path;
      private ResourceType type;
      private String content;
      private Set<String> operations;

      public SchemaEntry(Resource resource) {
         this.name = resource.getName();
         this.serviceId = resource.getServiceId();
         this.path = resource.getPath();
         this.type = resource.getType();
         this.content = resource.getContent();
         this.operations = resource.getOperations();
      }

      public String getName() {
         return name;
      }

      public String getServiceId() {
         return serviceId;
      }

      public String getPath() {
         return path;
      }

      public ResourceType getType() {
         return type;
      }

      public String getContent() {
         return content;
      }

      public Set<String> getOperations() {
         return operations;
      }
   }
}
