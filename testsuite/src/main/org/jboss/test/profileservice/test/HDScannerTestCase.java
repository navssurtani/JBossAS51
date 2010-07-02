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
package org.jboss.test.profileservice.test;

import org.jboss.system.server.profileservice.hotdeploy.HDScanner;

import org.jboss.test.JBossTestCase;

/**
 * HDScanner test cases.
 *
 * @author <a href="mailto:jawilson@redhat.com">Jimmy Wilson</a>
 * @author <a href="mailto:miclark@redhat.com">Mike M. Clark</a>
 */
public class HDScannerTestCase extends JBossTestCase
{
   public HDScannerTestCase(String name)
   {
      super(name);
   }

   /**
    * Test for JBAS-7528.
    *
    * Setting the scanEnabled attribute to true via XML led to
    * NullPointerExceptions in previous releases, so no thrown exception equals
    * a pass.
    */
   public void testSettingScanEnabledToTrueDoesNotCauseNPE()
   {
      HDScanner hdScanner = new HDScanner();

      // Calling the setter *before* create/start have been called just like
      // when set via XML.
      hdScanner.setScanEnabled(true);
   }

   /**
    * Test for JBAS-7604.
    *
    * Setting the scanEnabled attribute to false via XML did not stop the start
    * method from executing/scheduling scanner in previous releases.
    */
    public void testSettingScanEnabledToFalseDoesNotCauseActiveScan()
    throws Exception
    {
      HDScanner hdScanner = new HDScanner();
      hdScanner.setScanEnabled(false);
      hdScanner.create();
      hdScanner.start();

      // Does starting the HDScanner cause a scan to be scheduled?  It shouldn't when
      // ScanEnabled is false.
      assertFalse("HDScanner had a scheduled scan when ScanEnabled was false",
                  hdScanner.isScanScheduled());
    }

    /**
     * Test for JBAS-8071.
     * 
     * Verify that setting <code>scanEnabled</code> attribute from
     * <code>false</code> to <code>true</code> works.
     */
    public void testSettingScanEnabledFromFalseToTrueWorks()
    throws Exception
    {
      HDScanner hdScanner = new HDScanner();
      hdScanner.setScanEnabled(false);
      hdScanner.create();
      hdScanner.start();
      assertFalse("HDScanner had a scheduled scan when ScanEnabled was false",
                  hdScanner.isScanScheduled());
      
      hdScanner.setScanEnabled(true);
      assertTrue("HDScanner did not have a scheduled scan when ScanEnabled was set to true",
                  hdScanner.isScanScheduled());
    }
}
