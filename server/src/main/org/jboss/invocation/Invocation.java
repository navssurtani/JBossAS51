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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Map;
import java.util.HashMap;

import javax.transaction.Transaction;

import org.jboss.security.SecurityContext;

/**
 * The Invocation object is the generic object flowing through our interceptors.
 *
 * <p>The heart of it is the payload map that can contain anything we then 
 *    put readers on them.  The first <em>reader</em> is this 
 *    <em>Invocation</em> object that can interpret the data in it. 
 * 
 * <p>Essentially we can carry ANYTHING from the client to the server, we keep
 *    a series of of predefined variables and method calls to get at the 
 *    pointers.  But really it is just  a repository of objects. 
 *
 * @author  <a href="mailto:marc@jboss.org">Marc Fleury</a>
 * @author  <a href="mailto:christoph.jung@infor.de">Christoph G. Jung</a>
 * @author Anil.Saldhana@redhat.com
 * @version $Revision: 81030 $
 */
public class Invocation
{
   /** The signature of the invoke() method */
   public static final String[] INVOKE_SIGNATURE = { "org.jboss.invocation.Invocation" }; 

   // The payload is a repository of everything associated with the invocation
   // It is information that will need to travel 

   /** 
    * Contextual information to the invocation that is not part of the payload. 
    */
   public Map transient_payload;

   /**
    * as_is classes that will not be marshalled by the invocation
    * (java.* and javax.* or anything in system classpath is OK)
    */
   public Map as_is_payload;

   /** Payload will be marshalled for type hiding at the RMI layers. */
   public Map payload;

   public InvocationContext invocationContext;
   public Object[] args;
   public Object objectName;
   public Method method;
   public InvocationType invocationType;

   // The variables used to indicate what type of data and where to put it.

   //
   // We are using the generic payload to store some of our data, we define 
   // some integer entries. These are just some variables that we define for 
   // use in "typed" getters and setters. One can define anything either in
   // here explicitely or through the use of external calls to getValue
   //

   /**
    * No-args constructor exposed for externalization only.
    */
   public Invocation() 
   {
   }

   public Invocation( Object id, Method m, Object[] args, Transaction tx,
      Principal identity, Object credential )
   {
      setId(id);
      setMethod(m);
      setArguments(args);
      setTransaction(tx);
      setPrincipal(identity);
      setCredential(credential);
   }
   
   /**
    * The generic store of variables.
    *
    * <p>
    *    The generic getter and setter is really all that one needs to talk 
    *    to this object. We introduce typed getters and setters for 
    *    convenience and code readability in the codeba
    */
   public void setValue(Object key, Object value)
   {
      setValue(key, value, PayloadKey.PAYLOAD);
   }
   
   /**
    * Advanced store
    * Here you can pass a TYPE that indicates where to put the value.
    * TRANSIENT: the value is put in a map that WON'T be passed 
    * AS_IS: no need to marshall the value when passed (use for all JDK 
    *    java types)
    * PAYLOAD: we need to marshall the value as its type is application specific
    */
   public void setValue(Object key, Object value, PayloadKey type) 
   {
      if(type == PayloadKey.TRANSIENT) 
      {
          getTransientPayload().put(key,value);
      }
      else if(type == PayloadKey.AS_IS)
      {
          getAsIsPayload().put(key,value);
      }
      else if(type == PayloadKey.PAYLOAD)
      {
          getPayload().put(key,value);
      }
      else 
      {
         throw new IllegalArgumentException("Unknown PayloadKey: " + type);
      }
   }
 
   /**
    * Get a value from the stores.
    */
   public Object getValue(Object key) 
   { 
      // find where it is
      Object rtn = getPayloadValue(key);
      if (rtn != null) return rtn;

      rtn = getAsIsValue(key);
      if (rtn != null) return rtn;

      rtn = getTransientValue(key);
      return rtn;
   }
   
   public Object getPayloadValue(Object key)
   {
      if (payload == null) return null;
      return payload.get(key);
   }

   public Object getTransientValue(Object key)
   {
      if (transient_payload == null) return null;
      return transient_payload.get(key);
   }

   public Object getAsIsValue(Object key)
   {
      if (as_is_payload == null) return null;
      return as_is_payload.get(key);
   }



   //
   // Convenience typed getters, use pre-declared keys in the store, 
   // but it all comes back to the payload, here you see the usage of the 
   // different payloads.  Anything that has a well defined type can go in as_is
   // Anything that is arbitrary and depends on the application needs to go in 
   // in the serialized payload.  The "Transaction" is known, the type of the 
   // method arguments are not for example and are part of the EJB jar.
   //
   
   /**
    * set the transaction.
    */
   public void setTransaction(Transaction tx)
   {
      if( tx instanceof Serializable )
         getAsIsPayload().put(InvocationKey.TRANSACTION, tx);
      else
         getTransientPayload().put(InvocationKey.TRANSACTION, tx);
   }
   
