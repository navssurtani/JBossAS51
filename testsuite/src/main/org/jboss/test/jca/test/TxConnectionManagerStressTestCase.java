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
package org.jboss.test.jca.test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnectionFactory;
import javax.security.auth.Subject;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.jboss.logging.Logger;
import org.jboss.resource.connectionmanager.CachedConnectionManager;
import org.jboss.resource.connectionmanager.ConnectionListener;
import org.jboss.resource.connectionmanager.InternalManagedConnectionPool;
import org.jboss.resource.connectionmanager.JBossManagedConnectionPool;
import org.jboss.resource.connectionmanager.ManagedConnectionPool;
import org.jboss.resource.connectionmanager.TxConnectionManager;
import org.jboss.test.JBossTestCase;
import org.jboss.test.util.ejb.EJBTestCase;
import org.jboss.test.jca.adapter.TestConnectionRequestInfo;
import org.jboss.test.jca.adapter.TestManagedConnectionFactory;
import org.jboss.tm.TransactionManagerLocator;
import org.jboss.tm.TxUtils;

/**
 * Stress test case for TxConnectionManager.
 *
 * Based on BaseConnectionManagerStressTestCase by David Jencks.
 * @author <a href="mailto:jesper.pedersen@jboss.org">Jesper Pedersen</a>
 * @version $Revision: 85945 $
 */
public class TxConnectionManagerStressTestCase extends EJBTestCase
{
   private static final Logger log = Logger.getLogger(TxConnectionManagerStressTestCase.class);

   private boolean failed;
   private Exception error;

   private AtomicInteger startedThreadCount;
   private CountDownLatch finishedThreadCount;

   private AtomicLong elapsed = new AtomicLong(0);
   private AtomicLong getConnection = new AtomicLong(0);
   private AtomicLong returnConnection = new AtomicLong(0);
   private AtomicLong held = new AtomicLong(0);

   private AtomicInteger connectionCount;
   private AtomicInteger errorCount;

   private TransactionManager tm;
   private Subject subject = new Subject();
   private ConnectionRequestInfo cri = new TestConnectionRequestInfo();
   private CachedConnectionManager ccm = new CachedConnectionManager();

   /**
    * Constructor
    * @param name The test name
    */
   public TxConnectionManagerStressTestCase(String name)
   {
      super(name);
   }

   public static Test suite() throws Exception
   {
      TestSuite suite = new TestSuite();
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingNoFill"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingFill"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingPartFill"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingNearlyFill"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingAggressiveRemoval"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingAggressiveRemovalAndFill"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingNoFillTrackByTx"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingFillTrackByTx"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingPartFillTrackByTx"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingNearlyFillTrackByTx"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingAggressiveRemovalTrackByTx"));
      suite.addTest(new TxConnectionManagerStressTestCase("testBlockingAggressiveRemovalAndFillTrackByTx"));
      suite.addTest(new TxConnectionManagerStressTestCase("testTimeoutNoFill"));
      suite.addTest(new TxConnectionManagerStressTestCase("testTimeoutNoFillTrackByTx"));
      suite.addTest(new TxConnectionManagerStressTestCase("testTimeoutFill"));
      suite.addTest(new TxConnectionManagerStressTestCase("testTimeoutFillTrackByTx"));

      return JBossTestCase.getDeploySetup(suite, "jca-tests.jar");
   }

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();

