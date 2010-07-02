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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.WeakHashMap;
import javax.naming.InitialContext;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSession;

import org.jboss.logging.Logger;
import org.jboss.security.SecurityDomain;
import javassist.util.proxy.ProxyFactory;

/**
 * An implementation of ServerSocketFactory that creates SSL server sockets using the JSSE SSLContext and a JBossSX
 * SecurityDomain for the KeyManagerFactory and TrustManagerFactory objects.
 * 
 * @see javax.net.ssl.SSLContext
 * @see org.jboss.security.SecurityDomain
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 85945 $
 */
public class DomainServerSocketFactory extends SSLServerSocketFactory
{
   private static Logger log = Logger.getLogger(DomainServerSocketFactory.class);

   /** WeakHashMap<String, SSLSession> */
   private static WeakHashMap sessionMap = new WeakHashMap();

   private transient SecurityDomain securityDomain;

   private transient InetAddress bindAddress;

   private transient SSLContext sslCtx = null;

   private boolean wantsClientAuth = true;

   private boolean needsClientAuth = false;

   private String[] cipherSuites;

   private String[] protocols;

   /**
    * The default ServerSocketFactory which looks to the java:/jaas/other security domain configuration.
    */
   public static ServerSocketFactory getDefault()
   {
      DomainServerSocketFactory ssf = null;
      try
      {
         InitialContext iniCtx = new InitialContext();
         SecurityDomain sd = (SecurityDomain) iniCtx.lookup("java:/jaas/other");
         ssf = new DomainServerSocketFactory(sd);
      }
      catch (Exception e)
      {
         log.error("Failed to create default ServerSocketFactory", e);
      }
      return ssf;
   }

   public static synchronized SSLSession getSSLSession(String sessionID)
   {
      SSLSession session = (SSLSession) sessionMap.get(sessionID);
      return session;
   }

   static synchronized SSLSession putSSLSession(String sessionID, SSLSession session)
   {
      SSLSession prevSession = (SSLSession) sessionMap.put(sessionID, session);
      return prevSession;
   }

   static synchronized SSLSession removeSSLSession(String sessionID)
   {
      SSLSession session = (SSLSession) sessionMap.remove(sessionID);
      return session;
   }

   /**
    * A default constructor for use when created by Class.newInstance. The factory is not usable until its
    * SecurityDomain has been established.
    */
   public DomainServerSocketFactory()
   {
   }

   /**
    * Create a socket factory instance that uses the given SecurityDomain as the source for the SSL KeyManagerFactory
    * and TrustManagerFactory.
    */
   public DomainServerSocketFactory(SecurityDomain securityDomain) throws IOException
   {
      if (securityDomain == null)
         throw new IOException("The securityDomain may not be null");
      this.securityDomain = securityDomain;
   }

   public String getBindAddress()
   {
      String address = null;
      if (bindAddress != null)
         address = bindAddress.getHostAddress();
      return address;
   }

   public void setBindAddress(String host) throws UnknownHostException
   {
      bindAddress = InetAddress.getByName(host);
   }

   public SecurityDomain getSecurityDomain()
   {
      return securityDomain;
   }

   public void setSecurityDomain(SecurityDomain securityDomain)
   {
      this.securityDomain = securityDomain;
   }

   public boolean isWantsClientAuth()
   {
      return wantsClientAuth;
   }

   public void setWantsClientAuth(boolean wantsClientAuth)
   {
      this.wantsClientAuth = wantsClientAuth;
   }

   public boolean isNeedsClientAuth()
   {
      return needsClientAuth;
   }

   public void setNeedsClientAuth(boolean needsClientAuth)
   {
      this.needsClientAuth = needsClientAuth;
   }

   /**
    * @return current set of cipher suite names
    */
   public String[] getCipherSuites()
   {
      return cipherSuites;
   }

   /**
    * @param cipherSuites - set of cipher suite names to use
    */
   public void setCipherSuites(String[] cipherSuites)
   {
      this.cipherSuites = cipherSuites;
   }

