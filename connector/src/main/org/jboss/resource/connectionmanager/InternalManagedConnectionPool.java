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
package org.jboss.resource.connectionmanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ValidatingManagedConnectionFactory;
import javax.security.auth.Subject;

import org.jboss.logging.Logger;
import org.jboss.resource.JBossResourceException;
import org.jboss.util.UnreachableStatementException;

/**
 * The internal pool implementation
 *
 * @author <a href="mailto:d_jencks@users.sourceforge.net">David Jencks</a>
 * @author <a href="mailto:adrian@jboss.org">Adrian Brock</a>
 * @author <a href="mailto:weston.price@jboss.com">Weston Price</a>
 * @author <a href="mailto:jesper.pedersen@jboss.org">Jesper Pedersen</a>
 * @version $Revision: 102148 $
 */
public class InternalManagedConnectionPool implements IdleConnectionRemovalSupport
{
   /** The managed connection factory */
   private final ManagedConnectionFactory mcf;

   /** The connection listener factory */
   private final ConnectionListenerFactory clf;

   /** The default subject */
   private final Subject defaultSubject;

   /** The default connection request information */
   private final ConnectionRequestInfo defaultCri;

   /** The pooling parameters */
   private final PoolParams poolParams;

   /** The JBoss managed connection pool */
   private final JBossManagedConnectionPool jmcp;

   /** Copy of the maximum size from the pooling parameters.
    * Dynamic changes to this value are not compatible with
    * the semaphore which cannot change be dynamically changed.
    */
   private int maxSize;

   /** The available connection event listeners */
   private ArrayList cls;

   /** The permits used to control who can checkout a connection */
   private final Semaphore permits;

   /** The log */
   private final Logger log;

   /** Whether trace is enabled */
   private final boolean trace;

   /** Stats */
   private final Counter connectionCounter = new Counter();

   /** The checked out connections */
   private final HashSet checkedOut = new HashSet();

   /** Whether the pool has been started */
   private boolean started = false;

   /** Whether the pool has been shutdown */
   private AtomicBoolean shutdown = new AtomicBoolean(false);

   /** the max connections ever checked out **/
   private volatile int maxUsedConnections = 0;

   /**
    * Create a new internal pool
    *
    * @param mcf the managed connection factory
    * @param subject the subject
    * @param cri the connection request information
    * @param poolParams the pooling parameters
    * @param log the log
    */
   protected InternalManagedConnectionPool(ManagedConnectionFactory mcf, ConnectionListenerFactory clf, Subject subject,
                                           ConnectionRequestInfo cri, PoolParams poolParams, JBossManagedConnectionPool jmcp,
                                           Logger log)
   {
      this.mcf = mcf;
      this.clf = clf;
      this.defaultSubject = subject;
      this.defaultCri = cri;
      this.poolParams = poolParams;
      this.maxSize = poolParams.maxSize;
      this.jmcp = jmcp;
      this.log = log;
      this.trace = log.isTraceEnabled();
      this.cls = new ArrayList(this.maxSize);
      this.permits = new Semaphore(this.maxSize, true);
  
      if (poolParams.prefill)
      {
         PoolFiller.fillPool(this);
      }
   }

   /**
    * Initialize the pool
    */
   protected void initialize()
   {
      if (poolParams.idleTimeout != 0)
         IdleRemover.registerPool(this, poolParams.idleTimeout);

      if (poolParams.backgroundInterval > 0)
      {
         log.debug("Registering for background validation at interval " + poolParams.backgroundInterval);
         ConnectionValidator.registerPool(this, poolParams.backgroundInterval);
      }

      shutdown.set(false);
   }

   protected boolean isRunning()
   {
      return !shutdown.get();
   }

   public long getAvailableConnections()
   {
      return permits.availablePermits();
   }

   public int getMaxConnectionsInUseCount()
   {
      return maxUsedConnections;
   }

   public int getConnectionInUseCount()
   {
      return checkedOut.size();
   }

