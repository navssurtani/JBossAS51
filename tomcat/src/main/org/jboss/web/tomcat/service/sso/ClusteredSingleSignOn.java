/*
 * Copyright 1999-2001,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.web.tomcat.service.sso;


import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;

import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.SessionEvent;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.session.ManagerBase;
import org.apache.tomcat.util.modeler.Registry;
import org.jboss.web.tomcat.service.session.JBossManager;
import org.jboss.web.tomcat.service.sso.spi.FullyQualifiedSessionId;
import org.jboss.web.tomcat.service.sso.spi.SSOClusterManager;
import org.jboss.web.tomcat.service.sso.spi.SSOCredentials;
import org.jboss.web.tomcat.service.sso.spi.SSOLocalManager;


/**
 * A <strong>Valve</strong> that supports a "single sign on" user experience,
 * where the security identity of a user who successfully authenticates to one
 * web application is propagated to other web applications in the same
 * security domain.  For successful use, the following requirements must
 * be met:
 * <ul>
 * <li>This Valve must be configured on the Container that represents a
 * virtual host (typically an implementation of <code>Host</code>).</li>
 * <li>The <code>Realm</code> that contains the shared user and role
 * information must be configured on the same Container (or a higher
 * one), and not overridden at the web application level.</li>
 * <li>The web applications themselves must use one of the standard
 * Authenticators found in the
 * <code>org.apache.catalina.authenticator</code> package.</li>
 * </ul>
 *
 * @author Brian E. Stansberry based on the work of Craig R. McClanahan
 * @version $Revision: 85945 $ $Date: 2009-03-16 15:45:12 -0400 (Mon, 16 Mar 2009) $
 */
