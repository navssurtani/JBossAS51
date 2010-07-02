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
package org.jboss.resource.adapter.jdbc.jdk6;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Properties;

import org.jboss.resource.adapter.jdbc.BaseWrapperManagedConnection;
import org.jboss.resource.adapter.jdbc.WrappedCallableStatement;
import org.jboss.resource.adapter.jdbc.WrappedConnection;
import org.jboss.resource.adapter.jdbc.WrappedPreparedStatement;
import org.jboss.resource.adapter.jdbc.WrappedStatement;

/**
 * WrappedConnectionJDK6.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 85945 $
 */
@SuppressWarnings("unchecked")
public class WrappedConnectionJDK6 extends WrappedConnection
{
   /**
    * Create a new WrappedConnectionJDK6.
    * 
    * @param mc the managed connection
    */
   public WrappedConnectionJDK6(BaseWrapperManagedConnection mc)
   {
      super(mc);
   }

   protected WrappedStatement wrapStatement(Statement statement)
   {
      return new WrappedStatementJDK6(this, statement);
   }

   protected WrappedPreparedStatement wrapPreparedStatement(PreparedStatement statement)
   {
      return new WrappedPreparedStatementJDK6(this, statement);
   }

   protected WrappedCallableStatement wrapCallableStatement(CallableStatement statement)
   {
      return new WrappedCallableStatementJDK6(this, statement);
   }

   public Array createArrayOf(String typeName, Object[] elements) throws SQLException
   {
      lock();
      try
      {
         Connection c = getUnderlyingConnection();
         try
         {
            return c.createArrayOf(typeName, elements);
         }
         catch (Throwable t)
         {
            throw checkException(t);
         }
      }
      finally
      {
         unlock();
      }
   }

   public Blob createBlob() throws SQLException
   {
      lock();
      try
      {
         Connection c = getUnderlyingConnection();
         try
         {
            return c.createBlob();
         }
         catch (Throwable t)
         {
            throw checkException(t);
         }
      }
      finally
      {
         unlock();
      }
   }

   public Clob createClob() throws SQLException
   {
      lock();
      try
      {
         Connection c = getUnderlyingConnection();
         try
         {
            return c.createClob();
         }
         catch (Throwable t)
         {
            throw checkException(t);
         }
      }
      finally
      {
         unlock();
      }
   }

   public NClob createNClob() throws SQLException
   {
      lock();
      try
      {
         Connection c = getUnderlyingConnection();
         try
         {
            return c.createNClob();
         }
         catch (Throwable t)
         {
            throw checkException(t);
         }
      }
      finally
      {
         unlock();
      }
   }

   public SQLXML createSQLXML() throws SQLException
   {
      lock();
      try
      {
         Connection c = getUnderlyingConnection();
         try
         {
            return c.createSQLXML();
         }
         catch (Throwable t)
         {
            throw checkException(t);
         }
      }
      finally
      {
         unlock();
      }
   }

   public Struct createStruct(String typeName, Object[] attributes) throws SQLException
   {
      lock();
      try
      {
         Connection c = getUnderlyingConnection();
         try
         {
            return c.createStruct(typeName, attributes);
         }
         catch (Throwable t)
         {
            throw checkException(t);
         }
      }
      finally
      {
         unlock();
      }
   }

   public Properties getClientInfo() throws SQLException
   {
      lock();
      try
      {
         Connection c = getUnderlyingConnection();
         try
         {
            return c.getClientInfo();
         }
         catch (Throwable t)
         {
            throw checkException(t);
         }
      }
      finally
      {
         unlock();
      }
   }

   public String getClientInfo(String name) throws SQLException
   {
      lock();
      try
      {
         Connection c = getUnderlyingConnection();
         try
         {
            return c.getClientInfo(name);
         }
         catch (Throwable t)
         {
            throw checkException(t);
         }
      }
      finally
      {
         unlock();
      }
   }

   public boolean isValid(int timeout) throws SQLException
   {
      lock();
      try
      {
         Connection c = getUnderlyingConnection();
         try
         {
            return c.isValid(timeout);
         }
         catch (Throwable t)
         {
            throw checkException(t);
         }
      }
      finally
      {
         unlock();
      }
   }

   public void setClientInfo(Properties properties) throws SQLClientInfoException
   {
      try
      {
         lock();
         try
         {
            Connection c = getUnderlyingConnection();
            try
            {
               c.setClientInfo(properties);
            }
            catch (Throwable t)
            {
               throw checkException(t);
            }
         }
         catch (SQLClientInfoException e)
         {
            throw e;
         }
         catch (SQLException e)
         {
            SQLClientInfoException t = new SQLClientInfoException();
            t.initCause(e);
            throw t;
         }
      }
      catch (SQLException e)
      {
         SQLClientInfoException t = new SQLClientInfoException();
         t.initCause(e);
         throw t;
      }
      finally
      {
         unlock();
      }
   }

   public void setClientInfo(String name, String value) throws SQLClientInfoException
   {
      try
      {
         lock();
         try
         {
            Connection c = getUnderlyingConnection();
            try
            {
               c.setClientInfo(name, value);
            }
            catch (Throwable t)
            {
               throw checkException(t);
            }
         }
         catch (SQLClientInfoException e)
         {
            throw e;
         }
         catch (SQLException e)
         {
            SQLClientInfoException t = new SQLClientInfoException();
            t.initCause(e);
            throw t;
         }
      }
      catch (SQLException e)
      {
         SQLClientInfoException t = new SQLClientInfoException();
         t.initCause(e);
         throw t;
      }
      finally
      {
         unlock();
      }
   }
}
