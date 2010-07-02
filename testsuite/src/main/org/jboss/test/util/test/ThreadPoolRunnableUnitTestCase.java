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
package org.jboss.test.util.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;

import org.jboss.util.threadpool.BasicThreadPool;
import org.jboss.util.threadpool.ThreadPoolFullException;
import org.jboss.util.threadpool.BlockingMode;
import org.jboss.logging.Logger;
import junit.framework.TestCase;

/**
 * Tests of thread pool with Runnables added to the pool
 *
 * @see org.jboss.util.threadpool.ThreadPool
 * @author <a href="adrian@jboss.org">Adrian.Brock</a>
 * @author Scott.Stark@jboss.org
 * @version $Revision: 61635 $
 */
public class ThreadPoolRunnableUnitTestCase extends TestCase
{
   private static Logger log = Logger.getLogger(ThreadPoolRunnableUnitTestCase.class);

   /** Basic test */
   static final int BASIC = 0;

   /** Hold the thread after start */
   static final int HOLD_START = 1;

   /** The started runnables */
   HashSet startedRunnables = new HashSet();

   /** The started releases */
   HashSet startedReleases = new HashSet();

   /** The finished runnables */
   HashSet finishedRunnables = new HashSet();

   /** The thread names */
   HashMap threadNames = new HashMap();

   /**
    * Create a new ThreadPoolRunnableUnitTestCase
    *
    * @param name the test to run
    */
   public ThreadPoolRunnableUnitTestCase(String name)
   {
      super(name);
   }

