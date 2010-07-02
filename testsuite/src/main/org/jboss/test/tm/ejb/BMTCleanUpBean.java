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

import java.rmi.RemoteException;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import org.jboss.test.tm.interfaces.BMTCleanUp;
import org.jboss.test.util.ejb.SessionSupport;
import org.jboss.tm.TransactionManagerLocator;

/**
 * BMTCleanUpBean.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 85945 $
 */
public class BMTCleanUpBean extends SessionSupport
{
   /** The serialVersionUID */
   private static final long serialVersionUID = -6750278789142406435L;

   public void ejbCreate()
   {
   }

   public void doNormal() throws RemoteException
   {
      UserTransaction ut = sessionCtx.getUserTransaction();
      try
      {
         ut.begin();
         ut.commit();
      }
      catch (Exception e)
      {
         throw new RemoteException("Error", e);
      }
   }

   public void testIncomplete() throws RemoteException
   {
      BMTCleanUp remote = (BMTCleanUp) sessionCtx.getEJBObject();
      try
      {
         remote.doIncomplete();
      }
      catch (RemoteException expected)
      {
         // expected
      }
      checkTransaction();
      remote.doNormal();
   }

   public void doIncomplete() throws RemoteException
   {
      UserTransaction ut = sessionCtx.getUserTransaction();
      try
      {
         ut.begin();
      }
      catch (Exception e)
      {
         throw new RemoteException("Error", e);
      }
   }

   public void testTxTimeout() throws RemoteException
   {
      BMTCleanUp remote = (BMTCleanUp) sessionCtx.getEJBObject();
      try
      {
         remote.doTimeout();
      }
      catch (RemoteException expected)
      {
         // expected
      }
      checkTransaction();
      remote.doNormal();
   }

   public void doTimeout() throws RemoteException
   {
      UserTransaction ut = sessionCtx.getUserTransaction();
      try
      {
         ut.setTransactionTimeout(5);
         ut.begin();
         Thread.sleep(10000);
      }
      catch (InterruptedException ignored)
      {
      }
      catch (Exception e)
      {
         throw new RemoteException("Error", e);
      }
   }
   
   private void checkTransaction() throws RemoteException
   {
      TransactionManager tm = TransactionManagerLocator.getInstance().locate();
      try
      {
         Transaction tx = tm.getTransaction();
         if (tx != null)
            throw new RemoteException("There should be no transaction context: " + tx);
      }
      catch (RemoteException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RemoteException("Error", e);
      }
   }
}
