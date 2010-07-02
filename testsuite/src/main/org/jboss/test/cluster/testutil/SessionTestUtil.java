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
package org.jboss.test.cluster.testutil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.loader.FileCacheLoaderConfig;
import org.jboss.cache.pojo.PojoCache;
import org.jboss.cache.pojo.PojoCacheFactory;
import org.jboss.metadata.javaee.spec.EmptyMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.metadata.web.jboss.PassivationConfig;
import org.jboss.metadata.web.jboss.ReplicationConfig;
import org.jboss.metadata.web.jboss.ReplicationGranularity;
import org.jboss.metadata.web.jboss.ReplicationTrigger;
import org.jboss.metadata.web.jboss.SnapshotMode;
import org.jboss.test.cluster.web.CacheHelper;
import org.jboss.test.cluster.web.mocks.MockEngine;
import org.jboss.test.cluster.web.mocks.MockHost;
import org.jboss.test.cluster.web.mocks.MockRequest;
import org.jboss.test.cluster.web.mocks.MockValve;
import org.jboss.test.cluster.web.mocks.RequestHandler;
import org.jboss.test.cluster.web.mocks.RequestHandlerValve;
import org.jboss.web.tomcat.service.session.JBossCacheManager;
import org.jboss.web.tomcat.service.session.distributedcache.impl.DistributedCacheManagerFactoryImpl;
import org.jboss.web.tomcat.service.session.distributedcache.spi.ClusteringNotSupportedException;
import org.jboss.web.tomcat.service.session.distributedcache.spi.DistributedCacheManagerFactoryFactory;
import org.jgroups.Address;

/**
 * Utilities for session testing.
 * 
 * @author <a href="mailto://brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 100315 $
 */
public class SessionTestUtil
{  
   private static final String[] STRING_ONLY_TYPES = { String.class.getName() };
   private static final String[] STRING_BOOLEAN_TYPES = { String.class.getName(), boolean.class.getName() };
   private static final String CONFIG_LOCATION = "cluster/http/jboss-web-test-service.xml";
   private static DistributedCacheManagerFactoryImpl distributedManagerFactory;
   static
   {
      try
      {
         distributedManagerFactory = (DistributedCacheManagerFactoryImpl) DistributedCacheManagerFactoryFactory.getInstance().getDistributedCacheManagerFactory();
      }
      catch (ClusteringNotSupportedException e)
      {
         e.printStackTrace();
      }
   }
   
   public static JBossCacheManager createManager(String warName, int maxInactiveInterval, 
                                                 boolean local, String passivationDir, 
                                                 boolean totalReplication, boolean marshalling, 
                                                 String jvmRoute, Set<PojoCache> allCaches)
   {
      return createManager(warName, maxInactiveInterval, local, passivationDir, 
                           totalReplication, marshalling, false, jvmRoute, allCaches);
   }
   
   public static JBossCacheManager createManager(String warName, int maxInactiveInterval, 
                                                 boolean local, String passivationDir, 
                                                 boolean totalReplication, boolean marshalling,
                                                 boolean purgeCacheLoader,
                                                 String jvmRoute, Set<PojoCache> allCaches)
   {
      PojoCache cache = createCache(local, passivationDir, totalReplication, marshalling, allCaches);
      return createManager(warName, maxInactiveInterval, cache, jvmRoute);
   }
   
   public static JBossCacheManager createManager(String warName, 
                                                 int maxInactiveInterval, 
                                                 PojoCache cache, 
                                                 String jvmRoute)
   {
      if (distributedManagerFactory == null)
         throw new IllegalStateException("Failed to initialize distributedManagerFactory");
      
      distributedManagerFactory.setPojoCache(cache);
      JBossCacheManager jbcm = new JBossCacheManager(distributedManagerFactory);
      jbcm.setSnapshotMode(SnapshotMode.INSTANT);
      
      setupContainer(warName, jvmRoute, jbcm);
      
      // Do this after assigning the manager to the container, or else
      // the container's setting will override ours
      // Can't just set the container as their config is per minute not per second
      jbcm.setMaxInactiveInterval(maxInactiveInterval);
   
      return jbcm;      
   }
   
   public static PojoCache createCache(boolean local, String passivationDir, 
         boolean totalReplication, boolean marshalling, Set<PojoCache> allCaches)
   {
      return createCache(local, passivationDir, totalReplication, marshalling, false, allCaches);
   }
   
