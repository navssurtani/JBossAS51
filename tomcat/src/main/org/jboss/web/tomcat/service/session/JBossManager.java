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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.tomcat.util.http.TomcatCookie;
import org.apache.tomcat.util.modeler.Registry;
import org.jboss.logging.Logger;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.metadata.web.jboss.PassivationConfig;
import org.jboss.web.tomcat.service.session.distributedcache.spi.ClusteringNotSupportedException;
import org.jboss.web.tomcat.service.session.distributedcache.spi.OutgoingDistributableSessionData;
import org.jboss.web.tomcat.statistics.ReplicationStatistics;


/**
 * Base abstract implementation of Tomcat manager without the concept of
 * session operations, e.g., add, remove, etc.
 *
 * @author Ben Wang
 * @author Hany Mesha
 * @author Brian Stansberry
 * 
 * @version $Revision: 91181 $
 */
public abstract class JBossManager
   implements AbstractJBossManager, Lifecycle,
      JBossManagerMBean, PropertyChangeListener
{
   // ------------------------------------------------------------------ Fields
   
   protected ReplicationStatistics stats_ = new ReplicationStatistics();
   
   /**
    * Session passivation flag set in jboss-web.xml by the user.
    * If true, then the session passivation is enabled for this web application, 
    * otherwise, it's disabled
    */
   protected boolean passivationMode_ = false;
   
   /**
    * Min time (milliseconds) the session must be idle since lastAccesstime before 
    * it's eligible for passivation if passivation is enabled and more
    * than maxActiveAllowed_ sessions are in memory.
    * Setting to -1 means it's ignored.
    */
   protected long passivationMinIdleTime_ = -1;
   
   /**
    * Max time (milliseconds) the session must be idle since lastAccesstime before 
    * it will be passivated if passivation is enabled.
    * Setting to -1 means session should not be forced out.
    */
   protected long passivationMaxIdleTime_ = -1;
   
   /**
    * The lifecycle_ event support for this component.
    */
   protected LifecycleSupport lifecycle_ = new LifecycleSupport(this);
   
   /**
    * Has this component been started_ yet?
    */
   protected boolean started_ = false;
   
   /**
    * Are we allowing backgroundProcess() to execute? We use an object
    * so stop() can lock on it to wait for
    */
   protected AtomicBoolean backgroundProcessAllowed = new AtomicBoolean();
   
   /**
    * The objectname this Manager is associated with
    */
   protected ObjectName objectName_;
   
   /**
    * The Log object for this class
    */
   protected Logger log_ = Logger.getLogger(this.getClass().getName());  
   
   /**
    * Whether trace logging is enabled for our logger. Rechecked
    * every time backgroundProcess() is invoked.
    */
   protected boolean trace_ = log_.isTraceEnabled();   
   
   /**
    * The Container with which this Manager is associated.
    */
   protected Container container_;
   
  /**
   /**
    * The distributable flag for Sessions created by this Manager.  If this
    * flag is set to <code>true</code>, any user attributes added to a
    * session controlled by this Manager must be Serializable.
    */
   protected boolean distributable_ = true;
   
   /**
    * The default maximum inactive interval for Sessions created by
    * this Manager.
    */
   protected int maxInactiveInterval_ = 60;

   /** Maximum of active sessions allowed. -1 is unlimited. */
   protected int maxActiveAllowed_ = -1;

   /** Number of sessions created by this manager */
   protected AtomicInteger createdCounter_ = new AtomicInteger();

   /** number of sessions rejected because the number active sessions exceeds maxActive */
   protected AtomicInteger rejectedCounter_ = new AtomicInteger();

   /** Number of active sessions */
   protected AtomicInteger localActiveCounter_ = new AtomicInteger();
   
   /** Maximum number of concurrently locally active sessions */
   protected AtomicInteger maxLocalActiveCounter_ = new AtomicInteger();

   /** Maximum number of active sessions seen so far */
   protected AtomicInteger maxActiveCounter_ = new AtomicInteger();

   /** Number of sessions that have been active locally that are now expired. */
   protected AtomicInteger expiredCounter_ = new AtomicInteger();

   /** Number of ms since last call to reset() */
   protected long timeSinceLastReset_ = 0;

   /** Cumulative time spent in backgroundProcess */
   protected AtomicLong processingTime_ = new AtomicLong();
   
   /** Stores the locally active sessions. */
   protected final Map<String, ClusteredSession<? extends OutgoingDistributableSessionData>> sessions_ = 
      new ConcurrentHashMap<String,ClusteredSession<? extends OutgoingDistributableSessionData>>();

   /** The property change support for this component. */
   protected PropertyChangeSupport support_ = new PropertyChangeSupport(this);

   /** Generates ids for new sessions */
   protected SessionIDGenerator sessionIDGenerator_= SessionIDGenerator.getInstance();;

   /** Our containing engine's jvmRoute (if it has one) */
   protected String jvmRoute_;

   /** Our JMX Server */
   protected MBeanServer mserver_ = null;
   
   /** 
    * How often calls to backgroundProcess() should trigger 
    * expiration/passivation processing
    */
   protected volatile int processExpiresFrequency = 1;
   
   /**
    * How many times backgroundProcess() has been called since we last
    * processed expiration/passivation.
    */
   protected int backgroundProcessCount = 0;
   
   /** Maximum time in ms a now expired session has been alive */
   protected AtomicInteger maxAliveTime = new AtomicInteger();
   
   /** Average time in ms a now expired session has been alive */
   protected AtomicInteger averageAliveTime = new AtomicInteger();
   
   /** 
    * Number of times our session id generator has generated an id
    * that matches an existing session.
    */
   protected AtomicInteger duplicates_ = new AtomicInteger(); 
   
   // TODO Need a string manager to handle localization

   // ------------------------------------------------------------ Constructors
   
   /**
    * Creates a new JBossManager
    */
   protected JBossManager()
   {
   }

   // -------------------------------------------------------------- Properties
   
   public boolean getUseSessionPassivation()
   {
      return passivationMode_;      
   }
   
   public void setUseSessionPassivation(boolean enabled)
   {
      this.passivationMode_ = enabled;
   }
   
   public long getPassivationMinIdleTime()
   {
      return passivationMinIdleTime_;
   }

   public void setPassivationMinIdleTime(long passivationMinIdleTime)
   {
      this.passivationMinIdleTime_ = passivationMinIdleTime;
   }

   public long getPassivationMaxIdleTime()
   {
      return passivationMaxIdleTime_;
   }

   public void setPassivationMaxIdleTime(long passivationMaxIdleTime)
   {
      this.passivationMaxIdleTime_ = passivationMaxIdleTime;
   }
   
   // ---------------------------------------------------- AbstractJBossManager

   /**
    * {@inheritDoc}
    */
   public void init(String name, JBossWebMetaData webMetaData)
           throws ClusteringNotSupportedException
   {     
      if (webMetaData.getMaxActiveSessions() != null)
      {
         maxActiveAllowed_ = webMetaData.getMaxActiveSessions().intValue();
      }
      
      PassivationConfig pConfig = webMetaData.getPassivationConfig();
      if (pConfig != null)
      {
         if (pConfig.getUseSessionPassivation() != null)
         {
            setUseSessionPassivation(pConfig.getUseSessionPassivation().booleanValue());
            if (getUseSessionPassivation())
            {
               Integer min = pConfig.getPassivationMinIdleTime();
               if (min != null)
                  setPassivationMinIdleTime(min.intValue());
               Integer max = pConfig.getPassivationMaxIdleTime();
               if (max != null)
                  setPassivationMaxIdleTime(max.intValue());
            }
         }
      }
      
      log_.debug("init(): maxActiveSessions allowed is " + maxActiveAllowed_ +
         " and passivationMode is " + passivationMode_);
   }

   /**
    * {@inheritDoc}
    */
   public String getJvmRoute()
   {
      if (jvmRoute_ == null)
      {
         Engine e = getEngine();
         jvmRoute_= (e == null ? null : e.getJvmRoute());
      }
      return jvmRoute_;
   }

   /**
    * {@inheritDoc}
    */
   public void setNewSessionCookie(String sessionId, HttpServletResponse response)
   {
      if (response != null)
      {
         Context context = (Context) container_;
         Connector connector = ((Response) response).getConnector();
         if (context.getCookies())
         {
            // set a new session cookie
            TomcatCookie cookie = new TomcatCookie(Globals.SESSION_COOKIE_NAME, sessionId);
            // JBAS-6206. Configure cookie a la o.a.c.connector.Request.configureSessionCookie()
            cookie.setMaxAge(-1);
            if (context.getSessionCookie().getPath() != null)
            {
               cookie.setPath(context.getSessionCookie().getPath());
            }
            else
            {
               String contextPath = context.getEncodedPath();
               if ("".equals(contextPath))
               {
                  contextPath = "/";
               }
               cookie.setPath(contextPath);
            }
            if (context.getSessionCookie().getComment() != null)
            {
               cookie.setComment(context.getSessionCookie().getComment());
            }
            if (context.getSessionCookie().getDomain() != null)
            {
               cookie.setDomain(context.getSessionCookie().getDomain());
            }
            if (context.getSessionCookie().isHttpOnly())
            {
               cookie.setHttpOnly(true);
            }
            if (context.getSessionCookie().isSecure())
            {
               cookie.setSecure(true);
            }
            if (connector.getSecure())
            {
               cookie.setSecure(true);
            }

            if (trace_)
            {
               log_.trace("Setting cookie with session id:" + sessionId + " & name:" + Globals.SESSION_COOKIE_NAME);
            }

            response.addCookie(cookie);
         }
      }
   }
   
   // ----------------------------------------------------------------- Manager

   /**
    * {@inheritDoc}
    */
   public void addPropertyChangeListener(PropertyChangeListener listener)
   {
      support_.addPropertyChangeListener(listener);
   }

   /**
    * {@inheritDoc}
    */
   public void removePropertyChangeListener(PropertyChangeListener listener)
   {
      support_.removePropertyChangeListener(listener);
   }

   /**
    * {@inheritDoc}
    */
   public void propertyChange(PropertyChangeEvent evt)
   {
      support_.firePropertyChange(evt);
   }

   /**
    * {@inheritDoc}
    */
   public void backgroundProcess()
   {
      // Always reset trace_
      trace_ = log_.isTraceEnabled();
      
      // For other work, only execute every processExpiresFrequency
      backgroundProcessCount = (backgroundProcessCount + 1) % processExpiresFrequency;
      if (backgroundProcessCount != 0)
         return;
      
      synchronized (backgroundProcessAllowed)
      {
         if (backgroundProcessAllowed.get())
         {
            long start = System.currentTimeMillis();
            
            processExpirationPassivation();
            
            long elapsed = System.currentTimeMillis() - start;
            
            processingTime_.addAndGet(elapsed);
         }
      }
   }
   
   /**
    * {@inheritDoc}
    */
   public int getActiveSessions()
   {
      return (int) getLocalActiveSessionCount();
   }

   /**
    * {@inheritDoc}
    */
   public Container getContainer()
   {
      return container_;
   }

   /**
    * {@inheritDoc}
    */
   public void setContainer(Container container)
   {
      // De-register from the old Container (if any)
      if ((this.container_ != null) && (this.container_ instanceof Context))
         this.container_.removePropertyChangeListener(this);

      // Default processing provided by our superclass
      this.container_ = container;

      // Register with the new Container (if any)
      if ((this.container_ != null) && (this.container_ instanceof Context))
      {
         setMaxInactiveInterval
            (((Context) this.container_).getSessionTimeout() * 60);
         this.container_.addPropertyChangeListener(this);
      }
   }

   /**
    * {@inheritDoc}
    */
   public boolean getDistributable()
   {
      return distributable_;
   }

   /**
    * {@inheritDoc}
    */
   public void setDistributable(boolean distributable)
   {
      this.distributable_ = distributable;
   }

   /**
    * {@inheritDoc}
    */
   public int getExpiredSessions()
   {
      return expiredCounter_.get();
   }

   /** No-op */
   public void setExpiredSessions(int expiredSessions)
   {
      // ignored
   }

   /**
    * {@inheritDoc}
    */
   public int getMaxActive()
   {
      return maxActiveAllowed_;
   }

   /**
    * {@inheritDoc}
    */
   public void setMaxActive(int maxActive)
   {
      this.maxActiveAllowed_ = maxActive;
   }

   /**
    * {@inheritDoc}
    */
   public int getMaxInactiveInterval()
   {
      return maxInactiveInterval_;
   }

   /**
    * {@inheritDoc}
    */
   public void setMaxInactiveInterval(int interval)
   {
      this.maxInactiveInterval_ = interval;
   }
   
   /**
    * {@inheritDoc}
    */
   public long getProcessingTime()
   {
      return this.processingTime_.get();
   }

   /**
    * {@inheritDoc}
    */
   public int getRejectedSessions()
   {
      return rejectedCounter_.get();
   }

   /** No-op */
   public void setRejectedSessions(int rejectedSessions)
   {
      // ignored
   }

   /**
    * {@inheritDoc}
    */
   public int getSessionAverageAliveTime()
   {
       return averageAliveTime.get();
   }

   /** No-op */
   public void setSessionAverageAliveTime(int sessionAverageAliveTime)
   {
      // ignored
   }

   /**
    * {@inheritDoc}
    */
   public int getSessionCounter()
   {
      return createdCounter_.get();
   }

   /** No-op */
   public void setSessionCounter(int sessionCounter)
   {
      // ignored
   }

   /**
    * {@inheritDoc}
    */
   public int getSessionIdLength()
   {
      return SessionIDGenerator.SESSION_ID_BYTES;
   }

   /** No-op */
   public void setSessionIdLength(int idLength)
   {
      // ignored
   }
   
   /**
    * {@inheritDoc}
    */
   public int getSessionMaxAliveTime()
   {
       return maxAliveTime.get();
   }

   /** No-op */
   public void setSessionMaxAliveTime(int sessionAliveTime)
   {
      // ignored
   }

   /** Throws UnsupportedOperationException */
   public void load() throws ClassNotFoundException, IOException
   {
      throw new UnsupportedOperationException("load() not supported");
   }

   /** Throws UnsupportedOperationException */
   public void unload() throws IOException
   {
      throw new UnsupportedOperationException("unload() not supported");
   }

   // -------------------------------------------------------------- Lifecycle

   public void addLifecycleListener(LifecycleListener listener)
   {
      lifecycle_.addLifecycleListener(listener);
   }

   public LifecycleListener[] findLifecycleListeners()
   {
      return lifecycle_.findLifecycleListeners();
   }

   public void removeLifecycleListener(LifecycleListener listener)
   {
      lifecycle_.removeLifecycleListener(listener);
   }

   /**
    * Start this Manager
    *
    * @throws org.apache.catalina.LifecycleException
    *
    */
   public void start() throws LifecycleException
   {
      startManager();
   }

   /**
    * Stop this Manager
    *
    * @throws org.apache.catalina.LifecycleException
    *
    */
   public void stop() throws LifecycleException
   {
      // Block for any ongoing backgroundProcess, then disable
      synchronized (backgroundProcessAllowed)
      {
         backgroundProcessAllowed.set(false);
      }
      
      resetStats();
      stopManager();
   }

   // ------------------------------------------------------- JBossManagerMBean

   /**
    * {@inheritDoc}
    */
   public long getActiveSessionCount()
   {
      return calcActiveSessions();
   }

   /**
    * {@inheritDoc}
    */
   public String getAlgorithm()
   {
      return SessionIDGenerator.SESSION_ID_HASH_ALGORITHM;
   }

   /**
    * {@inheritDoc}
    */
   public String getClassName()
   {
      return getClass().getName();
   }

   /**
    * {@inheritDoc}
    */
   public long getCreatedSessionCount()
   {
      return createdCounter_.get();
   }

   /**
    * {@inheritDoc}
    */
   public long getExpiredSessionCount()
   {
      return expiredCounter_.get();
   }

   /**
    * {@inheritDoc}
    */
   public long getLocalActiveSessionCount()
   {
      return localActiveCounter_.get();
   }

   /**
    * {@inheritDoc}
    */
   public int getMaxActiveAllowed()
   {
      return getMaxActive();
   }
   
   /**
    * {@inheritDoc}
    */
   public void setMaxActiveAllowed(int maxActive)
   {
      setMaxActive(maxActive);
   }

   /**
    * {@inheritDoc}
    */
   public int getMaxActiveSessions()
   {
      return getMaxActiveAllowed();
   }
   
   /**
    * {@inheritDoc}
    */
   public long getMaxActiveSessionCount()
   {
      return this.maxActiveCounter_.get();
   }

   /**
    * {@inheritDoc}
    */
   public long getMaxLocalActiveSessionCount()
   {
      return maxLocalActiveCounter_.get();
   }

   /**
    * {@inheritDoc}
    */
   public String getName()
   {
      return getClass().getSimpleName();
   }

   /**
    * {@inheritDoc}
    */
   public int getProcessExpiresFrequency()
   {
      return this.processExpiresFrequency;
   }

   /**
    * {@inheritDoc}
    */
   public void setProcessExpiresFrequency(int frequency)
   {
      this.processExpiresFrequency = frequency;      
   }

   /**
    * {@inheritDoc}
    */
   public long getRejectedSessionCount()
   {
      return rejectedCounter_.get();
   }

   /**
    * {@inheritDoc}
    */
   public ReplicationStatistics getReplicationStatistics()
   {
      return stats_;
   }

   /**
    * {@inheritDoc}
    */
   public long getTimeSinceLastReset()
   {
      return (System.currentTimeMillis() - timeSinceLastReset_) / (1000L);
   }

   /**
    * {@inheritDoc}
    */
   public String reportReplicationStatistics()
   {
      StringBuffer tmp = new StringBuffer();
      tmp.append("<table><tr>");
      tmp.append("<th>sessionID</th>");
      tmp.append("<th>replicationCount</th>");
      tmp.append("<th>minPassivationTime</th>");
      tmp.append("<th>maxPassivationTime</th>");
      tmp.append("<th>totalPassivationTime</th>");
      tmp.append("<th>minReplicationTime</th>");
      tmp.append("<th>maxReplicationTime</th>");
      tmp.append("<th>totalReplicationlTime</th>");
      tmp.append("<th>loadCount</th>");
      tmp.append("<th>minLoadTime</th>");
      tmp.append("<th>maxLoadTime</th>");
      tmp.append("<th>totalLoadTime</th>");
      
      Map<String, ReplicationStatistics.TimeStatistic> copy = new HashMap<String, ReplicationStatistics.TimeStatistic>(stats_.getStats());
      for (Map.Entry<String, ReplicationStatistics.TimeStatistic> entry : copy.entrySet())
      {
         ReplicationStatistics.TimeStatistic stat = (ReplicationStatistics.TimeStatistic) entry.getValue();
         if (stat != null)
         {
            tmp.append("<tr><td>");
            tmp.append(entry.getKey());
            tmp.append("</td><td>");
            tmp.append(stat.replicationCount);
            tmp.append("</td><td>");
            tmp.append(stat.minPassivationTime);
            tmp.append("</td><td>");
            tmp.append(stat.maxPassivationTime);
            tmp.append("</td><td>");
            tmp.append(stat.totalPassivationTime);
            tmp.append("</td><td>");
            tmp.append(stat.minReplicationTime);
            tmp.append("</td><td>");
            tmp.append(stat.maxReplicationTime);
            tmp.append("</td><td>");
            tmp.append(stat.totalReplicationlTime);
            tmp.append("</td><td>");
            tmp.append(stat.loadCount);
            tmp.append("</td><td>");
            tmp.append(stat.minLoadTime);
            tmp.append("</td><td>");
            tmp.append(stat.maxLoadTime);
            tmp.append("</td><td>");
            tmp.append(stat.totalLoadlTime);
            tmp.append("</td></tr>");
         }
      }
      tmp.append("</table>");
      copy.clear();
      return tmp.toString();

   }
   
   /**
    * {@inheritDoc}
    */
   public String reportReplicationStatisticsCSV()
   {
      StringBuffer tmp = createCSVHeader();
      Map<String, ReplicationStatistics.TimeStatistic> copy = new HashMap<String, ReplicationStatistics.TimeStatistic>(stats_.getStats());
      for (Map.Entry<String, ReplicationStatistics.TimeStatistic> entry : copy.entrySet())
      {
         ReplicationStatistics.TimeStatistic stat = (ReplicationStatistics.TimeStatistic) entry.getValue();
         if (stat != null)
         {
            tmp.append("\n");
            tmp.append(entry.getKey());
            tmp.append(",");
            tmp.append(stat.replicationCount);
            tmp.append(",");
            tmp.append(stat.minPassivationTime);
            tmp.append(",");
            tmp.append(stat.maxPassivationTime);
            tmp.append(",");
            tmp.append(stat.totalPassivationTime);
            tmp.append(",");
            tmp.append(stat.minReplicationTime);
            tmp.append(",");
            tmp.append(stat.maxReplicationTime);
            tmp.append(",");
            tmp.append(stat.totalReplicationlTime);
            tmp.append(",");
            tmp.append(stat.loadCount);
            tmp.append(",");
            tmp.append(stat.minLoadTime);
            tmp.append(",");
            tmp.append(stat.maxLoadTime);
            tmp.append(",");
            tmp.append(stat.totalLoadlTime);
         }
      }
      copy.clear();
      return tmp.toString();

   }
   
   /**
    * {@inheritDoc}
    */
   public String reportReplicationStatisticsCSV(String sessionId)
   {
      StringBuffer tmp = createCSVHeader();
      Map<String, ReplicationStatistics.TimeStatistic> stats = stats_.getStats();
      ReplicationStatistics.TimeStatistic stat = 
         (ReplicationStatistics.TimeStatistic) stats.get(sessionId);
      if (stat != null)
      {
         tmp.append("\n");
         tmp.append(sessionId);
         tmp.append(",");
         tmp.append(stat.replicationCount);
         tmp.append(",");
         tmp.append(stat.minPassivationTime);
         tmp.append(",");
         tmp.append(stat.maxPassivationTime);
         tmp.append(",");
         tmp.append(stat.totalPassivationTime);
         tmp.append(",");
         tmp.append(stat.minReplicationTime);
         tmp.append(",");
         tmp.append(stat.maxReplicationTime);
         tmp.append(",");
         tmp.append(stat.totalReplicationlTime);
         tmp.append(",");
         tmp.append(stat.loadCount);
         tmp.append(",");
         tmp.append(stat.minLoadTime);
         tmp.append(",");
         tmp.append(stat.maxLoadTime);
         tmp.append(",");
         tmp.append(stat.totalLoadlTime);
      }
      return tmp.toString();
   }

   /**
    * {@inheritDoc}
    */
   public void resetStats()
   {
      stats_.resetStats();
      maxActiveCounter_.set(localActiveCounter_.get());
      rejectedCounter_.set(0);
      createdCounter_.set(0);
      expiredCounter_.set(0);
      processingTime_.set(0);
      maxAliveTime.set(0);
      averageAliveTime.set(0);
      duplicates_.set(0);
      timeSinceLastReset_ = System.currentTimeMillis();
   }

   // ------------------------------------------------------------------ Public

   /**
    * Gets the JMX <code>ObjectName</code> under
    * which our <code>TreeCache</code> is registered. 
    */
   public ObjectName getObjectName()
   {
      return objectName_;
   }

   // ------------------------------------------------------------------ Protected

   /**
    * Go through all sessions and look if they have expired or need to be passivated.
    */
   protected abstract void processExpirationPassivation(); 
   
   /** Get the total number of active sessions */
   protected abstract int getTotalActiveSessions();
   
   /** 
    * Calculates the number of active sessions, and updates
    * the max # of local active sessions and max # of sessions.
    * <p>
    * Call this method when a new session is added or when an
    * accurate count of active sessions is needed.
    * </p>
    * 
    * @return the size of the sessions map + the size of the unloaded sessions 
    *         map - the count of passivated sessions
    */
   protected int calcActiveSessions()
   {
      localActiveCounter_.set(sessions_.size());
      int active = localActiveCounter_.get();
      int maxLocal = maxLocalActiveCounter_.get();
      while (active > maxLocal)
      {
         if (!maxLocalActiveCounter_.compareAndSet(maxLocal, active))
         {
            maxLocal = maxLocalActiveCounter_.get();
         }
      }
      
      int count = getTotalActiveSessions();
      int max = maxActiveCounter_.get();
      while (count > max)
      {
         if (!maxActiveCounter_.compareAndSet(max, count))
         {
            max = maxActiveCounter_.get();
            // Something changed, so reset our count
            count = getTotalActiveSessions();
         }
      }
      return count;
   }

   /**
    * Returns the given session if it is being actively managed by this manager.
    * An actively managed session is on that was either created on this server,
    * brought into local management by a call to
    * {@link #findLocalSession(String)} or brought into local management by a
    * call to {@link #findSessions()}.
    *
    * @param realId the session id, with any trailing jvmRoute removed.
    *
    * @see #getRealId(String)
    */
   protected ClusteredSession<? extends OutgoingDistributableSessionData> findLocalSession(String realId)
   {
      return sessions_.get(realId);
   }

   /**
    * Returns all the sessions that are being actively managed by this manager.
    * This includes those that were created on this server, those that were
    * brought into local management by a call to
    * {@link #findLocalSession(String)} as well as all sessions brought into
    * local management by a call to {@link #findSessions()}.
    */
   protected ClusteredSession<? extends OutgoingDistributableSessionData>[] findLocalSessions()
   {
      Collection<ClusteredSession<? extends OutgoingDistributableSessionData>> coll = sessions_.values();
      @SuppressWarnings("unchecked")
      ClusteredSession<? extends OutgoingDistributableSessionData>[] sess = new ClusteredSession[coll.size()];
      return coll.toArray(sess);
   }

   /**
    * Get a new session-id from the distributed store
    *
    * @return new session-id
    */
   protected String getNextId()
   {
      return sessionIDGenerator_.getSessionId();
   }
   
   /**
    * Updates statistics to reflect that a session with a given "alive time"
    * has been expired.
    * 
    * @param sessionAliveTime number of ms from when the session was created
    *                         to when it was expired.
    */
   protected void sessionExpired(int sessionAliveTime)
   {
      int current = maxAliveTime.get();
      while (sessionAliveTime > current)
      {
         if (maxAliveTime.compareAndSet(current, sessionAliveTime))
            break;
         else
            current = maxAliveTime.get();
      }
      
      expiredCounter_.incrementAndGet();
      int newAverage;
      do
      {
         int expCount = expiredCounter_.get();
         current = averageAliveTime.get();
         newAverage = ((current * (expCount - 1)) + sessionAliveTime)/expCount;
      }
      while (averageAliveTime.compareAndSet(current, newAverage) == false);
   }
   
   /**
    * Register this Manager with JMX.
    */
   protected void registerManagerMBean() 
   {
      try
      {
         MBeanServer server = getMBeanServer();

         String domain;
         if (container_ instanceof ContainerBase)
         {
            domain = ((ContainerBase) container_).getDomain();
         }
         else
         {
            domain = server.getDefaultDomain();
         }
         String hostName = ((Host) container_.getParent()).getName();
         hostName = (hostName == null) ? "localhost" : hostName;
         ObjectName clusterName = new ObjectName(domain
               + ":type=Manager,host=" + hostName + ",path="
               + ((Context) container_).getPath());

         if (server.isRegistered(clusterName))
         {
            log_.warn("MBean " + clusterName + " already registered");
            return;
         }

         objectName_ = clusterName;
         server.registerMBean(this, clusterName);

      }
      catch (Exception ex)
      {
         log_.error("Could not register " + getClass().getSimpleName() + " to MBeanServer", ex);
      }
   }

   /**
    * Unregister this Manager from the JMX server.
    */
   protected void unregisterManagerMBean()
   {
      if (mserver_ != null && objectName_ != null)
      {
         try
         {
            mserver_.unregisterMBean(objectName_);
         }
         catch (Exception e)
         {
            log_.error("Could not unregister " + getClass().getSimpleName() + " from MBeanServer", e);
         }
      }
   }

   /**
    * Get the current MBean Server.
    * 
    * @return
    * @throws Exception
    */
   protected MBeanServer getMBeanServer() throws Exception
   {
      if (mserver_ == null)
      {
         mserver_ = Registry.getRegistry(null, null).getMBeanServer();
      }
      return mserver_;
   }

   /**
    * Prepare for the beginning of active use of the public methods of this
    * component.  This method should be called after <code>configure()</code>,
    * and before any of the public methods of the component are utilized.
    *
    * @throws IllegalStateException if this component has already been
    *                               started_
    * @throws org.apache.catalina.LifecycleException
    *                               if this component detects a fatal error
    *                               that prevents this component from being used
    */
   protected void startManager() throws LifecycleException
   {
      log_.debug("Starting JBossManager");

      // Validate and update our current component state
      if (started_)
         throw new LifecycleException("JBossManager alreadyStarted");
      
      backgroundProcessAllowed.set(true);
      
      lifecycle_.fireLifecycleEvent(START_EVENT, null);
      started_ = true;

      // register ClusterManagerMBean to the MBeanServer
      registerManagerMBean();
   }

   /**
    * Gracefully terminate the active use of the public methods of this
    * component.  This method should be the last one called on a given
    * instance of this component.
    *
    * @throws IllegalStateException if this component has not been started_
    * @throws org.apache.catalina.LifecycleException
    *                               if this component detects a fatal error
    *                               that needs to be reported
    */
   protected void stopManager() throws LifecycleException
   {
      log_.debug("Stopping JBossManager");

      // Validate and update our current component state
      if (!started_)
         throw new LifecycleException
            ("JBossManager notStarted");
      lifecycle_.fireLifecycleEvent(STOP_EVENT, null);
      started_ = false;

      // unregister from the MBeanServer
      unregisterManagerMBean();
   }

   // ----------------------------------------------------------------- Private
   
   private StringBuffer createCSVHeader()
   {
      StringBuffer tmp = new StringBuffer();
      tmp.append("sessionID,");
      tmp.append("replicationCount,");
      tmp.append("minPassivationTime,");
      tmp.append("maxPassivationTime,");
      tmp.append("totalPassivationTime,");
      tmp.append("minReplicationTime,");
      tmp.append("maxReplicationTime,");
      tmp.append("totalReplicationlTime,");
      tmp.append("loadCount,");
      tmp.append("minLoadTime,");
      tmp.append("maxLoadTime,");
      tmp.append("totalLoadTime");
      
      return tmp;
   }

   /**
    * Retrieve the enclosing Engine for this Manager.
    *
    * @return an Engine object (or null).
    */
   private Engine getEngine()
   {
      Engine e = null;
      for (Container c = getContainer(); e == null && c != null; c = c.getParent())
      {
         if (c != null && c instanceof Engine)
         {
            e = (Engine) c;
         }
      }
      return e;
   }

}
