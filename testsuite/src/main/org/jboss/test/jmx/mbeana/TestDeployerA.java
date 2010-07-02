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
package org.jboss.test.jmx.mbeana;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;

import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import javax.management.loading.MLet;


import org.jboss.system.Service;
import org.jboss.system.ServiceMBeanSupport;

/**
 *  This is a do-nothing mbean to test service archive deployment.
 *
 * @author     <a href="mailto:d_jencks@users.sourceforge.net">David Jencks</a>
 * @version    $Revision: 81036 $ <p>
 *
 *      <b>20010901 david jencks</b>
 *      <ul>initial import
 *        <li>
 *      </ul>
 *
 */
public class TestDeployerA
       extends ServiceMBeanSupport
       implements TestDeployerAMBean
{
   public String getName()
   {
      return "TestDeployerA";
   }

}
