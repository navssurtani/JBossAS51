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
package org.jboss.profileservice.management;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jboss.deployers.client.spi.main.MainDeployer;
import org.jboss.deployers.spi.DeploymentException;
import org.jboss.deployers.spi.management.ContextStateMapper;
import org.jboss.deployers.spi.management.DeploymentTemplate;
import org.jboss.deployers.spi.management.KnownComponentTypes;
import org.jboss.deployers.spi.management.KnownDeploymentTypes;
import org.jboss.deployers.spi.management.ManagementView;
import org.jboss.deployers.spi.management.NameMatcher;
import org.jboss.deployers.spi.management.RuntimeComponentDispatcher;
import org.jboss.logging.Logger;
import org.jboss.managed.api.ComponentType;
import org.jboss.managed.api.DeploymentState;
import org.jboss.managed.api.DeploymentTemplateInfo;
import org.jboss.managed.api.Fields;
import org.jboss.managed.api.ManagedComponent;
import org.jboss.managed.api.ManagedDeployment;
import org.jboss.managed.api.ManagedObject;
import org.jboss.managed.api.ManagedOperation;
import org.jboss.managed.api.ManagedProperty;
import org.jboss.managed.api.MutableManagedComponent;
import org.jboss.managed.api.MutableManagedObject;
import org.jboss.managed.api.RunState;
import org.jboss.managed.api.annotation.ActivationPolicy;
import org.jboss.managed.api.annotation.ManagementComponent;
import org.jboss.managed.api.annotation.ManagementObject;
import org.jboss.managed.api.annotation.ManagementObjectID;
import org.jboss.managed.api.annotation.ManagementObjectRef;
import org.jboss.managed.api.annotation.ManagementOperation;
import org.jboss.managed.api.annotation.ManagementProperties;
import org.jboss.managed.api.annotation.ManagementProperty;
import org.jboss.managed.api.annotation.ViewUse;
import org.jboss.managed.api.factory.ManagedObjectFactory;
import org.jboss.managed.plugins.ManagedComponentImpl;
import org.jboss.managed.plugins.ManagedDeploymentImpl;
import org.jboss.managed.plugins.factory.AbstractManagedObjectFactory;
import org.jboss.managed.plugins.jmx.ManagementFactoryUtils;
import org.jboss.metadata.spi.MetaData;
import org.jboss.metatype.api.types.ArrayMetaType;
import org.jboss.metatype.api.types.CollectionMetaType;
import org.jboss.metatype.api.types.MetaType;
import org.jboss.metatype.api.values.ArrayValue;
import org.jboss.metatype.api.values.CollectionValue;
import org.jboss.metatype.api.values.GenericValue;
import org.jboss.metatype.api.values.MetaValue;
import org.jboss.metatype.api.values.MetaValueFactory;
import org.jboss.metatype.api.values.SimpleValue;
import org.jboss.profileservice.spi.ManagedMBeanDeploymentFactory;
import org.jboss.profileservice.spi.ManagedMBeanDeploymentFactory.MBeanComponent;
import org.jboss.profileservice.spi.ManagedMBeanDeploymentFactory.MBeanDeployment;
import org.jboss.profileservice.spi.NoSuchDeploymentException;
import org.jboss.profileservice.spi.NoSuchProfileException;
import org.jboss.profileservice.spi.Profile;
import org.jboss.profileservice.spi.ProfileDeployment;
import org.jboss.profileservice.spi.ProfileKey;
import org.jboss.profileservice.spi.ProfileService;
import org.jboss.system.server.profileservice.attachments.AttachmentStore;

/**
 * The default ManagementView implementation.
 *
 * @author Scott.Stark@jboss.org
 * @author adrian@jboss.org
 * @author ales.justin@jboss.org
 * @author <a href="mailto:emuckenh@redhat.com">Emanuel Muckenhuber</a>
 * @version $Revision: 106289 $
 */
@ManagementObject(name="ManagementView", componentType=@ManagementComponent(type="MCBean", subtype="*"),
      properties = ManagementProperties.EXPLICIT, description = "The ProfileService ManagementView")
public class ManagementViewImpl extends AbstractTemplateCreator implements ManagementView
{
   private static RuntimePermission MV_RUNTIME_PERMISSION = new RuntimePermission(ManagementView.class.getName());

   /** The logger. */
   private static Logger log = Logger.getLogger(ManagementViewImpl.class);
   private static final String BUNDLE_NAME = "org.jboss.profileservice.management.messages"; //$NON-NLS-1$

   /** The ProfileService for loading profiles */
   private ProfileService ps;
   /** The last modified cache for loaded profiles */
   private Map<ProfileKey, Long> lastModified = new HashMap<ProfileKey, Long>();
   /** Force a reload of ManagementView. */
   private boolean forceReload;

   /** The MainDeployer only used to get the ManagedDeployments */
   private MainDeployer mainDeployer;
   /** The attachment store to persist the component changes. */
   private AttachmentStore store;

   /** The deployment templates that have been registered with the MV */
   private HashMap<String, DeploymentTemplate> templates = new HashMap<String, DeploymentTemplate>();

   /** The internationalization resource bundle */
   private ResourceBundle i18n;
   /** the Locale for the i18n messages */
   private Locale currentLocale;
   /** The formatter used for i18n messages */
   private MessageFormat formatter = new MessageFormat("");

   /** An index of ManagedComponent by ComponentType */
   private HashMap<ComponentType, Set<ManagedComponent>> compByCompType = new HashMap<ComponentType, Set<ManagedComponent>>();
   /** id/type key to ManagedObject map */
   private Map<String, ManagedObject> moRegistry = new HashMap<String, ManagedObject>();
   /** The ManagedPropertys with unresolved ManagementObjectRefs */
   private Map<String, Set<ManagedProperty>> unresolvedRefs = new HashMap<String, Set<ManagedProperty>>();
   /** A map of runtime ManagedObjects needing to be merged with their matching ManagedObject. */
   private Map<String, ManagedObject> runtimeMOs = new HashMap<String, ManagedObject>();

   /** The bootstrap deployment name to ManagedDeployment map */
   private Map<String, ManagedDeployment> bootstrapManagedDeployments = Collections.emptyMap();
   /** The deployment name to ManagedDeployment map */
   private Map<String, ManagedDeployment> managedDeployments = new HashMap<String, ManagedDeployment>();
   /** The root deployments to resolve the deployment name. */
   private List<String> rootDeployments = new ArrayList<String>();

   /** The state mappings. */
   private static final ContextStateMapper<RunState> runStateMapper;
   private static final ContextStateMapper<DeploymentState> deploymentStateMapper;

   /** The dispatcher handles ManagedOperation dispatches */
   private RuntimeComponentDispatcher dispatcher;
   /** The managed operation proxy factory. */
   private ManagedOperationProxyFactory proxyFactory;

   /** A proxy for pure JMX dispatch */
   private ManagedOperationProxyFactory mbeanProxyFactory;

   /** . */
   private MetaValueFactory metaValueFactory = MetaValueFactory.getInstance();
   /** ManagedObjectFactory used for platform mbean ManagedObjects */
   ManagedObjectFactory managedObjFactory = ManagedObjectFactory.getInstance();
   /** A map of ManagedMBeanDeploymentFactory for proxying mbeans into the management layer */
   private HashMap<String, ManagedMBeanDeploymentFactory> mdfs =
      new HashMap<String, ManagedMBeanDeploymentFactory>();

