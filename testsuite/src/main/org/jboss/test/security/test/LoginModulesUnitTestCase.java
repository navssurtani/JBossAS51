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
package org.jboss.test.security.test;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.acl.Group;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.naming.InitialContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.resource.spi.security.PasswordCredential;
import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.sql.DataSource;

import junit.framework.TestSuite;

import org.jboss.crypto.CryptoUtil;
import org.jboss.logging.Logger;
import org.jboss.security.AuthenticationManager;
import org.jboss.security.SecurityAssociation;
import org.jboss.security.SecurityDomain;
import org.jboss.security.SimpleGroup;
import org.jboss.security.SimplePrincipal;
import org.jboss.security.auth.callback.SecurityAssociationHandler;
import org.jboss.security.auth.callback.UsernamePasswordHandler;
import org.jboss.security.auth.spi.UsernamePasswordLoginModule;
import org.jboss.security.plugins.JaasSecurityDomain;
import org.jboss.test.JBossTestCase;

/** Tests of the LoginModule classes.

 @author Scott.Stark@jboss.org
 @version $Revision: 81036 $
 */
public class LoginModulesUnitTestCase extends JBossTestCase
{

   private static Logger log = Logger.getLogger(LoginModulesUnitTestCase.class);

   /** Hard coded login configurations for the test cases. The configuration
    name corresponds to the unit test function that uses the configuration.
    */
   static class TestConfig extends Configuration
   {
      public void refresh()
      {
      }

      public AppConfigurationEntry[] getAppConfigurationEntry(String name)
      {
         AppConfigurationEntry[] entry = null;
         try
         {
            Class[] parameterTypes = {};
            Method m = getClass().getDeclaredMethod(name, parameterTypes);
            Object[] args = {};
            entry = (AppConfigurationEntry[]) m.invoke(this, args);
         }
         catch(Exception e)
         {
         }
         return entry;
      }

