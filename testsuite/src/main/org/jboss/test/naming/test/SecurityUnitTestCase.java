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
package org.jboss.test.naming.test;

import java.lang.reflect.UndeclaredThrowableException;
import java.security.Principal;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import javax.security.auth.login.LoginContext;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.jboss.security.SecurityAssociation;
import org.jboss.test.JBossTestCase;
import org.jboss.test.naming.interfaces.TestENC;
import org.jboss.test.naming.interfaces.TestENCHome;
import org.jboss.test.util.AppCallbackHandler;

/** Tests of secured access to the JNDI naming service. This testsuite will
 * be run with the standard security resources available via the classpath.
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81036 $
 */
public class SecurityUnitTestCase extends JBossTestCase
{

   private String JNDI_URL;
   private String INVOKER_BASE;

   public static Test suite() throws Exception
   {
      // JBAS-3606, the execution order of tests in this test case is important
      // so it must be defined explicitly when running under some JVMs
      TestSuite suite = new TestSuite();
      suite.addTest(new SecurityUnitTestCase("testSecureHttpInvokerFailure"));
      suite.addTest(new SecurityUnitTestCase("testSecureHttpInvoker"));
      suite.addTest(new SecurityUnitTestCase("testHttpReadonlyLookup"));
      suite.addTest(new SecurityUnitTestCase("testHttpReadonlyContextLookup"));
      suite.addTest(new SecurityUnitTestCase("testLoginInitialContext"));
      suite.addTest(new SecurityUnitTestCase("testSecureEJBViaLoginInitialContextFactory"));
      suite.addTest(new SecurityUnitTestCase("testSecureEJBViaJndiLoginInitialContextFactory"));

      return suite;
   }

   protected void setUp() throws Exception
   {
      super.setUp();
      JNDI_URL = "jnp://" + getServerHost() + ":1099/";
      INVOKER_BASE = "http://"+ getServerHost() + ":8080/invoker/";
   }
   
   /**
    * Constructor for the SecurityUnitTestCase object
    *
    * @param name  Test name
    */
   public SecurityUnitTestCase(String name)
   {
      super(name);
   }

   /** Test access to the security http InitialContext without a login
    *
    * @throws Exception
    */
   public void testSecureHttpInvokerFailure() throws Exception
   {
      getLog().debug("+++ testSecureHttpInvokerFailure");
      Properties env = new Properties();
      env.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.HttpNamingContextFactory");

      // Test the secured JNDI factory
      env.setProperty(Context.PROVIDER_URL, INVOKER_BASE +"restricted/JNDIFactory");
      getLog().debug("Creating InitialContext with env="+env);

      // Try without a login to ensure the lookup fails
      try
      {
         getLog().debug("Testing without valid login");
         InitialContext ctx1 = new InitialContext(env);
         getLog().debug("Created InitialContext");
         Object obj1 = ctx1.lookup("jmx");
         getLog().debug("lookup(jmx) : "+obj1);
         fail("Should not have been able to lookup(jmx)");
      }
      catch(Exception e)
      {
         getLog().debug("Lookup failed as expected", e);
      }

   }

   /** Test access to the JNDI naming service over a restricted http URL
    */
   public void testSecureHttpInvoker() throws Exception
   {
      getLog().debug("+++ testSecureHttpInvoker");
      Properties env = new Properties();
      env.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.HttpNamingContextFactory");

      // Specify the login conf file location
      String authConf = super.getResourceURL("security/auth.conf");
      getLog().debug("Using auth.conf: "+authConf);
      System.setProperty("java.security.auth.login.config", authConf);
      AppCallbackHandler handler = new AppCallbackHandler("admin", "admin".toCharArray());
      LoginContext lc = new LoginContext("testSecureHttpInvoker", handler);
      lc.login();

      // Test the secured JNDI factory
      env.setProperty(Context.PROVIDER_URL, INVOKER_BASE  + "restricted/JNDIFactory");
      getLog().debug("Creating InitialContext with env="+env);
      InitialContext ctx = new InitialContext(env);
      getLog().debug("Created InitialContext");
      Object obj = ctx.lookup("jmx");
      getLog().debug("lookup(jmx) : "+obj);
      Context jmxCtx = (Context) obj;
      NamingEnumeration list = jmxCtx.list("");
      while( list.hasMore() )
      {
         Object entry = list.next();
         getLog().debug(" + "+entry);
      }
      ctx.close();
      lc.logout();

      Principal p = SecurityAssociation.getPrincipal();
      assertTrue("SecurityAssociation.getPrincipal is null", p == null);

      /* This is now failing because we don't appear to have anyway to flush
      the java.net.Authenticator cache. Need to figure out how this can
      be done or switch a better http client library.
      // Try without a login to ensure the lookup fails
      testSecureHttpInvokerFailure();
      */
   }

