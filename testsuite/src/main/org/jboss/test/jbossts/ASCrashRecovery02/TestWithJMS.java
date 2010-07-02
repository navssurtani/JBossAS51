/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 *
 * (C) 2008,
 * @author JBoss Inc.
 */
package org.jboss.test.jbossts.ASCrashRecovery02;

import org.jboss.test.jbossts.recovery.ASFailureSpec;
import org.jboss.test.jbossts.recovery.CrashHelperRem;
import org.jboss.test.jbossts.recovery.RecoveredXid;
import org.jboss.test.jbossts.taskdefs.JUnitClientTest;
import org.jboss.test.jbossts.taskdefs.TransactionLog;
import org.jboss.test.jbossts.taskdefs.Utils;
import org.jboss.test.jbossts.jms.JMSCrashHelper;
import org.jboss.test.jbossts.jms.JMSCrashRem;
import org.jboss.remoting.CannotConnectException;
import org.apache.tools.ant.BuildException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ejb.EJBTransactionRolledbackException;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

/**
 * Crash recovery tests with JMS.
 * 
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 * @version $Revision: 1.1 $
 */
public class TestWithJMS extends JUnitClientTest
{
   // the longest time to wait in millis before declaring a test a failed (overridable)
   private static final int MAX_TEST_TIME = 5 * 60 * 1000;     // 5 minutes  - allows two intervals of recovery which is 2 minutes by default

   private boolean isCMT = false;
   private boolean clientTx = false;
   private boolean expectFailure = false;
   private boolean reverseOrder = false;
   private boolean rollbackExpected = false;
   private boolean wipeOutTxsInDoubt = false;
   private boolean wipeOutTxsInDoubtBeforeTest = false;
   private boolean wipeOutTxsInDoubtAfterTest = false;
   private int maxTestTime = MAX_TEST_TIME;

   private String storeDir = null;
   private String storeImple = "HashedActionStore";
   private String storeType = "StateManager/BasicAction/TwoPhaseCoordinator/AtomicAction";
   private TransactionLog store;
   private int existingUids;
   private Set<RecoveredXid> existingXidsInDoubt;

   private String serverName = "default";

   
   public void testAction()
   {
      if (config == null || params == null)
         throw new UnsupportedOperationException("The test has not been initiated yet. Call the init() method first.");

      StringBuilder sb = new StringBuilder();
      ASFailureSpec[] fspecs = null;

      for (Map.Entry<String, String> me : params.entrySet())
      {
         String key = me.getKey().trim();
         String val = me.getValue().trim();

         if ("name".equals(key))
            setName(val);
         else if ("cmt".equals(key))
            isCMT = val.equalsIgnoreCase("true");
         else if ("debug".equals(key))
            isDebug = val.equalsIgnoreCase("true");
         else if ("serverName".equals(key))
            serverName = val;
         else if ("storeType".equals(key))
            storeType = val;
         else if ("storeDir".equals(key))
            storeDir = val;
         else if ("clientTx".equals(key))
            clientTx = val.equalsIgnoreCase("true");
         else if ("storeImple".equals(key))
            storeImple = val;
         else if ("testTime".equals(key))
            maxTestTime = Utils.parseInt(val, "parameter testTime should represent a number of miliseconds: ");
         else if ("specs".equals(key))
            fspecs = parseSpecs(val, sb);
         else if ("wait".equals(key))
            suspendFor(Integer.parseInt(val));
         else if ("reverseOrder".equals(key))
            reverseOrder = val.equalsIgnoreCase("true");
         else if ("rollbackExpected".equals(key))
            rollbackExpected = val.equalsIgnoreCase("true");
         else if ("wipeOutTxsInDoubt".equals(key))
            wipeOutTxsInDoubt = val.equalsIgnoreCase("true");
         else if ("wipeOutTxsInDoubtBeforeTest".equals(key))
            wipeOutTxsInDoubtBeforeTest = val.equalsIgnoreCase("true");
         else if ("wipeOutTxsInDoubtAfterTest".equals(key))
            wipeOutTxsInDoubtAfterTest = val.equalsIgnoreCase("true");
      }

      sb.insert(0, ":\n").insert(0, getName()).insert(0, "Executing test ");

      System.out.println(sb);

      try 
      {
         String serverPath = config.getServerPath(serverName);

         // get a handle to the transaction logs
         if (storeDir == null)
            storeDir = serverPath + "data/tx-object-store";
         else
            storeDir = serverPath + storeDir;
         System.out.println("transaction log will be stored in " + storeDir + "(file=" + storeImple+")");
         store = new TransactionLog(storeDir, storeImple);

         if (expectFailure)
         {
            // this test may halt the VM so make sure the transaction log is empty
            // before starting the test - then the pass/fail check is simply to
            // test whether or not the log is empty (see recoverUids() below).
            try
            {
               store.clearXids(storeType);
            }
            catch (Exception ignore)
            {
            }

         }
         
         existingUids = getPendingUids();

         if (wipeOutTxsInDoubtBeforeTest)
            wipeOutTxsInDoubt();

         existingXidsInDoubt = lookupCrashHelper().checkXidsInDoubt();
         if (existingXidsInDoubt.size() > 0)
            print(existingXidsInDoubt.size() + " txs in doubt before test run");

         String message = getName();
         
         // run the crash test
         boolean result = crashTest(message, fspecs, reverseOrder);
         print("crashTest result: " + result);

         // checking the state of JMS after recovering
         boolean jmsResult = true;
         if (result)
         {
            jmsResult = checkJMS(message);
            print("checkJMS result: " + jmsResult);
         }

         Set<RecoveredXid> xidsInDoubtAfterTest = lookupCrashHelper().checkXidsInDoubt();
         if (wipeOutTxsInDoubt || wipeOutTxsInDoubtAfterTest)
            wipeOutTxsInDoubt(existingXidsInDoubt, xidsInDoubtAfterTest);
         
         assertTrue("Crash recovery failed.", result);
         assertTrue("Incorrect JMS state after crash recovery.", jmsResult);
         assertEquals("There are still unrecovered txs in JMS after crash recovery.", existingXidsInDoubt.size(), xidsInDoubtAfterTest.size());
      }
      catch (Exception e)
      {
         if (isDebug)
            e.printStackTrace();

         throw new BuildException(e);
      }
   }

