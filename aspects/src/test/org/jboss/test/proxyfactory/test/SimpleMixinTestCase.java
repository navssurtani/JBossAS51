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
package org.jboss.test.proxyfactory.test;


import junit.framework.Test;

import org.jboss.aop.proxy.container.AOPProxyFactoryMixin;
import org.jboss.test.proxyfactory.AbstractProxyTest;
import org.jboss.test.proxyfactory.support.PlainBean;
import org.jboss.test.proxyfactory.support.Simple;
import org.jboss.test.proxyfactory.support.SimpleMixin;
import org.jboss.test.proxyfactory.support.SimpleMixinWithConstructorAndDelegate;

/**
 * DataSourceTestCase.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 80997 $
 */
public class SimpleMixinTestCase extends AbstractProxyTest
{
   public void testSimpleMixinDefaultConstructor() throws Exception
   {
      SimpleMixin.invoked = false;

      PlainBean bean = new PlainBean();
      AOPProxyFactoryMixin[] mixins = {new AOPProxyFactoryMixin(SimpleMixin.class, new Class[]{Simple.class})};
      Object proxy = assertCreateProxy(bean, mixins, Simple.class);

      Simple simple = (Simple)proxy;
      simple.doSomething();
      assertTrue(SimpleMixin.invoked);
   }
   
   public void testSimpleMixinConstructorProxyTarget() throws Exception
   {
      SimpleMixinWithConstructorAndDelegate.invoked = false;
      SimpleMixinWithConstructorAndDelegate.proxy = null;
      SimpleMixinWithConstructorAndDelegate.delegate = null;

      PlainBean bean = new PlainBean();
      AOPProxyFactoryMixin[] mixins = {new AOPProxyFactoryMixin(SimpleMixinWithConstructorAndDelegate.class, new Class[]{Simple.class}, "this")};
      Object proxy = assertCreateProxy(bean, mixins, Simple.class);

      Simple simple = (Simple)proxy;
      simple.doSomething();
      assertTrue(SimpleMixinWithConstructorAndDelegate.invoked);
      assertNotNull(SimpleMixinWithConstructorAndDelegate.proxy);
      assertEquals(proxy, SimpleMixinWithConstructorAndDelegate.proxy);
      assertNotNull(SimpleMixinWithConstructorAndDelegate.delegate);
      assertEquals(bean, SimpleMixinWithConstructorAndDelegate.delegate);
   }
   
   
   public static Test suite()
   {
      return suite(SimpleMixinTestCase.class);
   }

   public SimpleMixinTestCase(String name)
   {
      super(name);
   }
}
