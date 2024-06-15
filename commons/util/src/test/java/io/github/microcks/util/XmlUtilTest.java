package io.github.microcks.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XmlUtilTest {

  private Document document;
  private Element parent;

  @BeforeEach
  void setUp() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    document = builder.newDocument();

    parent = document.createElementNS("http://example.com", "parent");
    document.appendChild(parent);

    Element child1 = document.createElementNS("http://example.com", "child");
    parent.appendChild(child1);

    Element child2 = document.createElementNS("http://example.com", "child");
    parent.appendChild(child2);

    Element child3 = document.createElementNS("http://example.com", "otherChild");
    parent.appendChild(child3);
  }

  @Test
  void getDirectChildren() {
    List<Element> children = XmlUtil.getDirectChildren(parent, "http://example.com", "child");
    assertEquals(2, children.size());
    assertEquals("child", children.get(0).getLocalName());
    assertEquals("child", children.get(1).getLocalName());
  }

  @Test
  void getUniqueDirectChild() {
    assertThrows(MalformedXmlException.class, () -> {
      XmlUtil.getUniqueDirectChild(parent, "http://example.com", "child1");
    });

    Element uniqueChild = document.createElementNS("http://example.com", "uniqueChild");
    parent.appendChild(uniqueChild);

    try {
      Element retrievedChild = XmlUtil.getUniqueDirectChild(parent, "http://example.com", "uniqueChild");
      assertEquals("uniqueChild", retrievedChild.getLocalName());
    } catch (MalformedXmlException e) {
      fail("Exception should not have been thrown");
    }
  }

  @Test
  void hasDirectChild() {
    assertTrue(XmlUtil.hasDirectChild(parent, "http://example.com", "child"));
    assertFalse(XmlUtil.hasDirectChild(parent, "http://example.com", "nonExistentChild"));
  }
}
