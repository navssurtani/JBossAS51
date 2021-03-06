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
package javax.management.remote;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.MBeanServer;
import org.jboss.logging.Logger;
import org.jboss.mx.remoting.connector.ConnectorFactoryUtil;

/**
 * @author <a href="mailto:tom@jboss.org">Tom Elrod</a>
 */
public class JMXConnectorServerFactory
{
   public static final String DEFAULT_CLASS_LOADER = "jmx.remote.default.class.loader";
   public static final String DEFAULT_CLASS_LOADER_NAME = "jmx.remote.default.class.loader.name";
   public static final String PROTOCOL_PROVIDER_PACKAGES = "jmx.remote.protocol.provider.pkgs";
   public static final String PROTOCOL_PROVIDER_CLASS_LOADER = "jmx.remote.protocol.provider.class.loader";

   private static final String SERVER_CLASSNAME = "ServerProvider";

   private static final String DEFAULT_PROTOCOL_PROVIDER_PACKAGE = "org.jboss.mx.remoting.provider";

   protected static Logger log = Logger.getLogger(JMXConnectorServerFactory.class.getName());

   private JMXConnectorServerFactory()
   {
      // per spec, can not have any public instances of this class.
   }

   public static JMXConnectorServer newJMXConnectorServer(JMXServiceURL serviceURL,
                                                          Map environment,
                                                          MBeanServer mbeanServer)
         throws IOException
   {
      JMXConnectorServer connectorServer = null;

      if(serviceURL != null)
      {
         String protocol = serviceURL.getProtocol();
         protocol = ConnectorFactoryUtil.normalizeProtocol(protocol);


         Map localEnvironment = new HashMap();
         if(environment != null)
         {
            localEnvironment = new HashMap(environment);
         }

         // now need to check envrionment map for loading info
         if(localEnvironment != null)
         {
            ConnectorFactoryUtil.validateEnvironmentMap(localEnvironment);

            ClassLoader classLoader = ConnectorFactoryUtil.locateClassLoader(localEnvironment,
                                                                             PROTOCOL_PROVIDER_CLASS_LOADER);
            List providerPackages = ConnectorFactoryUtil.locateProviderPackage(localEnvironment,
                                                                               PROTOCOL_PROVIDER_PACKAGES);

            List providers = loadProviders(providerPackages, classLoader,
                                           protocol);

            for(int i = 0; i < providers.size() && connectorServer == null; i++)
            {
               JMXConnectorServerProvider provider = (JMXConnectorServerProvider) providers.get(i);
               connectorServer = provider.newJMXConnectorServer(serviceURL,
                                                                Collections.unmodifiableMap(localEnvironment),
                                                                mbeanServer);
            }
         }
      }
      else
      {
         throw new NullPointerException("Can not create JMXConnectorServer using null JMXServiceURL.");
      }

      return connectorServer;

   }

   private static List loadProviders(List providerPackages, ClassLoader classLoader,
                                     String protocol)
         throws JMXProviderException, MalformedURLException
   {
      List providers = new ArrayList();

      if(providerPackages != null)
      {
         for(int x = 0; x < providerPackages.size(); x++)
         {
            String providerPackage = (String) providerPackages.get(x);
            JMXConnectorServerProvider providerInstance = loadProvider(providerPackage, protocol, classLoader);
            providers.add(providerInstance);
         }
      }

      // now add default providers for this implementation if exist
      if(providers.size() == 0)
      {
         JMXConnectorServerProvider localProvider = loadProvider(DEFAULT_PROTOCOL_PROVIDER_PACKAGE,
                                                                 protocol,
                                                                 Thread.currentThread().getContextClassLoader());
         if(localProvider != null)
         {
            providers.add(localProvider);
         }
         else
         {
            throw new MalformedURLException("Error locating provider for protocol " + protocol);
         }
      }

      return providers;
   }

   private static JMXConnectorServerProvider loadProvider(String providerPackage, String protocol, ClassLoader classLoader)
         throws JMXProviderException
   {
      JMXConnectorServerProvider providerInstance = null;

      String providerClassName = providerPackage + "." + protocol + "." + SERVER_CLASSNAME;
      try
      {
         Class providerClass = Class.forName(providerClassName,
                                             true,
                                             classLoader);
         try
         {
            providerInstance = (JMXConnectorServerProvider) providerClass.newInstance();
         }
         catch(ClassCastException ccex)
         {
            throw new JMXProviderException("Class " + providerClass +
                                           " does not implement JMXConnectorServerProvider interface.");
         }
         catch(Exception e)
         {
            throw new JMXProviderException("Error loading provider instance.", e);
         }
      }
      catch(ClassNotFoundException e)
      {
         log.warn("Could not load provider class: " + providerClassName);
      }
      catch(Exception ex)
      {
         throw new JMXProviderException("Error loading provider class (" + providerClassName + ")", ex);
      }

      return providerInstance;
   }

}