      AppConfigurationEntry[] testClientLogin()
      {
         String name = "org.jboss.security.ClientLoginModule";
         HashMap options = new HashMap();
         options.put("restore-login-identity", "true");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testIdentity()
      {
         String name = "org.jboss.security.auth.spi.IdentityLoginModule";
         HashMap options = new HashMap();
         options.put("principal", "stark");
         options.put("roles", "Role3,Role4");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testJdbc()
      {
         String name = "org.jboss.security.auth.spi.DatabaseServerLoginModule";
         HashMap options = new HashMap();
         options.put("dsJndiName", "testJdbc");
         options.put("principalsQuery", "select Password from Principals where PrincipalID=?");
         options.put("rolesQuery", "select Role, RoleGroup from Roles where PrincipalID=?");
         options.put("suspendResume", "false");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testSimple()
      {
         String name = "org.jboss.security.auth.spi.SimpleServerLoginModule";
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, new HashMap());
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testUsernamePassword()
      {
         return other();
      }
      AppConfigurationEntry[] testUsernamePasswordHash()
      {
         HashMap options = new HashMap();
         options.put("hashAlgorithm", "MD5");
         options.put("hashEncoding", "base64");
         AppConfigurationEntry ace = new AppConfigurationEntry(HashTestLoginModule.class.getName(),
            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testUsernamePasswordHashWithDigestCallback()
      {
         HashMap options = new HashMap();
         options.put("hashAlgorithm", "MD5");
         options.put("hashEncoding", "base64");
         options.put("hashCharset", "UTF-8");
         options.put("digestCallback", "org.jboss.test.security.test.TestDigestCallback");
         options.put("digest.preSalt", "pre");
         options.put("digest.postSalt", "post");
         AppConfigurationEntry ace = new AppConfigurationEntry(HashTestDigestCallbackLoginModule.class.getName(),
            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testAnon()
      {
         String name = "org.jboss.security.auth.spi.AnonLoginModule";
         HashMap options = new HashMap();
         options.put("unauthenticatedIdentity", "nobody");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testNull()
      {
         String name = "org.jboss.security.auth.spi.AnonLoginModule";
         HashMap options = new HashMap();
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testUsersRoles()
      {
         String name = "org.jboss.security.auth.spi.UsersRolesLoginModule";
         HashMap options = new HashMap();
         options.put("usersProperties", "security/users.properties");
         options.put("rolesProperties", "security/roles.properties");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testUsersRolesHash()
      {
         String name = "org.jboss.security.auth.spi.UsersRolesLoginModule";
         HashMap options = new HashMap();
         options.put("usersProperties", "security/usersb64.properties");
         options.put("hashAlgorithm", "MD5");
         options.put("hashEncoding", "base64");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testAnonUsersRoles()
      {
         String name = "org.jboss.security.auth.spi.UsersRolesLoginModule";
         HashMap options = new HashMap();
         options.put("usersProperties", "security/users.properties");
         options.put("rolesProperties", "security/roles.properties");
         options.put("unauthenticatedIdentity", "nobody");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testControlFlags()
      {
         String name1 = "org.jboss.security.auth.spi.UsersRolesLoginModule";
         HashMap options1 = new HashMap();
         options1.put("usersProperties", "security/users.properties");
         options1.put("rolesProperties", "security/roles.properties");
         AppConfigurationEntry ace1 = new AppConfigurationEntry(name1,
            AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT, options1);

         String name2 = "org.jboss.security.auth.spi.DatabaseServerLoginModule";
         HashMap options = new HashMap();
         options.put("dsJndiName", "testJdbc");
         options.put("principalsQuery", "select Password from Principals where PrincipalID=?");
         options.put("rolesQuery", "select Role, RoleGroup from Roles where PrincipalID=?");
         options.put("suspendResume", "false");
         AppConfigurationEntry ace2 = new AppConfigurationEntry(name2,
            AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT, options);

         AppConfigurationEntry[] entry = {ace1, ace2};
         return entry;
      }
      AppConfigurationEntry[] testJCACallerIdentity()
      {
         String name = "org.jboss.resource.security.CallerIdentityLoginModule";
         HashMap options = new HashMap();
         options.put("userName", "jduke");
         options.put("password", "theduke");
         options.put("managedConnectionFactoryName", "jboss:name=fakeMCF");
         options.put("ignoreMissigingMCF", Boolean.TRUE);
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testJaasSecurityDomainIdentityLoginModule()
      {
         String name = "org.jboss.resource.security.JaasSecurityDomainIdentityLoginModule";
         HashMap options = new HashMap();
         options.put("userName", "sa");
         options.put("password", "E5gtGMKcXPP");
         options.put("managedConnectionFactoryName", "jboss.jca:service=LocalTxCM,name=DefaultDS");
         options.put("ignoreMissigingMCF", Boolean.TRUE);
         options.put("jaasSecurityDomain", "jboss.test:service=JaasSecurityDomain,domain=testJaasSecurityDomainIdentityLoginModule");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testCertLogin()
      {
         String name = "org.jboss.security.auth.spi.BaseCertLoginModule";
         HashMap options = new HashMap();
         options.put("securityDomain", "testCertLogin");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] testCertRoles()
      {
         String name = "org.jboss.security.auth.spi.CertRolesLoginModule";
         HashMap options = new HashMap();
         options.put("securityDomain", "testCertRoles");
         options.put("usersProperties", "security/users.properties");
         options.put("rolesProperties", "security/roles.properties");
         AppConfigurationEntry ace = new AppConfigurationEntry(name,
            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options);
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
      AppConfigurationEntry[] other()
      {
         AppConfigurationEntry ace = new AppConfigurationEntry(TestLoginModule.class.getName(),
         AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, new HashMap());
         AppConfigurationEntry[] entry = {ace};
         return entry;
      }
   }

   public static class TestLoginModule extends UsernamePasswordLoginModule
   {
      protected Group[] getRoleSets()
      {
         SimpleGroup roles = new SimpleGroup("Roles");
         Group[] roleSets = {roles};
         roles.addMember(new SimplePrincipal("TestRole"));
         roles.addMember(new SimplePrincipal("Role2"));
         return roleSets;
      }
      /** This represents the 'true' password
       */
      protected String getUsersPassword()
      {
         return "secret";
      }
   }
   public static class HashTestLoginModule extends TestLoginModule
   {
      /** This represents the 'true' password in its hashed form
       */
      protected String getUsersPassword()
      {
         MessageDigest md = null;
         try
         {
            md = MessageDigest.getInstance("MD5");
         }
         catch(Exception e)
         {
            e.printStackTrace();
         }
         byte[] passwordBytes = "secret".getBytes();
         byte[] hash = md.digest(passwordBytes);
         String passwordHash = CryptoUtil.encodeBase64(hash);
         return passwordHash;
      }
   }
   public static class HashTestDigestCallbackLoginModule extends TestLoginModule
   {
      /** This represents the 'true' password in its hashed form
       */
      protected String getUsersPassword()
      {
         MessageDigest md = null;
         try
         {
            md = MessageDigest.getInstance("MD5");
         }
         catch(Exception e)
         {
            e.printStackTrace();
         }
         byte[] passwordBytes = "secret".getBytes();
         md.update("pre".getBytes());
         md.update(passwordBytes);
         md.update("post".getBytes());
         byte[] hash = md.digest();
         String passwordHash = CryptoUtil.encodeBase64(hash);
         return passwordHash;
      }
   }

   /** A pseudo DataSource that is used to provide Hypersonic db
    connections to the DatabaseServerLoginModule.
    */
   static class TestDS implements DataSource, Serializable
   {
      private static final long serialVersionUID = 1;
      public java.sql.Connection getConnection() throws java.sql.SQLException
      {
         return getConnection("sa", "");
      }
      public java.sql.Connection getConnection(String user, String pass) throws java.sql.SQLException
      {
			java.sql.Connection con = null;
			String jdbcURL = "";
			try
         {
         	jdbcURL = "jdbc:hsqldb:hsql://" + System.getProperty("jbosstest.server.host", "localhost") + ":1701";
         	con = DriverManager.getConnection(jdbcURL, user, pass);
			}
         catch(java.sql.SQLException sqle)
         {
				jdbcURL = "jdbc:hsqldb:."; // only memory jdbc url
         	con = DriverManager.getConnection(jdbcURL, user, pass);
			}
         return con;
      }
      public java.io.PrintWriter getLogWriter() throws java.sql.SQLException
      {
         return null;
      }
      public void setLogWriter(java.io.PrintWriter out)
         throws java.sql.SQLException
      {
      }
      public int getLoginTimeout() throws java.sql.SQLException
      {
         return 0;
      }
      public void setLoginTimeout(int seconds) throws java.sql.SQLException
      {
      }

      public boolean isWrapperFor(Class<?> iface) throws SQLException
      {
         return false;
      }

      public <T> T unwrap(Class<T> iface) throws SQLException
      {
         throw new SQLException("No wrapper");
      }
   }

   static class TestSecurityDomain implements SecurityDomain, Serializable
   {
      private static final long serialVersionUID = 1;

      private transient KeyStore store;

      public KeyStore getKeyStore() throws SecurityException
      {
         return store;
      }

      public KeyManagerFactory getKeyManagerFactory() throws SecurityException
      {
         return null;
      }

      public KeyStore getTrustStore() throws SecurityException
      {
         return store;
      }

      public TrustManagerFactory getTrustManagerFactory() throws SecurityException
      {
         return null;
      }

      public String getSecurityDomain()
      {
         return null;
      }

      public Subject getActiveSubject()
      {
         return null;
      }

      public boolean isValid(Principal principal, Object credential,
         Subject activeSubject)
      {
         return false;
      }

      public boolean isValid(Principal principal, Object credential)
      {
         return false;
      }

      public Principal getPrincipal(Principal principal)
      {
         return null;
      }

      public boolean doesUserHaveRole(Principal principal, Set roles)
      {
         return false;
      }

      public Set getUserRoles(Principal principal)
      {
         return null;
      }

      private void readObject(java.io.ObjectInputStream in)
         throws IOException
      {
         try
         {
            store = KeyStore.getInstance("JKS");
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            URL resURL = loader.getResource("security/tst.keystore");
            store.load(resURL.openStream(), "unit-tests".toCharArray());
         }
         catch(Exception e)
         {
            throw new IOException(e.toString());
         }
      }
      
      /**
       * @see AuthenticationManager#getTargetPrincipal(Principal,Map)
       */
      public Principal getTargetPrincipal(Principal anotherDomainPrincipal, Map contextMap)
      {
         throw new RuntimeException("Not implemented yet");
      }
   }

   public LoginModulesUnitTestCase(String testName)
   {
      super(testName);
   }

   protected void setUp() throws Exception
   {
      // Install the custom JAAS configuration
      Configuration.setConfiguration(new TestConfig());
      super.setUp(); 
      log = getLog(); 
   }

   public void testClientLogin() throws Exception
   {
      log.info("testClientLogin");
      UsernamePasswordHandler handler = new UsernamePasswordHandler("scott", "secret".toCharArray());
      LoginContext lc = new LoginContext("testClientLogin", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Principal scott = new SimplePrincipal("scott");
      assertTrue("Principals contains scott", subject.getPrincipals().contains(scott));
      Principal saPrincipal = SecurityAssociation.getPrincipal();
      assertTrue("SecurityAssociation.getPrincipal == scott", saPrincipal.equals(scott));

      UsernamePasswordHandler handler2 = new UsernamePasswordHandler("scott2", "secret2".toCharArray());
      LoginContext lc2 = new LoginContext("testClientLogin", handler2);
      lc2.login();
      Principal scott2 = new SimplePrincipal("scott2");
      saPrincipal = SecurityAssociation.getPrincipal();
      assertTrue("SecurityAssociation.getPrincipal == scott2", saPrincipal.equals(scott2));
      lc2.logout();
      saPrincipal = SecurityAssociation.getPrincipal();
      assertTrue("SecurityAssociation.getPrincipal == scott", saPrincipal.equals(scott));
      
      lc.logout();      
   }

   public void testUsernamePassword() throws Exception
   {
      log.info("testUsernamePassword");
      UsernamePasswordHandler handler = new UsernamePasswordHandler("scott", "secret".toCharArray());
      LoginContext lc = new LoginContext("testUsernamePassword", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains scott", subject.getPrincipals().contains(new SimplePrincipal("scott")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      Group roles = (Group) groups.iterator().next();
      assertTrue("TestRole is a role", roles.isMember(new SimplePrincipal("TestRole")));
      assertTrue("Role2 is a role", roles.isMember(new SimplePrincipal("Role2")));

      lc.logout();
   }
   public void testUsernamePasswordHash() throws Exception
   {
      log.info("testUsernamePasswordHash");
      UsernamePasswordHandler handler = new UsernamePasswordHandler("scott", "secret".toCharArray());
      LoginContext lc = new LoginContext("testUsernamePasswordHash", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains scott", subject.getPrincipals().contains(new SimplePrincipal("scott")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      Group roles = (Group) groups.iterator().next();
      assertTrue("TestRole is a role", roles.isMember(new SimplePrincipal("TestRole")));
      assertTrue("Role2 is a role", roles.isMember(new SimplePrincipal("Role2")));

      lc.logout();
   }

   public void testUsernamePasswordHashWithDigestCallback() throws Exception
   {
      log.info("testUsernamePasswordHashWithDigestCallback");
      // secret in ascii
      byte[] passBytes = {115, 101, 99, 114, 101, 116};
      String pass = new String(passBytes, "UTF-8");
      UsernamePasswordHandler handler = new UsernamePasswordHandler("scott", pass.toCharArray());
      LoginContext lc = new LoginContext("testUsernamePasswordHashWithDigestCallback", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains scott", subject.getPrincipals().contains(new SimplePrincipal("scott")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      Group roles = (Group) groups.iterator().next();
      assertTrue("TestRole is a role", roles.isMember(new SimplePrincipal("TestRole")));
      assertTrue("Role2 is a role", roles.isMember(new SimplePrincipal("Role2")));

      lc.logout();
   }

   public void testUsersRoles() throws Exception
   {
      log.info("testUsersRoles");
      UsernamePasswordHandler handler = new UsernamePasswordHandler("scott", "echoman".toCharArray());
      LoginContext lc = new LoginContext("testUsersRoles", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains scott", subject.getPrincipals().contains(new SimplePrincipal("scott")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      assertTrue("Principals contains CallerPrincipal", groups.contains(new SimplePrincipal("CallerPrincipal")));
      Group roles = (Group) groups.iterator().next();
      Iterator groupsIter = groups.iterator();
      while( groupsIter.hasNext() )
      {
         roles = (Group) groupsIter.next();
         if( roles.getName().equals("Roles") )
         {
            assertTrue("Echo is a role", roles.isMember(new SimplePrincipal("Echo")));
            assertTrue("Java is NOT a role", roles.isMember(new SimplePrincipal("Java")) == false);
            assertTrue("Coder is NOT a role", roles.isMember(new SimplePrincipal("Coder")) == false);
         }
         else if( roles.getName().equals("CallerPrincipal") )
         {
            log.info("CallerPrincipal is "+roles.members().nextElement());
            boolean isMember = roles.isMember(new SimplePrincipal("callerScott"));
            assertTrue("CallerPrincipal is callerScott", isMember);
         }
      }
      lc.logout();

      handler = new UsernamePasswordHandler("stark", "javaman".toCharArray());
      lc = new LoginContext("testUsersRoles", handler);
      lc.login();
      subject = lc.getSubject();
      groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains stark", subject.getPrincipals().contains(new SimplePrincipal("stark")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      assertTrue("Principals contains CallerPrincipal", groups.contains(new SimplePrincipal("CallerPrincipal")));
      groupsIter = groups.iterator();
      while( groupsIter.hasNext() )
      {
         roles = (Group) groupsIter.next();
         if( roles.getName().equals("Roles") )
         {
            assertTrue("Echo is NOT a role", roles.isMember(new SimplePrincipal("Echo")) == false);
            assertTrue("Java is a role", roles.isMember(new SimplePrincipal("Java")));
            assertTrue("Coder is a role", roles.isMember(new SimplePrincipal("Coder")));
         }
         else if( roles.getName().equals("CallerPrincipal") )
         {
            log.info("CallerPrincipal is "+roles.members().nextElement());
            boolean isMember = roles.isMember(new SimplePrincipal("callerStark"));
            assertTrue("CallerPrincipal is callerStark", isMember);
         }
      }
      lc.logout();

      // Test the usernames with common prefix
      log.info("Testing similar usernames");
      handler = new UsernamePasswordHandler("jdukeman", "anotherduke".toCharArray());
      lc = new LoginContext("testUsersRoles", handler);
      lc.login();
      subject = lc.getSubject();
      groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains jdukeman", subject.getPrincipals().contains(new SimplePrincipal("jdukeman")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      assertTrue("Principals contains CallerPrincipal", groups.contains(new SimplePrincipal("CallerPrincipal")));
      groupsIter = groups.iterator();
      while( groupsIter.hasNext() )
      {
         roles = (Group) groupsIter.next();
         if( roles.getName().equals("Roles") )
         {
            assertTrue("Role1 is NOT a role", roles.isMember(new SimplePrincipal("Role1")) == false);
            assertTrue("Role2 is a role", roles.isMember(new SimplePrincipal("Role2")));
            assertTrue("Role3 is a role", roles.isMember(new SimplePrincipal("Role3")));
         }
         else if( roles.getName().equals("CallerPrincipal") )
         {
            log.info("CallerPrincipal is "+roles.members().nextElement());
            boolean isMember = roles.isMember(new SimplePrincipal("callerJdukeman"));
            assertTrue("CallerPrincipal is callerJdukeman", isMember);
         }
      }
      lc.logout();
   }

   public void testUsersRolesHash() throws Exception
   {
      log.info("testUsersRolesHash");
      UsernamePasswordHandler handler = new UsernamePasswordHandler("scott", "echoman".toCharArray());
      LoginContext lc = new LoginContext("testUsersRolesHash", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains scott", subject.getPrincipals().contains(new SimplePrincipal("scott")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      assertTrue("Principals contains CallerPrincipal", groups.contains(new SimplePrincipal("CallerPrincipal")));
      Group roles = (Group) groups.iterator().next();
      Iterator groupsIter = groups.iterator();
      while( groupsIter.hasNext() )
      {
         roles = (Group) groupsIter.next();
         if( roles.getName().equals("Roles") )
         {
            assertTrue("Echo is a role", roles.isMember(new SimplePrincipal("Echo")));
            assertTrue("Java is NOT a role", roles.isMember(new SimplePrincipal("Java")) == false);
            assertTrue("Coder is NOT a role", roles.isMember(new SimplePrincipal("Coder")) == false);
         }
         else if( roles.getName().equals("CallerPrincipal") )
         {
            log.info("CallerPrincipal is "+roles.members().nextElement());
            boolean isMember = roles.isMember(new SimplePrincipal("callerScott"));
            assertTrue("CallerPrincipal is callerScott", isMember);
         }
      }
      lc.logout();
   }

   public void testAnonUsersRoles() throws Exception
   {
      log.info("testAnonUsersRoles");
      UsernamePasswordHandler handler = new UsernamePasswordHandler(null, null);
      LoginContext lc = new LoginContext("testAnonUsersRoles", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains nobody", subject.getPrincipals().contains(new SimplePrincipal("nobody")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      Group roles = (Group) groups.iterator().next();
      assertTrue("Roles has no members", roles.members().hasMoreElements() == false);

      lc.logout();
   }
   public void testAnon() throws Exception
   {
      log.info("testAnon");
      UsernamePasswordHandler handler = new UsernamePasswordHandler(null, null);
      LoginContext lc = new LoginContext("testAnon", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains nobody", subject.getPrincipals().contains(new SimplePrincipal("nobody")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      Group roles = (Group) groups.iterator().next();
      assertTrue("Roles has no members", roles.members().hasMoreElements() == false);

      lc.logout();
   }
   public void testNull() throws Exception
   {
      log.info("testNull");
      UsernamePasswordHandler handler = new UsernamePasswordHandler(null, null);
      LoginContext lc = new LoginContext("testNull", handler);
      try
      {
         lc.login();
         fail("Should not be able to login as null, null");
      }
      catch(LoginException e)
      {
         // Ok
      }
   }

   public void testIdentity() throws Exception
   {
      log.info("testIdentity");
      LoginContext lc = new LoginContext("testIdentity");
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains stark", subject.getPrincipals().contains(new SimplePrincipal("stark")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      Group roles = (Group) groups.iterator().next();
      assertTrue("Role2 is not a role", roles.isMember(new SimplePrincipal("Role2")) == false);
      assertTrue("Role3 is a role", roles.isMember(new SimplePrincipal("Role3")));
      assertTrue("Role4 is a role", roles.isMember(new SimplePrincipal("Role4")));

      lc.logout();
   }
   public void testJCACallerIdentity() throws Exception
   {
      log.info("testJCACallerIdentity");
      MBeanServer server = MBeanServerFactory.createMBeanServer("jboss");
      LoginContext lc = new LoginContext("testJCACallerIdentity");
      lc.login();
      Subject subject = lc.getSubject();
      assertTrue("Principals contains jduke", subject.getPrincipals().contains(new SimplePrincipal("jduke")));
      Set creds = subject.getPrivateCredentials(PasswordCredential.class);
      PasswordCredential pc = (PasswordCredential) creds.iterator().next();
      String username = pc.getUserName();
      String password = new String(pc.getPassword());
      assertTrue("PasswordCredential.username = jduke", username.equals("jduke"));
      assertTrue("PasswordCredential.password = theduke", password.equals("theduke"));
      lc.logout();

      // Test the override of the default identity
      SecurityAssociation.setPrincipal(new SimplePrincipal("jduke2"));
      SecurityAssociation.setCredential("theduke2".toCharArray());
      lc.login();
      subject = lc.getSubject();
      Set principals = subject.getPrincipals();
      assertTrue("Principals contains jduke2", principals.contains(new SimplePrincipal("jduke2")));
      assertTrue("Principals does not contains jduke", principals.contains(new SimplePrincipal("jduke")) == false);
      creds = subject.getPrivateCredentials(PasswordCredential.class);
      pc = (PasswordCredential) creds.iterator().next();
      username = pc.getUserName();
      password = new String(pc.getPassword());
      assertTrue("PasswordCredential.username = jduke2", username.equals("jduke2"));
      assertTrue("PasswordCredential.password = theduke2", password.equals("theduke2"));
      lc.logout();
      MBeanServerFactory.releaseMBeanServer(server);
   }
   public void testJaasSecurityDomainIdentityLoginModule() throws Exception
   {
      log.info("testJaasSecurityDomainIdentityLoginModule");
      MBeanServer server = MBeanServerFactory.createMBeanServer("jboss");
      JaasSecurityDomain secDomain = new JaasSecurityDomain("testEncodeDecode");
      secDomain.setSalt("abcdefgh");
      secDomain.setIterationCount(13);
      secDomain.setKeyStorePass("master");
      secDomain.setManagerServiceName(null);
      secDomain.start();
      ObjectName name = new ObjectName("jboss.test:service=JaasSecurityDomain,domain=testJaasSecurityDomainIdentityLoginModule");
      server.registerMBean(secDomain, name);

      LoginContext lc = new LoginContext("testJaasSecurityDomainIdentityLoginModule");
      lc.login();
      Subject subject = lc.getSubject();
      assertTrue("Principals contains sa", subject.getPrincipals().contains(new SimplePrincipal("sa")));
      Set creds = subject.getPrivateCredentials(PasswordCredential.class);
      PasswordCredential pc = (PasswordCredential) creds.iterator().next();
      String username = pc.getUserName();
      String password = new String(pc.getPassword());
      assertTrue("PasswordCredential.username = sa", username.equals("sa"));
      assertTrue("PasswordCredential.password = ", password.equals(""));
      lc.logout();
      server.unregisterMBean(name);
      MBeanServerFactory.releaseMBeanServer(server);
   }

   public void testSimple() throws Exception
   {
      log.info("testSimple");
      UsernamePasswordHandler handler = new UsernamePasswordHandler("jduke", "jduke".toCharArray());
      LoginContext lc = new LoginContext("testSimple", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains jduke", subject.getPrincipals().contains(new SimplePrincipal("jduke")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      Group roles = (Group) groups.iterator().next();
      assertTrue("user is a role", roles.isMember(new SimplePrincipal("user")));
      assertTrue("guest is a role", roles.isMember(new SimplePrincipal("guest")));

      lc.logout();
   }

   /** Use this DDL script to setup tables:
    ; First load the JDBC driver and open a database.
    d org.enhydra.instantdb.jdbc.idbDriver;
    o jdbc:idb=/usr/local/src/cvsroot/jBoss/jboss/dist/conf/default/instantdb.properties;

    ; Create the Principal table
    e DROP TABLE Principals ;
    e CREATE TABLE Principals (
    PrincipalID	VARCHAR(64) PRIMARY KEY,
    Password	VARCHAR(64) );

    ; put some initial data in the table
    e INSERT INTO Principals VALUES ("scott", "echoman");
    e INSERT INTO Principals VALUES ("stark", "javaman");

    ; Create the Roles table
    e DROP TABLE Roles;
    e CREATE TABLE Roles (
    PrincipalID	VARCHAR(64) PRIMARY KEY,
    Role	VARCHAR(64),
    RoleGroup VARCHAR(64) );

    ; put some initial data in the table
    e INSERT INTO Roles VALUES ("scott", "Echo", "");
    e INSERT INTO Roles VALUES ("scott", "caller_scott", "CallerPrincipal");
    e INSERT INTO Roles VALUES ("stark", "Java", "");
    e INSERT INTO Roles VALUES ("stark", "Coder", "");
    e INSERT INTO Roles VALUES ("stark", "caller_stark", "CallerPrincipal");

    c close;
    */
   public void testJdbc() throws Exception
   {
      log.info("testJdbc");
      
      Connection conn = setupLoginTables();
      try
      {
         UsernamePasswordHandler handler = new UsernamePasswordHandler("stark", "javaman".toCharArray());
         LoginContext lc = new LoginContext("testJdbc", handler);
         lc.login();
         Subject subject = lc.getSubject();
         Set groups = subject.getPrincipals(Group.class);
         assertTrue("Principals contains stark", subject.getPrincipals().contains(new SimplePrincipal("stark")));
         assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
         Group roles = findRolesGroup(groups);
         assertTrue("Java is a role", roles.isMember(new SimplePrincipal("Java")));
         assertTrue("Coder is a role", roles.isMember(new SimplePrincipal("Coder")));

         lc.logout();
      }
      finally
      {
         conn.close();
      }
   }

   public void testControlFlags() throws Exception
   {
      log.info("testControlFlags");
      
      Connection conn = setupLoginTables();
      try
      {
         Configuration cfg = Configuration.getConfiguration();
         AppConfigurationEntry[] ace = cfg.getAppConfigurationEntry("testControlFlags");
         for(int n = 0; n < ace.length; n ++)
         {
            assertTrue("testControlFlags flag==SUFFICIENT",
               ace[n].getControlFlag() == AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT);
            log.info(ace[n].getControlFlag());
         }

         /* Test that the UsersRolesLoginModule is sufficient to login. Only the
          users.properties file has a jduke=theduke username to password mapping,
          and the DatabaseServerLoginModule will fail.
         */
         UsernamePasswordHandler handler = new UsernamePasswordHandler("jduke", "theduke".toCharArray());
         LoginContext lc = new LoginContext("testControlFlags", handler);
         lc.login();
         Subject subject = lc.getSubject();
         Set groups = subject.getPrincipals(Group.class);
         assertTrue("Principals contains jduke", subject.getPrincipals().contains(new SimplePrincipal("jduke")));
         assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
         Group roles = findRolesGroup(groups);
         // Only the roles from the DatabaseServerLoginModule should exist
         assertTrue("Role1 is a role", roles.isMember(new SimplePrincipal("Role1")));
         assertTrue("Role2 is a role", roles.isMember(new SimplePrincipal("Role2")));
         assertTrue("Role3 is NOT a role", !roles.isMember(new SimplePrincipal("Role3")));
         assertTrue("Role4 is NOT a role", !roles.isMember(new SimplePrincipal("Role4")));
         lc.logout();

         /* Test that the DatabaseServerLoginModule is sufficient to login. Only the
           Principals table has a jduke=jduke username to password mapping, and
           the UsersRolesLoginModule will fail.
         */
         handler = new UsernamePasswordHandler("jduke", "jduke".toCharArray());
         lc = new LoginContext("testControlFlags", handler);
         lc.login();
         subject = lc.getSubject();
         groups = subject.getPrincipals(Group.class);
         assertTrue("Principals contains jduke", subject.getPrincipals().contains(new SimplePrincipal("jduke")));
         assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
         roles = (Group) groups.iterator().next();
         Enumeration iter = roles.members();
         while( iter.hasMoreElements() )
            log.debug(iter.nextElement());
         // Only the roles from the DatabaseServerLoginModule should exist
         assertTrue("Role1 is NOT a role", !roles.isMember(new SimplePrincipal("Role1")));
         assertTrue("Role2 is NOT a role", !roles.isMember(new SimplePrincipal("Role2")));
         assertTrue("Role3 is a role", roles.isMember(new SimplePrincipal("Role3")));
         assertTrue("Role4 is a role", roles.isMember(new SimplePrincipal("Role4")));
         lc.logout();
      }
      finally
      {
         conn.close();
      }
   }

   public void testCertLogin() throws Exception
   {
      log.info("testCertLogin");
      InitialContext ctx = new InitialContext();
      ctx.rebind("testCertLogin", new TestSecurityDomain());

      KeyStore store = KeyStore.getInstance("JKS");
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      URL resURL = loader.getResource("security/tst.keystore");
      store.load(resURL.openStream(), "unit-tests".toCharArray());
      X509Certificate cert = (X509Certificate) store.getCertificate("unit-tests");
      SimplePrincipal x509 = new SimplePrincipal("unit-tests");
      SecurityAssociationHandler handler = new SecurityAssociationHandler(x509, cert);
      LoginContext lc = new LoginContext("testCertLogin", handler);
      lc.login();
      Subject subject = lc.getSubject();
      assertTrue("Principals contains unit-tests", subject.getPrincipals().contains(new SimplePrincipal("unit-tests")));
      assertTrue("Principals contains x509cert",
         subject.getPublicCredentials().contains(cert));
   }

   public void testCertRoles() throws Exception
   {
      log.info("testCertRoles");
      InitialContext ctx = new InitialContext();
      ctx.rebind("testCertRoles", new TestSecurityDomain());

      KeyStore store = KeyStore.getInstance("JKS");
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      URL resURL = loader.getResource("security/tst.keystore");
      store.load(resURL.openStream(), "unit-tests".toCharArray());
      X509Certificate cert = (X509Certificate) store.getCertificate("unit-tests");
      SimplePrincipal x509 = new SimplePrincipal("unit-tests");
      SecurityAssociationHandler handler = new SecurityAssociationHandler(x509, cert);
      LoginContext lc = new LoginContext("testCertRoles", handler);
      lc.login();
      Subject subject = lc.getSubject();
      Set groups = subject.getPrincipals(Group.class);
      assertTrue("Principals contains unit-tests", subject.getPrincipals().contains(new SimplePrincipal("unit-tests")));
      assertTrue("Principals contains Roles", groups.contains(new SimplePrincipal("Roles")));
      assertTrue("Principals contains x509cert",
         subject.getPublicCredentials().contains(cert));
      Group roles = (Group) groups.iterator().next();
      Iterator groupsIter = groups.iterator();
      while( groupsIter.hasNext() )
      {
         roles = (Group) groupsIter.next();
         if( roles.getName().equals("Roles") )
         {
            assertTrue("CertUser is a role", roles.isMember(new SimplePrincipal("CertUser")));
            assertTrue("Java is NOT a role", roles.isMember(new SimplePrincipal("Java")) == false);
            assertTrue("Coder is NOT a role", roles.isMember(new SimplePrincipal("Coder")) == false);
         }
         else if( roles.getName().equals("CallerPrincipal") )
         {
            log.info("CallerPrincipal is "+roles.members().nextElement());
            boolean isMember = roles.isMember(new SimplePrincipal("callerX509"));
            assertTrue("CallerPrincipal is callerX509", isMember);
         }
      }
      lc.logout();

   }

   // Method to find the group named "Roles" in the given Set of groups.
   private Group findRolesGroup(Set groups)
   {
       // Find the "Roles" group:
       Iterator groupsIter = groups.iterator();
       Group roles = null;
       while (groupsIter.hasNext()) {
      	 roles = (Group)groupsIter.next();
      	 if (roles.getName().equals("Roles"))
      		 break;
       }
       return roles;
   }

   private Connection setupLoginTables() throws Exception
   {
      Class.forName("org.hsqldb.jdbcDriver");
      // Create a DataSource binding
      TestDS ds = new TestDS();
      InitialContext ctx = new InitialContext();
      ctx.rebind("testJdbc", ds);

      // Start database and setup tables
      Connection conn = ds.getConnection("sa", "");
      Statement statement = conn.createStatement();
      createPrincipalsTable(statement);
      createRolesTable(statement);
      statement.close();
      
      // no connection closing, if hsql is in process
      // the database is disposed
      return conn;
   }

   private void createPrincipalsTable(Statement statement) throws SQLException
   {
      try
      {
         statement.execute("DROP TABLE Principals");
      }
      catch(SQLException e)
      {
         // Ok, assume table does not exist
      }
      boolean result = statement.execute("CREATE TABLE Principals ("
      + "PrincipalID VARCHAR(64) PRIMARY KEY,"
      + "Password VARCHAR(64) )"
      );
      log.info("Created Principals table, result="+result);
      result = statement.execute("INSERT INTO Principals VALUES ('scott', 'echoman')");
      log.info("INSERT INTO Principals VALUES ('scott', 'echoman'), result="+result);
      result = statement.execute("INSERT INTO Principals VALUES ('stark', 'javaman')");
      log.info("INSERT INTO Principals VALUES ('stark', 'javaman'), result="+result);
      // This differs from the users.properties jduke settings
      result = statement.execute("INSERT INTO Principals VALUES ('jduke', 'jduke')");
      log.info("INSERT INTO Principals VALUES ('jduke', 'jduke'), result="+result);
   }

   private void createRolesTable(Statement statement) throws SQLException
   {
      try
      {
         statement.execute("DROP TABLE Roles");
      }
      catch(SQLException e)
      {
         // Ok, assume table does not exist
      }
      boolean result = statement.execute("CREATE TABLE Roles ("
      + "PrincipalID	VARCHAR(64),"
      + "Role	VARCHAR(64),"
      + "RoleGroup VARCHAR(64) )"
      );
      log.info("Created Roles table, result="+result);
      result = statement.execute("INSERT INTO Roles VALUES ('scott', 'Echo', 'Roles')");
      log.info("INSERT INTO Roles VALUES ('scott', 'Echo', 'Roles'), result="+result);
      result = statement.execute("INSERT INTO Roles VALUES ('scott', 'callerScott', 'CallerPrincipal')");
      log.info("INSERT INTO Roles VALUES ('scott', 'callerScott', 'CallerPrincipal'), result="+result);
      result = statement.execute("INSERT INTO Roles VALUES ('stark', 'Java', 'Roles')");
      log.info("INSERT INTO Roles VALUES ('stark', 'Java', 'Roles'), result="+result);
      result = statement.execute("INSERT INTO Roles VALUES ('stark', 'Coder', 'Roles')");
      log.info("INSERT INTO Roles VALUES ('stark', 'Coder', 'Roles'), result="+result);
      result = statement.execute("INSERT INTO Roles VALUES ('stark', 'callerStark', 'CallerPrincipal')");
      log.info("INSERT INTO Roles VALUES ('stark', 'callerStark', 'CallerPrincipal'), result="+result);
      result = statement.execute("INSERT INTO Roles VALUES ('jduke', 'Role3', 'Roles')");
      log.info("INSERT INTO Roles VALUES ('jduke', 'Role3', 'Roles'), result="+result);
      result = statement.execute("INSERT INTO Roles VALUES ('jduke', 'Role4', 'Roles')");
      log.info("INSERT INTO Roles VALUES ('jduke', 'Role4', 'Roles'), result="+result);
   }
   
   public static void main(java.lang.String[] args)
   {
      // Print the location of the users.properties resource
      java.net.URL users = LoginModulesUnitTestCase.class.getResource("/users.properties");
      System.out.println("users.properties is here: "+users);
      TestSuite suite = new TestSuite(LoginModulesUnitTestCase.class);
      junit.textui.TestRunner.run(suite);
   }

}