   public static PojoCache createCache(boolean local, String passivationDir, 
         boolean totalReplication, boolean marshalling, boolean purgeCacheLoader, Set<PojoCache> allCaches)
   {
      Configuration cfg = getConfiguration(local, passivationDir, totalReplication, marshalling, purgeCacheLoader);
      PojoCache cache = PojoCacheFactory.createCache(cfg, true);
      
      if (allCaches != null)
         allCaches.add(cache);
      return cache;
   }
   
   public static Configuration getConfiguration(boolean local, String passivationDir, 
         boolean totalReplication, boolean marshalling, boolean purgeCacheLoader)
   {
      PojoCache temp = PojoCacheFactory.createCache(CONFIG_LOCATION, false);
      Configuration config = temp.getCache().getConfiguration();
      if (local)
         config.setCacheMode(CacheMode.LOCAL);
      if (passivationDir == null)
      {
         config.setCacheLoaderConfig(null);
      }
      else
      {
         CacheLoaderConfig clc = config.getCacheLoaderConfig();
         FileCacheLoaderConfig fclc = new FileCacheLoaderConfig();
         fclc.setProperties(clc.getFirstCacheLoaderConfig().getProperties());
         fclc.setLocation(passivationDir);
         fclc.setFetchPersistentState(true);
         fclc.setPurgeOnStartup(purgeCacheLoader);
         ArrayList<IndividualCacheLoaderConfig> iclcs = new ArrayList<IndividualCacheLoaderConfig>();
         iclcs.add(fclc);
         clc.setIndividualCacheLoaderConfigs(iclcs);
      }
      
      BuddyReplicationConfig brc = config.getBuddyReplicationConfig();
      brc.setEnabled(!local && !totalReplication);
      
      config.setUseRegionBasedMarshalling(marshalling);
      config.setInactiveOnStartup(marshalling);    
      
      // No async marshalling or notifications
      config.setSerializationExecutorPoolSize(0);
      config.setListenerAsyncPoolSize(0);  
      
      // Block for commits -- no races between test driver and replication
      config.setSyncCommitPhase(true);
      config.setSyncRollbackPhase(true);
      
      return config;
   } 
   
   public static PojoCache getDistributedCacheManagerFactoryPojoCache()
   {
      return distributedManagerFactory.getPojoCache();
   } 
   
   @SuppressWarnings("unchecked")
   public static Cache<Object, Object> getDistributedCacheManagerFactoryPlainCache()
   {
      return distributedManagerFactory.getPlainCache();
   }
   
   public static void clearDistributedCacheManagerFactory()
   {
      distributedManagerFactory.clearCaches();
   }

   public static void setupContainer(String warName, String jvmRoute, Manager mgr)
   {
      MockEngine engine = new MockEngine();
      engine.setJvmRoute(jvmRoute);
      MockHost host = new MockHost();
      engine.addChild(host);
      host.setName("localhost");
      StandardContext container = new StandardContext();
      container.setName(warName);
      host.addChild(container);
      container.setManager(mgr);
   }
   
   public static JBossWebMetaData createWebMetaData(int maxSessions)
   {
      return createWebMetaData(ReplicationGranularity.SESSION,
                               ReplicationTrigger.SET_AND_NON_PRIMITIVE_GET,
                               maxSessions, false, -1, -1, false, 0);
   } 
   
   public static JBossWebMetaData createWebMetaData(int maxSessions, boolean passivation,
         int maxIdle, int minIdle)
   {
      return createWebMetaData(ReplicationGranularity.SESSION,
                               ReplicationTrigger.SET_AND_NON_PRIMITIVE_GET,
                               maxSessions, passivation, maxIdle, minIdle, false, 60);
   } 
   
   public static JBossWebMetaData createWebMetaData(ReplicationGranularity granularity,
                                                    ReplicationTrigger trigger,boolean batchMode,
                                                    int maxUnreplicated)
   {
      return createWebMetaData(granularity, trigger, -1, false, 
                               -1, -1, batchMode, maxUnreplicated);
   } 
   
   public static JBossWebMetaData createWebMetaData(ReplicationGranularity granularity,
                                              ReplicationTrigger trigger,
                                              int maxSessions, boolean passivation,
                                              int maxIdle, int minIdle,
                                              boolean batchMode,
                                              int maxUnreplicated)
   {
      JBossWebMetaData webMetaData = new JBossWebMetaData();
      webMetaData.setDistributable(new EmptyMetaData());
      webMetaData.setMaxActiveSessions(new Integer(maxSessions));
      PassivationConfig pcfg = new PassivationConfig();
      pcfg.setUseSessionPassivation(Boolean.valueOf(passivation));
      pcfg.setPassivationMaxIdleTime(new Integer(maxIdle));
      pcfg.setPassivationMinIdleTime(new Integer(minIdle));
      webMetaData.setPassivationConfig(pcfg);
      ReplicationConfig repCfg = new ReplicationConfig();
      repCfg.setReplicationGranularity(granularity);
      repCfg.setReplicationTrigger(trigger);
      repCfg.setReplicationFieldBatchMode(Boolean.valueOf(batchMode));
      repCfg.setMaxUnreplicatedInterval(Integer.valueOf(maxUnreplicated));
      webMetaData.setReplicationConfig(repCfg);
      return webMetaData;
   }
   
