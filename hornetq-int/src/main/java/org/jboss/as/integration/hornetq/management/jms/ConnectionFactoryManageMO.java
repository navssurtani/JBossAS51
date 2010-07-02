/*
* JBoss, Home of Professional Open Source
* Copyright 2010, Red Hat Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
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
package org.jboss.as.integration.hornetq.management.jms;

import org.hornetq.api.core.management.ResourceNames;
import org.hornetq.api.jms.management.ConnectionFactoryControl;
import org.hornetq.api.jms.management.JMSServerControl;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.management.ManagementService;
import org.jboss.managed.api.annotation.*;
import org.jboss.metatype.api.annotations.MetaMapping;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 *         Created Mar 19, 2010
 */
@ManagementObject(componentType = @ManagementComponent(type = "JMSManage", subtype = "ConnectionFactoryManage"),
    properties = ManagementProperties.EXPLICIT, isRuntime = true)
public class ConnectionFactoryManageMO extends JMSManageMO
{
   public ConnectionFactoryManageMO(HornetQServer server)
   {
      super(server);
      this.server = server;
   }

   private JMSServerControl jmsServerControl;

   private ManagementService managementService;

   private HornetQServer server;

   public void start()
   {
      managementService = server.getManagementService();
      jmsServerControl = (JMSServerControl) managementService.getResource(ResourceNames.JMS_SERVER);
   }

   @ManagementOperation(name = "deleteConnectionFactory", description = "returns the JMS Connection Factory configuration",
       params = {@ManagementParameter(name = "name", description = "the connection factory name")})
   public void deleteConnectionFactory(String name) throws Exception
   {
      jmsServerControl.destroyConnectionFactory(name);
   }

   @ManagementOperation(name = "getConfiguration", description = "returns the JMS Connection Factory configuration",
       params = {@ManagementParameter(name = "name", description = "the connection factory name")})
   @MetaMapping(value = ConnectionFactoryMapper.class)
   public ConnectionFactoryControl getConfiguration(String name)
   {
      return (ConnectionFactoryControl) managementService.getResource("jms.connectionfactory." + name);
   }

   @ManagementOperation(name = "getMeasurements", description = "gets a connection factories",
       params = {
           @ManagementParameter(name = "name", description = "the queue name"),
           @ManagementParameter(name = "names", description = "the measurement names")})
   public String[] getMeasurements(String name, String[] names) throws Exception
   {
      ConnectionFactoryControl control = (ConnectionFactoryControl) managementService.getResource("jms.connectionfactory." + name);
      String[] val = new String[names.length];
      for (int i = 0, valLength = val.length; i < valLength; i++)
      {
         Object o = control.getClass().getMethod(names[i]).invoke(control);
         if(o instanceof Object[])
         {
            val[i] = coomaSeparatedString((Object[]) o);
         }
         else
         {
            val[i] = o.toString();
         }
      }
      return val;
   }


   @ManagementOperation(name = "getConnectionFactories", description = "returns the JMS Connection Factories")
   public String[] getJMSConnectionFactories()
   {
      return jmsServerControl.getConnectionFactoryNames();
   }

