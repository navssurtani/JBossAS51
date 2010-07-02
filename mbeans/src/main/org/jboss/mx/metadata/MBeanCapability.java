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
package org.jboss.mx.metadata;

import javax.management.DynamicMBean;
import javax.management.NotCompliantMBeanException;

/**
 * Holds the type of an MBean class.
 *
 * The introspection algorithm used is the following:
 *
 * 1. If MyClass is an instance of the DynamicMBean interface, then the return value of its getMBeanInfo method will
 *    list the attributes and operations of the resource. In other words, MyClass is a dynamic MBean.
 *
 * 2. If the MyClass MBean is an instance of a MyClassMBean interface, then only the methods listed in, or inherited by,
 *    the interface are considered among all the methods of, or inherited by, the MBean. The design patterns are then used to
 *    identify the attributes and operations from the method names in the MyClassMBean interface and its ancestors.
 *    In other words, MyClass is a standard MBean.
 *
 * 3. If MyClass is an instance of the DynamicMBean interface, then MyClassMBean is ignored.
 *    If MyClassMBean is not a public interface, it is not a JMX manageable resource.
 *    If the MBean is an instance of neither MyClassMBean nor DynamicMBean, the inheritance tree of MyClass is examined,
 *    looking for the nearest superclass that implements its own MBean interface.
 *
 *    a. If there is an ancestor called SuperClass that is an instance of SuperClassMBean, the design patterns
 *       are used to derive the attributes and operations from SuperClassMBean. In this case, the MBean MyClass then
 *       has the same management interface as the MBean SuperClass. If SuperClassMBean is not a public interface,
 *       it is not a JMX manageable resource.
 *
 *    b. When there is no superclass with its own MBean interface, MyClass is not a JMX manageable resource.
 *
 * @author  <a href="mailto:trevor@protocool.com">Trevor Squires</a>.
 * @author  thomas.diesler@jboss.org
 */
public class MBeanCapability
{
   public static final int DYNAMIC_MBEAN = 0x321;
   public static final int STANDARD_MBEAN = 0x123;
   public static final int NOT_AN_MBEAN = 0xc0de;

   protected int mbeanType = NOT_AN_MBEAN;

   private MBeanCapability(int type)
   {
      mbeanType = type;
   }

   public int getMBeanType()
   {
      return mbeanType;
   }

   public static MBeanCapability of(Class mbeanClass) throws NotCompliantMBeanException
   {
      if (null == mbeanClass)
      {
         throw new IllegalArgumentException("MBean class cannot be null");
      }

      // If MyClass is an instance of the DynamicMBean interface, MyClass is a dynamic MBean.
      if (DynamicMBean.class.isAssignableFrom(mbeanClass))
      {
         return new MBeanCapability(DYNAMIC_MBEAN);
      }

      // If the MyClass MBean is an instance of a MyClassMBean interface, MyClass is a standard MBean
      Class [] interfaces = mbeanClass.getInterfaces();
      for (int i = 0; i < interfaces.length; i++)
      {
         Class anInterface = interfaces[i];
         if (anInterface.getName().equals(mbeanClass.getName() + "MBean"))
         {
            return new MBeanCapability(STANDARD_MBEAN);
         }
      }

      // If there is an ancestor called SuperClass that is an instance of SuperClassMBean
      Class superClass = mbeanClass.getSuperclass();
      if (superClass != null)
         return of(superClass);

      throw new NotCompliantMBeanException("Class does not expose a management interface: " + mbeanClass.getName());
   }

}
