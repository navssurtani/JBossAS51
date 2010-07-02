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
package org.jboss.mx.server.registry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import javax.management.Descriptor;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeMBeanException;
import javax.management.RuntimeOperationsException;
import javax.management.loading.ClassLoaderRepository;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;

import EDU.oswego.cs.dl.util.concurrent.ConcurrentReaderHashMap;
import EDU.oswego.cs.dl.util.concurrent.SynchronizedLong;

import org.jboss.classloading.spi.RealClassLoader;
import org.jboss.logging.Logger;
import org.jboss.mx.loading.LoaderRepository;
import org.jboss.mx.metadata.MBeanCapability;
import org.jboss.mx.modelmbean.ModelMBeanConstants;
import org.jboss.mx.modelmbean.RequiredModelMBeanInvoker;
import org.jboss.mx.modelmbean.XMBean;
import org.jboss.mx.modelmbean.XMBeanConstants;
import org.jboss.mx.server.AbstractMBeanInvoker;
import org.jboss.mx.server.MBeanInvoker;
import org.jboss.mx.server.RawDynamicInvoker;
import org.jboss.mx.server.ServerConfig;
import org.jboss.mx.server.ServerObjectInstance;
import org.jboss.util.NestedRuntimeException;

/**
 * The registry for object name - object reference mapping in the
 * MBean server.
 * <p>
 * The implementation of this class affects the invocation speed
 * directly, please check any changes for performance.
 *
 * @todo JMI_DOMAIN isn't very protected
 *
 * @see org.jboss.mx.server.registry.MBeanRegistry
 *
 * @author  <a href="mailto:juha@jboss.org">Juha Lindfors</a>.
 * @author  <a href="mailto:trevor@protocool.com">Trevor Squires</a>.
 * @author  <a href="mailto:Adrian.Brock@HappeningTimes.com">Adrian Brock</a>.
 * @author  <a href="mailto:jhaynie@vocalocity.net">Jeff Haynie</a>.
 * @author  <a href="mailto:thomas.diesler@jboss.com">Thomas Diesler</a>.
 *
 * @version $Revision: 85294 $
 */
