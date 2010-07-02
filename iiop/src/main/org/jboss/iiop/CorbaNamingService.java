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
package org.jboss.iiop;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.jacorb.config.Configuration;
import org.jboss.logging.Logger;
import org.jboss.system.ServiceMBeanSupport;
import org.omg.CORBA.ORB;
import org.omg.CORBA.Policy;
import org.omg.CosNaming.Binding;
import org.omg.CosNaming.BindingHolder;
import org.omg.CosNaming.BindingIteratorHolder;
import org.omg.CosNaming.BindingListHolder;
import org.omg.CosNaming.BindingType;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContext;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.NamingContextHelper;
import org.omg.PortableServer.IdAssignmentPolicyValue;
import org.omg.PortableServer.LifespanPolicyValue;
import org.omg.PortableServer.POA;

/**
 * This is a JMX service that provides the default CORBA naming service
 * for JBoss to use.
 *      
 * @author  <a href="mailto:reverbel@ime.usp.br">Francisco Reverbel</a>
 * @version $Revision: 91795 $
 */
public class CorbaNamingService
   extends ServiceMBeanSupport
   implements CorbaNamingServiceMBean, ObjectFactory
{
   // Constants -----------------------------------------------------
   public static String NAMING_NAME = "JBossCorbaNaming";
    
   // Attributes ----------------------------------------------------

   /** The POA used by the CORBA naming service. */
   private POA namingPOA;

   // Static --------------------------------------------------------

   /** Root naming context (returned by <code>getObjectInstance()</code>). */
   private static NamingContextExt namingService;

   /** List the CORBA naming service contents.
    * 
    * @return
    */ 
   public String list()
   {
      StringBuffer buf = new StringBuffer();
      rlist(namingService, new NameComponent[0], buf);
      return buf.toString();
   }

   // ServiceMBeanSupport overrides ---------------------------------

   protected void startService()
      throws Exception
   {
      Context ctx;
      ORB orb;
      POA rootPOA;

      try {
         ctx = new InitialContext();
      }
      catch (NamingException e) {
         throw new RuntimeException("Cannot get intial JNDI context: " + e);
      }
      try {
         orb = (ORB)ctx.lookup("java:/" + CorbaORBService.ORB_NAME);
      } 
      catch (NamingException e) {
         throw new RuntimeException("Cannot lookup java:/" 
                                    + CorbaORBService.ORB_NAME + ": " + e);
      }
      try {
         rootPOA = (POA)ctx.lookup("java:/" + CorbaORBService.POA_NAME);
      } 
      catch (NamingException e) {
         throw new RuntimeException("Cannot lookup java:/" 
                                    + CorbaORBService.POA_NAME + ": " + e);
      }

      // Create the naming server POA as a child of the root POA
      Policy[] policies = new Policy[2];
      policies[0] = 
         rootPOA.create_id_assignment_policy(IdAssignmentPolicyValue.USER_ID);
      policies[1] = 
         rootPOA.create_lifespan_policy(LifespanPolicyValue.PERSISTENT);
      namingPOA = rootPOA.create_POA("Naming", null, policies);
      namingPOA.the_POAManager().activate();

      // Create the naming service
      org.jacorb.naming.NamingContextImpl.init(orb, rootPOA);
      NamingContextImpl ns = new NamingContextImpl(namingPOA);
      Configuration config = ((org.jacorb.orb.ORB)orb).getConfiguration();
      ns.configure(config); // configure the name service using the ORB config
      byte[] rootContextId = "root".getBytes();
      namingPOA.activate_object_with_id(rootContextId, ns);
      namingService = NamingContextExtHelper.narrow(
                  namingPOA.create_reference_with_id(rootContextId, 
                                "IDL:omg.org/CosNaming/NamingContextExt:1.0"));
      bind(NAMING_NAME, "org.omg.CosNaming.NamingContextExt");
      getLog().info("CORBA Naming Started");
      getLog().debug("Naming: ["+orb.object_to_string(namingService)+"]");
   }
    
   protected void stopService()
   {
      // Unbind from JNDI
      try {
         unbind(NAMING_NAME);
      } catch (Exception e) {
         log.error("Exception while stopping CORBA naming service", e);
      }

      // Destroy naming POA
      try {
         namingPOA.destroy(false, false);
      } catch (Exception e) {
         log.error("Exception while stopping CORBA naming service", e);
      }
   }
    
   // ObjectFactory implementation ----------------------------------

   public Object getObjectInstance(Object obj, Name name,
                                   Context nameCtx, Hashtable environment)
      throws Exception
   {
      String s = name.toString();
      if (getLog().isTraceEnabled())
         getLog().trace("getObjectInstance: obj.getClass().getName=\"" +
                        obj.getClass().getName() +
                        "\n                   name=" + s);
      if (NAMING_NAME.equals(s))
         return namingService;
      else
         return null;
   }

   // Private -------------------------------------------------------

   private void bind(String name, String className)
      throws Exception
   {
      Reference ref = new Reference(className, getClass().getName(), null);
      new InitialContext().bind("java:/"+name, ref);
   }

   private void unbind(String name)
      throws Exception
   {
      new InitialContext().unbind("java:/"+name);
   }

   private static void rlist(NamingContext ctx, NameComponent[] base,
      StringBuffer buf)
   {
      BindingListHolder listHolder = new BindingListHolder(new Binding[0]);
      BindingIteratorHolder iterHolder = new BindingIteratorHolder();
      ctx.list(0, listHolder, iterHolder);
      BindingHolder bindingHolder = new BindingHolder();

      if (iterHolder.value == null )
         return;

      NameComponent[] name = new NameComponent[base.length + 1];
      for (int i = 0; i < base.length; i++)
         name[i] = base[i];

      while (iterHolder.value.next_one(bindingHolder))
      {
         Binding binding = bindingHolder.value;
         name[name.length - 1] = binding.binding_name[0];
         try
         {
            String stringName = namingService.to_string(name);
            buf.append(stringName);
         }
         catch(Exception e)
         {
            buf.append(e.getMessage());
         }

         if (binding.binding_type.value() == BindingType._ncontext)
         {
            // this entry is for a subcontext
            // add trailing '/' just to distinguish
            // a subcontext from a regular object
            buf.append('/');
            buf.append('\n');

            // recursively list the subcontext contents
            try
            {
               NamingContext subCtx =
                  NamingContextHelper.narrow(ctx.resolve(binding.binding_name));
               rlist(subCtx, name, buf);
            }
            catch(Exception e)
            {
               buf.append(e.getMessage());
            }
         }
         else
         {
            buf.append('\n');
         }
      }
   }

   // Static inner class --------------------------------------------

   /**
    * This subclass of <code>org.jacorb.naming.NamingContextImpl</code>
    * overrides the method <code>new_context()</code>, because its
    * implementation in <code>org.jacorb.naming.NamingContextImpl</code>
    * is not suitable for our in-VM naming server. The superclass 
    * implementation of <code>new_context()</code> assumes that naming context
    * states are persistently stored and requires a servant activator that
    * reads context states from persistent storage.
    */
   static class NamingContextImpl
      extends org.jacorb.naming.NamingContextImpl
   {
      private POA poa;
      private int childCount = 0;
      private static final Logger logger = 
                              Logger.getLogger(NamingContextImpl.class);

      NamingContextImpl(POA poa)
      {
         this.poa = poa;
      }

      public NamingContext new_context() 
      {
         try {
            NamingContextImpl newContextImpl = new NamingContextImpl(poa);
            byte[] oid = (new String(poa.servant_to_id(this)) +  
                          "/ctx" + (++childCount)).getBytes();
            poa.activate_object_with_id(oid, newContextImpl);
            return NamingContextExtHelper.narrow(
                        poa.create_reference_with_id(oid, 
                                "IDL:omg.org/CosNaming/NamingContextExt:1.0"));
         }
         catch (Exception e) {
            logger.error("Cannot create CORBA naming context", e);
            return null;
         }
      }
   }

}
