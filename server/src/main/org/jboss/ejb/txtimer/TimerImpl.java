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
package org.jboss.ejb.txtimer;

// $Id: TimerImpl.java 88431 2009-05-08 13:36:00Z galder.zamarreno@jboss.com $

import java.io.Serializable;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.ejb.EJBException;
import javax.ejb.NoSuchObjectLocalException;
import javax.ejb.TimerHandle;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;

import org.jboss.ejb.AllowedOperationsAssociation;
import org.jboss.logging.Logger;

/**
 * An implementation of an EJB Timer.
 * 
 * Internally it uses a java.util.Timer and maintains its state in
 * a Tx manner.
 *
 * @author Thomas.Diesler@jboss.org
 * @author Dimitris.Andreadis@jboss.org
 * @version $Revision: 88431 $
 * @since 07-Apr-2004
 */
public class TimerImpl implements javax.ejb.Timer, Synchronization
{
   // logging support
   private static Logger log = Logger.getLogger(TimerImpl.class);

   /**
    * Timer states and their allowed transitions
    * <p/>
    * CREATED  - on create
    * CREATED -> STARTED_IN_TX - when strated with Tx
    * CREATED -> ACTIVE  - when started without Tx
    * STARTED_IN_TX -> ACTIVE - on Tx commit
    * STARTED_IN_TX -> CANCELED - on Tx rollback
    * ACTIVE -> CANCELED_IN_TX - on cancel() with Tx
    * ACTIVE -> CANCELED - on cancel() without Tx
    * CANCELED_IN_TX -> CANCELED - on Tx commit
    * CANCELED_IN_TX -> ACTIVE - on Tx rollback
    * ACTIVE -> IN_TIMEOUT - on TimerTask run
    * IN_TIMEOUT -> ACTIVE - on Tx commit if periode > 0
    * IN_TIMEOUT -> EXPIRED -> on Tx commit if periode == 0
    * IN_TIMEOUT -> RETRY_TIMEOUT -> on Tx rollback
    * RETRY_TIMEOUT -> ACTIVE -> on Tx commit/rollback if periode > 0
    * RETRY_TIMEOUT -> EXPIRED -> on Tx commit/rollback if periode == 0
    */
   private static final int CREATED = 0;
   private static final int STARTED_IN_TX = 1;
   private static final int ACTIVE = 2;
   private static final int CANCELED_IN_TX = 3;
   private static final int CANCELED = 4;
   private static final int EXPIRED = 5;
   private static final int IN_TIMEOUT = 6;
   private static final int RETRY_TIMEOUT = 7;

   private static final String[] TIMER_STATES = {"created", "started_in_tx", "active", "canceled_in_tx",
                                                 "canceled", "expired", "in_timeout", "retry_timeout"};

   // The initial txtimer properties
   private TimerServiceImpl timerService;
   private String timerId;
   private TimedObjectId timedObjectId;
   private TimedObjectInvoker timedObjectInvoker;
   private Date firstTime;
   private long periode;
   private Serializable info;

   private long nextExpire;
   private int timerState;
   private Timer utilTimer;
   private int hashCode;

   /**
    * Schedules the txtimer for execution at the specified time with a specified periode.
    */
   TimerImpl(TimerServiceImpl timerService, String timerId, TimedObjectId timedObjectId, TimedObjectInvoker timedObjectInvoker, Serializable info)
   {
      this.timerService = timerService;
      this.timerId = timerId;
      this.timedObjectId = timedObjectId;
      this.timedObjectInvoker = timedObjectInvoker;
      this.info = info;

      setTimerState(CREATED);
   }

   void startTimer(Date firstTime, long periode)
   {
      this.firstTime = firstTime;
      this.nextExpire = firstTime.getTime();
      this.periode = periode;

      timerService.addTimer(this);
      registerTimerWithTx();
      
      // the timer will actually go ACTIVE on tx commit
      startInTx();
   }

   public String getTimerId()
   {
      return timerId;
   }

   public TimedObjectId getTimedObjectId()
   {
      return timedObjectId;
   }

