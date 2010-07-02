/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.resource.connectionmanager;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.security.auth.Subject;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.jboss.deployers.spi.DeploymentException;
import org.jboss.logging.Logger;
import org.jboss.managed.api.ManagedOperation.Impact;
import org.jboss.managed.api.annotation.ManagementObject;
import org.jboss.managed.api.annotation.ManagementObjectID;
import org.jboss.managed.api.annotation.ManagementOperation;
import org.jboss.managed.api.annotation.ManagementParameter;
import org.jboss.managed.api.annotation.ManagementProperties;
import org.jboss.managed.api.annotation.ManagementProperty;
import org.jboss.managed.api.annotation.ViewUse;
import org.jboss.mx.util.JMXExceptionDecoder;
import org.jboss.resource.JBossResourceException;
import org.jboss.resource.connectionmanager.InternalManagedConnectionPool.PoolParams;
import org.jboss.resource.statistic.JBossStatistics;
import org.jboss.resource.statistic.StatisticsReporter;
import org.jboss.resource.statistic.formatter.StatisticsFormatter;
import org.jboss.resource.statistic.pool.JBossDefaultSubPoolStatisticFormatter;
import org.jboss.resource.statistic.pool.JBossManagedConnectionPoolStatistics;
import org.jboss.resource.statistic.pool.JBossSubPoolStatistics;
import org.jboss.resource.statistic.pool.ManagedConnectionPoolStatistics;
import org.jboss.system.ServiceMBeanSupport;
import org.jboss.tm.TransactionLocal;

/**
 * The JBossManagedConnectionPool mbean configures and supplies pooling of
 * JBossConnectionEventListeners to the BaseConnectionManager2 mbean.<p>
 *   
 * It may be replaced by any mbean with a readable ManagedConnectionPool attribute
 * of type ManagedConnectionPool.  Normal pooling parameters are supplied,
 * and the criteria to distinguish ManagedConnections is set in the Criteria attribute.
 *
 * @author <a href="mailto:d_jencks@users.sourceforge.net">David Jencks</a>
 * @author <a href="mailto:adrian@jboss.org">Adrian Brock</a>
 * @author <a href="mailto:weston.price@jboss.com">Weston Price</a>
 * @author <a href="mailto:jesper.pedersen@jboss.org">Jesper Pedersen</a>
 * 
 * @version $Revision: 102072 $
 */