   /**
    * todo distinguish between connection dying while match called
    * and bad match strategy.  In latter case we should put it back in
    * the pool.
    */
   public ConnectionListener getConnection(Subject subject, ConnectionRequestInfo cri) throws ResourceException
   {
      subject = (subject == null) ? defaultSubject : subject;
      cri = (cri == null) ? defaultCri : cri;
      long startWait = System.currentTimeMillis();
      try
      {
         connectionCounter.updateBlockTime(System.currentTimeMillis() - startWait);
         
         if (permits.tryAcquire(poolParams.blockingTimeout, TimeUnit.MILLISECONDS))
         {
            long poolBlockTime =  System.currentTimeMillis() - startWait ;
            connectionCounter.updateBlockTime(poolBlockTime);

            //We have a permit to get a connection. Is there one in the pool already?
            ConnectionListener cl = null;
            do
            {
               synchronized (cls)
               {
                  if (shutdown.get())
                  {
                     permits.release();
                     throw new RetryableResourceException("The pool has been shutdown");
                  }

                  int clsSize = cls.size();
                  if (clsSize > 0)
                  {
                     cl = (ConnectionListener) cls.remove(clsSize - 1);
                     checkedOut.add(cl);
                     int size = (int) (maxSize - permits.availablePermits());
                     if (size > maxUsedConnections)
                        maxUsedConnections = size;
                  }
               }
               if (cl != null)
               {
                  //Yes, we retrieved a ManagedConnection from the pool. Does it match?
                  try
                  {
                     Object matchedMC = mcf.matchManagedConnections(Collections.singleton(cl.getManagedConnection()),
                           subject, cri);
                     if (matchedMC != null)
                     {
                        if (trace)
                           log.trace("supplying ManagedConnection from pool: " + cl);
                        cl.grantPermit(true);
                        return cl;
                     }

                     //Match did not succeed but no exception was thrown.
                     //Either we have the matching strategy wrong or the
                     //connection died while being checked.  We need to
                     //distinguish these cases, but for now we always
                     //destroy the connection.
                     log.warn("Destroying connection that could not be successfully matched: " + cl);
                     synchronized (cls)
                     {
                        checkedOut.remove(cl);
                     }
                     doDestroy(cl);
                     cl = null;
                  }
                  catch (Throwable t)
                  {
                     log.warn("Throwable while trying to match ManagedConnection, destroying connection: " + cl, t);
                     synchronized (cls)
                     {
                        checkedOut.remove(cl);
                     }
                     doDestroy(cl);
                     cl = null;
                  }
                  //We made it here, something went wrong and we should validate if we should continue attempting to acquire a connection
                  if(poolParams.useFastFail)
                  {
                     log.trace("Fast failing for connection attempt. No more attempts will be made to acquire connection from pool and a new connection will be created immeadiately");
                     break;
                  }
               
               }
            }
            while (cls.size() > 0);//end of do loop

            //OK, we couldnt find a working connection from the pool.  Make a new one.
            try
            {
               //No, the pool was empty, so we have to make a new one.
               cl = createConnectionEventListener(subject, cri);
               synchronized (cls)
               {
                  checkedOut.add(cl);
                  int size = (int) (maxSize - permits.availablePermits());
                  if (size > maxUsedConnections)
                     maxUsedConnections = size;
               }

               //lack of synch on "started" probably ok, if 2 reads occur we will just
               //run fillPool twice, no harm done.
               if (started == false)
               {
                  started = true;
                  if (poolParams.minSize > 0)
                     PoolFiller.fillPool(this);
               }
               if (trace)
                  log.trace("supplying new ManagedConnection: " + cl);
               cl.grantPermit(true);
               return cl;
            }
            catch (Throwable t)
            {
               log.warn("Throwable while attempting to get a new connection: " + cl, t);
               //return permit and rethrow
               synchronized (cls)
               {
                  checkedOut.remove(cl);
               }
               permits.release();
               JBossResourceException.rethrowAsResourceException("Unexpected throwable while trying to create a connection: " + cl, t);
               throw new UnreachableStatementException();
            }
         }
         else
         {
            // we timed out
            throw new ResourceException("No ManagedConnections available within configured blocking timeout ( "
                  + poolParams.blockingTimeout + " [ms] )");
         }

      }
      catch (InterruptedException ie)
      {
         long end = System.currentTimeMillis() - startWait;
         connectionCounter.updateBlockTime(end);
         throw new ResourceException("Interrupted while requesting permit! Waited " + end + " ms");
      }
   }