   /**
    * Basic test
    */
   public void testBasic() throws Exception
   {
      log.debug("testBasic");
      BasicThreadPool pool = new BasicThreadPool();
      try
      {
         pool.run(new TestRunnable(BASIC, "test"));
         waitFinished(1);
         HashSet expected = makeExpected(new Object[] {"test"});
         assertEquals(expected, finishedRunnables);
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Multiple Basic test
    */
   public void testMultipleBasic() throws Exception
   {
      log.debug("testMultipleBasic");
      BasicThreadPool pool = new BasicThreadPool();
      try
      {
         pool.run(new TestRunnable(BASIC, "test1"));
         pool.run(new TestRunnable(BASIC, "test2"));
         pool.run(new TestRunnable(BASIC, "test3"));
         waitFinished(3);
         HashSet expected = makeExpected(new Object[] {"test1", "test2", "test3"});
         assertEquals(expected, finishedRunnables);
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Test pooling
    */
   public void testSimplePooling() throws Exception
   {
      log.debug("testSimplePooling");
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumPoolSize(1);
      try
      {
         pool.run(new TestRunnable(BASIC, "test1"));
         waitFinished(1);
         pool.run(new TestRunnable(BASIC, "test2"));
         waitFinished(2);
         assertEquals(threadNames.get("test1"), threadNames.get("test2"));
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Test multiple pooling
    */
   public void testMultiplePooling() throws Exception
   {
      log.debug("testMultiplePooling");
      BasicThreadPool pool = new BasicThreadPool();
      try
      {
         pool.run(new TestRunnable(HOLD_START, "test1"));
         waitStarted(1);
         pool.run(new TestRunnable(BASIC, "test2"));
         waitFinished(1);
         releaseStarted("test1");
         waitFinished(2);
         assertTrue("Shouldn't run on the same thread", threadNames.get("test1").equals(threadNames.get("test2")) == false);
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Test maximum pool
    */
   public void testMaximumPool() throws Exception
   {
      log.debug("testMaximumPool");
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumPoolSize(1);
      try
      {
         pool.run(new TestRunnable(HOLD_START, "test1"));
         waitStarted(1);
         pool.run(new TestRunnable(BASIC, "test2"));
         Thread.sleep(1000);
         assertEquals(0, finishedRunnables.size());
         releaseStarted("test1");
         waitFinished(2);
         assertEquals(makeExpected(new Object[] {"test1", "test2"}), finishedRunnables);
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Test maximum cache
    */
   public void testMaximumQueue() throws Exception
   {
      log.debug("testMaximumQueue");
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumQueueSize(1);
      pool.setMaximumPoolSize(1);
      try
      {
         pool.run(new TestRunnable(HOLD_START, "test1"));
         waitStarted(1);
         pool.run(new TestRunnable(BASIC, "test2"));

         boolean caught = false;
         try
         {
            pool.run(new TestRunnable(BASIC, "test3"));
         }
         catch (ThreadPoolFullException expected)
         {
            caught = true;
         }
         assertTrue("Expected ThreadPoolFullException", caught);

         releaseStarted("test1");
         waitFinished(2);
         assertEquals(makeExpected(new Object[] {"test1", "test2"}), finishedRunnables);
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Test runnable timeouts
    */
   public void testRunnableTimeout() throws Exception
   {
      log.debug("testRunnableTimeout");
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumQueueSize(1);
      pool.setMaximumPoolSize(1);
      try
      {
         TestRunnable test = new TestRunnable(HOLD_START, "test1", 12*1000);
         pool.run(test, 0, 10*1000);
         waitStarted(1);
         releaseStarted("test1");
         waitFinished(1);
         assertEquals(makeExpected(new Object[] {"test1"}), finishedRunnables);
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Test runnable timeouts
    */
   public void testRunnableTimeoutWithSpinLoop() throws Exception
   {
      log.debug("testRunnableTimeoutWithSpinLoop");
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumQueueSize(1);
      pool.setMaximumPoolSize(1);
      try
      {
         TestRunnable test = new TestRunnable(HOLD_START, "test1", Long.MAX_VALUE);
         pool.run(test, 0, 8*1000);
         waitStarted(1);
         releaseStarted("test1");
         Thread.sleep(12*1000);
         // Run another task to validate the previous thread has been cleared
         pool.run(new TestRunnable(BASIC, "test2"));
         waitStarted(1);
         releaseStarted("test2");
         waitFinished(1);
         assertEquals(makeExpected(new Object[] {"test2"}), finishedRunnables);
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Test runnable timeouts
    */
   public void testRunnableTimeoutWithSpinLoop2() throws Exception
   {
      log.debug("testRunnableTimeoutWithSpinLoop2");
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumQueueSize(1);
      pool.setMaximumPoolSize(1);
      pool.setBlockingMode(BlockingMode.RUN);
      try
      {
         TestRunnable test = new TestRunnable(BASIC, "testx", Long.MAX_VALUE);
         pool.run(test, 0, 1*1000);
         // Run another task to validate the previous thread has been cleared
         ArrayList tmp = new ArrayList();
         for(int n = 0; n < 10; n ++)
         {
            String name = "test"+n;
            pool.run(new TestRunnable(BASIC, name));
            tmp.add(name);
         }
         Thread.sleep(3000);
         assertEquals(makeExpected(tmp.toArray()), finishedRunnables);
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Save the thread name
    *
    * @param data the test data
    * @param name the thread name
    */
   public synchronized void saveRunnableThreadName(String data, String name)
   {
      threadNames.put(data, name);
   }

   /**
    * Wait for expected starts
    */
   public synchronized void waitStarted(int target)
      throws InterruptedException
   {
      log.info("waitStarted, target="+target);
      while (startedRunnables.size() < target)
         wait();
   }

   /**
    * Release in waiting for start
    *
    * @param data the thread to start
    */
   public synchronized void releaseStarted(String data)
   {
      log.info("releaseStarted, data="+data);
      startedReleases.add(data);
      notifyAll();
   }

   /**
    * Wait for release started
    */
   public synchronized void waitForReleaseStarted(String data)
   {
      try
      {
         log.info("waitForReleaseStarted, data="+data);
         while (startedReleases.contains(data) == false)
            wait();
      }
      catch (InterruptedException ignored)
      {
      }
   }

   /**
    * Notify started
    */
   public synchronized void notifyStarted(String data)
   {
      log.info("notifyStarted, data="+data);
      startedRunnables.add(data);
      notifyAll();
   }

   /**
    * Clear started
    */
   public synchronized void clearStarted()
   {
      log.info("clearStarted");
      startedRunnables.clear();
   }

   /**
    * Wait for expected finishes
    */
   public synchronized void waitFinished(int target)
      throws InterruptedException
   {
      log.info("waitFinished, target="+target);
      while (finishedRunnables.size() < target)
         wait();
   }

   /**
    * Notify finished
    */
   public synchronized void notifyFinished(String data)
   {
      log.info("notifyFinished, data="+data);
      finishedRunnables.add(data);
      notifyAll();
   }

   /**
    * Clear finished
    */
   public synchronized void clearFinished()
   {
      log.info("clearFinished");
      finishedRunnables.clear();
   }

   /**
    * Make the expected result
    *
    * @param expected the results as an object array
    * @return the expected result
    */
   public HashSet makeExpected(Object[] expected)
   {
      return new HashSet(Arrays.asList(expected));
   }

   /**
    * Test runnable
    */
   public class TestRunnable implements Runnable
   {
      /** The test to run */
      private int test;
      /** The data for the test */
      private String data;
      private long runSleepTime;

      /**
       * Create a new TestRunnable
       *
       * @param test the test
       * @param data the test data
       */
      public TestRunnable(int test, String data)
      {
         this(test, data, 0);
      }
      public TestRunnable(int test, String data, long runSleepTime)
      {
         this.test = test;
         this.data = data;
         this.runSleepTime = runSleepTime;
      }

      /**
       * Runnable implementation
       */
      public void run()
      {
         log.info("Begin run");
         saveThreadName();
         started();
         if( runSleepTime > 0 )
         {
            log.info("Begin spin loop");
            if( runSleepTime == Long.MAX_VALUE )
            {
               while( true )
                  ;
            }
            else
            {
               log.info("Begin sleep");
               try
               {
                  Thread.sleep(runSleepTime);
               }
               catch(InterruptedException e)
               {
               }
            }
         }
         finished();
         log.info("End run");
      }

      /**
       * Save the thread
       */
      public void saveThreadName()
      {
         saveRunnableThreadName(data, Thread.currentThread().getName());
      }

      /**
       * The test is finished
       */
      public void started()
      {
         notifyStarted(data);
         if (test == HOLD_START)
            waitForReleaseStarted(data);
      }

      /**
       * The test is finished
       */
      public void finished()
      {
         notifyFinished(data);
      }
   }
}