   public static void invokeRequest(Manager manager, RequestHandler handler, String sessionId)
      throws ServletException, IOException
   {
      Valve valve = setupPipeline(manager, handler);
      Request request = setupRequest(manager, sessionId);
      invokeRequest(valve, request);
   }
   
   public static void invokeRequest(Valve pipelineHead, Request request)
      throws ServletException, IOException
   {
      pipelineHead.invoke(request, request.getResponse());
      // StandardHostValve calls request.getSession(false) on way out, so we will too
      request.getSession(false);
      request.recycle();
   }
   
   public static Valve setupPipeline(Manager manager, RequestHandler requestHandler)
   {
      Pipeline pipeline = manager.getContainer().getPipeline();
      
      // Clean out any existing request handler
      Valve[] valves = pipeline.getValves();
      RequestHandlerValve mockValve = null;
      for (Valve valve: valves)
      {
         if (valve instanceof RequestHandlerValve)         
         {
            mockValve = (RequestHandlerValve) valve;
            break;
         }
      }
      
      if (mockValve == null)
      {
         mockValve = new RequestHandlerValve(requestHandler);
         pipeline.addValve(mockValve);
      }
      else
      {
         mockValve.setRequestHandler(requestHandler);
      }
      
      return pipeline.getFirst();
   }
   
   public static Request setupRequest(Manager manager, String sessionId)
   {
      MockRequest request = new MockRequest();
      request.setRequestedSessionId(sessionId);
      request.setContext((Context) manager.getContainer());
      Response response = new Response();
      request.setResponse(response);
      return request;
      
   }
   
   public static void cleanupPipeline(Manager manager)
   {
      Pipeline pipeline = manager.getContainer().getPipeline();
      
      Valve[] valves = pipeline.getValves();
      for (Valve valve: valves)
      {
         if (valve instanceof MockValve)         
         {
            ((MockValve) valve).clear();
         }
      }
   }
   
   public static Object getSessionVersion(MBeanServerConnection adaptor, String sessionFqn) throws Exception
   {
      return adaptor.invoke(CacheHelper.OBJECT_NAME, 
                            "getSessionVersion", 
                            new Object[] { sessionFqn }, 
                            STRING_ONLY_TYPES);
   }

   public static Object getBuddySessionVersion(MBeanServerConnection adaptor, String sessionFqn) throws Exception
   {

      return adaptor.invoke(CacheHelper.OBJECT_NAME, 
                            "getBuddySessionVersion", 
                            new Object[] { sessionFqn }, 
                            STRING_ONLY_TYPES);
   }
   
   public static void setCacheConfigName(MBeanServerConnection adaptor, String cacheConfigName, boolean usePojoCache) throws Exception
   {
      adaptor.invoke(CacheHelper.OBJECT_NAME, 
                     "setCacheConfigName", 
                     new Object[] { cacheConfigName, Boolean.valueOf(usePojoCache) }, 
                     new String[]{ String.class.getName(), boolean.class.getName() });
   }
   
   @SuppressWarnings("unchecked")
   public static Set<String> getSessionIds(MBeanServerConnection adaptor, String warFqn) throws Exception
   {
      return (Set<String>) adaptor.invoke(CacheHelper.OBJECT_NAME, 
                           "getSessionIds", 
                           new Object[] { warFqn }, 
                           STRING_ONLY_TYPES);
   }
   
   @SuppressWarnings("unchecked")
   public static Set<String> getSessionIds(MBeanServerConnection adaptor, String warFqn, boolean includeBuddies) throws Exception
   {
      return (Set<String>) adaptor.invoke(CacheHelper.OBJECT_NAME, 
                           "getSessionIds", 
                           new Object[] { warFqn, Boolean.valueOf(includeBuddies) }, 
                           STRING_BOOLEAN_TYPES);
   }
   
   public static boolean isBuddyReplication() throws Exception
   {
      return Boolean.parseBoolean(System.getProperty("jbosstest.cluster.web.cache.br", "false"));
   }
   