   /** Test access of the readonly context without a login
    *
    * @throws Exception
    */
   public void testHttpReadonlyLookup() throws Exception
   {
      getLog().debug("+++ testHttpReadonlyLookup");
      /* Try without a login to ensure that a lookup against "readonly" works.
       *First create the readonly context using the standard JNDI factory
      */
      InitialContext bootCtx = new InitialContext();
      try
      {
         bootCtx.unbind("readonly");
      }
      catch(NamingException ignore)
      {
      }
      Context readonly = bootCtx.createSubcontext("readonly");
      readonly.bind("data", "somedata");

      Properties env = new Properties();
      env.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.HttpNamingContextFactory");
      env.setProperty(Context.PROVIDER_URL, INVOKER_BASE + "ReadOnlyJNDIFactory");
      getLog().debug("Creating InitialContext with env="+env);
      InitialContext ctx = new InitialContext(env);
      Object data = ctx.lookup("readonly/data");
      getLog().debug("lookup(readonly/data) : "+data);
      try
      {
         // Try to bind into the readonly context
         ctx.bind("readonly/mydata", "otherdata");
         fail("Was able to bind into the readonly context");
      }
      catch(UndeclaredThrowableException e)
      {
         getLog().debug("Invalid exception", e);
         fail("UndeclaredThrowableException thrown");
      }
      catch(Exception e)
      {
         getLog().debug("Bind failed as expected", e);
      }

      try
      {
         // Try to access a context other then under readonly
         ctx.lookup("invokers");
         fail("Was able to lookup(invokers)");
      }
      catch(UndeclaredThrowableException e)
      {
         getLog().debug("Invalid exception", e);
         fail("UndeclaredThrowableException thrown");
      }
      catch(Exception e)
      {
         getLog().debug("lookup(invokers) failed as expected", e);
      }
   }

   /** Test access of the readonly context without a login
    *
    * @throws Exception
    */
   public void testHttpReadonlyContextLookup() throws Exception
   {
      getLog().debug("+++ testHttpReadonlyContextLookup");
      /* Deploy a customized naming service with a NamingContext proxy
      replacement interceptor
      */
      deploy("naming-readonly.sar");

      /* Try without a login to ensure that a lookup against "readonly" works.
      First create the readonly context using a non-readonly JNDI factory
      */
      Properties env = new Properties();
      env.setProperty(Context.INITIAL_CONTEXT_FACTORY,
         "org.jboss.test.naming.test.BootstrapNamingContextFactory");
      env.setProperty(Context.PROVIDER_URL, JNDI_URL);
      env.setProperty("bootstrap-binding", "naming/Naming");
      getLog().debug("Creating bootstrap InitialContext with env="+env);
      InitialContext bootCtx = new InitialContext(env);
      try
      {
         bootCtx.unbind("readonly");
      }
      catch(NamingException ignore)
      {
      }
      getLog().debug("Creating readonly context");
      bootCtx.createSubcontext("readonly");
      bootCtx.bind("readonly/data", "somedata");

      // Test access through the readonly proxy
      env.setProperty("bootstrap-binding", "naming/ReadOnlyNaming");
      getLog().debug("Creating InitialContext with env="+env);
      InitialContext ctx = new InitialContext(env);
      Object data = ctx.lookup("readonly/data");
      getLog().debug("lookup(readonly/data) : "+data);
      // Lookup the readonly context to see that the readonly proxy is seen
      Object robinding = ctx.lookup("readonly");
      getLog().debug("Looked up readonly: "+robinding);
      Context roctx = (Context) robinding;
      data = roctx.lookup("data");
      getLog().debug("Looked up data: "+data);
      assertTrue("lookup(data) == somedata: "+data, "somedata".equals(data));
      try
      {
         // Try to bind into the readonly context
         roctx.bind("mydata", "otherdata");
         fail("Was able to bind into the readonly context");
      }
      catch(UndeclaredThrowableException e)
      {
         getLog().debug("Invalid exception", e);
         fail("UndeclaredThrowableException thrown");
      }
      catch(NamingException e)
      {
         getLog().debug("Bind failed as expected", e);
      }

      try
      {
         // Try to access a context other then under readonly
         ctx.lookup("invokers");
         fail("Was able to lookup(invokers)");
      }
      catch(UndeclaredThrowableException e)
      {
         getLog().debug("Invalid exception", e);
         fail("UndeclaredThrowableException thrown");
      }
      catch(Exception e)
      {
         getLog().debug("lookup(invokers) failed as expected", e);
      }
      undeploy("naming-readonly.sar");
   }

