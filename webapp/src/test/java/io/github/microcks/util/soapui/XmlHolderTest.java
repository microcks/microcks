package io.github.microcks.util.soapui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

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
