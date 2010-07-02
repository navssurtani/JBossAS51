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
package org.jboss.web.tomcat.service.session;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.catalina.Valve;
import org.apache.catalina.core.ContainerBase;
import org.jboss.logging.Logger;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.metadata.web.jboss.ReplicationConfig;
import org.jboss.metadata.web.jboss.ReplicationGranularity;
import org.jboss.metadata.web.jboss.ReplicationTrigger;
import org.jboss.metadata.web.jboss.SnapshotMode;
import org.jboss.util.loading.ContextClassLoaderSwitcher;
import org.jboss.web.tomcat.service.session.distributedcache.spi.BatchingManager;
import org.jboss.web.tomcat.service.session.distributedcache.spi.ClusteringNotSupportedException;
import org.jboss.web.tomcat.service.session.distributedcache.spi.DistributableSessionMetadata;
import org.jboss.web.tomcat.service.session.distributedcache.spi.DistributedCacheManager;
import org.jboss.web.tomcat.service.session.distributedcache.spi.DistributedCacheManagerFactory;
import org.jboss.web.tomcat.service.session.distributedcache.spi.DistributedCacheManagerFactoryFactory;
import org.jboss.web.tomcat.service.session.distributedcache.spi.IncomingDistributableSessionData;
import org.jboss.web.tomcat.service.session.distributedcache.spi.LocalDistributableSessionManager;
import org.jboss.web.tomcat.service.session.distributedcache.spi.OutgoingAttributeGranularitySessionData;
import org.jboss.web.tomcat.service.session.distributedcache.spi.OutgoingDistributableSessionData;
import org.jboss.web.tomcat.service.session.distributedcache.spi.OutgoingSessionGranularitySessionData;
import org.jboss.web.tomcat.service.session.notification.ClusteredSessionNotificationCapability;
import org.jboss.web.tomcat.service.session.notification.ClusteredSessionNotificationCause;
import org.jboss.web.tomcat.service.session.notification.ClusteredSessionNotificationPolicy;
import org.jboss.web.tomcat.service.session.notification.IgnoreUndeployLegacyClusteredSessionNotificationPolicy;

/**
 * Implementation of a clustered session manager for
 * catalina using JBossCache replication.
 *
 * @author Ben Wang
 * @author Brian Stansberry
 * @author Hany Mesha
 * @version $Revision: 104645 $
 */
