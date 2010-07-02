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
package org.jboss.test.jbossmx.compliance.monitor;

import java.util.ArrayList;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.monitor.CounterMonitor;
import javax.management.monitor.GaugeMonitor;
import javax.management.monitor.MonitorNotification;
import javax.management.monitor.StringMonitor;

import org.jboss.test.jbossmx.compliance.TestCase;
import org.jboss.test.jbossmx.compliance.monitor.support.CounterSupport;
import org.jboss.test.jbossmx.compliance.monitor.support.StringSupport;

/**
 * Basic monitor test.<p>
 *
 * The aim of these tests is to check the most common uses of the monitor
 * services.
 *
 * @author  <a href="mailto:Adrian.Brock@HappeningTimes.com">Adrian Brock</a>.
 * @author  <a href="mailto:dimitris@jboss.org">Dimitris Andreadis</a>.
 */
public class BasicTestCase
  extends TestCase
  implements NotificationListener
{
  // Attributes ----------------------------------------------------------------

  /**
   * The object name of the monitor service
   */
  ObjectName monitorName;

  /**
   * The MBean server
   */
  MBeanServer server;

  /**
   * The observed object
   */
  Object monitored;

  /**
   * The observed object name
   */
  ObjectName observedObject;

  /**
   * The observed attribute
   */
  String observedAttribute;

  /**
   * The received notifications
   */
  ArrayList receivedNotifications = new ArrayList();

  // Constructor ---------------------------------------------------------------

  public BasicTestCase(String s)
  {
    super(s);
  }

  // Tests ---------------------------------------------------------------------

  /**
   * Test simple counter notification.
   */
  public void testCounterSimpleNotification()
    throws Exception
  {
    try
    {
      monitored = new CounterSupport();
      observedObject = new ObjectName("Monitor:type=CounterSupport");
      observedAttribute = "Value";
      startCounterService(false, 0, 0, 10);
       ObjectName[] observed = (ObjectName[]) server.getAttribute(this.monitorName,
          "ObservedObjects");
       assertTrue("ObservedObjects.length == 1", observed.length == 1);
       assertTrue("ObservedObjects[0] == Monitor:type=CounterSupport",
          observed[0].equals(observedObject));

      setAttribute(null, 0);
      setAttribute(new Integer(10), 1);
      setAttribute(new Integer(9), 1);
      setAttribute(new Integer(10), 2);
    }
    finally
    {
      stopMonitorService();
    }
  }

  /**
   * Test a counter in difference mode.
   */
  public void testCounterDifferenceNotification()
    throws Exception
  {
    try
    {
      monitored = new CounterSupport();
      observedObject = new ObjectName("Monitor:type=CounterSupport");
      observedAttribute = "Value";
      startCounterService(true, 0, 0, 10);

      setAttribute(null, 0);
      setAttribute(new Integer(10), 1);
      setAttribute(new Integer(9), 1);
      setAttribute(new Integer(10), 1);
      setAttribute(new Integer(20), 2);
    }
    finally
    {
      stopMonitorService();
    }
  }

  /**
   * Test simple gauge notification high and low.
   */
  public void testGaugeSimpleBothNotification()
    throws Exception
  {
    try
    {
      monitored = new CounterSupport();
      observedObject = new ObjectName("Monitor:type=GaugeSupport");
      observedAttribute = "Value";
      startGaugeService(true, true, false, 10, 0);

      setAttribute(null, 1);
      setAttribute(new Integer(10), 2);
      setAttribute(new Integer(9), 2);
      setAttribute(new Integer(10), 2);
      setAttribute(new Integer(0), 3);
      setAttribute(new Integer(1), 3);
      setAttribute(new Integer(0), 3);
    }
    finally
    {
      stopMonitorService();
    }
  }

  /**
   * Test simple gauge notification high.
   */
  public void testGaugeSimpleHighNotification()
    throws Exception
  {
    try
    {
      monitored = new CounterSupport();
      observedObject = new ObjectName("Monitor:type=GaugeSupport");
      observedAttribute = "Value";
      startGaugeService(true, false, false, 10, 0);

      setAttribute(null, 0);
      setAttribute(new Integer(10),1 );
      setAttribute(new Integer(9), 1);
      setAttribute(new Integer(10), 1);
      setAttribute(new Integer(0), 1);
      setAttribute(new Integer(10), 2);
    }
    finally
    {
      stopMonitorService();
    }
  }

  /**
   * Test simple gauge notification low.
   */
  public void testGaugeSimpleLowNotification()
    throws Exception
  {
    try
    {
      monitored = new CounterSupport();
      observedObject = new ObjectName("Monitor:type=GaugeSupport");
      observedAttribute = "Value";
      startGaugeService(false, true, false, 10, 0);

      setAttribute(null, 1);
      setAttribute(new Integer(10), 1);
      setAttribute(new Integer(9), 1);
      setAttribute(new Integer(0), 2);
      setAttribute(new Integer(1), 2);
      setAttribute(new Integer(0), 2);
    }
    finally
    {
      stopMonitorService();
    }
  }

  /**
   * Test a String notification (both match and differ).
   */
  public void testStringBothNotification()
    throws Exception
  {
    try
    {
      monitored = new StringSupport();
      observedObject = new ObjectName("Monitor:type=StringSupport");
      observedAttribute = "Value";
      startStringService(true, true, "test");

      // until sun-bug #6200031 gets resolved, reduce the counters by one
      // not expecting a MonitorNotification.OBSERVED_ATTRIBUTE_TYPE_ERROR
      setAttribute(null, 0);
      setAttribute("test", 1);
      setAttribute("not-test", 2);
    }
    finally
    {
      stopMonitorService();
    }
  }

  /**
   * Test a String notification (just match).
   */
  public void testStringMatchNotification()
    throws Exception
  {
    try
    {
      monitored = new StringSupport();
      observedObject = new ObjectName("Monitor:type=StringSupport");
      observedAttribute = "Value";
      startStringService(true, false, "test");

      // until sun-bug #6200031 gets resolved, reduce the counters by one
      // not expecting a MonitorNotification.OBSERVED_ATTRIBUTE_TYPE_ERROR
      setAttribute(null, 0);
      setAttribute("test", 1);
      setAttribute("not-test", 1);
    }
    finally
    {
      stopMonitorService();
    }
  }

  /**
   * Test a String notification (just differ).
   */
  public void testStringDifferNotification()
    throws Exception
  {
    try
    {
      monitored = new StringSupport();
      observedObject = new ObjectName("Monitor:type=StringSupport");
      observedAttribute = "Value";
      startStringService(false, true, "test");

      // until sun-bug #6200031 gets resolved, reduce the counters by one
      // not expecting a MonitorNotification.OBSERVED_ATTRIBUTE_TYPE_ERROR
      setAttribute(null, 0);
      setAttribute("test", 0);
      setAttribute("not-test", 1);
    }
    finally
    {
      stopMonitorService();
    }
  }

  // Support functions ---------------------------------------------------------

  /**
   * Start a counter service
   * @param mode the difference mode
   * @param modulus for counters that wrap
   * @param offset the offset value
   * @param threshold the threshold value
   */
  private void startCounterService(boolean mode, int modulus,
                                   int offset, int threshold)
    throws Exception
  {
    installMonitorService(new CounterMonitor());
    AttributeList attributes = new AttributeList();
    attributes.add(new Attribute("DifferenceMode", new Boolean(mode)));
    attributes.add(new Attribute("Modulus", new Integer(modulus)));
    attributes.add(new Attribute("Offset", new Integer(offset)));
    attributes.add(new Attribute("Notify", new Boolean(true)));
    attributes.add(new Attribute("Threshold", new Integer(threshold)));
    attributes.add(new Attribute("GranularityPeriod", new Long(PERIOD)));
    attributes.add(new Attribute("ObservedObject", observedObject));
    attributes.add(new Attribute("ObservedAttribute", observedAttribute));
    int before = attributes.size();
    attributes = server.setAttributes(monitorName, attributes);
    assertEquals(before, attributes.size());

    server.invoke(monitorName, "start", new Object[0], new String[0]);
  }

  /**
   * Start a gauge service
   * @param high notify on high
   * @param low notifiy on low
   * @param high notify on high
   * @param differ difference mode
   * @param highValue high threshold
   * @param lowValue low threshold
   */
  private void startGaugeService(boolean high, boolean low, boolean differ,
                                 int highValue, int lowValue)
    throws Exception
  {
    installMonitorService(new GaugeMonitor());
    AttributeList attributes = new AttributeList();
    attributes.add(new Attribute("NotifyHigh", new Boolean(high)));
    attributes.add(new Attribute("NotifyLow", new Boolean(low)));
    attributes.add(new Attribute("DifferenceMode", new Boolean(differ)));
    attributes.add(new Attribute("GranularityPeriod", new Long(PERIOD)));
    attributes.add(new Attribute("ObservedObject", observedObject));
    attributes.add(new Attribute("ObservedAttribute", observedAttribute));
    int before = attributes.size();
    attributes = server.setAttributes(monitorName, attributes);
    assertEquals(before, attributes.size());

    server.invoke(monitorName, "setThresholds", 
      new Object[] { new Integer(highValue), new Integer(lowValue) },
      new String[] { "java.lang.Number", "java.lang.Number" });

    server.invoke(monitorName, "start", new Object[0], new String[0]);
  }

  /**
   * Start a string service
   * @param match notify on match
   * @param differ notifiy on differ
   * @param value the value to check
   */
  private void startStringService(boolean match, boolean differ,
                                   String value)
    throws Exception
  {
    installMonitorService(new StringMonitor());
    AttributeList attributes = new AttributeList();
    attributes.add(new Attribute("NotifyDiffer", new Boolean(differ)));
    attributes.add(new Attribute("NotifyMatch", new Boolean(match)));
    attributes.add(new Attribute("StringToCompare", value));
    attributes.add(new Attribute("GranularityPeriod", new Long(PERIOD)));
    attributes.add(new Attribute("ObservedObject", observedObject));
    attributes.add(new Attribute("ObservedAttribute", observedAttribute));
    int before = attributes.size();
    attributes = server.setAttributes(monitorName, attributes);
    assertEquals(before, attributes.size());

    server.invoke(monitorName, "start", new Object[0], new String[0]);
  }

  /**
   * Get an MBeanServer, install the monitor service and a notification
   * listener.
   * @param monitor the object doing the monitoring
   */
  private void installMonitorService(Object monitor)
    throws Exception
  {
    server = MBeanServerFactory.createMBeanServer("Monitor");

    monitorName = new ObjectName("Monitor:type=MonitorService");
    server.registerMBean(monitor, monitorName);

    receivedNotifications.clear();
    
    // until sun-bug #6200031 gets resolved
    // filter out OBSERVED_ATTRIBUTE_TYPE_ERROR
    NotificationFilter filter = new NotificationFilter() {
       public boolean isNotificationEnabled(Notification notification)
       {
          return !MonitorNotification.OBSERVED_ATTRIBUTE_TYPE_ERROR.equals(notification.getType());
       }
    };
    
    server.addNotificationListener(monitorName, this, filter, null);

    server.registerMBean(monitored, observedObject);
  }

  /**
   * Remove everything used by this test. Cannot report failures because
   * the test might have failed earlier.
   */
  private void stopMonitorService()
  {
    try
    {
      server.invoke(monitorName, "stop", new Object[0], new String[0]);
      server.removeNotificationListener(monitorName, this);
      server.unregisterMBean(observedObject);
      server.unregisterMBean(monitorName);
      MBeanServerFactory.releaseMBeanServer(server);
    }
    catch (Exception ignored) {}
  }

  /**
   * Set an attribute and check the correct notifications are received
   * @param value the value to set, null is past at the start - only
   *        the check is perform
   * @param expected the expected number of notifications after setting
   *        the value
   */
  private void setAttribute(Object value, int expected)
    throws Exception
  {
    // Set the attribute unless the test has just started
    if (value != null)
    {
      Attribute attribute = new Attribute(observedAttribute, value);
      server.setAttribute(observedObject, attribute);
    }

    // Wait for the notification
    synchronized (receivedNotifications)
    {
      if (receivedNotifications.size() > expected )
        fail("too many notifications");
      if (receivedNotifications.size() <= expected )
        receivedNotifications.wait(WAIT);
      assertEquals(expected, receivedNotifications.size());
    }
  }

  /**
   * Handle a notification, just add it to the list
   *
   * @param notification the notification received
   * @param handback not used
   */
  public void handleNotification(Notification notification, Object handback)
  {
    synchronized (receivedNotifications)
    {
      receivedNotifications.add(notification);
      receivedNotifications.notifyAll();
    }
  }
}
