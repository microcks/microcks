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
package io.github.microcks.util.soapui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * This is a test case for class XmlHolder class.
 * @author laurent
 */
public class XmlHolderTest {

   private final String validSoap = """
         <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
            <soapenv:Header/>
            <soapenv:Body>
               <hel:sayHelloResponse>
                  <sayHello>Hello Andrew !</sayHello>
               </hel:sayHelloResponse>
            </soapenv:Body>
         </soapenv:Envelope>
         """;

   @Test
   public void testWithoutNamespace() throws Exception {
      String xpathStr = """
            //hel:sayHelloResponse/sayHello
            """;
      XmlHolder holder = new XmlHolder(validSoap);
      assertEquals("Hello Andrew !", holder.get(xpathStr));
   }

   @Test
   public void testWithNamespaceInXpath() throws Exception {
      String xpathStr = """
            declare namespace ser='http://www.example.com/hello';
            //ser:sayHelloResponse/sayHello
            """;
      XmlHolder holder = new XmlHolder(validSoap);
      assertEquals("Hello Andrew !", holder.get(xpathStr));
   }

   @Test
   public void testWithNamespaceInHolder() throws Exception {
      String xpathStr = """
            //ser:sayHelloResponse/sayHello
            """;
      XmlHolder holder = new XmlHolder(validSoap);
      holder.declareNamespace("ser", "http://www.example.com/hello");
      assertEquals("Hello Andrew !", holder.get(xpathStr));
   }

}
