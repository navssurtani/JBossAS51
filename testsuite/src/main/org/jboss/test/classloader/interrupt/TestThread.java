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
package org.jboss.test.classloader.interrupt;

import org.jboss.logging.Logger;

/** A thread subclass that loads a class while its interrupted flag is set.
 *
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class TestThread extends Thread
{
   private static Logger log = Logger.getLogger(TestThread.class);
   private Object listener;
   Throwable ex;

   /** Creates a new instance of TestThread */
   public TestThread(Object listener)
   {
      super("org.jboss.test.classloader.interrupt.TestThread");
      this.listener = listener;
   }

	public void run()
   {
      // Set our interrupted flag
      log.debug("Setting interrupt flag");
      this.interrupt();
      try
      {
         // An explict reference to TestClass will invoke loadClassInternal
         log.debug("Creating TestClass");
         this.interrupt();
         boolean wasInterrupted = this.isInterrupted();
         TestClass tc = new TestClass();
         log.debug("TestClass instance = "+tc);
         if( wasInterrupted == false )
            ex = new IllegalStateException("Interrupted state not restore after loadClassInternal");
      }
      catch(Throwable e)
      {
         this.ex = e;
         log.error("Failure creating TestClass", e);
      }
	}
}
