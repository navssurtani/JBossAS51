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
package org.jboss.invocation.pooled.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.LinkedList;

import org.jboss.invocation.Invocation;
import org.jboss.invocation.pooled.interfaces.OptimizedObjectInputStream;
import org.jboss.invocation.pooled.interfaces.OptimizedObjectOutputStream;
import org.jboss.logging.Logger;

/**
 * This Thread object hold a single Socket connection to a client
 * and is kept alive until a timeout happens, or it is aged out of the
 * PooledInvoker's LRU cache.
 *
 * There is also a separate thread pool that is used if the client disconnects.
 * This thread/object is re-used in that scenario and that scenario only.
 *
 * This class will demarshal then delegate to PooledInvoker for invocation.
 *
 *
 * *NOTES* ObjectStreams were found to be better performing than the Custom marshalling
 * done by the TrunkInvoker.
 *
 * @author    <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @author Scott.Stark@jboss.org
 * @version $Revision: 81030 $
 */
public class ServerThread extends Thread
{
   final static private Logger log = Logger.getLogger(ServerThread.class);

   protected ObjectInputStream in;
   protected ObjectOutputStream out;
   protected Socket socket;
   protected PooledInvoker invoker;
   protected LRUPool clientpool;
   protected LinkedList threadpool;
   protected volatile boolean running = true;
   protected volatile boolean handlingResponse = true; // start off as true so that nobody can interrupt us
   protected volatile boolean shutdown = false;
   protected boolean trace;
   protected static int id = 0;

   public static synchronized int nextID()
   {
      int nextID = id ++;
      return nextID;
   }

   public ServerThread(Socket socket, PooledInvoker invoker, LRUPool clientpool,
      LinkedList threadpool, int timeout) throws Exception
   {
      super("PooledInvokerThread-" + socket.getInetAddress().getHostAddress()+"-"+nextID());
      this.socket = socket;
      this.invoker = invoker;
      this.clientpool = clientpool;
      this.threadpool = threadpool;
      this.trace = log.isTraceEnabled();
      socket.setSoTimeout(timeout);
   }

   public void shutdown()
   {
      shutdown = true;
      running = false;
      // This is a race and there is a chance
      // that a invocation is going on at the time
      // of the interrupt.  But I see no way right
      // now to protect for this.

      // NOTE ALSO!:
      // Shutdown should never be synchronized.
      // We don't want to hold up accept() thread! (via LRUpool)
      if (!handlingResponse)
      {
         try
         {
            this.interrupt();
            Thread.interrupted(); // clear
         }
         catch (Exception ignored) {}
      }
      
   }

   public void evict()
   {
      running = false;
      // This is a race and there is a chance
      // that a invocation is going on at the time
      // of the interrupt.  But I see no way right
      // now to protect for this.
      // There may not be a problem because interrupt only effects
      // threads blocking on IO.
      

      // NOTE ALSO!:
      // Shutdown should never be synchronized.
      // We don't want to hold up accept() thread! (via LRUpool)
      if (!handlingResponse)
      {
         try
         {
            this.interrupt();
            Thread.interrupted(); // clear
         }
         catch (Exception ignored) {}
      }
   }


   public synchronized void wakeup(Socket socket, int timeout) throws Exception
   {
      this.socket = socket;
      String name = "PooledInvokerThread-" + socket.getInetAddress().getHostAddress()+"-"+nextID();
      super.setName(name);
      socket.setSoTimeout(timeout);
      running = true;
      handlingResponse = true;
      this.notify();
   }