   public static String getSessionFqn(String contextPath, String sessionId)
   {
      return "/JSESSION/" +  SessionTestUtil.getContextHostPath("localhost", contextPath) + "/" + sessionId;
   }
   
   public static String getContextHostPath(String hostname, String contextPath)
   {
      return contextPath + "_" + hostname;
   }

   /**
    * Loops, continually calling {@link #areCacheViewsComplete(org.jboss.cache.Cache[])}
    * until it either returns true or <code>timeout</code> ms have elapsed.
    *
    * @param caches  caches which must all have consistent views
    * @param timeout max number of ms to loop
    * @throws RuntimeException if <code>timeout</code> ms have elapse without
    *                          all caches having the same number of members.
    */
   public static void blockUntilViewsReceived(Cache<?, ?>[] caches, long timeout)
   {
      long failTime = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < failTime)
      {
         sleepThread(100);
         if (areCacheViewsComplete(caches))
         {
            return;
         }
      }

      throw new RuntimeException("timed out before caches had complete views");
   }

   /**
    * Checks each cache to see if the number of elements in the array
    * returned by {@link CacheSPI#getMembers()} matches the size of
    * the <code>caches</code> parameter.
    *
    * @param caches caches that should form a View
    * @return <code>true</code> if all caches have
    *         <code>caches.length</code> members; false otherwise
    * @throws IllegalStateException if any of the caches have MORE view
    *                               members than caches.length
    */
   public static boolean areCacheViewsComplete(Cache<?, ?>[] caches)
   {
      return areCacheViewsComplete(caches, true);
   }

   public static boolean areCacheViewsComplete(Cache<?, ?>[] caches, boolean barfIfTooManyMembers)
   {
      int memberCount = caches.length;

      for (int i = 0; i < memberCount; i++)
      {
         if (!isCacheViewComplete(caches[i], memberCount, barfIfTooManyMembers))
         {
            return false;
         }
      }

      return true;
   }

   public static boolean isCacheViewComplete(Cache<?, ?> c, int memberCount, boolean barfIfTooManyMembers)
   {
      CacheSPI<?, ?> cache = (CacheSPI<?, ?>) c;
      List<Address> members = cache.getMembers();
      if (members == null || memberCount > members.size())
      {
         return false;
      }
      else if (memberCount < members.size())
      {
         if (barfIfTooManyMembers)
         {
            // This is an exceptional condition
            StringBuffer sb = new StringBuffer("Cache at address ");
            sb.append(cache.getLocalAddress());
            sb.append(" had ");
            sb.append(members.size());
            sb.append(" members; expecting ");
            sb.append(memberCount);
            sb.append(". Members were (");
            for (int j = 0; j < members.size(); j++)
            {
               if (j > 0)
               {
                  sb.append(", ");
               }
               sb.append(members.get(j));
            }
            sb.append(')');

            throw new IllegalStateException(sb.toString());
         }
         else return false;
      }

      return true;
   }
   
   public static Cookie getSessionCookie(HttpClient client)
   {
      // Get the state for the JSESSIONID
      HttpState state = client.getState();
      // Get the JSESSIONID so we can reset the host
      Cookie[] cookies = state.getCookies();
      Cookie sessionID = null;
      for(int c = 0; c < cookies.length; c ++)
      {
         Cookie k = cookies[c];
         if( k.getName().equalsIgnoreCase("JSESSIONID") )
            sessionID = k;
      }
      return sessionID;
   }

   public static void setCookieDomainToThisServer(HttpClient client, String server)
   {
      // Get the session cookie
      Cookie sessionID = getSessionCookie(client);
      if (sessionID == null)
         throw new IllegalStateException("No session cookie found on " + client);
      // Reset the domain so that the cookie will be sent to server1
      sessionID.setDomain(server);
      client.getState().addCookie(sessionID);
   }


   /**
    * Puts the current thread to sleep for the desired number of ms, suppressing
    * any exceptions.
    *
    * @param sleeptime number of ms to sleep
    */
   public static void sleepThread(long sleeptime)
   {
      try
      {
         Thread.sleep(sleeptime);
      }
      catch (InterruptedException ie)
      {
      }
   }
   
   public static Object getAttributeValue(int value)
   {
      return Integer.valueOf(value);
   }

   public static void cleanPassivationDir(File root)
   {
	   if (root.exists())
	   {
		   if (root.isDirectory())
		   {
			   for (File child : root.listFiles())
			   {
				   cleanPassivationDir(child);
			   }
		   }
		   if (!root.delete())
		   {
			   root.deleteOnExit();
		   }
	   }
   }

   private SessionTestUtil() {}
}