   @ManagementOperation(name = "createConnectionFactory", description = "creates a connection factories",
       params = {
           @ManagementParameter(name = "name", description = "the connection factory name"),
           @ManagementParameter(name = "liveTransportClassNames", description = "comma-separated list of class names for transport to live servers"),
           @ManagementParameter(name = "liveTransportParams", description = "comma-separated list of key=value parameters for the live transports (enclosed between { } for each transport)"),
           @ManagementParameter(name = "backupTransportClassNames", description = "comma-separated list of class names for transport to backup servers"),
           @ManagementParameter(name = "backupTransportParams", description = "comma-separated list of key=value parameters for the backup transports (enclosed between { } for each transport)"),
           @ManagementParameter(name = "bindings", description = "comma-separated list of JNDI bindings"),
           @ManagementParameter(name = "discoveryAddress", description = "a discovery address"),
           @ManagementParameter(name = "discoveryPort", description = "a discovery port"),
           @ManagementParameter(name = "discoveryRefreshTimeout", description = "the discovery refresh timeout"),
           @ManagementParameter(name = "clientId", description = "the client id"),
           @ManagementParameter(name = "dupsOkBatchSize", description = "the batch size for DUPS_OK acknowledge mode"),
           @ManagementParameter(name = "transactionBatchSize", description = "the transaction batch size"),
           @ManagementParameter(name = "clientFailureCheckPeriod", description = "the client failure check period"),
           @ManagementParameter(name = "connectionTTL", description = "the connection time to live"),
           @ManagementParameter(name = "callTimeout", description = "the remote call timeout"),
           @ManagementParameter(name = "consumerWindowSize", description = "the consumer window size"),
           @ManagementParameter(name = "confirmationWindowSize", description = "the confirmation window size"),
           @ManagementParameter(name = "producerMaxRate", description = "the produxer max rate"),
           @ManagementParameter(name = "producerWindowSize", description = "the producer window size"),
           @ManagementParameter(name = "cacheLargeMessageClient", description = "do we cache large messages on the client"),
           @ManagementParameter(name = "minLargeMessageSize", description = "the minimum large message size"),
           @ManagementParameter(name = "blockOnNonDurableSend", description = "do we block on non durable send"),
           @ManagementParameter(name = "blockOnAcknowledge", description = "do we block on acknowledge"),
           @ManagementParameter(name = "blockOnDurableSend", description = "do we block on durable send"),
           @ManagementParameter(name = "autoGroup", description = "do we use autogroup"),
           @ManagementParameter(name = "preAcknowledge", description = "do we pre acknowledge messages"),
           @ManagementParameter(name = "maxRetryInterval", description = "the max retry interval"),
           @ManagementParameter(name = "retryIntervalMultiplier", description = "the max retry interval multiplier"),
           @ManagementParameter(name = "reconnectAttempts", description = "the reconnect attempts"),
           @ManagementParameter(name = "failoverOnShutdown", description = "do we failover on a clean shutdown"),
           @ManagementParameter(name = "scheduledThreadPoolMaxSize", description = "the pool size for scheduled threads"),
           @ManagementParameter(name = "threadPoolMaxSize", description = "the pool size for threads"),
           @ManagementParameter(name = "groupId", description = "the group id"),
           @ManagementParameter(name = "initialMessagePacketSize", description = "the initial message packet size"),
           @ManagementParameter(name = "useGlobalPools", description = "do we use global pools"),
           @ManagementParameter(name = "retryInterval", description = "the retry interval"),
           @ManagementParameter(name = "connectionLoadBalancingPolicyClassName", description = "the load balancing class")})
   public void createConnectionFactory(String name,
                                        final String liveTransportClassNames,
                                        final String liveTransportParams,
                                        final String backupTransportClassNames,
                                        final String backupTransportParams, 
                                        String bindings,
                                        String discoveryAddress,
                                        int discoveryPort,
                                        long discoveryRefreshTimeout,
                                        String clientId,
                                        int dupsOkBatchSize,
                                        int transactionBatchSize,
                                        long clientFailureCheckPeriod,
                                        long connectionTTL,
                                        long callTimeout,
                                        int consumerWindowSize,
                                        int confirmationWindowSize,
                                        int producerMaxRate,
                                        int producerWindowSize,
                                        boolean cacheLargeMessageClient,
                                        int minLargeMessageSize,
                                        boolean blockOnNonDurableSend,
                                        boolean blockOnAcknowledge,
                                        boolean blockOnDurableSend,
                                        boolean autoGroup,
                                        boolean preAcknowledge,
                                        long maxRetryInterval,
                                        double retryIntervalMultiplier,
                                        int reconnectAttempts,
                                        boolean failoverOnShutdown,
                                        int scheduledThreadPoolMaxSize,
                                        int threadPoolMaxSize,
                                        String groupId,
                                        int initialMessagePacketSize,
                                        boolean useGlobalPools,
                                        long retryInterval,
                                        String connectionLoadBalancingPolicyClassName)
         throws Exception
   {
      if(liveTransportClassNames != null)
      {
         jmsServerControl.createConnectionFactory(name, liveTransportClassNames, liveTransportParams == null?"":liveTransportParams,
               backupTransportClassNames == null?"":backupTransportClassNames, backupTransportParams == null?"":backupTransportParams, bindings);
      }
      else
      {
         jmsServerControl.createConnectionFactory(name, discoveryAddress, discoveryPort, bindings);
      }
      ConnectionFactoryControl control = (ConnectionFactoryControl) managementService.getResource("jms.connectionfactory." + name);
      control.setDiscoveryRefreshTimeout(discoveryRefreshTimeout);
      control.setClientID(clientId);
      control.setDupsOKBatchSize(dupsOkBatchSize);
      control.setTransactionBatchSize(transactionBatchSize);
      control.setClientFailureCheckPeriod(clientFailureCheckPeriod);
      control.setConnectionTTL(connectionTTL);
      control.setCallTimeout(callTimeout);
      control.setConsumerWindowSize(consumerWindowSize);
      control.setConfirmationWindowSize(confirmationWindowSize);
      control.setProducerMaxRate(producerMaxRate);
      control.setProducerWindowSize(producerWindowSize);
      control.setCacheLargeMessagesClient(cacheLargeMessageClient);
      control.setMinLargeMessageSize(minLargeMessageSize);
      control.setBlockOnDurableSend(blockOnNonDurableSend);
      control.setBlockOnAcknowledge(blockOnAcknowledge);
      control.setBlockOnDurableSend(blockOnDurableSend);
      control.setAutoGroup(autoGroup);
      control.setPreAcknowledge(preAcknowledge);
      control.setMaxRetryInterval(maxRetryInterval);
      control.setRetryIntervalMultiplier(retryIntervalMultiplier);
      control.setReconnectAttempts(reconnectAttempts);
      control.setFailoverOnServerShutdown(failoverOnShutdown);
      control.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
      control.setThreadPoolMaxSize(threadPoolMaxSize);
      control.setGroupID(groupId);
      control.setInitialMessagePacketSize(initialMessagePacketSize);
      control.setUseGlobalPools(useGlobalPools);
      control.setRetryInterval(retryInterval);
      control.setConnectionLoadBalancingPolicyClassName(connectionLoadBalancingPolicyClassName);
   }

