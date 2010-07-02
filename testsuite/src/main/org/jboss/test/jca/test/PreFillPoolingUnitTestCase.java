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
package org.jboss.test.jca.test;

import java.net.URL;
import java.sql.Connection;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.InitialContext;
import javax.resource.spi.ManagedConnectionFactory;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;

import junit.framework.Test;

import org.jboss.logging.Logger;
import org.jboss.mx.util.MBeanServerLocator;
import org.jboss.mx.util.ObjectNameFactory;
import org.jboss.resource.connectionmanager.BaseConnectionManager2;
import org.jboss.resource.connectionmanager.CachedConnectionManager;
import org.jboss.resource.connectionmanager.CachedConnectionManagerMBean;
import org.jboss.resource.connectionmanager.InternalManagedConnectionPool;
import org.jboss.resource.connectionmanager.JBossManagedConnectionPool;
import org.jboss.resource.connectionmanager.ManagedConnectionPool;
import org.jboss.resource.connectionmanager.NoTxConnectionManager;
import org.jboss.resource.connectionmanager.PreFillPoolSupport;
import org.jboss.test.JBossTestCase;
import org.jboss.test.jca.adapter.TestConnectionRequestInfo;
import org.jboss.test.jca.adapter.TestManagedConnectionFactory;
import org.jboss.test.util.ejb.EJBTestCase;
import org.jboss.tm.TransactionManagerLocator;

/**
 * A PrefillDeploymentUnitTestCase.
 * 
 * @author <a href="weston.price@jboss.com">Weston Price</a>
 * @version $Revision: 92695 $
 */
public class PreFillPoolingUnitTestCase extends EJBTestCase
{
   Logger log = Logger.getLogger(PreFillPoolingUnitTestCase.class);
  
   private static final ObjectName PREFILL_POOL = ObjectNameFactory.create("jboss.jca:name=PreFillDS,service=ManagedConnectionPool");
   private static final ObjectName NO_PREFILL_POOL = ObjectNameFactory.create("jboss.jca:name=NoPreFillDS,service=ManagedConnectionPool");
   private static final ObjectName NO_ELEMENT_PREFILL_POOL = ObjectNameFactory.create("jboss.jca:name=NoElementPreFillDS,service=ManagedConnectionPool");
   private static final ObjectName CRI_PREFILL_POOL = ObjectNameFactory.create("jboss.jca:name=CriPreFillDS,service=ManagedConnectionPool");
   private static final ObjectName INVALD_PREFILL_POOL = ObjectNameFactory.create("jboss.jca:name=InvalidPreFillDS,service=ManagedConnectionPool");

   private static final String CONN_NAME = "ConnectionCount";
   private static final String MIN_NAME = "MinSize";
   private static final String FLUSH_METHOD_NAME = "flush";
   private static final String PREFILL = "PreFill";
   
   //For non managed prefill testing.
   static TransactionManager tm;
   static TestConnectionRequestInfo cri1 = new TestConnectionRequestInfo("info1");
   static TestConnectionRequestInfo cri2 = new TestConnectionRequestInfo("info2");

   public PreFillPoolingUnitTestCase(String name)
   {
      super(name);
   }

   @Override
   public void setUp()
   {
      tm = TransactionManagerLocator.getInstance().locate();
   }

   protected MBeanServer getServer()
   {
      return MBeanServerLocator.locateJBoss();
   }
   
   /**
    * Test basic prefill support on a OnePool.
    * 
    * PoolType: OnePool
    * Deployed *-ds.xml: prefill-ds.xml
    * Conditions:
    * Prefill should be set to true
    * The pool should be prefilled and the connection count should equal the minimum count.
    * 
    * @throws Exception
    */
   public void testDeployPreFillPool() throws Exception
   {
      Boolean prefill = (Boolean)getServer().getAttribute(PREFILL_POOL, PREFILL);      
      assertTrue(prefill.booleanValue());
      
      Integer count = (Integer)getServer().getAttribute(PREFILL_POOL, CONN_NAME);
      Integer min = (Integer)getServer().getAttribute(PREFILL_POOL, MIN_NAME);
      
      assertTrue("Minimun count and connection count should be the same.", count.intValue() == min.intValue());
      
      
   }
   