   public Date getFirstTime()
   {
      return firstTime;
   }

   public long getPeriode()
   {
      return periode;
   }

   public long getNextExpire()
   {
      return nextExpire;
   }

   public Serializable getInfoInternal()
   {
      return info;
   }

   public boolean isActive()
   {
      return !isCanceled() && !isExpired();
   }

   public boolean isInRetry() {
      return timerState == RETRY_TIMEOUT;
   }

   public boolean isCanceled()
   {
      return timerState == CANCELED_IN_TX || timerState == CANCELED;
   }

   public boolean isExpired()
   {
      return timerState == EXPIRED;
   }

   /**
    * Cause the txtimer and all its associated expiration notifications to be cancelled.
    *
    * @throws IllegalStateException  If this method is invoked while the instance is in
    *                                a state that does not allow access to this method.
    * @throws javax.ejb.NoSuchObjectLocalException
    *                                If invoked on a txtimer that has expired or has been cancelled.
    * @throws javax.ejb.EJBException If this method could not complete due to a system-level failure.
    */
   public void cancel() throws IllegalStateException, NoSuchObjectLocalException, EJBException
   {
      assertTimedOut();
      assertAllowedOperation("Timer.cancel");
      registerTimerWithTx();
      cancelInTx();
   }

   /**
    * Kill the timer, and remove it from the timer service
    */
   public void killTimer()
   {
      log.debug("killTimer: " + this);
      if (timerState != EXPIRED)
         setTimerState(CANCELED);
      timerService.removeTimer(this);
      utilTimer.cancel();
   }

   /**
    * killTimer w/o persistence work
    */
   private void cancelTimer()
   {
      if (timerState != EXPIRED)
         setTimerState(CANCELED);
      utilTimer.cancel();
   }

   /**
    * Kill the timer, do not remove from timer service
    */
   public void stopTimer()
   {
      log.debug("stopTimer: " + this);
      if (timerState != EXPIRED)
         setTimerState(CANCELED);
      utilTimer.cancel();      
   }
   
   /**
    * Get the number of milliseconds that will elapse before the next scheduled txtimer expiration.
    *
    * @return Number of milliseconds that will elapse before the next scheduled txtimer expiration.
    * @throws IllegalStateException  If this method is invoked while the instance is in
    *                                a state that does not allow access to this method.
    * @throws javax.ejb.NoSuchObjectLocalException
    *                                If invoked on a txtimer that has expired or has been cancelled.
    * @throws javax.ejb.EJBException If this method could not complete due to a system-level failure.
    */
   public long getTimeRemaining() throws IllegalStateException, NoSuchObjectLocalException, EJBException
   {
      assertTimedOut();
      assertAllowedOperation("Timer.getTimeRemaining");
      return nextExpire - System.currentTimeMillis();
   }

   /**
    * Get the point in time at which the next txtimer expiration is scheduled to occur.
    *
    * @return Get the point in time at which the next txtimer expiration is scheduled to occur.
    * @throws IllegalStateException  If this method is invoked while the instance is in
    *                                a state that does not allow access to this method.
    * @throws javax.ejb.NoSuchObjectLocalException
    *                                If invoked on a txtimer that has expired or has been cancelled.
    * @throws javax.ejb.EJBException If this method could not complete due to a system-level failure.
    */
   public Date getNextTimeout() throws IllegalStateException, NoSuchObjectLocalException, EJBException
   {
      assertTimedOut();
      assertAllowedOperation("Timer.getNextTimeout");
      return new Date(nextExpire);
   }

   /**
    * Get the information associated with the txtimer at the time of creation.
    *
    * @return The Serializable object that was passed in at txtimer creation, or null if the
    *         info argument passed in at txtimer creation was null.
    * @throws IllegalStateException  If this method is invoked while the instance is in
    *                                a state that does not allow access to this method.
    * @throws javax.ejb.NoSuchObjectLocalException
    *                                If invoked on a txtimer that has expired or has been cancelled.
    * @throws javax.ejb.EJBException If this method could not complete due to a system-level failure.
    */
   public Serializable getInfo() throws IllegalStateException, NoSuchObjectLocalException, EJBException
   {
      assertTimedOut();
      assertAllowedOperation("Timer.getInfo");
      return info;
   }

