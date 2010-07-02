/*
 * Copyright 1999-2001,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.web.tomcat.service;


import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.modeler.Registry;

import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.manager.Constants;

/**
 * This servlet will display a complete status of the HTTP/1.1 connector.
 *
 * @author Remy Maucherat
 * @version $Revision: 105502 $ $Date: 2010-06-01 15:39:35 -0400 (Tue, 01 Jun 2010) $
 */

public class StatusServlet
   extends HttpServlet implements NotificationListener
{


   // ----------------------------------------------------- Instance Variables


   /**
    * The debugging detail level for this servlet.
    */
   private int debug = 0;


   /**
    * MBean server.
    */
   protected MBeanServer mBeanServer = null;


   /**
    * Vector of protocol handlers object names.
    */
   protected Vector protocolHandlers = new Vector();


   /**
    * Vector of thread pools object names.
    */
   protected Vector threadPools = new Vector();


   /**
    * Vector of request processors object names.
    */
   protected Vector requestProcessors = new Vector();


   /**
    * Vector of global request processors object names.
    */
   protected Vector globalRequestProcessors = new Vector();


   /**
    * The string manager for this package.
    */
   protected static StringManager sm =
      StringManager.getManager(Constants.Package);


   // --------------------------------------------------------- Public Methods


   /**
    * Initialize this servlet.
    */
   public void init() throws ServletException
   {

      // Retrieve the MBean server
      mBeanServer = Registry.getServer();

      // Set our properties from the initialization parameters
      String value = null;
      try
      {
         value = getServletConfig().getInitParameter("debug");
         debug = Integer.parseInt(value);
      }
      catch (Throwable t)
      {
         ;
      }

      try
      {

         // Query protocol handlers
         String onStr = "*:type=ProtocolHandler,*";
         ObjectName objectName = new ObjectName(onStr);
         Set set = mBeanServer.queryMBeans(objectName, null);
         Iterator iterator = set.iterator();
         while (iterator.hasNext())
         {
            ObjectInstance oi = (ObjectInstance) iterator.next();
            protocolHandlers.addElement(oi.getObjectName());
         }

         // Query Thread Pools
         onStr = "*:type=ThreadPool,*";
         objectName = new ObjectName(onStr);
         set = mBeanServer.queryMBeans(objectName, null);
         iterator = set.iterator();
         while (iterator.hasNext())
         {
            ObjectInstance oi = (ObjectInstance) iterator.next();
            threadPools.addElement(oi.getObjectName());
         }

         // Query Global Request Processors
         onStr = "*:type=GlobalRequestProcessor,*";
         objectName = new ObjectName(onStr);
         set = mBeanServer.queryMBeans(objectName, null);
         iterator = set.iterator();
         while (iterator.hasNext())
         {
            ObjectInstance oi = (ObjectInstance) iterator.next();
            globalRequestProcessors.addElement(oi.getObjectName());
         }

         // Query Request Processors
         onStr = "*:type=RequestProcessor,*";
         objectName = new ObjectName(onStr);
         set = mBeanServer.queryMBeans(objectName, null);
         iterator = set.iterator();
         while (iterator.hasNext())
         {
            ObjectInstance oi = (ObjectInstance) iterator.next();
            requestProcessors.addElement(oi.getObjectName());
         }

         // Register with MBean server
         onStr = "JMImplementation:type=MBeanServerDelegate";
         objectName = new ObjectName(onStr);
         mBeanServer.addNotificationListener(objectName, this, null, null);

      }
      catch (Exception e)
      {
         e.printStackTrace();
      }

   }


   /**
    * Finalize this servlet.
    */
   public void destroy()
   {

      ;       // No actions necessary

   }


   /**
    * Process a GET request for the specified resource.
    *
    * @param request  The servlet request we are processing
    * @param response The servlet response we are creating
    * @throws IOException      if an input/output error occurs
    * @throws ServletException if a servlet-specified error occurs
    */
   public void doGet(HttpServletRequest request,
      HttpServletResponse response)
      throws IOException, ServletException
   {

      // mode is flag for HTML or XML output
      int mode = 0;
      // if ?XML=true, set the mode to XML
      if ("true".equals(request.getParameter("XML")))
      {
         mode = 1;
      }
      StatusTransformer.setContentType(response, mode);

      PrintWriter writer = response.getWriter();

      boolean completeStatus = false;
      if ("true".equals(request.getParameter("full")))
      {
         completeStatus = true;
      }
        
      // use StatusTransformer to output status
      if (mode == 0)
      {
         // HTML Header Section
         writer.print(HTML_HEADER);
      }
      else if (mode == 1)
      {
         writer.write(Constants.XML_DECLARATION);
         writer.write
            (Constants.XML_STYLE);
         writer.write("<status>");
      }

      try
      {

         // Display virtual machine statistics
         StatusTransformer.writeVMState(writer, mode);

         Enumeration i = threadPools.elements();
         while (i.hasMoreElements())
         {
            ObjectName objectName = (ObjectName) i.nextElement();
            String name = objectName.getKeyProperty("name");
            // use StatusTransformer to output status
            StatusTransformer.writeConnectorState
               (writer, objectName,
                  name, mBeanServer, globalRequestProcessors,
                  requestProcessors, mode);
         }

         if (completeStatus)
         {
            // Note: Retrieving the full status is much slower
            // use StatusTransformer to output status
            StatusTransformer.writeDetailedState
               (writer, mBeanServer, mode);
         }

      }
      catch (Exception e)
      {
         throw new ServletException(e);
      }

      if (mode == 0)
      {
         writer.print(HTML_FOOTER);
      }
      else if (mode == 1)
      {
         writer.write("</status>");
      }

   }

   // ------------------------------------------- NotificationListener Methods


   public void handleNotification(Notification notification,
      java.lang.Object handback)
   {

      if (notification instanceof MBeanServerNotification)
      {
         ObjectName objectName =
            ((MBeanServerNotification) notification).getMBeanName();
         if (notification.getType().equals
            (MBeanServerNotification.REGISTRATION_NOTIFICATION))
         {
            String type = objectName.getKeyProperty("type");
            if (type != null)
            {
               if (type.equals("ProtocolHandler"))
               {
                  protocolHandlers.addElement(objectName);
               }
               else if (type.equals("ThreadPool"))
               {
                  threadPools.addElement(objectName);
               }
               else if (type.equals("GlobalRequestProcessor"))
               {
                  globalRequestProcessors.addElement(objectName);
               }
               else if (type.equals("RequestProcessor"))
               {
                  requestProcessors.addElement(objectName);
               }
            }
         }
         else if (notification.getType().equals
            (MBeanServerNotification.UNREGISTRATION_NOTIFICATION))
         {
            String type = objectName.getKeyProperty("type");
            if (type != null)
            {
               if (type.equals("ProtocolHandler"))
               {
                  protocolHandlers.removeElement(objectName);
               }
               else if (type.equals("ThreadPool"))
               {
                  threadPools.removeElement(objectName);
               }
               else if (type.equals("GlobalRequestProcessor"))
               {
                  globalRequestProcessors.removeElement(objectName);
               }
               else if (type.equals("RequestProcessor"))
               {
                  requestProcessors.removeElement(objectName);
               }
            }
            String j2eeType = objectName.getKeyProperty("j2eeType");
            if (j2eeType != null)
            {

            }
         }
      }

   }


   // ------------------------------------------------------- Private Constats


   private static final String HTML_HEADER =
      "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>"
      + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
      + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
      + "<head>"
      + "<title>Tomcat Status</title>"
      + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\" />"
      + "<link rel=\"StyleSheet\" href=\"css/jboss.css\" type=\"text/css\"/>"
      + "</head>"
      + "<body>"
      + "<!-- header begin -->"
      + "<a href=\"http://www.jboss.org\">"
      + "<img src=\"images/logo.gif\" alt=\"JBoss\" id=\"logo\" width=\"226\" height=\"105\" />"
      + "</a>"
      + "<div id=\"header\">&nbsp;</div>"
      + "<div id=\"navigation_bar\">"
      + "</div>"
      + "<!-- header end -->";

   private static final String HTML_FOOTER =
      "<!-- footer begin -->"
      + "<div id=\"footer\">"
      + "<div id=\"credits\">JBoss&trade; Application Server</div>"
      + "<div id=\"footer_bar\">&nbsp;</div>"
      + "</div>"
      + "<!-- footer end -->"
      + "</body>"
      + "</html>";


}