   /** Test an initial context factory that does a JAAS login to validate the
    * credentials passed in
    */
   public void testLoginInitialContext() throws Exception
   {
      getLog().debug("+++ testLoginInitialContext");
      Properties env = new Properties();
      // Try with a login that should succeed
      env.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.security.jndi.LoginInitialContextFactory");
      env.setProperty(Context.PROVIDER_URL, JNDI_URL);
      env.setProperty(Context.SECURITY_CREDENTIALS, "theduke");
      env.setProperty(Context.SECURITY_PRINCIPAL, "jduke");
      env.setProperty(Context.SECURITY_PROTOCOL, "testLoginInitialContext");

      // Specify the login conf file location
      String authConf = super.getResourceURL("security/auth.conf");
      System.setProperty("java.security.auth.login.config", authConf);

      getLog().debug("Creating InitialContext with env="+env);
      InitialContext ctx = new InitialContext(env);
      getLog().debug("Created InitialContext");
      Object obj = ctx.lookup("jmx");
      getLog().debug("lookup(jmx) : "+obj);
      Context jmxCtx = (Context) obj;
      NamingEnumeration list = jmxCtx.list("");
      while( list.hasMore() )
      {
         Object entry = list.next();
         getLog().debug(" + "+entry);
      }
      ctx.close();

      // Try with a login that should fail
      env.setProperty(Context.SECURITY_CREDENTIALS, "badpass");
      try
      {
         getLog().debug("Creating InitialContext with env="+env);
         ctx = new InitialContext(env);
         fail("Was able to create InitialContext with badpass");
      }
      catch(NamingException e)
      {
         getLog().debug("InitialContext failed as expected with exception", e);
      }
   }

   /**
    * Use the LoginInitialContextFactory to access a secured ejb
    * @throws Exception
    */ 
   public void testSecureEJBViaLoginInitialContextFactory() throws Exception
   {
      getLog().debug("+++ testSecureEJBViaLoginInitialContextFactory");
      Properties env = new Properties();
      // Try with a login that should succeed
      env.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.security.jndi.LoginInitialContextFactory");
      env.setProperty(Context.PROVIDER_URL, JNDI_URL);
      env.setProperty(Context.SECURITY_CREDENTIALS, "theduke");
      env.setProperty(Context.SECURITY_PRINCIPAL, "jduke");
      env.setProperty(Context.SECURITY_PROTOCOL, "testLoginInitialContext");

      // Specify the login conf file location
      String authConf = super.getResourceURL("security/auth.conf");
      log.info("auth.conf: "+authConf);
      System.setProperty("java.security.auth.login.config", authConf);

      getLog().debug("Creating InitialContext with env="+env);
      InitialContext ctx = new InitialContext(env);
      getLog().debug("Created InitialContext, ctx="+ctx);
      super.deploy("naming.jar");
      Object obj = getInitialContext().lookup("ENCTests/ejbs/SecuredENCBean");
      obj = PortableRemoteObject.narrow(obj, TestENCHome.class);
      TestENCHome home = (TestENCHome)obj;

      try
      {
         TestENC bean = home.create();
         getLog().debug("Created SecuredENCBean");
         bean.accessENC();
         bean.remove();
         System.setProperty("java.security.auth.login.config", "invalid");
      }
      finally
      {
         super.undeploy("naming.jar");
      }
   }

   /**
    * Use the LoginInitialContextFactory to access a secured ejb
    * @throws Exception
    */ 
   public void testSecureEJBViaJndiLoginInitialContextFactory() throws Exception
   {
      getLog().debug("+++ testSecureEJBViaJndiLoginInitialContextFactory");
      Properties env = new Properties();
      // Try with a login that should succeed
      env.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.security.jndi.JndiLoginInitialContextFactory");
      env.setProperty(Context.PROVIDER_URL, JNDI_URL);
      env.setProperty(Context.SECURITY_CREDENTIALS, "theduke");
      env.setProperty(Context.SECURITY_PRINCIPAL, "jduke");

      getLog().debug("Creating InitialContext with env="+env);
      InitialContext ctx = new InitialContext(env);
      getLog().debug("Created InitialContext, ctx="+ctx);
      super.deploy("naming.jar");
      Object obj = getInitialContext().lookup("ENCTests/ejbs/SecuredENCBean");
      obj = PortableRemoteObject.narrow(obj, TestENCHome.class);
      TestENCHome home = (TestENCHome)obj;
      getLog().debug("Found SecuredENCBean");

      try
      {
         TestENC bean = home.create();
         getLog().debug("Created SecuredENCBean");
         bean.accessENC();
         bean.remove();
      }
      finally
      {
         super.undeploy("naming.jar");
      }
   }
}
