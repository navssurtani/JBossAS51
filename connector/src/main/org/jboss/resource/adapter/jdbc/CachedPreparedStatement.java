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
package org.jboss.resource.adapter.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Ref;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Array;
import java.sql.ResultSetMetaData;
import java.sql.ParameterMetaData;
import java.sql.SQLWarning;
import java.sql.Connection;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper class for cached PreparedStatements.  Keeps a refcount.  When this refcount reaches 0,
 * it will close ps.
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @author <a href="mailto:adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 84265 $
 */
public abstract class CachedPreparedStatement extends JBossWrapper implements PreparedStatement
{
   private static final long serialVersionUID = 2085461257386274373L;
   
   private PreparedStatement ps;
   private AtomicBoolean cached = new AtomicBoolean(true);
   private AtomicInteger inUse = new AtomicInteger(1);

   private int defaultMaxFieldSize;
   private int defaultMaxRows;
   private int defaultQueryTimeout;
   private int defaultFetchDirection;
   private int defaultFetchSize;
   private int currentMaxFieldSize;
   private int currentMaxRows;
   private int currentQueryTimeout;
   private int currentFetchDirection;
   private int currentFetchSize;

   public CachedPreparedStatement(PreparedStatement ps) throws SQLException
   {
      this.ps = ps;

      // Remember the defaults
      defaultMaxFieldSize = ps.getMaxFieldSize();
      defaultMaxRows = ps.getMaxRows();
      defaultQueryTimeout = ps.getQueryTimeout();
      defaultFetchDirection = ps.getFetchDirection();
      defaultFetchSize = ps.getFetchSize();
      currentMaxFieldSize = defaultMaxFieldSize;
      currentMaxRows = defaultMaxRows;
      currentQueryTimeout = defaultQueryTimeout;
      currentFetchDirection = defaultFetchDirection;
      currentFetchSize = defaultFetchSize;
   }

   public PreparedStatement getUnderlyingPreparedStatement()
   {
      return ps;
   }

   public ResultSet executeQuery() throws SQLException
   {
      return ps.executeQuery();
   }

   public int executeUpdate() throws SQLException
   {
      return ps.executeUpdate();
   }

   public void setNull(int parameterIndex, int sqlType) throws SQLException
   {
      ps.setNull(parameterIndex, sqlType);
   }

   public void setBoolean(int parameterIndex, boolean x) throws SQLException
   {
      ps.setBoolean(parameterIndex, x);
   }

   public void setByte(int parameterIndex, byte x) throws SQLException
   {
      ps.setByte(parameterIndex, x);
   }

   public void setShort(int parameterIndex, short x) throws SQLException
   {
      ps.setShort(parameterIndex, x);
   }

   public void setInt(int parameterIndex, int x) throws SQLException
   {
      ps.setInt(parameterIndex, x);
   }

   public void setLong(int parameterIndex, long x) throws SQLException
   {
      ps.setLong(parameterIndex, x);
   }

   public void setFloat(int parameterIndex, float x) throws SQLException
   {
      ps.setFloat(parameterIndex, x);
   }

   public void setDouble(int parameterIndex, double x) throws SQLException
   {
      ps.setDouble(parameterIndex, x);
   }

   public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException
   {
      ps.setBigDecimal(parameterIndex, x);
   }

   public void setString(int parameterIndex, String x) throws SQLException
   {
      ps.setString(parameterIndex, x);
   }

   public void setBytes(int parameterIndex, byte x[]) throws SQLException
   {
      ps.setBytes(parameterIndex, x);
   }

   public void setDate(int parameterIndex, java.sql.Date x)
           throws SQLException
   {
      ps.setDate(parameterIndex, x);
   }

   public void setTime(int parameterIndex, java.sql.Time x)
           throws SQLException
   {
      ps.setTime(parameterIndex, x);
   }

   public void setTimestamp(int parameterIndex, java.sql.Timestamp x)
           throws SQLException
   {
      ps.setTimestamp(parameterIndex, x);
   }

