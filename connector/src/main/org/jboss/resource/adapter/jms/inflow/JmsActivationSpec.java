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

import javax.jms.Session;
import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ResourceAdapter;

import org.jboss.logging.Logger;
import org.jboss.util.Strings;

/**
 * A generic jms ActivationSpec.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author <a href="jesper.pedersen@jboss.org">Jesper Pedersen</a>
 * @version $Revision: 91228 $
 */
public class JmsActivationSpec implements ActivationSpec
{
   /** The log */
   private static final Logger log = Logger.getLogger(JmsActivationSpec.class);

   /** The resource adapter */
   private ResourceAdapter ra;

   /** The destination */
   private String destination;

   /** The destination type */
   private String destinationType;

   /** The message selector */
   private String messageSelector;

   /** The acknowledgement mode */
   private int acknowledgeMode;

   /** The subscription durability */
   private boolean subscriptionDurability;

   /** The client id */
   private String clientId;

   /** The subscription name */
   private String subscriptionName;

   /** The reconnect interval in seconds */
   private long reconnectInterval = 10;

   /** The jms provider adapter jndi name */
   private String providerAdapterJNDI = "java:/DefaultJMSProvider";

   /** The user */
   private String user;

   /** The password */
   private String pass;

   /** The maximum number of messages */
   private int maxMessages = 1;

   /** The minimum number of sessions */
   private int minSession = 1;

   /** The maximum number of sessions */
   private int maxSession = 15;

   /** The keep alive time for sessions */
   private long keepAlive = 60000;

   /** Is the session transacted */
   private boolean sessionTransacted = true;

   /** The DLQ handler class */
   private String dLQHandler = "org.jboss.resource.adapter.jms.inflow.dlq.GenericDLQHandler";

   /** Whether to use a DLQ */
   private boolean useDLQ = true;

   /** The DLQ jndi binding */
   private String dLQJNDIName = "queue/DLQ";

   /** The DLQ user */
   private String dLQUser;

   /** The DLQ password */
   private String dLQPassword;

   /** The DLQ client id  */
   private String dLQClientID;

   /** The DLQ max resent  */
   private int dLQMaxResent = 5;
   
   //Default to 5 attempts
   private int reconnectAttempts = 5;
   
   private boolean redeliverUnspecified = true;
   
   private int transactionTimeout;
   
   private Boolean isSameRMOverrideValue;
   
   private boolean forceClearOnShutdown = false;
   
   private long forceClearOnShutdownInterval = 1000;
   
   private int forceClearAttempts = 0;
   
   private Boolean forceTransacted = Boolean.FALSE;
   
   public void setForceClearOnShutdown(boolean forceClear)
   {
      this.forceClearOnShutdown = forceClear;
   }   
   
   public boolean isForceClearOnShutdown()
   {
      return this.forceClearOnShutdown;
   }
   
   public long getForceClearOnShutdownInterval()
   {
      return this.forceClearOnShutdownInterval;
   }
   
   public void setForceClearOnShutdownInterval(long forceClearOnShutdownInterval)
   {
      this.forceClearOnShutdownInterval = forceClearOnShutdownInterval;
   }
   
   public int getForceClearAttempts()
   {
      return forceClearAttempts;
   }
   
   public void setForceClearAttempts(int forceClearAttempts)
   {
      this.forceClearAttempts = forceClearAttempts;
   }

   /**
    * @return the acknowledgeMode.
    */
   public String getAcknowledgeMode()
   {
      if (sessionTransacted)
         return "Transacted";
      else if (Session.DUPS_OK_ACKNOWLEDGE == acknowledgeMode)
         return "Dups-ok-acknowledge";
      else
         return "Auto-acknowledge";
   }

   /**
    * @param acknowledgeMode The acknowledgeMode to set.
    */
   public void setAcknowledgeMode(String acknowledgeMode)
   {
      if ("DUPS_OK_ACKNOWLEDGE".equals(acknowledgeMode) || "Dups-ok-acknowledge".equals(acknowledgeMode))
         this.acknowledgeMode = Session.DUPS_OK_ACKNOWLEDGE;
      else if ("AUTO_ACKNOWLEDGE".equals(acknowledgeMode) || "Auto-acknowledge".equals(acknowledgeMode))
         this.acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
      else if ("SESSION_TRANSACTED".equals(acknowledgeMode))
         this.acknowledgeMode = Session.SESSION_TRANSACTED;
      else
         throw new IllegalArgumentException("Unsupported acknowledgement mode " + acknowledgeMode);
   }

   /**
    * @return the acknowledgement mode
    */
   public int getAcknowledgeModeInt()
   {
      if (sessionTransacted)
         return Session.SESSION_TRANSACTED;
      return acknowledgeMode;
   }

   /**
    * @return the clientId.
    */
   public String getClientId()
   {
      return clientId;
   }

   /**
    * @param clientId The clientId to set.
    */
   public void setClientId(String clientId)
   {
      this.clientId = clientId;
   }

   /**
    * @return the destination.
    */
   public String getDestination()
   {
      return destination;
   }

   /**
    * @param destination The destination to set.
    */
   public void setDestination(String destination)
   {
      this.destination = destination;
   }