   /**
    * Test prefill support after flushing a pool.
    * PoolType: OnePool
    * Deployed *-ds.xml: prefill-ds.xml
    * 
    * Conditions:
    * Prefill should be set to true
    * After flush, the pool should be prefilled and the connection count should equal the minimum count.
    * 
    * @throws Exception
    */
   public void testPreFillFlush() throws Exception
   {

      Boolean prefill = (Boolean)getServer().getAttribute(PREFILL_POOL, PREFILL);      
      assertTrue(prefill.booleanValue());

      Integer count = (Integer)getServer().getAttribute(PREFILL_POOL, CONN_NAME);
      Integer min = (Integer)getServer().getAttribute(PREFILL_POOL, MIN_NAME);
      assertTrue("Prefill is set to true. Minimun count and connection count should be the same.", count.intValue() == min.intValue());

      Attribute flush = new Attribute(PREFILL, Boolean.FALSE);
      getServer().setAttribute(PREFILL_POOL, flush);
      getServer().invoke(PREFILL_POOL, FLUSH_METHOD_NAME, new Object[0], new String[0]);
      
      count = (Integer)getServer().getAttribute(PREFILL_POOL, CONN_NAME);
      min = (Integer)getServer().getAttribute(PREFILL_POOL, MIN_NAME);
      assertTrue("Pool was flushed and prefill set to false. Minimum count and connection count should not be equal.", count.intValue() != min.intValue());

      flush = new Attribute(PREFILL, Boolean.TRUE);
      getServer().setAttribute(PREFILL_POOL, flush);
      getServer().invoke(PREFILL_POOL, FLUSH_METHOD_NAME, new Object[0], new String[0]);
      
      //Let pool filler run
      Thread.sleep(5000);
      
      count = (Integer)getServer().getAttribute(PREFILL_POOL, CONN_NAME);
      min = (Integer)getServer().getAttribute(PREFILL_POOL, MIN_NAME);
      assertTrue("Prefill is set to true. Minimun count and connection count should be the same.", count.intValue() == min.intValue());
        
      
   }
   
   /**
    * 
    * Test basic prefill support on a non prefilled pool.
    * 
    * @throws Exception
    */
   public void testDeployNoPreFillPool() throws Exception
   {
      Boolean prefill = (Boolean)getServer().getAttribute(NO_PREFILL_POOL, PREFILL);      
      assertFalse("Prefill is set to false.", prefill.booleanValue());
      
      Integer count = (Integer)getServer().getAttribute(NO_PREFILL_POOL, CONN_NAME);
      Integer min = (Integer)getServer().getAttribute(NO_PREFILL_POOL, MIN_NAME);
      assertTrue("Prefill is set to false. Min count and connection count should not be equal", count.intValue() != min.intValue());
      
      
   }
   
   /**
    * Test prefill pool where <prefill> element is explicitly false.
    * 
    * @throws Exception
    */
   public void testDeployElementNoPreFillPool() throws Exception
   {
      Boolean prefill = (Boolean)getServer().getAttribute(NO_ELEMENT_PREFILL_POOL, PREFILL);      
      assertFalse("Prefill was explicitly set ot false. Prefill should be false.", prefill.booleanValue());
      
      Integer count = (Integer)getServer().getAttribute(NO_ELEMENT_PREFILL_POOL, CONN_NAME);
      Integer min = (Integer)getServer().getAttribute(NO_ELEMENT_PREFILL_POOL, MIN_NAME);
      
      assertTrue("Prefill is set to false. Min count and connection count should not be equal", count.intValue() != min.intValue());
      
   }
   
   /**
    * FIXME Comment this
    * 
    * @throws Exception
    */
   public void testInvalidPreFillPool() throws Exception
   {
      Boolean prefill = (Boolean)getServer().getAttribute(INVALD_PREFILL_POOL, PREFILL);      
      assertTrue(prefill.booleanValue());

      Integer count = (Integer)getServer().getAttribute(INVALD_PREFILL_POOL, CONN_NAME);
      Integer min = (Integer)getServer().getAttribute(INVALD_PREFILL_POOL, MIN_NAME);
      assertTrue("Non supporting prefill pool is used. Min count and connection count should not be equal", count.intValue() != min.intValue());
      
   }
   
