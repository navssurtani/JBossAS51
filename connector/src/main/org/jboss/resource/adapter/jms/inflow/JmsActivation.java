/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.resource.adapter.jms.inflow;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnectionFactory;
import javax.management.Notification;
import javax.naming.Context;
import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkManager;
import javax.transaction.TransactionManager;

import org.jboss.jms.jndi.JMSProviderAdapter;
import org.jboss.logging.Logger;
import org.jboss.mx.util.JBossNotificationBroadcasterSupport;
import org.jboss.resource.adapter.jms.JmsResourceAdapter;
import org.jboss.tm.TransactionManagerLocator;
import org.jboss.util.Strings;
import org.jboss.util.naming.Util;

/**
 * A generic jms Activation.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author <a href="jesper.pedersen@jboss.org">Jesper Pedersen</a>
 * @version $Revision: 76819 $
 */
public class JmsActivation implements ExceptionListener
{
   /** The log */
   private static final Logger log = Logger.getLogger(JmsActivation.class);
   
   /** Notification sent before connectioning */
   private static final String CONNECTING_NOTIFICATION = "org.jboss.ejb.plugins.jms.CONNECTING";

   /** Notification sent after connection */
   private static final String CONNECTED_NOTIFICATION = "org.jboss.ejb.plugins.jms.CONNECTED";

   /** Notification sent before disconnection */
   private static final String DISCONNECTING_NOTIFICATION = "org.jboss.ejb.plugins.jms.DISCONNECTING";

   /** Notification sent before disconnected */
   private static final String DISCONNECTED_NOTIFICATION = "org.jboss.ejb.plugins.jms.DISCONNECTED";

   /** Notification sent at connection failure */
   private static final String FAILURE_NOTIFICATION = "org.jboss.ejb.plugins.jms.FAILURE";

   /** The onMessage method */
   public static final Method ONMESSAGE; 
   
   /** The resource adapter */
   protected JmsResourceAdapter ra;
   
   /** The activation spec */
   protected JmsActivationSpec spec;

   /** The message endpoint factory */
   protected MessageEndpointFactory endpointFactory;
   
   /** The notification emitter */
   protected JBossNotificationBroadcasterSupport emitter;
   
   /** Whether delivery is active */
   protected AtomicBoolean deliveryActive = new AtomicBoolean(false);

   // Whether we are in the failure recovery loop
   private AtomicBoolean inFailure = new AtomicBoolean(false);

   /** The jms provider adapter */
   protected JMSProviderAdapter adapter;
   
   /** The destination */
   protected Destination destination;

   /** The destination type */
   protected boolean isTopic = false;
   
   /** The connection */
   protected Connection connection;
   
   /** The server session pool */
   protected JmsServerSessionPool pool;
   
   /** Is the delivery transacted */
   protected boolean isDeliveryTransacted;
   
   /** The DLQ handler */
   protected DLQHandler dlqHandler;
   