   /** The JMX Kernel for non MC managed JMXobjects */
   private MBeanServer mbeanServer;

   /** An MO Factory using MBeanInfo */
   private MBeanManagedObjectFactory mbeanMOFactory = new MBeanManagedObjectFactory();

   static
   {
      // Set default run state mappings for mc beans/mbeans
      Map<String, RunState> runStateMappings = new HashMap<String, RunState>();
      runStateMappings.put("**ERROR**", RunState.FAILED);
      runStateMappings.put("Not Installed", RunState.STOPPED);
      runStateMappings.put("PreInstall", RunState.STOPPED);
      runStateMappings.put("Described", RunState.STOPPED);
      runStateMappings.put("Instantiated", RunState.STOPPED);
      runStateMappings.put("Configured", RunState.STOPPED);
      runStateMappings.put("Create", RunState.STOPPED);
      runStateMappings.put("Start", RunState.STOPPED);
      runStateMappings.put("Installed", RunState.RUNNING);

      runStateMapper = new ContextStateMapper<RunState>(runStateMappings,
            RunState.STARTING, RunState.STOPPED, RunState.FAILED, RunState.UNKNOWN);

      Map<String, DeploymentState> deploymentMappings = new HashMap<String, DeploymentState>();
      deploymentMappings.put("**ERROR**", DeploymentState.FAILED);
      deploymentMappings.put("Not Installed", DeploymentState.STOPPED);
      deploymentMappings.put("Installed", DeploymentState.STARTED);

      deploymentStateMapper = new ContextStateMapper<DeploymentState>(deploymentMappings,
            DeploymentState.STARTING, DeploymentState.STOPPING, DeploymentState.FAILED, DeploymentState.UNKNOWN);
   }

