package org.jboss.test.cluster.lock;

import static org.easymock.EasyMock.and;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.makeThreadSafe;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.resetToNice;
import static org.easymock.EasyMock.resetToStrict;
import static org.easymock.EasyMock.same;
import static org.easymock.EasyMock.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IArgumentMatcher;
import org.jboss.ha.framework.interfaces.ClusterNode;
import org.jboss.ha.framework.interfaces.HAPartition;
import org.jboss.ha.framework.server.ClusterNodeImpl;
import org.jboss.ha.framework.server.lock.AbstractClusterLockSupport;
import org.jboss.ha.framework.server.lock.LocalLockHandler;
import org.jboss.ha.framework.server.lock.RemoteLockResponse;
import org.jboss.ha.framework.server.lock.AbstractClusterLockSupport.RpcTarget;
import org.jgroups.stack.IpAddress;

public abstract class ClusteredLockManagerTestBase<T extends AbstractClusterLockSupport> extends TestCase
{

   protected ClusterNode node1;
   protected ClusterNode node2;
   protected ClusterNode node3;

   public static Object[] eqLockParams(ClusterNode node, long timeout)
   {
      EasyMock.reportMatcher(new LockParamsMatcher(node, timeout));
      return null;
   }

   protected static class LockParamsMatcher implements IArgumentMatcher
   {
      private final ClusterNode node;
      private final long timeout;
      
      LockParamsMatcher(ClusterNode node, long timeout)
      {
         this.node = node;
         this.timeout = timeout;
      }
      
      public void appendTo(StringBuffer buffer)
      {
         buffer.append("eqRemoteLockParams({\"test\",");
         buffer.append(node);
         buffer.append(',');
         buffer.append(timeout);
         buffer.append("})");         
      }

      public boolean matches(Object arg)
      {
         if (arg instanceof Object[])
         {
            Object[] args = (Object[]) arg;
            if (args.length == 3)
            {
               if ("test".equals(args[0]) && node.equals(args[1])
                     && args[2] instanceof Long)
               {
                  long l = ((Long) args[2]).longValue();
                  return l >= 0 && l <= timeout;
               }
            }
         }
         return false;
      }
      
   }

   public ClusteredLockManagerTestBase()
   {
      super();
   }

   public ClusteredLockManagerTestBase(String name)
   {
      super(name);
   }

   protected void setUp() throws Exception
   {
      super.setUp();
      node1 = new ClusterNodeImpl(new IpAddress("localhost", 1));
      node2 = new ClusterNodeImpl(new IpAddress("localhost", 2));
      node3 = new ClusterNodeImpl(new IpAddress("localhost", 3));
   }

   protected void tearDown() throws Exception
   {
      super.tearDown();
   }

   public void testConstructor() throws Exception
   {
      HAPartition haPartition = createNiceMock(HAPartition.class);      
      LocalLockHandler handler = createNiceMock(LocalLockHandler.class);
      
      try
      {
         createClusteredLockManager(null, haPartition, handler);
         fail("Null serviceHAName should prevent construction");
      }
      catch (IllegalArgumentException good) {}
      
      try
      {
         createClusteredLockManager("test", null, handler);
         fail("Null HAPartition should prevent construction");
      }
      catch (IllegalArgumentException good) {}     
      
      try
      {
         createClusteredLockManager("test", haPartition, null);
         fail("Null LocalLockHandler should prevent construction");
      }
      catch (IllegalArgumentException good) {}  
      
      expect(haPartition.getClusterNode()).andReturn(node1);
      expect(haPartition.getPartitionName()).andReturn("TestPartition");
      
      replay(haPartition);
      replay(handler);
      
      T testee = createClusteredLockManager("test", haPartition, handler);
      
      assertEquals("test", testee.getServiceHAName());
      assertEquals("TestPartition", testee.getPartitionName());
   }

