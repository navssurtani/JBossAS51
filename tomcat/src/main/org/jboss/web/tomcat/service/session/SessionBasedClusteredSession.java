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
package org.jboss.web.tomcat.service.session;

import java.util.HashMap;
import java.util.Map;

import org.jboss.web.tomcat.service.session.distributedcache.spi.DistributableSessionMetadata;
import org.jboss.web.tomcat.service.session.distributedcache.spi.OutgoingSessionGranularitySessionData;


/**
 * Implementation of a ClusteredSession where the replication granularity level 
 * is session based; that is, we replicate the entire attribute map whenever a 
 * request makes any attribute dirty.
 * <p/>
 * Note that the isolation level of the cache dictates the
 * concurrency behavior.</p>
 *
 * @author Ben Wang
 * @author Brian Stansberry
 * 
 * @version $Revision: 92504 $
 */
class SessionBasedClusteredSession
   extends ClusteredSession<OutgoingSessionGranularitySessionData>
{
   static final long serialVersionUID = 3200976125245487256L;

   /**
    * Descriptive information describing this Session implementation.
    */
   protected static final String info = "SessionBasedClusteredSession/1.0";

   // ----------------------------------------------------------- Constructors

   
   public SessionBasedClusteredSession(ClusteredManager<OutgoingSessionGranularitySessionData> manager)
   {
      super(manager);
   }

   
   // ---------------------------------------------- Overridden Public Methods

   @Override
   public String getInfo()
   {
      return (info);
   }

   @Override
   protected OutgoingSessionGranularitySessionData getOutgoingSessionData()
   {
      boolean needFull = isFullReplicationNeeded();
      Map<String, Object> attrs = (needFull || isSessionAttributeMapDirty()) ? getSessionAttributeMap() : null;
      DistributableSessionMetadata metadata = (needFull || isSessionMetadataDirty()) ? getSessionMetadata() : null;
      Long timestamp = attrs != null || metadata != null || getMustReplicateTimestamp() ? Long.valueOf(getSessionTimestamp()) : null;
      return new OutgoingData(getRealId(), getVersion(), timestamp, metadata, attrs);
   }

   @Override
   protected Object removeAttributeInternal(String name, 
                                            boolean localCall, 
                                            boolean localOnly)
   {
      if (localCall)
         sessionAttributesDirty();
      return getAttributesInternal().remove(name);
   }

   @Override
   protected Object setAttributeInternal(String name, Object value)
   {
      sessionAttributesDirty();
      return getAttributesInternal().put(name, value);
   }

   
   // ----------------------------------------------------------------- Private

   private Map<String, Object> getSessionAttributeMap()
   {
      Map<String, Object> attrs = new HashMap<String, Object>(getAttributesInternal());
      removeExcludedAttributes(attrs);
      return attrs;
   }

   // ----------------------------------------------------------------- Classes

   private static class OutgoingData
         extends OutgoingDistributableSessionDataImpl
         implements OutgoingSessionGranularitySessionData
   {
      private final Map<String, Object> attributes;
      
      public OutgoingData(String realId, int version, 
            Long timestamp, DistributableSessionMetadata metadata,
            Map<String, Object> attributes)
      {
         super(realId, version, timestamp, metadata);
         this.attributes = attributes;
      }

      public Map<String, Object> getSessionAttributes()
      {
         return attributes;
      }
   }
}