   /**
    * Get a serializable handle to the txtimer. This handle can be used at a later time to
    * re-obtain the txtimer reference.
    *
    * @return Handle of the Timer
    * @throws IllegalStateException  If this method is invoked while the instance is in
    *                                a state that does not allow access to this method.
    * @throws javax.ejb.NoSuchObjectLocalException
    *                                If invoked on a txtimer that has expired or has been cancelled.
    * @throws javax.ejb.EJBException If this method could not complete due to a system-level failure.
    */
   public TimerHandle getHandle() throws IllegalStateException, NoSuchObjectLocalException, EJBException
   {
      assertTimedOut();
      assertAllowedOperation("Timer.getHandle");
      return new TimerHandleImpl(this);
   }

   /**
    * Return true if objectId, createDate, periode are equal
    */
   public boolean equals(Object obj)
   {
      if (obj == this) return true;
      if (obj instanceof TimerImpl)
      {
         TimerImpl other = (TimerImpl)obj;
         return hashCode() == other.hashCode();
      }
      return false;
   }

   /**
    * Hash code based on the Timers invariant properties
    */
   public int hashCode()
   {
      if (hashCode == 0)
      {
         String hash = "[" + timerId + "," + timedObjectId + "," + firstTime + "," + periode + "]";
         hashCode = hash.hashCode();
      }
      return hashCode;
   }

   /**
    * Returns a string representation of the object.
    */
   public String toString()
   {
      long remaining = nextExpire - System.currentTimeMillis();
      String retStr = "[id=" + timerId + ",target=" + timedObjectId + ",remaining=" + remaining + ",periode=" + periode +
              "," + TIMER_STATES[timerState] + "]";
      return retStr;
   }

   /**
    * Register the txtimer with the current transaction
    */
   private void registerTimerWithTx()
   {
      Transaction tx = timerService.getTransaction();
      if (tx != null)
      {
         try
         {
            tx.registerSynchronization(this);
         }
         catch (Exception e)
         {
            log.error("Cannot register txtimer with Tx: " + this);
         }
      }
   }

   private void setTimerState(int state)
   {
      log.debug("setTimerState: " + TIMER_STATES[state]);
      timerState = state;
   }

   private void startInTx()
   {
      // JBAS-4330, provide a meaningful name to the timer thread, needs jdk5+
      utilTimer = new Timer("EJB-Timer-" + timerId + timedObjectId);
      
      if (timerService.getTransaction() != null)
      {
         // don't schedule the timeout yet
         setTimerState(STARTED_IN_TX);
      }
      else
      {
         setTimerState(ACTIVE);
         scheduleTimeout();
      }
   }

   private void cancelInTx()
   {
      if (timerService.getTransaction() != null)
         setTimerState(CANCELED_IN_TX);
      else
         killTimer();
   }

   private void scheduleTimeout()
   {
      if (periode > 0)
         utilTimer.schedule(new TimerTaskImpl(this), new Date(nextExpire), periode);
      else
         utilTimer.schedule(new TimerTaskImpl(this), new Date(nextExpire));      
   }
   
   /**
    * Throws NoSuchObjectLocalException if the txtimer was canceled or has expired
    */
   private void assertTimedOut()
   {
      if (timerState == EXPIRED)
         throw new NoSuchObjectLocalException("Timer has expired");
      if (timerState == CANCELED_IN_TX || timerState == CANCELED)
         throw new NoSuchObjectLocalException("Timer was canceled");
   }