   public void returnConnection(ConnectionListener cl, boolean kill)
   {
      synchronized (cls)
      {
         if (cl.getState() == ConnectionListener.DESTROYED)
         {
            if (trace)
               log.trace("ManagedConnection is being returned after it was destroyed" + cl);
            if (cl.hasPermit())
            {
               // release semaphore
               cl.grantPermit(false);
               permits.release();
            }

            return;
         }
      }

      if (trace)
         log.trace("putting ManagedConnection back into pool kill=" + kill + " cl=" + cl);
      try
      {
         cl.getManagedConnection().cleanup();
      }
      catch (ResourceException re)
      {
         log.warn("ResourceException cleaning up ManagedConnection: " + cl, re);
         kill = true;
      }

      synchronized (cls)
      {
         // We need to destroy this one
         if (cl.getState() == ConnectionListener.DESTROY || cl.getState() == ConnectionListener.DESTROYED)
            kill = true;
         checkedOut.remove(cl);

         // This is really an error
         if (kill == false && cls.size() >= poolParams.maxSize)
         {
            log.warn("Destroying returned connection, maximum pool size exceeded " + cl);
            kill = true;
         }

         // If we are destroying, check the connection is not in the pool
         if (kill)
         {
            // Adrian Brock: A resource adapter can asynchronously notify us that
            // a connection error occurred.
            // This could happen while the connection is not checked out.
            // e.g. JMS can do this via an ExceptionListener on the connection.
            // I have twice had to reinstate this line of code, PLEASE DO NOT REMOVE IT!
            cls.remove(cl);
         }
         // return to the pool
         else
         {
            cl.used();
            if (cls.contains(cl) == false)
               cls.add(cl);
            else
               log.warn("Attempt to return connection twice (ignored): " + cl, new Throwable("STACKTRACE"));
         }

         if (cl.hasPermit())
         {
            // release semaphore
            cl.grantPermit(false);
            permits.release();
         }
      }

      if (kill)
      {
         if (trace)
            log.trace("Destroying returned connection " + cl);
         doDestroy(cl);
      }

   }

   public void flush()
   {
      ArrayList destroy = null;
      synchronized (cls)
      {
         if (trace)
            log.trace("Flushing pool checkedOut=" + checkedOut + " inPool=" + cls);

         // Mark checked out connections as requiring destruction
         for (Iterator i = checkedOut.iterator(); i.hasNext();)
         {
            ConnectionListener cl = (ConnectionListener) i.next();
            if (trace)
               log.trace("Flush marking checked out connection for destruction " + cl);
            cl.setState(ConnectionListener.DESTROY);
         }
         // Destroy connections in the pool
         while (cls.size() > 0)
         {
            ConnectionListener cl = (ConnectionListener) cls.remove(0);
            if (destroy == null)
               destroy = new ArrayList();
            destroy.add(cl);
         }
      }

      // We need to destroy some connections
      if (destroy != null)
      {
         for (int i = 0; i < destroy.size(); ++i)
         {
            ConnectionListener cl = (ConnectionListener) destroy.get(i);
            if (trace)
               log.trace("Destroying flushed connection " + cl);
            doDestroy(cl);
         }

         // We destroyed something, check the minimum.
         if (shutdown.get() == false && poolParams.minSize > 0)
            PoolFiller.fillPool(this);
      }
   }

   public void removeIdleConnections()
   {
      ArrayList destroy = null;
      long timeout = System.currentTimeMillis() - poolParams.idleTimeout;
      while (true)
      {
         synchronized (cls)
         {
            
            // Nothing left to destroy
            if (cls.size() == 0)
               break;

            // Check the first in the list
            ConnectionListener cl = (ConnectionListener) cls.get(0);
            if (cl.isTimedOut(timeout) && shouldRemove())
            {
               connectionCounter.incTimedOut();
               // We need to destroy this one
               cls.remove(0);
               if (destroy == null)
                  destroy = new ArrayList();
               destroy.add(cl);
            }
            else
            {
               //They were inserted chronologically, so if this one isn't timed out, following ones won't be either.
               break;
            }
         }
      }

      // We found some connections to destroy
      if (destroy != null)
      {
         for (int i = 0; i < destroy.size(); ++i)
         {
            ConnectionListener cl = (ConnectionListener) destroy.get(i);
            if (trace)
               log.trace("Destroying timedout connection " + cl);
            doDestroy(cl);
         }

         // We destroyed something, check the minimum.
         if (shutdown.get() == false && poolParams.minSize > 0)
            PoolFiller.fillPool(this);

         // Empty sub-pool
         if (jmcp != null)
            jmcp.getPoolingStrategy().emptySubPool(this);
      }
   }

   
   /**
    * For testing
    */
   public void shutdownWithoutClear()
   {
      IdleRemover.unregisterPool(this);
      IdleRemover.waitForBackgroundThread();
      ConnectionValidator.unRegisterPool(this);
      ConnectionValidator.waitForBackgroundThread();

      fillToMin();
      shutdown.set(true);
   }

