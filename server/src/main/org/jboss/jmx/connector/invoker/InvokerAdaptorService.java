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
package org.jboss.jmx.connector.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.jboss.invocation.Invocation;
import org.jboss.invocation.MarshalledInvocation;
import org.jboss.jmx.adaptor.rmi.RMINotificationListener;
import org.jboss.jmx.connector.invoker.client.InvokerAdaptorException;
import org.jboss.security.SecurityConstants;
import org.jboss.security.SecurityContext;
import org.jboss.system.Registry;
import org.jboss.system.ServiceMBeanSupport;

/**
 * A JBoss service exposes an invoke(Invocation) operation that maps
 * calls to the ExposedInterface onto the MBeanServer this service
 * is registered with. It is used in conjunction with a proxy factory
 * to expose the MBeanServer to remote clients through arbitrary
 * protocols.<p>
 *
 * It sets up the correct classloader before unmarshalling the
 * arguments, this relies on the ObjectName being seperate from
 * from the other method arguments to avoid unmarshalling them
 * before the classloader is determined from the ObjectName.<p>
 *
 * The interface is configurable, it must be similar to MBeanServer,
 * though not necessarily derived from it<p>
 *
 * The invoker is configurable and must be specified
 *
 * @author <a href="mailto:Adrian.Brock@HappeningTimes.com">Adrian Brock</a>
 * @author Scott.Stark@jboss.org
 * @version $Revision: 92287 $
 *
 * @jmx:mbean name="jboss.jmx:type=adaptor,protocol=INVOKER"
 *            extends="org.jboss.system.ServiceMBean"
 **/
