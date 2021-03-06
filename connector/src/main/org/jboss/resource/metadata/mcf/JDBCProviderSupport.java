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
package org.jboss.resource.metadata.mcf;

/**
 * A JDBCProviderSupport.
 * 
 * @author <a href="weston.price@jboss.org">Weston Price</a>
 * @version $Revision: 85945 $
 */
public interface JDBCProviderSupport
{
   public String getNewConnectionSQL();
   public void setNewConnectionSQL(String newConnectionSQL);
   
   public String getCheckValidConnectionSQL();
   public void setCheckValidConnectionSQL(String checkValidConnectionSQL);
   
   public String getValidConnectionCheckerClassName();   
   public void setValidConnectionCheckerClassName(String validConnectionCheckerClassName);
   
   public String getExceptionSorterClassName();
   public void setExceptionSorterClassName(String exceptionSorterClassName);
   
   public void setTrackStatements(String trackStatements);
   public String getTrackStatements();
   
   public void setPreparedStatementCacheSize(int cacheSize);
   public int getPreparedStatementCacheSize();
   
   public void setSharePreparedStatements(boolean sharePreparedStatements);
   public boolean isSharePreparedStatements();
   
   public void setUseQueryTimeout(boolean useQueryTimeout);
   public boolean isUseQueryTimeout();
   
   
}