public class BasicMBeanRegistry
   implements MBeanRegistry
{
   // Constants -----------------------------------------------------

   /** The server config */
   private static ServerConfig serverConfig = ServerConfig.getInstance();

   /** The default domain */
   private static String JMI_DOMAIN = serverConfig.getJMIDomain();

   // Attributes ----------------------------------------------------

   /**
    * A map of domain name to another map containing object name canonical
    * key properties to registry entries.
    * domain -> canonicalKeyProperties -> MBeanEntry
    */
   private Map domainMap = new ConcurrentReaderHashMap();

   /**
    * The default domain for this registry
    */
   private String defaultDomain;

   /**
    * The MBeanServer for which we are the registry.
    */
   private MBeanServer server;

   /**
    * The loader repository for loading classes
    */
   private LoaderRepository loaderRepository;

   /**
    * Sequence number for the MBean server registration notifications.
    */
   protected final SynchronizedLong registrationNotificationSequence = new SynchronizedLong (1);

   /**
    * Sequence number for the MBean server unregistration notifications.
    */
   protected final SynchronizedLong unregistrationNotificationSequence = new SynchronizedLong (1);

   /**
    * Direct reference to the mandatory MBean server delegate MBean.
    */
   protected MBeanServerDelegate delegate;

   protected Vector fMbInfosToStore;
   private ObjectName mbeanInfoService;


   // Static --------------------------------------------------------

   /**
    * The logger
    */
   protected static Logger log = Logger.getLogger(BasicMBeanRegistry.class);


   // Constructors --------------------------------------------------

   /**
    * Constructs a new BasicMBeanRegistry.<p>
    */
   public BasicMBeanRegistry(MBeanServer server, String defaultDomain, ClassLoaderRepository clr)
   {
      // Store the context
      this.server = server;
      this.defaultDomain = defaultDomain;

      try
      {
         loaderRepository = (LoaderRepository) clr;
         mbeanInfoService = new ObjectName("user:service=MBeanInfoDB");
      }
      catch (Exception e)
      {
         throw new NestedRuntimeException("Error instantiating registry", e);
      }
   }


   // MBeanRegistry Implementation ----------------------------------

   public ObjectInstance registerMBean(Object object, ObjectName name, Map valueMap)
      throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException
   {
      ObjectName regName         = name;
      boolean registrationDone   = true;
      boolean invokedPreRegister = false;
      String magicToken          = null;
      MBeanInvoker invoker       = null;

      if (object == null)
         throw new RuntimeOperationsException(
               new IllegalArgumentException("Attempting to register null object"));

      // get mbean type, dynamic or standard
      MBeanCapability mbcap = MBeanCapability.of(object.getClass());

      try
      {

         if (valueMap != null)
            magicToken = (String) valueMap.get(JMI_DOMAIN);

         // TODO: allow custom factory for diff invoker types
         int mbeanType = mbcap.getMBeanType();
         if (mbeanType == MBeanCapability.STANDARD_MBEAN)
         {
            invoker = new XMBean(object, XMBeanConstants.STANDARD_MBEAN);
         }
         else if (object instanceof MBeanInvoker)
         {
            invoker = (MBeanInvoker)object;
         }
         else if (mbeanType == MBeanCapability.DYNAMIC_MBEAN)
         {
            if( object instanceof RequiredModelMBean )
               invoker = new RequiredModelMBeanInvoker((DynamicMBean)object);
            else
               invoker = new RawDynamicInvoker((DynamicMBean)object);
         }

         // Perform the pregistration
         MBeanEntry entry = new MBeanEntry(regName, invoker, object, valueMap);
         AbstractMBeanInvoker.setMBeanEntry(entry);
         regName = invokePreRegister(invoker, regName, magicToken);
         invokedPreRegister = true;

         try
         {
            MBeanInfo info = invoker.getMBeanInfo();
            verifyMBeanInfo(info, name);
            entry.setResourceClassName(info.getClassName());

            // Register the mbean

            // Update the registered name to the final value
            entry.setObjectName(regName);

            add(entry);

            try
            {
               // Add the classloader to the repository
               if (object instanceof ClassLoader)
                  registerClassLoader((ClassLoader)object);

               try
               {
                  if (delegate != null)
                     sendRegistrationNotification (regName);
                  else if (serverConfig.getMBeanServerDelegateName().equals(name))
                     delegate = (MBeanServerDelegate) object;

                  ServerObjectInstance serverObjInst = new ServerObjectInstance
                        (regName, entry.getResourceClassName(), delegate.getMBeanServerId());

                  persistIfRequired(invoker.getMBeanInfo(), regName);

                  return serverObjInst;

               }
               catch (Throwable t)
               {
                  // Problem, remove a classloader from the repository
                  if (object instanceof ClassLoader)
                     loaderRepository.removeClassLoader((ClassLoader)object);

                  throw t;
               }
            }
            catch (Throwable t)
            {
               // Problem, remove the mbean from the registry
               remove(regName);
               throw t;
            }
         }
         // Throw for null MBeanInfo
         catch (NotCompliantMBeanException e)
         {
            throw e;
         }
         // Thrown by the registry
         catch (InstanceAlreadyExistsException e)
         {
            throw e;
         }
         catch (Throwable t)
         {
            // Something is broken
            log.error("Unexpected Exception:", t);
            throw t;
         }
      }
      catch (NotCompliantMBeanException e)
      {
         registrationDone = false;
         throw e;
      }
      catch (InstanceAlreadyExistsException e)
      {
         // It was already registered
         registrationDone = false;
         throw e;
      }
      catch (MBeanRegistrationException e)
      {
         // The MBean cancelled the registration
         registrationDone = false;
         log.warn(e.toString());
         throw e;
      }
      catch (RuntimeOperationsException e)
      {
         // There was a problem with one the arguments
         registrationDone = false;
         throw e;
      }
      catch (Exception ex)
      {
         // any other exception is mapped to NotCompliantMBeanException
         registrationDone = false;
         NotCompliantMBeanException ncex = new NotCompliantMBeanException("Cannot register MBean: " + name);
         ncex.initCause(ex);
         throw ncex;
      }
      catch (Throwable t)
      {
         // Some other error
         log.error("Cannot register MBean", t);
         registrationDone = false;
         return null;
      }
      finally
      {
         // Tell the MBean the result of the registration
         if (invoker != null)
         {
            try
            {
               invoker.postRegister(new Boolean(registrationDone));
            }
            catch(Exception e)
            {
               // Only throw this if preRegister succeeded
               if( invokedPreRegister == true )
               {
                  if( e instanceof RuntimeException )
                     throw new RuntimeMBeanException((RuntimeException) e);
                  else
                     throw new MBeanRegistrationException(e);
               }
            }
         }
         AbstractMBeanInvoker.setMBeanEntry(null);
      }
    }

   /**
    * Verifies the MBeanInfo and throws an exception if something is wrong.
    * @param info a MBeanInfo
    * @param name a ObjectName
    * @throws NotCompliantMBeanException when something is wrong with the MBean info
    */
   private void verifyMBeanInfo(MBeanInfo info, ObjectName name)
           throws NotCompliantMBeanException
   {
      try
      {
         if (info == null)
            throw new NotCompliantMBeanException("MBeanInfo cannot be null, for: " + name);

         if (info.getClassName() == null)
            throw new NotCompliantMBeanException("Classname returned from MBeanInfo cannot be null, for: " + name);
      }
      catch (NotCompliantMBeanException ncex)
      {
         throw ncex;
      }
      catch (Throwable t)
      {
         NotCompliantMBeanException ncex = new NotCompliantMBeanException("Cannot verify MBeanInfo, for: " + name);
         ncex.initCause(t);
         throw ncex;
      }
   }

   /**
     * send a MBeanServerNotification.REGISTRATION_NOTIFICATION notification
     * to regName
     *
     * @param regName
     */
    protected void sendRegistrationNotification (ObjectName regName)
    {
        long sequence = registrationNotificationSequence.increment ();
        delegate.sendNotification (
                new MBeanServerNotification (
                        MBeanServerNotification.REGISTRATION_NOTIFICATION,
                        delegate, sequence, regName));
    }

    /**
     * subclasses can override to provide their own pre-registration pre- and post- logic for
     * <tt>preRegister</tt> and must call preRegister on the MBeanRegistration instance
     *
     * @param registrationInterface
     * @param regName
     * @return object name
     * @throws Exception
     */
    protected ObjectName handlePreRegistration (MBeanRegistration registrationInterface, ObjectName regName)
            throws Exception
    {
        ObjectName mbean = registrationInterface.preRegister (server, regName);
        if (regName == null)
        {
            return mbean;
        }
        else
        {
            return regName;
        }
    }


    /**
     * subclasses can override to provide any custom preDeregister logic
     * and must call preDregister on the MBeanRegistration instance
     *
     * @param registrationInterface
     * @throws Exception
     */
    protected void handlePreDeregister (MBeanRegistration registrationInterface)
            throws Exception
    {
        registrationInterface.preDeregister ();
    }

    /**
     * Subclasses can override if they wish to control the classloader
     * registration to loader repository.
     *
     * @param cl classloader
     */
    protected void registerClassLoader(ClassLoader cl)
    {
       if( (cl instanceof RealClassLoader) == false )
       {
         // Only register non-UCLs as UCLs already have a repository
         loaderRepository.addClassLoader(cl);
       }
    }


   public void unregisterMBean(ObjectName name)
      throws InstanceNotFoundException, MBeanRegistrationException
   {
      name = qualifyName(name);
      if (name.getDomain().equals(JMI_DOMAIN))
         throw new RuntimeOperationsException(new IllegalArgumentException(
            "Not allowed to unregister: " + name.toString()));

      MBeanEntry entry = get(name);
      Object resource = entry.getResourceInstance();

      try
      {
          // allow subclasses to perform their own pre- and post- pre-deregister logic
          handlePreDeregister (entry.getInvoker());

      }
      catch (Exception e)
      {
         // don't double wrap MBeanRegistrationException
         if (e instanceof MBeanRegistrationException)
            throw (MBeanRegistrationException)e;

         throw new MBeanRegistrationException(e, "preDeregister");
      }

      // Remove any classloader
      if (resource instanceof ClassLoader)
         loaderRepository.removeClassLoader((ClassLoader)resource);

      // It is no longer registered
      remove(name);

      sendUnRegistrationNotification (name);

      entry.getInvoker().postDeregister();
   }

  /**
   * send MBeanServerNotification.UNREGISTRATION_NOTIFICATION notification to
   * name
   *
   * @param name
   */
  protected void sendUnRegistrationNotification (ObjectName name)
  {
      long sequence = unregistrationNotificationSequence.increment ();

      delegate.sendNotification (
              new MBeanServerNotification (
                      MBeanServerNotification.UNREGISTRATION_NOTIFICATION,
                      delegate,
                      sequence,
                      name
              )
      );
  }

   public MBeanEntry get(ObjectName name)
      throws InstanceNotFoundException
   {
      if (name == null)
         throw new RuntimeOperationsException(new IllegalArgumentException("null object name"));

      // Determine the domain and retrieve its entries
      String domain = name.getDomain();

      if (domain.length() == 0)
         domain = defaultDomain;

      String props = name.getCanonicalKeyPropertyListString();
      Map mbeanMap = getMBeanMap(domain, false);

      // Retrieve the mbean entry
      Object o = null;
      if (null == mbeanMap || null == (o = mbeanMap.get(props)))
         throw new InstanceNotFoundException(name + " is not registered.");

      // We are done
      return (MBeanEntry) o;
   }

   public String getDefaultDomain()
   {
      return defaultDomain;
   }

   public String[] getDomains()
   {
      ArrayList domains = new ArrayList(domainMap.size());
      for (Iterator iterator = domainMap.entrySet().iterator(); iterator.hasNext();)
      {
         Map.Entry entry = (Map.Entry) iterator.next();
         String domainName = (String) entry.getKey();
         Map mbeans = (Map) entry.getValue();
         if (mbeans != null && mbeans.isEmpty() == false)
            domains.add(domainName);
      }
      return (String[]) domains.toArray(new String[domains.size()]);
   }

   public ObjectInstance getObjectInstance(ObjectName name)
      throws InstanceNotFoundException
   {
      if (!contains(name))
         throw new InstanceNotFoundException(name + " not registered.");

      return new ServerObjectInstance(qualifyName(name),
         get(name).getResourceClassName(), delegate.getMBeanServerId());
   }

   public Object getValue(ObjectName name, String key)
      throws InstanceNotFoundException
   {
      return get(name).getValue(key);
   }

   public boolean contains(ObjectName name)
   {
      // null safety check
      if (name == null)
         return false;

      // Determine the domain and retrieve its entries
      String domain = name.getDomain();

      if (domain.length() == 0)
         domain = defaultDomain;

      String props = name.getCanonicalKeyPropertyListString();
      Map mbeanMap = getMBeanMap(domain, false);

      // Return the result
      return (null != mbeanMap && mbeanMap.containsKey(props));
   }

   public  int getSize()
   {
      int retval = 0;
       for (Iterator iterator = domainMap.values().iterator(); iterator.hasNext();)
       {
          retval += ((Map)iterator.next()).size();
       }
   	 return retval;
   }

   public List findEntries(ObjectName pattern)
   {
      ArrayList retval = new ArrayList();

      // There are a couple of shortcuts we can employ to make this a
      // bit faster - they're commented.

      // First, if pattern == null or pattern.getCanonicalName() == "*:*" we want the
      // set of all MBeans.
      if (pattern == null || pattern.getCanonicalName().equals("*:*"))
      {
          for (Iterator domainIter = domainMap.values().iterator(); domainIter.hasNext();)
             retval.addAll(((Map)domainIter.next()).values());
      }
      // Next, if !pattern.isPattern() then we are doing a simple get (maybe defaultDomain).
      else if (!pattern.isPattern())
      {
         // simple get
         try
         {
            retval.add(get(pattern));
         }
         catch (InstanceNotFoundException e)
         {
            // we don't care
         }
      }
      // Now we have to do a brute force, oh well.
      else
      {
         // Here we go, step through every domain and see if our pattern matches before optionally checking
         // each ObjectName's properties for a match.
         for (Iterator domainIter = domainMap.entrySet().iterator(); domainIter.hasNext();)
         {
            Map.Entry mapEntry = (Map.Entry) domainIter.next();
            Map value = (Map) mapEntry.getValue();
            if (value != null && value.isEmpty() == false)
            {
               for (Iterator mbeanIter = value.values().iterator(); mbeanIter.hasNext();)
               {
                  MBeanEntry entry = (MBeanEntry) mbeanIter.next();
                  if (pattern.apply(entry.getObjectName()))
                     retval.add(entry);
               }
            }
         }
      }

      return retval;
   }


   /**
    * Cleans up the registry before the MBean server is released.
    */
   public void releaseRegistry()
      // This is based on patch by Rod Burgett (Bug report: 763378)
      // Modified. Server is calling the registry.
   {
       server = null;
       delegate = null;

       //  clear each value element from the domainMap
       for (Iterator iterator = domainMap.keySet().iterator(); iterator.hasNext();)
       {
          Map nextMap = (Map) domainMap.get(iterator.next());

          if ( nextMap.size() > 0 )
          {
             nextMap.clear();
          }
       }

       domainMap.clear();
       domainMap = null;
   }


   // Protected -----------------------------------------------------

   protected ObjectName invokePreRegister(MBeanInvoker invoker, ObjectName regName, String magicToken)
      throws MBeanRegistrationException, NotCompliantMBeanException
   {

      // if we were given a non-null object name for registration, qualify it
      // and expand default domain
      if (regName != null)
         regName = qualifyName(regName);

      // store the name returned by preRegister() here
      ObjectName mbeanName = null;

      try
      {
         // invoke preregister on the invoker, it will delegate to the resource
         // if needed
         mbeanName = invoker.preRegister(server, regName);
      }
      // if during pre registration, the mbean turns out to be not compliant
      catch (NotCompliantMBeanException ncex)
      {
         throw ncex;

      }
      // catch all exceptions cause by preRegister, these will abort registration
      catch (Exception e)
      {
         if (e instanceof MBeanRegistrationException)
         {
            throw (MBeanRegistrationException)e;
         }

         throw new MBeanRegistrationException(e,
               "preRegister() failed: " +
               "[ObjectName='" + regName +
               "', Class=" + invoker.getResource().getClass().getName() +
               " (" + invoker.getResource() + ")]"
         );
      }
      catch (Throwable t)
      {
         log.warn("preRegister() failed for " + regName + ": ", t);

         if (t instanceof Error)
            throw new RuntimeErrorException((Error)t);
         else
            throw new RuntimeException(t.toString());
      }


      // if registered with null name, use the default name returned by
      // the preregister implementation
      if (regName == null)
         regName = mbeanName;

      return validateAndQualifyName(regName, magicToken);
   }

   /**
    * Adds an MBean entry<p>
    *
    * WARNING: The object name should be fully qualified.
    *
    * @param entry the MBean entry to add
    * @exception InstanceAlreadyExistsException when the MBean's object name
    *            is already registered
    */
   protected synchronized void add(MBeanEntry entry)
      throws InstanceAlreadyExistsException
   {
      // Determine the MBean's name and properties
      ObjectName name = entry.getObjectName();
      String domain = name.getDomain();
      String props = name.getCanonicalKeyPropertyListString();

      // Create a properties -> entry map if we don't have one
      Map mbeanMap = getMBeanMap(domain, true);

      // Make sure we aren't already registered
      if (mbeanMap.get(props) != null)
         throw new InstanceAlreadyExistsException(name + " already registered.");

      // Ok, we are registered
      mbeanMap.put(props, entry);
   }

   /**
    * Removes an MBean entry
    *
    * WARNING: The object name should be fully qualified.
    *
    * @param name the object name of the entry to remove
    * @exception InstanceNotFoundException when the object name is not
    *            registered
    */
   protected synchronized void remove(ObjectName name)
      throws InstanceNotFoundException
   {
      // Determine the MBean's name and properties
      String domain = name.getDomain();
      String props = name.getCanonicalKeyPropertyListString();
      Map mbeanMap = getMBeanMap(domain, false);

      // Remove the entry, raise an exception when it didn't exist
      if (null == mbeanMap || null == mbeanMap.remove(props))
         throw new InstanceNotFoundException(name + " not registered.");
   }

   /**
    * Validates and qualifies an MBean<p>
    *
    * Validates the name is not a pattern.<p>
    *
    * Adds the default domain if no domain is specified.<p>
    *
    * Checks the name is not in the reserved domain JMImplementation when
    * the magicToken is not {@link org.jboss.mx.server.ServerConstants#JMI_DOMAIN JMI_DOMAIN}
    *
    * @param name the name to validate
    * @param magicToken used to get access to the reserved domain
    * @return the original name or the name prepended with the default domain
    *         if no domain is specified.
    * @exception RuntimeOperationsException containing an
    *            IllegalArgumentException for a problem with the name
    */
   protected ObjectName validateAndQualifyName(ObjectName name,
                                               String magicToken)
   {
      // Check for qualification
      ObjectName result = qualifyName(name);

      // Make sure the name is not a pattern
      if (result.isPattern())
         throw new RuntimeOperationsException(
               new IllegalArgumentException("Object name is a pattern:" + name));

      // Check for reserved domain
      if (magicToken != JMI_DOMAIN &&
          result.getDomain().equals(JMI_DOMAIN))
         throw new RuntimeOperationsException(new IllegalArgumentException(
                     "Domain " + JMI_DOMAIN + " is reserved"));

      // I can't think of anymore tests, we're done
      return result;
   }

   /**
    * Qualify an object name with the default domain<p>
    *
    * Adds the default domain if no domain is specified.
    *
    * @param name the name to qualify
    * @return the original name or the name prepended with the default domain
    *         if no domain is specified.
    * @exception RuntimeOperationsException containing an
    *            IllegalArgumentException when there is a problem
    */
   protected ObjectName qualifyName(ObjectName name)
   {
      if (name == null)
         throw new RuntimeOperationsException(
               new IllegalArgumentException("Null object name"));
      try
      {
         if (name.getDomain().length() == 0)
            return new ObjectName(defaultDomain + ":" +
                                  name.getCanonicalKeyPropertyListString());
         else
            return name;
      }
      catch (MalformedObjectNameException e)
      {
         throw new RuntimeOperationsException(
               new IllegalArgumentException(e.toString()));
      }
   }

   /**
    * Adds the given MBean Info object to the persistence queue if it explicity denotes
    * (via metadata) that it should be stored.
    * @todo -- add notification of registration of MBeanInfoDb.
    *   It is possible that some MBeans whose MBean Info should be stored are
    *   registered before the MBean Info Storage delegate is available.  These
    *   MBeans are remembered by the registry and should be added to the storage delegate
    *   as soon as it is available.  In the current mechanism, they are added only if another
    *   MBean requesting MBean info persistence is registered after the delegate is registered.
    *   Someone more familiar with the server could make this more robust by adding
    *   a notification mechanism such that the queue is flushed as soon as the
    *   delegate is available.  - Matt Munz
    * @todo does this code need to be here? can't a notification listener be
    *       registered with the MBeanServerDelegate that stores a backlog
    *       until the service becomes available?
    * @todo the mbInfoStores is a memory leak if the service is never registered
    * @todo mbInfoStores is not synchronized correctly
    *       Thread1 adds
    *       Thread1 clones and invokes
    *       Thread2 adds
    *       Thread1 clears
    *       Thread2's add is lost
    * @todo Don't use Vector, performs too fine grained synchronization,
    *       probably not important in this case.
    */
   protected void persistIfRequired(MBeanInfo info, ObjectName name)
     throws
       MalformedObjectNameException,
       InstanceNotFoundException,
       MBeanException,
       ReflectionException
   {
     if(!(info instanceof ModelMBeanInfo))
     {
         return;
     }
     ModelMBeanInfo mmbInfo = (ModelMBeanInfo) info;
     Descriptor descriptor;
     try
     {
        descriptor = mmbInfo.getMBeanDescriptor();
     }
     catch(MBeanException cause)
     {
       log.error("Error trying to get descriptors.", cause);
       return;
     }
     if (descriptor == null)
        return;
     String persistInfo = (String) descriptor.getFieldValue(ModelMBeanConstants.PERSIST_INFO);
     if (persistInfo == null)
        return; // use default -- no persistence
     log.debug("persistInfo: " + persistInfo);
     Boolean shouldPersist = new Boolean(persistInfo);
     if(!shouldPersist.booleanValue())
     {
        return;
     }
     mbInfosToStore().add(name);
     // see if MBeanDb is available
     if(contains(mbeanInfoService))
     {
       // flush queue to the MBeanDb
       log.debug("flushing queue");
       server.invoke(
         mbeanInfoService,
         "add",
         new Object[] { mbInfosToStore().clone() },
         new String[] { mbInfosToStore().getClass().getName() });
       log.debug("clearing queue");
       mbInfosToStore().clear();
     }
     else
     {
       log.debug("service is not registered.  items remain in queue");
     }
   }

   /**
    * ObjectName objects bound to MBean Info objects that are waiting to be stored in the
    * persistence store.
    */
   protected Vector mbInfosToStore()
   {
      if(fMbInfosToStore == null)
      {
         fMbInfosToStore = new Vector(10);
      }
      return fMbInfosToStore;
   }

   /**
    * The <code>getMBeanMap</code> method provides synchronized access
    * to the mbean map for a domain.  This is actually a solution to a
    * bug that resulted in wiping out the jboss domain mbeanMap for no
    * apparent reason.
    *
    * @param domain a <code>String</code> value
    * @param createIfMissing a <code>boolean</code> value
    * @return a <code>Map</code> value
    */
   private Map getMBeanMap(String domain, boolean createIfMissing)
   {
      Map mbeanMap = (Map) domainMap.get(domain);
      if (mbeanMap == null && createIfMissing)
      {
        mbeanMap = new ConcurrentReaderHashMap();
        domainMap.put(domain, mbeanMap);
      }
      return mbeanMap;
   }
} 