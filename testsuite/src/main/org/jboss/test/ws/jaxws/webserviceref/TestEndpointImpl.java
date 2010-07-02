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
package org.jboss.test.ws.jaxws.webserviceref;

import javax.jws.WebService;

import org.jboss.logging.Logger;

/**
 * Test the JAXWS annotation: javax.jws.WebServiceRef
 *
 * @author Thomas.Diesler@jboss.org
 * @since 23-Oct-2006
 */
@WebService(endpointInterface="org.jboss.test.ws.jaxws.webserviceref.TestEndpoint")
public class TestEndpointImpl implements TestEndpoint
{
   // Provide logging
   private static Logger log = Logger.getLogger(TestEndpointImpl.class);

   public String echo(String input)
   {
      log.info(input);
      return input;
   }
}