public class JBossCacheManager<O extends OutgoingDistributableSessionData>
   extends JBossManager
   implements JBossCacheManagerMBean, LocalDistributableSessionManager, ClusteredManager<O>
{
   // --------------------------------------------------------------- Constants
   
   /**
    * Informational name for this Catalina component
    */
   private static final String info_ = "JBossCacheManager/1.0";

   private static final int TOTAL_PERMITS = Integer.MAX_VALUE;
   
   // ------------------------------------------------------------------ Fields

   /** The transaction manager. */
   private BatchingManager batchingManager;

   /** Proxy-object for the JBossCache */
   private DistributedCacheManager<O> proxy_;

   /** The factory for our distributed cache manager */
   private final DistributedCacheManagerFactory distributedCacheManagerFactory;
   
   /** Id/timestamp of sessions in distributedcache that we haven't loaded locally*/
   private Map<String, OwnedSessionUpdate> unloadedSessions_ = 
         new ConcurrentHashMap<String, OwnedSessionUpdate>();

   /** 
    * Sessions that have been created but not yet loaded. Used to ensure
    * concurrent threads trying to load the same session
    */
   private final ConcurrentMap<String, ClusteredSession<? extends OutgoingDistributableSessionData>> embryonicSessions = 
      new ConcurrentHashMap<String, ClusteredSession<? extends OutgoingDistributableSessionData>>();
   
   /** Number of passivated sessions */
   private AtomicInteger passivatedCount_ = new AtomicInteger();
   
   /** Maximum number of concurrently passivated sessions */
   private AtomicInteger maxPassivatedCount_ = new AtomicInteger();
   
   /** If set to true, will add a JvmRouteFilter to the request. */
   private Boolean useJK_;

   /** Are we running embedded in JBoss? */
   private boolean embedded_ = false;

   /** Our ClusteredSessionValve's snapshot mode. */
   private SnapshotMode snapshotMode_ = null;

   /** Our ClusteredSessionValve's snapshot interval. */
   private int snapshotInterval_ = 0;
   
   /** Replication granularity. */
   private ReplicationGranularity replicationGranularity_;

   /** Policy to determine if a session is dirty */
   private ReplicationTrigger replicationTrigger_;

   /**
    * Whether we use batch mode replication for field level granularity.
    * We store this in a Boolean rather than a primitive so JBossCacheCluster
    * can determine if this was set via a <Manager> element.
    */
   private Boolean replicationFieldBatchMode_;
   
   /** Class loader for this web app. */
   private ClassLoader tcl_;
   
   /** The snapshot manager we are using. */
   private SnapshotManager snapshotManager_;
   
   /** The name of our cache's configuration */
   private String cacheConfigName_;
   
   private int maxUnreplicatedInterval_ = -1;

   private String notificationPolicyClass_;
   private ClusteredSessionNotificationPolicy notificationPolicy_;
   
   private ReplicationConfig replicationConfig_;

   private Semaphore semaphore = new Semaphore(TOTAL_PERMITS, true);
   private Lock valveLock = new SemaphoreLock(this.semaphore);

   private OutdatedSessionChecker outdatedSessionChecker;
   
   private volatile boolean stopping;
   
   //  ----------------------------------------------------------  Constructors

   public JBossCacheManager() throws ClusteringNotSupportedException
   {
      this(DistributedCacheManagerFactoryFactory.getInstance().getDistributedCacheManagerFactory());
   }
   
   public JBossCacheManager(DistributedCacheManagerFactory distributedManagerFactory)
   {
      assert distributedManagerFactory != null : "distributedManagerFactory is null";
      
      this.distributedCacheManagerFactory = distributedManagerFactory;
   }

   // ---------------------------------------------------- AbstractJBossManager

   /**
    * {@inheritDoc}
    * <p>
    * <strong>NOTE:</strong> This method should not be called when
    * running unembedded.
    * </p>
    */
   @Override
   public void init(String name, JBossWebMetaData webMetaData)
      throws ClusteringNotSupportedException
   {
      super.init(name, webMetaData);
      
      this.replicationConfig_ = webMetaData.getReplicationConfig();
      this.replicationTrigger_ = replicationConfig_.getReplicationTrigger();
      this.replicationGranularity_ = replicationConfig_.getReplicationGranularity();
      
      // Only configure JK usage if it was explicitly set; otherwise
      // wait until we're starting when we can check for a jvmRoute
      // in our containing Engine
      Boolean jk = replicationConfig_.getUseJK();
      if (jk != null)
      {
         this.useJK_ = jk;
      }
      
      Boolean batch = replicationConfig_.getReplicationFieldBatchMode();
      this.replicationFieldBatchMode_ = (batch == null ? Boolean.TRUE : batch);
      setSnapshotMode(replicationConfig_.getSnapshotMode());
      Integer snapshotInt = replicationConfig_.getSnapshotInterval();
      setSnapshotInterval(snapshotInt == null ? 0 : snapshotInt.intValue());
      
      Integer maxUnrep = replicationConfig_.getMaxUnreplicatedInterval();
      if (maxUnrep != null)
      {
         this.maxUnreplicatedInterval_ = maxUnrep.intValue();
      }
      
      log_.debug("init(): replicationGranularity_ is " + replicationGranularity_ +
         " and replicationTrigger is " + replicationTrigger_ +
         " and replicationFieldBatchMode is " + replicationFieldBatchMode_ +
         " and useJK is " + useJK_ +
         " and snapshotMode is " + snapshotMode_ +
         " and snapshotInterval is " + snapshotInterval_);     
      
      this.cacheConfigName_ = replicationConfig_.getCacheName();
      
      this.notificationPolicyClass_ = replicationConfig_.getSessionNotificationPolicy();
      
      // Initing the proxy would be better in start, but we do it here so we
      // can detect ClusteringNotSupportedException at this deploy stage
      initDistributedCacheManager();

      embedded_ = true;
   }

   /**
    * {@inheritDoc}
    * <p>
    * Removes the session from this Manager's collection of actively managed
    * sessions.  Also removes the session from this server's copy of the
    * distributed cache (but does not remove it from other servers'
    * distributed cache).
    * </p>
    */
   public void removeLocal(Session session)
   {
      ClusteredSession<? extends OutgoingDistributableSessionData> clusterSess = uncheckedCastSession(session);
      synchronized (clusterSess)
      {
         String realId = clusterSess.getRealId();
         if (realId == null) return;

         if (trace_)
         {
            log_.trace("Removing session from local store with id: " + realId);
         }

         try {
            clusterSess.removeMyselfLocal();
         }
         finally
         {
            // We don't want to replicate this session at the end
            // of the request; the removal process took care of that
            SessionReplicationContext.sessionExpired(clusterSess, realId, snapshotManager_);
            
            // Track this session to prevent reincarnation by this request 
            // from the distributed cache
            SessionInvalidationTracker.sessionInvalidated(realId, this);
            
            sessions_.remove(realId);
            stats_.removeStats(realId);

            // Compute how long this session has been alive, and update
            // our statistics accordingly
            int timeAlive = (int) ((System.currentTimeMillis() - clusterSess.getCreationTimeInternal())/1000);
            sessionExpired(timeAlive);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   public boolean storeSession(Session baseSession)
   {
      boolean stored = false;
      if(baseSession != null && started_)
      {
         ClusteredSession<? extends OutgoingDistributableSessionData> session = uncheckedCastSession(baseSession);

         synchronized (session)
         {
            if (trace_)
            {
               log_.trace("check to see if needs to store and replicate " +
                          "session with id " + session.getIdInternal());
            }

            if (session.isValid() &&
                  (session.isSessionDirty() || session.getMustReplicateTimestamp()))
            {
               String realId = session.getRealId();

               // Notify all session attributes that they get serialized (SRV 7.7.2)
               long begin = System.currentTimeMillis();
               session.notifyWillPassivate(ClusteredSessionNotificationCause.REPLICATION);
               long elapsed = System.currentTimeMillis() - begin;
               stats_.updatePassivationStats(realId, elapsed);

               // Do the actual replication
               begin = System.currentTimeMillis();
               processSessionRepl(session);
               elapsed = System.currentTimeMillis() - begin;
               stored = true;
               stats_.updateReplicationStats(realId, elapsed);
            }
            else if (trace_)
            {
               log_.trace("Session " + session.getIdInternal() + 
                          " did not require replication.");
            }
         }
      }

      return stored;
   }

   // ----------------------------------------------------------------- Manager

   /**
    * {@inheritDoc}
    */
   public void add(Session session)
   {
      if (session == null)
         return;

      if (!(session instanceof ClusteredSession))
      {
         throw new IllegalArgumentException("You can only add instances of " +
               "type ClusteredSession to this Manager. Session class name: " +
               session.getClass().getName());
      }

      try
      {
         // [JBAS-7123] Make sure we're either in the call stack where LockingValve has
         // a lock, or that we acquire one ourselves
         boolean inLockingValve = SessionReplicationContext.isLocallyActive();
         if (inLockingValve || this.valveLock.tryLock(0, TimeUnit.SECONDS))
         {
            try
            {
               add(uncheckedCastSession(session), false); // wait to replicate until req end
            }
            finally
            {
               if (!inLockingValve)
               {
                  this.valveLock.unlock();
               }
            }
         }
         else if (trace_)
         {
            log_.trace("add(): ignoring add -- Manager is not actively handling requests");
         }
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
   }

   // Satisfy the Manager interface.  Internally we use
   // createEmptyClusteredSession to avoid a cast
   public Session createEmptySession()
   {
      Session session = null;
      try
      {
         // [JBAS-7123] Make sure we're either in the call stack where LockingValve has
         // a lock, or that we acquire one ourselves
         boolean inLockingValve = SessionReplicationContext.isLocallyActive();
         if (inLockingValve || this.valveLock.tryLock(0, TimeUnit.SECONDS))
         {
            try
            {
               if (trace_)
               {
                  log_.trace("Creating an empty ClusteredSession");
               }
               session = createEmptyClusteredSession();
            }
            finally
            {
               if (!inLockingValve)
               {
                  this.valveLock.unlock();
               }
            }
         }
         else if (trace_)
         {
            log_.trace("createEmptySession(): Manager is not handling requests; returning null");
         }
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
      
      return session;
   }
   
   /**
    * {@inheritDoc}
    */
   public Session createSession()
   {
      return createSession(null);
   }

   /**
    * {@inheritDoc}
    */
   public Session createSession(String sessionId)
   {  
      Session session = null;
      try
      {
         // [JBAS-7123] Make sure we're either in the call stack where LockingValve has
         // a lock, or that we acquire one ourselves
         boolean inLockingValve = SessionReplicationContext.isLocallyActive();
         if (inLockingValve || this.valveLock.tryLock(0, TimeUnit.SECONDS))
         {
            try
            {
               session = createSessionInternal(sessionId);
            }
            finally
            {
               if (!inLockingValve)
               {
                  this.valveLock.unlock();
               }
            }
         }
         else if (trace_)
         {
            log_.trace("createEmptySession(): Manager is not handling requests; returning null");
         }
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
      
      return session;
   }

   private Session createSessionInternal(String sessionId)
   {
      // First check if we've reached the max allowed sessions, 
      // then try to expire/passivate sessions to free memory
      // maxActiveAllowed_ -1 is unlimited
      // We check here for maxActive instead of in add().  add() gets called
      // when we load an already existing session from the distributed cache
      // (e.g. in a failover) and we don't want to fail in that situation.

      if(maxActiveAllowed_ != -1 && calcActiveSessions() >= maxActiveAllowed_)
      {
         if (trace_)
         {
            log_.trace("createSession(): active sessions = " + calcActiveSessions() +
                       " and max allowed sessions = " + maxActiveAllowed_);
         }
         
         processExpirationPassivation();
         
         if (calcActiveSessions() >= maxActiveAllowed_)
         {
            // Exceeds limit. We need to reject it.
            rejectedCounter_.incrementAndGet();
            // Catalina api does not specify what happens
            // but we will throw a runtime exception for now.
            String msgEnd = (sessionId == null) ? "" : " id " + sessionId;
            throw new IllegalStateException("createSession(): number of " +
                   "active sessions exceeds the maximum limit: " +
                   maxActiveAllowed_ + " when trying to create session" + msgEnd);
         }
      }

      ClusteredSession<? extends OutgoingDistributableSessionData> session = createEmptyClusteredSession();

      if (session != null)
      {
         session.setNew(true);
         session.setCreationTime(System.currentTimeMillis());
         session.setMaxInactiveInterval(this.maxInactiveInterval_);
         session.setValid(true);
   
         String clearInvalidated = null; // see below
         
         if (sessionId == null)
         {
             sessionId = this.getNextId();
   
             // We are using mod_jk for load balancing. Append the JvmRoute.
             if (getUseJK())
             {
                 if (trace_)
                 {
                     log_.trace("createSession(): useJK is true. Will append JvmRoute: " + this.getJvmRoute());
                 }
                 sessionId += "." + this.getJvmRoute();
             }
         }
         else
         {
            clearInvalidated = sessionId;
         }
   
         session.setId(sessionId); // Setting the id leads to a call to add()
         
         getDistributedCacheManager().sessionCreated(session.getRealId());
         
         session.tellNew(ClusteredSessionNotificationCause.CREATE);
   
         if (trace_)
         {
            log_.trace("Created a ClusteredSession with id: " + sessionId);
         }
   
         createdCounter_.incrementAndGet(); // the call to add() handles the other counters 
         
         // Add this session to the set of those potentially needing replication
         SessionReplicationContext.bindSession(session, snapshotManager_);
         
         if (clearInvalidated != null)
         {
            // We no longer need to track any earlier session w/ same id 
            // invalidated by this thread
            SessionInvalidationTracker.clearInvalidatedSession(clearInvalidated, this);
         }
      }
      
      return session;
   }

   /**
    * Attempts to find the session in the collection of those being managed
    * locally, and if not found there, in the distributed cache of sessions.
    * <p>
    * If a session is found in the distributed cache, it is added to the
    * collection of those being managed locally.
    * </p>
    *
    * @param id the session id, which may include an appended jvmRoute
    *
    * @return the session, or <code>null</code> if no such session could
    *         be found
    */
   public Session findSession(String id)
   {
      String realId = getRealId(id);
      // Find it from the local store first
      ClusteredSession<? extends OutgoingDistributableSessionData> session = findLocalSession(realId);
      
      // If we didn't find it locally, only check the distributed cache
      // if we haven't previously handled this session id on this request.
      // If we handled it previously but it's no longer local, that means
      // it's been invalidated. If we request an invalidated session from
      // the distributed cache, it will be missing from the local cache but
      // may still exist on other nodes (i.e. if the invalidation hasn't 
      // replicated yet because we are running in a tx). With buddy replication,
      // asking the local cache for the session will cause the out-of-date
      // session from the other nodes to be gravitated, thus resuscitating
      // the session.
      if (session == null 
            && !SessionInvalidationTracker.isSessionInvalidated(realId, this))
      {
         if (trace_)
            log_.trace("Checking for session " + realId + " in the distributed cache");
         
         session = loadSession(realId);
//       if (session != null)
//       {
//          add(session);
//          // We now notify, since we've added a policy to allow listeners 
//          // to discriminate. But the default policy will not allow the 
//          // notification to be emitted for FAILOVER, so the standard
//          // behavior is unchanged.
//          session.tellNew(ClusteredSessionNotificationCause.FAILOVER);
//       }
      }
      else if (session != null && this.outdatedSessionChecker.isSessionOutdated(session))
      {
         if (trace_)
            log_.trace("Updating session " + realId + " from the distributed cache");
         
         // Need to update it from the cache
         session = loadSession(realId);
         if (session == null)
         {
            // We have a session locally but it's no longer available
            // from the distributed store; i.e. it's been invalidated elsewhere
            // So we need to clean up
            // TODO what about notifications?
            this.sessions_.remove(realId);
         }
      }

      if (session != null)
      {
         // Add this session to the set of those potentially needing replication
         SessionReplicationContext.bindSession(session, snapshotManager_);
         
         // If we previously called passivate() on the session due to
         // replication, we need to make an offsetting activate() call
         if (session.getNeedsPostReplicateActivation())
         {
            session.notifyDidActivate(ClusteredSessionNotificationCause.REPLICATION);
         }
      }

      return session;
   }

   /**
    * Return the sessions. Note that this will return not only the local
    * in-memory sessions, but also any sessions that are in the distributed
    * cache but have not previously been accessed on this server.  Invoking
    * this method will bring all such sessions into local memory and can
    * potentially be quite expensive.
    *
    * <p>
    * Note also that when sessions are loaded from the distributed cache, no
    * check is made as to whether the number of local sessions will thereafter
    * exceed the maximum number allowed on this server.
    * </p>
    *
    * @return an array of all the sessions
    */
   public Session[] findSessions()
   {
      Session[] sessions = null;
      try
      {
         // [JBAS-7123] Make sure we're either in the call stack where LockingValve has
         // a lock, or that we acquire one ourselves
         boolean inLockingValve = SessionReplicationContext.isLocallyActive();
         if (inLockingValve || this.valveLock.tryLock(0, TimeUnit.SECONDS))
         {
            try
            {
               // Need to load all the unloaded sessions
               if(unloadedSessions_.size() > 0)
               {
                  // Make a thread-safe copy of the new id list to work with
                  Set<String> ids = new HashSet<String>(unloadedSessions_.keySet());
  
                  if(trace_) {
                     log_.trace("findSessions: loading sessions from distributed cache: " + ids);
                  }
  
                  for(String id :  ids) {
                     loadSession(id);
                  }
               }
  
               // All sessions are now "local" so just return the local sessions
               sessions = findLocalSessions();
            }
            finally
            {
               if (!inLockingValve)
               {
                  this.valveLock.unlock();
               }
            }
         }
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
      
      return sessions;
   }

   /**
    * {@inheritDoc}
    */
   public String getInfo()
   {
      return info_;
   }

   /**
    * Removes the session from this Manager's collection of actively managed
    * sessions.  Also removes the session from the distributed cache, both
    * on this server and on all other server to which this one replicates.
    */
   public void remove(Session session)
   {
      ClusteredSession<? extends OutgoingDistributableSessionData> clusterSess = uncheckedCastSession(session);
      synchronized (clusterSess)
      {
         String realId = clusterSess.getRealId();
         if (realId == null)
            return;

         if (trace_)
         {
            log_.trace("Removing session from store with id: " + realId);
         }

         try {
            clusterSess.removeMyself();
         }
         finally {
            // We don't want to replicate this session at the end
            // of the request; the removal process took care of that
            SessionReplicationContext.sessionExpired(clusterSess, realId, snapshotManager_);
            
            // Track this session to prevent reincarnation by this request 
            // from the distributed cache
            SessionInvalidationTracker.sessionInvalidated(realId, this);
            
            sessions_.remove(realId);
            stats_.removeStats(realId);

            // Compute how long this session has been alive, and update
            // our statistics accordingly
            int timeAlive = (int) ((System.currentTimeMillis() - clusterSess.getCreationTimeInternal())/1000);
            sessionExpired(timeAlive);
         }
      }
   }

   // -------------------------------------------------------  ClusteredManager

   /**
    * Gets the <code>DistributedCacheManager</code> through which we interact
    * with the distributed store.
    */
   public DistributedCacheManager<O> getDistributedCacheManager()
   {
      return proxy_;
   }

   /**
    * {@inheritDoc}
    */
   public int getMaxUnreplicatedInterval()
   {
      return maxUnreplicatedInterval_;
   }

   public ClusteredSessionNotificationPolicy getNotificationPolicy()
   {
      return notificationPolicy_;
   }    
   
   /**
    * {@inheritDoc}
    */
   public ReplicationTrigger getReplicationTrigger()
   {
      return this.replicationTrigger_;
   }

   // --------------------------------------------------------------- Lifecycle
   
   /**
    * {@inheritDoc}
    */
   @Override
   public void start() throws LifecycleException
   {
      // Identify ourself more clearly
      log_ = Logger.getLogger(getClass().getName() + "." + 
                              getContainer().getName().replaceAll("/", ""));
      
      if (embedded_)
      {
         startEmbedded();
      }
      else
      {
         startUnembedded();
      }
      
      // Handle re-entrance
      if (!this.semaphore.tryAcquire())
      {
         log_.debug("Opening up LockingValve");
         
         // Make all permits available to locking valve
         this.semaphore.release(TOTAL_PERMITS);
      }
      else
      {
         // Release the one we just acquired
         this.semaphore.release();
      }
      
      log_.debug("Started");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void stop() throws LifecycleException
   {
      if (!started_)
      {
         throw new IllegalStateException("Manager not started");
      }
      
      if (stopping)
      {
         return;
      }
      
      log_.debug("Stopping");
      
      this.stopping = true;
      
      // Disable background work, then block for any ongoing backgroundProcess.
      // Do this before draining the semaphore so we know draining
      // won't impact any on-going background processing
      backgroundProcessAllowed.set(false);
      synchronized (backgroundProcessAllowed)
      {
         if (trace_)
         {
            log_.trace("All background processing terminated");
         }
      }
      
      // Handle re-entrance
      if (this.semaphore.tryAcquire())
      {
         try
         {
            log_.debug("Closing off LockingValve");
            
            // Acquire all remaining permits, shutting off locking valve
            this.semaphore.acquire(TOTAL_PERMITS - 1);
         }
         catch (InterruptedException e)
         {
            this.semaphore.release();
            
            throw new LifecycleException(e);
         }
      }
      
      // Let subclasses clean up
      stopExtensions();
      
      resetStats();
      
      // Notify our interested LifecycleListeners
      lifecycle_.fireLifecycleEvent(BEFORE_STOP_EVENT, this);
      
      clearSessions();
      
      // Don't leak the classloader
      tcl_ = null;
      
      proxy_.stop();
      proxy_ = null;
      
      batchingManager = null;
      
      snapshotManager_.stop();
      
      // Clean up maps
      sessions_.clear();
      unloadedSessions_.clear();
      
      passivatedCount_.set(0);
      
      started_ = false;
      
      // Notify our interested LifecycleListeners
      lifecycle_.fireLifecycleEvent(AFTER_STOP_EVENT, this);
      
      unregisterManagerMBean();
   }
   

   // -------------------------------------------------- JBossCacheManagerMBean

   /**
    * {@inheritDoc}
    */
   public void expireSession(String sessionId)
   {
      Session session = findSession(sessionId);
      if (session != null)
         session.expire();
   }

   /**
    * {@inheritDoc}
    */
   public String getCacheConfigName()
   {
      return cacheConfigName_;
   }

   /**
    * {@inheritDoc}
    */
   public String getCreationTime(String sessionId)
   {
      Session session = findSession(sessionId);
      if(session == null) 
      {
         log_.info("getCreationTime(): Session " + sessionId + 
                       " not found");
         return "";
      }
     return new Date(session.getCreationTime()).toString();
   }
   
   /**
    * {@inheritDoc}
    */
   public int getDuplicates()
   {
      return duplicates_.get();
   }

   /**
    * {@inheritDoc}
    */
   public String getLastAccessedTime(String sessionId)
   {
      Session session = findSession(sessionId);
      if(session == null) {
         log_.info("getLastAccessedTime(): Session " + sessionId + 
                   " not found");
         return "";
      }
     return new Date(session.getLastAccessedTime()).toString();
   }

   /**
    * {@inheritDoc}
    */
   public String getSessionAttribute(String sessionId, String key)
   {
      Object attr = null;
      ClusteredSession<? extends OutgoingDistributableSessionData> session = uncheckedCastSession(findSession(sessionId));
      if (session != null)
      {
         attr = session.getAttribute(key);
      }
      
      return attr == null ? null : attr.toString();
   }

   /**
    * {@inheritDoc}
    */
   public long getMaxPassivatedSessionCount()
   {
      return maxPassivatedCount_.get();
   }

   /**
    * {@inheritDoc}
    */
   public void setMaxUnreplicatedInterval(int maxUnreplicatedInterval)
   {
      this.maxUnreplicatedInterval_ = maxUnreplicatedInterval;
   }

   /**
    * {@inheritDoc}
    */
   public long getPassivatedSessionCount()
   {
      return passivatedCount_.get();
   }

   /**
    * {@inheritDoc}
    */
   public ReplicationGranularity getReplicationGranularity()
   {
      return replicationGranularity_;
   }

   /**
    * {@inheritDoc}
    */
   public int getSnapshotInterval()
   {
      return snapshotInterval_;
   }

   /**
    * {@inheritDoc}
    */
   public SnapshotMode getSnapshotMode()
   {
      return snapshotMode_;
   }

   /**
    * {@inheritDoc}
    */
   public boolean getUseJK()
   {
      return useJK_ == null ? false : useJK_.booleanValue();
   }
   
   /**
    * {@inheritDoc}
    */
   public boolean isPassivationEnabled()
   {
      return (passivationMode_ && proxy_.isPassivationEnabled());
   }

   /**
    * {@inheritDoc}
    */
   public Boolean isReplicationFieldBatchMode()
   {
      return replicationFieldBatchMode_;
   }
   
   /**
    * {@inheritDoc}
    */
   public String listLocalSessionIds()
   {
      return reportSessionIds(sessions_.keySet());
   }
   
   /**
    * {@inheritDoc}
    */
   public String listSessionIds()
   {
      Set<String> ids = new HashSet<String>(sessions_.keySet());
      ids.addAll(unloadedSessions_.keySet());
      return reportSessionIds(ids);
   }
   
   // --------------------------------------- LocalDistributableSessionManager

   public String getContextName()
   {
      return getContainer().getName();
   }
   
   public String getHostName()
   {
      return getContainer().getParent().getName();
   }
   
   public ClassLoader getApplicationClassLoader()
   {
      return tcl_;
   }
   
   public ReplicationConfig getReplicationConfig()
   {
      return replicationConfig_;
   }
   
   /**
    * Notifies the manager that a session in the distributed cache has
    * been invalidated
    * 
    * FIXME This method is poorly named, as we will also get this callback
    * when we use buddy replication and our copy of the session in JBC is being
    * removed due to data gravitation.
    * 
    * @param realId the session id excluding any jvmRoute
    */
   public void notifyRemoteInvalidation(String realId)
   {
      // Remove the session from our local map
      ClusteredSession<? extends OutgoingDistributableSessionData> session = sessions_.remove(realId);
      if (session == null)
      {
         // We weren't managing the session anyway.  But remove it
         // from the list of cached sessions we haven't loaded
         if (unloadedSessions_.remove(realId) != null)
         {
            if (trace_)
               log_.trace("Removed entry for session " + realId + " from unloaded session map");
         }
         
         // If session has failed over and has been passivated here,
         // session will be null, but we'll have a TimeStatistic to clean up
         stats_.removeStats(realId);
      }
      else
      {
         // Expire the session
         // DON'T SYNCHRONIZE ON SESSION HERE -- isValid() and
         // expire() are meant to be multi-threaded and synchronize
         // properly internally; synchronizing externally can lead
         // to deadlocks!!
         boolean notify = false; // Don't notify listeners. SRV.10.7
                                 // allows this, and sending notifications
                                 // leads to all sorts of issues; e.g.
                                 // circular calls with ClusteredSSO and
                                 // notifying when all that's happening is
                                 // data gravitation due to random failover
         boolean localCall = false; // this call originated from the cache;
                                    // we have already removed session
         boolean localOnly = true; // Don't pass attr removals to cache
         
         // Ensure the correct TCL is in place
         // BES 2008/11/27 Why?
         ContextClassLoaderSwitcher.SwitchContext switcher = null;         
         try
         {
            // Don't track this invalidation is if it were from a request
            SessionInvalidationTracker.suspend();
            
            switcher = getContextClassLoaderSwitcher().getSwitchContext();
            switcher.setClassLoader(tcl_);
            session.expire(notify, localCall, localOnly, ClusteredSessionNotificationCause.INVALIDATE);
         }
         finally
         {
            SessionInvalidationTracker.resume();
            
            // Remove any stats for this session
            stats_.removeStats(realId);
            
            if (switcher != null)
            {
               switcher.reset();
            }
         }
      }
   }
   
   /**
    * Callback from the distributed cache notifying of a local modification
    * to a session's attributes.  Meant for use with FIELD granularity,
    * where the session may not be aware of modifications.
    * 
    * @param realId the session id excluding any jvmRoute
    */
   public void notifyLocalAttributeModification(String realId)
   {
      ClusteredSession<? extends OutgoingDistributableSessionData> session = sessions_.get(realId);
      if (session != null)
      {
         session.sessionAttributesDirty();
      }
      else
      {
         log_.warn("Received local attribute notification for " + realId + 
               " but session is not locally active");
      }
   }
   
   public void sessionActivated()
   {
      int pc = passivatedCount_.decrementAndGet();
      // Correct for drift since we don't know the true passivation
      // count when we started.  We can get activations of sessions
      // we didn't know were passivated.
      // FIXME -- is the above statement still correct? Is this needed?
      if (pc < 0) 
      {
         // Just reverse our decrement.
         passivatedCount_.incrementAndGet();
      }
   }
   
   /**
    * Callback from the distributed cache to notify us that a session
    * has been modified remotely.
    * 
    * @param realId the session id, without any trailing jvmRoute
    * @param dataOwner  the owner of the session.  Can be <code>null</code> if
    *                   the owner is unknown.
    * @param distributedVersion the session's version per the distributed cache
    * @param timestamp the session's timestamp per the distributed cache
    * @param metadata the session's metadata per the distributed cache
    */
   public boolean sessionChangedInDistributedCache(String realId, 
                                         String dataOwner,
                                         int distributedVersion,
                                         long timestamp, 
                                         DistributableSessionMetadata metadata)
   {
      boolean updated = true;
      
      ClusteredSession<? extends OutgoingDistributableSessionData> session = findLocalSession(realId);
      if (session != null)
      {
         // Need to invalidate the loaded session. We get back whether
         // this an actual version increment
         updated = session.setVersionFromDistributedCache(distributedVersion);
         if (updated && trace_)      
         {            
            log_.trace("session in-memory data is invalidated for id: " + realId + 
                       " new version: " + distributedVersion);
         }         
      }
      else
      {
         int maxLife = metadata == null ? getMaxInactiveInterval() : metadata.getMaxInactiveInterval();
         
         Object existing = unloadedSessions_.put(realId, new OwnedSessionUpdate(dataOwner, timestamp, maxLife, false));
         if (existing == null)
         {
            calcActiveSessions();
            if (trace_)
            {
               log_.trace("New session " + realId + " added to unloaded session map");
            }
         }
         else if (trace_)
         {
            log_.trace("Updated timestamp for unloaded session " + realId);
         }
      }
      
      return updated;
   }
   
   // ----------------------------------------------- JBossCacheCluster Support

   /**
    * Sets how often session changes should be replicated to other nodes.
    *
    * @param snapshotInterval the number of milliseconds between
    *                         session replications.
    */
   public void setSnapshotInterval(int snapshotInterval)
   {
      this.snapshotInterval_ = snapshotInterval;
   }

   /**
    * Sets when sessions are replicated to the other nodes. Valid values are:
    * <ul>
    * <li>instant</li>
    * <li>interval</li>
    * </ul>
    */
   public void setSnapshotMode(SnapshotMode snapshotMode)
   {
      this.snapshotMode_ = snapshotMode;
   }
   
   public void setSnapshotMode(String snapshotMode)
   {
      snapshotMode = (snapshotMode == null ? null : snapshotMode.toUpperCase());
      setSnapshotMode(SnapshotMode.fromString(snapshotMode));
   }

   /**
    * Sets whether the <code>Engine</code> in which we are running
    * uses <code>mod_jk</code>.
    */
   public void setUseJK(boolean useJK)
   {
      this.useJK_ = Boolean.valueOf(useJK);
   }

   /**
    * Sets the granularity of session data replicated across the cluster.
    * Valid values are:
    * <ul>
    * <li>SESSION</li>
    * <li>ATTRIBUTE</li>
    * <li>FIELD</li>
    * </ul>
    */
   public void setReplicationGranularity(ReplicationGranularity granularity)
   {
      this.replicationGranularity_ = granularity;
   }

   /**
    * Returns the replication granularity.
    *
    *  @see JBossWebMetaData#REPLICATION_GRANULARITY_ATTRIBUTE
    *  @see JBossWebMetaData#REPLICATION_GRANULARITY_FIELD
    *  @see JBossWebMetaData#REPLICATION_GRANULARITY_SESSION
    */
   public String getReplicationGranularityString()
   {
      return replicationGranularity_ == null ? null : replicationGranularity_.toString();
   }

   /**
    * Sets the granularity of session data replicated across the cluster.
    * Valid values are:
    * <ul>
    * <li>SESSION</li>
    * <li>ATTRIBUTE</li>
    * <li>FIELD</li>
    * </ul>
    */
   public void setReplicationGranularityString(String granularity)
   {
      setReplicationGranularity(granularity == null ? null : 
                  ReplicationGranularity.fromString(granularity.toUpperCase()));
   } 
   
   /**
    * Sets the type of operations on a <code>HttpSession</code> that
    * trigger replication.  Valid values are:
    * <ul>
    * <li>SET_AND_GET</li>
    * <li>SET_AND_NON_PRIMITIVE_GET</li>
    * <li>SET</li>
    * </ul>
    */
   public void setReplicationTrigger(ReplicationTrigger trigger)
   {
      this.replicationTrigger_ = trigger;
   }

   public String getReplicationTriggerString()
   {      
      return replicationTrigger_ == null ? null : replicationTrigger_.toString();
   }

   public void setReplicationTriggerString(String trigger)
   {      
      setReplicationTrigger(trigger == null ? null : ReplicationTrigger.fromString(trigger.toUpperCase()));
   }

   /**
    * Sets whether, if replication granularity is set to <code>FIELD</code>,
    * replication should be done in batch mode.  Ignored if field-level
    * granularity is not used.
    */
   public void setReplicationFieldBatchMode(boolean replicationFieldBatchMode)
   {
      this.replicationFieldBatchMode_ = Boolean.valueOf(replicationFieldBatchMode);
   }

   public String getSessionNotificationPolicyClass()
   {
      return notificationPolicyClass_;
   }

   public void setSessionNotificationPolicyClass(String notificationPolicyClass)
   {
      this.notificationPolicyClass_ = notificationPolicyClass;
   }  
   

   // --------------------------------------------------------------- Protected


   /**
    * Accesses the underlying cache and creates the proxy
    * 
    * @throws ClusteringNotSupportedException
    */
   protected void initDistributedCacheManager() throws ClusteringNotSupportedException
   {
      proxy_ = distributedCacheManagerFactory.getDistributedCacheManager(this);
   }

   /**
    * Create and start a snapshot manager.
    */
   protected void initSnapshotManager()
   {
      String ctxPath = ((Context) container_).getPath();
      if (SnapshotMode.INSTANT == snapshotMode_)
      {
         snapshotManager_ = new InstantSnapshotManager(this, ctxPath);
      }
      else if (snapshotMode_ == null)
      {
         log_.warn("Snapshot mode must be 'instant' or 'interval' - " +
                   "using 'instant'");
         snapshotMode_ = SnapshotMode.INSTANT;
         snapshotManager_ = new InstantSnapshotManager(this, ctxPath);
      }
      else if (ReplicationGranularity.FIELD == replicationGranularity_)
      {
         throw new IllegalStateException("Property snapshotMode must be " + 
               SnapshotMode.INTERVAL + " when FIELD granularity is used");
      }
      else if (snapshotInterval_ < 1)
      {
         log_.warn("Snapshot mode set to 'interval' but snapshotInterval is < 1 " +
                   "using 'instant'");
         snapshotMode_ = SnapshotMode.INSTANT;
         snapshotManager_ = new InstantSnapshotManager(this, ctxPath);         
      }
      else
      {
         snapshotManager_ = new IntervalSnapshotManager(this, ctxPath, snapshotInterval_);
      }
      
      snapshotManager_.start();
   }
   
   protected SnapshotManager getSnapshotManager()
   {
      return snapshotManager_;
   }
   
   protected void setSnapshotManager(SnapshotManager manager)
   {
      this.snapshotManager_ = manager;
   }

   /**
    * Instantiate a SnapshotManager and ClusteredSessionValve and add 
    * the valve to our parent Context's pipeline.  
    * Add a JvmRouteValve and BatchReplicationClusteredSessionValve if needed.
    */
   protected void installValves()
   {
      log_.debug("Adding LockingValve");
      this.installValve(new LockingValve(this.valveLock));
      
      // If JK usage wasn't explicitly configured, default to enabling
      // it if jvmRoute is set on our containing Engine
      if (useJK_ == null)
      {
         useJK_ = Boolean.valueOf(getJvmRoute() != null);
      }
      
      if (getUseJK())
      {
         log_.debug("We are using JK for load-balancing. Adding JvmRouteValve.");         
         this.installValve(new JvmRouteValve(this));
      }
      
      // Handle batch replication if needed.
      // TODO -- should we add this even if not FIELD in case a cross-context
      // call traverses a field-based webapp?   
      BatchingManager valveBM = null;
      if (replicationGranularity_ == ReplicationGranularity.FIELD
            && Boolean.TRUE.equals(replicationFieldBatchMode_))
      {
         valveBM = this.batchingManager;
         log_.debug("Including transaction manager in ClusteredSessionValve to support batch replication.");
      }

      // Add clustered session valve
      ClusteredSessionValve valve = new ClusteredSessionValve(this, valveBM);
      log_.debug("Adding ClusteredSessionValve");
      this.installValve(valve);
   }

   protected void initClusteredSessionNotificationPolicy()
   {
      if (this.notificationPolicyClass_ == null || this.notificationPolicyClass_.length() == 0)
      {
         this.notificationPolicyClass_ = AccessController.doPrivileged(new PrivilegedAction<String>()
         {
            public String run()
            {
               return System.getProperty("jboss.web.clustered.session.notification.policy",
                     IgnoreUndeployLegacyClusteredSessionNotificationPolicy.class.getName());
            }
         });
      }
      
      try
      {
         this.notificationPolicy_ = (ClusteredSessionNotificationPolicy) Thread.currentThread().getContextClassLoader().loadClass(this.notificationPolicyClass_).newInstance();
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to instantiate " + 
               ClusteredSessionNotificationPolicy.class.getName() + 
               " " + this.notificationPolicyClass_, e);
      }
      
      this.notificationPolicy_.setClusteredSessionNotificationCapability(new ClusteredSessionNotificationCapability());      
   }
   
   protected OutdatedSessionChecker initOutdatedSessionChecker()
   {
      return new AskSessionOutdatedSessionChecker();      
   }
   
   /**
    * Gets the ids of all sessions in the distributed cache and adds
    * them to the unloaded sessions map, along with their lastAccessedTime
    * and their maxInactiveInterval. Passivates overage or excess sessions.
    */
   protected void initializeUnloadedSessions()
   {      
      Map<String, String> sessions = proxy_.getSessionIds();
      if (sessions != null)
      {
         boolean passivate = isPassivationEnabled();

         long passivationMax = passivationMaxIdleTime_ * 1000L;
         long passivationMin = passivationMinIdleTime_ * 1000L;
         
         for (Map.Entry<String, String> entry : sessions.entrySet())
         {
            String realId = entry.getKey();
            String owner = entry.getValue();

            long ts = -1;
            DistributableSessionMetadata md = null;
            try
            {
               IncomingDistributableSessionData sessionData = proxy_.getSessionData(realId, owner, false);
               ts = sessionData.getTimestamp();
               md = sessionData.getMetadata();
            }
            catch (Exception e)
            {
               // most likely a lock conflict if the session is being updated remotely; 
               // ignore it and use default values for timstamp and maxInactive
               log_.debug("Problem reading metadata for session " + realId + " -- " + e.toString());               
            }
            
            long lastMod = ts == -1 ? System.currentTimeMillis() : ts;
            int maxLife = md == null ? getMaxInactiveInterval() : md.getMaxInactiveInterval();
            
            OwnedSessionUpdate osu = new OwnedSessionUpdate(owner, lastMod, maxLife, false);
            unloadedSessions_.put(realId, osu);
         }
         
         if (passivate)
         {
            for (Map.Entry<String, OwnedSessionUpdate> entry : unloadedSessions_.entrySet())
            {
               String realId = entry.getKey();
               OwnedSessionUpdate osu = entry.getValue();
               try
               {
                  long elapsed = System.currentTimeMillis() - osu.getUpdateTime();
                  // if maxIdle time configured, means that we need to passivate sessions that have
                  // exceeded the max allowed idle time
                  if (passivationMax >= 0 
                        && elapsed > passivationMax)
                  {
                     if (trace_)
                     {
                        log_.trace("Elapsed time of " + elapsed + " for session "+ 
                              realId + " exceeds max of " + passivationMax + "; passivating");
                     }
                     processUnloadedSessionPassivation(realId, osu);
                  }
                  // If the session didn't exceed the passivationMaxIdleTime_, see   
                  // if the number of sessions managed by this manager greater than the max allowed 
                  // active sessions, passivate the session if it exceed passivationMinIdleTime_ 
                  else if (maxActiveAllowed_ > 0 
                              && passivationMin >= 0 
                              && calcActiveSessions() > maxActiveAllowed_ 
                              && elapsed >= passivationMin)
                  {
                     if (trace_)
                     {
                        log_.trace("Elapsed time of " + elapsed + " for session "+ 
                              realId + " exceeds min of " + passivationMin + "; passivating");
                     }
                     processUnloadedSessionPassivation(realId, osu);
                  }
               }
               catch (Exception e)
               {
                  // most likely a lock conflict if the session is being updated remotely; ignore it
                  log_.debug("Problem passivating session " + realId + " -- " + e.toString());
               }
            }
         }
      }
   }
   
   /**
    * Hook executed during {@link #start()} processing that allows subclasses
    * to add extra processing to the startup. This is invoked at the end of 
    * the startup process, just before external request are free to access
    * this manager.  This default implementation does nothing. 
    */
   protected void startExtensions()
   {
      // no-op
   }
   
   /**
    * Hook executed during {@link #stop()} processing that allows subclasses
    * to add extra processing to the shutdown. This is invoked at the beginning
    * of the shutdown process, just after external requests to this manager are
    * cut off.  This default implementation does nothing. 
    */
   protected void stopExtensions()
   {
      // no-op
   }
   
   /**
    * {@inheritDoc}
    * <p>
    * Overrides the superclass version to ensure that the generated id
    * does not duplicate the id of any other session this manager is aware of.
    * </p>
    */
   @Override
   protected String getNextId()
   {
      while (true)
      {
         String id = super.getNextId();
         if (sessions_.containsKey(id) || unloadedSessions_.containsKey(id))
         {
            duplicates_.incrementAndGet();
         }
         else
         {
            return id;
         }
      }
   }

   protected int getTotalActiveSessions()
   {
      return localActiveCounter_.get() + unloadedSessions_.size() - passivatedCount_.get();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void processExpirationPassivation()
   {      
      boolean expire = maxInactiveInterval_ >= 0;
      boolean passivate = isPassivationEnabled();
      
      long passivationMax = passivationMaxIdleTime_ * 1000L;
      long passivationMin = passivationMinIdleTime_ * 1000L;

      if (trace_)
      { 
         log_.trace("processExpirationPassivation(): Looking for sessions that have expired ...");
         log_.trace("processExpirationPassivation(): active sessions = " + calcActiveSessions());
         log_.trace("processExpirationPassivation(): expired sessions = " + expiredCounter_);
         if (passivate)
         {
            log_.trace("processExpirationPassivation(): passivated count = " + getPassivatedSessionCount());
         }
      }
      
      // Holder for sessions or OwnedSessionUpdates that survive expiration,
      // sorted by last acccessed time
      TreeSet<PassivationCheck> passivationChecks = new TreeSet<PassivationCheck>();
      
      try
      {
         // Don't track sessions invalidated via this method as if they
         // were going to be re-requested by the thread
         SessionInvalidationTracker.suspend();
         
         // First, handle the sessions we are actively managing
         ClusteredSession<? extends OutgoingDistributableSessionData> sessions[] = findLocalSessions();
         for (int i = 0; i < sessions.length; ++i)
         {
            if (!backgroundProcessAllowed.get())
            {
               return;
            }
            
            try
            {
               ClusteredSession<? extends OutgoingDistributableSessionData> session = sessions[i];
               if(session == null)
               {
                  log_.warn("processExpirationPassivation(): processing null session at index " +i);
                  continue;
               }

               if (expire)
               {
                  // JBAS-2403. Check for outdated sessions where we think
                  // the local copy has timed out.  If found, refresh the
                  // session from the cache in case that might change the timeout
                  if (this.outdatedSessionChecker.isSessionOutdated(session) && !(session.isValid(false)))
                  {
                     // FIXME in AS 5 every time we get a notification from the distributed
                     // cache of an update, we get the latest timestamp. So
                     // we shouldn't need to do a full session load here. A load
                     // adds a risk of an unintended data gravitation.
                     
                     // JBAS-2792 don't assign the result of loadSession to session
                     // just update the object from the cache or fall through if
                     // the session has been removed from the cache
                     loadSession(session.getRealId());
                  }
   
                  // Do a normal invalidation check that will expire the
                  // session if it has timed out
                  // DON'T SYNCHRONIZE on session here -- isValid() and
                  // expire() are meant to be multi-threaded and synchronize
                  // properly internally; synchronizing externally can lead
                  // to deadlocks!!
                  if (!session.isValid()) continue;
               }
                
               // we now have a valid session; store it so we can check later
               // if we need to passivate it
               if (passivate)
               {
                  passivationChecks.add(new PassivationCheck(session));
               }
               
            }
            catch (Exception ex)
            {
               log_.error("processExpirationPassivation(): failed handling " + 
                          sessions[i].getIdInternal() + " with exception: " + 
                          ex, ex);
            }
         }
         
         if (!backgroundProcessAllowed.get())
         {
            return;
         }

         // Next, handle any unloaded sessions

         
         // We may have not gotten replication of a timestamp for requests 
         // that occurred w/in maxUnreplicatedInterval_ of the previous
         // request. So we add a grace period to avoid flushing a session early
         // and permanently losing part of its node structure in JBoss Cache.
         long maxUnrep = maxUnreplicatedInterval_ < 0 ? 60 : maxUnreplicatedInterval_;
         
         Map<String, OwnedSessionUpdate> unloaded = getUnloadedSessions();
         for (Map.Entry<String, OwnedSessionUpdate> entry : unloaded.entrySet())
         {            
            if (!backgroundProcessAllowed.get())
            {
               return;
            }
            
            String realId = entry.getKey();
            OwnedSessionUpdate osu = entry.getValue();
            
            long now = System.currentTimeMillis();
            long elapsed = (now - osu.getUpdateTime());
            try
            {
               if (expire && osu.getMaxInactive() >= 1 && elapsed >= (osu.getMaxInactive() + maxUnrep) * 1000L)
               {
                  //if (osu.passivated && osu.owner == null)
                  if (osu.isPassivated())
                  {
                     // Passivated session needs to be expired. A call to 
                     // findSession will bring it out of passivation
                     Session session = findSession(realId);
                     if (session != null)
                     {
                        session.isValid(); // will expire
                        continue;
                     }
                  }
                  
                  // If we get here either !osu.passivated, or we don't own
                  // the session or the session couldn't be reactivated (invalidated by user). 
                  // Either way, do a cleanup
                  proxy_.removeSessionLocal(realId, osu.getOwner());
                  unloadedSessions_.remove(realId);
                  stats_.removeStats(realId);
                  
               }
               else if (passivate && !osu.isPassivated())
               {  
                  // we now have a valid session; store it so we can check later
                  // if we need to passivate it
                  passivationChecks.add(new PassivationCheck(realId, osu));
               }
            } 
            catch (Exception ex)
            {
               log_.error("processExpirationPassivation(): failed handling unloaded session " + 
                       realId, ex);
            }
         }
         
         // Now, passivations
         if (passivate)
         {
            // Iterate through sessions, earliest lastAccessedTime to latest
            for (PassivationCheck passivationCheck : passivationChecks)
            {               
               try
               {
                  long timeNow = System.currentTimeMillis();
                  long timeIdle = timeNow - passivationCheck.getLastUpdate();
                  // if maxIdle time configured, means that we need to passivate sessions that have
                  // exceeded the max allowed idle time
                  if (passivationMax >= 0 
                        && timeIdle > passivationMax)
                  {
                     passivationCheck.passivate();
                  }
                  // If the session didn't exceed the passivationMaxIdleTime_, see   
                  // if the number of sessions managed by this manager greater than the max allowed 
                  // active sessions, passivate the session if it exceed passivationMinIdleTime_ 
                  else if (maxActiveAllowed_ > 0 
                              && passivationMin > 0 
                              && calcActiveSessions() >= maxActiveAllowed_ 
                              && timeIdle > passivationMin)
                  {
                     passivationCheck.passivate();
                  }
                  else
                  {
                     // the entries are ordered by lastAccessed, so once
                     // we don't passivate one, we won't passivate any
                     break;
                  }
               }
               catch (Exception e)
               {
                  String unloadMark = passivationCheck.isUnloaded() ? "unloaded " : "";
                  log_.error("processExpirationPassivation(): failed passivating " + unloadMark + "session " + 
                        passivationCheck.getRealId(), e);
               }                  
            }
         }
      }
      catch (Exception ex)
      {
         log_.error("processExpirationPassivation(): failed with exception: " + ex, ex);
      }
      finally
      {
         SessionInvalidationTracker.resume();
      }
      
      if (trace_)
      { 
         log_.trace("processExpirationPassivation(): Completed ...");
         log_.trace("processExpirationPassivation(): active sessions = " + calcActiveSessions());
         log_.trace("processExpirationPassivation(): expired sessions = " + expiredCounter_);
         if (passivate)
         {
            log_.trace("processExpirationPassivation(): passivated count = " + getPassivatedSessionCount());
         }
      }
   }
   
   @Override
   public void resetStats()
   {
      super.resetStats();
      
      this.maxPassivatedCount_.set(this.passivatedCount_.get());
   }
   
   // ------------------------------------------------------ Session Management

   protected Map<String, OwnedSessionUpdate> getUnloadedSessions()
   {
      Map<String, OwnedSessionUpdate> unloaded = new HashMap<String, OwnedSessionUpdate>(unloadedSessions_);
      return unloaded;
   }

   private ClusteredSession<? extends OutgoingDistributableSessionData> createEmptyClusteredSession()
   {     
      ClusteredSession<? extends OutgoingDistributableSessionData> session = null;   
      try
      {
         // [JBAS-7123] Make sure we're either in the call stack where LockingValve has
         // a lock, or that we acquire one ourselves
         boolean inLockingValve = SessionReplicationContext.isLocallyActive();
         if (inLockingValve || this.valveLock.tryLock(0, TimeUnit.SECONDS))
         {
            try
            {
               switch (replicationGranularity_)
               {
                  case ATTRIBUTE :
                     ClusteredManager<OutgoingAttributeGranularitySessionData> amgr = uncheckedCastManager(this);
                     session = new AttributeBasedClusteredSession(amgr);
                     break;
                  case FIELD :
                     ClusteredManager<OutgoingDistributableSessionData> fmgr = uncheckedCastManager(this);
                     session = new FieldBasedClusteredSession(fmgr);
                     break;
                  default :
                     ClusteredManager<OutgoingSessionGranularitySessionData> smgr = uncheckedCastManager(this);
                     session = new SessionBasedClusteredSession(smgr);
                     break;
               }
            }
            finally
            {
               if (!inLockingValve)
               {
                  this.valveLock.unlock();
               }
            }
         }
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
      
      return session;
   }

   /**
    * Adds the given session to the collection of those being managed by this
    * Manager.
    *
    * @param session   the session. Cannot be <code>null</code>.
    * @param replicate whether the session should be replicated
    *
    * @throws NullPointerException if <code>session</code> is <code>null</code>.
    */
   private void add(ClusteredSession<? extends OutgoingDistributableSessionData> session, boolean replicate)
   {
      // TODO -- why are we doing this check? The request checks session 
      // validity and will expire the session; this seems redundant
      if (!session.isValid())
      {
         // Not an error; this can happen if a failover request pulls in an
         // outdated session from the distributed cache (see TODO above)
         log_.debug("Cannot add session with id=" + session.getIdInternal() +
                    " because it is invalid");
         return;
      }

      String realId = session.getRealId();
      Object existing = sessions_.put(realId, session);
      unloadedSessions_.remove(realId);

      if (!session.equals(existing))
      {
         if (replicate)
         {
            storeSession(session);
         }

         // Update counters
         calcActiveSessions();
         
         if (trace_)
         {
            log_.trace("Session with id=" + session.getIdInternal() + " added. " +
                       "Current active sessions " + localActiveCounter_.get());
         }
      }
   }
   
   /**
    * Loads a session from the distributed store.  If an existing session with
    * the id is already under local management, that session's internal state
    * will be updated from the distributed store.  Otherwise a new session
    * will be created and added to the collection of those sessions under
    * local management.
    *
    * @param realId  id of the session-id with any jvmRoute removed
    *
    * @return the session or <code>null</code> if the session cannot be found
    *         in the distributed store
    */
   private ClusteredSession<? extends OutgoingDistributableSessionData> loadSession(String realId)
   {
      if (realId == null)
      {
         return null;
      }
      ClusteredSession<? extends OutgoingDistributableSessionData> session = null;
      
      try
      {
         // [JBAS-7123] Make sure we're either in the call stack where LockingValve has
         // a lock, or that we acquire one ourselves
         boolean inLockingValve = SessionReplicationContext.isLocallyActive();
         if (inLockingValve || this.valveLock.tryLock(0, TimeUnit.SECONDS))
         {
            try
            {
               long begin = System.currentTimeMillis();
               boolean mustAdd = false;
               boolean passivated = false;
               
               session = sessions_.get(realId);
               boolean initialLoad = false;
               if (session == null)
               {                 
                  initialLoad = true;
                  // This is either the first time we've seen this session on this
                  // server, or we previously expired it and have since gotten
                  // a replication message from another server
                  mustAdd = true;
                  session = createEmptyClusteredSession();

                  // JBAS-7379 Ensure concurrent threads trying to load same session id
                  // use the same session
                  ClusteredSession<? extends OutgoingDistributableSessionData> embryo = 
                     this.embryonicSessions.putIfAbsent(realId, session);
                  if (embryo != null)
                  {
                     session = embryo;
                  }
                  
                  OwnedSessionUpdate osu = unloadedSessions_.get(realId);
                  passivated = (osu != null && osu.isPassivated());
               }
  
               synchronized (session)
               {
                  // JBAS-7379 check if we lost the race to the sync block
                  // and another thread has already loaded this session
                  if (initialLoad && session.isOutdated() == false)
                  {
                     // some one else loaded this
                     return session;
                  }

                  ContextClassLoaderSwitcher.SwitchContext switcher = null; 
                  boolean doTx = false; 
                  boolean loadCompleted = false;
                  try
                  {
                     // We need transaction so any data gravitation replication 
                     // is sent in batch.
                     // Don't do anything if there is already transaction context
                     // associated with this thread.
                     if (batchingManager.isBatchInProgress() == false)
                     {
                        batchingManager.startBatch();
                        doTx = true;
                     }
                     
                     // Tomcat calls Manager.findSession before setting the tccl,
                     // so we need to do it :(
                     switcher = getContextClassLoaderSwitcher().getSwitchContext();
                     switcher.setClassLoader(tcl_);
                                 
                     IncomingDistributableSessionData data = proxy_.getSessionData(realId, initialLoad);
                     if (data != null)
                     {
                        session.update(data);
                     }
                     else
                     {
                        // Clunky; we set the session variable to null to indicate
                        // no data so move on
                        session = null;
                     }
                     
                     if (session != null)
                     {
                        ClusteredSessionNotificationCause cause = passivated ? ClusteredSessionNotificationCause.ACTIVATION 
                                                                             : ClusteredSessionNotificationCause.FAILOVER;
                        session.notifyDidActivate(cause);
                     }
                     
                     loadCompleted = true;
                  }
                  catch (Exception ex)
                  {
                     try
                     {
  //                  if(doTx)
                        // Let's set it no matter what.
                        batchingManager.setBatchRollbackOnly();
                     }
                     catch (Exception exn)
                     {
                        log_.error("Caught exception rolling back transaction", exn);
                     }
                     // We will need to alert Tomcat of this exception.
                     if (ex instanceof RuntimeException)
                        throw (RuntimeException) ex;
                     
                     throw new RuntimeException("loadSession(): failed to load session " +
                                                realId, ex);
                  }
                  finally
                  {
                     try {
                        if(doTx)
                        {
                           try
                           {
                              batchingManager.endBatch();
                           }
                           catch (Exception e)
                           {
                              if (loadCompleted)
                              {
                                 // We read the data successfully but then failed in commit?
                                 // That indicates a JBC data gravitation where the replication of
                                 // the gravitated data to our buddy failed. We can ignore that
                                 // and count on this request updating the cache.                               // 
                                 log_.warn("Problem ending batch after loading session " + realId + " -- " + e.getLocalizedMessage() + " However session data was successful loaded.");
                                 log_.debug("Failure cause", e);
                              }
                              else
                              {
                                 if (e instanceof RuntimeException)
                                    throw (RuntimeException) e;
                                 
                                 throw new RuntimeException("loadSession(): failed to load session " +
                                                            realId, e);
                              }
                           }
                        }
                     }
                     finally {
                        if (switcher != null)
                        {
                           switcher.reset();
                        }
                     }
                  }
  
                  if (session != null)
                  {            
                     if (mustAdd)
                     {
                        add(session, false); // don't replicate
                        if (!passivated)
                        {
                           session.tellNew(ClusteredSessionNotificationCause.FAILOVER);
                        }
                     }
                     long elapsed = System.currentTimeMillis() - begin;
                     stats_.updateLoadStats(realId, elapsed);
  
                     if (trace_)
                     {
                        log_.trace("loadSession(): id= " + realId + ", session=" + session);
                     }
                  }
                  else if (trace_)
                  {
                     log_.trace("loadSession(): session " + realId +
                                " not found in distributed cache");
                  }
                  
                  if (initialLoad)
                  {                     
                     // The session is now in the regular map, or the session
                     // doesn't exist in the distributed cache. either way
                     // it's now safe to stop tracking this embryonic session
                     embryonicSessions.remove(realId);
                  }
               }
            }
            finally
            {
               if (!inLockingValve)
               {
                  this.valveLock.unlock();
               }
            }
         }
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
      }
      
      return session;
   }

   /**
    * Places the current session contents in the distributed cache and
    * replicates them to the cluster
    *
    * @param session  the session.  Cannot be <code>null</code>.
    */
   private void processSessionRepl(ClusteredSession<? extends OutgoingDistributableSessionData> session)
   {
      // If we are using SESSION granularity, we don't want to initiate a TX
      // for a single put
      boolean notSession = (replicationGranularity_ != ReplicationGranularity.SESSION);
      boolean doTx = false;
      try
      {
         // We need transaction so all the replication are sent in batch.
         // Don't do anything if there is already transaction context
         // associated with this thread.
         if(notSession && batchingManager.isBatchInProgress() == false)
         {
            batchingManager.startBatch();
            doTx = true;
         }

         session.processSessionReplication();
      }
      catch (Exception ex)
      {
         log_.debug("processSessionRepl(): failed with exception", ex);
         
         try
         {
            //if(doTx)
            // Let's setRollbackOnly no matter what.
            // (except if there's no tx due to SESSION (JBAS-3840))
            if (notSession)
               batchingManager.setBatchRollbackOnly();
         }
         catch (Exception exn)
         {
            log_.error("Caught exception rolling back transaction", exn);
         }
         
         // We will need to alert Tomcat of this exception.
         if (ex instanceof RuntimeException)
            throw (RuntimeException) ex;
         
         throw new RuntimeException("JBossCacheManager.processSessionRepl(): " +
                                    "failed to replicate session.", ex);
      }
      finally
      {
         if(doTx)
         {
            batchingManager.endBatch();
         }
      }
   }
   
   /**
    * Session passivation logic for an actively managed session.
    * 
    * @param realId the session id, minus any jvmRoute
    */
   private void processSessionPassivation(String realId)
   {
      // get the session from the local map
      ClusteredSession<? extends OutgoingDistributableSessionData> session = findLocalSession(realId);
      // Remove actively managed session and add to the unloaded sessions
      // if it's already unloaded session (session == null) don't do anything, 
      if (session != null)
      {
         synchronized (session)
         {
            if (trace_)
            {
               log_.trace("Passivating session with id: " + realId);
            }
            
            session.notifyWillPassivate(ClusteredSessionNotificationCause.PASSIVATION);
            proxy_.evictSession(realId);
            sessionPassivated();
            
            // Put the session in the unloadedSessions map. This will
            // expose the session to regular invalidation.
            Object obj = unloadedSessions_.put(realId, 
                  new OwnedSessionUpdate(null, session.getLastAccessedTimeInternal(), session.getMaxInactiveInterval(), true));
            if (trace_)
            {
               if (obj == null)
               {
                  log_.trace("New session " + realId + " added to unloaded session map");
               }
               else
               {
                  log_.trace("Updated timestamp for unloaded session " + realId);
               }
            }
            sessions_.remove(realId);
         }
      }
      else if (trace_)
      {
         log_.trace("processSessionPassivation():  could not find session " + realId);
      }
   }
   
   /**
    * Session passivation logic for sessions only in the distributed store.
    * 
    * @param realId the session id, minus any jvmRoute
    */
   private void processUnloadedSessionPassivation(String realId, OwnedSessionUpdate osu)
   {
      if (trace_)
      {
         log_.trace("Passivating session with id: " + realId);
      }

      proxy_.evictSession(realId, osu.getOwner());
      osu.setPassivated(true);
      sessionPassivated();      
   }
   
   private void sessionPassivated()
   {
      int pc = passivatedCount_.incrementAndGet();
      int max = maxPassivatedCount_.get();
      while (pc > max)
      {
         if (!maxPassivatedCount_ .compareAndSet(max, pc))
         {
            max = maxPassivatedCount_.get();
         }
      }
   }
   
   // -----------------------------------------------  Lifecyle When Unembedded

   /**
    * Start this Manager when running in standalone Tomcat.
    */
   private void startUnembedded() throws LifecycleException
   {
      if (started_)
      {
         return;
      }
      
      log_.debug("Manager is about to start");      

      // Notify our interested LifecycleListeners
      lifecycle_.fireLifecycleEvent(BEFORE_START_EVENT, this);
      
      configureUnembedded();            
      
      initClusteredSessionNotificationPolicy();
      
      // Create the DistributedCacheManager
      try
      {
         if (replicationConfig_ == null)
         {
            synthesizeReplicationConfig();
         }
         
         if (proxy_ == null)
         {
            initDistributedCacheManager();
         }
         
         // We need to pass the classloader that is associated with this 
         // web app so de-serialization will work correctly.
         tcl_ = container_.getLoader().getClassLoader();
         proxy_.start();
      }
      catch (Throwable t)
      {
         String str = "Problem starting DistributedCacheManager for HttpSession clustering";
         log_.error(str, t);
         throw new LifecycleException(str, t);
      }

      batchingManager = proxy_.getBatchingManager();
      if(batchingManager == null)
      {
         throw new LifecycleException("start(): Obtained null batchingManager");
      }
      
      try
      {         
         this.outdatedSessionChecker = initOutdatedSessionChecker();
         initializeUnloadedSessions();
         
         // Setup our SnapshotManager
         initSnapshotManager();

         // Add SnapshotValve and, if needed, JvmRouteValve and batch repl valve
         installValves();
         
         backgroundProcessAllowed.set(true);

         started_ = true;
         
         // Notify our interested LifecycleListeners
         lifecycle_.fireLifecycleEvent(AFTER_START_EVENT, this);
         
         // Let subclasses do what they want
         startExtensions();
         
         log_.debug("start(): DistributedCacheManager started");
      } 
      catch (Exception e)
      {
         log_.error("Unable to start manager.", e);
         throw new LifecycleException(e);
      }
      
      registerManagerMBean();
   }

   protected void configureUnembedded() throws LifecycleException
   {
      if (snapshotMode_ == null)
      {
         // We were not instantiated by a JBossCacheCluster, so we need to
         // find one and let it configure our cluster-wide properties
         try 
         {
            JBossCacheCluster cluster = (JBossCacheCluster) container_.getCluster();
            if (cluster == null)
            {
               cluster = new JBossCacheCluster();
            }
            cluster.configureManager(this);
         }
         catch (ClassCastException e)
         {
            String msg = "Cluster is not an instance of JBossCacheCluster";
            log_.error(msg, e);
            throw new LifecycleException(msg, e);
         }
      }
   }

   private void synthesizeReplicationConfig()
   {
      ReplicationConfig cfg = new ReplicationConfig();
      cfg.setReplicationGranularity(replicationGranularity_);
      cfg.setReplicationTrigger(replicationTrigger_);
      cfg.setUseJK(useJK_);
      cfg.setCacheName(cacheConfigName_);
      cfg.setSnapshotMode(snapshotMode_);
      cfg.setSnapshotInterval(Integer.valueOf(snapshotInterval_));
      cfg.setMaxUnreplicatedInterval(Integer.valueOf(maxUnreplicatedInterval_));
      cfg.setSessionNotificationPolicy(notificationPolicyClass_);      
      this.replicationConfig_ = cfg;
   }
   
   private void installValve(Valve valve)
   {
      boolean installed = false;
      
      // In embedded mode, install the valve via JMX to be consistent
      // with the way the overall context is created in TomcatDeployer.
      // We can't do this in unembedded mode because we are called
      // before our Context is registered with the MBean server
      if (embedded_)
      {
         ObjectName name = this.getObjectName(this.container_);
         
         if (name != null)
         {
            try
            {
               MBeanServer server = this.getMBeanServer();
               
               server.invoke(name, "addValve", new Object[] { valve }, new String[] { Valve.class.getName() });
               
               installed = true;
            }
            catch (Exception e)
            {
               // JBAS-2422.  If the context is restarted via JMX, the above
               // JMX call will fail as the context will not be registered
               // when it's made.  So we catch the exception and fall back
               // to adding the valve directly.
               // TODO consider skipping adding via JMX and just do it directly
               log_.debug("Caught exception installing valve to Context", e);
            }
         }
      }
      
      if (!installed)
      {
         // If possible install via the ContainerBase.addValve() API.
         if (this.container_ instanceof ContainerBase)
         {            
            ((ContainerBase) this.container_).addValve(valve);
         }
         else
         {
            // No choice; have to add it to the context's pipeline
            this.container_.getPipeline().addValve(valve);
         }
      }
   }
   
   private ObjectName getObjectName(Container container)
   {
      String oname = container.getObjectName();
      
      try
      {         
         return (oname == null) ? null : new ObjectName(oname);
      }
      catch (MalformedObjectNameException e)
      {
         log_.warn("Error creating object name from string " + oname, e);
         return null;
      }
   }
   
   /**
    * Clear the underlying cache store.
    */
   private void clearSessions()
   {
      boolean passivation = isPassivationEnabled();
      // First, the sessions we have actively loaded
      ClusteredSession<? extends OutgoingDistributableSessionData>[] sessions = findLocalSessions();
      for(int i=0; i < sessions.length; i++)
      {
         ClusteredSession<? extends OutgoingDistributableSessionData> ses = sessions[i];
         
         if (trace_)
         {
             log_.trace("clearSessions(): clear session by expiring or passivating: " + ses);
         }
         try
         {
            // if session passivation is enabled, passivate sessions instead of expiring them which means
            // they'll be available to the manager for activation after a restart. 
            if(passivation && ses.isValid())
            {               
               processSessionPassivation(ses.getRealId());
            }
            else
            {               
               boolean notify = true;
               boolean localCall = true;
               boolean localOnly = true;
               ses.expire(notify, localCall, localOnly, ClusteredSessionNotificationCause.UNDEPLOY);               
            }
         }
         catch (Throwable t)
         {
            log_.warn("clearSessions(): Caught exception expiring or passivating session " +
                     ses.getIdInternal(), t);
         }
         finally
         {
            // Guard against leaking memory if anything is holding a
            // ref to the session by clearing its internal state
            ses.recycle();
         }
      }      
      
      String action = passivation ? "evicting" : "removing";
      Set<Map.Entry<String, OwnedSessionUpdate>> unloaded = 
               unloadedSessions_.entrySet();
      for (Iterator<Map.Entry<String, OwnedSessionUpdate>> it = unloaded.iterator(); it.hasNext();)
      {
         Map.Entry<String, OwnedSessionUpdate> entry = it.next();
         String realId = entry.getKey();         
         try
         {
            if (passivation)
            {
               OwnedSessionUpdate osu = entry.getValue();
               // Ignore the marker entries for our passivated sessions
               if (!osu.isPassivated())
               {
                  proxy_.evictSession(realId, osu.getOwner());
               }
            }
            else
            {
               proxy_.removeSessionLocal(realId);           
            }
         }
         catch (Exception e)
         {
            // Not as big a problem; we don't own the session
            log_.debug("Problem " + action + " session " + realId + " -- " + e);
         }
         it.remove(); 
      }
   }
   
   // ------------------------------------------------------  Lifecyle Embedded
   
   /**
    * Start this Manager when running embedded in JBoss AS.
    *
    * @throws org.apache.catalina.LifecycleException
    */
   private void startEmbedded() throws LifecycleException
   {
      super.start();
      
      initClusteredSessionNotificationPolicy();
      this.outdatedSessionChecker = initOutdatedSessionChecker();
      
      // Start the DistributedCacheManager
      // Will need to pass the classloader that is associated with this 
      // web app so de-serialization will work correctly.
      tcl_ = super.getContainer().getLoader().getClassLoader();
      
      try
      {
         if (proxy_ == null)
         {
            initDistributedCacheManager();
         }
         
         proxy_.start();

         batchingManager = proxy_.getBatchingManager();
         if(batchingManager == null)
         {
            throw new LifecycleException("JBossCacheManager.start(): Obtain null batchingManager");
         }
         
         initializeUnloadedSessions();
         
         // Setup our SnapshotManager
         initSnapshotManager();
         
         // Add SnapshotValve and, if needed, JvmRouteValve and batch repl valve
         installValves();
         
         // Let subclasses do what they want
         startExtensions();

         log_.debug("start(): DistributedCacheManager started");         
      }
      catch (LifecycleException le)
      {
         throw le;
      }
      catch (Exception e)
      {
         log_.error("Unable to start manager.", e);
         throw new LifecycleException(e);
      }
   }

   // -------------------------------------------------------------------- Misc
   
   /**
    * Gets the session id with any jvmRoute removed.
    * 
    * @param id a session id with or without an appended jvmRoute.
    *           Cannot be <code>null</code>.
    */
   private String getRealId(String id)
   {
      return (getUseJK() ? Util.getRealId(id) : id);
   }
   
   private String reportSessionIds(Set<String> ids)
   {
      StringBuffer sb = new StringBuffer();
      boolean added = false;
      for (String id : ids)
      {
         if (added)
         {
            sb.append(',');
         }
         else
         {
            added = true;
         }
         
         sb.append(id);
      }
      return sb.toString();
   }   
   
   @SuppressWarnings("unchecked")
   private static ContextClassLoaderSwitcher getContextClassLoaderSwitcher()
   {
      return (ContextClassLoaderSwitcher) AccessController.doPrivileged(ContextClassLoaderSwitcher.INSTANTIATOR);
   }
   
   @SuppressWarnings("unchecked")
   private static <T extends OutgoingDistributableSessionData> JBossCacheManager<T> uncheckedCastManager(JBossCacheManager mgr)
   {
      return mgr;
   }
   
   @SuppressWarnings("unchecked")
   private static ClusteredSession<? extends OutgoingDistributableSessionData> uncheckedCastSession(Session session)
   {
      return (ClusteredSession) session;
   }
   
   // ------------------------------------------------------------ Inner Classes
   
   private class PassivationCheck implements Comparable<PassivationCheck>
   {
      private final String realId;
      private final OwnedSessionUpdate osu;
      private final ClusteredSession<? extends OutgoingDistributableSessionData> session;
      
      private PassivationCheck(String realId, OwnedSessionUpdate osu)
      {
         assert osu != null : "osu is null";
         assert realId != null : "realId is null";
         
         this.realId = realId;
         this.osu = osu;
         this.session = null;
      }
      
      private PassivationCheck(ClusteredSession<? extends OutgoingDistributableSessionData> session)
      {
         assert session != null : "session is null";
         
         this.realId = session.getRealId();
         this.session = session;
         this.osu = null;
      }
      
      private long getLastUpdate()
      {
         return osu == null ? session.getLastAccessedTimeInternal() : osu.getUpdateTime();
      }
      
      private void passivate()
      {         
         if (osu == null)
         {
            JBossCacheManager.this.processSessionPassivation(realId);
         }
         else
         {
            JBossCacheManager.this.processUnloadedSessionPassivation(realId, osu);
         }
      }
      
      private String getRealId()
      {
         return realId;
      }
      
      private boolean isUnloaded()
      {
         return osu != null;
      }

      // This is what causes sorting based on lastAccessed
      public int compareTo(PassivationCheck o)
      {
         long thisVal = getLastUpdate();
         long anotherVal = o.getLastUpdate();
         return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
      }
   }
   
   private static class SemaphoreLock implements Lock
   {
      private final Semaphore semaphore;
      
      SemaphoreLock(Semaphore semaphore)
      {
         this.semaphore = semaphore;
      }
      
      /**
       * @see java.util.concurrent.locks.Lock#lock()
       */
      public void lock()
      {
         this.semaphore.acquireUninterruptibly();
      }

      /**
       * @see java.util.concurrent.locks.Lock#lockInterruptibly()
       */
      public void lockInterruptibly() throws InterruptedException
      {
         this.semaphore.acquire();
      }

      /**
       * @see java.util.concurrent.locks.Lock#newCondition()
       */
      public Condition newCondition()
      {
         throw new UnsupportedOperationException();
      }

      /**
       * @see java.util.concurrent.locks.Lock#tryLock()
       */
      public boolean tryLock()
      {
         return this.semaphore.tryAcquire();
      }

      /**
       * @see java.util.concurrent.locks.Lock#tryLock(long, java.util.concurrent.TimeUnit)
       */
      public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException
      {
         return this.semaphore.tryAcquire(timeout, unit);
      }

      /**
       * @see java.util.concurrent.locks.Lock#unlock()
       */
      public void unlock()
      {
         this.semaphore.release();
      }
   }
}
