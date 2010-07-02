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
package org.jboss.ejb;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.security.auth.Subject;
import javax.security.jacc.PolicyContext;
import javax.security.jacc.PolicyContextException;

import org.jboss.mx.util.MBeanProxy;   
import org.jboss.security.SecurityContext;  
import org.jboss.security.SecurityContextAssociation;
import org.jboss.security.javaee.AbstractEJBAuthorizationHelper;
import org.jboss.security.javaee.SecurityHelperFactory;
import org.jboss.security.javaee.SecurityRoleRef;

/** A collection of privileged actions for this package
 * 
 * @author Scott.Stark@jboss.org
 * @author Anil.Saldhana@jboss.org
 * @version $Revison:$
 */
class SecurityActions
{
   private static class SetContextID implements PrivilegedAction
   {
      String contextID;
      SetContextID(String contextID)
      {
         this.contextID = contextID;
      }
      public Object run()
      {
         String previousID = PolicyContext.getContextID();
         PolicyContext.setContextID(contextID);
         return previousID;
      }
   } 

   /**
    * Wrap the MBeanProxy proxy in a priviledged action so that method
    * dispatch is done from a PrivilegedExceptionAction
    */
   private static class MBeanProxyAction implements PrivilegedExceptionAction
   {
      Class iface;
      ObjectName name;
      MBeanServer server;

      MBeanProxyAction(Class iface, ObjectName name, MBeanServer server)
      {
         this.iface = iface;
         this.name = name;
         this.server = server;
      }
      public Object run() throws Exception
      {
         Object proxy = MBeanProxy.get(iface, name, server);
         Class[] ifaces = {iface};
         InvocationHandler secureHandler = new InvocationHandlerAction(proxy);
         Object secureProxy = Proxy.newProxyInstance(iface.getClassLoader(), ifaces, secureHandler);
         return secureProxy;
      }
   }

   private static class InvocationHandlerAction
      implements InvocationHandler, PrivilegedExceptionAction
   {
      private Method method;
      private Object[] args;
      private Object mbean;
      private InvocationHandlerAction(Object mbean)
      {
         this.mbean = mbean;
      }
      public Object invoke(Object proxy, Method method, Object[] args)
         throws Throwable
      {
         this.method = method;
         this.args = args;
         Object value;
         try
         {
            value = AccessController.doPrivileged(this);
         }
         catch(PrivilegedActionException e)
         {
            throw e.getException();
         }
         return value;
      }
      public Object run() throws Exception
      {
         Object value = method.invoke(mbean, args);
         return value;
      }
   }

   static Object getMBeanProxy(Class iface, ObjectName name, MBeanServer server)
      throws Exception
   {
      Object proxy;
      if( System.getSecurityManager() == null )
      {
         proxy = MBeanProxy.get(iface, name, server);
      }
      else
      {
         MBeanProxyAction action = new MBeanProxyAction(iface, name, server);
         proxy = AccessController.doPrivileged(action);
      }
      return proxy;
   }
   static ClassLoader getContextClassLoader()
   {
      return TCLAction.UTIL.getContextClassLoader();
   }

   static ClassLoader getContextClassLoader(Thread thread)
   {
      return TCLAction.UTIL.getContextClassLoader(thread);
   }

   static void setContextClassLoader(ClassLoader loader)
   {
      TCLAction.UTIL.setContextClassLoader(loader);
   }

   static void setContextClassLoader(Thread thread, ClassLoader loader)
   {
      TCLAction.UTIL.setContextClassLoader(thread, loader);
   }

   static String setContextID(String contextID)
   {
      PrivilegedAction action = new SetContextID(contextID);
      String previousID = (String) AccessController.doPrivileged(action);
      return previousID;
   } 
   
   static Principal getCallerPrincipal(SecurityContext sc)
   {
      return IdentityAction.UTIL.getIdentityAction().getCallerPrincipal(sc);
   }

   interface IdentityAction
   {
      class UTIL
      {
         static IdentityAction getIdentityAction()
         {
            return System.getSecurityManager() == null ? NON_PRIVILEGED : PRIVILEGED;
         }
      }
      IdentityAction NON_PRIVILEGED = new IdentityAction()
      {  
         public Principal getCallerPrincipal(SecurityContext securityContext)
         {
            Principal caller = null;
            
            if(securityContext != null)
            {
               caller = securityContext.getIncomingRunAs();
               //If there is no caller run as, use the call principal
               if(caller == null)
                  caller = securityContext.getUtil().getUserPrincipal();
            }
            return caller;
         }
         
      };
      IdentityAction PRIVILEGED = new IdentityAction()
      { 
         public Principal getCallerPrincipal(final SecurityContext securityContext)
         {
            return (Principal)AccessController.doPrivileged(new PrivilegedAction(){
        
               public Object run()
               { 
                  Principal caller = null;
                  
                  if(securityContext != null)
                  {
                     caller = securityContext.getIncomingRunAs();
                     //If there is no caller run as, use the call principal
                     if(caller == null)
                        caller = securityContext.getUtil().getUserPrincipal();
                  }
                  return caller;
               }});
         }
      }; 
      Principal getCallerPrincipal(SecurityContext sc);
   }

   interface TCLAction
   {
      class UTIL
      {
         static TCLAction getTCLAction()
         {
            return System.getSecurityManager() == null ? NON_PRIVILEGED : PRIVILEGED;
         }

