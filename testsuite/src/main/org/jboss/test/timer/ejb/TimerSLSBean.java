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
package org.jboss.test.timer.ejb;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.io.*;

import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.ejb.TimedObject;
import javax.ejb.Timer;
import javax.ejb.TimerHandle;
import javax.ejb.TimerService;
import javax.ejb.EJBException;
import javax.ejb.NoSuchObjectLocalException;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServer;

import org.jboss.logging.Logger;
import org.jboss.test.timer.interfaces.TimerSLSB;
import org.jboss.ejb.txtimer.FixedDelayRetryPolicyMBean;

/**
 * Stateless Session Bean Timer Test
 *
 * @ejb:bean name="test/timer/TimerSLSB"
 *           display-name="Timer in Stateless Session Bean"
 *           type="Stateless"
 *           transaction-type="Container"
 *           view-type="remote"
 *           jndi-name="ejb/test/timer/TimerSLSB"
 *
 * @ejb:transaction type="Required"
 * @author Thomas Diesler
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 **/
public class TimerSLSBean
   implements SessionBean, TimedObject
{
   // -------------------------------------------------------------------------
   // Static
   // -------------------------------------------------------------------------
   private static HashMap timeoutCounts = new HashMap();
   private static Logger log = Logger.getLogger(TimerSLSBean.class);

   // -------------------------------------------------------------------------
   // Members
   // -------------------------------------------------------------------------
   private SessionContext context;

   // -------------------------------------------------------------------------
   // Methods
   // -------------------------------------------------------------------------

  /**
    * Start a single timer (if not already set) with the start date plus the period
    * Uses the string &quot;TimerSLSBean.startSingleTimer&quot; as the timer info data.
    *
    * @param pPeriod Time that will elapse between now and the timed event in milliseconds
    *
    * @ejb:interface-method view-type="remote"
    **/
   public byte[] startSingleTimer(long pPeriod)
   {
      return startSingleTimer(pPeriod,"TimerSLSBean.startSingleTimer");
   }

    /**
    * Start a single timer (if not already set) with the start date plus the period and specified info.
    *
    * @param pPeriod Time that will elapse between now and the timed event in milliseconds
    * @param info an object to be used as the info for the timer.
    *
    * @ejb:interface-method view-type="remote"
    **/
   public byte[] startSingleTimer(long pPeriod, Serializable info)
   {
      log.info("TimerSLSBean.startSingleTimer(), try to get a Timer Service from the Session Context");
      TimerService ts = context.getTimerService();
      long exp = System.currentTimeMillis() + pPeriod;
      Timer timer = ts.createTimer(new Date(exp), info);
      log.info("TimerSLSBean.startSingleTimer(), create a timer: "+timer);
      byte[] handle = getHandle(timer);
      return handle;
   }

   /**
    * Start a timer (if not already set) with the start date plus the period
    * and an interval of the given period
    * Uses the string &quot;TimerSLSBean.startTimer&quot; as the timer info data.
    *
    * @param pPeriod Time that will elapse between two events in milliseconds
    *
    * @ejb:interface-method view-type="remote"
    **/
   public byte[] startTimer(long pPeriod)
   {
      return startTimer(pPeriod, "TimerSLSBean.startTimer");
   }

     /**
    * Start a timer (if not already set) with the start date plus the period
    * and an interval of the given period
    *
    * @param pPeriod Time that will elapse between two events in milliseconds
    * @param info an object to be used as the info for the timer.
    *
    * @ejb:interface-method view-type="remote"
    **/
   public byte[] startTimer(long pPeriod, Serializable info)
   {
      log.info("TimerSLSBean.startTimer(), try to get a Timer Service from the Session Context");
      TimerService ts = context.getTimerService();
      long exp = System.currentTimeMillis() + pPeriod;
      Timer timer = ts.createTimer(new Date(exp), pPeriod, info);
      log.info("TimerSLSBean.startTimer(), create a timer: "+timer);
      byte[] handle = getHandle(timer);
      return handle;
   }

   /**
    * @ejb:interface-method view-type="remote"
    **/
   public void stopTimer(byte[] handle)
   {
      Timer timer = getTimer(handle);
      timer.cancel();
      log.info("TimerSLSBean.stopTimer(), create a timer: "+timer);
      synchronized( TimerSLSBean.class )
      {
         Long key = getKey(handle);
         timeoutCounts.remove(key);
      }
   }

   /**
    * @ejb:interface-method view-type="remote"
    **/
   public int getTimeoutCount(byte[] handle)
   {
      Integer count = null;
      try
      {
         Long key = getKey(handle);         
         count = (Integer) timeoutCounts.get(key);
      }
      catch(NoSuchObjectLocalException e)
      {
         // Expected if the timer has been stopped
      }
      log.info("TimerSLSBean.getTimeoutCount(): " + count);
      return count !=  null ? count.intValue() : 0;
   }

   /**
    * @return Date of the next timed event
    *
    * @ejb:interface-method view-type="remote"
    **/
   public Date getNextTimeout(byte[] handle)
   {
      Timer timer = getTimer(handle);
      return timer.getNextTimeout();
   }

   /**
    * @return Time remaining until next timed event in milliseconds
    *
    * @ejb:interface-method view-type="remote"
    **/
   public long getTimeRemaining(byte[] handle)
   {
      Timer timer = getTimer(handle);
      return timer.getTimeRemaining();
   }

   /**
    * @return User object of the timer
    *
    * @ejb:interface-method view-type="remote"
    **/
   public Object getInfo(byte[] handle)
   {
      Timer timer = getTimer(handle);
      return timer.getInfo();
   }

   /**
    * Create the Session Bean
    *
    * @ejb:create-method view-type="both"
    **/
   public void ejbCreate()
   {
      log.info("TimerSLSBean.ejbCreate()");
   }

   public void ejbTimeout(Timer timer)
   {
      Integer count = null;
      Long key = null;
      synchronized( TimerSLSBean.class )
      {
         log.debug("ejbTimeout(): Timer State:" + timer);
         byte[] handle = getHandle(timer);
         key = getKey(handle);
         count = (Integer) timeoutCounts.get(key);
         if( count == null )
            count = new Integer(1);
         else
            count = new Integer(1 + count.intValue());
         timeoutCounts.put(key, count);
         log.info("ejbTimeout(): count for timer handle " + key + " is " + count);
      }

      log.info("ejbTimeout(), timer: " + timer+", key: "+key+", count: "+count);

      Object info = timer.getInfo();
      if(info instanceof Map) {
         Map mInfo = ((Map)info);
         Integer failCount = (Integer) mInfo.get(TimerSLSB.INFO_EXEC_FAIL_COUNT);
         Integer taskTime = (Integer) mInfo.get(TimerSLSB.INFO_TASK_RUNTIME);

         // If the timer is supposed to fail (testing the retry mechanism)
         // then we simply rollback the trans. Note this will still increase
         // the timeoutCounts which is what we want.
         if(failCount != null && count.compareTo(failCount) <= 0) {
            log.info("ejbTimeout(): Failing timeout because '" + TimerSLSB.INFO_EXEC_FAIL_COUNT
                  + "' is set to " + failCount + " and count is " + count);
            context.setRollbackOnly();
            return;
         }

         // Make method simulate a long running task
         // This is used to test the case in JBAS-1926
         if(taskTime != null) {
            try
            {
               log.info("ejbTimeout(): Simulating long task ("+ taskTime +"ms)");
               Thread.sleep(taskTime.intValue());
            }
            catch (InterruptedException e) {}
         }
      }
   }

   /**
    * Describes the instance and its content for debugging purpose
    *
    * @return Debugging information about the instance and its content
    **/
   public String toString()
   {
      return "TimerSLSBean [ " + " ]";
   }

   // -------------------------------------------------------------------------
   // Framework Callbacks
   // -------------------------------------------------------------------------

   public void setSessionContext(SessionContext aContext)
   {
      context = aContext;
   }

   public void ejbActivate()
   {
   }

   public void ejbPassivate()
   {
   }

   public void ejbRemove()
   {
   }

   private Long getKey(byte[] handle)
   {
      long key = 0;
      for(int n = 0; n < handle.length; n ++)
         key += handle[n];
      log.info("HandleKey: "+key);
      return new Long(key);
   }
   private byte[] getHandle(Timer timer)
      throws EJBException
   {
      try
      {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(baos);
         oos.writeObject(timer.getHandle());
         oos.close();
         byte[] handle = baos.toByteArray();
         return handle;
      }
      catch (Exception e)
      {
         throw new EJBException("Failed to get timer from handle", e);
      }
   }
   private Timer getTimer(byte[] handle)
      throws NoSuchObjectLocalException, EJBException
   {
      try
      {
         ByteArrayInputStream bais = new ByteArrayInputStream(handle);
         ObjectInputStream ois = new ObjectInputStream(bais);
         TimerHandle th = null;
         th = (TimerHandle) ois.readObject();
         ois.close();
         Timer timer = th.getTimer();
         return timer;
      }
      catch(NoSuchObjectLocalException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new EJBException("Failed to get timer from handle", e);
      }
   }

   /**
    * Returns the value from the RetryPolicyMBean. This is used by unit tests to help determine timing
    * for some of the tests, specifically, those that test the fix for JBAS-1926.
    */
   public long getRetryTimeoutPeriod() {
      List lServers = MBeanServerFactory.findMBeanServer( null );
      MBeanServer lServer = (MBeanServer) lServers.get( 0 );
      try
      {
         Long val = (Long) lServer.getAttribute(FixedDelayRetryPolicyMBean.OBJECT_NAME, "Delay");
         return val.longValue();
      }
      catch (Exception e)
      {
         log.error(e);
         e.printStackTrace();

         return -1;
      }
   }
}
