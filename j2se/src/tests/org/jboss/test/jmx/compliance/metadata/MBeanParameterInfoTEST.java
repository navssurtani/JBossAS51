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
package org.jboss.test.jmx.compliance.metadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.management.MBeanParameterInfo;

import junit.framework.TestCase;

/**
 * MBean Parameter Info tests.<p>
 *
 * @author  <a href="mailto:Adrian.Brock@HappeningTimes.com">Adrian Brock</a>.
 */
public class MBeanParameterInfoTEST
  extends TestCase
{
   // Static --------------------------------------------------------------------

   // Attributes ----------------------------------------------------------------

   // Constructor ---------------------------------------------------------------

   /**
    * Construct the test
    */
   public MBeanParameterInfoTEST(String s)
   {
      super(s);
   }

   // Tests ---------------------------------------------------------------------

   public void testMBeanParameterInfo()
      throws Exception
   {
      MBeanParameterInfo info = new MBeanParameterInfo(
         "name", "type", "description");
      assertEquals("name", info.getName());
      assertEquals("type", info.getType());
      assertEquals("description", info.getDescription());
   }

   public void testHashCode()
      throws Exception
   {
      MBeanParameterInfo info1 = new MBeanParameterInfo("name", "type", "description");
      MBeanParameterInfo info2 = new MBeanParameterInfo("name", "type", "description");

      assertTrue("Different instances with the same hashcode are equal", info1.hashCode() == info2.hashCode());
   }

   public void testEquals()
      throws Exception
   {
      MBeanParameterInfo info = new MBeanParameterInfo(
         "name", "type", "description");

      assertTrue("Null should not be equal", info.equals(null) == false);
      assertTrue("Only MBeanParameterInfo should be equal", info.equals(new Object()) == false);

      MBeanParameterInfo info2 = new MBeanParameterInfo(
         "name", "type", "description");

      assertTrue("Different instances of the same data are equal", info.equals(info2));
      assertTrue("Different instances of the same data are equal", info2.equals(info));

      info2 = new MBeanParameterInfo(
         "name", "type", "description2");

      assertTrue("Different instances with different descriptions are not equal", info.equals(info2) == false);
      assertTrue("Different instances with different descritpions are not equal", info2.equals(info) == false);

      info2 = new MBeanParameterInfo(
         "name2", "type", "description");

      assertTrue("Instances with different names are not equal", info.equals(info2) == false);
      assertTrue("Instances with different names are not equal", info2.equals(info) == false);

      info2 = new MBeanParameterInfo(
         "name", "type2", "description");

      assertTrue("Instances with different types are not equal", info.equals(info2) == false);
      assertTrue("Instances with different types are not equal", info2.equals(info) == false);
   }

   public void testSerialization()
      throws Exception
   {
      MBeanParameterInfo info = new MBeanParameterInfo(
         "name", "type", "description");

      // Serialize it
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(info);
    
      // Deserialize it
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bais);
      Object result = ois.readObject();

      assertEquals(info, result);
   }
}