   @ManagementOperation(name = "createConnectionFactory", description = "creates a connection factories",
       params = {
           @ManagementParameter(name = "name", description = "the connection factory name"),
           @ManagementParameter(name = "discoveryRefreshTimeout", description = "the discovery refresh timeout"),
           @ManagementParameter(name = "clientId", description = "the client id"),
           @ManagementParameter(name = "dupsOkBatchSize", description = "the batch size for DUPS_OK acknowledge mode"),
           @ManagementParameter(name = "transactionBatchSize", description = "the transaction batch size"),
           @ManagementParameter(name = "clientFailureCheckPeriod", description = "the client failure check period"),
           @ManagementParameter(name = "connectionTTL", description = "the connection time to live"),
           @ManagementParameter(name = "callTimeout", description = "the remote call timeout"),
           @ManagementParameter(name = "consumerWindowSize", description = "the consumer window size"),
           @ManagementParameter(name = "confirmationWindowSize", description = "the confirmation window size"),
           @ManagementParameter(name = "producerMaxRate", description = "the produxer max rate"),
           @ManagementParameter(name = "producerWindowSize", description = "the producer window size"),
           @ManagementParameter(name = "cacheLargeMessageClient", description = "do we cache large messages on the client"),
           @ManagementParameter(name = "minLargeMessageSize", description = "the minimum large message size"),
           @ManagementParameter(name = "blockOnNonDurableSend", description = "do we block on non durable send"),
           @ManagementParameter(name = "blockOnAcknowledge", description = "do we block on acknowledge"),
           @ManagementParameter(name = "blockOnDurableSend", description = "do we block on durable send"),
           @ManagementParameter(name = "autoGroup", description = "do we use autogroup"),
           @ManagementParameter(name = "preAcknowledge", description = "do we pre acknowledge messages"),
           @ManagementParameter(name = "maxRetryInterval", description = "the max retry interval"),
           @ManagementParameter(name = "retryIntervalMultiplier", description = "the max retry interval multiplier"),
           @ManagementParameter(name = "reconnectAttempts", description = "the reconnect attempts"),
           @ManagementParameter(name = "failoverOnShutdown", description = "do we failover on a clean shutdown"),
           @ManagementParameter(name = "scheduledThreadPoolMaxSize", description = "the pool size for scheduled threads"),
           @ManagementParameter(name = "threadPoolMaxSize", description = "the pool size for threads"),
           @ManagementParameter(name = "groupId", description = "the group id"),
           @ManagementParameter(name = "initialMessagePacketSize", description = "the initial message packet size"),
           @ManagementParameter(name = "useGlobalPools", description = "do we use global pools"),
           @ManagementParameter(name = "retryInterval", description = "the retry interval"),
           @ManagementParameter(name = "connectionLoadBalancingPolicyClassName", description = "the load balancing class")})
   public void updateConnectionFactory(String name,
                                        long discoveryRefreshTimeout,
                                        String clientId,
                                        int dupsOkBatchSize,
                                        int transactionBatchSize,
                                        long clientFailureCheckPeriod,
                                        long connectionTTL,
                                        long callTimeout,
                                        int consumerWindowSize,
                                        int confirmationWindowSize,
                                        int producerMaxRate,
                                        int producerWindowSize,
                                        boolean cacheLargeMessageClient,
                                        int minLargeMessageSize,
                                        boolean blockOnNonDurableSend,
                                        boolean blockOnAcknowledge,
                                        boolean blockOnDurableSend,
                                        boolean autoGroup,
                                        boolean preAcknowledge,
                                        long maxRetryInterval,
                                        double retryIntervalMultiplier,
                                        int reconnectAttempts,
                                        boolean failoverOnShutdown,
                                        int scheduledThreadPoolMaxSize,
                                        int threadPoolMaxSize,
                                        String groupId,
                                        int initialMessagePacketSize,
                                        boolean useGlobalPools,
                                        long retryInterval,
                                        String connectionLoadBalancingPolicyClassName)
         throws Exception
   {
      ConnectionFactoryControl control = (ConnectionFactoryControl) managementService.getResource("jms.connectionfactory." + name);
      control.setDiscoveryRefreshTimeout(discoveryRefreshTimeout);
      control.setClientID(clientId);
      control.setDupsOKBatchSize(dupsOkBatchSize);
      control.setTransactionBatchSize(transactionBatchSize);
      control.setClientFailureCheckPeriod(clientFailureCheckPeriod);
      control.setConnectionTTL(connectionTTL);
      control.setCallTimeout(callTimeout);
      control.setConsumerWindowSize(consumerWindowSize);
      control.setConfirmationWindowSize(confirmationWindowSize);
      control.setProducerMaxRate(producerMaxRate);
      control.setProducerWindowSize(producerWindowSize);
      control.setCacheLargeMessagesClient(cacheLargeMessageClient);
      control.setMinLargeMessageSize(minLargeMessageSize);
      control.setBlockOnDurableSend(blockOnNonDurableSend);
      control.setBlockOnAcknowledge(blockOnAcknowledge);
      control.setBlockOnDurableSend(blockOnDurableSend);
      control.setAutoGroup(autoGroup);
      control.setPreAcknowledge(preAcknowledge);
      control.setMaxRetryInterval(maxRetryInterval);
      control.setRetryIntervalMultiplier(retryIntervalMultiplier);
      control.setReconnectAttempts(reconnectAttempts);
      control.setFailoverOnServerShutdown(failoverOnShutdown);
      control.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
      control.setThreadPoolMaxSize(threadPoolMaxSize);
      control.setGroupID(groupId);
      control.setInitialMessagePacketSize(initialMessagePacketSize);
      control.setUseGlobalPools(useGlobalPools);
      control.setRetryInterval(retryInterval);
      control.setConnectionLoadBalancingPolicyClassName(connectionLoadBalancingPolicyClassName);
   }
}