   public void testStart() throws Exception
   {
      HAPartition haPartition = createNiceMock(HAPartition.class);      
      LocalLockHandler handler = createNiceMock(LocalLockHandler.class);       
      expect(haPartition.getClusterNode()).andReturn(node1);
      expect(haPartition.getPartitionName()).andReturn("TestPartition");
      
      replay(haPartition);
      replay(handler);      
      
      T testee = createClusteredLockManager("test", haPartition, handler);
      
      try
      {
         testee.lock("id", 1000);
         fail("Call to lock() should fail if not started");
      }
      catch (IllegalStateException good) {}
      
      try
      {
         testee.unlock("id");
         fail("Call to unlock() should fail if not started");
      }
      catch (IllegalStateException good) {}
      
      reset(haPartition);
      
      assertEquals("Current view is empty when unstarted", 0, testee.getCurrentView().size());
      
      haPartition.registerRPCHandler(eq("test"), isA(RpcTarget.class));
      haPartition.registerMembershipListener(testee);
      expect(haPartition.getClusterNodes()).andReturn(new ClusterNode[]{node1});
      replay(haPartition);
      
      testee.start();
      
      verify(haPartition);
      
      assertEquals("Current view is correct", 1, testee.getCurrentView().size());
      assertTrue(testee.getCurrentView().contains(node1));
      
   }

   public void testStop() throws Exception
   {
      TesteeSet<T> testeeSet = getTesteeSet(node1, 0, 1);
      T testee = testeeSet.impl;
      HAPartition haPartition = testee.getPartition();      
      
      reset(haPartition);
   
      haPartition.unregisterMembershipListener(testee);
      haPartition.unregisterRPCHandler(eq("test"), same(testeeSet.target));
      
      replay(haPartition);
      
      testee.stop();
      
      verify(haPartition);
      
      assertEquals("Current view is empty when stopped", 0, testee.getCurrentView().size());
      
      try
      {
         testee.lock("id", 1000);
         fail("Call to lock() should fail if stopped");
      }
      catch (IllegalStateException good) {}
      
      try
      {
         testee.unlock("id");
         fail("Call to unlock() should fail if stopped");
      }
      catch (IllegalStateException good) {}
      
   }

   public void testGetMembers() throws Exception
   {
      TesteeSet<T> testeeSet = getTesteeSet(node1, 1, 2);
      T testee = testeeSet.impl;
      
      List<ClusterNode> members = testee.getCurrentView();
      assertEquals(2, members.size());
      assertEquals(node1, members.get(1));
      
      ClusterNode dead = members.get(0);
      assertFalse(node1.equals(dead));
      
      Vector<ClusterNode> newView = getView(node1, 0, 3);
      newView.remove(dead);
      
      Vector<ClusterNode> addedMembers = new Vector<ClusterNode>(newView);
      addedMembers.removeAll(members);
      
      Vector<ClusterNode> deadMembers = new Vector<ClusterNode>();
      deadMembers.add(dead);
      
      testee.membershipChanged(deadMembers, addedMembers, newView);
      
      members = testee.getCurrentView();
      assertEquals(2, members.size());
      assertEquals(node1, members.get(0));
      assertFalse(node1.equals(members.get(1)));
      assertFalse(members.contains(dead));
      
   }
   
   /**
    * Simple test of acquiring a cluster-wide lock in a two node cluster
    * where local-only locks are supported.
    * 
    * @throws Exception
    */
   public void testBasicClusterLock() throws Exception
   {
      basicClusterLockTest(2);
   }
   
   /**
    * Simple test of acquiring a cluster-wide lock in a cluster where the
    * caller is the only member and where local-only locks are supported.
    * 
    * @throws Exception
    */
   public void testStandaloneClusterLock() throws Exception
   {
      basicClusterLockTest(1);
   }

