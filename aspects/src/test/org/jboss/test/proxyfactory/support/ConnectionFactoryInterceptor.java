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
package org.jboss.test.proxyfactory.support;

import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;

import org.jboss.aop.joinpoint.Invocation;

/**
 * ConnectionFactoryInterceptor.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 80997 $
 */
public class ConnectionFactoryInterceptor extends AbstractInterceptor
{
   public static final String CONNECTION_FACTORY = "CONNECTION_FACTORY";
   public static final String CONNECTION_MANAGER = "CONNECTION_MANAGER";
   public static final String MANAGED_CONNECTION_FACTORY = "MANAGED_CONNECTION_FACTORY";
   
   public Object invoke(Invocation invocation) throws Throwable
   {
      ConnectionManager cm = (ConnectionManager) invocation.getMetaData(CONNECTION_FACTORY, CONNECTION_MANAGER);
      ManagedConnectionFactory mcf = (ManagedConnectionFactory) invocation.getMetaData(CONNECTION_MANAGER, MANAGED_CONNECTION_FACTORY);
      
      return cm.allocateConnection(mcf, null);
   }
}
