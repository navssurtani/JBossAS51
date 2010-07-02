/*
 * JBoss, Home of Professional Open Source
 * Copyright 2007, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.ejb3.deployers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jboss.beans.metadata.plugins.AbstractBeanMetaData;
import org.jboss.beans.metadata.plugins.AbstractConstructorMetaData;
import org.jboss.beans.metadata.plugins.AbstractDemandMetaData;
import org.jboss.beans.metadata.plugins.AbstractValueMetaData;
import org.jboss.beans.metadata.spi.BeanMetaDataFactory;
import org.jboss.beans.metadata.spi.DemandMetaData;
import org.jboss.beans.metadata.spi.SupplyMetaData;
import org.jboss.ejb3.DependencyPolicy;
import org.jboss.ejb3.DeploymentUnit;
import org.jboss.ejb3.KernelAbstraction;
import org.jboss.ejb3.MCDependencyPolicy;
import org.jboss.kernel.Kernel;
import org.jboss.kernel.plugins.deployment.AbstractKernelDeployment;
import org.jboss.kernel.spi.deployment.KernelDeployment;
import org.jboss.kernel.spi.registry.KernelRegistryEntry;
import org.jboss.logging.Logger;

/**
 * The JBossAS implementation of the ejb3 KernelAbstraction integration api
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @author Scott.Stark@jboss.org
 * @version $Revision: 85945 $
 */
