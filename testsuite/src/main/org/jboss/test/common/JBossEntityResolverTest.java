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

import java.io.InputStream;

import junit.framework.TestCase;

import org.jboss.util.JBossStringBuilder;
import org.jboss.util.xml.JBossEntityResolver;
import org.xml.sax.InputSource;

/**
 * A JBossEntityResolverTest.
 * 
 * @author <a href="wiesed@gmail.com">Daniel Wiese</a>
 * @version $Revision: 81036 $
 */
public class JBossEntityResolverTest extends TestCase
{

   private JBossEntityResolver toTest = null;

   /**
    * Setup the test.
    * @throws Exception - in error case
    */
   protected void setUp() throws Exception
   {
      super.setUp();
      //toTest = new JBossEntityResolver();
   }

   /**
    * Clenup method.
    * @throws Exception - in error case
    */
   protected void tearDown() throws Exception
   {
      super.tearDown();
      toTest = null;

   }

   /**
    * Test method for 'org.jboss.util.xml.JBossEntityResolver.resolveEntity(String, String)'.
    * @throws Exception - in error case
    */
   public void testResolveEntity_usingOasisCatog() throws Exception
   {
      if (true) return;
      InputSource input = this.toTest.resolveEntity("-//JBoss//DTD JBOSS XMBEAN 1.1//EN",
            "http://www.jboss.org/j2ee/dtd/jboss_xmbean_1_1.dtd");
      assertNotNull(input);
      String back = this.stream2String(input);
      assertNotNull(back);
      assertTrue(back.startsWith("<?xml version='1.0' encoding='UTF-8' ?>"));
      assertTrue(back.indexOf("<!ELEMENT mbean") > 0);

      input = this.toTest.resolveEntity("-//JBoss//DTD JBOSS XMBEAN 1.2//EN",
            "http://www.jboss.org/j2ee/dtd/jboss_xmbean_1_2.dtd");
      assertNotNull(input);
      back = this.stream2String(input);
      assertNotNull(input);
      assertNotNull(back);
      assertTrue(back.startsWith("<?xml version='1.0' encoding='UTF-8' ?>"));
      assertTrue(back.indexOf("<!ELEMENT mbean") > 0);

      input = this.toTest.resolveEntity(" -//JBoss//DTD JBOSS Security Config 3.0//EN",
            "http://www.jboss.org/j2ee/dtd/security_config.dtd");
      assertNotNull(input);
      back = this.stream2String(input);
      assertNotNull(input);
      assertNotNull(back);
      assertTrue(back.startsWith("<?xml version='1.0' encoding='UTF-8' ?>"));
      assertTrue(back.indexOf("<login-module code") > 0);

      input = this.toTest.resolveEntity(" -//JBoss//DTD JBOSSCMP-JDBC 4.0//EN",
            "http://www.jboss.org/j2ee/dtd/jbosscmp-jdbc_4_0.dtd");
      assertNotNull(input);
      back = this.stream2String(input);
      assertNotNull(input);
      assertNotNull(back);
      assertTrue(back.startsWith("<?xml version='1.0' encoding='UTF-8' ?>"));
      assertTrue(back.indexOf("<!ELEMENT jbosscmp-jdbc") > 0);

   }


   /**
    * Test the old backward compatibility resolution technique
    * 
    * @throws Exception - in error case
    */
   public void testResolveEntity_usingOldMap() throws Exception
   {
      if (true) return;
      this.toTest.registerLocalEntity("-//JBoss//DTD JBOSS XMBEAN 500//EN", "dtd/jboss_xmbean_1_0.dtd");

      InputSource input = this.toTest.resolveEntity("-//JBoss//DTD JBOSS XMBEAN 500//EN", null);
      assertNotNull(input);
      String back = this.stream2String(input);
      assertNotNull(back);
      assertTrue(back.startsWith("<?xml version='1.0' encoding='UTF-8' ?>"));
      assertTrue(back.indexOf("<!ELEMENT mbean") > 0);

   }
   
   /**
    * Test the old backward compatibility resolution technique
    * 
    * @throws Exception - in error case
    */
   public void testEntityResolved_singleThread() throws Exception
   {
      if (true) return;
      this.toTest.resolveEntity(null, null);
      assertFalse(this.toTest.isEntityResolved());
      this.toTest.resolveEntity("-//JBoss//DTD JBOSS XMBEAN 1.2//EN",
      "http://www.jboss.org/j2ee/dtd/jboss_xmbean_1_2.dtd");
      assertTrue(this.toTest.isEntityResolved());
      
   }

   private String stream2String(InputSource input) throws Exception
   {
      final JBossStringBuilder back = new JBossStringBuilder();
      byte[] tmp = new byte[255];
      InputStream rd = input.getByteStream();

      while (rd.read(tmp) != -1)
      {
         back.append(new String(tmp));
      }

      return back.toString();

   }

}
