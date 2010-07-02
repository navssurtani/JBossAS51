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
package org.jboss.test.web.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.Set;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.jboss.logging.Logger;

/**
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class SubjectServlet extends HttpServlet
{
   static Logger log = Logger.getLogger(SubjectServlet.class);

   protected void processRequest(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException
   {
      Principal user = request.getUserPrincipal();
      HttpSession session = request.getSession(false);
      Subject userSubject = null;
      try
      {
         userSubject = getActiveSubject();
         if( userSubject == null )
            throw new ServletException("Active subject was null");
         response.addHeader("X-SubjectServlet", userSubject.toString());         
      }
      catch(NamingException e)
      {
         throw new ServletException("Failed to lookup active subject", e);
      }
      response.setContentType("text/html");
      PrintWriter out = response.getWriter();
      out.println("<html>");
      out.println("<head><title>SecureServlet</title></head>");
      out.println("<h1>SecureServlet Accessed</h1>");
      out.println("<body>");
      out.println("You have accessed this servlet as user:"+user);
      if( session != null )
         out.println("<br>The session id is: "+session.getId());
      else
         out.println("<br>There is no session");
      out.println("<br>Subject: "+userSubject);
      out.println("</body></html>");
      out.close();
   }

   protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException
   {
      processRequest(request, response);
   }

   protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException
   {
      processRequest(request, response);
   }

   protected Subject getActiveSubject() throws NamingException
   {
      InitialContext ctx = new InitialContext();
      Subject s = (Subject) ctx.lookup("java:comp/env/security/subject");
      return s;
   }
}
