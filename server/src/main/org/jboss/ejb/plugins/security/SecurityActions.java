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
package org.jboss.ejb.plugins.security;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import org.jboss.security.ISecurityManagement;
import org.jboss.security.RunAs;
import org.jboss.security.SecurityContext;
import org.jboss.security.SecurityIdentity;
import org.jboss.security.SecurityContextFactory;
import org.jboss.security.SecurityContextAssociation;
 
/**
 *  Privileged Blocks
 *  @author Anil.Saldhana@redhat.com
 *  @since  Apr 30, 2007 
 *  @version $Revision: 99007 $
 */
class SecurityActions
{
   static SecurityContext createAndSetSecurityContext(final String domain,
         final String fqnClassName) throws PrivilegedActionException
   {
      return AccessController.doPrivileged(new PrivilegedExceptionAction<SecurityContext>()
      { 
         public SecurityContext run() throws Exception
         {
            SecurityContext sc =  SecurityContextFactory.createSecurityContext(domain, fqnClassName); 
            setSecurityContext(sc);
            return sc;
         }}
      );
   }

   static SecurityContext getSecurityContext()
   {
      return AccessController.doPrivileged(new PrivilegedAction<SecurityContext>()
      { 
         public SecurityContext run()
         {
            return SecurityContextAssociation.getSecurityContext(); 
         }}
      );
   }
   
   static void pushCallerRunAsIdentity(final RunAs ra)
   {
      AccessController.doPrivileged(new PrivilegedAction<Object>(){ 
         public Object run()
         {
            SecurityContext sc = SecurityContextAssociation.getSecurityContext();
            if(sc == null)
               throw new IllegalStateException("Security Context is null");
            sc.setIncomingRunAs(ra);
            return null;
         } 
      }); 
   }
   

   static void popCallerRunAsIdentity()
   {
      AccessController.doPrivileged(new PrivilegedAction<Object>(){ 
         public Object run()
         {
            SecurityContext sc = SecurityContextAssociation.getSecurityContext();
            if(sc == null)
               throw new IllegalStateException("Security Context is null");
            sc.setIncomingRunAs(null);
            return null;
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
         }}
      );
   }

   static void setSecurityIdentity(final SecurityContext sc,
         final SecurityIdentity si)
   {
      AccessController.doPrivileged(new PrivilegedAction<Object>()
      { 
         public Object run()
         {
            sc.getUtil().setSecurityIdentity(si);
            return null;
         }}
      );
   }
   
   static SecurityIdentity getSecurityIdentity(final SecurityContext sc)
   {
      return AccessController.doPrivileged(new PrivilegedAction<SecurityIdentity>()
      { 
         public SecurityIdentity run()
         { 
            return sc.getUtil().getSecurityIdentity();
         }
      });
   }
   
   static void setSecurityManagement(final SecurityContext sc, final ISecurityManagement sm)
   {
      AccessController.doPrivileged(new PrivilegedAction<Object>()
      { 
         public Object run()
         {
            sc.setSecurityManagement(sm);
            return null;
         }}
      );
   }
   
   static void setSecurityDomain(final SecurityContext sc, final String domain)
   {
	   AccessController.doPrivileged(new PrivilegedAction<Object>()
	   {
		  public Object run() 
		  {
			sc.setSecurityDomain(domain);  
			return null;
		  } 
	   });
   }
   
   static String trace(final SecurityContext sc)
   {
      return AccessController.doPrivileged(new PrivilegedAction<String>()
      { 
         public String run()
         {
            StringBuilder sb = new StringBuilder();
            sb.append(" Principal = " + sc.getUtil().getUserPrincipal());
            sb.append(" Subject:"+sc.getUtil().getSubject());
            sb.append(" Incoming run as:"+sc.getIncomingRunAs());
            sb.append(" Outgoing run as:"+sc.getOutgoingRunAs());
            return sb.toString();
         }
      }
      );
   }
   
   static String trace(final SecurityIdentity si)
   {
      return AccessController.doPrivileged(new PrivilegedAction<String>()
      { 
         public String run()
         {
            StringBuilder sb = new StringBuilder();
            sb.append(" Principal = " + si.getPrincipal());
            sb.append(" Subject:"+si.getSubject());
            sb.append(" Incoming run as:"+si.getIncomingRunAs());
            sb.append(" Outgoing run as:"+si.getOutgoingRunAs());
            return sb.toString();
         }
      }
      );
   }
}
