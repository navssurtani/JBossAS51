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
package org.jboss.mx.mxbean;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import javax.management.IntrospectionException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationBroadcaster;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport;
import javax.management.openmbean.OpenMBeanConstructorInfo;
import javax.management.openmbean.OpenMBeanConstructorInfoSupport;
import javax.management.openmbean.OpenMBeanInfoSupport;
import javax.management.openmbean.OpenMBeanOperationInfo;
import javax.management.openmbean.OpenMBeanOperationInfoSupport;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenMBeanParameterInfoSupport;
import javax.management.openmbean.OpenType;

import org.jboss.mx.metadata.AbstractBuilder;

/**
 * MXBeanMetaData
 *
 * @author  <a href="mailto:juha@jboss.org">Juha Lindfors</a>.
 * @author  <a href="mailto:trevor@protocool.com">Trevor Squires</a>.
 * @author  <a href="mailto:thomas.diesler@jboss.com">Thomas Diesler</a>.
 * @author  <a href="mailto:dimitris@jboss.org">Dimitris Andreadis</a>.
 */
public class MXBeanMetaData extends AbstractBuilder
{
   /** The MBean */
   private Object mbeanInstance;
   
   /** The class */
   private Class<?> mbeanClass;
   
   /** The interface */
   private Class<?> mbeanInterface;

   /**
    * Find the MXBean interface for a class
    * 
    * @param mbeanClass the mbean class
    * @return the interface
    */
   public static Class<?> findMXBeanInterface(Class<?> mbeanClass)
   {
      Class<?> concrete = mbeanClass;
      while (null != concrete)
      {
         Class result = findMXBeanInterface(concrete, concrete.getInterfaces());
         if (null != result)
            return result;
         concrete = concrete.getSuperclass();
      }
      return null;
   }

   /**
    * Find the MXBean interface for a class
    * 
    * @param concrete the mbean class
    * @param interfaces the interfaces to consider
    * @return the interface
    */
   private static Class<?> findMXBeanInterface(Class<?> concrete, Class<?>[] interfaces)
   {
      String mxName = concrete.getName() + "MXBean";
      String stdName = concrete.getName() + "MBean";

      for (Class<?> intf : interfaces)
      {
         String name = intf.getName();
         if (mxName.equals(name) || stdName.equals(name))
            return intf;
         
         MXBean mxBean = intf.getAnnotation(MXBean.class);
         if (mxBean != null && mxBean.value())
            return intf;
      }
      return null;
   }

   /**
    * Create a new MXBeanMetaData.
    * 
    * @param mbeanInstance the mbean instance
    * @throws NotCompliantMBeanException for any error
    */
   public MXBeanMetaData(Object mbeanInstance) throws NotCompliantMBeanException
   {
      this(mbeanInstance.getClass());
      this.mbeanInstance = mbeanInstance;
   }

   /**
    * Create a new MXBeanMetaData.
    * 
    * @param mbeanClass the class
    * @throws NotCompliantMBeanException for any error
    */
   public MXBeanMetaData(Class<?> mbeanClass)  throws NotCompliantMBeanException
   {
      this.mbeanClass = mbeanClass;
      this.mbeanInterface = findMXBeanInterface(mbeanClass);
      if (this.mbeanInterface == null)
         throw new NotCompliantMBeanException("Cannot obtain MXBean interface for: " + mbeanClass);
   }

   /**
    * Create a new MXBeanMetaData.
    * 
    * @param mbeanInstance the mbean instance
    * @param mbeanInterface the mbean interface
    * @throws NotCompliantMBeanException for any error
    */
   public MXBeanMetaData(Object mbeanInstance, Class<?> mbeanInterface) throws NotCompliantMBeanException
   {
      this.mbeanInstance = mbeanInstance;
      this.mbeanClass = mbeanInstance.getClass();
      this.mbeanInterface = mbeanInterface;
      if (this.mbeanInterface == null)
         this.mbeanInterface = MXBeanMetaData.findMXBeanInterface(mbeanClass);
      if (this.mbeanInterface == null)
         throw new NotCompliantMBeanException("Cannot obtain mxbean interface for: " + mbeanClass);
      if (this.mbeanInterface.isInterface() == false)
         throw new NotCompliantMBeanException("Management interface is not an interface: " + mbeanInterface);
   }

   /**
    * Retrieve the management interface
    * 
    * @return the interface
    */
   public Class<?> getMBeanInterface()
   {
      return mbeanInterface;
   }
   
