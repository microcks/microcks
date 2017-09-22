package com.github.lbroudoux.microcks.util;

import com.github.lbroudoux.microcks.util.postman.PostmanCollectionImporter;
import com.github.lbroudoux.microcks.util.soapui.SoapUIProjectImporter;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
/**
 * @author laurent
 */
public class MockRepositoryImporterFactoryTest {

   @Test
   public void testGetMockRepositoryImporter() {

      // Load a SoapUI file.
      File soapUIProject = new File("samples/HelloService-soapui-project.xml");
      MockRepositoryImporter importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(soapUIProject);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof SoapUIProjectImporter);

      // Load a Postman file.
      File postmanCollection = new File("samples/PetstoreAPI-collection.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(postmanCollection);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof PostmanCollectionImporter);
   }
}