   /**
    * Throws an IllegalStateException if the Timer method call is not allowed in the current context
    */
   private void assertAllowedOperation(String timerMethod)
   {
      AllowedOperationsAssociation.assertAllowedIn(timerMethod,
              AllowedOperationsAssociation.IN_BUSINESS_METHOD |
              AllowedOperationsAssociation.IN_EJB_TIMEOUT |
              AllowedOperationsAssociation.IN_SERVICE_ENDPOINT_METHOD |
              AllowedOperationsAssociation.IN_AFTER_BEGIN |
              AllowedOperationsAssociation.IN_BEFORE_COMPLETION |
              AllowedOperationsAssociation.IN_EJB_POST_CREATE |
              AllowedOperationsAssociation.IN_EJB_REMOVE |
              AllowedOperationsAssociation.IN_EJB_LOAD |
              AllowedOperationsAssociation.IN_EJB_STORE);
   }

   // Synchronization **************************************************************************************************

   /**
    * This method is invoked before the start of the commit or rollback
    * process. The method invocation is done in the context of the
    * transaction that is about to be committed or rolled back.
    */
   public void beforeCompletion()
   {
      switch(timerState)
      {
         case CANCELED_IN_TX:
            timerService.removeTimer(this);
            break;

         case IN_TIMEOUT:
         case RETRY_TIMEOUT:
            if(periode == 0)
            {
               timerService.removeTimer(this);
            }
            break;
      }
   }

   /**
    * This method is invoked after the transaction has committed or
    * rolled back.
    *
    * @param status The status of the completed transaction.
    */
   public void afterCompletion(int status)
   {
      if (status == Status.STATUS_COMMITTED)
      {
         log.debug("commit: " + this);

         switch (timerState)
         {
            case STARTED_IN_TX:
               scheduleTimeout();
               setTimerState(ACTIVE);
               break;

            case CANCELED_IN_TX:
               cancelTimer();
               break;

            case IN_TIMEOUT:
            case RETRY_TIMEOUT:
               if(periode == 0)
               {
                  setTimerState(EXPIRED);
                  cancelTimer();
               }
               else
               {
                  setTimerState(ACTIVE);
               }
               break;
         }
      }
      else if (status == Status.STATUS_ROLLEDBACK)
      {
         log.debug("rollback: " + this);

         switch (timerState)
         {
            case STARTED_IN_TX:
               cancelTimer();
               break;
               
            case CANCELED_IN_TX:
               setTimerState(ACTIVE);
               break;
               
            case IN_TIMEOUT:
               setTimerState(RETRY_TIMEOUT);
               log.debug("retry: " + this);
               timerService.retryTimeout(this);
               break;
               
            case RETRY_TIMEOUT:
               if (periode == 0)
               {
                  setTimerState(EXPIRED);
                  cancelTimer();
               }
               else
               {
                  setTimerState(ACTIVE);
               }
               break;
         }
      }
   }
      
   // TimerTask ********************************************************************************************************

   /**
    * The TimerTask's run method is invoked by the java.util.Timer
    */
   private class TimerTaskImpl extends TimerTask
   {
      private TimerImpl timer;

      public TimerTaskImpl(TimerImpl timer)
      {
         this.timer = timer;
      }

      /**
       * The action to be performed by this txtimer task.
       */
      public void run()
      {
         log.debug("run: " + timer);

         // Set next scheduled execution attempt. This is used only
         // for reporting (getTimeRemaining()/getNextTimeout())
         // and not from the underlying jdk timer implementation.
         if (isActive() && periode > 0)
         {
            nextExpire += periode;
         }
         
         // If a retry thread is in progress, we don't want to allow another
         // interval to execute until the retry is complete. See JIRA-1926.
         if (isInRetry())
         {
            log.debug("Timer in retry mode, skipping this scheduled execution");
            return;
         }
         
         if (isActive())
         {
            try
            {
               setTimerState(IN_TIMEOUT);
               timedObjectInvoker.callTimeout(timer);
            }
            catch (Exception e)
            {
               log.error("Error invoking ejbTimeout", e);
            }
            finally
            {
               if (timerState == IN_TIMEOUT)
               {
                  log.debug("Timer was not registered with Tx, resetting state: " + timer);
                  if (periode == 0)
                  {
                     setTimerState(EXPIRED);
                     killTimer();
                  }
                  else
                  {
                     setTimerState(ACTIVE);
                  }
               }
            }
         }
      }
   }
}
