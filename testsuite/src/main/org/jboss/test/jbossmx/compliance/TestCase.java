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
package org.jboss.test.jbossmx.compliance;

import org.jboss.test.JBossTestCase;

public class TestCase
   extends JBossTestCase
{

   /**
    * The period for a timer notification. This needs to be small so the tests
    * don't take too long.
    * The wait needs to be long enough to be sure the monitor has enough time
    * to send the notification and switch the context to the handler.
    */
   public static final long PERIOD = 100;
   public static final long WAIT = PERIOD * 2;

   /**
    * The number of repeats for occurances tests
    */
   public static final long REPEATS = 2;

   /**
    * The name of the MBeanServerDelegate from the spec
    */
   public static final String MBEAN_SERVER_DELEGATE = "JMImplementation:type=MBeanServerDelegate";

   public TestCase(String s)
   {
      super(s);
   }

   /**
    * We do not need the JBoss Server for compliance tests
    */
   public void testServerFound()
   {
   }
}
