/*
 * JBoss, Home of Professional Open Source
 * Copyright 2007, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.deployment;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.deployers.spi.DeploymentException;
import org.jboss.deployers.spi.deployer.DeploymentStages;
import org.jboss.deployers.spi.deployer.helpers.AbstractDeployer;
import org.jboss.deployers.structure.spi.DeploymentUnit;
import org.jboss.deployers.vfs.spi.structure.VFSDeploymentUnit;
import org.jboss.metadata.annotation.creator.client.ApplicationClient5MetaDataCreator;
import org.jboss.metadata.annotation.creator.ejb.jboss.JBoss50Creator;
import org.jboss.metadata.annotation.creator.web.Web25MetaDataCreator;
import org.jboss.metadata.annotation.finder.AnnotationFinder;
import org.jboss.metadata.annotation.finder.DefaultAnnotationFinder;
import org.jboss.metadata.client.spec.ApplicationClientMetaData;
import org.jboss.metadata.ejb.jboss.JBossMetaData;
import org.jboss.metadata.ejb.spec.EjbJar3xMetaData;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.web.spec.Web25MetaData;
import org.jboss.metadata.web.spec.WebMetaData;
import org.jboss.virtual.VFSUtils;
import org.jboss.virtual.VirtualFile;

/**
 * A POST_CLASSLOADER deployer which generates metadata from
 * annotations
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 85945 $
 */
public class AnnotationMetaDataDeployer extends AbstractDeployer
{
   public static final String EJB_ANNOTATED_ATTACHMENT_NAME = "annotated."+EjbJarMetaData.class.getName();
   public static final String CLIENT_ANNOTATED_ATTACHMENT_NAME = "annotated."+ApplicationClientMetaData.class.getName();
   public static final String WEB_ANNOTATED_ATTACHMENT_NAME = "annotated."+WebMetaData.class.getName();

   private boolean metaDataCompleteIsDefault = false;

   public AnnotationMetaDataDeployer()
   {
      setStage(DeploymentStages.POST_CLASSLOADER);
      addInput(EjbJarMetaData.class);
      addInput(WebMetaData.class);
      addInput(ApplicationClientMetaData.class);
      addOutput(EJB_ANNOTATED_ATTACHMENT_NAME);
      addOutput(CLIENT_ANNOTATED_ATTACHMENT_NAME);
      addOutput(WEB_ANNOTATED_ATTACHMENT_NAME);
   }

   public boolean isMetaDataCompleteIsDefault()
   {
      return metaDataCompleteIsDefault;
   }
   public void setMetaDataCompleteIsDefault(boolean metaDataCompleteIsDefault)
   {
      this.metaDataCompleteIsDefault = metaDataCompleteIsDefault;
   }

   public void deploy(DeploymentUnit unit) throws DeploymentException
   {
      if (unit instanceof VFSDeploymentUnit == false)
         return;
      
      VFSDeploymentUnit vfsDeploymentUnit = (VFSDeploymentUnit) unit;
      deploy(vfsDeploymentUnit);
   }

   public void undeploy(DeploymentUnit unit)
   {
      if (unit instanceof VFSDeploymentUnit == false)
         return;
      
      VFSDeploymentUnit vfsDeploymentUnit = (VFSDeploymentUnit) unit;
      undeploy(vfsDeploymentUnit);
   }

   /**
    * Process the 
    * 
    * @param unit the unit
    * @throws DeploymentException for any error
    */
   protected void deploy(VFSDeploymentUnit unit)
      throws DeploymentException
   {
      /* Ignore any spec metadata complete deployments. This expects that a
       deployment unit only represents one of the client, ejb or web
       deployments and its metadata completeness applies to the unit in terms
       of whether annotations should be scanned for.
       */
      boolean isComplete = this.isMetaDataCompleteIsDefault();
      EjbJarMetaData ejbJarMetaData = unit.getAttachment(EjbJarMetaData.class);
      if(ejbJarMetaData != null && ejbJarMetaData instanceof EjbJar3xMetaData)
      {
         isComplete |= ((EjbJar3xMetaData) ejbJarMetaData).isMetadataComplete();
      }
      else if(ejbJarMetaData != null)
      {
         // Any ejb-jar.xml 2.1 or earlier deployment is metadata complete
         isComplete = true;         
      }
      WebMetaData webMetaData = unit.getAttachment(WebMetaData.class);
      if(webMetaData != null && webMetaData instanceof Web25MetaData)
      {
         isComplete |= ((Web25MetaData)webMetaData).isMetadataComplete();
      }
      else if(webMetaData != null)
      {
         // Any web.xml 2.4 or earlier deployment is metadata complete
         isComplete = true;
      }
      ApplicationClientMetaData clientMetaData = unit.getAttachment(ApplicationClientMetaData.class);
      if(clientMetaData != null)
         isComplete |= clientMetaData.isMetadataComplete();

      if(isComplete)
      {
         log.debug("Deployment is metadata-complete, skipping annotation processing"
               + ", ejbJarMetaData="+ejbJarMetaData
               + ", jbossWebMetaData="+webMetaData
               + ", jbossClientMetaData="+clientMetaData
               + ", metaDataCompleteIsDefault="+metaDataCompleteIsDefault
               );
         return;
      }

      VirtualFile root = unit.getRoot();
      boolean isLeaf = true;
      try
      {
         isLeaf = root.isLeaf();
      }
      catch(IOException ignore)
      {
      }
      if(isLeaf == true)
         return;

      List<VirtualFile> classpath = unit.getClassPath();
      if(classpath == null || classpath.isEmpty())
         return;

      boolean trace = log.isTraceEnabled();
      if (trace)
         log.trace("Deploying annotations for unit: " + unit + ", classpath: " + classpath);

      try
      {
         processMetaData(unit, webMetaData, clientMetaData, classpath);
      }
      catch (Exception e)
      {
         throw DeploymentException.rethrowAsDeploymentException("Cannot process metadata", e);
      }
   }

