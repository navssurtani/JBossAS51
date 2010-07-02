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
package org.jboss.test.util.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.jboss.util.threadpool.BasicThreadPool;
import org.jboss.util.threadpool.Task;
import org.jboss.logging.Logger;
import junit.framework.TestCase;

/**
 * Tests of thread pool with Tasks added to the pool
 *
 * @see org.jboss.util.threadpool.ThreadPool
 * @author <a href="adrian@jboss.org">Adrian.Brock</a>
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class ThreadPoolTaskUnitTestCase extends TestCase
{
   private static Logger log = Logger.getLogger(ThreadPoolTaskUnitTestCase.class);

   /** Basic test */
   static final int BASIC = 0;

   /** Hold the thread after start */
   static final int HOLD_START = 1;

   /** The accepted stats */
   Stats accepted = new Stats("Accepted");

   /** The rejected stats */
   Stats rejected = new Stats("Rejected");

   /** The started stats */
   Stats started = new Stats("Started");

   /** The completed stats */
   Stats completed = new Stats("Completed");

   /** The stopped stats */
   Stats stopped = new Stats("Stopped");

   /** The thread names */
   HashMap threadNames = new HashMap();

   /**
    * Create a new ThreadPoolTaskUnitTestCase
    *
    * @param name the test to run
    */
   public ThreadPoolTaskUnitTestCase(String name)
   {
      super(name);
   }

   protected void setUp() throws Exception
   {
      log.debug("====> Starting test: " + getName());
   }

   protected void tearDown() throws Exception
   {
      log.debug("=====> Stopping test: " + getName());
   }

   /**
    * Basic test
    */
   public void testBasic() throws Exception
   {
      BasicThreadPool pool = new BasicThreadPool();
      try
      {
         pool.runTask(new TestTask(BASIC, "test"));
         completed.wait(1);
         HashSet expected = makeExpected(new Object[] {"test"});
         assertEquals(expected, accepted.tasks);
         assertEquals(expected, started.tasks);
         assertEquals(expected, completed.tasks);
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
      BasicThreadPool pool = new BasicThreadPool();
      try
      {
         pool.runTask(new TestTask(BASIC, "test1"));
         pool.runTask(new TestTask(BASIC, "test2"));
         pool.runTask(new TestTask(BASIC, "test3"));
         completed.wait(3);
         HashSet expected = makeExpected(new Object[] {"test1", "test2", "test3"});
         assertEquals(expected, accepted.tasks);
         assertEquals(expected, started.tasks);
         assertEquals(expected, completed.tasks);
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
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumPoolSize(1);
      try
      {
         pool.runTask(new TestTask(BASIC, "test1"));
         completed.wait(1);
         pool.runTask(new TestTask(BASIC, "test2"));
         completed.wait(2);
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
      BasicThreadPool pool = new BasicThreadPool();
      try
      {
         pool.runTask(new TestTask(HOLD_START, "test1"));
         started.wait(1);
         pool.runTask(new TestTask(BASIC, "test2"));
         completed.wait(1);
         started.release("test1");
         completed.wait(2);
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
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumPoolSize(1);
      try
      {
         pool.runTask(new TestTask(HOLD_START, "test1"));
         started.wait(1);
         pool.runTask(new TestTask(BASIC, "test2"));
         Thread.sleep(1000);
         assertEquals(0, completed.tasks.size());
         started.release("test1");
         completed.wait(2);
         assertEquals(makeExpected(new Object[] {"test1", "test2"}), completed.tasks);
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
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumQueueSize(1);
      pool.setMaximumPoolSize(1);
      try
      {
         pool.runTask(new TestTask(HOLD_START, "test1"));
         started.wait(1);
         pool.runTask(new TestTask(BASIC, "test2"));
         assertEquals(0, rejected.tasks.size());
         pool.runTask(new TestTask(BASIC, "test3"));
         assertEquals(makeExpected(new Object[] {"test3"}), rejected.tasks);

         started.release("test1");
         completed.wait(2);
         assertEquals(makeExpected(new Object[] {"test1", "test2"}), completed.tasks);
      }
      finally
      {
         pool.stop(true);
      }
   }

   /**
    * Test maximum cache
    */
   public void testCompleteTimeout() throws Exception
   {
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumQueueSize(1);
      pool.setMaximumPoolSize(1);
      try
      {
         /* Test that a task with a timeout that completes within its timeout
         works as expected
         */
         TestTask task = new TestTask(HOLD_START, "test1", 0, 10*1000, Task.WAIT_NONE);
         pool.runTask(task);
         started.wait(1);
         started.release("test1");
         completed.wait(1);

         /* Test a task with a timeout that does not complete within its timeout
         is stopped
         */
         task = new TestTask(HOLD_START, "test2", 0, 10*1000, Task.WAIT_NONE);
         task.setRunSleepTime(12*1000);
         pool.runTask(task);
         started.wait(1);
         started.release("test2");
         stopped.wait(1);
         completed.wait(1);

         // Test that another valid task completes as expected
         task = new TestTask(HOLD_START, "test3", 0, 0, Task.WAIT_NONE);
         pool.runTask(task);
         started.wait(1);
         started.release("test3");
         completed.wait(1);

         /* Test a task with a timeout that does not complete within its timeout
         is stopped
         */
         task = new TestTask(HOLD_START, "test4", 0, 10*1000, Task.WAIT_NONE);
         task.setRunSleepTime(12*1000);
         pool.runTask(task);
         started.wait(1);
         started.release("test4");
         stopped.wait(1);         
         completed.wait(1);
      }
      finally
      {
         pool.stop(true);
      }
   }

   public void testCompleteTimeoutWithSpinLoop() throws Exception
   {
      BasicThreadPool pool = new BasicThreadPool();
      pool.setMaximumQueueSize(1);
      pool.setMaximumPoolSize(1);
      try
      {
         /* Test that a task with a timeout that completes within its timeout
         works as expected
         */
         TestTask task = new TestTask(HOLD_START, "test1", 0, 10*1000, Task.WAIT_NONE);
         task.setRunSleepTime(Long.MAX_VALUE);
         pool.runTask(task);
         started.wait(1);
         started.release("test1");
         stopped.wait(1);         
         completed.wait(1);
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
    * Test task
    */
   public class TestTask implements Task
   {
      /** The test to run */
      private int test;
      /** The data for the test */
      private String data;
      /** The start timeout */
      private long startTimeout;
      /** The completion timeout */
      private long completionTimeout;
      /** The time to sleep in execute */
      private long runSleepTime;
      /** The wait type */
      private int waitType;

      /**
       * Create a new TestTask
       *
       * @param test the test
       * @param data the test data
       */
      public TestTask(int test, String data)
      {
         this(test, data, 0, Task.WAIT_NONE);
      }

      /**
       * Create a new TestTask
       *
       * @param test the test
       * @param data the test data
       * @param startTimeout the start timeout
       * @param waitType the wait type
       */
      public TestTask(int test, String data, long startTimeout, int waitType)
      {
         this(test, data, startTimeout, 0, waitType);
      }
      public TestTask(int test, String data, long startTimeout,
         long completionTimeout, int waitType)
      {
         this.test = test;
         this.data = data;
         this.startTimeout = startTimeout;
         this.completionTimeout = completionTimeout;
         this.waitType = waitType;
      }

      public void execute()
      {
         saveThreadName();
         log.info("Start execute");
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
         log.info("End execute");
      }

      public void saveThreadName()
      {
         saveRunnableThreadName(data, Thread.currentThread().getName());
      }

      public void accepted(long time)
      {
         accepted.notify(data, time);
      }

      public void rejected(long time, Throwable throwable)
      {
         rejected.notify(data, time, throwable);
      }

      public void started(long time)
      {
         started.notify(data, time);
         if (test == HOLD_START)
            started.waitForRelease(data);
      }

      public void completed(long time, Throwable throwable)
      {
         completed.notify(data, time, throwable);
      }

      public long getCompletionTimeout()
      {
         return completionTimeout;
      }

      public int getPriority()
      {
         return Thread.NORM_PRIORITY;
      }

      public long getStartTimeout()
      {
         return startTimeout;
      }

      public int getWaitType()
      {
         return waitType;
      }

      public void stop()
      {
         stopped.notify(data);
      }

      public void setRunSleepTime(long runSleepTime)
      {
         this.runSleepTime = runSleepTime;
      }
   }

   public class Stats
   {
      /**
       * The name
       */
      String name;

      /** The tasks */
      HashSet tasks = new HashSet();

      /** The times */
      HashMap times = new HashMap();

      /** The errors */
      HashMap errors = new HashMap();

      /** The releases */
      HashSet releases = new HashSet();

      public Stats(String name)
      {
         this.name = name;
      }

      /**
       * Wait for expected
       */
      public void wait(int target)
         throws InterruptedException
      {
         log.debug(Thread.currentThread().getName() + ": Waiting for " + name + " target=" + target);
         synchronized (ThreadPoolTaskUnitTestCase.this)
         {
            while (tasks.size() < target)
               ThreadPoolTaskUnitTestCase.this.wait();
            log.debug(Thread.currentThread().getName() + ": Waited for " + name + " target=" + target);
         }
      }

      /**
       * Release in waiting
       *
       * @param data the thread
       */
      public void release(String data)
      {
         log.debug(Thread.currentThread().getName() + ": Releasing " + name + " data=" + data);
         synchronized (ThreadPoolTaskUnitTestCase.this)
         {
            releases.add(data);
            ThreadPoolTaskUnitTestCase.this.notifyAll();
            log.debug(Thread.currentThread().getName() + ": Released " + name + " data=" + data);
         }
      }

      /**
       * Wait for release
       */
      public void waitForRelease(String data)
      {
         log.debug(Thread.currentThread().getName() + ": Waiting for release " + name + " data=" + data);
         synchronized (ThreadPoolTaskUnitTestCase.this)
         {
            try
            {
               while (releases.contains(data) == false)
                  ThreadPoolTaskUnitTestCase.this.wait();
            }
            catch (InterruptedException ignored)
            {
            }
            log.debug(Thread.currentThread().getName() + ": Waited for release " + name + " data=" + data);
         }
      }

      /**
       * Notify
       */
      public void notify(String data)
      {
         log.debug(Thread.currentThread().getName() + ": Notifying " + name + " data=" + data);
         synchronized (ThreadPoolTaskUnitTestCase.this)
         {
            tasks.add(data);
            ThreadPoolTaskUnitTestCase.this.notifyAll();
            log.debug(Thread.currentThread().getName() + ": Notified " + name + " data=" + data);
         }
      }

      /**
       * Notify
       */
      public void notify(String data, long time)
      {
         log.debug(Thread.currentThread().getName() + ": Notifying " + name + " data=" + data + " time=" + time);
         synchronized (ThreadPoolTaskUnitTestCase.this)
         {
            tasks.add(data);
            times.put(data, new Long(time));
            ThreadPoolTaskUnitTestCase.this.notifyAll();
         }
         log.debug(Thread.currentThread().getName() + ": Notified " + name + " data=" + data + " time=" + time);
      }

      /**
       * Notify
       */
      public void notify(String data, long time, Throwable throwable)
      {
         if (throwable != null)
            log.debug(Thread.currentThread().getName() + ": Notifying " + name + " data=" + data + " time=" + time, throwable);
         else
            log.debug(Thread.currentThread().getName() + ": Notifying " + name + " data=" + data + " time=" + time + " throwable=null");
         synchronized (ThreadPoolTaskUnitTestCase.this)
         {
            tasks.add(data);
            times.put(data, new Long(time));
            errors.put(data, throwable);
            ThreadPoolTaskUnitTestCase.this.notifyAll();
         }
         if (throwable != null)
            log.debug(Thread.currentThread().getName() + ": Notified " + name + " data=" + data + " time=" + time + " throwable=" + throwable.getMessage());
         else
            log.debug(Thread.currentThread().getName() + ": Notified " + name + " data=" + data + " time=" + time + " throwable=null");
      }

      /**
       * Clear
       */
      public void clear()
      {
         log.debug(Thread.currentThread().getName() + ": Clearing " + name);
         synchronized (ThreadPoolTaskUnitTestCase.this)
         {
            tasks.clear();
            log.debug(Thread.currentThread().getName() + ": Cleared " + name);
         }
      }
   }
}
