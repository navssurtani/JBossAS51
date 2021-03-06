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
package org.jboss.test.bankiiop.interfaces;

import java.rmi.*;
import javax.ejb.*;

/**
 *      
 *   @see <related>
 *   @author $Author: dimitris@jboss.org $
 *   @version $Revision: 81036 $
 */
public interface Teller
   extends EJBObject
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------
   public void transfer(Account from, Account to, float amount)
      throws RemoteException, BankException;

   public Account createAccount(Customer customer, float balance)
      throws RemoteException, BankException;

   public Account getAccount(Customer customer, float balance)
      throws RemoteException, BankException;

   public Customer getCustomer(String name)
      throws RemoteException, BankException;

   public void transferTest(Account from, Account to, float amount, int iter)
      throws java.rmi.RemoteException, BankException;
}

/*
 *   $Id: Teller.java 81036 2008-11-14 13:36:39Z dimitris@jboss.org $
 *   Currently locked by:$Locker$
 *   Revision:
 *   $Log$
 *   Revision 1.3  2006/03/09 05:14:48  starksm
 *   cleanup unused imports
 *
 *   Revision 1.2  2005/10/29 23:41:18  starksm
 *   Update the jboss LGPL headers
 *
 *   Revision 1.1  2002/03/15 22:36:29  reverbel
 *   Initial version of the bank test for JBoss/IIOP.
 *
 *   Revision 1.2  2001/01/07 23:14:36  peter
 *   Trying to get JAAS to work within test suite.
 *
 *   Revision 1.1.1.1  2000/06/21 15:52:38  oberg
 *   Initial import of jBoss test. This module contains CTS tests, some simple examples, and small bean suites.
 *
 *
 *  
 */