   /**
    * @return the destinationType.
    */
   public String getDestinationType()
   {
      return destinationType;
   }

   /**
    * @param destinationType The destinationType to set.
    */
   public void setDestinationType(String destinationType)
   {
      this.destinationType = destinationType;
   }

   /**
    * @return the messageSelector.
    */
   public String getMessageSelector()
   {
      return messageSelector;
   }

   /**
    * @param messageSelector The messageSelector to set.
    */
   public void setMessageSelector(String messageSelector)
   {
      this.messageSelector = messageSelector;
   }

   /**
    * @return the subscriptionDurability.
    */
   public String getSubscriptionDurability()
   {
      if (subscriptionDurability)
         return "Durable";
      else
         return "NonDurable";
   }

   /**
    * @param subscriptionDurability The subscriptionDurability to set.
    */
   public void setSubscriptionDurability(String subscriptionDurability)
   {
      this.subscriptionDurability = "Durable".equals(subscriptionDurability);
   }

   /**
    * @return wether the subscription is durable.
    */
   public boolean isDurable()
   {
      return subscriptionDurability;
   }

   /**
    * @return the subscriptionName.
    */
   public String getSubscriptionName()
   {
      return subscriptionName;
   }

   /**
    * @param subscriptionName The subscriptionName to set.
    */
   public void setSubscriptionName(String subscriptionName)
   {
      this.subscriptionName = subscriptionName;
   }

   /**
    * @return the reconnectInterval.
    */
   public long getReconnectInterval()
   {
      return reconnectInterval;
   }

   /**
    * @param reconnectInterval The reconnectInterval to set.
    */
   public void setReconnectInterval(long reconnectInterval)
   {
      this.reconnectInterval = reconnectInterval;
   }

   /**
    * @return the reconnect interval
    */
   public long getReconnectIntervalLong()
   {
      return reconnectInterval * 1000;
   }

   /**
    * @return the providerAdapterJNDI.
    */
   public String getProviderAdapterJNDI()
   {
      return providerAdapterJNDI;
   }

   /**
    * @param providerAdapterJNDI The providerAdapterJNDI to set.
    */
   public void setProviderAdapterJNDI(String providerAdapterJNDI)
   {
      this.providerAdapterJNDI = providerAdapterJNDI;
   }

   /**
    * @return the user.
    */
   public String getUser()
   {
      return user;
   }

   /**
    * @param user The user to set.
    */
   public void setUser(String user)
   {
      this.user = user;
   }

   /**
    * @return the password.
    */
   public String getPassword()
   {
      return pass;
   }

   /**
    * @param pass The password to set.
    */
   public void setPassword(String pass)
   {
      this.pass = pass;
   }

   /**
    * @return the maxMessages.
    */
   public int getMaxMessages()
   {
      return maxMessages;
   }

   /**
    * @param maxMessages The maxMessages to set.
    */
   public void setMaxMessages(int maxMessages)
   {
      this.maxMessages = maxMessages;
   }

   /**
    * @return the maximum number of messages
    */
   public int getMaxMessagesInt()
   {
      return maxMessages;
   }

   /**
    * @return the minSession.
    */
   public int getMinSession()
   {
      return minSession;
   }

   /**
    * @param minSession The minSession to set.
    */
   public void setMinSession(int minSession)
   {
      this.minSession = minSession;
   }

   /**
    * @return the minimum number of sessions
    */
   public int getMinSessionInt()
   {
      return minSession;
   }

   /**
    * @return the maxSession.
    */
   public int getMaxSession()
   {
      return maxSession;
   }

   /**
    * @param maxSession The maxSession to set.
    */
   public void setMaxSession(int maxSession)
   {
      this.maxSession = maxSession;
   }

   /**
    * @return the maximum number of sessions
    */
   public int getMaxSessionInt()
   {
      return maxSession;
   }

   /**
    * @return the keepAlive.
    */
   public long getKeepAlive()
   {
      return keepAlive;
   }

   /**
    * @param keepAlive The keepAlive to set.
    */
   public void setKeepAlive(long keepAlive)
   {
      this.keepAlive = keepAlive;
   }

   /**
    * @return the keep alive time of the session
    */
   public long getKeepAliveLong()
   {
      return keepAlive;
   }

   /**
    * @return the sessionTransacted.
    */
   public boolean getSessionTransacted()
   {
      return sessionTransacted;
   }

   /**
    * @param sessionTransacted The sessionTransacted to set.
    */
   public void setSessionTransacted(boolean sessionTransacted)
   {
      this.sessionTransacted = sessionTransacted;
   }

   /**
    * @return whether the session is transaction
    */
   public boolean isSessionTransacted()
   {
      return sessionTransacted;
   }

   /**
    * @return Returns the dLQHandler.
    */
   public String getDLQHandler()
   {
      return dLQHandler;
   }

   /**
    * @param handler The dLQHandler to set.
    */
   public void setDLQHandler(String handler)
   {
      this.dLQHandler = handler;
   }

   /**
    * @return Returns the dLQJNDIName.
    */
   public String getDLQJNDIName()
   {
      return dLQJNDIName;
   }

