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
package org.jboss.ejb.plugins.cmp.jdbc;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Collections;
import javax.ejb.FinderException;

import org.jboss.deployment.DeploymentException;
import org.jboss.ejb.EntityEnterpriseContext;
import org.jboss.ejb.GenericEntityObjectFactory;
import org.jboss.ejb.EntityContainer;
import org.jboss.ejb.plugins.cmp.ejbql.Catalog;
import org.jboss.ejb.plugins.cmp.ejbql.SelectFunction;
import org.jboss.ejb.plugins.cmp.jdbc.bridge.JDBCEntityBridge;
import org.jboss.ejb.plugins.cmp.jdbc.bridge.JDBCCMPFieldBridge;
import org.jboss.ejb.plugins.cmp.jdbc.bridge.JDBCFieldBridge;
import org.jboss.ejb.plugins.cmp.jdbc.metadata.JDBCQueryMetaData;
import org.jboss.ejb.plugins.cmp.jdbc.metadata.JDBCDynamicQLQueryMetaData;
import org.jboss.ejb.plugins.cmp.jdbc.metadata.JDBCReadAheadMetaData;

/**
 * This class generates a query from JBoss-QL.
 *
 * @author <a href="mailto:dain@daingroup.com">Dain Sundstrom</a>
 * @author <a href="mailto:alex@jboss.org">Alex Loubyansky</a>
 * @version $Revision: 81030 $
 */
public final class JDBCDynamicQLQuery extends JDBCAbstractQueryCommand
{
   private final Catalog catalog;
   private final JDBCDynamicQLQueryMetaData metadata;

   public JDBCDynamicQLQuery(JDBCStoreManager manager, JDBCQueryMetaData q)
      throws DeploymentException
   {
      super(manager, q);
      catalog = manager.getCatalog();
      metadata = (JDBCDynamicQLQueryMetaData)q;
   }

   public Collection execute(Method finderMethod, Object[] args, EntityEnterpriseContext ctx, GenericEntityObjectFactory factory)
      throws FinderException
   {
      String dynamicQL = (String)args[0];
      if(getLog().isDebugEnabled())
      {
         getLog().debug("DYNAMIC-QL: " + dynamicQL);
      }

      QLCompiler compiler = null;
      try
      {
         compiler = JDBCQueryManager.getInstance(metadata.getQLCompilerClass(), catalog);
      }
      catch(DeploymentException e)
      {
         throw new FinderException(e.getMessage());
      }

      // get the parameters
      Object[] parameters = (Object[])args[1];
      // parameter types
      Class[] parameterTypes;
      if(parameters == null)
      {
         parameterTypes = new Class[0];
      }
      else
      {
         // get the parameter types
         parameterTypes = new Class[parameters.length];
         for(int i = 0; i < parameters.length; i++)
         {
            if(parameters[i] == null)
            {
               throw new FinderException("Parameter[" + i + "] is null");
            }
            parameterTypes[i] = parameters[i].getClass();
         }
      }

      // compile the dynamic-ql
      try
      {
         compiler.compileJBossQL(
            dynamicQL,
            finderMethod.getReturnType(),
            parameterTypes,
            metadata);
      }
      catch(Throwable t)
      {
         t.printStackTrace();
         throw new FinderException("Error compiling ejbql: " + t);
      }

      int offset = toInt(parameters, compiler.getOffsetParam(), compiler.getOffsetValue());
      int limit = toInt(parameters, compiler.getLimitParam(), compiler.getLimitValue());

      JDBCEntityBridge selectEntity = null;
      JDBCCMPFieldBridge selectField = null;
      SelectFunction selectFunction = null;
      if(compiler.isSelectEntity())
      {
         selectEntity = (JDBCEntityBridge) compiler.getSelectEntity();
      }
      else if(compiler.isSelectField())
      {
         selectField = (JDBCCMPFieldBridge) compiler.getSelectField();
      }
      else
      {
         selectFunction = compiler.getSelectFunction();
      }

      boolean[] mask;
      List leftJoinCMRList;
      JDBCReadAheadMetaData readahead = metadata.getReadAhead();
      if(selectEntity != null && readahead.isOnFind())
      {
         mask = selectEntity.getLoadGroupMask(readahead.getEagerLoadGroup());
         boolean modifiedMask = false;
         leftJoinCMRList = compiler.getLeftJoinCMRList();

         // exclude non-searchable columns if distinct is used
         if(compiler.isSelectDistinct())
         {
            JDBCFieldBridge[] tableFields = selectEntity.getTableFields();
            for(int i = 0; i < tableFields.length; ++i)
            {
               if(mask[i] && !tableFields[i].getJDBCType().isSearchable())
               {
                  if(!modifiedMask)
                  {
                     boolean[] original = mask;
                     mask = new boolean[original.length];
                     System.arraycopy(original, 0, mask, 0, mask.length);
                     modifiedMask = true;
                  }
                  mask[i] = false;
               }
            }
         }
      }
      else
      {
         mask = null;
         leftJoinCMRList = Collections.EMPTY_LIST;
      }

      // get the parameter order
      setParameterList(compiler.getInputParameters());

      EntityContainer con = ((JDBCStoreManager)compiler.getStoreManager()).getContainer();
      factory = metadata.isResultTypeMappingLocal() && con.getLocalHomeClass() != null ?
         con.getLocalProxyFactory() : con.getProxyFactory();
      
      return execute(
         compiler.getSQL(),
         parameters,
         offset,
         limit,
         selectEntity,
         selectField,
         selectFunction,
         (JDBCStoreManager) compiler.getStoreManager(),
         mask,
         compiler.getInputParameters(),
         leftJoinCMRList,
         metadata,
         factory,
         log
      );
   }
}