public class InvokerAdaptorService
   extends ServiceMBeanSupport
   implements InvokerAdaptorServiceMBean
{
   private ObjectName mbeanRegistry;
   /** */
   private Map marshalledInvocationMapping = new HashMap();
   /** */
   private Class[] exportedInterfaces;
   /** A HashSet<Method> addNotificationListener methods */
   private HashSet addNotificationListeners = new HashSet();
   /** A HashSet<Method> removeNotificationListener methods */
   private HashSet removeNotificationListeners = new HashSet();
   /** A HashSet<RMINotificationListener, NotificationListenerDelegate> for the
    registered listeners */
   protected HashMap remoteListeners = new HashMap();

   public InvokerAdaptorService()
   {
   }

   /**
    * @jmx:managed-attribute
    */
   public Class[] getExportedInterfaces()
   {
      return exportedInterfaces;
   }
   /**
    * @jmx:managed-attribute
    */
   public void setExportedInterfaces(Class[] exportedInterfaces)
   {
      this.exportedInterfaces = exportedInterfaces;
   }

   protected void startService()
      throws Exception
   {
      // Build the interface method map
      HashMap tmpMap = new HashMap(61);
      for(int n = 0; n < exportedInterfaces.length; n ++)
      {
         Class iface = exportedInterfaces[n];
         Method[] methods = iface.getMethods();
         for(int m = 0; m < methods.length; m ++)
         {
            Method method = methods[m];
            Long hash = new Long(MarshalledInvocation.calculateHash(method));
            tmpMap.put(hash, method);
         }
         /* Look for a void addNotificationListener(ObjectName name,
               RMINotificationListener listener, NotificationFilter filter,
               Object handback)
         */
         try
         {
            Class[] sig = {ObjectName.class, RMINotificationListener.class,
               NotificationFilter.class, Object.class};
            Method addNotificationListener = iface.getMethod(
               "addNotificationListener", sig);
            addNotificationListeners.add(addNotificationListener);
         }
         catch(Exception e)
         {
            log.debug(iface+"No addNotificationListener(ObjectName, RMINotificationListener)");
         }

         /* Look for a void removeNotificationListener(ObjectName,
            RMINotificationListener)
         */
         try
         {
            Class[] sig = {ObjectName.class, RMINotificationListener.class};
            Method removeNotificationListener = iface.getMethod(
               "removeNotificationListener", sig);
            removeNotificationListeners.add(removeNotificationListener);
         }
         catch(Exception e)
         {
            log.debug(iface+"No removeNotificationListener(ObjectName, RMINotificationListener)");
         }            
      }
      marshalledInvocationMapping = Collections.unmodifiableMap(tmpMap);

      // Place our ObjectName hash into the Registry so invokers can resolve it
      Registry.bind(new Integer(serviceName.hashCode()), serviceName);
   }

   protected void stopService()
      throws Exception
   {
      // Remove the method hashses
      if( exportedInterfaces != null )
      {
         for(int n = 0; n < exportedInterfaces.length; n ++)
            MarshalledInvocation.removeHashes(exportedInterfaces[n]);
      }
      marshalledInvocationMapping = null;
      remoteListeners.clear();
      Registry.unbind(new Integer(serviceName.hashCode()));
   }

   /** 
    * Expose the service interface mapping as a read-only attribute
    *
    * @jmx:managed-attribute
    *
    * @return A Map<Long hash, Method> of the MBeanServer
    */
   public Map getMethodMap()
   {
      return marshalledInvocationMapping;
   }

   /**
    * Expose the MBeanServer service via JMX to invokers.
    *
    * @jmx:managed-operation
    *
    * @param invocation    A pointer to the invocation object
    * @return              Return value of method invocation.
    * 
    * @throws Exception    Failed to invoke method.
    */
   public Object invoke(Invocation invocation)
       throws Exception
   {
      try
      {
         // Make sure we have the correct classloader before unmarshalling
         ClassLoader oldCL = SecurityActions.getContextClassLoader();

         ClassLoader newCL = null;
         // Get the MBean this operation applies to
         ObjectName objectName = (ObjectName) invocation.getValue("JMX_OBJECT_NAME");
         if (objectName != null)
         {
            // Obtain the ClassLoader associated with the MBean deployment
            newCL = server.getClassLoaderFor(objectName);
         }

         if (newCL != null && newCL != oldCL)
            SecurityActions.setContextClassLoader(newCL);

         //JBAS-6449: Cache the incoming security context to be retained on exit
         SecurityContext previousSecurityContext = SecurityActions.getSecurityContext();
         
         try
         {
            // Set the method hash to Method mapping
            if (invocation instanceof MarshalledInvocation)
            {
               MarshalledInvocation mi = (MarshalledInvocation) invocation;
               mi.setMethodMap(marshalledInvocationMapping);
            }
            // Invoke the MBeanServer method via reflection
            Method method = invocation.getMethod();
            Object[] args = invocation.getArguments();
            Principal principal = invocation.getPrincipal();
            Object credential = invocation.getCredential();
            Object value = null;
            SecurityContext sc = SecurityActions.createSecurityContext(SecurityConstants.DEFAULT_APPLICATION_POLICY);
            SecurityActions.setSecurityContext(sc);
            // Associate the method 
            SecurityActions.pushSubjectContext(principal, credential, null);

            try
            {
               if( addNotificationListeners.contains(method) )
               {
                  ObjectName name = (ObjectName) args[0];
                  RMINotificationListener listener = (RMINotificationListener)
                     args[1];
                  NotificationFilter filter = (NotificationFilter) args[2];
                  Object handback = args[3];
                  addNotificationListener(name, listener, filter, handback);
               }
               else if( removeNotificationListeners.contains(method) )
               {
                  ObjectName name = (ObjectName) args[0];
                  RMINotificationListener listener = (RMINotificationListener)
                     args[1];
                  removeNotificationListener(name, listener);            
               }
               else
               {
                  String name = method.getName();
                  Class[] paramTypes = method.getParameterTypes();
                  Method mbeanServerMethod = MBeanServer.class.getMethod(name,
                     paramTypes);
                  value = mbeanServerMethod.invoke(server, args);
               }
            }
            catch(InvocationTargetException e)
            {
               Throwable t = e.getTargetException();
               if( t instanceof Exception )
                  throw (Exception) t;
               else
                  throw new UndeclaredThrowableException(t, method.toString());
            }

            return value;
         }
         finally
         {
            // Restore the input security context
            SecurityActions.popSubjectContext();
            if(previousSecurityContext != null)
               SecurityActions.setSecurityContext(previousSecurityContext);
            else
               SecurityActions.clearSecurityContext();
            // Restore the input class loader
            if (newCL != null && newCL != oldCL)
               SecurityActions.setContextClassLoader(oldCL);
         }
      }
      catch (Throwable t)
      {
         throw new InvokerAdaptorException(t);
      }
   }

   public void addNotificationListener(ObjectName name,
      RMINotificationListener listener, NotificationFilter filter,
      Object handback)
      throws InstanceNotFoundException, RemoteException
   {
      if( log.isTraceEnabled() )
         log.trace("addNotificationListener, name="+name+", listener="+listener);
      NotificationListenerDelegate delegate =
         new NotificationListenerDelegate(listener, name);
      remoteListeners.put(listener, delegate);
      getServer().addNotificationListener(name, delegate, filter, handback);
   }

   public void removeNotificationListener(ObjectName name,
      RMINotificationListener listener)
      throws InstanceNotFoundException, ListenerNotFoundException,
      RemoteException
   {
      if( log.isTraceEnabled() )
         log.trace("removeNotificationListener, name="+name+", listener="+listener);
      NotificationListenerDelegate delegate = (NotificationListenerDelegate)
         remoteListeners.remove(listener);
      if( delegate == null )
         throw new ListenerNotFoundException("No listener matches: "+listener);
      getServer().removeNotificationListener(name, delegate);
   }

   private class NotificationListenerDelegate
      implements NotificationListener
   {
      /** The remote client */
      private RMINotificationListener client;
      /** The mbean the client is monitoring */
      private ObjectName targetName;

      public NotificationListenerDelegate(RMINotificationListener client,
         ObjectName targetName)
      {
         this.client = client;
         this.targetName = targetName;
      }

      public void handleNotification(Notification notification,
         Object handback)
      {
         try
         {
            if( log.isTraceEnabled() )
            {
               log.trace("Sending notification to client, event:"+notification);
            }
            client.handleNotification(notification, handback);
         }
         catch(Throwable t)
         {
            log.debug("Failed to notify client, unregistering listener", t);
            try
            {
               removeNotificationListener(targetName, client);
            }
            catch(Exception e)
            {
               log.debug("Failed to unregister listener", e);
            }
         }
      }
   }

}
