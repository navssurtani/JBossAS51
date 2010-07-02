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
package org.jboss.resource.connectionmanager.xa;

import java.io.Serializable;
import java.util.Arrays;

import javax.transaction.xa.Xid;

/**
 * A XidWrapper.
 * 
 * @author <a href="weston.price@jboss.com">Weston Price</a>
 * @version $Revision: 85945 $
 */
public class XidWrapperImpl implements XidWrapper
{
   /** The serialVersionUID */
   private static final long serialVersionUID = 8226195409384804425L;

   /** The formatId */
   private int formatId;
   
   /** The globalTransactionId */
   private byte[] globalTransactionId;
   
   /** The branchQualifier */
   private byte[] branchQualifier;
   
   /** Cached toString() */
   private transient String cachedToString;

   /** Cached hashCode() */
   private transient int cachedHashCode;

   /** Whether or not to pad the id */
   private boolean pad;
   
   public XidWrapperImpl(Xid xid)
   {
      this(false, xid);
   }
   
   public XidWrapperImpl(boolean pad, Xid xid)
   {
      this.pad = pad;
      
      branchQualifier = (pad) ? new byte[Xid.MAXBQUALSIZE] : new byte[xid.getBranchQualifier().length];      
      System.arraycopy(xid.getBranchQualifier(), 0, branchQualifier, 0, xid.getBranchQualifier().length);      
      this.globalTransactionId = xid.getGlobalTransactionId();
      this.formatId = xid.getFormatId();
   }

   public byte[] getBranchQualifier()
   {
      return this.branchQualifier;
   }

   public int getFormatId()
   {
      return this.formatId;
   }

   public byte[] getGlobalTransactionId()
   {
      return this.globalTransactionId;
   }
   
   public boolean equals(Object object)
   {
      if (object == null || (object instanceof Xid) == false)
         return false;

      Xid other = (Xid) object;
      return
      (
         formatId == other.getFormatId() && 
         Arrays.equals(globalTransactionId, other.getGlobalTransactionId()) &&
         Arrays.equals(branchQualifier, other.getBranchQualifier())
      );
   }

   public int hashCode()
   {
      if (cachedHashCode == 0)
      {
         cachedHashCode = formatId;
         for (int i = 0; i < globalTransactionId.length; ++i)
            cachedHashCode += globalTransactionId[i];
      }
      return cachedHashCode;
   }

   public String toString()
   {
      if (cachedToString == null)
      {
         StringBuffer buffer = new StringBuffer();
         buffer.append("XidWrapper[FormatId=").append(getFormatId());
         buffer.append(" GlobalId=").append(new String(getGlobalTransactionId()).trim());
         byte[] branchQualifer = getBranchQualifier();
         buffer.append(" BranchQual=");
         if (branchQualifer == null)
            buffer.append("null");
         else
            buffer.append(new String(getBranchQualifier()).trim());
         buffer.append(']');
         cachedToString = buffer.toString();
      }
      return cachedToString;
   }
}