   private boolean checkJMS(String sentMessage)
   {
      String receivedMessage = null;
      
      try
      {
         receivedMessage = receiveMessage();
      }
      catch (Exception e)
      {
         return false;
      }
      
      return (rollbackExpected) ? (receivedMessage == null) : sentMessage.equals(receivedMessage);
   }

   private String receiveMessage() throws Exception
   {
      Context context = null;
      Connection connection = null;
      try
      {
         context = config.getNamingContext(serverName);

         ConnectionFactory cf = (ConnectionFactory) context.lookup("/ConnectionFactory");

         Queue queue = (Queue) context.lookup("queue/crashRecoveryQueue");

         connection = cf.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumer = session.createConsumer(queue);

         connection.start();

         print("waiting to receive a message from queue/crashRecoveryQueue...");
         TextMessage message = (TextMessage) consumer.receive(5 * 1000);
         
         if (isDebug)
            print("received message: " + ((message != null) ? message.getText() : message));
         
         return (message != null) ? message.getText() : null;
      }
      catch (Exception e)
      {
         print("Error in receiving a message: " + e);
         e.printStackTrace();
         throw e;
      }
      finally
      {
         if (connection != null)
         {
            try
            {
               connection.close();
            }
            catch (JMSException e)
            {
               e.printStackTrace();
            }
         }
      }
   }

   private boolean crashTest(String message, ASFailureSpec[] sa, boolean reverseOrder) throws Exception
   {
      UserTransaction tx = null;

      try
      {
         JMSCrashRem cr = lookupCrashBean(isCMT ? JMSCrashRem.CMT_JNDI_NAME : JMSCrashRem.BMT_JNDI_NAME);

         if (clientTx)
            tx = startTx();

         String res = cr.testXA(message, reverseOrder, sa);

         return "Passed".equalsIgnoreCase(res);
      }
      catch (CannotConnectException e)
      {
         if (expectFailure)
         {
            print("Failure was expected: " + e.getMessage());

            return recoverUids();
         }
         else
         {
            System.err.println("XACrashTest:crashTest: Caught[1] " + e);

            e.printStackTrace();
         }
      }
      catch (EJBTransactionRolledbackException re)
      {
         // try to recover, this failure was expected maybe?!
         print("Failure was expected (maybe): " + re.getMessage());

         return recoverUids();
      }
      catch (RuntimeException re)
      {
         if (re.getCause() instanceof HeuristicMixedException)
         {
            // try to recover, this failure was expected maybe?!
            print("Failure was expected (maybe): " + re.getMessage());

            return recoverUids();
         }
         else
         {
            System.err.println("XACrashTest:crashTest: Caught[2] " + re);
            re.printStackTrace();            
         }
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         System.err.println("XACrashTest:crashTest: Caught[3] " + t);
      }
      finally {
         if (clientTx)
            try
         {
               tx.commit();
         }
         catch (Throwable e)
         {
            System.out.println("User tx commit failure: " + e.getMessage());
         }
      }

      return false;
   }

