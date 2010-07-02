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
package org.jboss.security.ssl;

import java.net.Socket;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.SSLSession;

import javassist.util.proxy.MethodHandler;

/**
 * A wrapper around SSLServerSocket that intercepts the accept call to add a
 * HandshakeCompletedListener to the resulting SSLSocket so that we can build
 * a session id to SSLSession map.
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 85945 $
 */
class DomainServerSocket
   implements MethodHandler, HandshakeCompletedListener
{
   private SSLServerSocket delegate;

   DomainServerSocket(SSLServerSocket delegate)
   {
      this.delegate = delegate;
   }

   public Object invoke(Object self, Method method, Method method1, Object[] args)
      throws Exception
   {
      Object rtn = null;
      if( method.getName().equals("accept") )
         rtn = this.accept();
      else
      {
         try
         {
            rtn = method.invoke(delegate, args);
         }
         catch (InvocationTargetException e)
         {
            Throwable t = e.getTargetException();
            if( t instanceof Exception )
               throw (Exception) t;
            else if( t instanceof Error )
               throw (Error) t;
            // Not good, but simply cannot throw a Throwable
            throw e;
         }
      }
      return rtn;
   }

   public Socket accept()
      throws IOException
   {
      SSLSocket socket = (SSLSocket) delegate.accept();
      socket.addHandshakeCompletedListener(this);
      return socket;
   }

   public void handshakeCompleted(HandshakeCompletedEvent event)
   {
      SSLSession session = event.getSession();
      String sessionID = null;
      byte[] id = session.getId();
      try
      {
         sessionID = new String(id, "UTF-8");
      }
      catch (UnsupportedEncodingException e)
      {
         sessionID = new String(id);
      }
      DomainServerSocketFactory.putSSLSession(sessionID, session);
   }

}