   public void shutdown()
   {
      shutdown.set(true);
      IdleRemover.unregisterPool(this);
      ConnectionValidator.unRegisterPool(this);
      flush();
   }

   public void fillToMin()
   {
      while (true)
      {
         // Get a permit - avoids a race when the pool is nearly full
         // Also avoids unnessary fill checking when all connections are checked out
         try
         {
            if (permits.tryAcquire(poolParams.blockingTimeout, TimeUnit.MILLISECONDS))
            {
               try
               {
                  if (shutdown.get())
                     return;

                  // We already have enough connections
                  if (getMinSize() - connectionCounter.getGuaranteedCount() <= 0)
                     return;

                  // Create a connection to fill the pool
                  try
                  {
                     ConnectionListener cl = createConnectionEventListener(defaultSubject, defaultCri);
                     synchronized (cls)
                     {
                        if (trace)
                           log.trace("Filling pool cl=" + cl);
                        cls.add(cl);
                     }
                  }
                  catch (ResourceException re)
                  {
                     log.warn("Unable to fill pool ", re);
                     return;
                  }
               }
               finally
               {
                  permits.release();
               }
            }
         }
         catch (InterruptedException ignored)
         {
            log.trace("Interrupted while requesting permit in fillToMin");
         }
      }
   }

   public int getConnectionCount()
   {
      return connectionCounter.getCount();
   }
   
   public long getTotalBlockTime()
   {
      return connectionCounter.getTotalBlockTime();
   }
   
   public int getTimedOut()
   {
      return connectionCounter.getTimedOut();
   }
   
   public long getAverageBlockTime()
   {
      return connectionCounter.getTotalBlockTime() / getConnectionCreatedCount();
   }
   
   public long getMaxWaitTime()
   {
       return connectionCounter.getMaxWaitTime();
   }

   public int getConnectionCreatedCount()
   {
      return connectionCounter.getCreatedCount();
   }

   public int getConnectionDestroyedCount()
   {
      return connectionCounter.getDestroyedCount();
   }
   
   Set getConnectionListeners()
   {
      synchronized (cls)
      {         
         Set result = new HashSet();
         result.addAll(cls); 
         result.addAll(checkedOut); 
         return result;
      }
   }

   boolean isEmpty()
   {
      synchronized (cls)
      {
         return cls.size() == 0;
      }
   }

   /**
    * Create a connection event listener
    *
    * @param subject the subject
    * @param cri the connection request information
    * @return the new listener
    * @throws ResourceException for any error
    */
   private ConnectionListener createConnectionEventListener(Subject subject, ConnectionRequestInfo cri)
         throws ResourceException
   {
      ManagedConnection mc = mcf.createManagedConnection(subject, cri);
      connectionCounter.inc();
      try
      {
         return clf.createConnectionListener(mc, this);
      }
      catch (ResourceException re)
      {
         connectionCounter.dec();
         mc.destroy();
         throw re;
      }
   }

   /**
    * Destroy a connection
    *
    * @param cl the connection to destroy
    */
   private void doDestroy(ConnectionListener cl)
   {
      if (cl.getState() == ConnectionListener.DESTROYED)
      {
         log.trace("ManagedConnection is already destroyed " + cl);
         return;
      }

      connectionCounter.dec();
      cl.setState(ConnectionListener.DESTROYED);
      try
      {
         cl.getManagedConnection().destroy();
      }
      catch (Throwable t)
      {
         log.debug("Exception destroying ManagedConnection " + cl, t);
      }

   }
   
   private boolean shouldRemove()
   {      
      boolean remove = true;
      
      if(poolParams.stictMin)
      {
         remove = cls.size() > poolParams.minSize;
         
         log.trace("StrictMin is active. Current connection will be removed is " + remove);
         
      }
      
      return remove;
      
   }
   
