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

import java.util.Properties;
import java.net.URL;
import java.net.InetAddress;

import org.jboss.xb.binding.Unmarshaller;
import org.jboss.xb.binding.UnmarshallerFactory;
import org.jboss.xb.binding.sunday.unmarshalling.SchemaBinding;
import org.jboss.xb.binding.sunday.unmarshalling.XsdBinder;
import org.jboss.naming.JNDIBindings;
import org.jboss.naming.JNDIBinding;

import junit.framework.TestCase;

/**
 * Test unmarshalling xml documents conforming to xml/naming/jndi_binding_service_1_0.xsd
 * the org.jboss.naming.JNDIBindings and related objects.
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class JNDIBindingUnitTestCase
   extends TestCase
{
   public void testMain() throws Exception
   {
      URL url = getResource("xml/naming/jndi-binding-service_1_0.xsd");
      SchemaBinding schemaBinding = XsdBinder.bind(url.toString());
      schemaBinding.setIgnoreUnresolvedFieldOrClass(false);

      Unmarshaller unmarshaller = UnmarshallerFactory.newInstance().newUnmarshaller();

      URL xml = getResource("xml/naming/testBindings.xml");
      JNDIBindings bindings = (JNDIBindings)unmarshaller.unmarshal(xml.openStream(), schemaBinding);
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

   private static URL getResource(String path)
   {
      java.net.URL url = Thread.currentThread().getContextClassLoader().getResource(path);
      if(url == null)
      {
         fail("URL not found: " + path);
      }
      return url;
   }
}
