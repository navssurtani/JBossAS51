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
package org.jboss.test.common;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.jboss.util.xml.catalog.CatalogManager;
import org.jboss.util.xml.catalog.Resolver;

import junit.framework.TestCase;

/**
 * A ResolverTest.
 * 
 * @author <a href="wiesed@gmail.com">Daniel Wiese</a>
 * @version $Revision: 81036 $
 */
public class ResolverTest extends TestCase
{

   /**
    * Test the refactored apache catolog.
    * 
    * @throws IOException - in error case
    * @throws MalformedURLException - in error case
    * @throws URISyntaxException - if the url can't be resolved
    */
   public void testResolver() throws IOException, MalformedURLException, URISyntaxException{
      if (true) return;
      final Resolver toTest=new Resolver();
      //If the source document contains "oasis-xml-catalog" processing instructions,
      // should they be used?
      System.setProperty("xml.catalog.allowPI", "true");
      //Which identifier is preferred, "public" or "system"?
      System.setProperty("xml.catalog.prefer", "system");
      //If non-zero, the Catalog classes will print informative and debugging messages.
      //The higher the number, the more messages.
      System.setProperty("xml.catalog.verbosity", "0");
      
      toTest.setCatalogManager(CatalogManager.getStaticManager());
      toTest.setupReaders();
      
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      URL url = loader.getResource("jax-ws-catalog.xml");
      toTest.parseCatalog(url);
      
      final String systemID="http://www.jboss.org/j2ee/dtd/jboss_xmbean_1_1.dtd";
      final String publicID="-//JBoss//DTD JBOSS XMBEAN 1.1//EN";
      
      String resolved = toTest.resolveSystem(systemID);
      this.testIfExists(resolved);
      System.out.println("Resolved: "+resolved);
      
      resolved = toTest.resolvePublic(publicID, systemID);
      System.out.println("Resolved: "+resolved);
      this.testIfExists(resolved);
      
   }
   
   private void testIfExists(String fileName) throws URISyntaxException{
      if (true) return;
      assertNotNull(fileName);
      final File file=new File(new URI(fileName));
      assertTrue(file.exists());
      System.out.println(file.getName());
   }
}
