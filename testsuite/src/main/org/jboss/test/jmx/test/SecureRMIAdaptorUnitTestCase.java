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
package org.jboss.test.jmx.test;

import javax.management.ObjectName;
import javax.management.MBeanServerConnection;
import javax.security.auth.login.LoginContext;
import javax.naming.InitialContext;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.jboss.test.JBossTestCase;
import org.jboss.test.JBossTestSetup;
import org.jboss.test.util.AppCallbackHandler;

/** Tests for a secured deployment of the jmx invoker adaptor
 *
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class SecureRMIAdaptorUnitTestCase
   extends JBossTestCase
{
   public SecureRMIAdaptorUnitTestCase(String name)
   {
      super(name);
   }

   public static Test suite()
      throws Exception
   {
      TestSuite suite = new TestSuite();
      suite.addTest(new TestSuite(SecureRMIAdaptorUnitTestCase.class));
      JBossTestSetup wrapper = new JBossTestSetup(suite)
      {
         protected void setUp() throws Exception
         {
            deploymentException = null;
            try
            {
               super.setUp();
               this.delegate.init();
               // deploy the comma seperated list of jars
               String serviceURL = this.getResourceURL("jmx/jmxadaptor/securejmx-invoker-service.xml");
               this.redeploy(serviceURL);
               this.getLog().debug("deployed package: " + serviceURL);
            }
            catch (Exception ex)
            {
               // Throw this in testServerFound() instead.
               deploymentException = ex;
            }
         }

         protected void tearDown() throws Exception
         {
            super.tearDown();
            String serviceURL = this.getResourceURL("jmx/jmxadaptor/securejmx-invoker-service.xml");            
            this.undeploy(serviceURL);
            this.getLog().debug("undeployed package: " + serviceURL);
         }
      };
      return wrapper;

   }

   /**
    * Test that a valid jmx-console domain user can invoke operations
    * through the jmx/invoker/AuthenticatedRMIAdaptor
    * @throws Exception
    */ 
   public void testAuthenticatedAccess() throws Exception
   {
      LoginContext lc = login("admin", "admin".toCharArray());
      InitialContext ctx = getInitialContext();
      MBeanServerConnection conn = (MBeanServerConnection) ctx.lookup("jmx/invoker/AuthenticatedRMIAdaptor");
      ObjectName server = new ObjectName("jboss.system:type=Server");
      String version = (String) conn.getAttribute(server, "Version");
      log.info("Obtained server version: "+version);
      lc.logout();
   }

   /**
    * Test that a valid jmx-console domain user can NOT invoke operations
    * through the jmx/invoker/AuthenticatedRMIAdaptor
    * @throws Exception
    */ 
   public void testUnauthenticatedAccess() throws Exception
   {
      InitialContext ctx = getInitialContext();
      MBeanServerConnection conn = (MBeanServerConnection) ctx.lookup("jmx/invoker/AuthenticatedRMIAdaptor");
      ObjectName server = new ObjectName("jboss.system:type=Server");
      try
      {
         String version = (String) conn.getAttribute(server, "Version");
         log.info("Obtained server version: "+version);
         fail("Was able to get server Version attribute");
      }
      catch(Exception e)
      {
         log.info("Access failed as expected", e);
      }
   }

   /**
    * Test that a valid jmx-console domain user can invoke operations
    * through the jmx/invoker/AuthenticatedRMIAdaptor
    * @throws Exception
    */ 
   public void testAuthorizedAccess() throws Exception
   {
      LoginContext lc = login("admin", "admin".toCharArray());
      InitialContext ctx = getInitialContext();
      MBeanServerConnection conn = (MBeanServerConnection) ctx.lookup("jmx/invoker/AuthorizedRMIAdaptor");
      ObjectName server = new ObjectName("jboss.system:type=Server");
      String version = (String) conn.getAttribute(server, "Version");
      log.info("Obtained server version: "+version);
      lc.logout();
   }

   /**
    * Test that a valid jmx-console domain user can NOT invoke operations
    * through the jmx/invoker/AuthenticatedRMIAdaptor
    * @throws Exception
    */ 
   public void testUnauthorizedAccess() throws Exception
   {
      InitialContext ctx = getInitialContext();
      MBeanServerConnection conn = (MBeanServerConnection) ctx.lookup("jmx/invoker/AuthorizedRMIAdaptor");
      ObjectName server = new ObjectName("jboss.system:type=Server");
      try
      {
         String version = (String) conn.getAttribute(server, "Version");
         log.info("Obtained server version: "+version);
         fail("Was able to get server Version attribute");
      }
      catch(Exception e)
      {
         log.info("Access failed as expected", e);
      }
   }

   private LoginContext login(String username, char[] password) throws Exception
   {
      String confName = System.getProperty("conf.name", "other");
      AppCallbackHandler handler = new AppCallbackHandler(username, password);
      log.debug("Creating LoginContext("+confName+")");
      LoginContext lc = new LoginContext(confName, handler);
      lc.login();
      log.debug("Created LoginContext, subject="+lc.getSubject());
      return lc;
   }

}