   /**
    * get the transaction.
    */
   public Transaction getTransaction()
   {
      Transaction tx = (Transaction) getAsIsPayload().get(InvocationKey.TRANSACTION);
      if( tx == null )
         tx = (Transaction) getTransientPayload().get(InvocationKey.TRANSACTION);
      return tx;
   }

   /**
    * Change the security identity of this invocation.
    */
   public void setPrincipal(Principal principal)
   {
      getAsIsPayload().put(InvocationKey.PRINCIPAL, principal);
   }
   
   public Principal getPrincipal()
   {
      return (Principal) getAsIsPayload().get(InvocationKey.PRINCIPAL);
   }
   
   /**
    * Change the security credentials of this invocation.
    */
   public void setCredential(Object credential)
   {
      getPayload().put(InvocationKey.CREDENTIAL, credential);
   }
   
   public Object getCredential()
   {
      return getPayloadValue(InvocationKey.CREDENTIAL);
   }
   
   /**
    * container for server side association.
    */
   public void setObjectName(Object objectName)
   {
      this.objectName = objectName;
   }
   
   public Object getObjectName()
   {
      return objectName;
   }
   
   /**
    * An arbitrary type.
    */
   public void setType(InvocationType type)
   {
      invocationType = type;
   }
   
   public InvocationType getType()
   {
      if (invocationType == null) return InvocationType.LOCAL;
      return invocationType;
   }

   /**
    * Return the invocation target ID.  Can be used to identify a cached object
    */
   public void setId(Object id)
   {
      getPayload().put(InvocationKey.CACHE_ID, id);
   }
   
   public Object getId()
   {
      return getPayloadValue(InvocationKey.CACHE_ID);
   }
   
   /**
    * set on method Return the invocation method.
    */
   public void setMethod(Method method)
   {
      this.method = method;
   }
   
   /**
    * get on method Return the invocation method.
    */
   public Method getMethod()
   {
      return method;
   }
   
   /**
    * A list of arguments for the method.
    */
   public void setArguments(Object[] arguments)
   {
      this.args = arguments;
   }
   
   public Object[] getArguments()
   {
      return this.args;
   }

   public InvocationContext getInvocationContext()
   {
      return invocationContext;
   }

   public void setInvocationContext(InvocationContext ctx)
   {
      this.invocationContext = ctx;
   }
   
   public void setEnterpriseContext(Object ctx)
   {
      getTransientPayload().put(InvocationKey.ENTERPRISE_CONTEXT, ctx);
   }
      
   public Object getEnterpriseContext()
   {
      return getTransientPayload().get(InvocationKey.ENTERPRISE_CONTEXT);
   }
   
   public SecurityContext getSecurityContext()
   {
      return (SecurityContext) getAsIsPayload().get(InvocationKey.SECURITY_CONTEXT);
   }
   
   public void setSecurityContext(SecurityContext sc)
   {
      getAsIsPayload().put(InvocationKey.SECURITY_CONTEXT, sc);
   }
   
   public boolean isInterVM()
   {
      Boolean b = (Boolean) getAsIsPayload().get(InvocationKey.INTERVM);
      return b != null && b == Boolean.TRUE;
   }
   
   public void setInterVM(Boolean boolValue)
   {
      getAsIsPayload().put(InvocationKey.INTERVM, boolValue);
   }
   
   /**
    * Set whether the invocation is secure or not
    * @param secure boolean value
    */
   public void setSecure(Boolean secure)
   {
      this.getAsIsPayload().put(InvocationKey.SECURE, secure);   
   }
   

   public Map getTransientPayload()
   {
      if (transient_payload == null) transient_payload = new HashMap();
      return transient_payload;
   }

   public Map getAsIsPayload()
   {
      if (as_is_payload == null) as_is_payload = new HashMap();
      return as_is_payload;
   }

   public Map getPayload()
   {
      if (payload == null) payload = new HashMap();
      return payload;
   }

   /**
    * This method will be called by the container(ContainerInterceptor) to issue the
    * ultimate method call represented by this invocation. It is overwritten, e.g., by the
    * WS4EE invocation in order to realize JAXRPC pre- and postprocessing.
    */
   public Object performCall(Object instance, Method m, Object[] arguments)
           throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, Exception
   {
      return m.invoke(instance,arguments);
   }

   /**
    * Helper method to determine whether an invocation is local
    * 
    * @return true when local, false otherwise
    */
   public boolean isLocal()
   {
      InvocationType type = getType();
      return (type == InvocationType.LOCAL || type == InvocationType.LOCALHOME
            || type == InvocationType.SERVICE_ENDPOINT);
   }
   
   /**
    * Determine whether the invocation arrived on a secure channel
    * @return true invocation is secure
    */
   public Boolean isSecure()
   {
      return (Boolean) this.getAsIsPayload().get(InvocationKey.SECURE);
   }
}
