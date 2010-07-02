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
package org.jboss.invocation;

import java.io.Serializable;
import java.io.ObjectStreamException;


/** Type safe enumeration used for keys in the Invocation object. This relies
 * on an integer id as the identity for a key. When you add a new key enum
 * value you must assign it an ordinal value of the current MAX_KEY_ID+1 and
 * update the MAX_KEY_ID value.
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81030 $
 */
public final class InvocationKey implements Serializable
{
   /** The serial version ID */
   private static final long serialVersionUID = -5117370636698417671L;

   /** The max ordinal value in use for the InvocationKey enums. When you add a
    * new key enum value you must assign it an ordinal value of the current
    * MAX_KEY_ID+1 and update the MAX_KEY_ID value.
    */
   private static final int MAX_KEY_ID = 22;

   /** The array of InvocationKey indexed by ordinal value of the key */
   private static final InvocationKey[] values = new InvocationKey[MAX_KEY_ID+1];

   /** 
    * Transactional information with the invocation. 
    */ 
   public static final InvocationKey TRANSACTION = 
         new InvocationKey("TRANSACTION", 0);

   /** 
    * Security principal assocated with this invocation.
    */
   public static final InvocationKey PRINCIPAL =
      new InvocationKey("PRINCIPAL", 1);

   /** 
    * Security credential assocated with this invocation. 
    */
   public static final InvocationKey CREDENTIAL = 
         new InvocationKey("CREDENTIAL", 2);

   /** Any authenticated Subject associated with the invocation */
   public static final InvocationKey SUBJECT = new InvocationKey("SUBJECT", 14);

   /** 
    * We can keep a reference to an abstract "container" this invocation 
    * is associated with. 
    */
   public static final InvocationKey OBJECT_NAME = 
         new InvocationKey("CONTAINER", 3);

   /** 
    * The type can be any qualifier for the invocation, anything (used in EJB). 
    */
   public static final InvocationKey TYPE = new InvocationKey("TYPE", 4);

   /** 
    * The Cache-ID associates an instance in cache somewhere on the server 
    * with this invocation. 
    */
   public static final InvocationKey CACHE_ID = new InvocationKey("CACHE_ID", 5);

   /** 
    * The invocation can be a method invocation, we give the method to call. 
    */
   public static final InvocationKey METHOD = new InvocationKey("METHOD", 6);

   /** 
    * The arguments of the method to call. 
    */
   public static final InvocationKey ARGUMENTS =
      new InvocationKey("ARGUMENTS", 7);

   /** 
    * Invocation context
    */
   public static final InvocationKey INVOCATION_CONTEXT = 
         new InvocationKey("INVOCATION_CONTEXT", 8);

   /** 
    * Enterprise context
    */
   public static final InvocationKey ENTERPRISE_CONTEXT = 
         new InvocationKey("ENTERPRISE_CONTEXT", 9);

   /** 
    * The invoker-proxy binding name
    */
   public static final InvocationKey INVOKER_PROXY_BINDING = 
         new InvocationKey("INVOKER_PROXY_BINDING", 10);

   /** 
    * The invoker 
    */
   public static final InvocationKey INVOKER = new InvocationKey("INVOKER", 11);

   /**
    * The JNDI name of the EJB.
    */
   public static final InvocationKey JNDI_NAME =
      new InvocationKey("JNDI_NAME", 12);

   /** 
    * The EJB meta-data for the {@link javax.ejb.EJBHome} reference.
    */
   public final static InvocationKey EJB_METADATA = 
         new InvocationKey("EJB_METADATA", 13);

   /** The EJB home proxy bound for use by getEJBHome */
   public final static InvocationKey EJB_HOME =
         new InvocationKey("EJB_HOME", 14);

   /** The SOAP Message Context that is available to the SLSB during a service endpoint invocation */
   public final static InvocationKey SOAP_MESSAGE_CONTEXT =
         new InvocationKey("SOAP_MESSAGE_CONTEXT", 15);

   /** The SOAP Message that is available to the SLSB during a service endpoint invocation */
   public final static InvocationKey SOAP_MESSAGE =
         new InvocationKey("SOAP_MESSAGE", 16);

   /** The JAAC context id associated with the invocation */
   public final static InvocationKey JACC_CONTEXT_ID =
         new InvocationKey("JACC_CONTEXT_ID", 17);

   /**
    * The Security Context associated with the invocation
    */
   public final static InvocationKey SECURITY_CONTEXT =
      new InvocationKey("SECURITY_CONTEXT", 18);
   
   /**
    * Indicate whether the invocation is secure
    */
   public final static InvocationKey SECURE =
      new InvocationKey("SECURE", 19);
   
   /**
    * Indicate whether an inter-vm invocation
    */
   public final static InvocationKey INTERVM =
      new InvocationKey("INTERVM", 20);
   
   /**
    * Indicate whether an inter-vm invocation
    */
   public final static InvocationKey SECURITY_DOMAIN =
      new InvocationKey("SECURITY_DOMAIN", 21);
   
   /** The key enum symbolic value */
   private final transient String name;
   /** The persistent integer representation of the key enum */
   private final int ordinal;

   private InvocationKey(String name, int ordinal)
   {
      this.name = name;
      this.ordinal = ordinal;
      values[ordinal] = this;
   }

   public String toString()
   {
      return name;
   }

   Object readResolve() throws ObjectStreamException
   {
      return values[ordinal];
   }
}
