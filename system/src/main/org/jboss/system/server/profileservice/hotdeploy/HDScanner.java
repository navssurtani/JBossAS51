/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.system.server.profileservice.hotdeploy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.deployers.client.spi.main.MainDeployer;
import org.jboss.deployers.spi.DeploymentException;
import org.jboss.logging.Logger;
import org.jboss.profileservice.spi.ModificationInfo;
import org.jboss.profileservice.spi.MutableProfile;
import org.jboss.profileservice.spi.NoSuchProfileException;
import org.jboss.profileservice.spi.Profile;
import org.jboss.profileservice.spi.ProfileDeployment;
import org.jboss.profileservice.spi.ProfileKey;
import org.jboss.profileservice.spi.ProfileService;
import org.jboss.system.server.profileservice.repository.MainDeployerAdapter;

// ************************************************************************
// NOTE: Direct tests for this class are located in
// org.jboss.test.profileservice.test.HDScannerTestCase in the same package
// as other indirect tests of the scanner.
// ************************************************************************

/**
 * A DeploymentScanner built on the ProfileService and MainDeployer. This
 * is really just a simple ExecutorService Runnable that knows nothing
 * about how to detect changed deployers. The ProfileService determines
 * this.
 *
 * @author <a href="mailto:dimitris@jboss.org">Dimitris Andreadis</a>
 * @author Scott.Stark@jboss.org
 * @author adrian@jboss.org
 * @author <a href="mailto:emuckenh@redhat.com">Emanuel Muckenhuber</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 *
 * @version $Revision: 105743 $
 * @see MainDeployer
 * @see ProfileService
 */
public class HDScanner implements Runnable, Scanner
{
   private static final Logger log = Logger.getLogger(HDScanner.class);

   /**
    * The MainDeployer used to deploy modifications
    */
   private MainDeployerAdapter deployer;

   /**
    * The ProfileService used to determine modified deployments
    */
   private ProfileService profileService;

   /**
    * The ExecutorService/ThreadPool for performing scans
    */
   private ScheduledExecutorService scanExecutor;
   private ScheduledFuture activeScan;
   /** Did we create the ScheduledExecutorService */
   private boolean createdScanExecutor;

   /**
    * Thread name used when the ScheduledExecutorService is created internally
    */
   private String scanThreadName = "HDScanner";

   /**
    * Period in ms between deployment scans
    */
   private long scanPeriod = 5000;

   /**
    * The number of scans that have been done
    */
   private int scanCount;

   /**
    * The suspended flag
    */
   private boolean suspended;

   /**
    * Whether or not scanning has been enabled via the scanEnabled attribute
    * (default is <code>true</code>).
    */
   private boolean scanEnabled = true;

   public void setDeployer(MainDeployerAdapter deployer)
   {
      this.deployer = deployer;
   }

   public ProfileService getProfileService()
   {
      return profileService;
   }

   public void setProfileService(ProfileService profileService)
   {
      this.profileService = profileService;
   }

   /**
    * @return Returns the scanExecutor.
    */
   public ScheduledExecutorService getScanExecutor()
   {
      return this.scanExecutor;
   }

   /**
    * @param scanExecutor The scanExecutor to set.
    */
   public void setScanExecutor(ScheduledExecutorService scanExecutor)
   {
      this.scanExecutor = scanExecutor;
      createdScanExecutor = false;
   }

   public String getScanThreadName()
   {
      return scanThreadName;
   }

   public void setScanThreadName(String scanThreadName)
   {
      this.scanThreadName = scanThreadName;
   }

   /* (non-Javadoc)
    * @see org.jboss.deployment.scanner.VFSDeploymentScanner#getScanPeriod()
    */
   public long getScanPeriod()
   {
      return scanPeriod;
   }

   /* (non-Javadoc)
   * @see org.jboss.deployment.scanner.VFSDeploymentScanner#setScanPeriod(long)
   */
   public void setScanPeriod(long period)
   {
      this.scanPeriod = period;
   }

   /**
    * Is there a deployment scanner currently scheduled?  A scheduled scan is
    * not necessarily active.
    *
    * This method, while similar to {@link isScanEnabled}, may return a
    * different value.  Since the {@link start} and {@link stop} methods
    * may be called independently of {@link setScanEnabled}.
    *
    * @return <code>true</code> if there is a deployment scanner currently
    * scheduled; <code>false</code> otherwise.
    */
   public boolean isScanScheduled()
   {
      return activeScan != null;
   }

   /**
    * Are deployment scans enabled?
    *
    * This method, while similar to {@link isScanScheduled}, may return a
    * different value.  Since the {@link start} and {@link stop} methods
    * may be called independently of {@link setScanEnabled}.
    *
    * @return <code>true</code> if scans are enabled; <code>false</code>
    * otherwise.
    */
   public boolean isScanEnabled()
   {
      return scanEnabled;
   }