   /**
    * Wait for any pending transactions to recover by restarting the AS.
    * @return true if all pending branches have been recovered
    * @throws IOException if the server cannot be started
    */
   private boolean recoverUids() throws IOException
   {
      int retryPeriod = 60000;    // 1 minute 
      int maxWait = maxTestTime;
      
      Set<RecoveredXid> xidsInDoubtAfterTest;
      int pendingUids;
      int pendingXidsInDoubt;
      int totalExistingXidsInDoubt = existingXidsInDoubt.size();
      
      // wait for the server to start up the first time through, we will need it for later checking
      suspendFor(2000);   // short waiting is needed sometimes in order to be able to start the server again, 2 secs
      config.startServer(serverName);

      do
      {
         pendingUids = getPendingUids();
         try 
         {
            xidsInDoubtAfterTest = lookupCrashHelper().checkXidsInDoubt();
         }
         catch (Exception e)
         {
            e.printStackTrace();
            return false;
         }
         pendingXidsInDoubt = xidsInDoubtAfterTest.size();

         if (pendingUids == -1)
         {
            print("recoverUids failed, object store error, pendingUids == -1");
            return false;   // object store error
         }
         if (pendingUids <= existingUids && pendingXidsInDoubt <= totalExistingXidsInDoubt)
         {
            print("recoverUids success");
            return true;    // all uids in AS recovered
         }
         pendingUids -= existingUids;
         pendingXidsInDoubt -= totalExistingXidsInDoubt;

         print("waiting for " + pendingUids + " branches");
         print("waiting for " + pendingXidsInDoubt + " txs in doubt");

         suspendFor(retryPeriod);
         maxWait -= retryPeriod;
         
      } while (maxWait > 0);

      print("recoverUids failed, took too long to recover");

      // the test failed to recover some uids - clear them out ready for the next test
      if (pendingUids > 0)
      {
         try
         {
            store.clearXids(storeType);
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
      }

      // the test failed to recover some xids in JMS - clear them out ready for the next test
      if (pendingXidsInDoubt > 0) 
      {
         print(pendingXidsInDoubt + " new txs in doubt after the test");
         
         if (wipeOutTxsInDoubt || wipeOutTxsInDoubtAfterTest)
            wipeOutTxsInDoubt(existingXidsInDoubt, xidsInDoubtAfterTest);
      }

      return false;
   }

   /**
    * Wipes out all the txs in doubt.
    * 
    * @return true in success, fail otherwise
    */
   private boolean wipeOutTxsInDoubt()
   {
      // wipes out all txs in doubt
      return wipeOutTxsInDoubt(null);
   }

   /**
    * Wipes out only new txs in doubt after test run.
    *
    * @param xidsInDoubtBeforeTest txs in doubt before test run
    * @param xidsInDoubtBeforeTest txs in doubt after test run
    * @return true in success, fail otherwise
    */
   private boolean wipeOutTxsInDoubt(Set<RecoveredXid> xidsInDoubtBeforeTest, Set<RecoveredXid> xidsInDoubtAfterTest)
   {
      Set<RecoveredXid> xidsToRecover = new HashSet<RecoveredXid>(xidsInDoubtAfterTest);
      xidsToRecover.removeAll(xidsInDoubtBeforeTest);
      
      if (xidsToRecover.isEmpty())
         return true;
      
      return wipeOutTxsInDoubt(xidsToRecover);
   }

   /**
    * Wipes out txs in doubt according to a xidsToRecover list.
    * 
    * @param xidsToRecover list of xids to recover
    * @return true in success, fail otherwise
    */
   private boolean wipeOutTxsInDoubt(Set<RecoveredXid> xidsToRecover)
   {
      print("wiping out txs in doubt");
      try
      {
         lookupCrashHelper().wipeOutTxsInDoubt(xidsToRecover);
      }  
      catch (Exception e)
      {
         e.printStackTrace();
      }
      return false;
   }

   private ASFailureSpec[] parseSpecs(String specArg, StringBuilder sb)
   {
      ASFailureSpec[] fspecs = config.parseSpecs(specArg);

      for (ASFailureSpec spec : fspecs)
      {
         String name = (spec == null ? "INVALID" : spec.getName());

         if (spec != null && spec.willTerminateVM())
            expectFailure = true;

         sb.append("\t").append(name).append('\n');
      }

      return fspecs;
   }

   // count how many pending transaction branches there are in the transaction log
   private int getPendingUids()
   {
      try
      {
         return store.getIds(storeType).size();
      }
      catch (Exception e)
      {
         e.printStackTrace();

         return -1;
      }
   }

   private JMSCrashRem lookupCrashBean(String name) throws Exception
   {
      return (JMSCrashRem) config.getNamingContext(serverName).lookup(name);
   }

   private CrashHelperRem lookupCrashHelper() throws Exception
   {
      return (CrashHelperRem) config.getNamingContext(serverName).lookup(JMSCrashHelper.REMOTE_JNDI_NAME);
   }

   private UserTransaction startTx() throws NamingException, SystemException, NotSupportedException
   {
      UserTransaction tx = (UserTransaction) config.getNamingContext(serverName).lookup("UserTransaction");

      tx.begin();

      return tx;
   }

}