   public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length)
           throws SQLException
   {
      ps.setAsciiStream(parameterIndex, x, length);
   }

   @Deprecated
   public void setUnicodeStream(int parameterIndex, java.io.InputStream x,
                                int length) throws SQLException
   {
      ps.setUnicodeStream(parameterIndex, x, length);
   }

   public void setBinaryStream(int parameterIndex, java.io.InputStream x,
                               int length) throws SQLException
   {
      ps.setBinaryStream(parameterIndex, x, length);
   }

   public void clearParameters() throws SQLException
   {
      ps.clearParameters();
   }

   public void setObject(int parameterIndex, Object x, int targetSqlType, int scale)
           throws SQLException
   {
      ps.setObject(parameterIndex, x, targetSqlType, scale);
   }

   public void setObject(int parameterIndex, Object x, int targetSqlType)
           throws SQLException
   {
      ps.setObject(parameterIndex, x, targetSqlType);
   }

   public void setObject(int parameterIndex, Object x) throws SQLException
   {
      ps.setObject(parameterIndex, x);
   }

   public boolean execute() throws SQLException
   {
      return ps.execute();
   }

   public void addBatch() throws SQLException
   {
      ps.addBatch();
   }

   public void setCharacterStream(int parameterIndex,
                                  java.io.Reader reader,
                                  int length) throws SQLException
   {
      ps.setCharacterStream(parameterIndex, reader, length);
   }

   public void setRef(int i, Ref x) throws SQLException
   {
      ps.setRef(i, x);
   }

   public void setBlob(int i, Blob x) throws SQLException
   {
      ps.setBlob(i, x);
   }

   public void setClob(int i, Clob x) throws SQLException
   {
      ps.setClob(i, x);
   }

   public void setArray(int i, Array x) throws SQLException
   {
      ps.setArray(i, x);
   }

   public ResultSetMetaData getMetaData() throws SQLException
   {
      return ps.getMetaData();
   }

   public void setDate(int parameterIndex, java.sql.Date x, Calendar cal)
           throws SQLException
   {
      ps.setDate(parameterIndex, x, cal);
   }

   public void setTime(int parameterIndex, java.sql.Time x, Calendar cal)
           throws SQLException
   {
      ps.setTime(parameterIndex, x, cal);
   }

   public void setTimestamp(int parameterIndex, java.sql.Timestamp x, Calendar cal)
           throws SQLException
   {
      ps.setTimestamp(parameterIndex, x, cal);
   }

   public void setNull(int paramIndex, int sqlType, String typeName)
           throws SQLException
   {
      ps.setNull(paramIndex, sqlType, typeName);
   }

   public void setURL(int parameterIndex, java.net.URL x) throws SQLException
   {
      ps.setURL(parameterIndex, x);
   }

   public ParameterMetaData getParameterMetaData() throws SQLException
   {
      return ps.getParameterMetaData();
   }

   public ResultSet executeQuery(String sql) throws SQLException
   {
      return ps.executeQuery(sql);
   }

   public int executeUpdate(String sql) throws SQLException
   {
      return ps.executeUpdate(sql);
   }

   public boolean isInUse()
   {
      return inUse.get() > 0;
   }
   
   public void inUse()
   {
      inUse.incrementAndGet();
   }

   public void agedOut() throws SQLException
   {
      cached.set(false);
      if (inUse.get() == 0)
         ps.close();
   }

   public void close() throws SQLException
   {
      if (inUse.decrementAndGet() == 0)
      {
         if (cached.get() == false)
            ps.close();
         else
         {
            // Reset the defaults
            if (defaultMaxFieldSize != currentMaxFieldSize)
            {
               ps.setMaxFieldSize(defaultMaxFieldSize);
               currentMaxFieldSize = defaultMaxFieldSize;
            }
            if (defaultMaxRows != currentMaxRows)
            {
               ps.setMaxRows(defaultMaxRows);
               currentMaxRows = defaultMaxRows;
            }
            if (defaultQueryTimeout != currentQueryTimeout)
            {
               ps.setQueryTimeout(defaultQueryTimeout);
               currentQueryTimeout = defaultQueryTimeout;
            }
            if (defaultFetchDirection != currentFetchDirection)
            {
               ps.setFetchDirection(defaultFetchDirection);
               currentFetchDirection = defaultFetchDirection;
            }
            if (defaultFetchSize != currentFetchSize)
            {
               ps.setFetchSize(defaultFetchSize);
               currentFetchSize = defaultFetchSize;
            }
         }
      }
   }

   public int getMaxFieldSize() throws SQLException
   {
      return ps.getMaxFieldSize();
   }

   public void setMaxFieldSize(int max) throws SQLException
   {
      ps.setMaxFieldSize(max);
      currentMaxFieldSize = max;
   }

   public int getMaxRows() throws SQLException
   {
      return ps.getMaxRows();
   }

   public void setMaxRows(int max) throws SQLException
   {
      ps.setMaxRows(max);
      currentMaxRows = max;
   }

   public void setEscapeProcessing(boolean enable) throws SQLException
   {
      ps.setEscapeProcessing(enable);
   }

   public int getQueryTimeout() throws SQLException
   {
      return ps.getQueryTimeout();
   }

   public void setQueryTimeout(int seconds) throws SQLException
   {
      ps.setQueryTimeout(seconds);
      currentQueryTimeout = seconds;
   }

   public void cancel() throws SQLException
   {
      ps.cancel();
   }

   public SQLWarning getWarnings() throws SQLException
   {
      return ps.getWarnings();
   }

   public void clearWarnings() throws SQLException
   {
      ps.clearWarnings();
   }

   public void setCursorName(String name) throws SQLException
   {
      ps.setCursorName(name);
   }

   public boolean execute(String sql) throws SQLException
   {
      return ps.execute(sql);
   }

   public ResultSet getResultSet() throws SQLException
   {
      return ps.getResultSet();
   }

   public int getUpdateCount() throws SQLException
   {
      return ps.getUpdateCount();
   }

   public boolean getMoreResults() throws SQLException
   {
      return ps.getMoreResults();
   }

   public void setFetchDirection(int direction) throws SQLException
   {
      ps.setFetchDirection(direction);
      currentFetchDirection = direction;
   }

   public int getFetchDirection() throws SQLException
   {
      return ps.getFetchDirection();
   }

   public void setFetchSize(int rows) throws SQLException
   {
      ps.setFetchSize(rows);
      currentFetchSize = rows;
   }

   public int getFetchSize() throws SQLException
   {
      return ps.getFetchSize();
   }

   public int getResultSetConcurrency() throws SQLException
   {
      return ps.getResultSetConcurrency();
   }

   public int getResultSetType() throws SQLException
   {
      return ps.getResultSetType();
   }

   public void addBatch(String sql) throws SQLException
   {
      ps.addBatch(sql);
   }

   public void clearBatch() throws SQLException
   {
      ps.clearBatch();
   }

   public int[] executeBatch() throws SQLException
   {
      return ps.executeBatch();
   }

   public Connection getConnection() throws SQLException
   {
      return ps.getConnection();
   }

   public boolean getMoreResults(int current) throws SQLException
   {
      return ps.getMoreResults(current);
   }

   public ResultSet getGeneratedKeys() throws SQLException
   {
      return ps.getGeneratedKeys();
   }

   public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException
   {
      return ps.executeUpdate(sql, autoGeneratedKeys);
   }

   public int executeUpdate(String sql, int columnIndexes[]) throws SQLException
   {
      return ps.executeUpdate(sql, columnIndexes);
   }

   public int executeUpdate(String sql, String columnNames[]) throws SQLException
   {
      return ps.executeUpdate(sql, columnNames);
   }

   public boolean execute(String sql, int autoGeneratedKeys) throws SQLException
   {
      return ps.execute(sql, autoGeneratedKeys);
   }

   public boolean execute(String sql, int columnIndexes[]) throws SQLException
   {
      return ps.execute(sql, columnIndexes);
   }

   public boolean execute(String sql, String columnNames[]) throws SQLException
   {
      return ps.execute(sql, columnNames);
   }

   public int getResultSetHoldability() throws SQLException
   {
      return ps.getResultSetHoldability();
   }

   protected PreparedStatement getWrappedObject() throws SQLException
   {
      return ps;
   }
}
