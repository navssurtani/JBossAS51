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
package org.jboss.test.security.ejb3;

import java.security.Principal;

import javax.annotation.Resource;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Remote;
import javax.ejb.SessionContext;
import javax.ejb.Stateful;

/**
 * <p>
 * Stateful session bean implementation used in the EJB3 security tests.
 * </p>
 * 
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
@Stateful
@Remote(SimpleSession.class)
@RolesAllowed({"RegularUser", "Administrator"})
public class SimpleStatefulSessionBean implements SimpleSession
{

   @Resource
   private SessionContext context;

   /*
    * (non-Javadoc)
    * 
    * @see org.jboss.test.security.ejb3.SimpleSession#invokeRegularMethod()
    */
   public Principal invokeRegularMethod()
   {
      // this method allows the same roles as the class.
      return this.context.getCallerPrincipal();
   }

   /*
    * (non-Javadoc)
    * 
    * @see org.jboss.test.security.ejb3.SimpleSession#invokeAdministrativeMethod()
    */
   @RolesAllowed({"Administrator"})
   public Principal invokeAdministrativeMethod()
   {
      // this method overrides the roles defined by the class to grant access to admnistrators only.
      return this.context.getCallerPrincipal();
   }

   /*
    * (non-Javadoc)
    * 
    * @see org.jboss.test.security.ejb3.SimpleSession#invokeUnprotectedMethod()
    */
   @PermitAll
   public Principal invokeUnprotectedMethod()
   {
      // this method overrides the roles defined by the class to grant access to all roles.
      return this.context.getCallerPrincipal();
   }

   /*
    * (non-Javadoc)
    * 
    * @see org.jboss.test.security.ejb3.SimpleSession#invokeUnavailableMethod()
    */
   @DenyAll
   public Principal invokeUnavailableMethod()
   {
      // this method should never be called - it overrides the class roles to deny access to all roles.
      return this.context.getCallerPrincipal();
   }

}
