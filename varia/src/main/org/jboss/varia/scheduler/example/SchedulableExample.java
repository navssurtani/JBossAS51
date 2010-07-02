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
package org.jboss.varia.scheduler.example;

import java.util.Date;

import org.jboss.logging.Logger;

import org.jboss.varia.scheduler.Schedulable;

/**
 * A simple Schedulable implementaion that logs when an event occurs.
 *
 * @author <a href="mailto:andreas@jboss.org">Andreas Schaefer</a>
 * @author Cameron (camtabor)
 * @version $Revision: 81038 $
 */
public class SchedulableExample
   implements Schedulable
{
   /** Class logger. */
   private static final Logger log = Logger.getLogger(SchedulableExample.class);
      
   private String mName;
   private int mValue;
   
   public SchedulableExample(String pName, int pValue)
   {
      mName = pName;
      mValue = pValue;
   }

   /**
    * Just log the call
    **/
   public void perform(Date pTimeOfCall, long pRemainingRepetitions)
   {
      log.info("Schedulable Examples is called at: " + pTimeOfCall +
               ", remaining repetitions: " + pRemainingRepetitions +
               ", test, name: " + mName + ", value: " + mValue);
   }
}
