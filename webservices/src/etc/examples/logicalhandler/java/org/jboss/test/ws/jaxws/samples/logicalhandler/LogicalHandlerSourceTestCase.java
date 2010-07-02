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
public class LogicalHandlerSourceTestCase extends JBossWSTest
{
   public static Test suite()
   {
      return new JBossWSTestSetup(LogicalHandlerSourceTestCase.class, "jaxws-samples-logicalhandler-source.war");
   }

   public void testSourceDoc() throws Exception
   {
      String endpointAddress = "http://" + getServerHost() + ":8080/jaxws-samples-logicalhandler-source/doc";
      QName serviceName = new QName("http://org.jboss.ws/jaxws/samples/logicalhandler", "SOAPEndpointDocService");
      Service service = Service.create(new URL(endpointAddress + "?wsdl"), serviceName);
      SOAPEndpointSourceDoc port = (SOAPEndpointSourceDoc)service.getPort(SOAPEndpointSourceDoc.class);
      
      String retStr = port.echo("hello");
      
      StringBuffer expStr = new StringBuffer("hello");
      if (isIntegrationNative())
      {
         expStr.append(":Outbound:LogicalSourceHandler");
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
      expStr.append(":Inbound:LogicalSourceHandler");
      expStr.append(":endpoint");
      expStr.append(":Outbound:LogicalSourceHandler");
      expStr.append(":Outbound:ProtocolHandler");
      expStr.append(":Outbound:PortHandler");
      if (isIntegrationNative())
      {
         expStr.append(":Inbound:PortHandler");
         expStr.append(":Inbound:ProtocolHandler");
         expStr.append(":Inbound:LogicalSourceHandler");
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


   public void testSourceRpc() throws Exception
   {
      String endpointAddress = "http://" + getServerHost() + ":8080/jaxws-samples-logicalhandler-source/rpc";
      QName serviceName = new QName("http://org.jboss.ws/jaxws/samples/logicalhandler", "SOAPEndpointRpcService");
      Service service = Service.create(new URL(endpointAddress + "?wsdl"), serviceName);
      SOAPEndpointSourceRpc port = (SOAPEndpointSourceRpc)service.getPort(SOAPEndpointSourceRpc.class);
      
      String retStr = port.echo("hello");
      
      StringBuffer expStr = new StringBuffer("hello");
      if (isIntegrationNative())
      {
         expStr.append(":Outbound:LogicalSourceHandler");
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
      expStr.append(":Inbound:LogicalSourceHandler");
      expStr.append(":endpoint");
      expStr.append(":Outbound:LogicalSourceHandler");
      expStr.append(":Outbound:ProtocolHandler");
      expStr.append(":Outbound:PortHandler");
      if (isIntegrationNative()) 
      {
         expStr.append(":Inbound:PortHandler");
         expStr.append(":Inbound:ProtocolHandler");
         expStr.append(":Inbound:LogicalSourceHandler");
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