public class JBossASKernel
   implements KernelAbstraction
{
   private static Logger log = Logger.getLogger(JBossASKernel.class);
   private MBeanServer mbeanServer;
   private Kernel kernel;

   public JBossASKernel(Kernel kernel)
   {
      this(kernel, null);
   }
   public JBossASKernel(Kernel kernel, MBeanServer mbeanServer)
   {
      this.kernel = kernel;
      this.mbeanServer = mbeanServer;
   }

   public MBeanServer getMbeanServer()
   {
      return mbeanServer;
   }
   public void setMbeanServer(MBeanServer mbeanServer)
   {
      this.mbeanServer = mbeanServer;
   }

   public void install(String name, DependencyPolicy dependencies,
         DeploymentUnit unit, Object service)
   {
      // Look for the kernel deployment
      KernelDeployment deployment = null;
      if(unit != null)
      {
         deployment = (KernelDeployment) unit.getAttachment(KernelDeployment.class.getName());
         if(deployment == null)
         {
            AbstractKernelDeployment akd = new AbstractKernelDeployment();
            akd.setBeanFactories(new ArrayList<BeanMetaDataFactory>());
            deployment = akd;
            String kdname = unit.getShortName();
            deployment.setName(kdname);
            unit.addAttachment(KernelDeployment.class.getName(), deployment);
            log.info("Created KernelDeployment for: "+unit.getShortName());
         }
      }

      // Create the metadata for the bean to install
      AbstractBeanMetaData bean = new AbstractBeanMetaData(name, service.getClass().getName());
      bean.setConstructor(new AlreadyInstantiated(service));
      MCDependencyPolicy policy = (MCDependencyPolicy) dependencies;
      bean.setDepends(policy.getDependencies());
      bean.setDemands(policy.getDemands());
      bean.setSupplies(policy.getSupplies());
      log.info("installing bean: " + name);
      log.info("  with dependencies:");
      for (Object obj : policy.getDependencies())
      {
         Object msgObject = obj;
         if (obj instanceof AbstractDemandMetaData)
         {
            msgObject = ((AbstractDemandMetaData)obj).getDemand();
         }
         log.info("\t" + msgObject);
      }
      log.info("  and demands:");
      for(DemandMetaData dmd : policy.getDemands())
      {
         log.info("\t" + dmd.getDemand());
      }
      log.info("  and supplies:");
      for(SupplyMetaData smd : policy.getSupplies())
      {
         log.info("\t" + smd.getSupply());
      }

      if(unit != null)
      {
         // Just add the mc bean metadata to the unit
         deployment.getBeanFactories().add(bean);
         log.info("Added bean("+name+") to KernelDeployment of: "+unit.getShortName());
      }
      else
      {
         // Install directly into the kernel
         try
         {
            try 
            {
               kernel.getController().uninstall(name);
            }
            catch (IllegalStateException e){}
                  
            log.info("Installing bean("+name+") into kernel");
            kernel.getController().install(bean);
         }
         catch (Throwable throwable)
         {
            throw new RuntimeException(throwable);
         }
      }
   }

   public void installMBean(ObjectName on, DependencyPolicy dependencies,
         Object service)
   {
      if(mbeanServer == null)
         throw new RuntimeException("No MBeanServer has been injected");

      try
      {
         mbeanServer.registerMBean(service, on);
         install(on.getCanonicalName(), dependencies, null, service);
         
         // EJBTHREE-606: emulate the ServiceController calls
         MBeanInfo info = mbeanServer.getMBeanInfo(on); // redundant call for speed
         invokeOptionalMethod(on, info, "create");
         invokeOptionalMethod(on, info, "start");
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   public void uninstall(String name)
   {
      
      
   }

   public Object getAttribute(ObjectName objectName, String attribute) throws Exception
   {
      String name = objectName.getCanonicalName();
      KernelRegistryEntry entry = kernel.getRegistry().getEntry(name);
      if (entry != null)
      {
         Object target = entry.getTarget();
         Field field = target.getClass().getField(attribute);
         return field.get(target);
      }
      return null;
   }
   
   public Set getMBeans(ObjectName query) throws Exception
   {
      Object target = kernel.getRegistry().getEntry(query);
      Set set = new HashSet();
      set.add(target);
      return set;
   }

   public void uninstallMBean(ObjectName on)
   {
      try
      {
         // EJBTHREE-606: emulate the ServiceController calls
         MBeanInfo info = mbeanServer.getMBeanInfo(on); // redundant call for speed
         try
         {
            invokeOptionalMethod(on, info, "stop");
         }
         catch(Exception e)
         {
            log.warn("stop on " + on + " failed", e);
         }
         try
         {
            invokeOptionalMethod(on, info, "destroy");
         }
         catch(Exception e)
         {
            log.warn("destroy on " + on + " failed", e);
         }
         
         mbeanServer.unregisterMBean(on);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   public Object invoke(ObjectName objectName, String operationName, Object[] params, String[] signature) throws Exception
   {
      String name = objectName.getCanonicalName();
      KernelRegistryEntry entry = kernel.getRegistry().getEntry(name);
      if (entry != null)
      {
         Object target = entry.getTarget();
         Class[] types = new Class[signature.length];
         for (int i = 0; i < signature.length; ++i)
         {
            types[i] = Thread.currentThread().getContextClassLoader().loadClass(signature[i]);
         }
         Method method = target.getClass().getMethod(operationName, types);
         return method.invoke(target, params);
      }
      return null;
   }

   private boolean hasOperation(MBeanInfo info, String operationName)
   {
      for(MBeanOperationInfo operationInfo : info.getOperations())
      {
         if(operationInfo.getName().equals(operationName) == false)
            continue;
         
         // void return type
         if(operationInfo.getReturnType().equals("void") == false)
            continue;
         
         // no parameters
         if(operationInfo.getSignature().length != 0)
            continue;
         
         return true;
      }
      
      return false;
   }
   private void invokeOptionalMethod(ObjectName on, MBeanInfo info, String operationName)
      throws InstanceNotFoundException, MBeanException, ReflectionException
   {
      Object params[] = { };
      String signature[] = { };
      if(hasOperation(info, operationName))
         mbeanServer.invoke(on, operationName, params, signature);
   }

   public static class AlreadyInstantiated extends AbstractConstructorMetaData
   {
      private static final long serialVersionUID = 1L;
      
      private Object bean;

      public class Factory
      {

         public Object create()
         {
            return bean;
         }
      }

      public AlreadyInstantiated(Object bean)
      {
         this.bean = bean;
         this.setFactory(new AbstractValueMetaData(new Factory()));
         this.setFactoryClass(Factory.class.getName());
         this.setFactoryMethod("create");
      }
   }
 
}
