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
package org.jboss.test.tm.ejb;

import javax.ejb.EJBException;
import javax.naming.InitialContext;
import javax.transaction.Status;
import javax.transaction.TransactionManager;

import org.jboss.test.util.ejb.SessionSupport;
import org.jboss.tm.TxUtils;

/**
 * @version $Revision: 64601 $
 */
public class TxTimeoutBean extends SessionSupport
{
   /** The serialVersionUID */
   private static final long serialVersionUID = -6750278789142406435L;

   public void ejbCreate()
   {
   }
   
   /**
    * The harness should have set the default timeout to 10 secs
    */
   public void testDefaultTimeout()
   {
      sleep(12000, false);
      int status = getTxStatus();
      if (status != Status.STATUS_MARKED_ROLLBACK)
         throw new EJBException("Should be marked rolled back: " + TxUtils.getStatusAsString(status));
   }

   /**
    * This method's timeout should be 5 secs
    */
   public void testOverriddenTimeoutExpires()
   {
      sleep(7000, false);
      int status = getTxStatus();
      log.info("testOverriddenTimeoutExpires: " + TxUtils.getStatusAsString(status));
      if (TxUtils.isRollback(status) == false)
      {
         // give it a second chance
         sleep(2000, false);
         status = getTxStatus();
         log.info("testOverriddenTimeoutExpires: " + TxUtils.getStatusAsString(status));
         
         if (TxUtils.isRollback(status) == false)
            throw new EJBException("Should be marked rolled back: " + TxUtils.getStatusAsString(status));         
      }
   }

   /**
    * This method's timeout should be 20 secs
    */
   public void testOverriddenTimeoutDoesNotExpire()
   {
      sleep(12000, true);
      int status = getTxStatus();
      if (status != Status.STATUS_ACTIVE)
         throw new EJBException("Should be active: " + TxUtils.getStatusAsString(status));
   }
   
   private int getTxStatus()
   {
      try
      {
         InitialContext ctx = new InitialContext();
         TransactionManager tm = (TransactionManager) ctx.lookup("java:/TransactionManager");
         return tm.getStatus();
      }
      catch (Exception e)
      {
         throw new EJBException(e);
      }
   }
   
   private void sleep(int timeout, boolean throwEJBException)
   {
      try
      {
         Thread.sleep(timeout);
      }
      catch (Exception e)
      {
         if (throwEJBException)
            throw new EJBException(e);
         else
            log.debug("Ignored", e);
      }
   }
}
