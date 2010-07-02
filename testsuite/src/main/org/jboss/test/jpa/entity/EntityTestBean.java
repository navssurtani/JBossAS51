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
package org.jboss.test.jpa.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.event.EventSource;
import org.hibernate.jdbc.JDBCContext;
import org.jboss.ejb3.annotation.JndiInject;


/**
 * Comment
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 96150 $
 */
@Stateless
@Remote(EntityTest.class)
public class EntityTestBean implements EntityTest
{
   private @PersistenceContext EntityManager manager;
   private @PersistenceContext Session session;
   private @JndiInject(jndiName="java:/TransactionManager") TransactionManager tm;
   private static Log log = LogFactory.getLog( "org.hibernate.ejb" );


   @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
   public void testOutsideTransaction()
   {
      Transaction tx = null;
      try
      {
         tx = tm.getTransaction();
      }
      catch (SystemException e)
      {
         throw new RuntimeException(e);
      }
      if (tx != null) throw new RuntimeException("THERE IS A TRANSACTION!!!");
      Query q = manager.createQuery("SELECT c FROM Customer c");
      List l = q.getResultList();
      if (l.size() == 0) throw new RuntimeException("failed");
      org.hibernate.Query q2 = session.createQuery("FROM Customer c");
      l = q2.list();
      if (l.size() == 0) throw new RuntimeException("failed");

   }

   public Customer oneToManyCreate() throws Exception
   {
      Ticket t = new Ticket();
      //t.setId( new Long(1) );
      t.setNumber("33A");
      Customer c = new Customer();
      //c.setId( new Long(1) );
      Set<Ticket> tickets = new HashSet<Ticket>();
      tickets.add(t);
      t.setCustomer(c);
      c.setTickets(tickets);
      Address address = new Address();
      address.setStreet("Clarendon Street");
      address.setCity("Boston");
      address.setState("MA");
      address.setZip("02116");
      c.setAddress(address);
      manager.persist(c);
      return c;
   }

   public Customer findCustomerById(Long id) throws Exception
   {
      return manager.find(Customer.class, id);
   }

   public Flight manyToOneCreate() throws Exception
   {
      Flight firstOne = new Flight();
      firstOne.setId(new Long(1));
      firstOne.setName("AF0101");
      Company frenchOne = new Company();
      frenchOne.setName("Air France");
      firstOne.setCompany(frenchOne);
      manager.persist(firstOne);
      return firstOne;
   }

   public void manyToManyCreate() throws Exception
   {

      Flight firstOne = findFlightById(new Long(1));
      Flight second = new Flight();
      second.setId(new Long(2));
      second.setName("US1");
      Company us = new Company();
      us.setName("USAir");
      second.setCompany(us);

      Set<Customer> customers1 = new HashSet<Customer>();
      Set<Customer> customers2 = new HashSet<Customer>();


      Customer bill = new Customer();
      bill.setName("Bill");
      customers1.add(bill);

      Customer monica = new Customer();
      monica.setName("Monica");
      customers1.add(monica);

      Customer molly = new Customer();
      molly.setName("Molly");
      customers2.add(molly);

      firstOne.setCustomers(customers1);
      second.setCustomers(customers2);

      manager.persist(second);
   }


   public Flight findFlightById(Long id) throws Exception
   {
      return manager.find(Flight.class, id);
   }

   public Company findCompanyById(Integer id) throws Exception
   {
      return manager.find(Company.class, id);
   }

   public FieldCustomer fieldOneToManyCreate() throws Exception
   {
      FieldTicket t = new FieldTicket();
      //t.setId( new Long(1) );
      t.setNumber("33A");
      FieldCustomer c = new FieldCustomer();
      //c.setId( new Long(1) );
      Set<FieldTicket> tickets = new HashSet<FieldTicket>();
      tickets.add(t);
      t.setCustomer(c);
      c.setTickets(tickets);
      FieldAddress address = new FieldAddress();
      address.setStreet("Clarendon Street");
      address.setCity("Boston");
      address.setState("MA");
      address.setZip("02116");
      c.setAddress(address);
      manager.persist(c);
      return c;
   }