      tm = TransactionManagerLocator.getInstance().locate();
   }

   @Override
   protected void tearDown() throws Exception
   {
      tm = null;

      super.tearDown();
   }

   private TxConnectionManager getCM(InternalManagedConnectionPool.PoolParams pp, boolean trackByTx) throws Exception
   {
      ManagedConnectionFactory mcf = new TestManagedConnectionFactory();
      ManagedConnectionPool poolingStrategy = new TestPool(mcf, pp, false, log);

      TxConnectionManager cm = new TxConnectionManager(ccm, poolingStrategy, tm);
      cm.setTrackConnectionByTx(trackByTx);

      poolingStrategy.setConnectionListenerFactory(cm);
      return cm;
   }

   private void shutdown(TxConnectionManager cm)
   {
      TestPool pool = (TestPool) cm.getPoolingStrategy();
      pool.shutdown();
   }

   public void testBlockingNoFill() throws Exception
   {
      doBlocking(20, 0, 5000, false);
   }

   public void testBlockingFill() throws Exception
   {
      doBlocking(20, getBeanCount(), 5000, false);
   }

   public void testBlockingPartFill() throws Exception
   {
      doBlocking(20, getBeanCount()/2, 5000, false);
   }

   public void testBlockingNearlyFill() throws Exception
   {
      doBlocking(20, getBeanCount() - 1, 5000, false);
   }

   public void testBlockingAggressiveRemoval() throws Exception
   {
      doBlocking(20, 0, 10, false);
   }

   public void testBlockingAggressiveRemovalAndFill() throws Exception
   {
      doBlocking(20, getBeanCount(), 10, false);
   }

   public void testBlockingNoFillTrackByTx() throws Exception
   {
      doBlocking(20, 0, 5000, true);
   }

   public void testBlockingFillTrackByTx() throws Exception
   {
      doBlocking(20, getBeanCount(), 5000, true);
   }

   public void testBlockingPartFillTrackByTx() throws Exception
   {
      doBlocking(20, getBeanCount()/2, 5000, true);
   }

   public void testBlockingNearlyFillTrackByTx() throws Exception
   {
      doBlocking(20, getBeanCount() - 1, 5000, true);
   }

   public void testBlockingAggressiveRemovalTrackByTx() throws Exception
   {
      doBlocking(20, 0, 10, true);
   }

   public void testBlockingAggressiveRemovalAndFillTrackByTx() throws Exception
   {
      doBlocking(20, getBeanCount(), 10, true);
   }

   public void testTimeoutNoFill() throws Exception
   {
      doTimeout(0, 5000, false);
   }

   public void testTimeoutNoFillTrackByTx() throws Exception
   {
      doTimeout(0, 5000, true);
   }

   public void testTimeoutFill() throws Exception
   {
      doTimeout(getBeanCount(), 5000, false);
   }

   public void testTimeoutFillTrackByTx() throws Exception
   {
      doTimeout(getBeanCount(), 5000, true);
   }

   /**
    * The doBlocking method tries to simulate extremely high load on the pool.
    * @exception Exception if an error occurs
    */
   public void doBlocking(long sleep, int min, long idle, final boolean trackByTx) throws Exception
   {  
      failed = false;

      startedThreadCount = new AtomicInteger(0);
      connectionCount = new AtomicInteger(0);
      errorCount = new AtomicInteger(0);

      final int reps = 5; //getIterationCount();
      final int threadsPerConnection = 10; //getThreadCount();
      final long sleepTime = sleep;

      InternalManagedConnectionPool.PoolParams pp = new InternalManagedConnectionPool.PoolParams();
      pp.minSize = min;
      pp.maxSize = getBeanCount();
      pp.blockingTimeout = 30000;
      pp.idleTimeout = idle;

      final TxConnectionManager cm = getCM(pp, trackByTx);

      try
      {
         int totalThreads = pp.maxSize * threadsPerConnection;
         finishedThreadCount = new CountDownLatch(totalThreads);

         log.info("Blocking test with connections: " + pp.maxSize + " totalThreads: " + totalThreads + " reps: " + reps);
         for (int i = 0; i < totalThreads; i++)
         {
            Runnable t = new Runnable()
            {
               int id;
               public void run()
               {
                  id = startedThreadCount.getAndIncrement();

                  long duration = 0;
                  long getConnection = 0;
                  long returnConnection = 0;
                  long heldConnection = 0;
                  for (int j = 0; j < reps; j++)
                  {
                     ConnectionListener cl = null;
                     try
                     {
                        if (tm == null)
                           throw new SystemException("TM is null");

                        tm.begin();

                        long startGetConnection = System.currentTimeMillis();
                        cl = cm.getManagedConnection(subject, cri);
                        cl.enlist();
                        long endGetConnection = System.currentTimeMillis();

                        TxConnectionManagerStressTestCase.this.connectionCount.incrementAndGet();

                        Thread.sleep(sleepTime);

                        if (tm == null)
                           throw new SystemException("TM is null");

                        tm.commit();

                        long startReturnConnection = System.currentTimeMillis();
                        if (!trackByTx)
                        {
                           cl.delist();
                           cm.returnManagedConnection(cl, false);
                        }
                        cl = null;
                        long endReturnConnection = System.currentTimeMillis();
                        
                        duration += (endReturnConnection - startGetConnection);
                        getConnection += (endGetConnection - startGetConnection);
                        returnConnection += (endReturnConnection - startReturnConnection);
                        heldConnection += (startReturnConnection - endGetConnection);
                      }
                      catch (NotSupportedException nse)
                      {
                         TxConnectionManagerStressTestCase.this.log.info("error: iterationCount: " + j + ", connectionCount: " + TxConnectionManagerStressTestCase.this.connectionCount.get() + " " + nse.getMessage());
                         TxConnectionManagerStressTestCase.this.errorCount.incrementAndGet();
                         TxConnectionManagerStressTestCase.this.error = nse;
                         TxConnectionManagerStressTestCase.this.failed = true;
                      }
                      catch (SystemException se)
                      {
                         TxConnectionManagerStressTestCase.this.log.info("error: iterationCount: " + j + ", connectionCount: " + TxConnectionManagerStressTestCase.this.connectionCount.get() + " " + se.getMessage());
                         TxConnectionManagerStressTestCase.this.errorCount.incrementAndGet();
                         TxConnectionManagerStressTestCase.this.error = se;
                         TxConnectionManagerStressTestCase.this.failed = true;
                      }
                      catch (RollbackException rbe)
                      {
                         TxConnectionManagerStressTestCase.this.log.info("error: iterationCount: " + j + ", connectionCount: " + TxConnectionManagerStressTestCase.this.connectionCount.get() + " " + rbe.getMessage());
                         TxConnectionManagerStressTestCase.this.errorCount.incrementAndGet();
                         TxConnectionManagerStressTestCase.this.error = rbe;
                         TxConnectionManagerStressTestCase.this.failed = true;
                      }
                      catch (HeuristicMixedException hme)
                      {
                         TxConnectionManagerStressTestCase.this.log.info("error: iterationCount: " + j + ", connectionCount: " + TxConnectionManagerStressTestCase.this.connectionCount.get() + " " + hme.getMessage());
                         TxConnectionManagerStressTestCase.this.errorCount.incrementAndGet();
                         TxConnectionManagerStressTestCase.this.error = hme;
                         TxConnectionManagerStressTestCase.this.failed = true;
                      }
                      catch (HeuristicRollbackException hre)
                      {
                         TxConnectionManagerStressTestCase.this.log.info("error: iterationCount: " + j + ", connectionCount: " + TxConnectionManagerStressTestCase.this.connectionCount.get() + " " + hre.getMessage());
                         TxConnectionManagerStressTestCase.this.errorCount.incrementAndGet();
                         TxConnectionManagerStressTestCase.this.error = hre;
                         TxConnectionManagerStressTestCase.this.failed = true;
                      }
                      catch (ResourceException re)
                      {
                         TxConnectionManagerStressTestCase.this.log.info("error: iterationCount: " + j + ", connectionCount: " + TxConnectionManagerStressTestCase.this.connectionCount.get() + " " + re.getMessage());
                         TxConnectionManagerStressTestCase.this.errorCount.incrementAndGet();
                         TxConnectionManagerStressTestCase.this.error = re;
                         TxConnectionManagerStressTestCase.this.failed = true;
                      }
                      catch (InterruptedException ie)
                      {
                         break;
                      }
                      finally
                      {
                         if (cl != null)
                            cm.returnManagedConnection(cl, true);

                         try
                         {
                            if (tm == null)
                               throw new SystemException("TM is null");

                            Transaction tx = tm.getTransaction();
                            if (tx != null)
                               log.info("TX STATUS=" + TxUtils.getStatusAsString(tx.getStatus()));
                            if (tx != null && TxUtils.isUncommitted(tx))
                            {
                               tm.rollback();
                            }
                         }
                         catch (SystemException se)
                         {
                            TxConnectionManagerStressTestCase.this.log.info("error: iterationCount: " + j + ", connectionCount: " + TxConnectionManagerStressTestCase.this.connectionCount.get() + " " + se.getMessage());
                            TxConnectionManagerStressTestCase.this.errorCount.incrementAndGet();
                            TxConnectionManagerStressTestCase.this.error = se;
                            TxConnectionManagerStressTestCase.this.failed = true;
                         }
                      }
                  }

                   TxConnectionManagerStressTestCase.this.elapsed.addAndGet(duration);
                   TxConnectionManagerStressTestCase.this.getConnection.addAndGet(getConnection);
                   TxConnectionManagerStressTestCase.this.returnConnection.addAndGet(returnConnection);
                   TxConnectionManagerStressTestCase.this.held.addAndGet(heldConnection);

                   finishedThreadCount.countDown();
               }
            };
            new Thread(t).start();
         }
         finishedThreadCount.await();
         
         // Stop the pool/idle remover, otherwise the following checks will be random
         TestPool pool = (TestPool) cm.getPoolingStrategy();
         pool.shutdownWithoutClear();
         
         float expected = totalThreads * reps;
         float lessWaiting = getConnection.get() - (threadsPerConnection - 1) * held.get();
         log.info("completed " + getName() + " with connectionCount: " + connectionCount.get() + ", expected : " + expected);
         log.info("errorCount: " + errorCount.get() + " %error=" + ((100 * errorCount.get()) / expected));
         log.info("Total time elapsed: " + elapsed.get()  + ", perRequest: " + (elapsed.get() / (float)connectionCount.get()));
         log.info("Total time held   : " + held.get()  + ", perRequest: " + (held.get() / (float)connectionCount.get()));
         log.info("Time getConnection: " + getConnection.get()  + ", perRequest: " + (getConnection.get() / (float)connectionCount.get()));
         log.info("     lessWaiting  : " + lessWaiting  + ", perRequest: " + (lessWaiting / connectionCount.get()));
         log.info("Time retConnection: " + returnConnection.get()  + ", perRequest: " + (returnConnection.get() / (float)connectionCount.get()));
         int available = (int) pool.getAvailableConnectionCount();
         assertTrue("Wrong number of connections counted: " + available, available == pp.maxSize);
         assertTrue("Blocking Timeout occurred in blocking test: " + error, !failed);
      }
      finally
      {
         if (cm != null)
            shutdown(cm);
      }
   }
   
   /**
    * The doTimeout method tries to simulate extremely high load on the pool.
    * @exception Exception if an error occurs
    */
   public void doTimeout(int min, long idle, final boolean trackByTx) throws Exception
   {  
      failed = false;

      startedThreadCount = new AtomicInteger(0);

      final int reps = 1; //getIterationCount();
      final int threadsPerConnection = 1; //getThreadCount();

      InternalManagedConnectionPool.PoolParams pp = new InternalManagedConnectionPool.PoolParams();
      pp.minSize = min;
      pp.maxSize = getBeanCount();
      pp.blockingTimeout = 30000;
      pp.idleTimeout = idle;

      final TxConnectionManager cm = getCM(pp, trackByTx);

      try
      {
         int totalThreads = pp.maxSize * threadsPerConnection;
         finishedThreadCount = new CountDownLatch(totalThreads);

         log.info("Timeout test with connections: " + pp.maxSize + " totalThreads: " + totalThreads + " reps: " + reps);
         for (int i = 0; i < totalThreads; i++)
         {
            Runnable t = new TestRunnable(reps, cm, trackByTx);
            new Thread(t).start();
         }
         finishedThreadCount.await();


         assertTrue("Error in timeout test: " + error, !failed);
      }
      finally
      {
         if (cm != null)
            shutdown(cm);
      }
   }
   
   public class TestRunnable implements Runnable, Synchronization
   {
      int id;
      int reps;
      TxConnectionManager cm;
      boolean trackByTx;
      CountDownLatch latch = new CountDownLatch(1);

      public TestRunnable(int reps, TxConnectionManager cm, boolean trackByTx)
      {
         this.reps = reps;
         this.cm = cm;
         this.trackByTx = trackByTx;
      }

      public void run()
      {
         id = startedThreadCount.getAndIncrement();

         for (int j = 0; j < reps; j++)
         {
            ConnectionListener cl = null;
            try
            {
               assertNotNull(tm);

               tm.setTransactionTimeout(2);
               tm.begin();
               Transaction tx = tm.getTransaction();
               tx.registerSynchronization(this);

               cl = cm.getManagedConnection(subject, cri);
               cl.enlist();

               latch.await(20, TimeUnit.SECONDS);

               assertNotNull(tm);

               tx = tm.getTransaction();
               if (tx != null && TxUtils.isActive(tx))
                  failed = true;

               if (!trackByTx)
               {
                  cl.delist();
                  cm.returnManagedConnection(cl, false);
               }
               cl = null;

             }
             catch (NotSupportedException nse)
             {
                error = nse;
                log.info(nse.getMessage(), nse);
             }
             catch (RollbackException se)
             {
                error = se;
                log.info(se.getMessage(), se);
             }
             catch (SystemException se)
             {
                error = se;
                log.info(se.getMessage(), se);
             }
             catch (ResourceException re)
             {
                error = re;
                log.info(re.getMessage(), re);
             }
             catch (InterruptedException ie)
             {
                break;
             }
             finally
             {
                if (cl != null)
                   cm.returnManagedConnection(cl, true);

                try
                {
                   assertNotNull(tm);
                   Transaction tx = tm.suspend();
                   if (tx != null && TxUtils.isActive(tx))
                      failed = true;
                }
                catch (SystemException se)
                {
                }
             }
         }

         finishedThreadCount.countDown();
      }

      public void beforeCompletion()
      {
      }
      
      public void afterCompletion(int status)
      {
         latch.countDown();
      }
   }
   
   public class TestPool extends JBossManagedConnectionPool.OnePool
   {
      public TestPool(final ManagedConnectionFactory mcf, 
                      final InternalManagedConnectionPool.PoolParams poolParams,
                      final boolean noTxSeparatePools, 
                      final Logger log)
      {
         super(mcf, poolParams, noTxSeparatePools, log);
      }

      public void shutdownWithoutClear()
      {
         super.shutdownWithoutClear();
      }
   }
}