   public MBeanInfo build() throws NotCompliantMBeanException
   {
      try
      {
         // First check the mbean instance implements the interface
         if (mbeanInterface == null)
            throw new NotCompliantMBeanException("The mbean does not implement a management interface");
         if (mbeanInstance != null && mbeanInterface.isInstance(mbeanInstance) == false)
            throw new NotCompliantMBeanException("The mbean does not implement its management interface " + mbeanInterface.getName());

         OpenMBeanConstructorInfo[] constructorInfo = buildConstructors();

         HashMap<String, Method> getters = new HashMap<String, Method>();
         HashMap<String, Method> setters = new HashMap<String, Method>();

         HashMap<String, OpenMBeanOperationInfo> operInfo = new HashMap<String, OpenMBeanOperationInfo>();
         List<OpenMBeanAttributeInfo> attrInfo = new ArrayList<OpenMBeanAttributeInfo>();

         Method[] methods = mbeanInterface.getMethods();
         for (Method method : methods)
         {
            String methodName = method.getName();
            Type[] signature = method.getGenericParameterTypes();
            Type returnType = method.getGenericReturnType();

            if (methodName.startsWith("set") &&
                methodName.length() > 3 && 
                signature.length == 1 && 
                returnType == Void.TYPE)
            {
               String key = methodName.substring(3, methodName.length());
               Method setter = setters.get(key);
               if (setter != null && setter.getGenericParameterTypes()[0].equals(signature[0]) == false)
                  throw new IntrospectionException("overloaded type for attribute set: " + key);
               setters.put(key, method);
            }
            else if (methodName.startsWith("get") &&
                     methodName.length() > 3 &&
                     signature.length == 0 &&
                     returnType != Void.TYPE)
            {
               String key = methodName.substring(3, methodName.length());
               Method getter = getters.get(key);
               if (getter != null && getter.getName().startsWith("is"))
                  throw new IntrospectionException("mixed use of get/is for attribute " + key);
               getters.put(key, method);
            }
            else if (methodName.startsWith("is") &&
                     methodName.length() > 2 &&
                     signature.length == 0 &&
                     isBooleanReturn(returnType))
            {
               String key = methodName.substring(2, methodName.length());
               Method getter = getters.get(key);
               if (getter != null && getter.getName().startsWith("get"))
                  throw new IntrospectionException("mixed use of get/is for attribute " + key);
               getters.put(key, method);
            }
            else
            {
               OpenMBeanOperationInfo info = buildOperation(method);
               operInfo.put(getSignatureString(method), info);
            }
         }

         String[] keys = getters.keySet().toArray(new String[getters.size()]);
         for (String key : keys)
         {
            Method getter = getters.remove(key);
            Method setter = setters.remove(key);
            OpenMBeanAttributeInfo info = buildAttribute(key, getter, setter);
            attrInfo.add(info);
         }

         for (String key : setters.keySet())
         {
            Method setter = setters.remove(key);
            OpenMBeanAttributeInfo info = buildAttribute(key, null, setter);
            attrInfo.add(info);
         }

         OpenMBeanAttributeInfo[] attributeInfo = attrInfo.toArray(new OpenMBeanAttributeInfo[attrInfo.size()]);
         Collection<OpenMBeanOperationInfo> operations = operInfo.values();
         OpenMBeanOperationInfo[] operationInfo = operations.toArray(new OpenMBeanOperationInfo[operations.size()]);

         MBeanNotificationInfo[] notifications = null;
         if (mbeanInstance instanceof NotificationBroadcaster)
            notifications = ((NotificationBroadcaster) mbeanInstance).getNotificationInfo();
         else
            notifications = new MBeanNotificationInfo[0];

         return buildMBeanInfo(attributeInfo, constructorInfo, operationInfo, notifications);

      }
      catch (Throwable t)
      {
         NotCompliantMBeanException e = new NotCompliantMBeanException("Error generating OpenMBeanInfo.");
         e.initCause(t);
         throw e;
      }
   }

   /**
    * Build the constructors
    * 
    * @return the info
    * @throws Exception for any error
    */
   private OpenMBeanConstructorInfo[] buildConstructors() throws Exception
   {
      Constructor<?>[] constructors = mbeanClass.getConstructors();
      OpenMBeanConstructorInfo[] constructorInfo = new OpenMBeanConstructorInfo[constructors.length];
      for (int i = 0; i < constructors.length; ++i)
         constructorInfo[i] = buildConstructor(constructors[i]);
      return constructorInfo;
   }
   