   /**
    * FIXME Comment this
    * 
    * @throws Exception
    */
   public void testPoolByCriPreFill() throws Exception
   {
      Boolean prefill = (Boolean)getServer().getAttribute(CRI_PREFILL_POOL, PREFILL);      
      assertTrue(prefill.booleanValue());

      Integer count = (Integer)getServer().getAttribute(CRI_PREFILL_POOL, CONN_NAME);
      Integer min = (Integer)getServer().getAttribute(CRI_PREFILL_POOL, MIN_NAME);
      Integer initialCount = count;
      Integer initialMin = min;
      assertTrue(count.intValue() != min.intValue());
      
      InitialContext ctx = new InitialContext();
      DataSource ds = (DataSource)ctx.lookup("java:/CriPreFillDS");
      Connection conn = ds.getConnection("sa", "");
      
      Thread.sleep(30000);
      count = (Integer)getServer().getAttribute(CRI_PREFILL_POOL, CONN_NAME);
      min = (Integer)getServer().getAttribute(CRI_PREFILL_POOL, MIN_NAME);
      
      assertTrue("Count=" + count + ", Min=" + min +", initial Count before getting connection =" + initialCount +
         ",  initial Min before getting connection = "+ initialMin, count.intValue() == min.intValue());
      
      conn.close();
      
      //Now we explictly set prefill, flush
      Attribute flush = new Attribute(PREFILL, Boolean.TRUE);
      getServer().setAttribute(CRI_PREFILL_POOL, flush);
      getServer().invoke(CRI_PREFILL_POOL, FLUSH_METHOD_NAME, new Object[0], new String[0]);
      
      count = (Integer)getServer().getAttribute(CRI_PREFILL_POOL, CONN_NAME);
      min = (Integer)getServer().getAttribute(CRI_PREFILL_POOL, MIN_NAME);
      
      assertTrue(count.intValue() != min.intValue());
      
      conn = ds.getConnection("sa", "");
      Thread.currentThread().sleep(20000);
      
      count = (Integer)getServer().getAttribute(CRI_PREFILL_POOL, CONN_NAME);
      min = (Integer)getServer().getAttribute(CRI_PREFILL_POOL, MIN_NAME);

      assertTrue(count.intValue() == min.intValue());
      conn.close();
   }
   
   
   public void testNonPreFillSupportingPoolPreFill() throws Exception{
      
      int minSize = 3;
      int maxSize = 5;
      
      ManagedConnectionPool mcp = getOnePoolPrefill(minSize, maxSize, true);
      BaseConnectionManager2 cm = getNoTxCM(mcp);      
      PreFillPoolSupport support = (PreFillPoolSupport)mcp;
      support.prefill(null, null, false);
      
      //Let pool fill
      Thread.sleep(10000);
      
      int currentSize = mcp.getConnectionCount();
      assertTrue(currentSize == minSize);
      
      
   }

   public void testNoPreFillSupportingPool() throws Exception{
   
      int minSize = 3;
      int maxSize = 5;
      
      ManagedConnectionPool mcp = getOnePoolPrefill(minSize, maxSize, false);
      BaseConnectionManager2 cm = getNoTxCM(mcp);      
      PreFillPoolSupport support = (PreFillPoolSupport)mcp;
      support.prefill(null, null, false);
      
      //Let pool fill
      Thread.sleep(10000);
      
      int currentSize = mcp.getConnectionCount();
      assertTrue(currentSize == 0);
      assertTrue(currentSize != minSize);
      
      
   }

   public void testPreFillSupportingPool() throws Exception{
   
      int minSize = 3;
      int maxSize = 5;
      
      ManagedConnectionPool mcp = getOnePoolPrefill(minSize, maxSize, true);
      BaseConnectionManager2 cm = getNoTxCM(mcp);      
      PreFillPoolSupport support = (PreFillPoolSupport)mcp;
      support.prefill(null, null, false);
      
      //Let pool fill
      Thread.sleep(10000);
      
      int currentSize = mcp.getConnectionCount();
      assertTrue(currentSize == minSize);
      
   }

   private ManagedConnectionPool getOnePoolPrefill(int minSize, int maxSize, boolean prefill){
      
      InternalManagedConnectionPool.PoolParams pp = new InternalManagedConnectionPool.PoolParams();
      pp.minSize = minSize;
      pp.maxSize = maxSize;
      pp.blockingTimeout = 10000;
      pp.idleTimeout = 0;
      pp.prefill = prefill;
      ManagedConnectionFactory mcf = new TestManagedConnectionFactory();
      ManagedConnectionPool poolingStrategy = new JBossManagedConnectionPool.OnePool(mcf, pp, false, log);
      return poolingStrategy;
      
   }

   private BaseConnectionManager2 getNoTxCM(ManagedConnectionPool poolingStrategy) throws Exception
   {
      CachedConnectionManager ccm = (CachedConnectionManager) getServer().getAttribute(CachedConnectionManagerMBean.OBJECT_NAME, "Instance");
      BaseConnectionManager2 cm = new NoTxConnectionManager(ccm, poolingStrategy);
      poolingStrategy.setConnectionListenerFactory(cm);
      return cm;
   }

   public static Test suite() throws Exception
   {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      URL resURL = loader.getResource("jca/prefill/prefill-ds.xml");
      Test t1 = JBossTestCase.getDeploySetup(PreFillPoolingUnitTestCase.class, resURL.toString());
      
      resURL = loader.getResource("jca/prefill/no-prefill-ds.xml");
      
      Test t2 = JBossTestCase.getDeploySetup(t1, resURL.toString());
      resURL = loader.getResource("jca/prefill/no-element-prefill-ds.xml");
      
      Test t3 = JBossTestCase.getDeploySetup(t2, resURL.toString());
      
      resURL = loader.getResource("jca/prefill/cri-prefill-ds.xml");
      
      Test t4 = JBossTestCase.getDeploySetup(t3, resURL.toString());
      resURL = loader.getResource("jca/prefill/invalid-prefill-ds.xml");
      
      Test t5 = JBossTestCase.getDeploySetup(t4, resURL.toString());
      
      return JBossTestCase.getDeploySetup(t5, "jca-tests.jar");
   }
   
   
}
