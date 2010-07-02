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
package org.jboss.wsf.container.jboss50.invocation;

import org.jboss.dependency.spi.ControllerContext;
import org.jboss.kernel.spi.dependency.KernelController;
import org.jboss.wsf.spi.deployment.Endpoint;
import org.jboss.wsf.spi.invocation.Invocation;
import org.jboss.wsf.spi.invocation.integration.InvocationContextCallback;
import org.jboss.wsf.spi.invocation.integration.ServiceEndpointContainer;
import org.jboss.wsf.spi.util.KernelLocator;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.xml.ws.WebServiceException;
 
import org.jboss.ejb3.EJBContainer;
import java.lang.reflect.Method;

/**
 * Handles invocations on EJB3 endpoints.
 *
 * @author Thomas.Diesler@jboss.org
 * @author Heiko.Braun@jboss.com
 * 
 * @since 25-Apr-2007
 */
public class InvocationHandlerEJB3 extends AbstractInvocationHandler
{
   private static final String EJB3_JNDI_PREFIX = "java:env/";
   public static final String CONTAINER_NAME = "org.jboss.wsf.spi.invocation.ContainerName";

   private String containerName;
   private KernelController houston;
   private ServiceEndpointContainer serviceEndpointContainer;


   InvocationHandlerEJB3()
   {
      houston = KernelLocator.getKernel().getController();
   }

   public Invocation createInvocation()
   {
      return new Invocation();
   }

   public void init(Endpoint ep)
   {
      containerName = (String)ep.getProperty(InvocationHandlerEJB3.CONTAINER_NAME);
      assert containerName!=null : "Target container name not set";

   }

   public Context getJNDIContext(final Endpoint ep) throws NamingException
   {
      final EJBContainer ejb3Container = (EJBContainer)lazyInitializeInvocationTarget();
      return (Context)ejb3Container.getEnc().lookup(EJB3_JNDI_PREFIX);
   }

   private ServiceEndpointContainer lazyInitializeInvocationTarget()
   {
      if(null==this.serviceEndpointContainer)
      {
         ControllerContext context = houston.getInstalledContext(containerName);
         if (context == null)
            throw new WebServiceException("Cannot find service endpoint target: " + containerName);

         assert (context.getTarget() instanceof ServiceEndpointContainer) : "Invocation target mismatch";
         this.serviceEndpointContainer = (ServiceEndpointContainer) context.getTarget();
      }

      return this.serviceEndpointContainer;
   }

   public void invoke(Endpoint ep, Invocation wsInv) throws Exception
   {
      try
      {
         ServiceEndpointContainer invocationTarget = lazyInitializeInvocationTarget();
         
         Class beanClass = invocationTarget.getServiceImplementationClass();
         Method method = getImplMethod(beanClass, wsInv.getJavaMethod());
         Object[] args = wsInv.getArgs();
         InvocationContextCallback invProps = new EJB3InvocationContextCallback(wsInv);
         
         Object retObj = invocationTarget.invokeEndpoint(method, args, invProps);

         wsInv.setReturnValue(retObj);
      }
      catch (Throwable th)
      {
         handleInvocationException(th);
      }
   }

   static class EJB3InvocationContextCallback implements InvocationContextCallback
   {
      private Invocation wsInv;

      public EJB3InvocationContextCallback(Invocation wsInv)
      {
         this.wsInv = wsInv;
      }

      public <T> T get(Class<T> propertyType)
      {
         return wsInv.getInvocationContext().getAttachment(propertyType);               
      }
   }
}
