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
package org.jboss.test.jacc.test;

import org.jboss.test.JBossTestCase;
import junit.framework.Test;

/**
 * Run the web security-constraint tests under JACC
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class WebConstraintsUnitTestCase
   extends org.jboss.test.security.test.WebConstraintsUnitTestCase
{
   public WebConstraintsUnitTestCase(String name)
   {
      super(name);
   }

   public void testGetAccess() throws Exception
   {
      setStrictStarRolesMode(true);
      super.testGetAccess();
   }

   public void testPostAccess() throws Exception
   {
      setStrictStarRolesMode(true);
      super.testPostAccess();
   }

   public static Test suite() throws Exception
   {
      return JBossTestCase.getDeploySetup(WebConstraintsUnitTestCase.class,
         org.jboss.test.security.test.WebConstraintsUnitTestCase.WAR);
   }
}