   public synchronized int getScanCount()
   {
      return scanCount;
   }

   public synchronized void resetScanCount()
   {
      this.scanCount = 0;
   }

   /**
    * Enable/disable deployment scans.
    *
    * @param scanEnabled true to enable scans, false to disable.
    */
   public synchronized void setScanEnabled(boolean enabled)
   {
      this.scanEnabled = enabled;

      if (enabled == true && activeScan == null && scanExecutor != null)
      {
         start();
      }
      else if (enabled == false && activeScan != null)
      {
         stop();
      }
   }

   public boolean isCreatedScanExecutor()
   {
      return createdScanExecutor;
   }

   public void create() throws Exception
   {
      // Default to a single thread executor
      if (scanExecutor == null)
      {
         scanExecutor = Executors.newSingleThreadScheduledExecutor(
               new ThreadFactory()
               {
                  public Thread newThread(Runnable r)
                  {
                     return new Thread(r, HDScanner.this.getScanThreadName());
                  }
               }
         );
         createdScanExecutor = true;
      }
   }

   public void start()
   {
      if (scanEnabled)
      {
         activeScan = scanExecutor.scheduleWithFixedDelay(this, 0, scanPeriod, TimeUnit.MILLISECONDS);
      }
   }

   public synchronized void stop()
   {
      if (activeScan != null)
      {
         activeScan.cancel(true);
         activeScan = null;
      }
   }
   public void destroy()
   {
      // Shutdown the scanExecutor
      if (scanExecutor != null && createdScanExecutor)
      {
         try
         {
            scanExecutor.shutdownNow();
         }
         catch(Exception e)
         {
            log.debug("Failed to cleanly shutdown scanExecutor", e);
         }
      }
   }

   /**
    * Executes scan
    */
   public void run()
   {
      try
      {
         scan();
      }
      catch (Throwable e)
      {
         log.warn("Scan failed", e);
      }
      finally
      {
         incScanCount();
      }
   }

   public synchronized void suspend()
   {
      suspended = (activeScan != null);
      if (suspended)
      {
         activeScan.cancel(false);
         try
         {
            activeScan.get();
         }
         catch (Exception ignored)
         {
         }
         activeScan = null;
      }
   }

   public synchronized void resume()
   {
      if (suspended)
      {
         start();
      }
      suspended = false;
   }

   public synchronized void scan() throws Exception
   {
      boolean trace = log.isTraceEnabled();

      // Query the ProfileService for deployments
      if (trace)
         log.trace("Begin deployment scan");

      // Get the active profiles
      Collection<ProfileKey> activeProfiles = profileService.getActiveProfileKeys();
      if (activeProfiles == null || activeProfiles.isEmpty())
      {
         if (trace)
            log.trace("End deployment scan, no active profiles");
         return;
      }

      // Get the modified deployments
      boolean modified = false;
      Collection<String> modifiedDeploymentNames = new ArrayList<String>();
      for (ProfileKey key : activeProfiles)
      {
         // The profile
         Profile profile;
         try
         {
            profile = profileService.getActiveProfile(key);
         }
         catch (NoSuchProfileException ignore)
         {
            if (trace)
               log.debug("failed to get profile for key: " + key);
            continue;
         }
         // Check if it's a mutable profile
         if (profile.isMutable() == false)
         {
            if (trace)
               log.trace("Ignoring not mutable profile: " + key);
            continue;
         }

         MutableProfile activeProfile = (MutableProfile) profile;
         Collection<ModificationInfo> modifiedDeployments = activeProfile.getModifiedDeployments();
         for (ModificationInfo info : modifiedDeployments)
         {
            ProfileDeployment ctx = info.getDeployment();
            try
            {
               switch (info.getStatus())
               {
                  case ADDED:
                  case MODIFIED:
                     deployer.addDeployment(ctx);
                     modifiedDeploymentNames.add(ctx.getName());
                     break;
                  case REMOVED:
                     deployer.removeDeployment(ctx.getName());
                     modified = true;
                     break;
               }
            }
            catch(DeploymentException e)
            {
               log.warn("Failed to add deployment: " + ctx.getName(), e);
            }
         }

         if (modifiedDeployments.size() > 0)
            modified = true;
      }

      try
      {
         // Process the changes
         if (modified)
         {
            deployer.process();

            // Only check the modified deployments to avoid duplicate errors
            for(String name : modifiedDeploymentNames)
            {
               // Can be nulled by a shutdown
               if (deployer != null)
                  deployer.checkComplete(name);
            }
         }
      }
      catch (Exception e)
      {
         log.warn("Failed to process changes", e);
         return;
      }

      if (trace)
         log.trace("End deployment scan");
   }

   /**
    * Inc the scanCount and to a notifyAll.
    */
   protected synchronized void incScanCount()
   {
      scanCount++;
      notifyAll();
   }
}