public class ClusteredSingleSignOn
   extends org.apache.catalina.authenticator.SingleSignOn
   implements LifecycleListener, SSOLocalManager
{
   /** By default we process expired SSOs no more often than once per minute */
   public static final int DEFAULT_PROCESS_EXPIRES_INTERVAL = 60;
   /** By default we let SSOs without active sessions live for 30 mins */
   public static final int DEFAULT_MAX_EMPTY_LIFE = 1800;
   /** The default JBoss Cache to use for storing SSO entries */
   public static final String DEFAULT_CACHE_NAME = "clustered-sso";
   public static final String LEGACY_CACHE_NAME = "jboss.cache:service=TomcatClusteringCache";
   public static final String DEFAULT_CLUSTER_MANAGER_CLASS = "org.jboss.web.tomcat.service.sso.jbc.JBossCacheSSOClusterManager";
   
   // Override the superclass value
   static
   {
      info = ClusteredSingleSignOn.class.getName();
   }
    
   // ----------------------------------------------------- Instance Variables

 
   /**
    * Fully qualified name of a class implementing
    * {@link SSOClusterManager SSOClusterManager} that will be used
    * to manage SSOs across a cluster.
    */
   private String clusterManagerClass = DEFAULT_CLUSTER_MANAGER_CLASS;

   /**
    * Object used to provide cross-cluster support for single sign on.
    */
   private SSOClusterManager ssoClusterManager = null;

   /**
    * Object name of the tree cache used by SSOClusterManager.
    * Only relevant if the SSOClusterManager implementation is
    * TreeCacheSSOClusterManager.
    */
   private String cacheConfigName = DEFAULT_CACHE_NAME;

   /**
    * Object name of the thread pool used by SSOClusterManager.
    * Only relevant if the SSOClusterManager implementation is
    * TreeCacheSSOClusterManager.
    */
   private String threadPoolName = "jboss.system:service=ThreadPool";

   /** Currently started Managers that have associated as session with an SSO */
   private Set activeManagers = Collections.synchronizedSet(new HashSet());
   
   /** Max number of ms an SSO with no active sessions will be usable by a request */
   private int maxEmptyLife = DEFAULT_MAX_EMPTY_LIFE * 1000;
   
   /** 
    * Minimum number of ms since the last processExpires() run 
    * before a new run is allowed.
    */
   private int processExpiresInterval = DEFAULT_PROCESS_EXPIRES_INTERVAL * 1000;
   
   /** Timestamp of the last processExpires() run */
   private long lastProcessExpires = System.currentTimeMillis();
   
   /** 
    * Map<String, Long> containing the ids of SSOs with no active sessions
    * and the time at which they entered that state
    */
   private Map emptySSOs = new ConcurrentHashMap();
   
   /** Used for sync locking of processExpires runs */
   private final Object mutex = new Object();

   // ------------------------------------------------------------- Properties

   /**
    * Gets the object that provides SSO support across a cluster.
    *
    * @return the object provided cluster support, or <code>null</code> if
    *         no such object has been configured.
    */
   public SSOClusterManager getClusterManager()
   {
      return this.ssoClusterManager;
   }


   /**
    * Sets the object that provides SSO support across a cluster.
    *
    * @param clusterManager the object that provides SSO support.
    * @throws IllegalStateException if this method is invoked after this valve
    *                               has been started.
    */
   public void setClusterManager(SSOClusterManager clusterManager)
   {
      if (started && (clusterManager != ssoClusterManager))
      {
         throw new IllegalStateException("already started -- cannot set a " +
            "new SSOClusterManager");
      }

      this.ssoClusterManager = clusterManager;

      if (clusterManager != null)
      {
         clusterManagerClass = clusterManager.getClass().getName();
      }
   }


   /**
    * Gets the name of the class that will be used to provide SSO support
    * across a cluster.
    *
    * @return Fully qualified name of a class implementing
    *         {@link SSOClusterManager SSOClusterManager}
    *         that is being used to manage SSOs across a cluster.
    *         May return <code>null</code> (the default) if clustered
    *         SSO support is not configured.
    */
   public String getClusterManagerClass()
   {
      return clusterManagerClass;
   }


   /**
    * Sets the name of the class that will be used to provide SSO support
    * across a cluster.
    * <p><b>NOTE: </b>
    * If this Valve has already started, and no SSOClusterManager has been
    * configured for it, calling this method will
    *
    * @param managerClass Fully qualified name of a class implementing
    *                     {@link SSOClusterManager SSOClusterManager}
    *                     that will be used to manage SSOs across a cluster.
    *                     Class must declare a public no-arguments
    *                     constructor.  <code>null</code> is allowed.
    */
   public void setClusterManagerClass(String managerClass)
   {
      if (!started)
      {
         clusterManagerClass = managerClass;
      }
      else if (ssoClusterManager == null)
      {
         try
         {
            createClusterManager(managerClass);
         }
         catch (LifecycleException e)
         {
            getContainer().getLogger().error("Exception creating SSOClusterManager " +
               managerClass, e);
         }
      }
      else
      {
          getContainer().getLogger().error("Cannot set clusterManagerClass to " + managerClass +
            "; already started using " + clusterManagerClass);
      }
   }

   /**
    * Name of the cache config used by SSOClusterManager.
    * 
    * @deprecated use {@link #getCacheConfig()}
    */
   @Deprecated
   public String getTreeCacheName()
   {
      return getCacheConfig();
   }

   /**
    * Sets the name of the cache config used by SSOClusterManager.
    * 
    * @deprecated use {@link #setCacheConfig(String)}
    */
   @Deprecated
   public void setTreeCacheName(String cacheName)
      throws Exception
   {
      setCacheConfig(cacheName);
   }
   
   /**
    * Name of the cache config used by SSOClusterManager.
    */
   public String getCacheConfig()
   {
      return cacheConfigName;
   }
   
   /**
    * Sets the name of the cache config used by SSOClusterManager.
    */
   public void setCacheConfig(String cacheConfig)
   {
      this.cacheConfigName = cacheConfig;      
   }
   
   /**
    * Object name of the thread pool used by SSOClusterManager.
    * Only relevant if the SSOClusterManager implementation is
    * TreeCacheSSOClusterManager.
    */
   public String getThreadPoolName()
   {
      return threadPoolName;
   }

   /**
    * Sets the object name of the thread pool used by SSOClusterManager.
    * Only relevant if the SSOClusterManager implementation is
    * TreeCacheSSOClusterManager.
    */
   public void setThreadPoolName(String poolName)
      throws Exception
   {
      this.threadPoolName = poolName;
   }

   /**
    * Gets the max number of seconds an SSO with no active sessions will be 
    * usable by a request.
    * 
    * @return a non-negative number
    * 
    * @see #DEFAULT_MAX_EMPTY_LIFE    * 
    * @see #setMaxEmptyLife() 
    */
   public int getMaxEmptyLife()
   {
      return (maxEmptyLife / 1000);
   }


   /**
    * Sets the maximum number of seconds an SSO with no active sessions will be 
    * usable by a request.
    * <p>
    * A positive value for this property allows a user to continue to use an SSO
    * even after all the sessions associated with it have been expired. It does not
    * keep an SSO alive if a session associated with it has been invalidated due to 
    * an <code>HttpSession.invalidate()</code> call.
    * </p>
    * <p>
    * The primary purpose of this property is to avoid the situation where a server
    * on which all of an SSO's sessions lives is shutdown, thus expiring all the
    * sessions and causing the invalidation of the SSO. A positive value for this
    * property would give the user an opportunity to fail over to another server
    * and maintain the SSO.
    * </p>
    * 
    * @param maxEmptyLife a non-negative number
    * 
    * @throws IllegalArgumentException if <code>maxEmptyLife < 0</code>
    */
   public void setMaxEmptyLife(int maxEmptyLife)
   {
      if (maxEmptyLife < 0)
         throw new IllegalArgumentException("maxEmptyLife must be >= 0");
      
      this.maxEmptyLife = maxEmptyLife * 1000;
   }


   /**
    * Gets the minimum number of seconds since the start of the last check for overaged
    * SSO's with no active sessions before a new run is allowed.
    * 
    * @return a positive number
    * 
    * @see #DEFAULT_PROCESS_EXPIRES_INTERVAL
    * @see #setMaxEmptyLife() 
    * @see #setProcessExpiresInterval(int)
    */
   public int getProcessExpiresInterval()
   {
      return processExpiresInterval / 1000;
   }

   /**
    * Sets the minimum number of seconds since the start of the last check for overaged
    * SSO's with no active sessions before a new run is allowed.  During this check,
    * any such overaged SSOs will be invalidated. 
    * <p>
    * Note that setting this value does not imply that a check will be performed
    * every <code>processExpiresInterval</code> seconds, only that it will not
    * be performed more often than that.
    * </p>
    * 
    * @param processExpiresInterval a non-negative number. <code>0</code> means
    *                               the overage check can be performed whenever
    *                               the container wishes to.
    *                               
    * @throws IllegalArgumentException if <code>processExpiresInterval < 1</code>
    * 
    * @see #setMaxEmptyLife()
    */
   public void setProcessExpiresInterval(int processExpiresInterval)
   {
      if (processExpiresInterval < 0)
         throw new IllegalArgumentException("processExpiresInterval must be >= 0");
      
      this.processExpiresInterval = processExpiresInterval * 1000;
   }


   /**
    * Gets the timestamp of the start of the last check for overaged
    * SSO's with no active sessions.
    * 
    * @see #setProcessExpiresInterval(int)
    */
   public long getLastProcessExpires()
   {
      return lastProcessExpires;
   }


   // ------------------------------------------------------ Lifecycle Methods


   /**
    * Prepare for the beginning of active use of the public methods of this
    * component.  This method should be called after <code>configure()</code>,
    * and before any of the public methods of the component are utilized.
    *
    * @throws LifecycleException if this component detects a fatal error
    *                            that prevents this component from being used
    */
   public void start() throws LifecycleException
   {
      // Validate and update our current component state
      if (started)
      {
         throw new LifecycleException
            (sm.getString("authenticator.alreadyStarted"));
      }

      // Attempt to create an SSOClusterManager
      createClusterManager(getClusterManagerClass());

      lifecycle.fireLifecycleEvent(START_EVENT, null);
      started = true;

      if (ssoClusterManager != null)
      {
         try
         {
            ssoClusterManager.start();
         }
         catch (LifecycleException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new LifecycleException("Caught exception stopping " + 
                  ssoClusterManager.getClass().getSimpleName(), e);
         }
      }

   }


   /**
    * Gracefully terminate the active use of the public methods of this
    * component.  This method should be the last one called on a given
    * instance of this component.
    *
    * @throws LifecycleException if this component detects a fatal error
    *                            that needs to be reported
    */
   public void stop() throws LifecycleException
   {
      // Validate and update our current component state
      if (!started)
      {
         throw new LifecycleException
            (sm.getString("authenticator.notStarted"));
      }

      if (ssoClusterManager != null)
      {
         try
         {
            ssoClusterManager.stop();
         }
         catch (LifecycleException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new LifecycleException("Caught exception stopping " + 
                  ssoClusterManager.getClass().getSimpleName(), e);
         }
      }

      lifecycle.fireLifecycleEvent(STOP_EVENT, null);
      started = false;

   }


   // ------------------------------------------------ SessionListener Methods


   /**
    * Updates the state of a single sign on session to reflect the destruction
    * of a standard HTTP session.
    * <p/>
    * If the given event is a {@link Session#SESSION_DESTROYED_EVENT
    * Session destroyed event}, checks whether the session was destroyed due
    * to timeout or user action (i.e. logout).  If due to timeout, disassociates
    * the Session from the single sign on session.  If due to logout, invokes
    * the {@link #logout} method.
    *
    * @param event SessionEvent that has occurred
    */
   public void sessionEvent(SessionEvent event)
   {
      // We only care about session destroyed events
      if (!Session.SESSION_DESTROYED_EVENT.equals(event.getType()))
         return;

      // Look up the single session id associated with this session (if any)
      Session session = event.getSession();
      if (getContainer().getLogger().isTraceEnabled())
          getContainer().getLogger().trace("Process session destroyed on " + session);

      String ssoId = null;
      synchronized (reverse)
      {
         ssoId = (String) reverse.get(session);
      }
      if (ssoId == null)
         return;

      try
      {
         // Was the session destroyed as the result of a timeout or
         // the undeployment of the containing webapp?
         // If so, we'll just remove the expired session from the
         // SSO.  If the session was logged out, we'll log out
         // of all sessions associated with the SSO.
         if (isSessionTimedOut(session) || isManagerStopped(session))
         {
            removeSession(ssoId, session);

            // Quite poor.  We hijack the caller thread (the Tomcat background thread)
            // to do our cleanup of expired sessions
            processExpires();
         }
         else
         {
            // The session was logged out.
            logout(ssoId);
         }
      }
      catch (Exception e)
      {
         // Don't propagate back to the webapp; we don't want to disrupt
         // the session expiration process
         getContainer().getLogger().error("Caught exception updating SSO " + ssoId +
                                          " following destruction of session " +
                                          session.getIdInternal(), e);
      }
   }

   private boolean isSessionTimedOut(Session session)
   {
      return (session.getMaxInactiveInterval() > 0)
               && (System.currentTimeMillis() - session.getLastAccessedTime() >=
                  session.getMaxInactiveInterval() * 1000);
   }

   private boolean isManagerStopped(Session session)
   {
      boolean stopped = false;
      
      Manager manager = session.getManager();
      
      if (manager instanceof ManagerBase)
      {
         ObjectName mgrName = ((ManagerBase)manager).getObjectName();
         stopped = (!activeManagers.contains(mgrName));
      }
      else if (manager instanceof JBossManager)
      {
         ObjectName mgrName = ((JBossManager)manager).getObjectName();
         stopped = (!activeManagers.contains(mgrName));
      }
      else if (manager instanceof Lifecycle)
      {
         stopped = (!activeManagers.contains(manager));
      }
      // else we have no way to tell, so assume not
      
      return stopped;
   }

   // ----------------------------------------------  LifecycleListener Methods


   public void lifecycleEvent(LifecycleEvent event)
   {
      String type = event.getType();
      if (Lifecycle.BEFORE_STOP_EVENT.equals(type) 
            || Lifecycle.STOP_EVENT.equals(type) 
            || Lifecycle.AFTER_STOP_EVENT.equals(type))
      {
         Lifecycle source = event.getLifecycle();
         boolean removed;
         if (source instanceof ManagerBase)
         {
            removed = activeManagers.remove(((ManagerBase)source).getObjectName());
         }
         else if (source instanceof JBossManager)
         {
            removed = activeManagers.remove(((JBossManager)source).getObjectName());
         }
         else
         {
            removed = activeManagers.remove(source);
         }
         
         if (removed)
         {
            source.removeLifecycleListener(this);
            
            getContainer().getLogger().debug("ClusteredSSO: removed stopped " +
                                             "manager " + source.toString());            
         }
         
         // TODO consider getting the sessions and removing any from our sso's
         // Idea is to cleanup after managers that don't destroy sessions
         
      }      
   }
   

   // ---------------------------------------------------------- Valve Methods


   /**
    * Perform single-sign-on support processing for this request.
    * <p/>
    * Overrides the superclass version by handling the fact that a
    * single sign on may have been originated on another cluster node and
    * thus may not have a <code>Principal</code> object associated with it
    * on this node.
    *
    * @param request  The servlet request we are processing
    * @param response The servlet response we are creating
    * @param context  The valve context used to invoke the next valve
    *                 in the current processing pipeline
    * @throws IOException      if an input/output error occurs
    * @throws ServletException if a servlet error occurs
    */
   public void invoke(Request request, Response response)
      throws IOException, ServletException
   {
      request.removeNote(Constants.REQ_SSOID_NOTE);

      // Has a valid user already been authenticated?
      if (getContainer().getLogger().isTraceEnabled())
         getContainer().getLogger().trace("Process request for '" + request.getRequestURI() + "'");
      if (request.getUserPrincipal() != null)
      {
         if (getContainer().getLogger().isTraceEnabled())
            getContainer().getLogger().trace(" Principal '" + request.getUserPrincipal().getName() +
               "' has already been authenticated");
         getNext().invoke(request, response);
         return;
      }

      // Check for the single sign on cookie
      Cookie cookie = null;
      Cookie cookies[] = request.getCookies();
      if (cookies == null)
         cookies = new Cookie[0];
      for (int i = 0; i < cookies.length; i++)
      {
         if (Constants.SINGLE_SIGN_ON_COOKIE.equals(cookies[i].getName()))
         {
            cookie = cookies[i];
            break;
         }
      }
      if (cookie == null)
      {
         if (getContainer().getLogger().isTraceEnabled())
            getContainer().getLogger().trace(" SSO cookie is not present");
         getNext().invoke(request, response);
         return;
      }

      // Look up the cached Principal associated with this cookie value
      String ssoId = cookie.getValue();
      if (getContainer().getLogger().isTraceEnabled())
          getContainer().getLogger().trace(" Checking for cached principal for " + ssoId);
      JBossSingleSignOnEntry entry = getSingleSignOnEntry(cookie.getValue());
      if (entry != null && isValid(ssoId, entry))
      {
         Principal ssoPrinc = entry.getPrincipal();
         // have to deal with the fact that the entry may not have an
         // associated Principal. SSO entries retrieved via a lookup from a 
         // cluster will not have a Principal, as Principal is not Serializable
         if (getContainer().getLogger().isTraceEnabled())
         {
             getContainer().getLogger().trace(" Found cached principal '" +
               (ssoPrinc == null ? "NULL" : ssoPrinc.getName()) +
               "' with auth type '" + entry.getAuthType() + "'");
         }
         request.setNote(Constants.REQ_SSOID_NOTE, cookie.getValue());
         // Only set security elements if per-request reauthentication is 
         // not required AND the SSO entry had a Principal.  
         if (!getRequireReauthentication() && ssoPrinc != null)
         {
            request.setAuthType(entry.getAuthType());
            request.setUserPrincipal(ssoPrinc);
         }
      }
      else
      {
         if (getContainer().getLogger().isTraceEnabled())
            getContainer().getLogger().trace(" No cached principal found, erasing SSO cookie");
         cookie.setMaxAge(0);
         response.addCookie(cookie);
      }

      // Invoke the next Valve in our pipeline
      getNext().invoke(request, response);
   }


   // ------------------------------------------------------ Protected Methods


   /**
    * Associate the specified single sign on identifier with the
    * specified Session.
    * <p/>
    * Differs from the superclass version in that it notifies the cluster
    * of any new association of SSO and Session.
    *
    * @param ssoId   Single sign on identifier
    * @param session Session to be associated
    */
   public void associate(String ssoId, Session session)
   {
      if (getContainer().getLogger().isTraceEnabled())
          getContainer().getLogger().trace("Associate sso id " + ssoId + " with session " + session);

      JBossSingleSignOnEntry sso = getSingleSignOnEntry(ssoId);
      boolean added = false;
      if (sso != null)
         added = sso.addSession2(this, session);

      synchronized (reverse)
      {
         reverse.put(session, ssoId);
      }

      // If we made a change, track the manager and notify any cluster
      if (added)
      {
         Manager manager = session.getManager();
         
         // Prefer to cache an ObjectName to avoid risk of leaking a manager,
         // so if the manager exposes one, use it
         Object mgrKey = null;
         if (manager instanceof ManagerBase)
         {            
            mgrKey = ((ManagerBase)manager).getObjectName();
         }
         else if (manager instanceof JBossManager)
         {
            mgrKey = ((JBossManager)manager).getObjectName();
         }
         else if (manager instanceof Lifecycle)
         {
            mgrKey = manager;
         }
         else {
            getContainer().getLogger().warn("Manager for session " + 
                      session.getIdInternal() +
                      " does not implement Lifecycle; web app shutdown may " +
                      " lead to incorrect SSO invalidations");
         }
         
         if (mgrKey != null)
         {
            synchronized (activeManagers)
            {
               if (!activeManagers.contains(mgrKey))
               {
                  activeManagers.add(mgrKey);
                  ((Lifecycle) manager).addLifecycleListener(this);
               }
            }
         }
         
         if (ssoClusterManager != null)         
            ssoClusterManager.addSession(ssoId, getFullyQualifiedSessionId(session));
      }
   }


   /**
    * Deregister the specified session.  If it is the last session,
    * then also get rid of the single sign on identifier.
    * <p/>
    * Differs from the superclass version in that it notifies the cluster
    * of any disassociation of SSO and Session.
    *
    * @param ssoId   Single sign on identifier
    * @param session Session to be deregistered
    */
   protected void deregister(String ssoId, Session session)
   {
      synchronized (reverse)
      {
         reverse.remove(session);
      }

      JBossSingleSignOnEntry sso = getSingleSignOnEntry(ssoId);
      if (sso == null)
         return;

      boolean removed = sso.removeSession2(session);
      // If we changed anything, notify any cluster
      if (removed && ssoClusterManager != null)
      {
         ssoClusterManager.removeSession(ssoId, getFullyQualifiedSessionId(session));
      }

      // see if this was the last session on this node, 
      // if remove sso entry from our local cache
      if (sso.getSessionCount() == 0)
      {
         synchronized (cache)
         {
            sso = (JBossSingleSignOnEntry) cache.remove(ssoId);
         }
      }
   }


   /**
    * Deregister the specified single sign on identifier, and invalidate
    * any associated sessions.
    *
    * @param ssoId Single sign on identifier to deregister
    */
   public void deregister(String ssoId)
   {
      if (getContainer().getLogger().isTraceEnabled())
          getContainer().getLogger().trace("Deregistering sso id '" + ssoId + "'");

      
      // It's possible we don't have the SSO locally but it's in
      // the emptySSOs map; if so remove it
      emptySSOs.remove(ssoId);
      
      // Look up and remove the corresponding SingleSignOnEntry
      JBossSingleSignOnEntry sso = null;
      synchronized (cache)
      {
         sso = (JBossSingleSignOnEntry) cache.remove(ssoId);
      }

      if (sso == null)
         return;

      // Expire any associated sessions
      Session sessions[] = sso.findSessions();
      for (int i = 0; i < sessions.length; i++)
      {
         if (getContainer().getLogger().isTraceEnabled())
             getContainer().getLogger().trace(" Invalidating session " + sessions[i]);
         // Remove from reverse cache first to avoid recursion
         synchronized (reverse)
         {
            reverse.remove(sessions[i]);
         }
         // Invalidate this session
         sessions[i].expire();
      }

      // NOTE:  Clients may still possess the old single sign on cookie,
      // but it will be removed on the next request since it is no longer
      // in the cache
   }


   /**
    * Deregister the given SSO, invalidating any associated sessions, then
    * notify any cluster of the logout.
    *
    * @param ssoId the id of the SSO session
    */
   protected void logout(String ssoId)
   {
      deregister(ssoId);
      //  broadcast logout to any cluster
      if (ssoClusterManager != null)
         ssoClusterManager.logout(ssoId);
   }


   /**
    * Look up and return the cached SingleSignOn entry associated with this
    * sso id value, if there is one; otherwise return <code>null</code>.
    *
    * @param ssoId Single sign on identifier to look up
    */
   protected JBossSingleSignOnEntry getSingleSignOnEntry(String ssoId)
   {
	  JBossSingleSignOnEntry sso = localLookup(ssoId);
      // If we don't have one locally and there is a cluster,
      // query the cluster for the SSO
      if (sso == null && ssoClusterManager != null)
      {
         SSOCredentials credentials = ssoClusterManager.lookup(ssoId);
         if (credentials != null)
         {
            sso = new JBossSingleSignOnEntry(null, credentials.getAuthType(), 
                                             credentials.getUsername(), 
                                             credentials.getPassword());
            // Store it locally
            synchronized (cache)
            {
               cache.put(ssoId, sso);
            }
         }
      }

      return sso;
   }


   /**
    * Attempts reauthentication to the given <code>Realm</code> using
    * the credentials associated with the single sign-on session
    * identified by argument <code>ssoId</code>.
    * <p/>
    * If reauthentication is successful, the <code>Principal</code> and
    * authorization type associated with the SSO session will be bound
    * to the given <code>HttpRequest</code> object via calls to
    * {@link HttpRequest#setAuthType HttpRequest.setAuthType()} and
    * {@link HttpRequest#setUserPrincipal HttpRequest.setUserPrincipal()}
    * </p>
    *
    * @param ssoId   identifier of SingleSignOn session with which the
    *                caller is associated
    * @param realm   Realm implementation against which the caller is to
    *                be authenticated
    * @param request the request that needs to be authenticated
    * @return <code>true</code> if reauthentication was successful,
    *         <code>false</code> otherwise.
    */
   public boolean reauthenticate(String ssoId, Realm realm,
      Request request)
   {
      if (ssoId == null || realm == null)
         return false;

      boolean reauthenticated = false;

      JBossSingleSignOnEntry entry = getSingleSignOnEntry(ssoId);
      if (entry != null && entry.getCanReauthenticate())
      {

         String username = entry.getUsername();
         if (username != null)
         {
            Principal reauthPrincipal =
               realm.authenticate(username, entry.getPassword());
            if (reauthPrincipal != null)
            {
               reauthenticated = true;                    
               // Bind the authorization credentials to the request
               request.setAuthType(entry.getAuthType());
               request.setUserPrincipal(reauthPrincipal);
               // JBAS-2314 -- bind principal to the entry as well
               entry.setPrincipal(reauthPrincipal);
            }
         }
      }

      return reauthenticated;
   }


   /**
    * Register the specified Principal as being associated with the specified
    * value for the single sign on identifier.
    * <p/>
    * Differs from the superclass version in that it notifies the cluster
    * of the registration.
    *
    * @param ssoId     Single sign on identifier to register
    * @param principal Associated user principal that is identified
    * @param authType  Authentication type used to authenticate this
    *                  user principal
    * @param username  Username used to authenticate this user
    * @param password  Password used to authenticate this user
    */
   public void register(String ssoId, Principal principal, String authType,
      String username, String password)
   {
      registerLocal(ssoId, principal, authType, username, password);

      // broadcast change to any cluster
      if (ssoClusterManager != null)
         ssoClusterManager.register(ssoId, authType, username, password);
   }


   /**
    * Remove a single Session from a SingleSignOn.  Called when
    * a session is timed out and no longer active.
    * <p/>
    * Differs from the superclass version in that it notifies the cluster
    * of any disassociation of SSO and Session.
    *
    * @param ssoId   Single sign on identifier from which to remove the session.
    * @param session the session to be removed.
    */
   protected void removeSession(String ssoId, Session session)
   {
      if (getContainer().getLogger().isTraceEnabled())
          getContainer().getLogger().trace("Removing session " + session.toString() +
            " from sso id " + ssoId);

      // Get a reference to the SingleSignOn
      JBossSingleSignOnEntry entry = getSingleSignOnEntry(ssoId);
      if (entry == null)
         return;

      // Remove the inactive session from SingleSignOnEntry
      boolean removed = entry.removeSession2(session);
      // If we changed anything, notify any cluster
      if (removed && ssoClusterManager != null)
      {
         ssoClusterManager.removeSession(ssoId, getFullyQualifiedSessionId(session));
      }

      // Remove the inactive session from the 'reverse' Map.
      synchronized (reverse)
      {
         reverse.remove(session);
      }
   }


   /**
    * Updates any <code>SingleSignOnEntry</code> found under key
    * <code>ssoId</code> with the given authentication data.
    * <p/>
    * The purpose of this method is to allow an SSO entry that was
    * established without a username/password combination (i.e. established
    * following DIGEST or CLIENT-CERT authentication) to be updated with
    * a username and password if one becomes available through a subsequent
    * BASIC or FORM authentication.  The SSO entry will then be usable for
    * reauthentication.
    * <p/>
    * <b>NOTE:</b> Only updates the SSO entry if a call to
    * <code>SingleSignOnEntry.getCanReauthenticate()</code> returns
    * <code>false</code>; otherwise, it is assumed that the SSO entry already
    * has sufficient information to allow reauthentication and that no update
    * is needed.
    * <p/>
    * Differs from the superclass version in that it notifies the cluster
    * of any update.
    *
    * @param ssoId     identifier of Single sign to be updated
    * @param principal the <code>Principal</code> returned by the latest
    *                  call to <code>Realm.authenticate</code>.
    * @param authType  the type of authenticator used (BASIC, CLIENT-CERT,
    *                  DIGEST or FORM)
    * @param username  the username (if any) used for the authentication
    * @param password  the password (if any) used for the authentication
    */
   public void update(String ssoId, Principal principal, String authType,
      String username, String password)
   {
      boolean needToBroadcast = updateLocal(ssoId, principal, authType,
         username, password);                                    
                                    
      // if there was a change, broadcast it to any cluster
      if (needToBroadcast && ssoClusterManager != null)
      {
         ssoClusterManager.updateCredentials(ssoId, authType,
            username, password);
      }
   }

   //----------------------------------------------  Package-Protected Methods

   /**
    * Search in our local cache for an SSO entry.
    *
    * @param ssoId the id of the SSO session
    * @return any SingleSignOnEntry associated with the given id, or
    *         <code>null</code> if there is none.
    */
   JBossSingleSignOnEntry localLookup(String ssoId)
   {
      synchronized (cache)
      {
         return ((JBossSingleSignOnEntry) cache.get(ssoId));
      }

   }

   /**
    * Create a SingleSignOnEntry using the passed configuration parameters and
    * register it in the local cache, bound to the given id.
    *
    * @param ssoId     the id of the SSO session
    * @param principal the <code>Principal</code> returned by the latest
    *                  call to <code>Realm.authenticate</code>.
    * @param authType  the type of authenticator used (BASIC, CLIENT-CERT,
    *                  DIGEST or FORM)
    * @param username  the username (if any) used for the authentication
    * @param password  the password (if any) used for the authentication
    */
   void registerLocal(String ssoId, Principal principal, String authType,
      String username, String password)
   {
      if (getContainer().getLogger().isTraceEnabled())
      {
          getContainer().getLogger().trace("Registering sso id '" + ssoId + "' for user '" +
            principal.getName() + "' with auth type '" + authType + "'");
      }

      synchronized (cache)
      {
         cache.put(ssoId, new JBossSingleSignOnEntry(principal, authType,
            username, password));
      }
   }

   /**
    * Updates any <code>SingleSignOnEntry</code> found under key
    * <code>ssoId</code> with the given authentication data.
    *
    * @param ssoId     identifier of Single sign to be updated
    * @param principal the <code>Principal</code> returned by the latest
    *                  call to <code>Realm.authenticate</code>.
    * @param authType  the type of authenticator used (BASIC, CLIENT-CERT,
    *                  DIGEST or FORM)
    * @param username  the username (if any) used for the authentication
    * @param password  the password (if any) used for the authentication
    * @return <code>true</code> if the update resulted in an actual change
    *         to the entry's authType, username or principal properties
    */
   boolean updateLocal(String ssoId, Principal principal, String authType,
      String username, String password)
   {
      boolean shouldBroadcast = false;

      JBossSingleSignOnEntry sso = getSingleSignOnEntry(ssoId);
      // Only update if the entry is missing information
      if (sso != null)
      {
         if (sso.getCanReauthenticate() == false)
         {
            if (getContainer().getLogger().isTraceEnabled())
                getContainer().getLogger().trace("Update sso id " + ssoId + " to auth type " + authType);

            synchronized (sso)
            {
               shouldBroadcast = sso.updateCredentials2(principal, authType,
                  username, password);
            }
         }
         else if (sso.getPrincipal() == null && principal != null)
         {
            if (getContainer().getLogger().isTraceEnabled())
                getContainer().getLogger().trace("Update sso id " + ssoId + " with principal " +
                  principal.getName());

            synchronized (sso)
            {
               sso.setPrincipal(principal);
               // No need to notify cluster; Principals don't replicate
            }
         }

      }

      return shouldBroadcast;

   }

   public void remoteUpdate(String ssoId, SSOCredentials credentials)
   {
	  JBossSingleSignOnEntry sso = localLookup(ssoId);
      // Only update if the entry is missing information
      if (sso != null && sso.getCanReauthenticate() == false)
      {
         if (getContainer().getLogger().isTraceEnabled())
             getContainer().getLogger().trace("Update sso id " + ssoId + 
                   " to auth type " + credentials.getAuthType());

         synchronized (sso)
         {
            // Use the existing principal
            Principal p = sso.getPrincipal();
            sso.updateCredentials(p, credentials.getAuthType(), 
                                  credentials.getUsername(), 
                                  credentials.getPassword());
         }
      }

   }
   
   /**
    * Callback from the SSOManager when it detects an SSO without
    * any active sessions across the cluster
    */
   public void notifySSOEmpty(String ssoId)
   {
      Object obj = emptySSOs.put(ssoId, new Long(System.currentTimeMillis()));
      
      if (obj == null && getContainer().getLogger().isTraceEnabled())
      {
         getContainer().getLogger().trace("Notified that SSO " + ssoId + " is empty");
      }
   }
   
   /**
    * Callback from the SSOManager when it detects an SSO that
    * has active sessions across the cluster
    */
   public void notifySSONotEmpty(String ssoId)
   {
      Object obj = emptySSOs.remove(ssoId);
      
      if (obj != null && getContainer().getLogger().isTraceEnabled())
      {
         getContainer().getLogger().trace("Notified that SSO " + ssoId + 
                                          " is no longer empty");
      }
   }

   /**
    * Get the current Catalina MBean Server.
    * 
    * @return the mbean server
    */
   public MBeanServer getMBeanServer()
   {
      if (mserver == null)
      {
         mserver = Registry.getRegistry(null, null).getMBeanServer();
      }
      return mserver;
   }

   
   // -------------------------------------------------------  Private Methods

   
   /**
    * Instantiates an instance of the given class, making it this valve's
    * SSOClusterManager.
    * <p/>
    * If this valve has been started and the given class implements
    * <code>Lifecycle</code>, starts the new SSOClusterManager.
    *
    * @param className fully qualified class name of an implementation
    *                  of {@link SSOClusterManager SSOClusterManager}.
    * @throws LifecycleException if there is any problem instantiating or
    *                            starting the object, or if the created
    *                            object does not implement
    *                            <code>SSOClusterManger</code>
    */
   private void createClusterManager(String className)
      throws LifecycleException
   {
      if (ssoClusterManager != null)
         return;

      if (className != null)
      {
         SSOClusterManager mgr = null;
         try
         {
            ClassLoader tcl =
               Thread.currentThread().getContextClassLoader();
            Class clazz = tcl.loadClass(className);
            mgr = (SSOClusterManager) clazz.newInstance();
            mgr.setSSOLocalManager(this);
            ssoClusterManager = mgr;
            clusterManagerClass = className;
         }
         catch (Throwable t)
         {
            throw new LifecycleException("Cannot create " +
               "SSOClusterManager using " +
               className, t);
         }

         if (started)
         {
            try
            {
               ssoClusterManager.start();
            }
            catch (LifecycleException e)
            {
               throw e;
            }
            catch (Exception e)
            {
               throw new LifecycleException("Caught exception stopping " + 
                     ssoClusterManager.getClass().getSimpleName(), e);
            }
         }
      }
   }
   
   
   private void processExpires()
   {
      long now = 0L;
      synchronized (mutex)
      {
         now = System.currentTimeMillis();
      
         if (now - lastProcessExpires > processExpiresInterval)
         {
            lastProcessExpires = now;
         }
         else
         {
             return;
         }
      }
      
      clearExpiredSSOs(now);
   }
   
   private synchronized void clearExpiredSSOs(long now)
   {      
      for (Iterator iter = emptySSOs.entrySet().iterator(); iter.hasNext();)
      {
         Map.Entry entry = (Map.Entry) iter.next();
         if ( (now - ((Long) entry.getValue()).longValue()) > maxEmptyLife)
         {
            String ssoId = (String) entry.getKey();
            if (getContainer().getLogger().isTraceEnabled())
            {
               getContainer().getLogger().trace("Invalidating expired SSO " + ssoId);
            }
            logout(ssoId);
         }
      }      
   }
   
   private boolean isValid(String ssoId, JBossSingleSignOnEntry entry)
   {
      boolean valid = true;
      if (entry.getSessionCount() == 0)
      {
         Long expired = (Long) emptySSOs.get(ssoId);
         if (expired != null 
               && (System.currentTimeMillis() - expired.longValue()) > maxEmptyLife)
         {
            valid = false;
            
            if (getContainer().getLogger().isTraceEnabled())
            {
               getContainer().getLogger().trace("Invalidating expired SSO " + ssoId);
            }
            
            logout(ssoId);
         }
      }
      
      return valid;
   }
   
   private FullyQualifiedSessionId getFullyQualifiedSessionId(Session session)
   {
      String id = session.getIdInternal();
      Container context = session.getManager().getContainer();
      String contextName = context.getName(); 
      Container host = context.getParent();
      String hostName = host.getName();
      
      return new FullyQualifiedSessionId(id, contextName, hostName);
   }

}
