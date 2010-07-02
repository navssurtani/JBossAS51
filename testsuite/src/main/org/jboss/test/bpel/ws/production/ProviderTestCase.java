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
package org.jboss.test.bpel.ws.production;

import java.util.Properties;
import javax.naming.InitialContext;

import junit.framework.Test;

import org.jboss.test.webservice.WebserviceTestBase;

/**
 * @author Alejandro Guizar
 * @version $Revision: 81084 $ $Date: 2008-11-14 12:30:43 -0500 (Fri, 14 Nov 2008) $
 */
public class ProviderTestCase extends WebserviceTestBase {

  private HelloPT greeter;

  public ProviderTestCase(String name) {
    super(name);
  }

  protected void setUp() throws Exception {
    InitialContext ctx = getClientContext();
    // JNDI name bound to the service interface (in application-client.xml)
    String serviceRefName = "service/Hello";
    // lookup the service interface in the environment context
    HelloWorldService service = (HelloWorldService) ctx.lookup("java:comp/env/" + serviceRefName);
    // obtain the dynamic proxy for the web service port
    greeter = service.getCallerPort();
  }

  public void testSayHello() throws Exception {
    String greeting = greeter.sayHello("Popeye");
    assertEquals("Hello, Popeye!", greeting);
  }

  public static Test suite() throws Exception {
     return getDeploySetup(ProviderTestCase.class, "bpel-hello.ear");
  }
}