   /**
    * Process metadata.
    *
    * @param unit the deployment unit
    * @param webMetaData the web metadata
    * @param clientMetaData the client metadata
    * @param classpath the classpath
    * @throws DeploymentException for any error
    */
   protected void processMetaData(VFSDeploymentUnit unit, WebMetaData webMetaData, ApplicationClientMetaData clientMetaData, List<VirtualFile> classpath) throws Exception
   {
      String mainClassName = getMainClassName(unit);
      Collection<Class<?>> classes = getClasses(unit, mainClassName, classpath);
      if (classes.size() > 0)
      {
         AnnotationFinder<AnnotatedElement> finder = new DefaultAnnotationFinder<AnnotatedElement>();
         if (webMetaData != null)
            processJBossWebMetaData(unit, finder, classes);
         else if (clientMetaData != null || mainClassName != null)
            processJBossClientMetaData(unit, finder, classes);
         else
            processJBossMetaData(unit, finder, classes);
      }
   }

   /**
    * Get the classes we want to scan.
    *
    * @param unit the deployment unit
    * @param mainClassName the main class name
    * @param classpath the classpath
    * @return possible classes containing metadata annotations
    * @throws IOException for any error
    */
   protected Collection<Class<?>> getClasses(VFSDeploymentUnit unit, String mainClassName, List<VirtualFile> classpath) throws IOException
   {
      Map<VirtualFile, Class<?>> classpathClasses = new HashMap<VirtualFile, Class<?>>();
      for(VirtualFile path : classpath)
      {
         AnnotatedClassFilter classVisitor = new AnnotatedClassFilter(unit, unit.getClassLoader(), path, mainClassName);
         path.visit(classVisitor);
         Map<VirtualFile, Class<?>> classes = classVisitor.getAnnotatedClasses();
         if(classes != null && classes.size() > 0)
         {
            if(log.isTraceEnabled())
               log.trace("Annotated classes: " + classes);
            classpathClasses.putAll(classes);
         }
      }
      return classpathClasses.values();
   }

   /**
    * Undeploy a vfs deployment
    * 
    * @param unit the unit
    */
   protected void undeploy(VFSDeploymentUnit unit)
   {
      // Nothing
   }

   /**
    * Process annotations.
    *
    * @param unit the deployment unit
    * @param finder the annotation finder
    * @param classes the candidate classes
    */
   protected void processJBossMetaData(VFSDeploymentUnit unit,
         AnnotationFinder<AnnotatedElement> finder, Collection<Class<?>> classes)
   {
      // Create the metadata model from the annotations
      JBoss50Creator creator = new JBoss50Creator(finder);
      JBossMetaData annotationMetaData = creator.create(classes);
      if(annotationMetaData != null)
         unit.addAttachment(EJB_ANNOTATED_ATTACHMENT_NAME, annotationMetaData, JBossMetaData.class);
   }

   /**
    * Process annotations.
    *
    * @param unit the deployment unit
    * @param finder the annotation finder
    * @param classes the candidate classes
    */
   protected void processJBossWebMetaData(VFSDeploymentUnit unit,
         AnnotationFinder<AnnotatedElement> finder, Collection<Class<?>> classes)
   {
      Web25MetaDataCreator creator = new Web25MetaDataCreator(finder);
      WebMetaData annotationMetaData = creator.create(classes);
      if(annotationMetaData != null)
         unit.addAttachment(WEB_ANNOTATED_ATTACHMENT_NAME, annotationMetaData, WebMetaData.class);
   }

   /**
    * Process annotations.
    *
    * @param unit the deployment unit
    * @param finder the annotation finder
    * @param classes the candidate classes
    */
   protected void processJBossClientMetaData(VFSDeploymentUnit unit,
         AnnotationFinder<AnnotatedElement> finder, Collection<Class<?>> classes)
   {
      ApplicationClient5MetaDataCreator creator = new ApplicationClient5MetaDataCreator(finder);
      ApplicationClientMetaData annotationMetaData = creator.create(classes);
      if(annotationMetaData != null)
         unit.addAttachment(CLIENT_ANNOTATED_ATTACHMENT_NAME, annotationMetaData, ApplicationClientMetaData.class);      
   }

   /**
    * Get main class from manifest.
    *
    * @param unit the deployment unit
    * @return main class name
    * @throws IOException for any error
    */
   protected String getMainClassName(VFSDeploymentUnit unit)
      throws IOException
   {
      VirtualFile file = unit.getMetaDataFile("MANIFEST.MF");
      if (log.isTraceEnabled())
         log.trace("parsing " + file);

      if(file == null)
      {
         return null;
      }

      try
      {
         Manifest mf = VFSUtils.readManifest(file);
         Attributes attrs = mf.getMainAttributes();
         return attrs.getValue(Attributes.Name.MAIN_CLASS);
      }
      finally
      {
         file.close();
      }
   }
}

