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

package org.jboss.test.cluster.defaultcfg.simpleweb.test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.pojo.PojoCache;
import org.jboss.logging.Logger;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.test.cluster.testutil.JGroupsSystemPropertySupport;
import org.jboss.test.cluster.testutil.SessionTestUtil;
import org.jboss.web.tomcat.service.session.JBossCacheManager;

/**
 * Unit tests of session count management.
 * 
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 104633 $
 */
public class SessionCountUnitTestCase extends TestCase
{
   private static final Logger log = Logger.getLogger(SessionCountUnitTestCase.class);
   
   private static long testCount = System.currentTimeMillis();
   
   private JGroupsSystemPropertySupport jgroupsSupport;
   private Set<PojoCache> caches = new HashSet<PojoCache>();
   private String tempDir;
   
   /**
    * Create a new SessionCountUnitTestCase.
    * 
    * @param name
    */
   public SessionCountUnitTestCase(String name)
   {
      super(name);
   }  
   
   
   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      
      // Set system properties to properly bind JGroups channels
      jgroupsSupport = new JGroupsSystemPropertySupport();
      jgroupsSupport.setUpProperties();
      
      File tmpDir = new File(System.getProperty("java.io.tmpdir"));
      File root = new File(tmpDir, getClass().getSimpleName());
      root.mkdirs();
      root.deleteOnExit();
      tempDir = root.getAbsolutePath();
    }

   @Override
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
         
         if (tempDir != null)
         {
            SessionTestUtil.cleanPassivationDir(new File(tempDir));
         }
      }
   }

   public void testStandaloneMaxSessions() throws Exception
   {
      log.info("Enter testStandaloneMaxSessions");
      
      ++testCount;
      
      JBossCacheManager jbcm = SessionTestUtil.createManager("test" + testCount, 5, true, null, false, false, null, caches);
       
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(2);
      jbcm.init("test.war", webMetaData);
      
      jbcm.start();
      
      assertFalse("Passivation is disabled", jbcm.isPassivationEnabled());
      assertEquals("Correct max active count", 2, jbcm.getMaxActiveAllowed());
      
      // Set up a session
      Session sess1 = createAndUseSession(jbcm, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());
      
      createAndUseSession(jbcm, "2", true, true);
      
      assertEquals("Session count correct", 2, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 2, jbcm.getLocalActiveSessionCount());
      
      // Should fail to create a 3rd
      createAndUseSession(jbcm, "3", false, false);
      
      // Confirm a session timeout clears space
      sess1.setMaxInactiveInterval(1);       
      SessionTestUtil.sleepThread(1100);      
      
      createAndUseSession(jbcm, "3", true, true);      
      
      assertEquals("Session count correct", 2, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 2, jbcm.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 3, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 1, jbcm.getExpiredSessionCount());
   }
   
   public void testStandaloneMaxSessionsWithMaxIdle()
         throws Exception
   {
      log.info("Enter testStandaloneMaxSessionsWithMaxIdle");
      
      ++testCount;
      String passDir = getPassivationDir(testCount, 1);
      JBossCacheManager jbcm = SessionTestUtil.createManager("test" + testCount, 5, true, passDir, false, false, null, caches);
       
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(1, true, 1, -1);
      jbcm.init("test.war", webMetaData);
      
      jbcm.start();
      
      assertTrue("Passivation is enabled", jbcm.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 1, jbcm.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", -1, jbcm.getPassivationMinIdleTime());

      // Set up a session
      Session sess1 = createAndUseSession(jbcm, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());
      
      // Should fail to create a 2nd
      createAndUseSession(jbcm, "2", false, false);
      
      // Confirm a session timeout clears space
      sess1.setMaxInactiveInterval(1);       
      SessionTestUtil.sleepThread(1100);      
      
      createAndUseSession(jbcm, "2", true, true);      
      
      assertEquals("Session count correct", 1, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 2, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 1, jbcm.getExpiredSessionCount());
      assertEquals("Passivated session count correct", 0, jbcm.getPassivatedSessionCount());

      //    Sleep past maxIdleTime
      SessionTestUtil.sleepThread(1100);        
      
      assertEquals("Passivated session count correct", 0, jbcm.getPassivatedSessionCount());
      
      createAndUseSession(jbcm, "3", true, true);      
      
      assertEquals("Session count correct", 1, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 3, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 1, jbcm.getExpiredSessionCount());
      assertEquals("Passivated session count correct", 1, jbcm.getPassivatedSessionCount());
      
   }
   
   public void testStandaloneMaxSessionsWithMinIdle() throws Exception
   {
      log.info("Enter testStandaloneMaxSessionsWithMinIdle");
      
      ++testCount;
      String passDir = getPassivationDir(testCount, 1);
      JBossCacheManager jbcm = SessionTestUtil.createManager("test" + testCount, 5, true, passDir, false, false, null, caches);
      
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(1, true, 3, 1);
      jbcm.init("test.war", webMetaData);
      
      jbcm.start();
      
      assertTrue("Passivation is enabled", jbcm.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm.getPassivationMinIdleTime());
      
      // Set up a session
      Session sess1 = createAndUseSession(jbcm, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());
      
      // Should fail to create a 2nd
      createAndUseSession(jbcm, "2", false, false);
      
      // Confirm a session timeout clears space
      sess1.setMaxInactiveInterval(1);       
      SessionTestUtil.sleepThread(1100);      
      
      createAndUseSession(jbcm, "2", true, false);      
      
      assertEquals("Session count correct", 1, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());     
      assertEquals("Created session count correct", 2, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 1, jbcm.getExpiredSessionCount());

      //    Sleep past minIdleTime
      SessionTestUtil.sleepThread(1100);        
      
//      assertTrue("Session 2 still valid", sess2.isValid());
      assertEquals("Passivated session count correct", 0, jbcm.getPassivatedSessionCount());
      
      createAndUseSession(jbcm, "3", true, true);      
      
      assertEquals("Session count correct", 1, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 3, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 1, jbcm.getExpiredSessionCount());
      assertEquals("Passivated session count correct", 1, jbcm.getPassivatedSessionCount());
   }
   
   public void testReplicatedMaxSessions() throws Exception
   {
      log.info("Enter testReplicatedMaxSessions");
      
      ++testCount;
      JBossCacheManager jbcm0 = SessionTestUtil.createManager("test" + testCount, 1, false, null, false, false, null, caches);
      
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(1);
      jbcm0.init("test.war", webMetaData);
      
      jbcm0.start();
      
      assertFalse("Passivation is disabled", jbcm0.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm0.getMaxActiveAllowed());
      assertEquals("Correct max inactive interval", 1, jbcm0.getMaxInactiveInterval());
      
      JBossCacheManager jbcm1 = SessionTestUtil.createManager("test" + testCount, 1, false, null, false, false, null, caches);
      
      jbcm1.init("test.war", webMetaData);
      
      jbcm1.start();
      
      assertFalse("Passivation is disabled", jbcm1.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm1.getMaxActiveAllowed());
      assertEquals("Correct max inactive interval", 1, jbcm1.getMaxInactiveInterval());
      
      // Set up a session
      Session sess1 = createAndUseSession(jbcm0, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());      
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
      
      // Should fail to create a 2nd
      createAndUseSession(jbcm1, "2", false, false);
      
      // Confirm a session timeout clears space
      sess1.setMaxInactiveInterval(1);     
      useSession(jbcm0, "1");
      SessionTestUtil.sleepThread(jbcm0.getMaxInactiveInterval() * 1000 + 100);      
      
      createAndUseSession(jbcm1, "2", true, true);      
      
      assertEquals("Session count correct", 2, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());      
      
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());
   }
   
   public void testReplicatedMaxSessionsWithMaxIdle() throws Exception
   {
      log.info("Enter testReplicatedMaxSessionsWithMaxIdle");
      
      ++testCount;
      String passDir = getPassivationDir(testCount, 1);
      JBossCacheManager jbcm0 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, false, false, null, caches);
      
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(1, true, 1, -1);
      jbcm0.init("test.war", webMetaData);
      
      jbcm0.start();
      
      assertTrue("Passivation is enabled", jbcm0.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm0.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 1, jbcm0.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", -1, jbcm0.getPassivationMinIdleTime());
      
      passDir = getPassivationDir(testCount, 2);
      JBossCacheManager jbcm1 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, false, false, null, caches);
      
      jbcm1.init("test.war", webMetaData);
      
      jbcm1.start();
      
      assertTrue("Passivation is enabled", jbcm1.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm1.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 1, jbcm1.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", -1, jbcm1.getPassivationMinIdleTime());
      
      // Set up a session
      createAndUseSession(jbcm0, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Passivated session count correct", 0, jbcm0.getPassivatedSessionCount());      
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
      assertEquals("Passivated session count correct", 0, jbcm1.getPassivatedSessionCount());
      
      // Should fail to create a 2nd
      createAndUseSession(jbcm1, "2", false, false);      
      
      //    Sleep past maxIdleTime      
      SessionTestUtil.sleepThread(1100);        
      
      assertEquals("Passivated session count correct", 0, jbcm1.getPassivatedSessionCount());
       
      createAndUseSession(jbcm1, "2", true, true);      
       
      assertEquals("Session count correct", 2, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());  
      assertEquals("Passivated session count correct", 0, jbcm0.getPassivatedSessionCount());    
       
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm1.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm1.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm1.getExpiredSessionCount()); 
      assertEquals("Passivated session count correct", 1, jbcm1.getPassivatedSessionCount());     
   }
   
   public void testReplicatedMaxSessionsWithMinIdle() throws Exception
   {
      log.info("Enter testReplicatedMaxSessionsWithMinIdle");
      
      ++testCount;
      String passDir = getPassivationDir(testCount, 1);
      JBossCacheManager jbcm0 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, false, false, null, caches);
      
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(1, true, 3, 1);
      jbcm0.init("test.war", webMetaData);
      
      jbcm0.start();
      
      assertTrue("Passivation is enabled", jbcm0.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm0.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm0.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm0.getPassivationMinIdleTime());
      
      passDir = getPassivationDir(testCount, 2);
      JBossCacheManager jbcm1 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, false, false, null, caches);
      
      jbcm1.init("test.war", webMetaData);
      
      jbcm1.start();
      
      assertTrue("Passivation is enabled", jbcm1.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm1.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm1.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm1.getPassivationMinIdleTime());
      
      // Set up a session
      createAndUseSession(jbcm0, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Passivated session count correct", 0, jbcm0.getPassivatedSessionCount());      
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
      assertEquals("Passivated session count correct", 0, jbcm0.getPassivatedSessionCount());
      
      // Should fail to create a 2nd
      createAndUseSession(jbcm1, "2", false, false);      
      
      // Sleep past maxIdleTime      
      SessionTestUtil.sleepThread(1100);        
      
      assertEquals("Passivated session count correct", 0, jbcm1.getPassivatedSessionCount());
       
      createAndUseSession(jbcm1, "2", true, true);      
       
      assertEquals("Session count correct", 2, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());  
      assertEquals("Passivated session count correct", 0, jbcm0.getPassivatedSessionCount());    
       
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm1.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm1.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm1.getExpiredSessionCount()); 
      assertEquals("Passivated session count correct", 1, jbcm1.getPassivatedSessionCount());     
      
   }
   
   public void testTotalReplication() throws Exception
   {
      log.info("Enter testTotalReplication");
      
      ++testCount;
      String passDir = getPassivationDir(testCount, 1);
      JBossCacheManager jbcm0 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, true, false, null, caches);
      
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(1, true, 3, 1);
      jbcm0.init("test.war", webMetaData);
      
      jbcm0.start();
      
      assertTrue("Passivation is enabled", jbcm0.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm0.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm0.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm0.getPassivationMinIdleTime());
      
      passDir = getPassivationDir(testCount, 2);
      JBossCacheManager jbcm1 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, true, false, null, caches);
      
      jbcm1.init("test.war", webMetaData);
      
      jbcm1.start();
      
      assertTrue("Passivation is enabled", jbcm1.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm1.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm1.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm1.getPassivationMinIdleTime());
      
      // Set up a session
      createAndUseSession(jbcm0, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());      
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
      
      // Should fail to create a 2nd
      createAndUseSession(jbcm1, "2", false, false);      
      
      // Sleep past maxIdleTime      
      SessionTestUtil.sleepThread(1100);        
      
      assertEquals("Passivated session count correct", 0, jbcm1.getPassivatedSessionCount());
       
      createAndUseSession(jbcm1, "2", true, true);      
       
      assertEquals("Session count correct", 2, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());      
       
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());      
      
   }
   
   public void testRegionBasedMarshalling() throws Exception
   {
      log.info("Enter testRegionBasedMarshalling");
      
      ++testCount;
      String passDir = getPassivationDir(testCount, 1);
      JBossCacheManager jbcm0 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, false, true, null, caches);
      
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(1, true, 3, 1);
      jbcm0.init("test.war", webMetaData);
      
      jbcm0.start();
      
      assertTrue("Passivation is enabled", jbcm0.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm0.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm0.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm0.getPassivationMinIdleTime());
      
      passDir = getPassivationDir(testCount, 2);
      JBossCacheManager jbcm1 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, false, true, null, caches);
      
      jbcm1.init("test.war", webMetaData);
      
      jbcm1.start();
      
      assertTrue("Passivation is enabled", jbcm1.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm1.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm1.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm1.getPassivationMinIdleTime());
      
      // Set up a session
      createAndUseSession(jbcm0, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());      
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
      
      // Should fail to create a 2nd
      createAndUseSession(jbcm1, "2", false, false);      
      
      // Sleep past maxIdleTime      
      SessionTestUtil.sleepThread(1100);        
      
      assertEquals("Passivated session count correct", 0, jbcm1.getPassivatedSessionCount());
       
      createAndUseSession(jbcm1, "2", true, true);      
       
      assertEquals("Session count correct", 2, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());      
       
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());      
      
   }
   
   public void testTotalReplicationWithMarshalling() throws Exception
   {
      log.info("Enter testTotalReplicationWithMarshalling");
      
      ++testCount;
      String passDir = getPassivationDir(testCount, 1);
      JBossCacheManager jbcm0 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, true, true, null, caches);
      
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(1, true, 3, 1);
      jbcm0.init("test.war", webMetaData);
      
      jbcm0.start();
      
      assertTrue("Passivation is enabled", jbcm0.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm0.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm0.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm0.getPassivationMinIdleTime());
      
      passDir = getPassivationDir(testCount, 2);
      JBossCacheManager jbcm1 = SessionTestUtil.createManager("test" + testCount, 1, false, passDir, true, true, null, caches);
      
      jbcm1.init("test.war", webMetaData);
      
      jbcm1.start();
      
      assertTrue("Passivation is enabled", jbcm1.isPassivationEnabled());
      assertEquals("Correct max active count", 1, jbcm1.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm1.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm1.getPassivationMinIdleTime());
      
      // Set up a session
      createAndUseSession(jbcm0, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());      
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
      
      // Should fail to create a 2nd
      createAndUseSession(jbcm1, "2", false, false);      
      
      // Sleep past maxIdleTime      
      SessionTestUtil.sleepThread(1100);        
      
      assertEquals("Passivated session count correct", 0, jbcm1.getPassivatedSessionCount());
       
      createAndUseSession(jbcm1, "2", true, true);      
       
      assertEquals("Session count correct", 2, jbcm0.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());      
       
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm0.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm0.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm0.getExpiredSessionCount());      
      
   }
   
   public void testStandaloneRedeploy() throws Exception
   {
      log.info("Enter testStandaloneRedeploy");
      
      standaloneWarRedeployTest(false);
   }
   
   public void testStandaloneRestart() throws Exception
   {
      log.info("Enter testStandaloneRedeploy");
      
      standaloneWarRedeployTest(true);
   }
   
   private void standaloneWarRedeployTest(boolean restartCache)
         throws Exception
   {
      ++testCount;
      String passDir = getPassivationDir(testCount, 1);
      JBossCacheManager jbcm = SessionTestUtil.createManager("test" + testCount, 300, true, passDir, false, false, null, caches);
      PojoCache cache = SessionTestUtil.getDistributedCacheManagerFactoryPojoCache();
      
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(2, true, 3, 1);
      jbcm.init("test.war", webMetaData);
      
      jbcm.start();
      
      assertTrue("Passivation is enabled", jbcm.isPassivationEnabled());
      assertEquals("Correct max active count", 2, jbcm.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm.getPassivationMinIdleTime());
      
      // Set up a session
      createAndUseSession(jbcm, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());
      
      // And a 2nd
      createAndUseSession(jbcm, "2", true, true);     
      
      assertEquals("Session count correct", 2, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 2, jbcm.getLocalActiveSessionCount());     
      assertEquals("Created session count correct", 2, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm.getExpiredSessionCount());

      //    Sleep past minIdleTime
      SessionTestUtil.sleepThread(1100);
      
      assertEquals("Passivated session count correct", 0, jbcm.getPassivatedSessionCount());
      
      createAndUseSession(jbcm, "3", true, true);      
      
      assertEquals("Session count correct", 2, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 2, jbcm.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 3, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm.getExpiredSessionCount());
      assertEquals("Passivated session count correct", 1, jbcm.getPassivatedSessionCount());
      
      jbcm.stop();
      
      if (restartCache)
      {
         cache.stop();
         cache.destroy();
         caches.remove(cache);
         
         passDir = getPassivationDir(testCount, 1);
         jbcm = SessionTestUtil.createManager("test" + testCount, 300, true, passDir, false, false, null, caches);
      }
      else
      {
         jbcm = SessionTestUtil.createManager("test" + testCount, 300, cache, null);
      }
      jbcm.init("test.war", webMetaData);
      
      jbcm.start();
      
      assertTrue("Passivation is enabled", jbcm.isPassivationEnabled());
      assertEquals("Correct max active count", 2, jbcm.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 3, jbcm.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm.getPassivationMinIdleTime());     
      
      assertEquals("Session count correct", 2, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 0, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm.getExpiredSessionCount());
      assertEquals("Passivated session count correct", 1, jbcm.getPassivatedSessionCount());
      
      // Sleep past minIdleTime
      SessionTestUtil.sleepThread(1100);
      
      createAndUseSession(jbcm, "4", true, true); 
   }
   
   public void testReplicatedRedeploy() throws Exception
   {
      log.info("Enter testReplicatedRedeploy");
      
      replicatedWarRedeployTest(false, false, false, false);
   }
   
   public void testReplicatedRestart() throws Exception
   {
      log.info("Enter testReplicatedRestart");
      
      replicatedWarRedeployTest(true, true, false, false);
      
   }
   
   private void replicatedWarRedeployTest(boolean restartCache, 
                                          boolean fullRestart,
                                          boolean totalReplication,
                                          boolean marshalling)
         throws Exception
   {
      ++testCount;
      String passDir = getPassivationDir(testCount, 1);
      JBossCacheManager jbcm = SessionTestUtil.createManager("test" + testCount, 300, false, passDir, totalReplication, marshalling, null, caches);
      PojoCache cache = SessionTestUtil.getDistributedCacheManagerFactoryPojoCache();
      
      JBossWebMetaData webMetaData = SessionTestUtil.createWebMetaData(2, true, 30, 1);
      jbcm.init("test.war", webMetaData);
      
      jbcm.start();
      
      assertTrue("Passivation is enabled", jbcm.isPassivationEnabled());
      assertEquals("Correct max active count", 2, jbcm.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 30, jbcm.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm.getPassivationMinIdleTime());
      
      passDir = getPassivationDir(testCount, 2);
      JBossCacheManager jbcm1 = SessionTestUtil.createManager("test" + testCount, 300, false, passDir, totalReplication, marshalling, null, caches);
      PojoCache cache1 = SessionTestUtil.getDistributedCacheManagerFactoryPojoCache();
      
      jbcm1.init("test.war", webMetaData);
      
      jbcm1.start();
      
      SessionTestUtil.blockUntilViewsReceived(new Cache[]{ cache.getCache(), cache1.getCache()}, 10000);
      
      assertTrue("Passivation is enabled", jbcm1.isPassivationEnabled());
      assertEquals("Correct max active count", 2, jbcm1.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 30, jbcm1.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm1.getPassivationMinIdleTime());
      
      // Set up a session
      createAndUseSession(jbcm, "1", true, true);
      
      assertEquals("Session count correct", 1, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());    
      assertEquals("Session count correct", 1, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm1.getLocalActiveSessionCount());
      
      // Create a 2nd
      createAndUseSession(jbcm1, "2", true, true);     
      
      assertEquals("Session count correct", 2, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());     
      assertEquals("Created session count correct", 1, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm.getExpiredSessionCount());
      assertEquals("Session count correct", 2, jbcm1.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm1.getLocalActiveSessionCount());     
      assertEquals("Created session count correct", 1, jbcm1.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm1.getExpiredSessionCount());

      //    Sleep past minIdleTime
      SessionTestUtil.sleepThread(1100);
      
      assertEquals("Passivated session count correct", 0, jbcm1.getPassivatedSessionCount());
      
      createAndUseSession(jbcm1, "3", true, true);      
      
      // jbcm has 3 active because receipt of repl doesn't trigger passivation
      assertEquals("Session count correct", 3, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 1, jbcm.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 1, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm.getExpiredSessionCount());
      assertEquals("Passivated session count correct", 0, jbcm.getPassivatedSessionCount());
      // jbcm1 only has 2 active since it passivated one when it created 3rd 
      assertEquals("Session count correct", 2, jbcm1.getActiveSessionCount());
      // Both active sessions are local, as the remote session is oldest so we passivate it first 
      assertEquals("Local session count correct", 2, jbcm1.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 2, jbcm1.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm1.getExpiredSessionCount());
      assertEquals("Passivated session count correct", 1, jbcm1.getPassivatedSessionCount());
      
      if (fullRestart)
      {
        jbcm1.stop();
        cache1.stop();
        cache1.destroy();
        caches.remove(cache1);
        // Give jbcm a bit of time to react to view change before
        // stopping it
        SessionTestUtil.sleepThread(100);
      }
      
      jbcm.stop();
      
      if (restartCache)
      {
         cache.stop();
         cache.destroy();
         caches.remove(cache);
         
         passDir = getPassivationDir(testCount, 1);
         jbcm = SessionTestUtil.createManager("test" + testCount, 300, false, passDir, totalReplication, marshalling, null, caches);
      }
      else
      {
         jbcm = SessionTestUtil.createManager("test" + testCount, 300, cache, null);
      }
      jbcm.init("test.war", webMetaData);
      
      jbcm.start();
      
      assertTrue("Passivation is enabled", jbcm.isPassivationEnabled());
      assertEquals("Correct max active count", 2, jbcm.getMaxActiveAllowed());
      assertEquals("Correct max idle time", 30, jbcm.getPassivationMaxIdleTime());
      assertEquals("Correct min idle time", 1, jbcm.getPassivationMinIdleTime());     
      
      int expected = (totalReplication && marshalling && fullRestart) ? 0 : 2;
      assertEquals("Session count correct", expected, jbcm.getActiveSessionCount());
      assertEquals("Local session count correct", 0, jbcm.getLocalActiveSessionCount());
      assertEquals("Created session count correct", 0, jbcm.getCreatedSessionCount());
      assertEquals("Expired session count correct", 0, jbcm.getExpiredSessionCount());
      expected = (totalReplication && !(marshalling && fullRestart)) ? 1 : 0;
      assertEquals("Passivated session count correct", expected, jbcm.getPassivatedSessionCount());
      
      if (!fullRestart)
      {
         assertEquals("Session count correct", 2, jbcm1.getActiveSessionCount());
         assertEquals("Local session count correct", 2, jbcm1.getLocalActiveSessionCount());
         assertEquals("Created session count correct", 2, jbcm1.getCreatedSessionCount());
         assertEquals("Expired session count correct", 0, jbcm1.getExpiredSessionCount());
         assertEquals("Passivated session count correct", 1, jbcm1.getPassivatedSessionCount());
      }
      // Sleep past minIdleTime
      SessionTestUtil.sleepThread(1100);
      
      createAndUseSession(jbcm, "4", true, true); 
   }
   
   public void testTotalReplicatedRedeploy() throws Exception
   {
      log.info("Enter testTotalReplicatedRedeploy");
      
      replicatedWarRedeployTest(false, false, true, false);
      
   }
   
   public void testTotalReplicatedRestart() throws Exception
   {
      log.info("Enter testTotalReplicatedRestart");
      
      replicatedWarRedeployTest(true, true, true, false);
      
   }
   
   public void testMarshalledRedeploy() throws Exception
   {
      log.info("Enter testMarshalledRedeploy");
      
      replicatedWarRedeployTest(false, false, false, true);
      
   }
   
   public void testMarshalledRestart() throws Exception
   {
      log.info("Enter testMarshalledRestart");
      
      replicatedWarRedeployTest(true, true, false, true);
      
   }
   
   public void testTotalMarshalledRedeploy() throws Exception
   {
      log.info("Enter testTotalMarshalledRedeploy");
      
      replicatedWarRedeployTest(false, false, true, true);
      
   }
   
   public void testTotalMarshalledRestart() throws Exception
   {
      log.info("Enter testTotalMarshalledRestart");
      
      replicatedWarRedeployTest(true, true, true, true);
      
   }
   
   private Session createAndUseSession(JBossCacheManager jbcm, String id, 
                           boolean canCreate, boolean access)
         throws Exception
   {
      //    Shift to Manager interface when we simulate Tomcat
      Manager mgr = jbcm;
      Session sess = mgr.findSession(id);
      assertNull("session does not exist", sess);
      try
      {
         sess = mgr.createSession(id);
         if (!canCreate)
            fail("Could not create session" + id);
      }
      catch (IllegalStateException ise)
      {
         if (canCreate)
         {
            log.error("Failed to create session " + id, ise);
            fail("Could create session " + id);
         }
      }
      
      if (access)
      {
         sess.access();
         sess.getSession().setAttribute("test", "test");
         
         jbcm.storeSession(sess);
         
         sess.endAccess();
      }
      
      return sess;
   }
   
   private void useSession(JBossCacheManager jbcm, String id)
         throws Exception
   {
      //    Shift to Manager interface when we simulate Tomcat
      Manager mgr = jbcm;
      Session sess = mgr.findSession(id);
      assertNotNull("session exists", sess);
      
      sess.access();
      sess.getSession().setAttribute("test", "test");
      
      jbcm.storeSession(sess);
      
      sess.endAccess();
   }
   
   private String getPassivationDir(long testCount, int cacheCount)
   {
      File dir = new File(tempDir);
      dir = new File(dir, String.valueOf(testCount));
      dir.mkdirs();
      dir.deleteOnExit();
      dir = new File(dir, String.valueOf(cacheCount));
      dir.mkdirs();
      dir.deleteOnExit();
      return dir.getAbsolutePath();
   }
}