         static ClassLoader getContextClassLoader()
         {
            return getTCLAction().getContextClassLoader();
         }

         static ClassLoader getContextClassLoader(Thread thread)
         {
            return getTCLAction().getContextClassLoader(thread);
         }

         static void setContextClassLoader(ClassLoader cl)
         {
            getTCLAction().setContextClassLoader(cl);
         }

         static void setContextClassLoader(Thread thread, ClassLoader cl)
         {
            getTCLAction().setContextClassLoader(thread, cl);
         }
      }

      TCLAction NON_PRIVILEGED = new TCLAction()
      {
         public ClassLoader getContextClassLoader()
         {
            return Thread.currentThread().getContextClassLoader();
         }

         public ClassLoader getContextClassLoader(Thread thread)
         {
            return thread.getContextClassLoader();
         }

         public void setContextClassLoader(ClassLoader cl)
         {
            Thread.currentThread().setContextClassLoader(cl);
         }

         public void setContextClassLoader(Thread thread, ClassLoader cl)
         {
            thread.setContextClassLoader(cl);
         }
      };

      TCLAction PRIVILEGED = new TCLAction()
      {
         private final PrivilegedAction getTCLPrivilegedAction = new PrivilegedAction()
         {
            public Object run()
            {
               return Thread.currentThread().getContextClassLoader();
            }
         };

         public ClassLoader getContextClassLoader()
         {
            return (ClassLoader)AccessController.doPrivileged(getTCLPrivilegedAction);
         }

         public ClassLoader getContextClassLoader(final Thread thread)
         {
            return (ClassLoader)AccessController.doPrivileged(new PrivilegedAction()
            {
               public Object run()
               {
                  return thread.getContextClassLoader();
               }
            });
         }

         public void setContextClassLoader(final ClassLoader cl)
         {
            AccessController.doPrivileged(
               new PrivilegedAction()
               {
                  public Object run()
                  {
                     Thread.currentThread().setContextClassLoader(cl);
                     return null;
                  }
               }
            );
         }

         public void setContextClassLoader(final Thread thread, final ClassLoader cl)
         {
            AccessController.doPrivileged(
               new PrivilegedAction()
               {
                  public Object run()
                  {
                     thread.setContextClassLoader(cl);
                     return null;
                  }
               }
            );
         }
      };

      ClassLoader getContextClassLoader();

      ClassLoader getContextClassLoader(Thread thread);

      void setContextClassLoader(ClassLoader cl);

      void setContextClassLoader(Thread thread, ClassLoader cl);
   }
   
   interface PolicyContextActions 
   { 
      /** The JACC PolicyContext key for the current Subject */ 
      static final String SUBJECT_CONTEXT_KEY = "javax.security.auth.Subject.container"; 
      PolicyContextActions PRIVILEGED = new PolicyContextActions() 
      { 
         private final PrivilegedExceptionAction exAction = new PrivilegedExceptionAction() 
         { 
            public Object run() throws Exception 
            { 
               return (Subject) PolicyContext.getContext(SUBJECT_CONTEXT_KEY); 
            } 
         }; 
         public Subject getContextSubject() 
         throws PolicyContextException 
         { 
            try 
            { 
               return (Subject) AccessController.doPrivileged(exAction); 
            } 
            catch(PrivilegedActionException e) 
            { 
               Exception ex = e.getException(); 
               if( ex instanceof PolicyContextException ) 
                  throw (PolicyContextException) ex; 
               else 
                  throw new UndeclaredThrowableException(ex); 
            } 
         } 
      }; 

      PolicyContextActions NON_PRIVILEGED = new PolicyContextActions() 
      { 
         public Subject getContextSubject() 
         throws PolicyContextException 
         { 
            return (Subject) PolicyContext.getContext(SUBJECT_CONTEXT_KEY); 
         } 
      }; 

      Subject getContextSubject() 
      throws PolicyContextException; 
   } 

   static Subject getContextSubject()  
   throws PolicyContextException 
   { 
      if(System.getSecurityManager() == null) 
      { 
         return PolicyContextActions.NON_PRIVILEGED.getContextSubject(); 
      } 
      else 
      { 
         return PolicyContextActions.PRIVILEGED.getContextSubject(); 
      }    
   }

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
   
   static void setSecurityContext(final SecurityContext securityContext) 
   { 
      AccessController.doPrivileged(new PrivilegedAction<SecurityContext>()
      {
         public SecurityContext run() 
         {
            SecurityContextAssociation.setSecurityContext(securityContext);
            return null;
         }
      });
   }
   
   static boolean isCallerInRole(final SecurityContext sc, final String roleName,
         final String ejbName, final Principal principal, final Subject contextSubject,
         final String jaccContextID, final Set<SecurityRoleRef> securityRoleRefs)
   throws PrivilegedActionException
   {
      return AccessController.doPrivileged(new PrivilegedExceptionAction<Boolean>()
      {
         public Boolean run() throws Exception
         {
            AbstractEJBAuthorizationHelper helper = SecurityHelperFactory.getEJBAuthorizationHelper(sc); 
            return helper.isCallerInRole(roleName, 
                  ejbName, principal, contextSubject, 
                  jaccContextID, securityRoleRefs);
         }
      });
   }
}