   /**
    * Build a constructor
    * 
    * @param constructor the constructor
    * @return the info 
    * @throws Exception for any error
    */
   private OpenMBeanConstructorInfo buildConstructor(Constructor<?> constructor) throws Exception
   {
      Type[] parameterTypes = constructor.getGenericParameterTypes();
      OpenMBeanParameterInfo[] parameterInfo = new OpenMBeanParameterInfo[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; ++i)
         parameterInfo[i] = buildParameter(i, parameterTypes[i]);
      return new OpenMBeanConstructorInfoSupport("MBean Constructor.", "MBean Constructor.", parameterInfo);
   }

   /**
    * Build a parameter
    * 
    * @param i the index of the parameter
    * @param parameterType the parameter type
    * @return the info
    * @throws Exception for any error
    */
   private OpenMBeanParameterInfo buildParameter(int i, Type parameterType) throws Exception
   {
      OpenType openType = MXBeanUtils.getOpenType(parameterType);
      return new OpenMBeanParameterInfoSupport("arg" + i, "MBean Parameter.", openType);
   }

   /**
    * Build an attribute
    * 
    * @param attrName the attribute name
    * @param getter the getter
    * @param setter the setter
    * @return the info
    * @throws Exception for any error
    */
   private OpenMBeanAttributeInfo buildAttribute(String attrName, Method getter, Method setter) throws Exception
   {
      boolean isReadable = (getter != null);
      boolean isWritable = (setter != null);
      boolean isIs = false;
      
      OpenType openType = null; 
      if (getter != null)
      {
         openType = MXBeanUtils.getOpenType(getter.getGenericReturnType());
         if (getter.getName().startsWith("is"))
            isIs = true;
      }
      else
      {
         openType = MXBeanUtils.getOpenType(setter.getGenericParameterTypes()[0]);
      }
      
      return new OpenMBeanAttributeInfoSupport(attrName, attrName, openType, isReadable, isWritable, isIs);
   }
   
   /**
    * Build the operation info
    * 
    * @param method the method
    * @return the info
    * @throws Exception for any error
    */
   private OpenMBeanOperationInfo buildOperation(Method method) throws Exception
   {
      Type[] parameterTypes = method.getGenericParameterTypes();
      OpenMBeanParameterInfo[] parameterInfo = new OpenMBeanParameterInfo[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; ++i)
         parameterInfo[i] = buildParameter(i, parameterTypes[i]);
      OpenType openType = MXBeanUtils.getOpenType(method.getGenericReturnType());
      return new OpenMBeanOperationInfoSupport(method.getName(), method.getName(), parameterInfo, openType, MBeanOperationInfo.ACTION);
   }
   
   /**
    * Build the mbean info
    * 
    * @param attributes the attributes
    * @param constructors the constructors
    * @param operations the operations
    * @param notifications the notifications
    * @return the info
    * @throws Exception for any error
    */
   private OpenMBeanInfoSupport buildMBeanInfo(OpenMBeanAttributeInfo[] attributes, OpenMBeanConstructorInfo[] constructors, OpenMBeanOperationInfo[] operations, MBeanNotificationInfo[] notifications) throws Exception
   {
      return new OpenMBeanInfoSupport(mbeanClass.getName(), mbeanClass.getName(), attributes, constructors, operations, notifications);
   }
   
   /**
    * JMX standard specifies that only "boolean isX()" style methods
    * represent attributes. "Boolean isX()" methods map to operations.
    * 
    * @param returnType the return type
    * @return true when boolean
    */
   private boolean isBooleanReturn(Type returnType)
   {
      return returnType == Boolean.TYPE;
   }

   /**
    * Get a signature string for a method
    * 
    * @param method the method
    * @return the signature
    */
   private String getSignatureString(Method method)
   {
      String name = method.getName();
      Class[] signature = method.getParameterTypes();
      StringBuffer buffer = new StringBuffer(512);
      buffer.append(name);
      buffer.append("(");
      if (signature != null)
      {
         for (int i = 0; i < signature.length; i++)
         {
            buffer.append(signature[i].getName());
            if (i < signature.length-1)
               buffer.append(",");
         }
      }
      buffer.append(")");
      return buffer.toString();
   }
}

