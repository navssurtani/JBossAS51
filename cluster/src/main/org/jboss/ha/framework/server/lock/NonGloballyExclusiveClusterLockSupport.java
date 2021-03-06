/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.ha.framework.server.lock;

import java.io.Serializable;

import org.jboss.ha.framework.interfaces.ClusterNode;
import org.jboss.ha.framework.interfaces.HAPartition;

/**
 * Support class for cluster locking scenarios where threads can hold a
 * local lock on a category but not a cluster-wide lock. Multiple nodes can
 * simultaneously hold a local lock on a category, but none can hold a local
 * lock on a category if the cluster-wide lock is held. Cluster-wide lock cannot
 * be acquired while any node holds a local lock.
 * <p>
 * <strong>NOTE:</strong> This class does not support "upgrades", i.e. scenarios 
 * where a thread acquires the local lock and then while holding the local
 * lock attempts to acquire the cluster-wide lock.
 * </p>
 * 
 * @author Brian Stansberry
 */
public class NonGloballyExclusiveClusterLockSupport extends AbstractClusterLockSupport
{
   
   // ------------------------------------------------------------- Constructor
   
   public NonGloballyExclusiveClusterLockSupport(String serviceHAName, 
                                 HAPartition partition, 
                                 LocalLockHandler handler)
   {
      super(serviceHAName, partition, handler);
   }
   
   // ------------------------------------------------------------------ Public
   
   public void unlock(Serializable lockId)
   {
      ClusterNode myself = getLocalClusterNode();
      if (myself == null)
      {
         throw new IllegalStateException("Must call start() before first call to unlock()");
      }
      
      ClusterLockState lockState = getClusterLockState(lockId, false);
      
      if (lockState != null && myself.equals(lockState.getHolder()))
      {
         getLocalHandler().unlockFromCluster(lockId, myself);
         lockState.release();
         
         try
         {
            getPartition().callMethodOnCluster(getServiceHAName(), "releaseRemoteLock", 
                  new Object[]{lockId, myself}, 
                  RELEASE_REMOTE_LOCK_TYPES, true);
         }
         catch (RuntimeException e)
         {
            throw e;
         }
         catch (Exception e)
         {
            throw new RuntimeException("Failed releasing remote lock", e);
         }
      }
   }
   
   // --------------------------------------------------------------- Protected
   
   @Override
   protected ClusterLockState getClusterLockState(Serializable categoryName)
   {
      return getClusterLockState(categoryName, true);
   }
   
   @Override
   protected RemoteLockResponse yieldLock(ClusterLockState lockState, ClusterNode caller, long timeout)
   {
      return new RemoteLockResponse(getLocalClusterNode(), RemoteLockResponse.Flag.REJECT, lockState.getHolder());
   }

   @Override
   protected RemoteLockResponse handleLockSuccess(ClusterLockState lockState, ClusterNode caller)
   {
      recordLockHolder(lockState, caller);
      return new RemoteLockResponse(getLocalClusterNode(), RemoteLockResponse.Flag.OK);
   }
   
   // ----------------------------------------------------------------- Private
}
