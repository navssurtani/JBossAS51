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
package org.jboss.test.jmx.test;

import java.net.URL;
import java.net.HttpURLConnection;

import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.HttpClient;

import org.jboss.test.JBossTestCase;
import org.jboss.test.util.web.HttpUtils;

/** Basic access tests of the http jmx-console interface
 *
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class JMXConsoleUnitTestCase
   extends JBossTestCase
{
   private String baseURLNoAuth = HttpUtils.getBaseURLNoAuth(); 

   public JMXConsoleUnitTestCase(String name)
   {
      super(name);
   }

   /** Test access of the jmx-console/index.jsp page
    * @throws Exception
    */ 
   public void testIndexPage()
      throws Exception
   {
      URL url = new URL(baseURLNoAuth+"jmx-console/index.jsp");
      HttpUtils.accessURL(url);
   }

   /** Test an mbean inspection via a get to the HtmlAdaptor
    * @throws Exception
    */ 
   public void testMBeanInspection()
      throws Exception
   {
      // The jboss:service=Naming mbean view 
      URL url = new URL(baseURLNoAuth+"jmx-console/HtmlAdaptor?action=inspectMBean&name=jboss%3Aservice%3DNaming");
      HttpUtils.accessURL(url);
   }

   /** Test an mbean invocation via a post to the HtmlAdaptor
    * @throws Exception
    */ 
   public void testMBeanOperation()
      throws Exception
   {
      // The jboss.system:type=Server mbean view 
      URL url = new URL(baseURLNoAuth+"jmx-console/HtmlAdaptor?action=inspectMBean&name=jboss.system%3Atype%3DServer");
      HttpUtils.accessURL(url);
      // Submit the op invocation form for op=runGarbageCollector
      PostMethod formPost = new PostMethod(baseURLNoAuth+"jmx-console/HtmlAdaptor");
      formPost.addRequestHeader("Referer", baseURLNoAuth+"jmx-console/HtmlAdaptor");
      formPost.addParameter("action", "invokeOpByName");
      formPost.addParameter("name", "jboss.system:type=Server");
      formPost.addParameter("methodName", "runGarbageCollector");
      HttpClient httpConn = new HttpClient();
      int responseCode = httpConn.executeMethod(formPost.getHostConfiguration(),
         formPost);
      assertTrue("HTTP_OK", responseCode==HttpURLConnection.HTTP_OK);
   }
}