@ManagementObject(isRuntime=true, properties=ManagementProperties.EXPLICIT)
public class JBossManagedConnectionPool extends ServiceMBeanSupport 
   implements JBossManagedConnectionPoolMBean, NotificationListener
{
   
   static Logger log = Logger.getLogger(JBossManagedConnectionPool.class);
   
   /** The managed connection factory name */
   private ObjectName managedConnectionFactoryName;

   /** The pooling criteria */
   private String criteria;

   /** The pooling strategy */
   private ManagedConnectionPool poolingStrategy;

   /** The pooling parameters */
   private final InternalManagedConnectionPool.PoolParams poolParams = new InternalManagedConnectionPool.PoolParams();

   /** Whether to use separate pools for transactional and non-transaction use */
   private boolean noTxSeparatePools;

   /** The statisticsFormatter 
    *
    * */
   private String statisticsFormatter;
   /** The mcf jndi name */
   private String poolJndiName;

   public JBossManagedConnectionPool()
   {
   }
   
   @ManagementOperation(description="Obtain a formatted statistics report",
         impact=Impact.ReadOnly,
         params={@ManagementParameter(name="formatClassName",description="The StatisticsFormatter class name")})
   public Object listFormattedSubPoolStatistics(String formatClassName)
   {
      final JBossStatistics stats = (JBossStatistics)listStatistics();
      final ClassLoader cl = Thread.currentThread().getContextClassLoader();
      Class clazz;
      StatisticsFormatter formatter = null;
      
      try
      {
         clazz = cl.loadClass(formatClassName);
         formatter = (StatisticsFormatter)clazz.newInstance();
      }
      catch (Exception e)
      {
         log.warn("warn: statistics formatter not found, setting to " + statisticsFormatter);
         formatter = new JBossDefaultSubPoolStatisticFormatter();
         
      }
   
      return formatter.formatStatistics(stats);      
   }

   @ManagementOperation(description="Obtain a formatted statistics report",
         impact=Impact.ReadOnly)
   public Object listFormattedSubPoolStatistics()
   {
      
      Object formatted = listFormattedSubPoolStatistics(statisticsFormatter);
      return formatted;  
   }
   
   @ManagementOperation(description="Obtain a statistics report",
         impact=Impact.ReadOnly)
   public Object listStatistics()
   {
      ManagedConnectionPoolStatistics stats = null;
      
      if (poolingStrategy instanceof StatisticsReporter)
      {
         
         StatisticsReporter reporter = (StatisticsReporter) poolingStrategy;
         stats = (ManagedConnectionPoolStatistics)reporter.listStatistics();
         stats.setCriteria(getCriteria());
         stats.setName(getManagedConnectionFactoryName().toString());
         
      }
      
      return stats;      
   }
   
   public Object listUnderlyingNativeConnectionStatistics()
   {
       return poolingStrategy.listUnderlyingNativeConnectionStatistics();
   }
 
   public ManagedConnectionPool getManagedConnectionPool()
   {
      return poolingStrategy;
   }

   public ObjectName getManagedConnectionFactoryName()
   {
      return managedConnectionFactoryName;
   }

   public void setManagedConnectionFactoryName(ObjectName newManagedConnectionFactoryName)
   {
      this.managedConnectionFactoryName = newManagedConnectionFactoryName;
   }

   @ManagementProperty(use={ViewUse.STATISTIC}, description="number of available connection")
   public long getAvailableConnectionCount()
   {
      return (poolingStrategy == null) ? 0 : poolingStrategy.getAvailableConnectionCount();
   }

   @ManagementProperty(use={ViewUse.STATISTIC}, description="number of maximum connections in use")
   public long getMaxConnectionsInUseCount()
   {
      return (poolingStrategy == null) ? 0 : poolingStrategy.getMaxConnectionsInUseCount();
   }

   @ManagementProperty(use={ViewUse.STATISTIC}, description="number of connections currently in use")
   public long getInUseConnectionCount ()
   {
      return (poolingStrategy == null) ? 0 : poolingStrategy.getInUseConnectionCount();
   }

   @ManagementProperty(use={ViewUse.STATISTIC})
   public int getMinSize()
   {
      return poolParams.minSize;
   }

   public void setMinSize(int newMinSize)
   {
      poolParams.minSize = newMinSize;
   }

   @ManagementProperty(use={ViewUse.STATISTIC})
   public int getMaxSize()
   {
      return poolParams.maxSize;
   }

   public void setMaxSize(int newMaxSize)
   {
      poolParams.maxSize = newMaxSize;
   }

   public int getBlockingTimeoutMillis()
   {
      return poolParams.blockingTimeout;
   }

   public void setBlockingTimeoutMillis(int newBlockingTimeout)
   {
      poolParams.blockingTimeout = newBlockingTimeout;
   }

   public long getIdleTimeoutMinutes()
   {
      return poolParams.idleTimeout / (1000 * 60);
   }

   public void setIdleTimeoutMinutes(long newIdleTimeoutMinutes)
   {
      poolParams.idleTimeout = newIdleTimeoutMinutes * 1000 * 60;
   }

   /**
    * Get the IdleTimeout value.
    *
    * @return the IdleTimeout value.
    */
   public long getIdleTimeout()
   {
      return poolParams.idleTimeout;
   }

   /**
    * Set the IdleTimeout value.
    *
    * @param newIdleTimeout The new IdleTimeout value.
    */
   public void setIdleTimeout(long newIdleTimeout)
   {
      poolParams.idleTimeout = newIdleTimeout;
   }

   public String getCriteria()
   {
      return criteria;
   }

   public void setCriteria(String newCriteria)
   {
      this.criteria = newCriteria;
   }

   public boolean getNoTxSeparatePools()
   {
      return noTxSeparatePools;
   }

   public void setNoTxSeparatePools(boolean value)
   {
      this.noTxSeparatePools = value;
   }

   public boolean getPreFill(){
      
      return poolParams.prefill;
      
   }
   
   public void setPreFill(boolean prefill){
      
      poolParams.prefill = prefill;
   }
   
   public void setStrictMin(boolean strictMin)
   {
      poolParams.stictMin = strictMin;
      
   }
   
   public boolean getStrictMin()
   {
         
      return poolParams.stictMin;
      
   }
   
   public boolean getUseFastFail()
   {
      return this.poolParams.useFastFail;
   }
   
   public void setUseFastFail(boolean useFastFail)
   {
      this.poolParams.useFastFail = useFastFail;
   }

   @ManagementOperation(description="Flush the connections in the pool",
         impact=Impact.WriteOnly)
   public void flush()
   {
      if (poolingStrategy == null)
         throw new IllegalStateException("The connection pool is not started");

      poolingStrategy.flush();

      if (poolingStrategy instanceof PreFillPoolSupport)
      {
         final PreFillPoolSupport pfs = (PreFillPoolSupport) poolingStrategy;

         if (pfs.shouldPreFill())
            pfs.prefill(noTxSeparatePools);
      }
   }

   private boolean internalTestConnection(Subject subject)
   {
      boolean result = false;
         ConnectionListener cl = null;
         try
         {
            if (getAvailableConnectionCount() > 0)
            {
               cl = poolingStrategy.getConnection(null, subject, null);
               result = true;
            }
         }
         catch (Exception ignored)
         {
         }
         finally
         {
            if (cl != null)
            {
               try
               {
                  poolingStrategy.returnConnection(cl, false);
               }
               catch (ResourceException ire)
               {
               }
            }
         }
      return result;
   }


   /**
    * Test if a connection can be obtained using default values
    * @return True if a connection was obtained; otherwise false
    */
   @ManagementOperation(description="Test if a connection can be obtained",
         impact=Impact.WriteOnly)
   public boolean testConnection()
   {
      boolean result = false;
      ConnectionListener cl = null;
      // first try to get connection with Subject
      if (poolingStrategy instanceof BasePool)
      {
         BasePool basePool = (BasePool)poolingStrategy;
         if (basePool.clf instanceof BaseConnectionManager2)
         {
            try {
               BaseConnectionManager2 baseConnectionMgr = (BaseConnectionManager2)basePool.clf;
               Subject subject = baseConnectionMgr.getSubjectFactory().createSubject(baseConnectionMgr.getSecurityDomainJndiName());
               result = internalTestConnection(subject);
            }
            catch ( Exception ignored )  // createSubject could throw security exception, ignore it
            {
               
            }
         }
      }

      // then try to get connection without Subject
      if (!result)
      {
         result = internalTestConnection(null);
      }

      return result;
   }

   @ManagementProperty(use={ViewUse.STATISTIC})
   public int getConnectionCount()
   {
      return (poolingStrategy == null)? 0: poolingStrategy.getConnectionCount();
   }

   @ManagementProperty(use={ViewUse.STATISTIC})
   public int getConnectionCreatedCount()
   {
      return (poolingStrategy == null)? 0: poolingStrategy.getConnectionCreatedCount();
   }

   @ManagementProperty(use={ViewUse.STATISTIC})
   public int getConnectionDestroyedCount()
   {
      return (poolingStrategy == null)? 0: poolingStrategy.getConnectionDestroyedCount();
   }

   public String getName()
   {
      return "JBossManagedConnectionPool";
   }
   public String getStatisticsFormatter()
   {
      return statisticsFormatter;
   }

   public void setStatisticsFormatter(String statisticsFormatter)
   {
      this.statisticsFormatter = statisticsFormatter;
   }

   /**
    * The connection factory jndi name. This is used to tie the pool
    * ManagedObject back to the ManagedConnectionFactoryDeploymentMetaData
    * @return
    */
   @ManagementObjectID(type="DataSource")
   @ManagementProperty(use={ViewUse.RUNTIME})
   public String getPoolJndiName()
   {
      return this.poolJndiName;
   }

   public void setPoolJndiName(String poolName)
   {
      this.poolJndiName = poolName;
   }

   public long getBackGroundValidationMillis()
   {
      return poolParams.backgroundInterval;
   }

   public void setBackGroundValidationMillis(long backgroundValidationInterval)
   {

      poolParams.backgroundInterval = backgroundValidationInterval;
   }

   protected void startService() throws Exception
   {
      ManagedConnectionFactory mcf = null;
      
      if(managedConnectionFactoryName == null)
      {
         throw new org.jboss.deployers.spi.DeploymentException("ManagedConnectionFactory is not set.");          
      
      }
         
      try
      {
         //We are getting the actual mcf instance itself.  This will require
         //some work if the mcf is an xmbean of itself.
         mcf = (ManagedConnectionFactory)server.getAttribute(managedConnectionFactoryName, "McfInstance");
      }
      catch (Exception e)
      {
         JMXExceptionDecoder.rethrow(e);
      }

      getServer().addNotificationListener
      (
         managedConnectionFactoryName,
         this,
         new NotificationFilter()
         {
            private static final long serialVersionUID = -9211456539783257343L;

            public boolean isNotificationEnabled(Notification n)
            {
               return RARDeployment.MCF_ATTRIBUTE_CHANGED_NOTIFICATION.equals(n.getType())
                      && managedConnectionFactoryName.equals(n.getSource());
            }
         },
         null
      );

      if ("ByContainerAndApplication".equals(criteria))
         poolingStrategy = new PoolBySubjectAndCri(mcf, poolParams, noTxSeparatePools, this, log);
      else if ("ByContainer".equals(criteria))
         poolingStrategy = new PoolBySubject(mcf, poolParams, noTxSeparatePools, this, log);
      else if ("ByApplication".equals(criteria))
         poolingStrategy = new PoolByCri(mcf, poolParams, noTxSeparatePools, this, log);
      else if ("ByNothing".equals(criteria))
         poolingStrategy = new OnePool(mcf, poolParams, noTxSeparatePools, this, log);
      else
         throw new DeploymentException("Unknown pooling criteria: " + criteria); 
         
   }

   protected void stopService() throws Exception
   {
      if (poolingStrategy != null)
      {
         poolingStrategy.shutdown();
         
      }

      getServer().removeNotificationListener(managedConnectionFactoryName, this);
      poolingStrategy = null;
   }

   public void handleNotification(Notification notification, Object handback)
   {
      log.trace("Flushing pool due to notification from ManagedConnectionFactory" + notification);      
      flush();
   }

   ManagedConnectionPool getPoolingStrategy()
   {
      return poolingStrategy;
   }

   public static class SubPoolContext
   {
      /** The subpool */
      private InternalManagedConnectionPool subPool;
      
      /** The track by transaction transaction local */
      private TransactionLocal trackByTx;

      /**
       * Create a new SubPoolContext.
       * 
       * @param tm the transaction manager
       * @param mcf the managed connection factory
       * @param clf the connection listener factory
       * @param subject the subject
       * @param cri the connection request info
       * @param poolParams the pool parameters
       * @param jmcp the JBoss managed connection pool reference
       * @param log the log
       */
      public SubPoolContext(TransactionManager tm, ManagedConnectionFactory mcf, ConnectionListenerFactory clf, Subject subject,
                            ConnectionRequestInfo cri, PoolParams poolParams, JBossManagedConnectionPool jmcp, Logger log)
      {
         subPool = new InternalManagedConnectionPool(mcf, clf, subject, cri, poolParams, jmcp, log);
         if (tm != null)
            trackByTx = new TransactionLocal(tm);
      }
      
      /**
       * Get the sub pool
       * 
       * @return the sub pool
       */
      public InternalManagedConnectionPool getSubPool()
      {
         return subPool;
      }
      
      /**
       * Get the track by transaction
       * 
       * @return the transaction local
       */
      public TransactionLocal getTrackByTx()
      {
         return trackByTx;
      }
      
      /**
       * Initialize the subpool context
       */
      public void initialize()
      {
         subPool.initialize();
      }
   }
   
   /**
    * The base pool implementation
    */
   public abstract static class BasePool implements ManagedConnectionPool, StatisticsReporter, PreFillPoolSupport
   {
      /** The subpools */
      private final ConcurrentMap<Object, SubPoolContext> subPools = new ConcurrentHashMap<Object, SubPoolContext>();

      /** The managed connection factory */
      private final ManagedConnectionFactory mcf;
      
      /** The connection listener factory */
      private ConnectionListenerFactory clf;

      /** The pool parameters */
      private final InternalManagedConnectionPool.PoolParams poolParams;

      /** Whether to use separate pools for transactional and non-transaction use */
      private boolean noTxSeparatePools;

      /** The poolName */
      private String poolName;

      /** The JBoss managed connection pool */
      private final JBossManagedConnectionPool jmcp;

      /** The logger */
      private final Logger log;

      /** Is trace enabled */
      private boolean traceEnabled = false;

      /**
       * Create a new base pool
       * 
       * @param mcf the managed connection factory
       * @param poolParams the pooling parameters
       * @param log the log
       */
      public BasePool(final ManagedConnectionFactory mcf, final InternalManagedConnectionPool.PoolParams poolParams,
                      final boolean noTxSeparatePools, final JBossManagedConnectionPool jmcp, final Logger log)
      {
         this.mcf = mcf;
         this.poolParams = poolParams;
         this.noTxSeparatePools = noTxSeparatePools;
         this.jmcp = jmcp;
         this.log = log;
         this.traceEnabled = log.isTraceEnabled();
      }

      /**
       * Retrieve the key for this request
       * 
       * @param subject the subject 
       * @param cri the connection request information
       * @return the key
       * @throws ResourceException for any error
       */
      protected abstract Object getKey(Subject subject, ConnectionRequestInfo cri, boolean separateNoTx) throws ResourceException;
      
     
      public ManagedConnectionFactory getManagedConnectionFactory()
      {
         return mcf;
      }

      public void setConnectionListenerFactory(ConnectionListenerFactory clf)
      {
         this.clf = clf;
      }
     
      public ConnectionListener getConnection(Transaction trackByTransaction, Subject subject, ConnectionRequestInfo cri)
         throws ResourceException
      {
         // Determine the pool key for this request
         boolean separateNoTx = false;
         if (noTxSeparatePools)
            separateNoTx = clf.isTransactional();
         Object key = getKey(subject, cri, separateNoTx);
         SubPoolContext subPool = getSubPool(key, subject, cri);
         
         InternalManagedConnectionPool mcp = subPool.getSubPool();
         
         // Are we doing track by connection?
         TransactionLocal trackByTx = subPool.getTrackByTx();

         // Simple case
         if (trackByTransaction == null || trackByTx == null)
         {
            try
            {
               ConnectionListener cl = mcp.getConnection(subject, cri);
               if (traceEnabled)
                  dump("Got connection from pool " + cl);
               return cl;
            }
            catch (RetryableResourceException rre)
            {
               if (log.isDebugEnabled())
                  log.debug("Got a RetryableResourceException - trying to reinitialize the pool");

               // The IMCP is down - retry
               subPool = getSubPool(key, subject, cri);
               mcp = subPool.getSubPool();

               // Make sure that IMCP is running
               if (!mcp.isRunning())
                  mcp.initialize();

               ConnectionListener cl = mcp.getConnection(subject, cri);
               if (traceEnabled)
                  dump("Got connection from pool (retried) " + cl);

               return cl;
            }
         }

         // Track by transaction
         try
         {
            trackByTx.lock(trackByTransaction);
         }
         catch (Throwable t)
         {
            JBossResourceException.rethrowAsResourceException("Unable to get connection from the pool for tx=" + trackByTransaction, t);
         }
         try
         {
            // Already got one
            ConnectionListener cl = (ConnectionListener) trackByTx.get(trackByTransaction);
            if (cl != null)
            {
               if (traceEnabled)
                  dump("Previous connection tracked by transaction " + cl + " tx=" + trackByTransaction);
               return cl;
            }
         }
         finally
         {
            trackByTx.unlock(trackByTransaction);
         }

         // Need a new one for this transaction
         // This must be done outside the tx local lock, otherwise
         // the tx timeout won't work and get connection can do a lot of other work
         // with many opportunities for deadlocks.
         // Instead we do a double check after we got the transaction to see
         // whether another thread beat us to the punch.
         ConnectionListener cl = mcp.getConnection(subject, cri);
         if (traceEnabled)
            dump("Got connection from pool tracked by transaction " + cl + " tx=" + trackByTransaction);
         
         // Relock and check/set status
         try
         {
            trackByTx.lock(trackByTransaction);
         }
         catch (Throwable t)
         {
            mcp.returnConnection(cl, false);
            if (traceEnabled)
               dump("Had to return connection tracked by transaction " + cl + " tx=" + trackByTransaction + " error=" + t.getMessage());
            JBossResourceException.rethrowAsResourceException("Unable to get connection from the pool for tx=" + trackByTransaction, t);
         }
         try
         {
            // Check we weren't racing with another transaction
            ConnectionListener other = (ConnectionListener) trackByTx.get(trackByTransaction);
            if (other != null)
            {
               mcp.returnConnection(cl, false);
               if (traceEnabled)
                  dump("Another thread already got a connection tracked by transaction " + other + " tx=" + trackByTransaction);
               return other;
            }
            
            // This is the connection for this transaction
            cl.setTrackByTx(true);
            trackByTx.set(cl);
            if (traceEnabled)
               dump("Using connection from pool tracked by transaction " + cl + " tx=" + trackByTransaction);
            return cl;
         }
         finally
         {
            trackByTx.unlock(trackByTransaction);
         }
      }

      public void returnConnection(ConnectionListener cl, boolean kill) throws ResourceException
      {
         cl.setTrackByTx(false);
         InternalManagedConnectionPool mcp = (InternalManagedConnectionPool) cl.getContext();
         mcp.returnConnection(cl, kill);
         if (traceEnabled)
            dump("Returning connection to pool " + cl);
      }
      
      /**
       * Return the inuse count
       * 
       * @return the count
       */
      public int getInUseConnectionCount()
      {
         int count = 0;
         synchronized (subPools)
         {
            for (Iterator i = subPools.values().iterator(); i.hasNext(); )
            {
               SubPoolContext subPool = (SubPoolContext) i.next();
               count += subPool.getSubPool().getConnectionInUseCount();
            }
         }
         return count;
      }
      public boolean getPreFill()
      {
         return this.poolParams.prefill;

      }

      public boolean shouldPreFill()
      {
         return getPreFill();
      }

      public void prefill()
      {

         prefill(null, null, false);

      }

      public void prefill(boolean noTxSeperatePool)
      {

         prefill(null, null, noTxSeperatePool);

      }

      public void prefill(Subject subject, ConnectionRequestInfo cri, boolean noTxSeperatePool)
      {
         if (getPreFill())
         {

            log.debug("Attempting to prefill pool for pool with jndi name" + poolName);

            try
            {

               getSubPool(getKey(subject, cri, noTxSeparatePools), subject, cri);

            }
            catch (Throwable t)
            {
               //No real need to throw here being that pool remains in the same state as before.
               log.error("Unable to prefill pool with jndi name" + getPoolName(), t);

            }

         }

      }

      public int getConnectionCount()
      {
         int count = 0;
         synchronized (subPools)
         {
            for (Iterator i = subPools.values().iterator(); i.hasNext(); )
            {
               SubPoolContext subPool = (SubPoolContext) i.next();
               count += subPool.getSubPool().getConnectionCount();
            }
         }
         return count;
      }
      
      public String getPoolName()
      {
         return poolName;
      }
      public int getConnectionCreatedCount()
      {
         int count = 0;
         synchronized (subPools)
         {
            for (Iterator i = subPools.values().iterator(); i.hasNext(); )
            {
               SubPoolContext subPool = (SubPoolContext) i.next();
               count += subPool.getSubPool().getConnectionCreatedCount();
            }
         }
         return count;
      }

      public int getConnectionDestroyedCount()
      {
         int count = 0;
         synchronized (subPools)
         {
            for (Iterator i = subPools.values().iterator(); i.hasNext(); )
            {
               SubPoolContext subPool = (SubPoolContext) i.next();
               count += subPool.getSubPool().getConnectionDestroyedCount();
            }
         }
         return count;
      }

      public long getAvailableConnectionCount()
      {
         long count = 0;
         synchronized (subPools)
         {
            if (subPools.size() == 0)
               return poolParams.maxSize;
            for (Iterator i = subPools.values().iterator(); i.hasNext(); )
            {
               SubPoolContext subPool = (SubPoolContext) i.next();
               count += subPool.getSubPool().getAvailableConnections();
            }
         }
         return count;
      }

      public int getMaxConnectionsInUseCount()
      {
         int count = 0;
         synchronized (subPools)
         {
            for (Iterator i = subPools.values().iterator(); i.hasNext(); )
            {
               SubPoolContext subPool = (SubPoolContext) i.next();
               count += subPool.getSubPool().getMaxConnectionsInUseCount();
            }
         }
         return count;
      }
      
      public void shutdown()
      {
         synchronized (subPools)
         {
            for (Iterator i = subPools.values().iterator(); i.hasNext(); )
            {
               SubPoolContext subPool = (SubPoolContext) i.next();
               subPool.getSubPool().shutdown();
            }
            subPools.clear();
         }
      }

      public void flush()
      {
         synchronized (subPools)
         {
            for (Iterator i = subPools.values().iterator(); i.hasNext(); )
            {
               SubPoolContext subPool = (SubPoolContext) i.next();
               subPool.getSubPool().shutdown();
            }
            subPools.clear();
         }
      }

      /**
       * Remove the matching SubPoolContext if the pool is empty
       * @param pool The internal managed connection pool
       */
      public void emptySubPool(InternalManagedConnectionPool pool)
      {
         if (pool != null)
         {
            synchronized (subPools)
            {
               for (Iterator i = subPools.values().iterator(); i.hasNext(); )
               {
                  SubPoolContext subPoolContext = (SubPoolContext) i.next();
                  InternalManagedConnectionPool other = subPoolContext.getSubPool();
                  if (other == pool && pool.isEmpty())
                  {
                     pool.shutdown();
                     i.remove();
                     break;
                  }
               }
            }
         }
      }

      /**
       * For testing
       */
      protected void shutdownWithoutClear()
      {
         synchronized (subPools)
         {
            for (Iterator i = subPools.values().iterator(); i.hasNext(); )
            {
               SubPoolContext subPool = (SubPoolContext) i.next();
               subPool.getSubPool().shutdown();
            }
         }
      }
      
      /**
       * Get any transaction manager associated with the pool
       * 
       * @return the transaction manager
       */
      protected TransactionManager getTransactionManager()
      {
         if (clf != null)
            return clf.getTransactionManagerInstance();
         else
            return null;
      }

      /**
       * Determine the correct pool for this request,
       * creates a new one when necessary
       * 
       * @param key the key to the pool
       * @param subject the subject of the pool
       * @param cri the connection request info
       * @return the subpool context
       * @throws ResourceException for any error
       */
      protected SubPoolContext getSubPool(Object key, Subject subject, ConnectionRequestInfo cri) throws ResourceException
      {
         SubPoolContext subPool = (SubPoolContext)subPools.get(key);
         if (subPool == null)
         {
            SubPoolContext newSubPool = new SubPoolContext(getTransactionManager(), mcf, clf, subject, cri, poolParams, jmcp, log);
            subPool = subPools.putIfAbsent(key, newSubPool);
            if (subPool == null)
            {
               subPool = newSubPool;
               subPool.initialize();
            }
         }

         return subPool;
      }

      /**
       * Dump the stats to the trace log
       * 
       * @param info some context
       */
      private void dump(String info)
      {
         if (traceEnabled)
         {
            StringBuffer toLog = new StringBuffer(100);
            toLog.append(info).append(" [InUse/Available/Max]: [");
            toLog.append(this.getInUseConnectionCount()).append("/");
            toLog.append(this.getAvailableConnectionCount()).append("/");
            toLog.append(this.poolParams.maxSize);
            toLog.append("]");;
            log.trace(toLog);
         }
      }
         
      public JBossStatistics listStatistics()
      {   
         final ManagedConnectionPoolStatistics subPoolStats = new JBossManagedConnectionPoolStatistics(subPools.size());
         
         subPoolStats.setBlockingTimeout(poolParams.blockingTimeout);
         subPoolStats.setIdleTimeout(poolParams.idleTimeout);
         subPoolStats.setMax(poolParams.maxSize);
         subPoolStats.setMin(poolParams.minSize);
         subPoolStats.setPrefill(poolParams.prefill);
         subPoolStats.setNoTxnSeperatePool(noTxSeparatePools);
         
         for(Iterator iter = subPools.values().iterator(); iter.hasNext();)
         {   
            JBossSubPoolStatistics stat = new JBossSubPoolStatistics();
            SubPoolContext subContext = (SubPoolContext)iter.next();
            final InternalManagedConnectionPool internalPool = subContext.getSubPool();
            stat.setAvailableConnections(internalPool.getAvailableConnections());
            stat.setConnectionsDestroyed(internalPool.getConnectionDestroyedCount());
            stat.setConnectionsInUse(internalPool.getConnectionInUseCount());
            stat.setMaxConnectionsInUse(internalPool.getMaxConnectionsInUseCount());
            stat.setTotalBlockTime(internalPool.getTotalBlockTime());
            stat.setAverageBlockTime(internalPool.getAverageBlockTime());
            stat.setMaxWaitTime(internalPool.getMaxWaitTime());
            stat.setTotalTimedOut(internalPool.getTimedOut());
            subPoolStats.addSubPool(stat);
         }

         return (JBossStatistics)subPoolStats;
      }
      
      public Object listUnderlyingNativeConnectionStatistics()
      {
          String statistics = "";
          for(Iterator iter = subPools.values().iterator(); iter.hasNext();)
          {                      
              SubPoolContext subContext = (SubPoolContext)iter.next();
              InternalManagedConnectionPool internalPool = subContext.getSubPool();
              Set cels = internalPool.getConnectionListeners();
              for(Iterator celsIter = cels.iterator(); celsIter.hasNext();)
              {
                 ConnectionListener cl = (ConnectionListener) celsIter.next();
                 ManagedConnection mc = cl.getManagedConnection();
                 if (mc instanceof org.jboss.resource.statistic.JBossConnectionStatistics)
                 {
                    org.jboss.resource.statistic.JBossConnectionStatistics stats = (org.jboss.resource.statistic.JBossConnectionStatistics)mc;
                    statistics += stats.listConnectionStats();
                    if(statistics.startsWith("-1")){
                      statistics = " ManagedConnetion in a Pool does not expose NativeConnectionStatistics !!!... ";
                      break;
                    }
                 }
                 else
                 {
                     statistics = mc + " does not implement org.jboss.resource.statistic.JBossConnectionStatistics , <br><font color='red'>So this Operation is Not available!!!</font> ";                       
                     break;                      
                 }
              }
          }   
          return statistics;
      }
   }

   /**
    * Pooling by subject and connection request information
    */
   public static class PoolBySubjectAndCri
      extends BasePool
   {
      public PoolBySubjectAndCri(final ManagedConnectionFactory mcf,
                                 final InternalManagedConnectionPool.PoolParams poolParams,
                                 final boolean noTxSeparatePools, 
                                 final JBossManagedConnectionPool jmcp, 
                                 final Logger log)
      {
         super(mcf, poolParams, noTxSeparatePools, jmcp, log);
      }

      protected Object getKey(final Subject subject, final ConnectionRequestInfo cri, final boolean separateNoTx) throws ResourceException
      {
         return new SubjectCriKey(subject, cri, separateNoTx);
      }
   
      public void prefill()
      {
         prefill(null, null, false);
      }

      public void prefill(boolean noTxSeperatePool)
      {
         prefill(null, null, noTxSeperatePool);
      }

      public void prefill(Subject subject, ConnectionRequestInfo cri)
      {
         prefill(subject, cri, false);

      }

      public void prefill(Subject subject, ConnectionRequestInfo cri, boolean noTxSeperatePool)
      {
         if (getPreFill())
         {
            log.warn("Prefill pool option was selected for pool with JNDI name " + getPoolName()
                  + " that does not support this feature.");
            log
                  .warn("Please verify your *-ds.xml file that corresponds with this resource and either remove the <prefill>true|false</prefill element or explicitly set this value to false.");

         }
      }
   }

   /**
    * Pool by subject and criteria
    */
   private static class SubjectCriKey
   {
      /** Identifies no subject */
      private static final Subject NOSUBJECT = new Subject();
      
      /** Identifies no connection request information */
      private static final Object NOCRI = new Object();

      /** The subject */
      private final Subject subject;
      
      /** The connection request information */
      private final Object cri;
      
      /** The cached hashCode */
      private int hashCode = Integer.MAX_VALUE;

      /** Separate no tx */
      private boolean separateNoTx;

      SubjectCriKey(Subject subject, ConnectionRequestInfo cri, boolean separateNoTx)
      {
         this.subject = (subject == null)? NOSUBJECT:subject;
         this.cri = (cri == null)? NOCRI:cri;
         this.separateNoTx = separateNoTx;
      }

      public int hashCode()
      {
         if (hashCode == Integer.MAX_VALUE)
            hashCode = SubjectActions.hashCode(subject) ^ cri.hashCode();
         return hashCode;
      }

      public boolean equals(Object obj)
      {
         if (this == obj)
            return true;
         if (obj == null || (obj instanceof SubjectCriKey) == false)
            return false;
         SubjectCriKey other = (SubjectCriKey) obj;
         return SubjectActions.equals(subject, other.subject) 
            && cri.equals(other.cri)
            && separateNoTx == other.separateNoTx;
      }
   }

   /**
    * Pool by subject
    */
   public static class PoolBySubject
      extends BasePool
   {

      public PoolBySubject(final ManagedConnectionFactory mcf,
                           final InternalManagedConnectionPool.PoolParams poolParams,
                           final boolean noTxSeparatePools, 
                           final JBossManagedConnectionPool jmcp, 
                           final Logger log)
      {
         super(mcf, poolParams, noTxSeparatePools, jmcp, log);
      }

      protected Object getKey(final Subject subject, final ConnectionRequestInfo cri, boolean separateNoTx)
      {
         return new SubjectKey(subject, separateNoTx);
      }
   
      public void prefill()
      {
         prefill(null, null, false);
      }

      public void prefill(boolean noTxSeperatePool)
      {
         prefill(null, null, noTxSeperatePool);
      }

      public void prefill(Subject subject, ConnectionRequestInfo cri)
      {
         prefill(subject, cri, false);

      }

      public void prefill(Subject subject, ConnectionRequestInfo cri, boolean noTxSeperatePool)
      {
         if (getPreFill())
         {
            log.warn("Prefill pool option was selected for pool with JNDI name " + getPoolName()
                  + " that does not support this feature.");
            log
                  .warn("Please verify your *-ds.xml file that corresponds with this resource and either remove the <prefill>true|false</prefill element or explicitly set this value to false.");

         }
      }
   }

   /**
    * Pool by subject
    */
   private static class SubjectKey
   {
      /** Identifies no subject */
      private static final Subject NOSUBJECT = new Subject();

      /** The subject */
      private final Subject subject;

      /** Separate no tx */
      private boolean separateNoTx;
      
      /** The cached hashCode */
      private int hashCode = Integer.MAX_VALUE;

      SubjectKey(Subject subject, boolean separateNoTx)
      {
         this.subject = (subject == null)? NOSUBJECT:subject;
         this.separateNoTx = separateNoTx;
      }

      public int hashCode()
      {
         if (hashCode == Integer.MAX_VALUE)
            hashCode = SubjectActions.hashCode(subject);
         return hashCode;
      }

      public boolean equals(Object obj)
      {
         if (this == obj)
            return true;
         if (obj == null || (obj instanceof SubjectKey) == false)
            return false;
         SubjectKey other = (SubjectKey) obj;
         return SubjectActions.equals(subject, other.subject)
            && separateNoTx == other.separateNoTx;
      }
   }

   /**
    * Pool by connection request information
    */
   public static class PoolByCri
      extends BasePool
   {
      public PoolByCri(final ManagedConnectionFactory mcf,
                       final InternalManagedConnectionPool.PoolParams poolParams,
                       final boolean noTxSeparatePools, 
                       final JBossManagedConnectionPool jmcp, 
                       final Logger log)
      {
         super(mcf, poolParams, noTxSeparatePools, jmcp, log);
      }

      protected Object getKey(final Subject subject, final ConnectionRequestInfo cri, boolean separateNoTx)
      {
         return new CriKey(cri, separateNoTx);
      }
   
      public void prefill()
      {
         prefill(null, null, false);
      }

      public void prefill(boolean noTxSeperatePool)
      {
         prefill(null, null, noTxSeperatePool);
      }

      public void prefill(Subject subject, ConnectionRequestInfo cri)
      {
         prefill(subject, cri, false);

      }

      public void prefill(Subject subject, ConnectionRequestInfo cri, boolean noTxSeperatePool)
      {
         if (getPreFill())
         {
            log.warn("Prefill pool option was selected for pool with JNDI name " + getPoolName()
                  + " that does not support this feature.");
            log
                  .warn("Please verify your *-ds.xml file that corresponds with this resource and either remove the <prefill>true|false</prefill element or explicitly set this value to false.");

         }
      }
   }

   /**
    * Pool by subject and criteria
    */
   private static class CriKey
   {
      /** Identifies no connection request information */
      private static final Object NOCRI = new Object();
      
      /** The connection request information */
      private final Object cri;

      /** Separate no tx */
      private boolean separateNoTx;
      
      /** The cached hashCode */
      private int hashCode = Integer.MAX_VALUE;

      CriKey(ConnectionRequestInfo cri, boolean separateNoTx) 
      {
         this.cri = (cri == null)? NOCRI : cri;
         this.separateNoTx = separateNoTx;
      }

      public int hashCode()
      {
         if (hashCode == Integer.MAX_VALUE)
            hashCode = cri.hashCode();
         return hashCode;
      }

      public boolean equals(Object obj)
      {
         if (this == obj)
            return true;
         if (obj == null || (obj instanceof CriKey) == false)
            return false;
         CriKey other = (CriKey) obj;
         return cri.equals(other.cri) && separateNoTx == other.separateNoTx;
      }
   }

   /**
    * One pool
    */
   public static class OnePool
      extends BasePool
   {
      public OnePool(final ManagedConnectionFactory mcf,
                     final InternalManagedConnectionPool.PoolParams poolParams,
                     final boolean noTxSeparatePools, 
                     final Logger log)
      {
         super(mcf, poolParams, noTxSeparatePools, null, log);
      }

      public OnePool(final ManagedConnectionFactory mcf,
                     final InternalManagedConnectionPool.PoolParams poolParams,
                     final boolean noTxSeparatePools, 
                     final JBossManagedConnectionPool jmcp, 
                     final Logger log)
      {
         super(mcf, poolParams, noTxSeparatePools, jmcp, log);
      }

      protected Object getKey(final Subject subject, final ConnectionRequestInfo cri, boolean separateNoTx)
      {
         if (separateNoTx)
            return Boolean.TRUE;
         else
            return Boolean.FALSE;
      }

      public void prefill(Subject sub){
         
         log.debug("Attempting to prefill pool" + getClass());
         
         try
         {
            //WMP is this really the best way to do this? 
            getSubPool(getKey(null, null, false), null, null);

         }

         catch (ResourceException e)
         {
            log.error("Prefill failed for pool instance " + getClass(), e);
            
         }
         
      }

      public void emptySubPool(InternalManagedConnectionPool pool)
      {
      }
   }

   private static class SubjectActions implements PrivilegedAction
   {
      Subject subject;
      Subject other;
      SubjectActions(Subject subject, Subject other)
      {
         this.subject = subject;
         this.other = other;
      }
      public Object run()
      {
         Object value = null;
         if( other == null )
            value = Integer.valueOf(subject.hashCode());
         else
            value = Boolean.valueOf(subject.equals(other));
         return value;
      }
      static int hashCode(Subject subject)
      {
         SubjectActions action = new SubjectActions(subject, null);
         return ((Integer) AccessController.doPrivileged(action)).intValue();
      }
      static boolean equals(Subject subject, Subject other)
      {
         SubjectActions action = new SubjectActions(subject, other);
         return ((Boolean) AccessController.doPrivileged(action)).booleanValue();
      }
   }
}