   public ManagementViewImpl() throws IOException
   {

      currentLocale = Locale.getDefault();
      formatter.setLocale(currentLocale);
      i18n = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale);
   }

   public void start() throws Exception
   {
      // nothing
   }

   public void stop()
   {
      // Cleanup on stop
      release();
   }

   public synchronized boolean load()
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      // If the profile is not modified do nothing
      if(isReload() == false)
      {
         log.trace("Not reloading profiles.");
         return false;
      }
      this.forceReload = false;

      // Clear any thread interrupt
      boolean wasInterrupted = Thread.interrupted();
      if(wasInterrupted)
         log.debug("Cleared interrupted state of calling thread");

      // Cleanup
      release();

      //
      boolean trace = log.isTraceEnabled();

      // load the profiles
      loadProfiles(trace);

      // Process mbean components that need to be exposed as ManagedDeployment/ManagedComponent
      for(ManagedMBeanDeploymentFactory mdf : mdfs.values())
      {
         log.trace("Processing deployments for factory: "+mdf.getFactoryName());
         Collection<MBeanDeployment> deployments = mdf.getDeployments(mbeanServer);
         for(MBeanDeployment md : deployments)
         {
            log.trace("Saw MBeanDeployment: "+md);
            HashMap<String, ManagedObject> unitMOs = new HashMap<String, ManagedObject>();
            Collection<MBeanComponent> components = md.getComponents();
            if(components != null)
            {
               for(MBeanComponent comp : components)
               {
                  log.trace("Saw MBeanComponent: "+comp);
                  try
                  {
                     ManagedObject mo = createManagedObject(comp.getName(), mdf.getDefaultViewUse(), mdf.getPropertyMetaMappings());

                     String name = comp.getName().getCanonicalName();
                     ManagementObject moAnn = createMOAnnotation(name, comp.getType(), comp.getSubtype());

                     // Both the ManagementObject and ManagementComponent annotation need to be in the MO annotations
                     mo.getAnnotations().put(ManagementObject.class.getName(), moAnn);
                     ManagementComponent mcAnn = moAnn.componentType();
                     mo.getAnnotations().put(ManagementComponent.class.getName(), mcAnn);
                     unitMOs.put(name, mo);
                  }
                  catch(Exception e)
                  {
                     log.warn("Failed to create ManagedObject for: "+comp, e);
                  }
               }
            }
            ManagedDeploymentImpl mdi = new ManagedDeploymentImpl(md.getName(), md.getName(), null, unitMOs);
            mdi.setTypes(Collections.singleton("external-mbean"));
            try
            {
               processManagedDeployment(mdi, null, DeploymentState.STARTED, 0, trace);
            }
            catch(Exception e)
            {
               log.warn("Failed to process ManagedDeployment for: " + md.getName(), e);
            }
         }
      }

      // Process the bootstrap deployments
      for(ManagedDeployment md : bootstrapManagedDeployments.values())
      {
         try
         {
            //
            processManagedDeployment(md, null, DeploymentState.STARTED, 0, trace);
         }
         catch(Exception e)
         {
            log.warn("Failed to process ManagedDeployment for: " + md.getName(), e);
         }
      }
      if(this.runtimeMOs.size() > 0)
         log.warn("Failed to merged the following runtime ManagedObjects: "+runtimeMOs);

      // Now create a ManagedDeployment for the platform beans
      Map<String, ManagedObject> platformMBeanMOs = ManagementFactoryUtils.getPlatformMBeanMOs(managedObjFactory);
      ManagedDeploymentImpl platformMBeans = new ManagedDeploymentImpl("JDK PlatformMBeans", "PlatformMBeans", null,
            platformMBeanMOs);
      List<ManagedObject> gcMbeans = ManagementFactoryUtils.getGarbageCollectorMXBeans(managedObjFactory);
      Map<String, ManagedObject> gcMOs = new HashMap<String, ManagedObject>();
      for (ManagedObject mo : gcMbeans)
         gcMOs.put(mo.getName(), mo);
      List<ManagedObject> mmMbeans = ManagementFactoryUtils.getMemoryManagerMXBeans(managedObjFactory);
      Map<String, ManagedObject> mmMOs = new HashMap<String, ManagedObject>();
      for (ManagedObject mo : mmMbeans)
         mmMOs.put(mo.getName(), mo);
      List<ManagedObject> mpoolMBeans = ManagementFactoryUtils.getMemoryPoolMXBeans(managedObjFactory);
      Map<String, ManagedObject> mpoolMOs = new HashMap<String, ManagedObject>();
      for (ManagedObject mo : mpoolMBeans)
         mpoolMOs.put(mo.getName(), mo);
      ManagedDeploymentImpl gcMD = new ManagedDeploymentImpl("GarbageCollectorMXBeans", "GarbageCollectorMXBeans",
            null, gcMOs);
      platformMBeans.getChildren().add(gcMD);
      ManagedDeploymentImpl mmMD = new ManagedDeploymentImpl("MemoryManagerMXBeans", "MemoryManagerMXBeans", null, mmMOs);
      platformMBeans.getChildren().add(mmMD);
      ManagedDeploymentImpl mpoolMD = new ManagedDeploymentImpl("MemoryPoolMXBeans", "MemoryPoolMXBeans", null, mpoolMOs);
      platformMBeans.getChildren().add(mpoolMD);

      try
      {
         // Create the ManagedComponents
         processManagedDeployment(platformMBeans, null, DeploymentState.STARTED, 0, trace);
      }
      catch(Exception e)
      {
         log.warn("Failed to process ManagedDeployments for the platform beans", e);
      }

      if(wasInterrupted)
      {
         Thread.currentThread().interrupt();
         log.debug("Restored interrupted state of calling thread");
      }
      return true;
   }

   @SuppressWarnings("all")
   private static final class ManagementObjectAnnotationImpl implements ManagementObject, Serializable
   {
      private static final long serialVersionUID=5355799336353299850L;

      private final String name;
      private final String type;
      private final String subtype;

      @SuppressWarnings("all")
      private final class ManagementComponentAnnotationImpl implements ManagementComponent, Serializable
      {
         private static final long serialVersionUID=5355799336353299850L;

         public String subtype()
         {
            return subtype;
         }

         public String type()
         {
            return type;
         }

         public Class<? extends Annotation> annotationType()
         {
            return ManagementComponent.class;
         }
      }

      private ManagementObjectAnnotationImpl(String name, String type, String subtype)
      {
         this.name=name;
         this.type=type;
         this.subtype=subtype;
      }

      public String attachmentName()
      {
         return "";
      }

      public ManagementProperty[] classProperties()
      {
         return new ManagementProperty[0];
      }

      public ManagementComponent componentType()
      {
         return new ManagementComponentAnnotationImpl();
      }

      public String description()
      {
         return "";
      }

      public boolean isRuntime()
      {
         return true;
      }

      public String name()
      {
         return name;
      }

      public ManagementOperation[] operations()
      {
         return new ManagementOperation[0];
      }

      public ManagementProperties properties()
      {
         return ManagementProperties.ALL;
      }

      public Class<?> targetInterface()
      {
         return Object.class;
      }

      public String type()
      {
         return "";
      }

      public Class<? extends Annotation> annotationType()
      {
         return ManagementObject.class;
      }
   }

   private ManagementObject createMOAnnotation(final String name, final String type, final String subtype)
   {
      return new ManagementObjectAnnotationImpl(name, type, subtype);
   }

   public void reload()
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      forceReload = true;
      load();
   }

   public void release()
   {
      // Cleanup
      this.compByCompType.clear();
      this.managedDeployments.clear();
      this.moRegistry.clear();
      this.runtimeMOs.clear();
      this.unresolvedRefs.clear();
      this.lastModified.clear();
      this.rootDeployments.clear();
      // Cleanup delegate operations
      this.proxyFactory.clear();

   }

   protected void loadProfiles(boolean trace)
   {
      log.trace("reloading profiles: "+ this.ps.getActiveProfileKeys());
      for(ProfileKey key : this.ps.getActiveProfileKeys())
      {
         try
         {
            // Get the active profile
            Profile profile = this.ps.getActiveProfile(key);
            // Get the deployments
            Collection<ProfileDeployment> deployments = profile.getDeployments();
            // Add the lastModified cache
            this.lastModified.put(key, profile.getLastModified());
            // Process the deployments
            for(ProfileDeployment deployment : deployments)
            {
               try
               {
                  try
                  {
                     ManagedDeployment md = getManagedDeployment(deployment);
                     processRootManagedDeployment(md, key, trace);

                     // Cache the deployment types
                     if(md.getTypes() != null && md.getTypes().isEmpty() == false)
                        deployment.addTransientAttachment(KnownDeploymentTypes.class.getName(), md.getTypes());
                  }
                  catch(DeploymentException e)
                  {
                     // FIXME Assume a undeployed (stopped) deployment
                     String deploymentName = deployment.getName();
                     ManagedDeployment md = new ManagedDeploymentImpl(deploymentName,
                           deployment.getRoot().getName());

                     //
                     md.setAttachment(Exception.class.getName(), e);
                     
                     // Try to get the cached deployment type
                     Collection<String> deploymentTypes = (Collection<String>) deployment
                           .getTransientAttachment(KnownDeploymentTypes.class.getName());

                     if(deploymentTypes != null)
                     {
                        md.setTypes(new HashSet<String>(deploymentTypes));
                     }
                     else
                     {
                        int i = deploymentName.lastIndexOf(".");
                        if(i != -1 && (i + 1) < deploymentName.length())
                        {
                           String guessedType = deploymentName.substring(i + 1, deploymentName.length());
                           if(guessedType.endsWith("/"))
                              guessedType = guessedType.substring(0, guessedType.length() -1 );
                           md.setTypes(new HashSet<String>(1));
                           md.addType(guessedType);
                        }
                     }

                     processManagedDeployment(md, key, DeploymentState.STOPPED, 0, trace);
                  }
               }
               catch(Exception e)
               {
                  log.warn("Failed to create ManagedDeployment for: " + deployment.getName(), e);
               }
            }
         }
         catch(Exception e)
         {
            log.debug("failed to load profile " + key, e);
         }
      }
   }

   protected boolean isReload()
   {
      if(forceReload == true)
      {
         forceReload = false;
         return true;
      }

      for(ProfileKey key : this.ps.getActiveProfileKeys())
      {
         if(this.lastModified.containsKey(key) == false)
            return true;

         try
         {
            Profile profile = this.ps.getActiveProfile(key);
            long lastModified = this.lastModified.get(key);
            if(profile.getLastModified() > lastModified)
               return true;
         }
         catch(Exception ignore) { /** . */ }
      }
      return false;
   }

   /**
    * Process the root managed deployment. This gets
    * the deployment state for this deployment, which will
    * get populated to the child deployments as well.
    *
    * @param md the managed deployment
    * @param profile the associated profile key
    * @param trace is trace enabled
    * @throws Exception for any error
    */
   protected void processRootManagedDeployment(ManagedDeployment md, ProfileKey profile, boolean trace) throws Exception
   {
      DeploymentState state = getDeploymentState(md);
      processManagedDeployment(md, profile, state, 0, trace);
   }

   /**
    * Process managed deployment.
    *
    * @param md the managed deployment
    * @param profile the associated profile key
    * @param state the deployment state
    * @param level depth level
    * @param trace is trace enabled
    * @throws Exception for any error
    */
   protected void processManagedDeployment(ManagedDeployment md, ProfileKey profile, DeploymentState state, int level, boolean trace) throws Exception
   {
      String name = md.getName();
      if (trace)
         log.trace(name + " ManagedDeployment_" + level + ": " + md);
      Map<String, ManagedObject> mos = md.getManagedObjects();
      if (trace)
         log.trace(name + " ManagedObjects_ " + level + ": " + mos);

      // Set the deployment state
      if(state != null && md instanceof ManagedDeploymentImpl)
         ((ManagedDeploymentImpl)md).setDeploymentState(state);

      // Map any existing ManagedComponent types
      for(ManagedComponent comp : md.getComponents().values())
      {
         ComponentType type = comp.getType();
         Set<ManagedComponent> typeComps = compByCompType.get(type);
         if (typeComps == null)
         {
            typeComps = new HashSet<ManagedComponent>();
            compByCompType.put(type, typeComps);
         }
         typeComps.add(comp);
      }
      
      for(ManagedObject mo : mos.values())
      {
         processManagedObject(mo, md);
      }
      managedDeployments.put(name, md);

      // Associate profile with the deployment
      if(profile != null)
      {
         md.setAttachment(ProfileKey.class.getName(), profile);
      }

      // Add root deployments
      if(level == 0)
         this.rootDeployments.add(name);

      // Process children
      List<ManagedDeployment> mdChildren = md.getChildren();
      if(mdChildren != null && mdChildren.isEmpty() == false)
      {
         for(ManagedDeployment mdChild : mdChildren)
         {
            // process the child deployments, with the state of the parent.
            processManagedDeployment(mdChild, profile, state, level + 1, trace);
         }
      }
   }

   /**
    * Process managed object.
    *
    * @param mo the managed object
    * @param md the managed deployment
    */
   protected void processManagedObject(ManagedObject mo, ManagedDeployment md)
      throws Exception
   {
      String key = mo.getName() + "/" + mo.getNameType();
      log.trace("ID for ManagedObject: "+key+", attachmentName: "+mo.getAttachmentName());

      // See if this is a runtime ManagedObject
      Map<String, Annotation> moAnns = mo.getAnnotations();
      
      // Create ManagedComponents for ManagedObjects annotated with ManagementComponent
      ManagementComponent mc = (ManagementComponent) moAnns.get(ManagementComponent.class.getName());
      if (mc != null && md.getComponent(mo.getName()) == null)
      {
         ComponentType type = new ComponentType(mc.type(), mc.subtype());
         MutableManagedComponent comp = new ManagedComponentImpl(type, md, mo);
         
         if(mo.getTransientAttachment(MBeanRuntimeComponentDispatcher.class.getName()) == null)
            comp = this.proxyFactory.createComponentProxy(comp);
         
         md.addComponent(mo.getName(), comp);
         log.trace("Processing ManagementComponent("+mo.getName()+"): "+comp);
         Set<ManagedComponent> typeComps = compByCompType.get(type);
         if (typeComps == null)
         {
            typeComps = new HashSet<ManagedComponent>();
            compByCompType.put(type, typeComps);
         }
         typeComps.add(comp);
         RunState state = updateRunState(mo, comp);
      }

      ManagementObject managementObject = (ManagementObject) moAnns.get(ManagementObject.class.getName());
      if (managementObject != null && managementObject.isRuntime())
      {
         boolean merged = false;
         ManagementComponent component = managementObject.componentType();
         boolean isMC = !(component.type().length() == 0 && component.subtype().length() == 0);

         // Merge this with the ManagedObject
         ManagedObject parentMO = moRegistry.get(key);
         if (parentMO == null && isMC == false)
         {
            log.trace("Deferring resolution of runtime ManagedObject: "+managementObject);
            // Save the runtime mo for merging
            runtimeMOs.put(key, mo);
         }
         else
         {
            mergeRuntimeMO(parentMO, mo);
            merged = true;
            runtimeMOs.remove(key);
         }
         // Update the runtime state of any ManagedComponent associated with this runtime mo
         ManagedComponent comp = md.getComponent(mo.getName());
         if (comp != null)
         {
            RunState state = updateRunState(mo, comp);
            log.trace("Updated component: "+comp+" run state to: "+state);
         }
         // There is no further processing of runtime ManagedObjects, unless its marked as a component
         if (isMC == false)
            return;
         //
         else if (merged == false)
         {
            Set<ManagedOperation> runtimeOps = mo.getOperations();
            runtimeOps = createOperationProxies(mo, runtimeOps);
            MutableManagedObject moi = (MutableManagedObject) mo;
            moi.setOperations(runtimeOps);
         }
      }
      else
      {
         // See if there is runtime info to merge
         ManagedObject runtimeMO = runtimeMOs.get(key);
         if (runtimeMO != null)
         {
            mergeRuntimeMO(mo, runtimeMO);
            runtimeMOs.remove(key);
            // Update the runtime state of any ManagedComponent associated with this runtime mo
            ManagedComponent comp = md.getComponent(mo.getName());
            if (comp != null)
            {
               RunState state = updateRunState(runtimeMO, comp);
               log.trace("Updated component: "+comp+" run state to: "+state);
            }
         }
      }

      // Update the MO registry
      // TODO - does this make sense? In case of a MetaType.isCollection we could get different results then
//      ManagedObject prevMO = moRegistry.put(key, mo);
//      if( prevMO != null )
//      {
//         // This should only matter for ManagedObjects that have a ManagementObjectID
//         log.trace("Duplicate mo for key: "+key+", prevMO: "+prevMO);
//         return;
//      }
      // Check for unresolved refs
      checkForReferences(key, mo);

      // Scan for @ManagementObjectRef
      for(ManagedProperty prop : mo.getProperties().values())
      {
         log.trace("Checking property: "+prop);
         // See if this is a ManagementObjectID
         Map<String, Annotation> pannotations = prop.getAnnotations();
         if (pannotations != null && pannotations.isEmpty() == false)
         {
            ManagementObjectID id = (ManagementObjectID) pannotations.get(ManagementObjectID.class.getName());
            if (id != null)
            {
               Object refName = getRefName(prop.getValue());
               if (refName == null)
                  refName = id.name();
               String propKey = refName + "/" + id.type();
               log.trace("ManagedProperty level ID for ManagedObject: "+propKey+", attachmentName: "+mo.getAttachmentName());
               moRegistry.put(propKey, mo);
               checkForReferences(propKey, mo);
            }
            // See if this is a ManagementObjectRef
            ManagementObjectRef ref = (ManagementObjectRef) pannotations.get(ManagementObjectRef.class.getName());
            if ( ref != null )
            {
               // The reference key is the prop value + ref.type()
               log.trace("Property("+prop.getName()+") references: "+ref);
               Object refName = getRefName(prop.getValue());
               if (refName == null)
                  refName = ref.name();
               String targetKey = refName + "/" + ref.type();
               ManagedObject target = moRegistry.get(targetKey);
               if (target != null)
               {
                  log.trace("Resolved property("+prop.getName()+") reference to: "+targetKey);
                  prop.setTargetManagedObject(target);
               }
               else
               {
                  Set<ManagedProperty> referers =  unresolvedRefs.get(targetKey);
                  if (referers == null)
                  {
                     referers = new HashSet<ManagedProperty>();
                     unresolvedRefs.put(targetKey, referers);
                  }
                  referers.add(prop);
               }
            }
         }

         MetaType propType = prop.getMetaType();
         if (propType == AbstractManagedObjectFactory.MANAGED_OBJECT_META_TYPE)
         {
            processGenericValue ((GenericValue)prop.getValue(), md);
         }
         else if (propType.isArray())
         {
            ArrayMetaType amt = (ArrayMetaType) propType;
            MetaType etype = amt.getElementType();
            if (etype == AbstractManagedObjectFactory.MANAGED_OBJECT_META_TYPE)
            {
               ArrayValue avalue = (ArrayValue) prop.getValue();
               int length = avalue != null ? avalue.getLength() : 0;
               for(int n = 0; n < length; n ++)
                  processGenericValue((GenericValue) avalue.getValue(n), md);
            }
         }
         else if (propType.isCollection())
         {
            CollectionMetaType amt = (CollectionMetaType) propType;
            MetaType etype = amt.getElementType();
            if (etype == AbstractManagedObjectFactory.MANAGED_OBJECT_META_TYPE)
            {
               CollectionValue avalue = (CollectionValue) prop.getValue();
               if(avalue != null)
               {
                  MetaValue[] elements = avalue.getElements();
                  for(int n = 0; n < avalue.getSize(); n ++)
                  {
                     GenericValue gv = (GenericValue) elements[n];
                     ManagedObject propMO = (ManagedObject) gv.getValue();
                     if(propMO != null)
                        processManagedObject(propMO, md);
                  }
               }
            }
         }
      }
   }

   /**
    * Get ref name.
    *
    * @param value property value
    * @return plain value
    */
   protected Object getRefName(Object value)
   {
      if (value instanceof MetaValue)
      {
         MetaValue metaValue = (MetaValue)value;
         if (metaValue.getMetaType().isSimple() == false)
            throw new IllegalArgumentException("Can only get ref from simple value: " + value);
         SimpleValue svalue = (SimpleValue) metaValue;
         return svalue.getValue();
      }
      return value;
   }

   protected RunState updateRunState(ManagedObject runtimeMO, ManagedComponent comp)
   {
      RunState state = comp.getRunState();
      if (state == RunState.UNKNOWN && dispatcher != null)
      {
         Object name = comp.getComponentName();
         if (name == null && runtimeMO != null)
            name = runtimeMO.getComponentName();
         if (name != null)
         {
            state = getComponentMappedState(comp, runtimeMO, name, runStateMapper);
            if (comp instanceof MutableManagedComponent)
            {
               MutableManagedComponent mcomp = MutableManagedComponent.class.cast(comp);
               mcomp.setRunState(state);
            }
         }
      }
      return state;
   }

   protected DeploymentState getDeploymentState(ManagedDeployment md)
   {
      DeploymentState state = md.getDeploymentState();
      if(state == DeploymentState.UNKNOWN && dispatcher != null)
      {
         Object name = md.getName();
         if(name != null)
         {
            state = getMappedState(name, deploymentStateMapper);
         }
      }
      return state;
   }

   protected <T extends Enum<?>> T getMappedState(Object name, ContextStateMapper<T> mapper)
   {
      T state = mapper.getErrorState();
      try
      {
         if(dispatcher != null)
         {
            state = dispatcher.mapControllerState(name, mapper);
         }
      }
      catch(Exception e)
      {
         log.debug("Failed to get controller state", e);
      }
      return state;
   }

   protected <T extends Enum<?>> T getComponentMappedState(ManagedComponent comp, ManagedObject mo, Object name, ContextStateMapper<T> mapper)
   {
      T state = mapper.getErrorState();
      try
      {
         RuntimeComponentDispatcher dispatcher;
         if (mo != null && mo.getTransientAttachment(MBeanRuntimeComponentDispatcher.class.getName()) != null)
         {
            dispatcher = mbeanProxyFactory.getDispatcher();
         }
         else
         {
            dispatcher = this.dispatcher;
         }

         if (dispatcher != null)
         {
            state = dispatcher.mapControllerState(name, mapper);
         }
      }
      catch(Exception e)
      {
         log.debug("Failed to get controller state", e);
      }
      return state;
   }

   /**
    * Process generic value.
    *
    * @param genericValue the generic value
    * @param md the managed deployment
    * @throws Exception for any error
    */
   protected void processGenericValue(GenericValue genericValue, ManagedDeployment md) throws Exception
   {
      // TODO: a null is probably an error condition
      if (genericValue != null)
      {
         ManagedObject propMO = (ManagedObject) genericValue.getValue();
         // TODO: a null is probably an error condition
         if (propMO != null)
            processManagedObject(propMO, md);
      }
   }


   public Map<String, ManagedDeployment> getBootstrapManagedDeployments()
   {
      return bootstrapManagedDeployments;
   }
   public void setBootstrapManagedDeployments(
         Map<String, ManagedDeployment> bootstrapManagedDeployments)
   {
      this.bootstrapManagedDeployments = bootstrapManagedDeployments;
   }

   public ProfileService getProfileService()
   {
      return ps;
   }

   public void setProfileService(ProfileService ps)
   {
      this.ps = ps;
      if(log.isTraceEnabled())
         log.trace("setProfileService: "+ps);
   }

   public ManagedOperationProxyFactory getProxyFactory()
   {
      return proxyFactory;
   }

   public void setProxyFactory(ManagedOperationProxyFactory proxyFactory)
   {
      this.proxyFactory = proxyFactory;
   }

   public AttachmentStore getAttachmentStore()
   {
      return store;
   }

   public void setAttachmentStore(AttachmentStore store)
   {
      this.store = store;
   }

   public MainDeployer getMainDeployer()
   {
      return mainDeployer;
   }

   public void setMainDeployer(MainDeployer mainDeployer)
   {
      this.mainDeployer = mainDeployer;
      if(log.isTraceEnabled())
         log.trace("setMainDeployer: "+mainDeployer);
   }

   public MetaValueFactory getMetaValueFactory()
   {
      return metaValueFactory;
   }
   public void setMetaValueFactory(MetaValueFactory metaValueFactory)
   {
      this.metaValueFactory = metaValueFactory;
   }

   public ManagedObjectFactory getManagedObjFactory()
   {
      return managedObjFactory;
   }
   public void setManagedObjFactory(ManagedObjectFactory managedObjFactory)
   {
      this.managedObjFactory = managedObjFactory;
   }

   public void setDispatcher(RuntimeComponentDispatcher dispatcher)
   {
      this.dispatcher = dispatcher;
   }


   public MBeanServer getMbeanServer()
   {
      return mbeanServer;
   }

   public void setMbeanServer(MBeanServer mbeanServer)
   {
      this.mbeanServer = mbeanServer;
   }


   public MBeanManagedObjectFactory getMbeanMOFactory()
   {
      return mbeanMOFactory;
   }

   public void setMbeanMOFactory(MBeanManagedObjectFactory mbeanMOFactory)
   {
      this.mbeanMOFactory = mbeanMOFactory;
   }

   /**
    * Get the names of the deployment in the profile.
    */
   public Set<String> getDeploymentNames()
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      return new TreeSet<String>(this.managedDeployments.keySet());
   }

   /**
    * Get the names of the deployment in the profile that have the
    * given deployment type.
    *
    * @param type - the deployment type
    */
   public Set<String> getDeploymentNamesForType(String type)
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

     Set<String> matches = new TreeSet<String>();
      for(ManagedDeployment md : managedDeployments.values())
      {
         String name = md.getName();
         Set<String> types = md.getTypes();
         if(types != null)
         {
            if(types.contains(type))
            {
               if(log.isTraceEnabled())
                  log.trace(name+" matches type: "+type+", types:"+types);
               matches.add(name);
            }
         }
      }
      return matches;
   }

   public Set<String> getMatchingDeploymentName(String regex)
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
      {
         sm.checkPermission(MV_RUNTIME_PERMISSION);
      }
      if(regex == null)
      {
         throw new IllegalArgumentException("null regex");
      }
      Set<String> names = getDeploymentNames();
      HashSet<String> matches = new HashSet<String>();
      Pattern p = Pattern.compile(regex);
      for(String name : names)
      {
         Matcher m = p.matcher(name);
         if( m.matches() )
         {
            matches.add(name);
         }
      }
      return matches;
   }
   
   public Set<ManagedDeployment> getMatchingDeployments(String name, NameMatcher<ManagedDeployment> matcher)
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
      {
         sm.checkPermission(MV_RUNTIME_PERMISSION);
      }
      if(name == null)
      {
         throw new IllegalArgumentException("null deployment name");
      }
      if(matcher == null)
      {
         throw new IllegalArgumentException("null deployment matcher");
      }
      Set<ManagedDeployment> matches = new HashSet<ManagedDeployment>();
      for(ManagedDeployment deployment : this.managedDeployments.values())
      {
         if(matcher.matches(deployment, name))
         {
            matches.add(deployment);
         }
      }
      return matches;
   }

   public Set<String> getTemplateNames()
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      return new HashSet<String>(templates.keySet());
   }

   public void addManagedMBeanDeployments(ManagedMBeanDeploymentFactory factory)
   {
      log.trace("addManagedDeployment, "+factory);
      String name = factory.getFactoryName();
      this.mdfs.put(name, factory);
   }
   public void removeManagedMBeanDeployments(ManagedMBeanDeploymentFactory factory)
   {
      log.trace("removeManagedDeployment, "+factory);
      String name = factory.getFactoryName();
      this.mdfs.remove(name);
   }

   public void addTemplate(DeploymentTemplate template)
   {
      this.templates.put(template.getInfo().getName(), template);
      log.trace("addTemplate: "+template);
   }

   public void removeTemplate(DeploymentTemplate template)
   {
      this.templates.remove(template.getInfo().getName());
      log.trace("removeTemplate: "+template);
   }


   /**
    * Get the managed deployment.
    *
    * @param name the deployment name
    * @throws NoSuchDeploymentException if no matching deployment was found
    */
   public ManagedDeployment getDeployment(String name) throws NoSuchDeploymentException
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      if(name == null)
         throw new IllegalArgumentException("Null deployment name");

      // Resolve internally.
      ManagedDeployment md = this.managedDeployments.get(name);
      if (md == null)
      {
         // Check the bootstrap deployments
         md = this.bootstrapManagedDeployments.get(name);
      }

      // Check the file name
      if(md == null)
      {
         for(String deployment : this.rootDeployments)
         {
            String fixedDeploymentName = deployment;
            if(deployment.endsWith("/"))
               fixedDeploymentName = deployment.substring(0, deployment.length() - 1);

            if(fixedDeploymentName.endsWith(name))
            {
               md = this.managedDeployments.get(deployment);
               break;
            }
         }
      }
      // Do not return null
      if (md == null)
         throw new NoSuchDeploymentException("Managed deployment: " + name + " not found.");

      return md;
   }

   /**
    *
    * @param key
    * @param type
    * @return
    * @throws NoSuchProfileException
    */
   public Set<ManagedDeployment> getDeploymentsForType(String type)
      throws Exception
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      Set<String> names = getDeploymentNamesForType(type);
      HashSet<ManagedDeployment> mds = new HashSet<ManagedDeployment>();
      for(String name : names)
      {
         ManagedDeployment md = getDeployment(name);
         mds.add(md);
      }
      return mds;
   }

   /**
    * Get a set of the component types in use in the profiles
    * @return set of component types in use
    */
   public Set<ComponentType> getComponentTypes()
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      HashSet<ComponentType> types = new HashSet<ComponentType>(compByCompType.keySet());
      return types;
   }

   /**
    *
    * @param key
    * @param type
    * @return
    * @throws NoSuchProfileException
    */
   public Set<ManagedComponent> getComponentsForType(ComponentType type)
      throws Exception
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

     Set<ManagedComponent> comps = null;
      // Check the any component type
      if(type.equals(KnownComponentTypes.ANY_TYPE))
      {
         HashSet<ManagedComponent> all = new HashSet<ManagedComponent>();
         for(Set<ManagedComponent> typeComps : compByCompType.values())
         {
            for(ManagedComponent comp : typeComps)
            {
               all.add(comp);
            }
         }
         comps = all;
      }
      else
      {
        comps = compByCompType.get(type);
      }
      if(comps == null)
         comps = Collections.emptySet();
      return comps;
   }

   public ManagedComponent getComponent(String name, ComponentType type)
      throws Exception
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      Set<ManagedComponent> components = compByCompType.get(type);
      ManagedComponent comp = null;
      if(components != null)
      {
         for(ManagedComponent mc : components)
         {
            if(mc.getName().equals(name))
            {
               comp = mc;
               break;
            }
         }
      }
      if(comp != null)
      {
         Map<String, ManagedProperty> props = comp.getProperties();
         Set<ManagedOperation> ops = comp.getOperations();
         if(log.isTraceEnabled())
            log.trace("Component"
               +"(ops.size="
               +ops != null ? ops.size() : 0
               +",props.size=)"
               +props != null ? props.size() : 0);
      }
      return comp;
   }
   public Set<ManagedComponent> getMatchingComponents(String name, ComponentType type,
         NameMatcher<ManagedComponent> matcher)
      throws Exception
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      Set<ManagedComponent> components = compByCompType.get(type);
      Set<ManagedComponent> matched = new HashSet<ManagedComponent>();
      if(components != null)
      {
         for(ManagedComponent mc : components)
         {
            if(matcher.matches(mc, name))
               matched.add(mc);
         }
      }
      if(matched.size() > 0)
      {
         if(log.isTraceEnabled())
            log.trace("getComponents matched: "+matched);
      }
      return matched;
   }

   public DeploymentTemplateInfo getTemplate(String name)
      throws NoSuchDeploymentException
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      DeploymentTemplate template = templates.get(name);
      if( template == null )
      {
         formatter.applyPattern(i18n.getString("ManagementView.NoSuchTemplate")); //$NON-NLS-1$
         Object[] args = {name};
         String msg = formatter.format(args);
         throw new IllegalStateException(msg);
      }

      // Make sure to return a copy to avoid call by reference uses modifying the template values
      DeploymentTemplateInfo info = template.getInfo();
      info = info.copy();
      if(log.isTraceEnabled())
         log.trace("getTemplate, "+info);
      return info;
   }

   public void applyTemplate(String deploymentBaseName, DeploymentTemplateInfo info)
      throws Exception
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      if(deploymentBaseName == null)
         throw new IllegalArgumentException("Null deployment base name.");
      if(info == null)
         throw new IllegalArgumentException("Null template info.");

      DeploymentTemplate template = templates.get(info.getName());
      if( template == null )
      {
         formatter.applyPattern(i18n.getString("ManagementView.NoSuchTemplate")); //$NON-NLS-1$
         Object[] args = {info.getName()};
         String msg = formatter.format(args);
         throw new IllegalStateException(msg);
      }

      // Create a deployment base from the template
      if( log.isTraceEnabled() )
         log.trace("applyTemplate, deploymentBaseName="+deploymentBaseName +", info="+info);

      // Create, distribute and start a deployment template
      String deploymentName = super.applyTemplate(template, deploymentBaseName, info);

      // Process the deployment
      ManagedDeployment md = getMainDeployer().getManagedDeployment(deploymentName);
      processRootManagedDeployment(md, getDefaulProfiletKey(), log.isTraceEnabled());
   }

   public void process() throws DeploymentException
   {
      //
   }

   /**
    * Process a component update.
    */
   public void updateComponent(ManagedComponent comp)
      throws Exception
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      if(comp == null)
         throw new IllegalArgumentException("Null managed component.");
      // Find the comp deployment
      ManagedDeployment md = comp.getDeployment();

      // Get the parent
      while( md.getParent() != null )
         md = md.getParent();

      String name = md.getName();
      ProfileDeployment compDeployment = getProfileDeployment(name);
      if( compDeployment == null )
      {
         formatter.applyPattern(i18n.getString("ManagementView.NoSuchDeploymentException")); //$NON-NLS-1$
         Object[] args = {name};
         String msg = formatter.format(args);
         throw new NoSuchDeploymentException(msg);
      }

      // Apply the managed properties to the server ManagedDeployment/ManagedComponent
      ManagedDeployment compMD = managedDeployments.get(md.getName());
      log.trace("updateComponent, deploymentName="+name+": "+compMD);

      ManagedComponent serverComp = null;
      // Find the managed component again
      if(comp.getDeployment().getParent() == null)
      {
         serverComp = compMD.getComponent(comp.getName());
      }
      else
      {
         // Look at the children
         // TODO - support more levels of nested deployments ?
         if(compMD.getChildren() != null && compMD.getChildren().isEmpty() == false)
         {
            for(ManagedDeployment child : compMD.getChildren())
            {
               if(serverComp != null)
                  break;

               serverComp = child.getComponent(comp.getName());
            }
         }
      }
      if(serverComp == null)
      {
         log.debug("Name: "+comp.getName()+" does not map to existing ManagedComponet in ManagedDeployment: "+md.getName()
               + ", components: "+compMD.getComponents());
         formatter.applyPattern(i18n.getString("ManagementView.InvalidComponentName")); //$NON-NLS-1$
         Object[] args = {comp.getName(), md.getName()};
         String msg = formatter.format(args);
         throw new IllegalArgumentException(msg);
      }

      // Dispatch any runtime component property values
      for(ManagedProperty prop : comp.getProperties().values())
      {
         // Skip null values && non-CONFIGURATION values, unmodified values, and removed values
         boolean skip = prop.getValue() == null
            || prop.isReadOnly()
            || prop.hasViewUse(ViewUse.CONFIGURATION) == false
//            || prop.isModified() == false
            || prop.isRemoved() == true;
         if( skip )
         {
            if(log.isTraceEnabled())
               log.trace("Skipping component property: "+prop);
            continue;
         }

         ManagedProperty ctxProp = serverComp.getProperties().get(prop.getName());
         // Check for a mapped name
         if( ctxProp == null )
         {
            String mappedName = prop.getMappedName();
            if( mappedName != null )
               ctxProp = serverComp.getProperties().get(mappedName);
         }
         if( ctxProp == null )
         {
            formatter.applyPattern(i18n.getString("ManagementView.InvalidTemplateProperty")); //$NON-NLS-1$
            Object[] args = {prop.getName()};
            String msg = formatter.format(args);
            throw new IllegalArgumentException(msg);
         }
         // The property value must be a MetaValue
         Object value = prop.getValue();
         if ((value instanceof MetaValue) == false)
         {
            formatter.applyPattern(i18n.getString("ManagementView.InvalidPropertyValue")); //$NON-NLS-1$
            Object[] args = {prop.getName(), value.getClass()};
            String msg = formatter.format(args);
            throw new IllegalArgumentException(msg);
         }
         // Update the serverComp
         MetaValue metaValue = (MetaValue)value;
         ctxProp.setField(Fields.META_TYPE, metaValue.getMetaType());
         ctxProp.setValue(metaValue);

         // Dispatch any runtime component property values
         Object componentName = getComponentName(ctxProp);
         ActivationPolicy policy = ctxProp.getActivationPolicy();

         if (componentName != null && policy.equals(ActivationPolicy.IMMEDIATE))
         {
            AbstractRuntimeComponentDispatcher.setActiveProperty(ctxProp);
            dispatcher.set(componentName, ctxProp.getName(), metaValue);
         }
      }

      // Persist the changed values
      this.store.updateDeployment(comp.getDeployment().getName(), serverComp);
      // Force reload
      this.forceReload = true;
   }

   public void removeComponent(ManagedComponent comp) throws Exception
   {
      SecurityManager sm = System.getSecurityManager();
      if(sm != null)
         sm.checkPermission(MV_RUNTIME_PERMISSION);

      if(comp == null)
         throw new IllegalArgumentException("null managed component.");
      //
      ManagedDeployment md = comp.getDeployment();

      // Get the parent
      while( md.getParent() != null )
         md = md.getParent();

      String name = md.getName();
      ProfileDeployment profileDeployment = getProfileDeployment(name);
      if( profileDeployment == null )
      {
         formatter.applyPattern(i18n.getString("ManagementView.NoSuchDeploymentException")); //$NON-NLS-1$
         Object[] args = {name};
         String msg = formatter.format(args);
         throw new NoSuchDeploymentException(msg);
      }

      // Apply the managed properties to the server ManagedDeployment/ManagedComponent
      ManagedDeployment compMD = managedDeployments.get(md.getName());
      log.trace("updateComponent, deploymentName="+name+": "+compMD);

      ManagedComponent serverComp = null;
      // Find the managed component again
      if(comp.getDeployment().getParent() == null)
      {
         serverComp = compMD.getComponent(comp.getName());
      }
      else
      {
         // Look at the children
         // TODO - support more levels of nested deployments ?
         if(compMD.getChildren() != null && compMD.getChildren().isEmpty() == false)
         {
            for(ManagedDeployment child : compMD.getChildren())
            {
               if(serverComp != null)
                  break;

               serverComp = child.getComponent(comp.getName());
            }
         }
      }
      if(serverComp == null)
      {
         log.debug("Name: "+comp.getName()+" does not map to existing ManagedComponet in ManagedDeployment: "+md.getName()
               + ", components: "+compMD.getComponents());
         formatter.applyPattern(i18n.getString("ManagementView.InvalidComponentName")); //$NON-NLS-1$
         Object[] args = {comp.getName(), md.getName()};
         String msg = formatter.format(args);
         throw new IllegalArgumentException(msg);
      }

      //
      log.trace("remove component: " + comp + ", deployment: "+ profileDeployment);
      // Remove
      this.store.removeComponent(comp.getDeployment().getName(), serverComp);
   }

   /**
    * Get the component name from managed property.
    *
    * @param property the managed property
    * @return component name or null if no coresponding component
    */
   protected Object getComponentName(ManagedProperty property)
   {
      // first check target
      ManagedObject targetObject = property.getTargetManagedObject();
      if (targetObject != null)
         return targetObject.getComponentName();

      // check owner
      targetObject = property.getManagedObject();
      return targetObject != null ? targetObject.getComponentName() : null;
   }

   protected void checkForReferences(String key, ManagedObject mo)
   {
      Set<ManagedProperty> referers =  unresolvedRefs.get(key);
      log.trace("checkForReferences, "+key+" has referers: "+referers);
      if (referers != null)
      {
         for(ManagedProperty prop : referers)
         {
            prop.setTargetManagedObject(mo);
         }
         unresolvedRefs.remove(key);
      }
   }

   /**
    * Merge the and proxy runtime props and ops
    *
    * @param mo - the parent managed object to merge into. May be null if the
    * runtimeMO is a self contained managed object as is the case for runtime
    * components.
    * @param runtimeMO - the managed object with isRuntime=true to merge/proxy
    * properties and operations for.
    */
   protected void mergeRuntimeMO(ManagedObject mo, ManagedObject runtimeMO)
      throws Exception
   {
      Map<String, ManagedProperty> runtimeProps = runtimeMO.getProperties();
      Set<ManagedOperation> runtimeOps = runtimeMO.getOperations();
      // Get the runtime MO component name
      Object componentName = runtimeMO.getComponentName();
      log.debug("Merging runtime: "+runtimeMO.getName()+", compnent name: "+componentName);
      Map<String, ManagedProperty> moProps = null;
      Set<ManagedOperation> moOps = null;
      HashMap<String, ManagedProperty> props = null;
      HashSet<ManagedOperation> ops = null;
      // If mo is null, the merge target is the runtimeMO
      if (mo == null)
      {
         // Just proxy the runtime props/ops
         mo = runtimeMO;
         moProps = mo.getProperties();
         moOps = mo.getOperations();
         // These will be updated with the proxied values, don't duplicate props/ops
         props = new HashMap<String, ManagedProperty>();
         ops = new HashSet<ManagedOperation>();
      }
      else
      {
         // Merge the runtime props/ops
         moProps = mo.getProperties();
         moOps = mo.getOperations();
         props = new HashMap<String, ManagedProperty>(moProps);
         ops = new HashSet<ManagedOperation>(moOps);
      }

      boolean trace = log.isTraceEnabled();
      if (runtimeProps != null && runtimeProps.size() > 0)
      {
         if(trace)
            log.trace("Properties before:"+props);
         // We need to pull the runtime values for stats
         for(ManagedProperty prop : runtimeProps.values())
         {
            if(prop.hasViewUse(ViewUse.STATISTIC))
            {
               String propName = prop.getMappedName();
               try
               {
                  AbstractRuntimeComponentDispatcher.setActiveProperty(prop);
                  MetaValue propValue = dispatcher.get(componentName, propName);
                  if(propValue != null)
                     prop.setValue(propValue);
               }
               catch(Throwable t)
               {
                  log.debug("Failed to get stat value, "+componentName+":"+propName);
               }
               ManagedProperty proxiedProp = createPropertyProxy(prop);
               props.put(prop.getName(), proxiedProp);
            }
            else
            {
               props.put(prop.getName(), prop);
            }
            // Keep the property associated with the runtime MO for invocations/updates
            if (prop.getTargetManagedObject() == null)
               prop.setTargetManagedObject(runtimeMO);
         }

         if(trace)
            log.trace("Properties after:"+props);
      }
      if (runtimeOps != null && runtimeOps.size() > 0)
      {
         if(trace)
            log.trace("Ops before:"+ops);
         runtimeOps = createOperationProxies(runtimeMO, runtimeOps);
         ops.addAll(runtimeOps);
         if(trace)
            log.trace("Ops after:"+ops);
      }

      MutableManagedObject moi = (MutableManagedObject) mo;
      moi.setProperties(props);
      moi.setOperations(ops);
   }

   private ManagedProperty createPropertyProxy(ManagedProperty prop)
      throws Exception
   {
      if (proxyFactory == null)
         throw new IllegalArgumentException("Missing RuntimeComponentDispatcher.");

      // Create the delegate property
      Object componentName = prop.getManagedObject().getComponentName();

      if (prop.getManagedObject().getTransientAttachment(MBeanRuntimeComponentDispatcher.class.getName()) != null)
         return mbeanProxyFactory.createPropertyProxy(prop, componentName);

      return proxyFactory.createPropertyProxy(prop, componentName);
   }

   /**
    * Create ManagedOperation wrapper to intercept
    * its invocation, pushing the actual invocation
    * to runtime component.
    *
    * @param mo the managed object
    * @param ops the managed operations
    * @return set of wrapped managed operations
    * @throws Exception for any error
    * @see #
    */
   protected Set<ManagedOperation> createOperationProxies(ManagedObject mo, Set<ManagedOperation> ops)
      throws Exception
   {
      if (proxyFactory == null)
         throw new IllegalArgumentException("Missing RuntimeComponentDispatcher.");

      Object componentName = mo.getComponentName();
      return createOperationProxies(ops, componentName);
   }

   protected Set<ManagedOperation> createOperationProxies(Set<ManagedOperation> ops, Object componentName)
      throws Exception
   {
      // Create the delegate operation
      return proxyFactory.createOperationProxies(ops, componentName);
   }

   private ManagedObject createManagedObject(ObjectName mbean, String defaultViewUse, Map<String, String> propertyMetaMappings)
      throws Exception
   {
      MBeanInfo info = mbeanServer.getMBeanInfo(mbean);
      ClassLoader mbeanLoader = mbeanServer.getClassLoaderFor(mbean);
      MetaData metaData = null;
      ViewUse[] viewUse = defaultViewUse == null ? null : new ViewUse[]{ViewUse.valueOf(defaultViewUse)};
      ManagedObject mo = mbeanMOFactory.getManagedObject(mbean, info, mbeanLoader, metaData, viewUse, propertyMetaMappings);
      return mo;
   }

   private ManagedDeployment getManagedDeployment(ProfileDeployment ctx) throws DeploymentException
   {
      return mainDeployer.getManagedDeployment(ctx.getName());
   }

   private ProfileKey getProfileKeyForDeployemnt(String name) throws NoSuchDeploymentException
   {
      ManagedDeployment md = getDeployment(name);
      return md.getAttachment(ProfileKey.class);
   }

   private Profile getProfileForDeployment(String name) throws Exception
   {
      ProfileKey key = getProfileKeyForDeployemnt(name);
      if(key == null)
         throw new NoSuchDeploymentException("No associated profile found for deployment:" + name);

      return this.ps.getActiveProfile(key);
   }

   private ProfileDeployment getProfileDeployment(String name) throws Exception
   {
      Profile profile = getProfileForDeployment(name);
      return profile.getDeployment(name);
   }

   public static void main(String[] args)
      throws Exception
   {
      MBeanServer server = ManagementFactory.getPlatformMBeanServer();
      ObjectName name = new ObjectName("jboss.management.local:J2EEApplication=null,J2EEServer=Local,j2eeType=WebModule,*");
      Set<ObjectName> matches = server.queryNames(name, null);
      for(ObjectName on : matches)
      {
         System.err.println(on);
      }
   }

   public ManagedOperationProxyFactory getMbeanProxyFactory()
   {
      return mbeanProxyFactory;
   }

   public void setMbeanProxyFactory(ManagedOperationProxyFactory mbeanProxyFactory)
   {
      this.mbeanProxyFactory = mbeanProxyFactory;
   }
}
