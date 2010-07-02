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
package org.jboss.proxy.ejb;

import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

import org.jboss.security.RunAs;
import org.jboss.security.SecurityContext;
import org.jboss.security.SecurityContextAssociation;
import org.jboss.security.SecurityContextFactory;

/**
 * Privileged Blocks
 * @author Anil.Saldhana@redhat.com
 * @since Dec 1, 2008
 */
class SecurityActions
{
  static SecurityContext getSecurityContext()
  {
     return AccessController.doPrivileged(new PrivilegedAction<SecurityContext>()
     {
        public SecurityContext run()
        {
           return SecurityContextAssociation.getSecurityContext();
        }
     });
  }
  
  static RunAs getCallerRunAsIdentity()
  {
     return AccessController.doPrivileged(new PrivilegedAction<RunAs>()
     {
        public RunAs run()
        {
           RunAs rai = null;
           //Pluck the run-as identity from the existing SC if any
           SecurityContext existingSC = getSecurityContext();
           if(existingSC != null)
           { 
              rai = existingSC.getOutgoingRunAs();
           }
           return rai;
        }
     });
  }
 

  static void setSecurityContext(final SecurityContext sc)
  {
     AccessController.doPrivileged(new PrivilegedAction<Object>()
     {
        public Object run()
        {
           SecurityContextAssociation.setSecurityContext(sc);
           return null;
        }
     }); 
  }
  
  static SecurityContext createSecurityContext(final Principal p, final Object cred, 
        final String sdomain) throws Exception
  {
     return AccessController.doPrivileged(new PrivilegedExceptionAction<SecurityContext>()
     {
        public SecurityContext run() throws Exception
        {
           return SecurityContextFactory.createSecurityContext(p,cred, null, sdomain);
        }
     }); 
  }
  
  static void setIncomingRunAs(final SecurityContext sc, final RunAs incomingRunAs)
  {
     AccessController.doPrivileged(new PrivilegedAction<Object>()
     {
        public Object run()
        {
           sc.setOutgoingRunAs(incomingRunAs);
           return null;
        }
     });
  }

  static void setOutgoingRunAs(final SecurityContext sc, final RunAs outgoingRunAs)
  {
     AccessController.doPrivileged(new PrivilegedAction<Object>()
     {
        public Object run()
        {
           sc.setOutgoingRunAs(outgoingRunAs);
           return null;
        }
     });
  } 
}