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
package org.jboss.test.cluster.multicfg.test;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.test.JBossClusteredTestCase;
import org.jnp.interfaces.NamingContext;

/**
 * GlobalDiscoveryDisableTestCase.
 * 
 * @author Galder Zamarreño
 */
public class GlobalDiscoveryDisableTestCase extends JBossClusteredTestCase
{
   // ENVIRONMENT PROPERTIES
   private static final String NODE0 = System.getProperty("node0");
   private static final String NODE0_JNDI = System.getProperty("node0.jndi.url");
   private static final String NODE1_JNDI = System.getProperty("node1.jndi.url");
   private static final String NODE0_HAJNDI = System.getProperty("node0.hajndi.url");
   private static final String NODE1_HAJNDI = System.getProperty("node1.hajndi.url");
   private static final String DISCOVERY_GROUP = System.getProperty("jbosstest.udpGroup");
   private static final String DISCOVERY_TTL = System.getProperty("jbosstest.udp.ip_ttl", "1");
   private static final String DISCOVERY_PARTITION = System.getProperty("jbosstest.partitionName", "DefaultPartition");
   
   // BINDING KEYS and VALUES
   private static final String LOCAL2_KEY = "org.jboss.test.cluster.test.Local2Key";
   private static final String LOCAL2_VALUE = "Local2Value";
   private static final String JNDI_KEY4 = "org.jboss.test.cluster.test.JNDIKey4";
   private static final String JNDI_VALUE4 = "JNDIValue4";

   public GlobalDiscoveryDisableTestCase(String name)
   {
      super(name);
      System.setProperty("jboss.global.jnp.disableDiscovery" , "true");
   }

   /**
    * Test HA-JNDI AutoDiscovery with global disable discovery system property.
    *
    * @throws Exception
    */
   public void testAutoDiscoveryWithGlobalDisableDiscoverySysProp()
      throws Exception
   {
      getLog().debug("HAJndiTestCase.testAutoDiscovery()");
      validateUrls();
      
      // this test doesn't run properly if node0=localhost or node0=127.0.0.1
      // because the jndi code would find localhost:1099 server and would use 
      // that one
      if (NODE0 != null && (NODE0.equalsIgnoreCase("localhost") || NODE0.equalsIgnoreCase("127.0.0.1")))
      {
         getLog().debug("testAutoDiscovery() - test skipped because node0=localhost");
         return;
      }
      
      // bind to node1 locally
      Context naming = getContext(NODE1_JNDI);
      naming.bind(LOCAL2_KEY, LOCAL2_VALUE);
      closeContext(naming);
      
      // bind to node0 using HA-JNDI
      naming = getContext(NODE0_HAJNDI);
      naming.bind(JNDI_KEY4, JNDI_VALUE4);  
      closeContext(naming);
     
      //create context with AutoDiscovery disabled via global sys property
      naming = getAutoDiscoveryContext(false, false);
      // lookup local binding without HA-JNDI AutoDiscovery - should fail
      String value = (String)lookup(naming, LOCAL2_KEY, false);
      assertNull("local lookup with AutoDiscovery disabled", value);
      // lookup HA binding without HA-JNDI AutoDiscovery - should fail
      value = (String)lookup(naming, JNDI_KEY4, false);
      assertNull("lookup of HA-JNDI binding with AutoDiscovery disabled", value);
      closeContext(naming);

      //create context with AutoDiscovery disabled via global sys property and per ctx property
      naming = getAutoDiscoveryContext(true, false);
      // lookup local binding without HA-JNDI AutoDiscovery - should fail
      value = (String)lookup(naming, LOCAL2_KEY, false);
      assertNull("local lookup with AutoDiscovery disabled", value);
      // lookup HA binding without HA-JNDI AutoDiscovery - should fail
      value = (String)lookup(naming, JNDI_KEY4, false);
      assertNull("lookup of HA-JNDI binding with AutoDiscovery disabled", value);
      closeContext(naming);

      //create context with AutoDiscovery enabled via explicit enabling in spite of sys property
      naming = getAutoDiscoveryContext(false, true);
      // lookup local binding using HA-JNDI AutoDiscovery - should succeed
      value = (String)lookup(naming, LOCAL2_KEY, true);
      assertEquals("local lookup with AutoDiscovery enabled", LOCAL2_VALUE, value);
      // lookup HA binding using HA-JNDI AutoDiscovery - should succeed
      value = (String)lookup(naming, JNDI_KEY4, true);
      assertEquals("lookup of HA-JNDI binding with AutoDiscovery enabled", JNDI_VALUE4, value);     
      closeContext(naming);      
   }

   private Context getContext(String url) throws Exception
   {
      Properties env = new Properties();        
      env.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory");
      env.setProperty(Context.PROVIDER_URL, url);
      env.setProperty(Context.URL_PKG_PREFIXES, "org.jboss.naming:org.jnp.interfaces");
      
      Context naming = new InitialContext (env);
      return naming;
   }

   private Context getAutoDiscoveryContext(boolean autoDisabled, boolean disableDiscoveryExplicit) throws Exception
   {
      // do not add any urls to the context
      Properties env = new Properties();        
      env.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.jnp.interfaces.NamingContextFactory");
      env.setProperty(Context.URL_PKG_PREFIXES, "org.jboss.naming:org.jnp.interfaces");
      // Don't let the discovery packet off the test server so we don't
      // get spurious responses from other servers on the network
      env.setProperty(NamingContext.JNP_DISCOVERY_TTL, DISCOVERY_TTL);
   
      if (autoDisabled)
      {
         env.put(NamingContext.JNP_DISABLE_DISCOVERY, "true");
      }
      else 
      {
         if (disableDiscoveryExplicit)
         {
            env.put(NamingContext.JNP_DISABLE_DISCOVERY, "false");
         }
         
         if (DISCOVERY_GROUP != null && "".equals(DISCOVERY_GROUP) == false)      
         {
            // Use the multicast address this test environment is using
            env.put(NamingContext.JNP_DISCOVERY_GROUP, DISCOVERY_GROUP);
         }
         if (DISCOVERY_PARTITION != null && "".equals(DISCOVERY_PARTITION) == false)
         {
            // Limit to the partition this test environment is using
            env.put(NamingContext.JNP_PARTITION_NAME, DISCOVERY_PARTITION);
         }
      }  
        
      Context naming = new InitialContext (env);
      return naming;
   }

   private void closeContext(Context context)
   {
      try 
      {
         context.close();        
      }
      catch (NamingException e)
      {
         // no action required
      }
   }  

   private Object lookup(Context context, String name, boolean failIfMissing)
   {     
      try
      {
         Object o = context.lookup(name);
         log.info(name + " binding value: " + o);
         return o;
      }
      catch (NamingException e)
      {
         if (failIfMissing)
         {
           String msg =   "Name " + name + " not found. " + e.getLocalizedMessage();
           log.info(msg, e);
           fail(msg);
         }
         else
         {
            log.debug("Name " + name + " not found.");
         }
         return null;
      }     
   }

   private void validateUrls() throws Exception      
   {
      if (NODE0_JNDI == null)
         throw new Exception("node0.jndi.url not defined.");
         
      if (NODE1_JNDI == null)
         throw new Exception("node1.jndi.url not defined.");
         
      if (NODE0_HAJNDI == null)
         throw new Exception("node0.hajndi.url not defined.");
         
      if (NODE1_HAJNDI == null)
         throw new Exception("node1.hajndi.url not defined.");   
   }
}
