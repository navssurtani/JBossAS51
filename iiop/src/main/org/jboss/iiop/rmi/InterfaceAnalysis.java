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

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;


/**
 *  Interface analysis.
 *
 *  Routines here are conforming to the "Java(TM) Language to IDL Mapping
 *  Specification", version 1.1 (01-06-07).
 *      
 *  @author <a href="mailto:osh@sparre.dk">Ole Husgaard</a>
 *  @version $Revision: 81018 $
 */
public class InterfaceAnalysis
   extends ContainerAnalysis
{
   // Constants -----------------------------------------------------
    
   // Attributes ----------------------------------------------------

   /**
    *  Map of IDL operation names to operation analyses.
    */
   Map operationAnalysisMap;

   // Static --------------------------------------------------------

   private static final org.jboss.logging.Logger logger = 
               org.jboss.logging.Logger.getLogger(InterfaceAnalysis.class);

   private static WorkCacheManager cache
                               = new WorkCacheManager(InterfaceAnalysis.class);

   public static InterfaceAnalysis getInterfaceAnalysis(Class cls) 
      throws RMIIIOPViolationException
   {
      return (InterfaceAnalysis)cache.getAnalysis(cls);
   }

   // Constructors --------------------------------------------------

   protected InterfaceAnalysis(Class cls)
   {
      super(cls);
      logger.debug("new InterfaceAnalysis: " + cls.getName()); 
   }

   protected void doAnalyze()
      throws RMIIIOPViolationException
   {
      super.doAnalyze();

      calculateOperationAnalysisMap();
      fixupCaseNames();
   }

   // Public --------------------------------------------------------

   public boolean isAbstractInterface()
   {
      return abstractInterface;
   }

   public boolean isRmiIdlRemoteInterface()
   {
      return (!abstractInterface);
   }

   public String[] getAllTypeIds()
   {
      if (allTypeIds == null)
         logger.debug(cls + " null allTypeIds");
      return (String[])allTypeIds.clone();
   }

   // Protected -----------------------------------------------------
 
   /**
    *  Return a list of all the entries contained here.
    *
    *  @param entries The list of entries contained here. Entries in this list
    *                 are subclasses of <code>AbstractAnalysis</code>.
    */
   protected ArrayList getContainedEntries()
   {
      ArrayList ret = new ArrayList(constants.length +
                                    attributes.length +
                                    operations.length);

      for (int i = 0; i < constants.length; ++i)
         ret.add(constants[i]);
      for (int i = 0; i < attributes.length; ++i)
         ret.add(attributes[i]);
      for (int i = 0; i < operations.length; ++i)
         ret.add(operations[i]);

      return ret;
   }

   /**
    *  Analyse operations.
    *  This will fill in the <code>operations</code> array.
    */
   protected void analyzeOperations()
      throws RMIIIOPViolationException
   {
      logger.debug(cls + " analyzeOperations");

      if (!cls.isInterface())
         throw new IllegalArgumentException("Class \"" + cls.getName() +
                                            "\" is not an interface.");

      abstractInterface = RmiIdlUtil.isAbstractInterface(cls);
      calculateAllTypeIds();

      int operationCount = 0;
      for (int i = 0; i < methods.length; ++i)
         if ((m_flags[i] & (M_READ|M_WRITE|M_READONLY)) == 0)
            ++operationCount;
      operations = new OperationAnalysis[operationCount];
      operationCount = 0;
      for (int i = 0; i < methods.length; ++i) {
         if ((m_flags[i] & (M_READ|M_WRITE|M_READONLY)) == 0) {
            operations[operationCount] = new OperationAnalysis(methods[i]);
            ++operationCount;
         }
      }

      logger.debug(cls + " analyzeOperations operations=" + operations.length);
   }

   /**
    *  Calculate the map that maps IDL operation names to operation analyses.
    *  Besides mapped operations, this map also contains the attribute
    *  accessor and mutator operations.
    */
   protected void calculateOperationAnalysisMap()
   {
      operationAnalysisMap = new HashMap();
      OperationAnalysis oa;

      // Map the operations
      for (int i = 0; i < operations.length; ++i) {
         oa = operations[i];
         operationAnalysisMap.put(oa.getIDLName(), oa);
      }

      // Map the attributes
      for (int i = 0; i < attributes.length; ++i) {
         AttributeAnalysis attr = attributes[i];

         oa = attr.getAccessorAnalysis();

         // Not having an accessor analysis means that 
         // the attribute is not in a remote interface
         if (oa != null) {
            operationAnalysisMap.put(oa.getIDLName(), oa);

            oa = attr.getMutatorAnalysis();
            if (oa != null)
               operationAnalysisMap.put(oa.getIDLName(), oa);
         }
      }
   }

   /**
    *  Calculate the array containing all type ids of this interface,
    *  in the format that org.omg.CORBA.portable.Servant._all_interfaces()
    *  is expected to return.
    */
   protected void calculateAllTypeIds()
   {
      if (!isRmiIdlRemoteInterface()) {
         allTypeIds = new String[0];
      }
      else {
         ArrayList a = new ArrayList();
         InterfaceAnalysis[] intfs = getInterfaces();
         for (int i = 0; i < intfs.length; ++i) {
            String[] ss = intfs[i].getAllTypeIds();

            for (int j = 0; j < ss.length; ++j)
               if (!a.contains(ss[j]))
                  a.add(ss[j]);
         }
         allTypeIds = new String[a.size() + 1];
         allTypeIds[0] = getRepositoryId();
         for (int i = 1; i <= a.size(); ++i)
            allTypeIds[i] = (String)a.get(a.size()-i);
      }
   }

   // Private -------------------------------------------------------

   private boolean abstractInterface;

   private String[] allTypeIds;
}