   public void run()
   {
      try
      {
         while (true)
         {
            dorun();
            //System.out.println("finished....");
            if (shutdown)
            {
               //System.out.println("doing shutdown");
               synchronized (clientpool)
               {
                  clientpool.remove(this);
               }
               return; // exit thread
            }
            else
            {
               //System.out.println("save thread");
               synchronized (this)
               {
                  //System.out.println("synch on client pool");
                  synchronized(clientpool)
                  {
                     //System.out.println("synch on thread pool");
                     synchronized(threadpool)
                     {
                        //System.out.println("removing myself from the pool: " + clientpool.size());
                        clientpool.remove(this);
                        //System.out.println("adding myself to threadpool");
                        threadpool.add(this);
                        Thread.interrupted(); // clear any interruption so that we can be pooled.
                        clientpool.notify();
                     }
                  }
                  if( trace )
                     log.trace("begin thread wait");
                  this.wait();
                  if( trace )
                     log.trace("WAKEUP in SERVER THREAD");
               }
            }
         }
      }
      catch (Exception ignored) 
      {
         if( trace )
            log.trace("Exiting run on exception", ignored);
      }
   }

   protected void acknowledge() throws Exception
   {
      //System.out.println("****acknowledge " + Thread.currentThread());
      // Perform acknowledgement to convince client
      // that the socket is still active
      byte ACK = in.readByte();
      //System.out.println("****acknowledge read byte" + Thread.currentThread());

      // HERE IS THE RACE between ACK received and handlingResponse = true
      // We can't synchronize because readByte blocks and client is expecting
      // a response and we don't want to hang client.
      // see shutdown and evict for more details
      // There may not be a problem because interrupt only effects
      // threads blocking on IO. and this thread will just continue.
      handlingResponse = true;
      
      out.writeByte(ACK);
      out.flush();
   }

   protected void processInvocation() throws Exception
   {
      handlingResponse = true;
      // Ok, now read invocation and invoke
      Invocation invocation = (Invocation)in.readObject();
      in.readObject(); // for stupid ObjectInputStream reset
      Object response = null;
      try
      {
          // Make absolutely sure thread interrupted is cleared.
         boolean interrupted = Thread.interrupted();
         response = invoker.invoke(invocation);
      }
      catch (Exception ex)
      {
         response = ex;
      }
      Thread.interrupted(); // clear interrupted state so we don't fail on socket writes
      out.writeObject(response);
      out.reset();
      // to make sure stream gets reset
      // Stupid ObjectInputStream holds object graph
      // can only be set by the client/server sending a TC_RESET
      out.writeObject(Boolean.TRUE); 
      out.flush();
      out.reset();
      handlingResponse = false;
   }

   /**
    * This is needed because Object*Streams leak
    */
   protected void dorun()
   {
      log.debug("beginning dorun");
      running = true;
      handlingResponse = true;
      try
      {
         BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());
         out = new OptimizedObjectOutputStream(bos);
         out.flush();
         BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
         in = new OptimizedObjectInputStream(bis);
      }
      catch (Exception e)
      {
         log.error("Failed to initialize", e);
      }

      // Always do first one without an ACK because its not needed
      try
      {
         processInvocation();
      }
      catch (Exception e)
      {
         running = false;
         if( trace )
            log.trace("invocation failed", e);
      }

      // Re-use loop
      while (running)
      {
         try
         {
            acknowledge();
            processInvocation();
         }
         catch (InterruptedIOException e)
         {
            log.debug("socket timed out", e);
            running = false;
         }
         catch (InterruptedException e)
         {
            log.debug("interrupted", e);
         }
         catch (Exception ex)
         {
            if( trace )
               log.debug("invocation failed", ex);
            running = false;
         }
         // clear any interruption so that thread can be pooled.
         Thread.interrupted();
      }

      if( trace )
         log.trace("finished loop");
      // Ok, we've been shutdown.  Do appropriate cleanups.
      try
      {
         if (in != null) in.close();
         if (out != null) out.close();
      }
      catch (Exception ex)
      {
      }
      try
      {
         socket.close();
      }
      catch (Exception ex)
      {
         log.debug("Failed cleanup", ex);
      }
      socket = null;
      in = null;
      out = null;
   }
}