   /** The TransactionManager */
   protected TransactionManager tm;
   
   
   static
   {
      try
      {
         ONMESSAGE = MessageListener.class.getMethod("onMessage", new Class[] { Message.class });
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   public JmsActivation(JmsResourceAdapter ra, MessageEndpointFactory endpointFactory, JmsActivationSpec spec) throws ResourceException
   {
      this.ra = ra;
      this.endpointFactory = endpointFactory;
      this.spec = spec;
      try
      {
         this.isDeliveryTransacted = endpointFactory.isDeliveryTransacted(ONMESSAGE);
      }
      catch (Exception e)
      {
         throw new ResourceException(e);
      }
      if (endpointFactory instanceof JBossNotificationBroadcasterSupport)
         emitter = (JBossNotificationBroadcasterSupport) endpointFactory;
   }

   /**
    * @return the activation spec
    */
   public JmsActivationSpec getActivationSpec()
   {
      return spec;
   }

   /**
    * @return the message endpoint factory
    */
   public MessageEndpointFactory getMessageEndpointFactory()
   {
      return endpointFactory;
   }

   /**
    * @return whether delivery is transacted
    */
   public boolean isDeliveryTransacted()
   {
      return isDeliveryTransacted;
   }

   /**
    * @return the work manager
    */
   public WorkManager getWorkManager()
   {
      return ra.getWorkManager();
   }
   
   public TransactionManager getTransactionManager()
   {
      if (tm == null)
         tm = TransactionManagerLocator.locateTransactionManager();
      return tm;
   }

   /**
    * @return the connection
    */
   public Connection getConnection()
   {
      return connection;
   }

   /**
    * @return the destination
    */
   public Destination getDestination()
   {
      return destination;
   }

   /**
    * @return the destination type
    */
   public boolean isTopic()
   {
      return isTopic;
   }
   
   /**
    * @return the provider adapter 
    */
   public JMSProviderAdapter getProviderAdapter()
   {
      return adapter; 
   }
   
   /**
    * @return the dlq handler 
    */
   public DLQHandler getDLQHandler()
   {
      return dlqHandler; 
   }
   
   /**
    * Start the activation
    * 
    * @throws ResourceException for any error
    */
   public void start() throws ResourceException
   {
      deliveryActive.set(true);
      ra.getWorkManager().scheduleWork(new SetupActivation());
   }

   /**
    * Stop the activation
    */
   public void stop()
   {
      deliveryActive.set(false);
      teardown();
   }

   /**
    * Handles any failure by trying to reconnect
    * 
    * @param failure the reason for the failure
    */
   public void handleFailure(Throwable failure)
   {
      log.warn("Failure in jms activation " + spec, failure);
      int reconnectCount = 0;
      
      // Only enter the failure loop once
      if (inFailure.getAndSet(true))
         return;
      try
      {
         while (deliveryActive.get() && reconnectCount < spec.getReconnectAttempts())
         {
            teardown();

            sendNotification(FAILURE_NOTIFICATION, failure);

            try
            {
               Thread.sleep(spec.getReconnectIntervalLong());
            }
            catch (InterruptedException e)
            {
               log.debug("Interrupted trying to reconnect " + spec, e);
               break;
            }

            log.info("Attempting to reconnect " + spec);
            try
            {
               setup();
               log.info("Reconnected with messaging provider.");            
               break;
            }
            catch (Throwable t)
            {
               log.error("Unable to reconnect " + spec, t);
            }
            ++reconnectCount;
         }
      }
      finally
      {
         // Leaving failure recovery loop
         inFailure.set(false);
      }
   }

   public void onException(JMSException exception)
   {
      handleFailure(exception);
   }

   public String toString()
   {
      StringBuffer buffer = new StringBuffer();
      buffer.append(Strings.defaultToString(this)).append('(');
      buffer.append("spec=").append(Strings.defaultToString(spec));
      buffer.append(" mepf=").append(Strings.defaultToString(endpointFactory));
      buffer.append(" active=").append(deliveryActive.get());
      if (destination != null)
         buffer.append(" destination=").append(destination);
      if (connection != null)
         buffer.append(" connection=").append(connection);
      if (pool != null)
         buffer.append(" pool=").append(Strings.defaultToString(pool));
      if (dlqHandler != null)
         buffer.append(" dlq=").append(Strings.defaultToString(dlqHandler));
      buffer.append(" transacted=").append(isDeliveryTransacted);
      buffer.append(')');
      return buffer.toString();
   }

   /**
    * Setup the activation
    * 
    * @throws Exception for any error
    */
   protected void setup() throws Exception
   {
      log.debug("Setting up " + spec);
      
      sendNotification(CONNECTING_NOTIFICATION, null);

      setupJMSProviderAdapter();
      Context ctx = adapter.getInitialContext();
      log.debug("Using context " + ctx.getEnvironment() + " for " + spec);
      try
      {
         setupDLQ(ctx);
         setupDestination(ctx);
         setupConnection(ctx);
      }
      finally
      {
         ctx.close();
      }
      setupSessionPool();
      
      log.debug("Setup complete " + this);

      sendNotification(CONNECTED_NOTIFICATION, null);
   }
   
   /**
    * Teardown the activation
    */
   protected void teardown()
   {
      log.debug("Tearing down " + spec);

      sendNotification(DISCONNECTING_NOTIFICATION, null);

      teardownSessionPool();
      teardownConnection();
      teardownDestination();
      teardownDLQ();

      log.debug("Tearing down complete " + this);
      
      sendNotification(DISCONNECTED_NOTIFICATION, null);
   }

   /**
    * Get the jms provider
    * 
    * @throws Exception for any error
    */
   protected void setupJMSProviderAdapter() throws Exception
   {
      String providerAdapterJNDI = spec.getProviderAdapterJNDI();
      if (providerAdapterJNDI.startsWith("java:") == false)
         providerAdapterJNDI = "java:" + providerAdapterJNDI;

      log.debug("Retrieving the jms provider adapter " + providerAdapterJNDI + " for " + this);
      adapter = (JMSProviderAdapter) Util.lookup(providerAdapterJNDI, JMSProviderAdapter.class);
      log.debug("Using jms provider adapter " + adapter + " for " + this);
   }
   
   /**
    * Setup the DLQ
    *
    * @param ctx the naming context
    * @throws Exception for any error
    */
   protected void setupDLQ(Context ctx) throws Exception
   {
      if (spec.isUseDLQ())
      {
         Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(spec.getDLQHandler());
         dlqHandler = (DLQHandler) clazz.newInstance();
         dlqHandler.setup(this, ctx);
      }
      
      log.debug("Setup DLQ " + this);
   }
   
   /**
    * Teardown the DLQ
    */
   protected void teardownDLQ()
   {
      log.debug("Removing DLQ " + this);
      try
      {
         if (dlqHandler != null)
            dlqHandler.teardown();
      }
      catch (Throwable t)
      {
         log.debug("Error tearing down the DLQ " + dlqHandler, t);
      }
      dlqHandler = null;
   }
   
   /**
    * Setup the Destination
    *
    * @param ctx the naming context
    * @throws Exception for any error
    */
   protected void setupDestination(Context ctx) throws Exception
   {
      String destinationName = spec.getDestination();

      String destinationTypeString = spec.getDestinationType();
      if (destinationTypeString != null && !destinationTypeString.trim().equals(""))
      {
         log.debug("Destination type defined as " + destinationTypeString);

         Class<?> destinationType;
         if (Topic.class.getName().equals(destinationTypeString))
         {
            destinationType = Topic.class;
            isTopic = true;
         }
         else
         {
            destinationType = Queue.class;
         }

         log.debug("Retrieving destination " + destinationName + " of type " + destinationType.getName());
         destination = (Destination) Util.lookup(ctx, destinationName, destinationType);
      }
      else
      {
         log.debug("Destination type not defined");
         log.debug("Retrieving destination " + destinationName + " of type " + Destination.class.getName());

         destination = (Destination) Util.lookup(ctx, destinationName, Destination.class);
         if (destination instanceof Topic)
         {
            isTopic = true;
         }
      }

      log.debug("Got destination " + destination + " from " + destinationName);
   }
   
   /**
    * Teardown the destination
    */
   protected void teardownDestination()
   {
      destination = null;
   }
   
   /**
    * Setup the Connection
    *
    * @param ctx the naming context
    * @throws Exception for any error
    */
   protected void setupConnection(Context ctx) throws Exception
   {
      log.debug("setup connection " + this);

      String user = spec.getUser();
      String pass = spec.getPassword();
      String clientID = spec.getClientId();

      if (isTopic)
         connection = setupTopicConnection(ctx, user, pass, clientID);
      else
         connection = setupQueueConnection(ctx, user, pass, clientID);
      
      log.debug("established connection " + this);
   }
   
   /**
    * Setup a Queue Connection
    *
    * @param ctx the naming context
    * @param user the user
    * @param pass the password
    * @param clientID the client id
    * @return the connection
    * @throws Exception for any error
    */
   protected QueueConnection setupQueueConnection(Context ctx, String user, String pass, String clientID) throws Exception
   {
      String queueFactoryRef = adapter.getQueueFactoryRef();
      log.debug("Attempting to lookup queue connection factory " + queueFactoryRef);
      QueueConnectionFactory qcf = (QueueConnectionFactory) Util.lookup(ctx, queueFactoryRef, QueueConnectionFactory.class);
      log.debug("Got queue connection factory " + qcf + " from " + queueFactoryRef);
      log.debug("Attempting to create queue connection with user " + user);
      QueueConnection result;
      if (qcf instanceof XAQueueConnectionFactory && isDeliveryTransacted)
      {
         XAQueueConnectionFactory xaqcf = (XAQueueConnectionFactory) qcf;
         if (user != null)
            result = xaqcf.createXAQueueConnection(user, pass);
         else
            result = xaqcf.createXAQueueConnection();
      }
      else
      {
         if (user != null)
            result = qcf.createQueueConnection(user, pass);
         else
            result = qcf.createQueueConnection();
      }
      try
      {
         if (clientID != null)
            result.setClientID(clientID);
         result.setExceptionListener(this);
         log.debug("Using queue connection " + result);
         return result;
      }
      catch (Throwable t)
      {
         try
         {
            result.close();
         }
         catch (Exception e)
         {
            log.trace("Ignored error closing connection", e);
         }
         if (t instanceof Exception)
            throw (Exception) t;
         throw new RuntimeException("Error configuring connection", t);
      }
   }
   
   /**
    * Setup a Topic Connection
    *
    * @param ctx the naming context
    * @param user the user
    * @param pass the password
    * @param clientID the client id
    * @return the connection
    * @throws Exception for any error
    */
   protected TopicConnection setupTopicConnection(Context ctx, String user, String pass, String clientID) throws Exception
   {
      String topicFactoryRef = adapter.getTopicFactoryRef();
      log.debug("Attempting to lookup topic connection factory " + topicFactoryRef);
      TopicConnectionFactory tcf = (TopicConnectionFactory) Util.lookup(ctx, topicFactoryRef, TopicConnectionFactory.class);
      log.debug("Got topic connection factory " + tcf + " from " + topicFactoryRef);
      log.debug("Attempting to create topic connection with user " + user);
      TopicConnection result;
      if (tcf instanceof XATopicConnectionFactory && isDeliveryTransacted)
      {
         XATopicConnectionFactory xatcf = (XATopicConnectionFactory) tcf;
         if (user != null)
            result = xatcf.createXATopicConnection(user, pass);
         else
            result = xatcf.createXATopicConnection();
      }
      else
      {
         if (user != null)
            result = tcf.createTopicConnection(user, pass);
         else
            result = tcf.createTopicConnection();
      }
      try
      {
         if (clientID != null)
            result.setClientID(clientID);
         result.setExceptionListener(this);
         log.debug("Using topic connection " + result);
         return result;
      }
      catch (Throwable t)
      {
         try
         {
            result.close();
         }
         catch (Exception e)
         {
            log.trace("Ignored error closing connection", e);
         }
         if (t instanceof Exception)
            throw (Exception) t;
         throw new RuntimeException("Error configuring connection", t);
      }
   }
   
   /**
    * Teardown the connection
    */
   protected void teardownConnection()
   {
      try
      {
         if (connection != null)
         {
            log.debug("Closing the " + connection);
            connection.close();
         }
      }
      catch (Throwable t)
      {
         log.debug("Error closing the connection " + connection, t);
      }
      connection = null;
   }
   
   /**
    * Setup the server session pool
    * 
    * @throws Exception for any error
    */
   protected void setupSessionPool() throws Exception
   {
      pool = new JmsServerSessionPool(this);
      log.debug("Created session pool " + pool);
      
      log.debug("Starting session pool " + pool);
      pool.start();
      log.debug("Started session pool " + pool);
      
      log.debug("Starting delivery " + connection);
      connection.start();
      log.debug("Started delivery " + connection);
   }
   
   /**
    * Teardown the server session pool
    */
   protected void teardownSessionPool()
   {
      try
      {
         if (connection != null)
         {
            log.debug("Stopping delivery " + connection);
            connection.stop();
         }
      }
      catch (Throwable t)
      {
         log.debug("Error stopping delivery " + connection, t);
      }

      try
      {
         if (pool != null)
         {
            log.debug("Stopping the session pool " + pool);
            pool.stop();
         }
      }
      catch (Throwable t)
      {
         log.debug("Error clearing the pool " + pool, t);
      }
      pool = null;
   }

   /**
    * Notify of an event
    * 
    * @param event the event
    * @param userData any user data, e.g. the exception on a failure
    */
   protected void sendNotification(String event, Object userData)
   {
      if (emitter == null)
         return;
      
      try
      {
         Notification notif = new Notification(event, spec, emitter.nextNotificationSequenceNumber());
         notif.setUserData(userData);
         emitter.sendNotification(notif);
      }
      catch (Throwable t)
      {
         log.warn("Error sending notification: " + event, t);
      }
   }

   /**
    * Handles the setup
    */
   private class SetupActivation implements Work
   {
      public void run()
      {
         try
         {
            setup();
         }
         catch (Throwable t)
         {
            handleFailure(t);
         }
      }

      public void release()
      {
      }
   }
}
