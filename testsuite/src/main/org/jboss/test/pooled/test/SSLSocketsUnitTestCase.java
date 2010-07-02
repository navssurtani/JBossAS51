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
package org.jboss.test.pooled.test;

import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.Principal;
import java.rmi.RemoteException;
import javax.naming.InitialContext;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import junit.framework.Test;
import org.jboss.security.ssl.DomainSocketFactory;
import org.jboss.test.JBossTestCase;
import org.jboss.test.pooled.interfaces.StatelessSession;
import org.jboss.test.pooled.interfaces.StatelessSessionHome;

/**
 * Test of using custom SSL socket factories with the PooledInvoker ejb
 * container invoker.
 *
 * @author  Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class SSLSocketsUnitTestCase extends JBossTestCase
   implements HandshakeCompletedListener
{
   private String cipherSuite;
   private Certificate[] localCerts;
   private Certificate[] peerCerts;

   /**
    * Constructor for the CustomSocketsUnitTestCase object
    *
    * @param name  Description of Parameter
    */
   public SSLSocketsUnitTestCase(String name)
   {
      super(name);
   }

   /**
    * Test basic ejb access over the ssl socket requiring a client cert
    *
    * @exception Exception  Description of Exception
    */
   public void testClientCertSSLAccess() throws Exception
   {
      log.info("+++ testClientCertSSLAccess");
      String res = super.getResourceURL("test-configs/tomcat-ssl/conf/client.keystore");
      log.info("client.keystore: "+res);
      URL clientURL = new URL(res);
      System.setProperty("javax.net.ssl.trustStore", clientURL.getFile());
      System.setProperty("javax.net.ssl.trustStorePassword", "unit-tests-client");
      System.setProperty("javax.net.ssl.keyStore", clientURL.getFile());
      System.setProperty("javax.net.ssl.keyStorePassword", "unit-tests-client");
      //System.setProperty("javax.net.debug", "all");
      System.getProperties().put(DomainSocketFactory.HANDSHAKE_COMPLETE_LISTENER, this);

      InitialContext jndiContext = new InitialContext();
      log.debug("Lookup StatelessSessionWithPooledSSL");
      Object obj = jndiContext.lookup("StatelessSessionWithPooledSSL");
      StatelessSessionHome home = (StatelessSessionHome)obj;
      log.debug("Found StatelessSessionWithPooledSSL Home");
      StatelessSession bean = home.create();
      log.debug("Created StatelessSessionWithPooledSSL");
      Principal p = bean.echoCaller("testClientCertSSLAccess");
      log.debug("bean.echoCaller(testClientCertSSLAccess) = " + p);
      try
      {
         bean.noop();
         fail("Should not have been able to call noop");
      }
      catch(RemoteException e)
      {
         log.debug("noop failed as expected", e);
      }
      bean.remove();

      // Validate the expected ssl session
      assertTrue("CipherSuite = TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
         cipherSuite.equals("TLS_DHE_DSS_WITH_AES_128_CBC_SHA"));
      X509Certificate localCert = (X509Certificate) localCerts[0];
      assertTrue("LocalCert.SubjectDN = CN=unit-tests-client, OU=JBoss Inc., O=JBoss Inc., ST=Washington, C=US",
         localCert.getSubjectDN().getName().equals("CN=unit-tests-client, OU=JBoss Inc., O=JBoss Inc., ST=Washington, C=US"));
   }

   public void handshakeCompleted(HandshakeCompletedEvent event)
   {
      log.info("handshakeCompleted, event="+event);
      try
      {
         cipherSuite = event.getCipherSuite();
         log.info("CipherSuite: "+cipherSuite);
         localCerts = event.getLocalCertificates();
         log.info("LocalCertificates:");
         for(int n = 0; n < localCerts.length; n ++)
         {
            Certificate cert = localCerts[n];
            log.info(cert);
         }
         log.info("PeerCertificates:");
         peerCerts = event.getPeerCertificates();
         for(int n = 0; n < peerCerts.length; n ++)
         {
            Certificate cert = peerCerts[n];
            log.info(cert);
         }

         SSLSession session = event.getSession();
         String[] names = session.getValueNames();
         for(int n = 0; n < names.length; n ++)
         {
            String name = names[n];
            log.info(name+"="+session.getValue(name));
         }
      }
      catch (SSLPeerUnverifiedException e)
      {
         log.error("Failed to get peer cert", e);
      }
   }

   public static Test suite() throws Exception
   {
      System.setProperty("jbosstest.secure", "false");
      return getDeploySetup(SSLSocketsUnitTestCase.class, "pooled.jar");
   }

}
