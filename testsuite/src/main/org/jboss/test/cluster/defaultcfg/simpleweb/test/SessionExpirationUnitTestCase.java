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

package org.jboss.test.cluster.defaultcfg.simpleweb.test;

import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.catalina.Session;
import org.jboss.cache.Fqn;
import org.jboss.cache.pojo.PojoCache;
import org.jboss.logging.Logger;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.test.cluster.testutil.JGroupsSystemPropertySupport;
import org.jboss.test.cluster.testutil.SessionTestUtil;
import org.jboss.web.tomcat.service.session.JBossCacheManager;

/**
 * Unit tests of session expiration
 * 
 * TODO move some of the other expiration tests here where we can
 * more closely control the test timing
 * 
 * @author Brian Stansberry
 */
public class SessionExpirationUnitTestCase extends TestCase
{
   private static final Logger log = Logger.getLogger(SessionExpirationUnitTestCase.class);
   
   private static long testCount = System.currentTimeMillis();
   
   private JGroupsSystemPropertySupport jgroupsSupport;
   private Set<PojoCache> caches = new HashSet<PojoCache>();

   /**
    * Create a new SessionExpirationUnitTestCase.
    * 
    * @param name
    */
   public SessionExpirationUnitTestCase(String name)
   {
      super(name);
   }

   protected void setUp() throws Exception
   {
      super.setUp();
      
      // Set system properties to properly bind JGroups channels
      jgroupsSupport = new JGroupsSystemPropertySupport();
      jgroupsSupport.setUpProperties();
   }

   protected void tearDown() throws Exception
   {
      try
      {
         super.tearDown();
      }
      finally
      {         
         SessionTestUtil.clearDistributedCacheManagerFactory();
         
         // Restore any system properties we set in setUp
         if (jgroupsSupport != null)
         {
            jgroupsSupport.restoreProperties();
         }
         
         for (PojoCache cache : caches)
         { 
            // Try to clean up so we avoid loading sessions 
            // from storage in later tests
            try
            {
               log.info("Removing /JSESSION from " + cache.getCache().getLocalAddress());
               cache.getCache().removeNode(Fqn.fromString("/JSESSION"));
            }
            catch (Exception e)
            {
               log.error("Cache " + cache.getCache().getLocalAddress() + ": " + e.getMessage(), e);
            }
            
            try
            {
               cache.stop();
               cache.destroy();
            }
            catch (Exception e)
            {
               log.error("Cache " + cache.getCache().getLocalAddress() + ": " + e.getMessage(), e);
            }
         }
         
         caches.clear();
      }
   }

   /**
    * Test for JBAS-5404
    * 
    * @throws Exception
    */
   public void testMaxInactiveIntervalReplication() throws Exception
   {
      log.info("Enter testMaxInactiveIntervalReplication");
      
      ++testCount;
      
      JBossCacheManager jbcm0= SessionTestUtil.createManager("test" + testCount, 5, false, null, true, true, null, caches);
       
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(2);
      jbcm0.init("test.war", webMetaData);
      
      jbcm0.start();
      
      JBossCacheManager jbcm1 = SessionTestUtil.createManager("test" + testCount, 5, false, null, true, true, null, caches);
      
      jbcm1.init("test.war", webMetaData);
      
      jbcm1.start();
      
      // Set up a session
      String id = "1";
      Session sess = jbcm0.findSession(id);
      assertNull("session does not exist", sess);
      
      sess = jbcm0.createSession(id);
      sess.access();
      sess.getSession().setAttribute("test", "test");      
      jbcm0.storeSession(sess);      
      sess.endAccess();
      
      assertEquals("Session count correct", 1, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());      
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
      
      // Confirm a session timeout clears space
      sess = jbcm0.findSession(id);
      sess.access();
      sess.setMaxInactiveInterval(1);        
      jbcm0.storeSession(sess);      
      sess.endAccess();
      
      SessionTestUtil.sleepThread(1005);      
           
      jbcm1.backgroundProcess();
      
      assertEquals("Session count correct", 1, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());      
      assertEquals("Session count correct", 0, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
      
      jbcm0.backgroundProcess();
      
      assertEquals("Session count correct", 0, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm0.getLocalActiveSessionCount());      
      assertEquals("Session count correct", 0, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
   }

}
