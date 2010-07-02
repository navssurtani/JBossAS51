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
package org.jboss.test.jpa.test;

import java.util.Set;

import org.jboss.test.jpa.entity.Address;
import org.jboss.test.jpa.entity.Company;
import org.jboss.test.jpa.entity.Customer;
import org.jboss.test.jpa.entity.EntityTest;
import org.jboss.test.jpa.entity.FieldAddress;
import org.jboss.test.jpa.entity.FieldCompany;
import org.jboss.test.jpa.entity.FieldCustomer;
import org.jboss.test.jpa.entity.FieldFlight;
import org.jboss.test.jpa.entity.FieldTicket;
import org.jboss.test.jpa.entity.Flight;
import org.jboss.test.jpa.entity.Ticket;
import org.jboss.test.JBossTestCase;
import junit.framework.Test;

import org.jboss.logging.Logger;

/**
 * Sample client for the jboss container.
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Id: EntityUnitTestCase.java 57207 2006-09-26 12:06:13Z dimitris@jboss.org $
 */

public class EntityOptimisticLockingUnitTestCase
extends JBossTestCase
{
   private static final Logger log = Logger.getLogger(EntityOptimisticLockingUnitTestCase.class);

   static boolean deployed = false;
   static int test = 0;

   public EntityOptimisticLockingUnitTestCase(String name)
   {

      super(name);

   }

   public void testOneToMany() throws Exception
   {
      EntityTest test = (EntityTest) this.getInitialContext().lookup("EntityTestBean/remote");
      Customer c = test.oneToManyCreate();
      assertNotNull(c);
      assertNotNull(c.getTickets());
      Set<Ticket> tickets = c.getTickets();
      assertTrue(tickets.size() > 0);

      // test find
      c = test.findCustomerById(c.getId());
      assertNotNull(c);
      assertNotNull(c.getTickets());
      tickets = c.getTickets();
      assertTrue(tickets.size() > 0);

      // test 1-1
      Address address = c.getAddress();
      assertTrue(address != null);
      assertTrue(address.getCity().equals("Boston"));
   }

   public void testManyToOne() throws Exception
   {
      EntityTest test = (EntityTest) this.getInitialContext().lookup("EntityTestBean/remote");
      Flight f = test.manyToOneCreate();
      f = test.findFlightById(f.getId());
      assertTrue(f.getName().equals("AF0101"));
      assertTrue(f.getCompany().getName().equals("Air France"));

      Company c = test.findCompanyById(f.getCompany().getId());
      assertTrue(c != null);
      assertTrue(c.getFlights().size() == 1);
   }

   public void testManyToMany() throws Exception
   {
      EntityTest test = (EntityTest) this.getInitialContext().lookup("EntityTestBean/remote");
      test.manyToManyCreate();

      Flight one = test.findFlightById(new Long(1));
      assertTrue(one.getCompany().getName().equals("Air France"));

      Flight two = test.findFlightById(new Long(2));
      assertTrue(two.getCompany().getName().equals("USAir"));

      System.out.println("Air France customers");
      for (Customer c : one.getCustomers())
      {
         System.out.println(c.getName());

      }
      System.out.println("USAir customers");

      for (Customer c : two.getCustomers())
      {
         System.out.println(c.getName());
      }

   }

   public void testFieldOneToMany() throws Exception
   {
      EntityTest test = (EntityTest) this.getInitialContext().lookup("EntityTestBean/remote");
      FieldCustomer c = test.fieldOneToManyCreate();
      assertNotNull(c);
      assertNotNull(c.getTickets());
      Set<FieldTicket> tickets = c.getTickets();
      assertTrue(tickets.size() > 0);

      // test find
      c = test.fieldFindCustomerById(c.getId());
      assertNotNull(c);
      assertNotNull(c.getTickets());
      tickets = c.getTickets();
      assertTrue(tickets.size() > 0);

      // test 1-1
      FieldAddress address = c.getAddress();
      assertTrue(address != null);
      assertTrue(address.getCity().equals("Boston"));
   }

   public void testFieldManyToOne() throws Exception
   {
      EntityTest test = (EntityTest) this.getInitialContext().lookup("EntityTestBean/remote");
      FieldFlight f = test.fieldManyToOneCreate();
      f = test.fieldFindFlightById(f.getId());
      assertTrue(f.getName().equals("AF0101"));
      assertTrue(f.getCompany().getName().equals("Air France"));

      FieldCompany c = test.fieldFindCompanyById(f.getCompany().getId());
      assertTrue(c != null);
      assertTrue(c.getFlights().size() == 1);
   }

   public void testFieldManyToMany() throws Exception
   {
      EntityTest test = (EntityTest) this.getInitialContext().lookup("EntityTestBean/remote");
      test.fieldManyToManyCreate();

      FieldFlight one = test.fieldFindFlightById(new Long(1));
      assertTrue(one.getCompany().getName().equals("Air France"));

      FieldFlight two = test.fieldFindFlightById(new Long(2));
      assertTrue(two.getCompany().getName().equals("USAir"));

      System.out.println("Air France customers");
      for (FieldCustomer c : one.getCustomers())
      {
         System.out.println(c.getName());

      }
      System.out.println("USAir customers");

      for (FieldCustomer c : two.getCustomers())
      {
         System.out.println(c.getName());
      }

   }

   public void testNamedQueries() throws Exception
   {
      EntityTest test = (EntityTest) this.getInitialContext().lookup("EntityTestBean/remote");
      test.testNamedQueries();      
   }

   public void testOutsideTx() throws Exception
   {
      EntityTest test = (EntityTest) this.getInitialContext().lookup("EntityTestBean/remote");
      test.testOutsideTransaction();      

   }
   
   public void testFlush() throws Exception
   {
      EntityTest test = (EntityTest) this.getInitialContext().lookup("EntityTestBean/remote");
      Customer c = test.createCustomer("Emmanuel");
	  test.changeCustomer(c.getId(), "Bill");
	  Customer c2 = test.loadCustomer(c.getId());
	  assertEquals("Bill", c2.getName());
   }

   public static Test suite() throws Exception
   {
      return getDeploySetup(EntityOptimisticLockingUnitTestCase.class, "entityoptimisticlocking-test.jar");
   }

}
