/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.test.ws.jaxws.samples.logicalhandler;

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;

import junit.framework.Test;

import org.jboss.wsf.test.JBossWSTest;
import org.jboss.wsf.test.JBossWSTestSetup;

/**
 * Test JAXWS logical handlers
 *
 * @author Thomas.Diesler@jboss.org
 * @since 12-Aug-2006
 */
public class LogicalHandlerJAXBTestCase extends JBossWSTest
{
   public static Test suite()
   {
      return new JBossWSTestSetup(LogicalHandlerJAXBTestCase.class, "jaxws-samples-logicalhandler-jaxb.war");
   }

   public void testClientAccess() throws Exception
   {
      String endpointAddress = "http://" + getServerHost() + ":8080/jaxws-samples-logicalhandler-jaxb";
      QName serviceName = new QName("http://org.jboss.ws/jaxws/samples/logicalhandler", "SOAPEndpointService");
      Service service = Service.create(new URL(endpointAddress + "?wsdl"), serviceName);
      SOAPEndpointJAXB port = (SOAPEndpointJAXB)service.getPort(SOAPEndpointJAXB.class);

      BindingProvider bindingProvider = (BindingProvider)port;
      bindingProvider.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpointAddress);

      String retStr = port.echo("hello");

      StringBuffer expStr = new StringBuffer("hello");
      if (isIntegrationNative())
      {
         expStr.append(":Outbound:LogicalJAXBHandler");
         expStr.append(":Outbound:ProtocolHandler");
         expStr.append(":Outbound:PortHandler");
      }
      else
      {
         if (isIntegrationMetro())
         {
            System.out.println("FIXME: [JBWS-1672] Metro does not respect @HandlerChain on client SEI");
         }
         else
         {
            System.out.println("FIXME: [CXF-1253] CXF does not respect @HandlerChain on client SEI");
         }
      }
      expStr.append(":Inbound:PortHandler");
      expStr.append(":Inbound:ProtocolHandler");
      expStr.append(":Inbound:LogicalJAXBHandler");
      expStr.append(":endpoint");
      expStr.append(":Outbound:LogicalJAXBHandler");
      expStr.append(":Outbound:ProtocolHandler");
      expStr.append(":Outbound:PortHandler");
      if (isIntegrationNative())
      {
         expStr.append(":Inbound:PortHandler");
         expStr.append(":Inbound:ProtocolHandler");
         expStr.append(":Inbound:LogicalJAXBHandler");
      }
      else
      {
         if (isIntegrationMetro())
         {
            System.out.println("FIXME: [JBWS-1672] Metro does not respect @HandlerChain on client SEI");
         }
         else
         {
            System.out.println("FIXME: [CXF-1253] CXF does not respect @HandlerChain on client SEI");
         }
      }
      assertEquals(expStr.toString(), retStr);
   }
}
