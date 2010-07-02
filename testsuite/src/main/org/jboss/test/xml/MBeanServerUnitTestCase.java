/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.test.xml;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.net.URL;
import java.net.InetAddress;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.security.auth.login.AppConfigurationEntry;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;

import org.jboss.test.xml.mbeanserver.Services;
import org.jboss.test.xml.mbeanserver.MBeanData;
import org.jboss.test.xml.mbeanserver.MBeanAttribute;
import org.jboss.test.xml.mbeanserver.PolicyConfig;
import org.jboss.test.xml.mbeanserver.AuthenticationInfo;
import org.jboss.xb.binding.JBossXBRuntimeException;
import org.jboss.xb.binding.Unmarshaller;
import org.jboss.xb.binding.UnmarshallerFactory;
import org.jboss.xb.binding.sunday.unmarshalling.SchemaBinding;
import org.jboss.xb.binding.sunday.unmarshalling.SchemaBindingResolver;
import org.jboss.xb.binding.sunday.unmarshalling.XsdBinder;
import org.jboss.naming.JNDIBindings;
import org.jboss.naming.JNDIBinding;
import org.jboss.util.xml.JBossEntityResolver;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import junit.framework.TestCase;

/**
 * Test unmarshalling xml documents conforming to mbean-service_1_0.xsd into
 * the org.jboss.test.xml.mbeanserver.Services and related objects.
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class MBeanServerUnitTestCase
   extends TestCase
{
   public void testMbeanService() throws Exception
   {
      InputStream is = getResource("xml/mbeanserver/mbean-service_1_0.xsd");
      SchemaBinding schemaBinding = XsdBinder.bind(is, null);
      schemaBinding.setIgnoreUnresolvedFieldOrClass(true);
      schemaBinding.setSchemaResolver(new SchemaBindingResolver()
      {
         public String getBaseURI()
         {
            throw new UnsupportedOperationException("getBaseURI is not implemented.");
         }

         public void setBaseURI(String baseURI)
         {
            throw new UnsupportedOperationException("setBaseURI is not implemented.");
         }

         public SchemaBinding resolve(String nsUri, String baseURI, String schemaLocation)
         {
            try
            {
               if("urn:jboss:login-config2".equals(nsUri))
               {
                  InputStream is = getResource("xml/mbeanserver/login-config2.xsd");
                  SchemaBinding schemaBinding = XsdBinder.bind(is, null, baseURI);
                  schemaBinding.setSchemaResolver(this);
                  return schemaBinding;
               }
               else if("urn:jboss:user-roles".equals(nsUri))
               {
                  InputStream is = getResource("xml/mbeanserver/user-roles.xsd");
                  return XsdBinder.bind(is, null, baseURI);
               }
               else
               {
                  throw new JBossXBRuntimeException("Unrecognized namespace: " + nsUri);
               }
            }
            catch(IOException e)
            {
               throw new JBossXBRuntimeException("IO error", e);
            }
         }

         public LSInput resolveAsLSInput(String nsUri, String baseUri, String schemaLocation)
         {
            throw new UnsupportedOperationException("resolveResource is not implemented.");
         }
      });

      Unmarshaller unmarshaller = UnmarshallerFactory.newInstance().newUnmarshaller();
      InputStream is2 = getResource("xml/mbeanserver/testObjFactory.xml");
      Services services = (Services)unmarshaller.unmarshal(is2, schemaBinding);
      List mbeans = services.getMBeans();
      assertEquals("There is 1 mbean", 1, mbeans.size());

      MBeanData mbean = (MBeanData) mbeans.get(0);
      assertTrue("ClassName == org.jboss.security.auth.login.DynamicLoginConfig",
         mbean.getCode().equals("org.jboss.security.auth.login.DynamicLoginConfig"));
      assertTrue("Name == jboss.security.tests:service=DynamicLoginConfig",
         mbean.getName().equals("jboss.security.tests:service=DynamicLoginConfig"));
      Map attributes = mbean.getAttributeMap();
      assertTrue("There are 2 attributes",
         attributes.size() == 2);
      MBeanAttribute attr = (MBeanAttribute) attributes.get("PolicyConfig");
      Object value = attr.getValue();
      assertTrue("Value isA PolicyConfig",
          value instanceof PolicyConfig );
      PolicyConfig pc = (PolicyConfig) value;
      assertTrue("There 1 AuthenticationInfo",
         pc.size() == 1);
      AuthenticationInfo auth = pc.get("conf1");
      assertTrue("The AuthenticationInfo name ic config1",
         auth != null);
      AppConfigurationEntry[] ace = auth.getAppConfigurationEntry();
      assertTrue("The AppConfigurationEntry has one entry",
         ace != null && ace.length == 1);
      assertTrue("LoginModuleName", 
         ace[0].getLoginModuleName().equals("org.jboss.security.auth.spi.IdentityLoginModule"));

      attr = (MBeanAttribute) attributes.get("UserHome");
      assertTrue("Name == UserHome",
         attr.getName().equals("UserHome"));
      assertTrue("Text != null",
         attr.getText() != null);
   }

   /**
    * A test of unmarshalling an element from a document without any knowledge
    * of the associated schema.
    * 
    * @throws Exception
    */ 
   public void testJndiBindings() throws Exception
   {
      InputStream is = getResource("xml/mbeanserver/testBinding.xml");
      // Get the Bindings attribute element
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(is);
      NodeList attributes = doc.getElementsByTagName("attribute");
      Element element = (Element) attributes.item(0);
      NodeList children = element.getChildNodes();
      Element content = null;
      for(int n = 0; n < children.getLength(); n ++)
      {
         Node node = children.item(n);
         if( node.getNodeType() == Node.ELEMENT_NODE )
         {
            content = (Element) node;
            break;
         }
      }

      // Get a parsable representation of this elements content
      DOMSource source = new DOMSource(content);
      TransformerFactory tFactory = TransformerFactory.newInstance();
      Transformer transformer = tFactory.newTransformer();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      StreamResult result = new StreamResult(baos);
      transformer.transform(source, result);
      baos.close();

      ByteArrayInputStream is2 = new ByteArrayInputStream(baos.toByteArray());

      /* Parse the element content using the Unmarshaller starting with an
      empty schema since we don't know anything about it. This is not quite
      true as we set the schema baseURI to the resources/xml/naming/ directory
      so that the jndi-binding-service_1_0.xsd can be found, but this baseURI
      can be easily specified to the SARDeployer, or the schema can be made
      available to the entity resolver via some other configuration.
      */
      final URL url = Thread.currentThread().getContextClassLoader().getResource("xml/naming/");

      Unmarshaller unmarshaller = UnmarshallerFactory.newInstance().newUnmarshaller();
      unmarshaller.setEntityResolver(new JBossEntityResolver(){
         public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException
         {
            if(systemId.endsWith("custom-object-binding.xsd") ||
               systemId.endsWith("jndi-binding-service_1_0.xsd"))
            {
               String fileName = systemId.substring(systemId.lastIndexOf('/') + 1);
               URL url = Thread.currentThread().getContextClassLoader().
                  getResource("xml/naming/" + fileName);
               return new InputSource(url.toExternalForm());
            }
            else
            {
               return super.resolveEntity(publicId, systemId);
            }
         }
      });

      JNDIBindings bindings = (JNDIBindings) unmarshaller.unmarshal(is2,
         new SchemaBindingResolver(){
            public String getBaseURI()
            {
               throw new UnsupportedOperationException("getBaseURI is not implemented.");
            }

            public void setBaseURI(String baseURI)
            {
               throw new UnsupportedOperationException("setBaseURI is not implemented.");
            }

            public SchemaBinding resolve(String nsUri, String baseURI, String schemaLocation)
            {
               return XsdBinder.bind(url.toExternalForm() + schemaLocation, this);
            }

            public LSInput resolveAsLSInput(String nsUri, String baseUri, String schemaLocation)
            {
               throw new UnsupportedOperationException("resolveAsLSInput is not implemented.");
            }
         });

      is2.close();

      // Validate the bindings
      JNDIBinding[] values = bindings.getBindings();
      assertTrue("There are 5 bindings("+values.length+")", values.length == 5);

      JNDIBinding key1 = values[0];
      assertTrue("values[0] name is ctx1/key1", key1.getName().equals("ctx1/key1"));
      assertTrue("values[0] is string of value1", key1.getText().equals("value1"));

      JNDIBinding userHome = values[1];
      assertTrue("values[1] name is ctx1/user.home", userHome.getName().equals("ctx1/user.home"));
      String p = System.getProperty("user.home");
      assertTrue("values[1] is property ${user.home}", userHome.getText().equals(p));

      // Test binding from a text to URL based on the type attribute PropertyEditor
      JNDIBinding jbossHome = values[2];
      assertTrue("values[2] name is ctx1/key2", jbossHome.getName().equals("ctx1/key2"));
      assertTrue("values[2] is http://www.jboss.org",
         jbossHome.getText().equals("http://www.jboss.org"));
      assertTrue("values[2] type is java.net.URL",
         jbossHome.getType().equals("java.net.URL"));
      Object value2 = jbossHome.getValue();
      assertTrue("values[2] value is URL(http://www.jboss.org)",
         value2.equals(new URL("http://www.jboss.org")));

      // Test a binding from an xml fragment from a foreign namespace.
      JNDIBinding properties = values[3];
      Object value = properties.getValue();
      assertTrue("values[3] name is ctx2/key1", properties.getName().equals("ctx2/key1"));
      assertTrue("values[3] is java.util.Properties", value instanceof Properties);
      Properties props = (Properties) value;
      assertTrue("Properties(key1) == value1", props.getProperty("key1").equals("value1"));
      assertTrue("Properties(key2) == value2", props.getProperty("key2").equals("value2"));

      // Test binding from a text to InetAddress based on the editor attribute PropertyEditor
      JNDIBinding host = values[4];
      assertTrue("values[4] name is hosts/localhost", host.getName().equals("hosts/localhost"));
      assertTrue(host.isTrim());
      assertTrue("values[4] text is 127.0.0.1",
         host.getText().equals("127.0.0.1"));
      assertTrue("values[4] editor is org.jboss.util.propertyeditor.InetAddressEditor",
         host.getEditor().equals("org.jboss.util.propertyeditor.InetAddressEditor"));
      Object value4 = host.getValue();
      InetAddress hostValue = (InetAddress) value4;
      InetAddress localhost = InetAddress.getByName("127.0.0.1");
      assertTrue("values[4] value is InetAddress(127.0.0.1)",
         hostValue.getHostAddress().equals(localhost.getHostAddress()));

   }

   // Private

   private InputStream getResource(String path)
      throws IOException
   {
      URL url = Thread.currentThread().getContextClassLoader().getResource(path);
      if(url == null)
      {
         fail("URL not found: " + path);
      }
      return url.openStream();
   }
}