   /**
    * This is an error due to a typo in the ciperSuites ivar
    * 
    * @deprecated use getCipherSuites
    * @return current set of cipher suite names
    */
   @Deprecated
   public String[] getCiperSuites()
   {
      return cipherSuites;
   }

   /**
    * This is an error due to a typo in the ciperSuites ivar
    * 
    * @deprecated use getCipherSuites
    * @param cipherSuites - set of cipher suite names to use
    */
   @Deprecated
   public void setCiperSuites(String[] cipherSuites)
   {
      this.cipherSuites = cipherSuites;
   }

   public String[] getProtocols()
   {
      return protocols;
   }

   public void setProtocols(String[] protocols)
   {
      this.protocols = protocols;
   }

   // --- Begin SSLServerSocketFactory interface methods
   @Override
   public ServerSocket createServerSocket(int port) throws IOException
   {
      return createServerSocket(port, 50, bindAddress);
   }

   @Override
   public ServerSocket createServerSocket(int port, int backlog) throws IOException
   {
      return createServerSocket(port, backlog, bindAddress);
   }

   /**
    * Returns a server socket which uses only the specified network interface on the local host, is bound to a the
    * specified port, and uses the specified connection backlog. The socket is configured with the socket options (such
    * as accept timeout) given to this factory.
    * 
    * @param port the port to listen to
    * @param backlog how many connections are queued
    * @param ifAddress the network interface address to use
    * 
    * @exception IOException for networking errors
    */
   @Override
   public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException
   {
      initSSLContext();
      SSLServerSocketFactory factory = sslCtx.getServerSocketFactory();
      SSLServerSocket socket = (SSLServerSocket) factory.createServerSocket(port, backlog, ifAddress);
      SSLSessionContext ctx = sslCtx.getServerSessionContext();
      System.out.println(ctx);
      if (log.isTraceEnabled())
      {
         String[] supportedProtocols = socket.getSupportedProtocols();
         log.debug("Supported protocols: " + Arrays.asList(supportedProtocols));
         String[] supportedCipherSuites = socket.getSupportedCipherSuites();
         log.debug("Supported CipherSuites: " + Arrays.asList(supportedCipherSuites));
      }
      socket.setNeedClientAuth(needsClientAuth);
      // JBAS-5815: only set the wantClientAuth property if needClientAuth hasn't been already set.
      if (!needsClientAuth)
         socket.setWantClientAuth(wantsClientAuth);

      if (protocols != null)
         socket.setEnabledProtocols(protocols);
      if (cipherSuites != null)
         socket.setEnabledCipherSuites(cipherSuites);

      DomainServerSocket handler = new DomainServerSocket(socket);
      ProxyFactory pf = new ProxyFactory();
      pf.setHandler(handler);
      pf.setSuperclass(SSLServerSocket.class);
      Class[] sig = {};
      Object[] args = {};

      SSLServerSocket proxy = null;
      try
      {
         proxy = (SSLServerSocket) pf.create(sig, args);
      }
      catch (Exception e)
      {
         IOException ioe = new IOException("Failed to create SSLServerSocket proxy");
         ioe.initCause(e);
         throw ioe;
      }
      return proxy;
   }

   @Override
   public String[] getDefaultCipherSuites()
   {
      String[] cipherSuites = {};
      try
      {
         initSSLContext();
         SSLServerSocketFactory factory = sslCtx.getServerSocketFactory();
         cipherSuites = factory.getDefaultCipherSuites();
      }
      catch (IOException e)
      {
         log.error("Failed to get default SSLServerSocketFactory", e);
      }
      return cipherSuites;
   }

   @Override
   public String[] getSupportedCipherSuites()
   {
      String[] cipherSuites = {};
      try
      {
         initSSLContext();
         SSLServerSocketFactory factory = sslCtx.getServerSocketFactory();
         cipherSuites = factory.getSupportedCipherSuites();
      }
      catch (IOException e)
      {
         log.error("Failed to get default SSLServerSocketFactory", e);
      }
      return cipherSuites;
   }

   // --- End SSLServerSocketFactory interface methods

   private void initSSLContext() throws IOException
   {
      if (sslCtx != null)
         return;
      sslCtx = Context.forDomain(securityDomain);
   }
}