   public void validateConnections() throws Exception
   {

      if (trace)
         log.trace("Attempting to  validate connections for pool " + this);

      if (permits.tryAcquire(poolParams.blockingTimeout, TimeUnit.MILLISECONDS))
      {

         boolean anyDestroyed = false;

         try
         {

            while (true)
            {

               ConnectionListener cl = null;
               boolean destroyed = false;

               synchronized (cls)
               {
                  if (cls.size() == 0)
                  {

                     break;

                  }

                  cl = removeForFrequencyCheck();

               }

               if (cl == null)
               {

                  break;
               }

               try
               {

                  Set candidateSet = Collections.singleton(cl.getManagedConnection());

                  if (mcf instanceof ValidatingManagedConnectionFactory)
                  {
                     ValidatingManagedConnectionFactory vcf = (ValidatingManagedConnectionFactory) mcf;
                     candidateSet = vcf.getInvalidConnections(candidateSet);

                     if (candidateSet != null && candidateSet.size() > 0)
                     {

                        if (cl.getState() != ConnectionListener.DESTROY)
                        {
                           doDestroy(cl);
                           destroyed = true;
                           anyDestroyed = true;
                        }
                     }

                  }
                  else
                  {
                     log.warn("warning: background validation was specified with a non compliant ManagedConnectionFactory interface.");
                  }

               }
               finally
               {
                  if(!destroyed)
                  {
                     synchronized (cls)
                     {
                        returnForFrequencyCheck(cl);
                     }
                     
                  }

               }

            }

         }
         finally
         {
            permits.release();

            if (anyDestroyed && shutdown.get() == false && poolParams.minSize > 0)
            {
               PoolFiller.fillPool(this);
            }

         }

      }

   }
   private ConnectionListener removeForFrequencyCheck()
   {

      log.debug("Checking for connection within frequency");

      ConnectionListener cl = null;

      for (Iterator iter = cls.iterator(); iter.hasNext();)
      {

         cl = (ConnectionListener) iter.next();
         long lastCheck = cl.getLastValidatedTime();

         if ((System.currentTimeMillis() - lastCheck) >= poolParams.backgroundInterval)
         {
            cls.remove(cl);
            break;

         }
         else
         {
            cl = null;
         }

      }

      return cl;
   }

   private void returnForFrequencyCheck(ConnectionListener cl)
   {

      log.debug("Returning for connection within frequency");

      cl.setLastValidatedTime(System.currentTimeMillis());
      cls.add(cl);

   }
   /**
    * Guard against configurations or
    * dynamic changes that may increase the minimum
    * beyond the maximum
    */
   private int getMinSize()
   {
      if (poolParams.minSize > maxSize)
         return maxSize;
      
      return poolParams.minSize;
   }

   public static class PoolParams
   {
	   public int minSize = 0;

		public int maxSize = 10;

		public int blockingTimeout = 30000; // milliseconds

		public long idleTimeout = 1000 * 60 * 30; // milliseconds, 30 minutes.

		public long backgroundInterval = 0;
		
		public boolean prefill;
      
        public boolean stictMin;
        
        //Do we want to immeadiately break when a connection cannot be matched and not evaluate the rest of the pool?
        public boolean useFastFail;
   }

   /**
	 * Stats
	 */
   private static class Counter
   {
      private int created = 0;

      private int destroyed = 0;

      // Total wait time to get Connection from Pool.
      private long totalBlockTime;
      
      // Idle timed out Connection Count.
      private int timedOut;

      // The maximum wait time */      
      private long maxWaitTime;
      
      synchronized int getGuaranteedCount()
      {
         return created - destroyed;
      }

      int getCount()
      {
         return created - destroyed;
      }

      int getCreatedCount()
      {
         return created;
      }

      int getDestroyedCount()
      {
         return destroyed;
      }

      synchronized void inc()
      {
         ++created;
      }

      synchronized void dec()
      {
         ++destroyed;
      }
   
      synchronized void updateBlockTime(long latest)
      {
         totalBlockTime += latest;
         if (maxWaitTime < latest)
            maxWaitTime = latest;
      }

      long getTotalBlockTime()
      {
         return totalBlockTime;
      }
 
      int getTimedOut()
      {
         return timedOut;
      }
      
      synchronized void incTimedOut()
      {
         ++timedOut;
      }

      long getMaxWaitTime()
      {
          return maxWaitTime;
      }
   }
}
