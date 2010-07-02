/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.mail;

import java.util.Hashtable;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Session;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.spi.ObjectFactory;

/**
 * A jndi ObjectFactory implementation that creates a new Session from the
 * static class information on each getObjectInstance call.
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 85945 $
 */
public class SessionObjectFactory implements ObjectFactory
{
   private static Properties props;
   private static Authenticator auth;
   private static Session instance;
   private static boolean shareSessionInstance;

   static void setSessionFactoryInfo(Properties props, Authenticator auth)
   {
      SessionObjectFactory.props = props;
      SessionObjectFactory.auth = auth;
   }
   
   static void setShareSessionInstance(boolean shareSessionInstance)
   {
      SessionObjectFactory.shareSessionInstance = shareSessionInstance;
   }

   public Object getObjectInstance(Object obj, Name name, Context nameCtx,
         Hashtable<?, ?> environment) throws Exception
   {
      Session session = null;
      if(shareSessionInstance)
      {
         initSession();
         session = instance;
      }
      else
      {
         session = Session.getInstance(props, auth);
      }
      return session;
   }

   static synchronized void initSession()
   {
      if(instance == null)
      {
         instance = Session.getInstance(props, auth);
      }
   }
}
