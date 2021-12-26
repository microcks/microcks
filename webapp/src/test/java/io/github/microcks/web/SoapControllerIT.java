/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.web;

import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * Test case for the Soap mock controller.
 * @author laurent
 */
public class SoapControllerIT  extends AbstractBaseIT {

   @Test
   public void testHelloSoapWSMocking() {
      // Upload Hello Service SoapUI project.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/soapui/HelloService-soapui-project.xml", true);

      // Create headers for sayHello operation.
      HttpHeaders headers = new HttpHeaders();
      headers.put("Content-type", Collections.singletonList("application/soap+xml;action=sayHello"));

      String request = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n" +
            "   <soapenv:Header/>\n" +
            "   <soapenv:Body>\n" +
            "      <hel:sayHello>\n" +
            "         <name>Karla</name>\n" +
            "      </hel:sayHello>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>";

      // Build the request
      HttpEntity<String> entity = new HttpEntity<>(request, headers);

      ResponseEntity<String> response = restTemplate.postForEntity("/soap/HelloService+Mock/0.9", entity, String.class);
      assertEquals(200, response.getStatusCode().value());

      assertEquals("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n" +
            "   <soapenv:Header/>\n" +
            "   <soapenv:Body>\n" +
            "      <hel:sayHelloResponse>\n" +
            "         <sayHello>Hello Karla !</sayHello>\n" +
            "      </hel:sayHelloResponse>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>", response.getBody());
   }
}
