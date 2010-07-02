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

import java.util.Set;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.TableGenerator;
import javax.persistence.Transient;
import javax.persistence.Version;

/**
 * Flight
 *
 * @author Emmanuel Bernard
 */
@TableGenerator(
   name="default-generator",
   table="TEST_GENERATOR_TABLE",
   pkColumnValue= "TEST_PK",
   allocationSize = 20
)
@Entity
public class Flight implements java.io.Serializable
{
   Long id;
   String name;
   long duration;
   long durationInSec;
   Integer version;
   Company company;
   Set<Customer> customers;

   @Id
   @GeneratedValue(strategy=GenerationType.TABLE, generator = "default-generator")
   public Long getId()
   {
      return id;
   }

   public void setId(Long long1)
   {
      id = long1;
   }

   @Column(updatable = false, name = "flight_name", nullable = false, length = 50)
   public String getName()
   {
      return name;
   }

   public void setName(String string)
   {
      name = string;
   }

   @Basic(fetch = FetchType.LAZY)
   public long getDuration()
   {
      return duration;
   }

   public void setDuration(long l)
   {
      duration = l;
      //durationInSec = duration / 1000;
   }

   @Transient
   public long getDurationInSec()
   {
      return durationInSec;
   }

   public void setDurationInSec(long l)
   {
      durationInSec = l;
   }

   @Version
   @Column(name = "OPTLOCK")
   public Integer getVersion()
   {
      return version;
   }

   public void setVersion(Integer i)
   {
      version = i;
   }

   //@ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
@ManyToOne(cascade = {CascadeType.ALL})
           @JoinColumn(name = "COMP_ID")
   public Company getCompany()
   {
      return company;
   }

   public void setCompany(Company company)
   {
      this.company = company;
   }

   @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
   public Set<Customer> getCustomers()
   {
      return customers;
   }

   public void setCustomers(Set<Customer> customers)
   {
      this.customers = customers;
   }
}