   public FieldCustomer fieldFindCustomerById(Long id) throws Exception
   {
      return manager.find(FieldCustomer.class, id);
   }

   public FieldFlight fieldManyToOneCreate() throws Exception
   {
      FieldFlight firstOne = new FieldFlight();
      firstOne.setId(new Long(1));
      firstOne.setName("AF0101");
      FieldCompany frenchOne = new FieldCompany();
      frenchOne.setName("Air France");
      firstOne.setCompany(frenchOne);
      manager.persist(firstOne);
      return firstOne;
   }

   public void fieldManyToManyCreate() throws Exception
   {

      FieldFlight firstOne = fieldFindFlightById(new Long(1));
      FieldFlight second = new FieldFlight();
      second.setId(new Long(2));
      second.setName("US1");
      FieldCompany us = new FieldCompany();
      us.setName("USAir");
      second.setCompany(us);

      Set<FieldCustomer> customers1 = new HashSet<FieldCustomer>();
      Set<FieldCustomer> customers2 = new HashSet<FieldCustomer>();


      FieldCustomer bill = new FieldCustomer();
      bill.setName("Bill");
      customers1.add(bill);

      FieldCustomer monica = new FieldCustomer();
      monica.setName("Monica");
      customers1.add(monica);

      FieldCustomer molly = new FieldCustomer();
      molly.setName("Molly");
      customers2.add(molly);

      firstOne.setCustomers(customers1);
      second.setCustomers(customers2);

      manager.persist(second);
   }


   public FieldFlight fieldFindFlightById(Long id) throws Exception
   {
      return manager.find(FieldFlight.class, id);
   }

   public FieldCompany fieldFindCompanyById(Integer id) throws Exception
   {
      return manager.find(FieldCompany.class, id);
   }

   public void testNamedQueries() throws Exception
   {
      System.out.println("testNamedQueries()");
      ArrayList ids = new ArrayList();
      Airport ap1 = new Airport("OSL", "Oslo");
      manager.persist(ap1);

      Airport ap2 = new Airport("LHR", "London");
      manager.persist(ap2);

      Airport ap3 = new Airport("LAX", "Los Angeles");
      manager.persist(ap3);

      List list = manager.createNamedQuery("allAirports").getResultList();
      if (list.size() != 3) throw new RuntimeException("Wrong number returned for allAirports query " + list.size());

      Airport ap = (Airport)manager.createNamedQuery("airportById").setParameter("id", ap2.getId()).getSingleResult();
      if (ap == null) throw new RuntimeException("No object returned by airportById query");

      FieldAirport fap1 = new FieldAirport("LGW", "London");
      manager.persist(fap1);

      FieldAirport fap2 = new FieldAirport("ORL", "Paris");
      manager.persist(fap2);

      FieldAirport fap = (FieldAirport)manager.createNamedQuery("airportByCode").setParameter("code", "LGW").getSingleResult();
      if (fap == null) throw new RuntimeException("No object returned by airportById query");
   }
   
   public Customer createCustomer(String name) {
	   Customer c = new Customer();
	   c.setName(name);
	   manager.persist(c);
	   return c;
   }
   
   public void changeCustomer(Long id, String name) {
	   Customer c = manager.find(Customer.class, id);
	   c.setName(name);
   }
   
   public Customer loadCustomer(Long id) {
	   Customer c =  manager.find(Customer.class, id);
	   return c;
   }

   public boolean isDelegateASession() {
      //has to delegate to the underlying entitymanager
      return (manager.getDelegate() != null) && (manager.getDelegate() instanceof Session);
   }

   public boolean isTrueHibernateSession() {
      //has to implement the private Session interfaces
      return (session instanceof Session)
            && (session instanceof SessionImplementor)
            && (session instanceof EventSource)
            && (session instanceof JDBCContext.Context);
   }
   
   @PostConstruct
   public void construct()
   {
      manager.find(Customer.class, 1L);
   }
}