   private void basicClusterLockTest(int viewSize) throws Exception
   {
      int viewPos = viewSize == 1 ? 0 : 1;
      TesteeSet<T> testeeSet = getTesteeSet(node1, viewPos, viewSize);
      AbstractClusterLockSupport testee = testeeSet.impl;
      HAPartition partition = testee.getPartition();
      LocalLockHandler handler = testee.getLocalHandler();
      
      resetToStrict(partition);
      resetToStrict(handler);
      
      ArrayList<RemoteLockResponse> rspList = new ArrayList<RemoteLockResponse>();
      for (int i = 0; i < viewSize - 1; i++)
      {
         rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.OK));
      }
      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("remoteLock"), 
                                           eqLockParams(node1, 200000), 
                                           aryEq(AbstractClusterLockSupport.REMOTE_LOCK_TYPES), 
                                           eq(true))).andReturn(rspList);
      
      handler.lockFromCluster(eq("test"), eq(node1), anyLong());
      
      replay(partition);
      replay(handler);
      
      assertTrue(testee.lock("test", 200000));
      
      verify(partition);
      verify(handler);
      
   }
   
   public void testRemoteRejectionFromSuperiorCaller() throws Exception
   {
      TesteeSet<T> testeeSet = getTesteeSet(node1, 1, 3);
      AbstractClusterLockSupport testee = testeeSet.impl;
      HAPartition partition = testee.getPartition();
      LocalLockHandler handler = testee.getLocalHandler();
      
      resetToNice(partition);
      resetToStrict(handler);
      
      ClusterNode superior = testee.getCurrentView().get(0);
      
      ArrayList<RemoteLockResponse> rspList = new ArrayList<RemoteLockResponse>();
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.OK));
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.REJECT, superior));
      
      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("remoteLock"), 
                                           eqLockParams(node1, 200000), 
                                           aryEq(AbstractClusterLockSupport.REMOTE_LOCK_TYPES), 
                                           eq(true))).andReturn(rspList).atLeastOnce();
      
      replay(partition);
      replay(handler);
      
      assertFalse(testee.lock("test", 50));
      
      verify(partition);
      verify(handler);
      
   }
   
   public void testRemoteRejectionFromInferiorCaller() throws Exception
   {
      TesteeSet<T> testeeSet = getTesteeSet(node1, 1, 3);
      AbstractClusterLockSupport testee = testeeSet.impl;
      HAPartition partition = testee.getPartition();
      LocalLockHandler handler = testee.getLocalHandler();
      
      resetToStrict(partition);
      resetToStrict(handler);
      
      ClusterNode inferior = testee.getCurrentView().get(2);
      
      ArrayList<RemoteLockResponse> rspList = new ArrayList<RemoteLockResponse>();
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.OK));
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.REJECT, inferior));
      
      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("remoteLock"), 
                                           eqLockParams(node1, 200000), 
                                           aryEq(AbstractClusterLockSupport.REMOTE_LOCK_TYPES), 
                                           eq(true))).andReturn(rspList);

      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("releaseRemoteLock"), 
                                           aryEq(new Object[]{"test", node1}), 
                                           aryEq(AbstractClusterLockSupport.RELEASE_REMOTE_LOCK_TYPES), 
                                           eq(true))).andReturn(rspList);
      
      rspList = new ArrayList<RemoteLockResponse>();
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.OK));
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.OK));
      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("remoteLock"), 
                                           eqLockParams(node1, 200000), 
                                           aryEq(AbstractClusterLockSupport.REMOTE_LOCK_TYPES), 
                                           eq(true))).andReturn(rspList);
      
      handler.lockFromCluster(eq("test"), eq(node1), anyLong());      
      expectLastCall().atLeastOnce();
      
      replay(partition);
      replay(handler);
      
      assertTrue(testee.lock("test", 50));
      
      verify(partition);
      verify(handler);
      
   }
   
   public void testLocalLockingStateRejectsSuperiorRemoteCaller() throws Exception
   { 
      TesteeSet<T> testeeSet = getTesteeSet(node1, 1, 3);
      T testee = testeeSet.impl;
      HAPartition partition = testee.getPartition();
      LocalLockHandler handler = testee.getLocalHandler();
      final RpcTarget target = testeeSet.target;
      
      ClusterNode superiorCaller = testee.getCurrentView().get(0);
      assertFalse(node1.equals(superiorCaller));

      resetToStrict(partition);    
      makeThreadSafe(partition, true);
      resetToStrict(handler);   
      makeThreadSafe(handler, true);
      
      ArrayList<RemoteLockResponse> rspList = new ArrayList<RemoteLockResponse>();
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.OK));
      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("remoteLock"), 
                                           eqLockParams(node1, 200000), 
                                           aryEq(AbstractClusterLockSupport.REMOTE_LOCK_TYPES), 
                                           eq(true))).andReturn(rspList);
      
      // When caller 1 invokes, block before giving response 
      CountDownLatch answerAwaitLatch = new CountDownLatch(1);
      CountDownLatch answerStartLatch = new CountDownLatch(1);
      CountDownLatch answerDoneLatch = new CountDownLatch(1);
      BlockingAnswer<Boolean> caller1Answer = new BlockingAnswer<Boolean>(Boolean.TRUE, answerAwaitLatch, answerStartLatch, null);
      handler.lockFromCluster(eq("test"), eq(node1), anyLong());
      expectLastCall().andAnswer(caller1Answer);
      
      replay(partition);
      replay(handler);
      
      LocalLockCaller winner = new LocalLockCaller(testee, null, null, answerDoneLatch, 500);
      
      Thread t1 = new Thread(winner);
      t1.setDaemon(true);
      
      try
      {
         t1.start();         
         assertTrue(answerStartLatch.await(500, TimeUnit.SECONDS));
         // t1 should now be blocking in caller1Answer
         
         RemoteLockResponse rsp = target.remoteLock("test", superiorCaller, 1);
         assertEquals(RemoteLockResponse.Flag.REJECT, rsp.flag);
         assertEquals(node1, rsp.holder);
         
         // release t1
         answerAwaitLatch.countDown();
         
         // wait for t1 to complete
         assertTrue(answerDoneLatch.await(5, TimeUnit.SECONDS));
         
         verify(handler);
         
         rethrow("winner had an exception", winner.getException());
         
         Boolean locked = winner.getResult();         
         assertEquals(Boolean.TRUE, locked);
      }
      finally
      {
         if (t1.isAlive())
            t1.interrupt();
      }
   }
   
   public void testRemoteLockingStateAllowsSuperiorRemoteCaller() throws Exception
   {
      TesteeSet<T> testeeSet = getTesteeSet(node1, 1, 3);
      T testee = testeeSet.impl;
      HAPartition partition = testee.getPartition();
      LocalLockHandler handler = testee.getLocalHandler();
      final RpcTarget target = testeeSet.target;
      
      ClusterNode superiorCaller = testee.getCurrentView().get(0);
      assertFalse(node1.equals(superiorCaller));

      resetToNice(partition);   // nice as we may loop retrying and failing 
      makeThreadSafe(partition, true);
      resetToStrict(handler);   
      makeThreadSafe(handler, true);
      
      // When caller 1 invokes, block before giving response 
      CountDownLatch answerAwaitLatch = new CountDownLatch(1);
      CountDownLatch answerStartLatch = new CountDownLatch(1);
      
      ArrayList<RemoteLockResponse> rspList = new ArrayList<RemoteLockResponse>();
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.OK));
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.REJECT, superiorCaller));
      
      BlockingAnswer<ArrayList<RemoteLockResponse>> caller1Answer = new BlockingAnswer<ArrayList<RemoteLockResponse>>(rspList, answerAwaitLatch, answerStartLatch, null);
      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("remoteLock"), 
                                           eqLockParams(node1, 200000), 
                                           aryEq(AbstractClusterLockSupport.REMOTE_LOCK_TYPES), 
                                           eq(true))).andAnswer(caller1Answer).atLeastOnce();
      
      handler.lockFromCluster(eq("test"), eq(superiorCaller), anyLong());   
      
      expect(partition.callMethodOnCluster(eq("test"), 
            eq("releaseRemoteLock"), 
            aryEq(new Object[]{"test", node1}), 
            aryEq(AbstractClusterLockSupport.RELEASE_REMOTE_LOCK_TYPES), 
            eq(true))).andReturn(new ArrayList<Object>()).atLeastOnce();
      		
      replay(partition);
      replay(handler);

      CountDownLatch finishedLatch = new CountDownLatch(1);
      LocalLockCaller loser = new LocalLockCaller(testee, null, null, finishedLatch);
      
      Thread t1 = new Thread(loser);
      t1.setDaemon(true);
      
      try
      {
         t1.start();         
         assertTrue(answerStartLatch.await(1, TimeUnit.SECONDS));
         // t1 should now be blocking in caller1Answer
         
         RemoteLockResponse rsp = target.remoteLock("test", superiorCaller, 1);
         assertEquals(RemoteLockResponse.Flag.OK, rsp.flag);
         
         // release t1
         answerAwaitLatch.countDown();
         
         // wait for t1 to complete
         assertTrue(finishedLatch.await(5, TimeUnit.SECONDS));
         
         verify(handler);
         
         rethrow("winner had an exception", loser.getException());
         
         Boolean locked = loser.getResult();         
         assertEquals(Boolean.FALSE, locked);
      }
      finally
      {
         if (t1.isAlive())
            t1.interrupt();
      }
   }
   
   public void testRemoteLockingStateRejectsInferiorRemoteCaller() throws Exception
   {
      TesteeSet<T> testeeSet = getTesteeSet(node1, 1, 3);
      T testee = testeeSet.impl;
      HAPartition partition = testee.getPartition();
      LocalLockHandler handler = testee.getLocalHandler();
      final RpcTarget target = testeeSet.target;
      
      ClusterNode inferiorNode = testee.getCurrentView().get(2);
      assertFalse(node1.equals(inferiorNode));
      
      ClusterNode superiorNode = testee.getCurrentView().get(0);
      assertFalse(node1.equals(superiorNode));

      resetToStrict(partition);    
      makeThreadSafe(partition, true);
      resetToStrict(handler);   
      makeThreadSafe(handler, true);
      
      // When caller 1 invokes, block before giving response 
      CountDownLatch answerAwaitLatch = new CountDownLatch(1);
      CountDownLatch answerStartLatch = new CountDownLatch(1);
      
      ArrayList<RemoteLockResponse> rspList = new ArrayList<RemoteLockResponse>();
      rspList.add(new RemoteLockResponse(superiorNode, RemoteLockResponse.Flag.OK));
      rspList.add(new RemoteLockResponse(inferiorNode, RemoteLockResponse.Flag.REJECT, inferiorNode));
      
      BlockingAnswer<ArrayList<RemoteLockResponse>> caller1Answer = 
         new BlockingAnswer<ArrayList<RemoteLockResponse>>(rspList, answerAwaitLatch, answerStartLatch, null);
      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("remoteLock"), 
                                           eqLockParams(node1, 200000), 
                                           aryEq(AbstractClusterLockSupport.REMOTE_LOCK_TYPES), 
                                           eq(true))).andAnswer(caller1Answer);  
      
      expect(partition.callMethodOnCluster(eq("test"), 
            eq("releaseRemoteLock"), 
            aryEq(new Object[]{"test", node1}), 
            aryEq(AbstractClusterLockSupport.RELEASE_REMOTE_LOCK_TYPES), 
            eq(true))).andReturn(new ArrayList<Object>()); 
      
      rspList = new ArrayList<RemoteLockResponse>();
      rspList.add(new RemoteLockResponse(superiorNode, RemoteLockResponse.Flag.OK));
      rspList.add(new RemoteLockResponse(inferiorNode, RemoteLockResponse.Flag.OK));
      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("remoteLock"), 
                                           eqLockParams(node1, 200000), 
                                           aryEq(AbstractClusterLockSupport.REMOTE_LOCK_TYPES), 
                                           eq(true))).andReturn(rspList);  
      
      handler.lockFromCluster(eq("test"), eq(node1), anyLong());
      
      replay(partition);
      replay(handler);

      CountDownLatch finishedLatch = new CountDownLatch(1);
      LocalLockCaller winner = new LocalLockCaller(testee, null, null, finishedLatch);
      
      Thread t1 = new Thread(winner);
      t1.setDaemon(true);
      
      try
      {
         t1.start();         
         assertTrue(answerStartLatch.await(1, TimeUnit.SECONDS));
         // t1 should now be blocking in caller1Answer
         
         RemoteLockResponse rsp = target.remoteLock("test", inferiorNode, 1);
         assertEquals(RemoteLockResponse.Flag.REJECT, rsp.flag);
         assertEquals(node1, rsp.holder);
         
         // release t1
         answerAwaitLatch.countDown();
         
         // wait for t1 to complete
         assertTrue(finishedLatch.await(5, TimeUnit.SECONDS));
         
         verify(handler);
         
         rethrow("winner had an exception", winner.getException());
         
         Boolean locked = winner.getResult();         
         assertEquals(Boolean.TRUE, locked);
      }
      finally
      {
         if (t1.isAlive())
            t1.interrupt();
      }
   }
   
   /**
    * Local node acquires a lock; remote node tries to releasem which is ignored.
    * 
    * @throws Exception
    */
   public void testSpuriousRemoteLockReleaseIgnored() throws Exception
   {
      TesteeSet<T> testeeSet = getTesteeSet(node1, 1, 2);
      AbstractClusterLockSupport testee = testeeSet.impl;
      HAPartition partition = testee.getPartition();
      LocalLockHandler handler = testee.getLocalHandler();
      
      ClusterNode other = testee.getCurrentView().get(0);
      resetToStrict(partition);
      resetToStrict(handler);
      
      ArrayList<RemoteLockResponse> rspList = new ArrayList<RemoteLockResponse>();
      rspList.add(new RemoteLockResponse(null, RemoteLockResponse.Flag.OK));
      
      
      expect(partition.callMethodOnCluster(eq("test"), 
                                           eq("remoteLock"), 
                                           eqLockParams(node1, 200000), 
                                           aryEq(AbstractClusterLockSupport.REMOTE_LOCK_TYPES), 
                                           eq(true))).andReturn(rspList);
      
      handler.lockFromCluster(eq("test"), eq(node1), anyLong());
      
      expect(handler.getLockHolder("test")).andReturn(node1);
      
      replay(partition);
      replay(handler);
      
      assertTrue(testee.lock("test", 200000));
      testeeSet.target.releaseRemoteLock("test", other);
      
      verify(partition);
      verify(handler);
   }

   protected TesteeSet<T> getTesteeSet(ClusterNode node, int viewPos, int viewSize) throws Exception
   {
      HAPartition haPartition = createNiceMock(HAPartition.class);      
      LocalLockHandler handler = createNiceMock(LocalLockHandler.class);       
      expect(haPartition.getClusterNode()).andReturn(node);
      expect(haPartition.getPartitionName()).andReturn("TestPartition");
      
      Capture<RpcTarget>  c = new Capture<RpcTarget>();
      haPartition.registerRPCHandler(eq("test"), and(isA(RpcTarget.class), capture(c)));
      Vector<ClusterNode> view = getView(node, viewPos, viewSize);
      expect(haPartition.getClusterNodes()).andReturn(view.toArray(new ClusterNode[view.size()]));
      
      replay(haPartition);
      replay(handler);      
      
      T testee = createClusteredLockManager("test", haPartition, handler);
      
      testee.start();
      
      reset(haPartition);
      reset(handler);
      
      return new TesteeSet<T>(testee, c.getValue());     
   } 
   
   protected abstract T createClusteredLockManager(String serviceHAName, HAPartition partition, LocalLockHandler handler);
   
   private Vector<ClusterNode> getView(ClusterNode member, int viewPos, int numMembers)
   {
      Vector<ClusterNode> all = new Vector<ClusterNode>(Arrays.asList(new ClusterNode[]{node1, node2, node3}));
      all.remove(member);
      while (all.size() > numMembers - 1) // -1 'cause we'll add one in a sec
      {
         all.remove(all.size() - 1);
      }
      all.add(viewPos, member);
      
      return all;
   }
   
   protected static void rethrow(String msg, Throwable t) throws Exception
   {
      if (t != null)
      {   
         if (t instanceof AssertionError)
         {
            AssertionError rethrow = new AssertionError(msg);
            rethrow.initCause(t);
            throw rethrow;
         }
         
         throw new RuntimeException(msg, t);
      }
   }
   
   protected class TesteeSet<C extends AbstractClusterLockSupport>
   {
      public final C impl;
      public final RpcTarget target;
      
      TesteeSet(C impl, RpcTarget target)
      {
         this.impl = impl;
         this.target = target;
      }
   }
   
   /**
    * Allows EasyMock to block before returning.
    * 
    * @author Brian Stansberry
    *
    * @param <T>
    */
   protected class BlockingAnswer<C>  implements IAnswer<C>
   {
      private final C answer;
      private final Exception toThrow;
      private final CountDownLatch startLatch;
      private final CountDownLatch awaitlatch;
      private final CountDownLatch endLatch;
      private final long timeout;
      
      public BlockingAnswer(C answer, CountDownLatch awaitLatch, CountDownLatch startLatch, CountDownLatch endLatch)
      {
         this(answer, awaitLatch, 0, startLatch, endLatch);
      }
      
      public BlockingAnswer(C answer, CountDownLatch awaitLatch, long timeout, CountDownLatch startLatch, CountDownLatch endLatch)
      {
         this.awaitlatch = awaitLatch;
         this.startLatch = startLatch;
         this.endLatch = endLatch;
         this.timeout = timeout;
         this.answer = answer;
         this.toThrow = null;
      }
      
      public BlockingAnswer(Exception toThrow, CountDownLatch awaitLatch, long timeout, CountDownLatch startLatch, CountDownLatch endLatch)
      {
         this.awaitlatch = awaitLatch;
         this.startLatch = startLatch;
         this.endLatch = endLatch;
         this.timeout = timeout;
         this.answer = null;
         this.toThrow = toThrow;
      }
      
      public C answer() throws Throwable
      {
         if (startLatch != null)
         {
            startLatch.countDown();
         }
         
         try
         {
            if (timeout > 0)
            {
               awaitlatch.await(timeout, TimeUnit.MILLISECONDS);
            }
            else
            {
               awaitlatch.await();
            }
            
            if (toThrow != null)
            {
               throw toThrow;
            }
            
            return answer;
         }
         finally
         {
            if (endLatch != null)
            {
               endLatch.countDown();
            }
         }
      }      
   }
   
   protected abstract class AbstractCaller<C> implements Runnable
   {
      private final CountDownLatch startLatch;
      private final CountDownLatch proceedLatch;
      private final CountDownLatch finishLatch;
      private C result;
      private Throwable exception;
      
      AbstractCaller(CountDownLatch startLatch, 
            CountDownLatch proceedLatch, CountDownLatch finishLatch)
      {
         this.startLatch = startLatch;
         this.proceedLatch = proceedLatch;
         this.finishLatch = finishLatch;
      }
      
      public void run()
      {         
         try
         {
            if (startLatch != null)
            {
               startLatch.countDown();
            }
            
            if (proceedLatch != null)
            {
               proceedLatch.await();
            }
            
            result = execute();
         }
         catch (Throwable t)
         {
            exception = t;
         }
         finally
         {            
            if (finishLatch != null)
            {
               finishLatch.countDown();
            }
            
         }
      }
      
      protected abstract C execute();

      public C getResult()
      {
         return result;
      }

      public Throwable getException()
      {
         return exception;
      }      
      
   }
   
   protected class RemoteLockCaller extends AbstractCaller<RemoteLockResponse>
   {
      private final RpcTarget target;
      private final ClusterNode caller;
      
      public RemoteLockCaller(RpcTarget target, ClusterNode caller, CountDownLatch startLatch, 
            CountDownLatch proceedLatch, CountDownLatch finishLatch)
      {
         super(startLatch, proceedLatch, finishLatch);
         this.target = target;
         this.caller = caller;
      }
      
      protected RemoteLockResponse execute()
      {         
         return target.remoteLock("test", caller, 1000);
      }
   }
   
   protected class LocalLockCaller extends AbstractCaller<Boolean>
   {
      private final AbstractClusterLockSupport target;
      private final long timeout;
      
      public LocalLockCaller(AbstractClusterLockSupport target, CountDownLatch startLatch, 
            CountDownLatch proceedLatch, CountDownLatch finishLatch)
      {
         this(target, startLatch, proceedLatch, finishLatch, 3000);
      }
      
      public LocalLockCaller(AbstractClusterLockSupport target, CountDownLatch startLatch, 
            CountDownLatch proceedLatch, CountDownLatch finishLatch, long timeout)
      {
         super(startLatch, proceedLatch, finishLatch);
         this.target = target;
         this.timeout = timeout;
      }
      
      protected Boolean execute()
      {         
         return Boolean.valueOf(target.lock("test", timeout));
      }
   }

}