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
package org.jboss.resource.deployers.management;

import java.io.ObjectStreamException;
import java.io.Serializable;

import org.jboss.beans.info.spi.BeanInfo;
import org.jboss.beans.info.spi.PropertyInfo;
import org.jboss.managed.api.Fields;
import org.jboss.managed.api.ManagedObject;
import org.jboss.managed.api.factory.ManagedObjectFactory;
import org.jboss.managed.plugins.ManagedPropertyImpl;
import org.jboss.managed.plugins.factory.ManagedObjectFactoryBuilder;
import org.jboss.managed.spi.factory.InstanceClassFactory;
import org.jboss.metatype.api.types.MapCompositeMetaType;
import org.jboss.metatype.api.types.MetaType;
import org.jboss.metatype.api.types.SimpleMetaType;
import org.jboss.metatype.api.values.MetaValue;
import org.jboss.metatype.api.values.MetaValueFactory;

/**
 * @author <a href="mailto:alex@jboss.org">Alexey Loubyansky</a>
 * @version <tt>$Revision: 85526 $</tt>
 */
public class XAConnectionFactoryProperty
   extends ManagedPropertyImpl
{
   private static final long serialVersionUID = 1;

   private MetaValueFactory metaValueFactory;
   private ManagedObjectFactory moFactory;
   private MapCompositeMetaType type;


   public XAConnectionFactoryProperty(String s)
   {
      super(s);
      type = new MapCompositeMetaType(SimpleMetaType.STRING);
      setField(Fields.META_TYPE, null);
   }

   public XAConnectionFactoryProperty(Fields fields)
   {
      super(fields);
      type = new MapCompositeMetaType(SimpleMetaType.STRING);
      setField(Fields.META_TYPE, null);
   }

   public XAConnectionFactoryProperty(ManagedObject managedObject, Fields fields)
   {
      super(managedObject, fields);
      type = new MapCompositeMetaType(SimpleMetaType.STRING);
      setField(Fields.META_TYPE, null);
   }


   public MetaType getMetaType()
   {
      return type;
   }

   public void setField(String fieldName, Serializable value)
   {
      if(Fields.META_TYPE.equals(fieldName))
         value = type;
      super.setField(fieldName, value);
   }

   /**
    * Write the value back to the attachment if there is a PropertyInfo
    * in the Fields.PROPERTY_INFO field.
    */
   @Override
   @SuppressWarnings("unchecked")
   public void setValue(MetaValue value)
   {
      super.setValue(value);

      PropertyInfo propertyInfo = getField(Fields.PROPERTY_INFO, PropertyInfo.class);
      if (propertyInfo != null)
      {
         Object attachment = getManagedObject().getAttachment();
         if (attachment != null)
         {
            InstanceClassFactory icf = getMOFactory().getInstanceClassFactory(attachment.getClass());
            BeanInfo beanInfo = propertyInfo.getBeanInfo();
            icf.setValue(beanInfo, this, attachment, value);
         }
      }
   }

   private ManagedObjectFactory getMOFactory()
   {
      if(moFactory == null)
         moFactory = ManagedObjectFactoryBuilder.create();
      return moFactory;
   }

   private MetaValueFactory getMetaValueFactory()
   {
      if(metaValueFactory == null)
         metaValueFactory = MetaValueFactory.getInstance();
      return metaValueFactory;
   }

   /**
    * Expose only plain ManangedPropertyImpl.
    *
    * @return simpler ManagedPropertyImpl
    * @throws java.io.ObjectStreamException for any error
    */
   private Object writeReplace() throws ObjectStreamException
   {
      ManagedPropertyImpl managedProperty = new ManagedPropertyImpl(getManagedObject(), getFields());
      managedProperty.setTargetManagedObject(getTargetManagedObject());
      return managedProperty;
   }

}
