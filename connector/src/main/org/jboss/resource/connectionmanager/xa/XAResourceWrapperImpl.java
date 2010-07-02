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

import org.jboss.tm.XAResourceWrapper;

import java.io.Serializable;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.jboss.logging.Logger;

/**
 * A XAResourceWrapper.
 * 
 * @author <a href="weston.price@jboss.com">Weston Price</a>
 * @author <a href="jesper.pedersen@jboss.org">Jesper Pedersen</a>
 * @version $Revision: 85945 $
 */
public class XAResourceWrapperImpl implements XAResourceWrapper
{
   /** The serialVersionUID */
   //private static final long serialVersionUID = 4551722165222496828L;

   private static final Logger log = Logger.getLogger(XAResourceWrapperImpl.class);
   
   /** The xaResource */
   private XAResource xaResource;

   private boolean pad;

   private Boolean overrideRmValue;

   private String productName;

   private String productVersion;
   
   public XAResourceWrapperImpl(XAResource resource)
   {
      this(resource, false, Boolean.FALSE, null, null);
   }

   public XAResourceWrapperImpl(XAResource resource, boolean pad)
   {
      this(resource, pad, Boolean.FALSE, null, null);
   }

   public XAResourceWrapperImpl(XAResource resource, boolean pad, Boolean override)
   {
      this(resource, pad, override, null, null);
   }

   public XAResourceWrapperImpl(XAResource resource, boolean pad, Boolean override, String productName, String productVersion)
   {
      this.overrideRmValue = override;
      this.pad = pad;
      this.xaResource = resource;
      this.productName = productName;
      this.productVersion = productVersion;
   }

   public void commit(Xid xid, boolean onePhase) throws XAException
   {
      xid = convertXid(xid);
      xaResource.commit(xid, onePhase);
   }

   public void end(Xid xid, int flags) throws XAException
   {
      xid = convertXid(xid);
      xaResource.end(xid, flags);
   }

   public void forget(Xid xid) throws XAException
   {
      xid = convertXid(xid);
      xaResource.forget(xid);
   }

   public int getTransactionTimeout() throws XAException
   {
      return xaResource.getTransactionTimeout();
   }

   public boolean isSameRM(XAResource resource) throws XAException
   {
      if (overrideRmValue != null)
      {
         if(log.isTraceEnabled())
         {
            log.trace("Executing isSameRM with override value" + overrideRmValue + " for XAResourceWrapper" + this);
         }
         return overrideRmValue.booleanValue();
      }
      else
      {
         if(resource instanceof XAResourceWrapper)
         {
            XAResourceWrapper other = (XAResourceWrapper)resource;
            return xaResource.isSameRM(other.getResource());
         }
         else
         {
            return xaResource.isSameRM(resource);
         }
         
      }
   }

   public int prepare(Xid xid) throws XAException
   {
      xid = convertXid(xid);
      return xaResource.prepare(xid);
   }

   public Xid[] recover(int flag) throws XAException
   {
      return xaResource.recover(flag);
   }

   public void rollback(Xid xid) throws XAException
   {
      xid = convertXid(xid);      
      xaResource.rollback(xid);
   }

   public boolean setTransactionTimeout(int flag) throws XAException
   {
      return xaResource.setTransactionTimeout(flag);
   }

   public void start(Xid xid, int flags) throws XAException
   {
      xid = convertXid(xid);
      xaResource.start(xid, flags);
   }

   /**
    * Get the XAResource that is being wrapped
    * @return The XAResource
    */
   public XAResource getResource()
   {
      return xaResource;
   }

   /**
    * Get product name
    * @return Product name of the instance if defined; otherwise <code>null</code>
    */
   public String getProductName()
   {
      return productName;
   }

   /**
    * Get product version
    * @return Product version of the instance if defined; otherwise <code>null</code>
    */
   public String getProductVersion()
   {
      return productVersion;
   }

   private Xid convertXid(Xid xid)
   {
      if (xid instanceof XidWrapper)
         return xid;
      else
         return new XidWrapperImpl(pad, xid);
   }

   public String toString()
   {
      return super.toString();
   }
}
