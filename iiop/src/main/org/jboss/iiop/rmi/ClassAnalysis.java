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
package org.jboss.iiop.rmi;


/**
 *  Analysis class for classes. These define IDL types.
 *
 *  Routines here are conforming to the "Java(TM) Language to IDL Mapping
 *  Specification", version 1.1 (01-06-07).
 *      
 *  @author <a href="mailto:osh@sparre.dk">Ole Husgaard</a>
 *  @version $Revision: 81018 $
 */
public class ClassAnalysis
   extends AbstractAnalysis
{
   // Constants -----------------------------------------------------
    
   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   /**
    *  Analyze the given class, and return the analysis.
   public static ClassAnalysis getClassAnalysis(Class cls)
      throws RMIIIOPViolationException
   {
      if (cls == null)
         throw new IllegalArgumentException("Cannot analyze NULL class.");
      if (cls == java.lang.String.class || cls == java.lang.Object.class     ||
          cls == java.lang.Class.class  || cls == java.io.Serializable.class ||
          cls == java.io.Externalizable.class ||
          cls == java.rmi.Remote.class)
         throw new IllegalArgumentException("Cannot analyze special class: " +
                                            cls.getName());
 
      if (cls.isPrimitive())
         return PrimitiveAnalysis.getPrimitiveAnalysis(cls);


      if (cls.isInterface() && java.rmi.Remote.class.isAssignableFrom(cls))
         return InterfaceAnalysis.getInterfaceAnalysis(cls);
// TODO
throw new RuntimeException("ClassAnalysis.getClassAnalysis() TODO");
   }
    */

   static private String javaNameOfClass(Class cls)
   {
      if (cls == null)
         throw new IllegalArgumentException("Cannot analyze NULL class.");
 
      String s = cls.getName();
 
      return s.substring(s.lastIndexOf('.')+1);
   }

   // Constructors --------------------------------------------------

   public ClassAnalysis(Class cls, String idlName, String javaName)
   {
      super(idlName, javaName);

      this.cls = cls;
   }

   public ClassAnalysis(Class cls, String javaName)
   {
      this(cls, Util.javaToIDLName(javaName), javaName);
   }

   public ClassAnalysis(Class cls)
   {
      this(cls, javaNameOfClass(cls));
   }

   // Public --------------------------------------------------------

   /**
    *  Return my java class.
    */
   public Class getCls()
   {
      return cls;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   /**
    *  My java class.
    */
   protected Class cls;

   // Private -------------------------------------------------------
}

