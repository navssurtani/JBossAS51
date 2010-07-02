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
package org.jboss.ejb.plugins;

import java.security.Principal;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.X509Certificate;
import java.util.Set;

import org.jboss.invocation.Invocation;

import org.jboss.security.ssl.DomainServerSocketFactory;
import org.jboss.security.CertificatePrincipal;
import org.jboss.security.SecurityContext;
import org.jboss.security.auth.certs.SubjectDNMapping;
import org.jboss.security.identity.Identity;
import org.jboss.security.identity.IdentityFactory;
import org.jboss.security.identity.IdentityType;
import org.jboss.security.identity.extensions.CertificateIdentity;
import org.jboss.security.identity.extensions.CertificateIdentityFactory;
import org.jboss.security.identity.extensions.CredentialIdentity;

/**
 * An interceptor that looks for the peer certificates from the SSLSession
 * associated with the sessionIDKey(defaults to SESSION_ID) of the invocation.
 *
 * @see org.jboss.security.ssl.DomainServerSocketFactory
 * 
 * @author <a href="mailto:Scott.Stark@jboss.org">Scott Stark</a>.
 * @version $Revision: 81030 $
 */
public class SSLSessionInterceptor extends AbstractInterceptor
{
   /** The certificate to principal mapping interface */
   private CertificatePrincipal cpMapping = new SubjectDNMapping();
   /** The name of the invocation key with the session id */
   private String sessionIDKey = "SESSION_ID";

   public Object invokeHome(Invocation mi) throws Exception
   {
      extractSessionPrincipal(mi);
      Object returnValue = getNext().invokeHome(mi);
      return returnValue;      
   }

   public CertificatePrincipal getPrincialMapping()
   {
      return cpMapping;
   }

   public void setPrincialMapping(CertificatePrincipal cpMapping)
   {
      this.cpMapping = cpMapping;
   }

   public String getSessionIDKey()
   {
      return sessionIDKey;
   }

   public void setSessionIDKey(String sessionIDKey)
   {
      this.sessionIDKey = sessionIDKey;
   }

   public Object invoke(Invocation mi) throws Exception
   {
      extractSessionPrincipal(mi);
      Object returnValue = getNext().invoke(mi);
      return returnValue;      
   }

   /**
    * Look for the session id in the invocation and if there is an associated
    * session in DomainServerSocketFactory, use the client cert as the
    * credential, and the cert principal mapping as the principal.
    * 
    * @param mi - the method invocation
    * @throws SSLPeerUnverifiedException
    */
   private void extractSessionPrincipal(Invocation mi)
      throws SSLPeerUnverifiedException
   {
      String sessionID = (String) mi.getValue(sessionIDKey);
      if( sessionID != null )
      {
         SSLSession session = DomainServerSocketFactory.getSSLSession(sessionID);
         if( session != null )
         {
            X509Certificate[] certs = (X509Certificate[]) session.getPeerCertificates();
            Principal caller = cpMapping.toPrinicipal(certs);
            mi.setPrincipal(caller);
            mi.setCredential(certs);
            
            //Update the invocation security context
            SecurityContext invSC = mi.getSecurityContext();
            if(invSC != null)
            {
               CertificateIdentityFactory identityFactory = (CertificateIdentityFactory) 
                                      IdentityFactory.getFactory(IdentityType.CERTIFICATE);
               CertificateIdentity certIdentity = identityFactory.createIdentity(caller, certs, null); 
               invSC.getUtil().clearIdentities(CredentialIdentity.class); 
               invSC.getUtil().addIdentity(certIdentity); 
            }
         }
      }
   }
}