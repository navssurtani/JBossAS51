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

import java.lang.reflect.Method;

import org.jboss.logging.Logger;
import org.jboss.wsf.spi.deployment.Endpoint;
import org.jboss.wsf.spi.invocation.Invocation;
import org.jboss.wsf.spi.invocation.InvocationContext;

/**
 * Handles invocations on MDB EJB3 endpoints.
 *
 * @author Thomas.Diesler@jboss.org
 * @since 25-Apr-2007
 */
public class InvocationHandlerMDB3 extends AbstractInvocationHandler
{
   // provide logging
   private static final Logger log = Logger.getLogger(InvocationHandlerMDB3.class);

   public Invocation createInvocation()
   {
      return new Invocation();
   }

   public void init(Endpoint ep)
   {

   }

   public void invoke(Endpoint ep, Invocation epInv) throws Exception
   {
      log.debug("Invoke: " + epInv.getJavaMethod().getName());

      try
      {
         InvocationContext invCtx = epInv.getInvocationContext();
         Object targetBean = invCtx.getTargetBean();
         if (targetBean == null)
         {
            try
            {
               Class epImpl = ep.getTargetBeanClass();
               targetBean = epImpl.newInstance();
               invCtx.setTargetBean(targetBean);
            }
            catch (Exception ex)
            {
               throw new IllegalStateException("Canot get target bean instance", ex);
            }
         }
         Class implClass = targetBean.getClass();
         Method seiMethod = epInv.getJavaMethod();
         Method implMethod = getImplMethod(implClass, seiMethod);

         Object[] args = epInv.getArgs();
         Object retObj = implMethod.invoke(targetBean, args);
         epInv.setReturnValue(retObj);
      }
      catch (Exception e)
      {
         handleInvocationException(e);
      }
   }
}
