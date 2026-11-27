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
package io.github.microcks.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

/**
 * An implementation of LSResourceResolver that may use local cache for standard schemas (xml.xsd) of an URL resolver
 * for relative references to XSD that may be embedded into other XSD.
 * @author laurent
 */
public class XmlSchemaURLResolver implements LSResourceResolver {

   /** A commons logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(XmlSchemaURLResolver.class);

   private static final Map<String, String> LOCAL_RESOLUTIONS = Map.of("http://www.w3.org/2001/xml.xsd", "xml.xsd");

   private String baseResourceURL;

   /**
    * Build a XmlSchemaURLResolver with base URL for resolving relative dependencies.
    * @param baseResourceURL The base URL to consider for relative dependencies
    */
   public XmlSchemaURLResolver(String baseResourceURL) {
      this.baseResourceURL = baseResourceURL;
   }

   @Override
   public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
      log.debug("Resolving resource with systemId: {}", systemId);

      InputStream resourceStream = null;
      if (LOCAL_RESOLUTIONS.containsKey(systemId)) {
         log.debug("Got a local resolution, loading it from classloader");
         resourceStream = this.getClass().getClassLoader().getResourceAsStream(LOCAL_RESOLUTIONS.get(systemId));
      } else if (baseResourceURL != null && (!systemId.startsWith("http://") || !systemId.startsWith("https://"))) {
         log.debug("Got a baseResourceURL defined and relative reference, loading it from URL");
         String sanitizedSystemId = systemId;
         if (systemId.startsWith("./")) {
            sanitizedSystemId = systemId.substring(2);
         }
         try {
            URL resourceURL = new URL(baseResourceURL + (baseResourceURL.endsWith("/") ? "" : "/") + sanitizedSystemId);
            resourceStream = resourceURL.openStream();
         } catch (Exception e) {
            log.error("Failed to open stream on {}/{}", baseResourceURL, sanitizedSystemId, e);
         }
      }

      if (resourceStream != null) {
         Input input = new Input();
         input.setSystemId(systemId);
         input.setPublicId(publicId);
         input.setBaseURI(baseURI);
         input.setCharacterStream(new InputStreamReader(resourceStream));
         return input;
      }
      // Let default behaviour happen!
      return null;
   }

   public class Input implements LSInput {

      private Reader characterStream;
      private InputStream byteStream;
      private String stringData;
      private String systemId;
      private String publicId;
      private String baseURI;
      private String encoding;
      private boolean certifiedText;

      @Override
      public Reader getCharacterStream() {
         return characterStream;
      }

      @Override
      public void setCharacterStream(Reader characterStream) {
         this.characterStream = characterStream;
      }

      @Override
      public InputStream getByteStream() {
         return byteStream;
      }

      @Override
      public void setByteStream(InputStream byteStream) {
         this.byteStream = byteStream;
      }

      @Override
      public String getStringData() {
         return stringData;
      }

      @Override
      public void setStringData(String stringData) {
         this.stringData = stringData;
      }

      @Override
      public String getSystemId() {
         return systemId;
      }

      @Override
      public void setSystemId(String systemId) {
         this.systemId = systemId;
      }

      @Override
      public String getPublicId() {
         return publicId;
      }

      @Override
      public void setPublicId(String publicId) {
         this.publicId = publicId;
      }

      @Override
      public String getBaseURI() {
         return baseURI;
      }

      @Override
      public void setBaseURI(String baseURI) {
         this.baseURI = baseURI;
      }

      @Override
      public String getEncoding() {
         return encoding;
      }

      @Override
      public void setEncoding(String encoding) {
         this.encoding = encoding;
      }

      @Override
      public boolean getCertifiedText() {
         return false;
      }

      @Override
      public void setCertifiedText(boolean certifiedText) {
         this.certifiedText = certifiedText;
      }
   }
}
