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

import javax.management.Attribute;
import javax.management.ObjectName;

import junit.framework.Test;

import org.jboss.logging.Logger;
import org.jboss.test.JBossTestCase;
import org.jboss.test.jca.bank.interfaces.Account;
import org.jboss.test.jca.bank.interfaces.Teller;
import org.jboss.test.jca.bank.interfaces.TellerHome;

/**
 * CachedConnectionBankStressTestCase.java
 * Tests connection disconnect-reconnect mechanism.
 *
 * Created: Mon Mar 18 07:57:41 2002
 *
 * @author <a href="mailto:d_jencks@users.sourceforge.net">David Jencks</a>
 * @version
 */

public class CachedConnectionBankStressTestCase extends JBossTestCase
{

   private TellerHome th;
   private Teller t;

   private Exception exc;

   private int iter;

   public CachedConnectionBankStressTestCase (String name)
   {
      super(name);
   }

   protected void setUp() throws Exception
   {
      super.setUp();
      ObjectName CCM = new ObjectName("jboss.jca:service=CachedConnectionManager");
      getServer().setAttribute(CCM, new Attribute("SpecCompliant", Boolean.TRUE));
      th = (TellerHome)getInitialContext().lookup("Teller");
      t = th.create();
      t.setUp();
   }

   protected void tearDown() throws Exception
   {
      if (t != null)
      {
         t.tearDown();
      } // end of if ()
      ObjectName CCM = new ObjectName("jboss.jca:service=CachedConnectionManager");
      getServer().setAttribute(CCM, new Attribute("SpecCompliant", Boolean.FALSE));

   }

   public static Test suite() throws Exception
   {
      return getDeploySetup(CachedConnectionBankStressTestCase.class, "jcabanktest.jar");
   }

   public void testCachedConnectionBank() throws Exception
   {
      Account[] accounts = new Account[getThreadCount()];
      for (int i = 0; i < getThreadCount(); i++)
      {
         accounts[i] = t.createAccount(new Integer(i));
      } // end of for ()
      final Object lock = new Object();

      iter = 0;
      getLog().info("Start test. "+getThreadCount()+ " threads, "+getIterationCount()+" iterations");
      long start = System.currentTimeMillis();

      for (int i = 0; i < getThreadCount() - 1; i++)
      {
         //Thread.sleep(500); // Wait between each client
         new Thread(new TransferThread(accounts[i],
                            accounts[(i + 1) % getThreadCount()],
                            getIterationCount(),
                            lock)).start();
         synchronized (lock)
         {
            iter++;
         }
      }

      synchronized(lock)
      {
         while(iter > 0)
         {
            lock.wait();
         }
      }

      if (exc != null) throw exc;

      for (int i = 1; i < getThreadCount() - 1; i++)
      {
         assertTrue("nonzero final balance for" + i, accounts[i].getBalance() == 0);
      } // end of for ()


      long end = System.currentTimeMillis();

      getLog().info("Time:"+(end-start));
      getLog().info("Avg. time/call(ms):"+((end-start)/(getThreadCount()*getIterationCount())));
}




   public class TransferThread implements Runnable
   {
      Logger log = Logger.getLogger(getClass().getName());
      Account to;
      Account from;
      int iterationCount;
      Object lock;

      public TransferThread(final Account to,
                            final Account from,
                            final int iterationCount,
                            final Object lock) throws Exception
      {
         this.to = to;
         this.from = from;
         this.iterationCount = iterationCount;
         this.lock = lock;
      }

      public void run()
      {
         try
         {

            for (int j = 0; j < iterationCount; j++)
            {
               if (exc != null) break;

               t.transfer(from,to, 1);
            }
         } catch (Exception e)
         {
            exc = e;
         }

         synchronized(lock)
         {
            iter--;
            log.info("Only "+iter+" left");
            lock.notifyAll();
         }
      }
   }
}// CachedConnectionSessionUnitTestCase
