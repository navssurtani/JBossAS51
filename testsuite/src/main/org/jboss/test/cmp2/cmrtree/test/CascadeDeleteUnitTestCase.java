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
package org.jboss.test.cmp2.cmrtree.test;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.jboss.test.JBossTestCase;
import org.jboss.test.util.ejb.EJBTestCase;
import org.jboss.test.cmp2.cmrtree.ejb.Facade;
import org.jboss.test.cmp2.cmrtree.ejb.FacadeUtil;

/**
 * @author <a href="mailto:alex@jboss.org">Alexey Loubyansky</a>
 * @version <tt>$Revision: 81036 $</tt>
 */
public class CascadeDeleteUnitTestCase extends EJBTestCase
{
   public static Test suite() throws Exception
   {
      // JBAS-3496, the execution order of tests in this test case is important
      // so it must be defined explicitly when running under some JVMs
      TestSuite suite = new TestSuite();
      suite.addTest(new CascadeDeleteUnitTestCase("testCascadeDelete"));
      suite.addTest(new CascadeDeleteUnitTestCase("testUpdateCMPFieldToNullOnRelatedInstance"));
      
      return JBossTestCase.getDeploySetup(suite, "cmp2-cmrtree.jar");      
   }

   public CascadeDeleteUnitTestCase(String s)
   {
      super(s);
   }

   public void testCascadeDelete() throws Exception
   {
      final Facade facade = FacadeUtil.getHome().create();
      facade.setup();
      facade.test(0);
   }

   public void testUpdateCMPFieldToNullOnRelatedInstance() throws Exception
   {
      final Facade facade = FacadeUtil.getHome().create();
      facade.setup2();
      assertEquals("some name", facade.getBName());
      facade.setBNameToNull();
      assertNull(facade.getBName());
   }
}