   /**
    * @param name The dLQJNDIName to set.
    */
   public void setDLQJNDIName(String name)
   {
      dLQJNDIName = name;
   }

   /**
    * @return Returns the useDLQ.
    */
   public boolean getUseDLQ()
   {
      return useDLQ;
   }

   /**
    * @param useDLQ The useDLQ to set.
    */
   public void setUseDLQ(boolean useDLQ)
   {
      this.useDLQ = useDLQ;
   }

   /**
    * Whether we should use a DLQ
    * 
    * @return true when using a DLQ
    */
   public boolean isUseDLQ()
   {
      return useDLQ;
   }

   /**
    * @return Returns the dLQClientID.
    */
   public String getDLQClientID()
   {
      return dLQClientID;
   }

   /**
    * @param clientID The dLQClientID to set.
    */
   public void setDLQClientID(String clientID)
   {
      dLQClientID = clientID;
   }

   /**
    * @return Returns the dLQPassword.
    */
   public String getDLQPassword()
   {
      return dLQPassword;
   }

   /**
    * @param password The dLQPassword to set.
    */
   public void setDLQPassword(String password)
   {
      dLQPassword = password;
   }

   /**
    * @return Returns the dLQUser.
    */
   public String getDLQUser()
   {
      return dLQUser;
   }

   /**
    * @param user The dLQUser to set.
    */
   public void setDLQUser(String user)
   {
      dLQUser = user;
   }

   /**
    * @return Returns the maxResent.
    */
   public int getDLQMaxResent()
   {
      return dLQMaxResent;
   }
   /**
    * @param maxResent The maxResent to set.
    */
   public void setDLQMaxResent(int maxResent)
   {
      this.dLQMaxResent = maxResent;
   }

   public ResourceAdapter getResourceAdapter()
   {
      return ra;
   }

   public void setResourceAdapter(ResourceAdapter ra) throws ResourceException
   {
      this.ra = ra;
   }

   public void validate() throws InvalidPropertyException
   {
      if (log.isTraceEnabled())
         log.trace("validate " + this);
      // TODO validate
   }

   public String toString()
   {
      StringBuffer buffer = new StringBuffer();
      buffer.append(Strings.defaultToString(this)).append('(');
      buffer.append("ra=").append(ra);
      buffer.append(" destination=").append(destination);
      buffer.append(" destinationType=").append(destinationType);
      if (messageSelector != null)
         buffer.append(" selector=").append(messageSelector);
      buffer.append(" tx=").append(sessionTransacted);
      if (sessionTransacted == false)
         buffer.append(" ack=").append(getAcknowledgeMode());
      buffer.append(" durable=").append(subscriptionDurability);
      if (clientId != null)
         buffer.append(" clientID=").append(clientId);
      if (subscriptionName != null)
         buffer.append(" subscription=").append(subscriptionName);
      buffer.append(" reconnect=").append(reconnectInterval);
      buffer.append(" provider=").append(providerAdapterJNDI);
      buffer.append(" user=").append(user);
      if (pass != null)
         buffer.append(" pass=").append("<not shown>");
      buffer.append(" maxMessages=").append(maxMessages);
      buffer.append(" minSession=").append(minSession);
      buffer.append(" maxSession=").append(maxSession);
      buffer.append(" keepAlive=").append(keepAlive);
      buffer.append(" useDLQ=").append(useDLQ);
      if (useDLQ)
      {
         buffer.append(" DLQHandler=").append(dLQHandler);
         buffer.append(" DLQJndiName=").append(dLQJNDIName);
         buffer.append(" DLQUser=").append(dLQUser);
         if (dLQPassword != null)
            buffer.append(" DLQPass=").append("<not shown>");
         if (dLQClientID != null)
            buffer.append(" DLQClientID=").append(dLQClientID);
         buffer.append(" DLQMaxResent=").append(dLQMaxResent);
      }
      buffer.append(')');
      return buffer.toString();
   }

   public int getReconnectAttempts()
   {
      return reconnectAttempts;
   }

   public void setReconnectAttempts(int reconnectAttempts)
   {
      this.reconnectAttempts = reconnectAttempts;
   }

   public boolean getRedeliverUnspecified()
   {
      return redeliverUnspecified;
   }

   public void setRedeliverUnspecified(boolean redeliverUnspecified)
   {
      this.redeliverUnspecified = redeliverUnspecified;
   }
   
   public int getTransactionTimeout()
   {
      return transactionTimeout;
   }

   public void setTransactionTimeout(int transactionTimeout)
   {
      this.transactionTimeout = transactionTimeout;
   }
   
   public Boolean getIsSameRMOverrideValue()
   {
      return isSameRMOverrideValue;
   }

   public void setIsSameRMOverrideValue(Boolean isSameRMOverrideValue)
   {
      this.isSameRMOverrideValue = isSameRMOverrideValue;
   }

   public Boolean isForceTransacted()
   {
      if (forceTransacted != null)
         return forceTransacted;

      return Boolean.FALSE;
   }

   public void setForceTransacted(Boolean forceTransacted)
   {
      this.forceTransacted = forceTransacted;
   }
}
