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
package org.jboss.test.jbossmessaging.clustertest.killservice;

import java.lang.reflect.Method;

import org.jboss.system.ServiceMBeanSupport;


public class KillService extends ServiceMBeanSupport implements KillServiceMBean
{

    public void kill(final int miliseconds)
    {
        new Thread()
        {
            public void run()
            {
                try
                {
                    System.out.println("Server will be killed in " + miliseconds + " miliseconds");
                    Thread.sleep(miliseconds);
                    try
                    {
                        /** We need a real halt for failover, as System.exit would cause a regular shutdown process */
                        Class clazz = this.getClass().getClassLoader().loadClass("java.lang.Shutdown");
                        Method method = clazz.getDeclaredMethod("halt",  new Class[]{Integer.TYPE});
                        method.setAccessible(true);
                        System.out.println("Halt forced!");
                        System.out.flush();
                        method.invoke(null, new Object[]{new Integer(-1)});
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }
                catch (Exception ignored)
                {
                    // nothing to do if it's interrupted
                }
                
                
            }
        }.start();
        
    }

}
