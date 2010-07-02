/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.web.tomcat.service.jasper;

import java.util.Properties;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Map;
import java.io.File;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.apache.jasper.Options;
import org.apache.jasper.Constants;
import org.apache.jasper.xmlparser.ParserUtils;
import org.apache.jasper.compiler.TldLocationsCache;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.Localizer;
import org.jboss.logging.Logger;
import org.jboss.web.jsf.integration.config.JBossJSFConfigureListener;

/**
 * Override the default JspServletOptions to externalize the jsp layer
 * configuration. This overrides the default TagLibCache to the jboss version.
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 100924 $
 */
public class JspServletOptions
   implements Options
{
   static Logger log = Logger.getLogger(JspServletOptions.class);

   private Properties settings = new Properties();

   /**
    * Is Jasper being used in development mode?
    */
   private boolean development = true;

   /**
    * Should we include a source fragment in exception messages, which could be displayed
    * to the developer ?
    */
   private boolean displaySourceFragment = true;

   /**
    * Should Ant fork its java compiles of JSP pages.
    */
   public boolean fork = true;

   /**
    * Do you want to keep the generated Java files around?
    */
   private boolean keepGenerated = true;

   /**
    * Should white spaces between directives or actions be trimmed?
    */
   private boolean trimSpaces = false;

   /**
    * Determines whether tag handler pooling is enabled.
    */
   private boolean isPoolingEnabled = true;

   /**
    * Do you want support for "mapped" files? This will generate servlet that
    * has a print statement per line of the JSP file. This seems like a really
    * nice feature to have for debugging.
    */
   private boolean mappedFile = true;

   /**
    * Do you want stack traces and such displayed in the client's browser? If
    * this is false, such messages go to the standard error or a log file if the
    * standard error is redirected.
    */
   private boolean sendErrorToClient = false;

   /**
    * Do we want to include debugging information in the class file?
    */
   private boolean classDebugInfo = true;

   /**
    * Background compile thread check interval in seconds.
    */
   private int checkInterval = 0;

   /**
    * Is the generation of SMAP info for JSR45 debuggin suppressed?
    */
   private boolean isSmapSuppressed = false;

   /**
    * Should SMAP info for JSR45 debugging be dumped to a file?
    */
   private boolean isSmapDumped = false;

   /**
    * Are Text strings to be generated as char arrays?
    */
   private boolean genStringAsCharArray = false;

   private boolean errorOnUseBeanInvalidClassAttribute = true;

   /**
    * I want to see my generated servlets. Which directory are they in?
    */
   private File scratchDir;

   /**
    * Need to have this as is for versions 4 and 5 of IE. Can be set from the
    * initParams so if it changes in the future all that is needed is to have a
    * jsp initParam of type ieClassId="<value>"
    */
   private String ieClassId = "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93";

   /**
    * What classpath should I use while compiling generated servlets?
    */
   private String classpath = null;

   /**
    * Compiler to use.
    */
   private String compiler = null;

   /**
    * The compiler class name.
    */
   private String compilerClassName = null;
   
   /**
    * Compiler target VM.
    */
   private String compilerTargetVM = "1.5";

   /**
    * The compiler source VM.
    */
   private String compilerSourceVM = "1.5";

   /**
    * Cache for the TLD locations
    */
   private TldLocationsCache tldLocationsCache = null;

   /**
    * Jsp config information
    */
   private JspConfig jspConfig = null;

   /**
    * TagPluginManager
    */
   private TagPluginManager tagPluginManager = null;

   /**
    * Java platform encoding to generate the JSP page servlet.
    */
   private String javaEncoding = "UTF8";

   /**
    * Modification test interval.
    */
   private int modificationTestInterval = 4;

   private boolean ignoreAnnotations;



   /**
    * Is generation of X-Powered-By response header enabled/disabled?
    */
   private boolean xpoweredBy;


   public boolean getIgnoreAnnotations()
   {
      return ignoreAnnotations;
   }

   public void setIgnoreAnnotations(boolean ignoreAnnotations)
   {
      this.ignoreAnnotations = ignoreAnnotations;
   }

   public String getProperty(String name)
   {
      return settings.getProperty(name);
   }

   public void setProperty(String name, String value)
   {
      if (name != null && value != null)
      {
         settings.setProperty(name, value);
      }
   }

   /**
    * Are we keeping generated code around?
    */
   public boolean getKeepGenerated()
   {
      return keepGenerated;
   }

   /**
    * Should white spaces between directives or actions be trimmed?
    */
   public boolean getTrimSpaces()
   {
      return trimSpaces;
   }

   public boolean isPoolingEnabled()
   {
      return isPoolingEnabled;
   }

   /**
    * Are we supporting HTML mapped servlets?
    */
   public boolean getMappedFile()
   {
      return mappedFile;
   }

   /**
    * Should errors be sent to client or thrown into stderr?
    */
   public boolean getSendErrorToClient()
   {
      return sendErrorToClient;
   }

   /**
    * Should class files be compiled with debug information?
    */
   public boolean getClassDebugInfo()
   {
      return classDebugInfo;
   }

   /**
    * Background JSP compile thread check intervall
    */
   public int getCheckInterval()
   {
      return checkInterval;
   }

   /**
    * Modification test interval.
    */
   public int getModificationTestInterval()
   {
      return modificationTestInterval;
   }

   /**
    * Is Jasper being used in development mode?
    */
   public boolean getDevelopment()
   {
      return development;
   }

   public boolean getDisplaySourceFragment()
   {
      // TODO Auto-generated method stub
      return displaySourceFragment;
   }

   /**
    * Is the generation of SMAP info for JSR45 debuggin suppressed?
    */
   public boolean isSmapSuppressed()
   {
      return isSmapSuppressed;
   }

   /**
    * Should SMAP info for JSR45 debugging be dumped to a file?
    */
   public boolean isSmapDumped()
   {
      return isSmapDumped;
   }

   /**
    * Are Text strings to be generated as char arrays?
    */
   public boolean genStringAsCharArray()
   {
      return this.genStringAsCharArray;
   }

   /**
    * Class ID for use in the plugin tag when the browser is IE.
    */
   public String getIeClassId()
   {
      return ieClassId;
   }

   /**
    * What is my scratch dir?
    */
   public File getScratchDir()
   {
      return scratchDir;
   }

   /**
    * What classpath should I use while compiling the servlets generated from
    * JSP files?
    */
   public String getClassPath()
   {
      return classpath;
   }

   /**
    * Is generation of X-Powered-By response header enabled/disabled?
    */
   public boolean isXpoweredBy()
   {
      return xpoweredBy;
   }

   /**
    * Compiler to use.
    */
   public String getCompiler()
   {
      return compiler;
   }

   /**
    * @see Options#getCompilerClassName
    */
   public String getCompilerClassName()
   {
      return compilerClassName;
   }
   
   /**
    * @see Options#getCompilerTargetVM
    */
   public String getCompilerTargetVM()
   {
      return compilerTargetVM;
   }

   /**
    * @see Options#getCompilerSourceVM
    */
   public String getCompilerSourceVM()
   {
      return compilerSourceVM;
   }

   public boolean getErrorOnUseBeanInvalidClassAttribute()
   {
      return errorOnUseBeanInvalidClassAttribute;
   }

   public void setErrorOnUseBeanInvalidClassAttribute(boolean b)
   {
      errorOnUseBeanInvalidClassAttribute = b;
   }

   public TldLocationsCache getTldLocationsCache()
   {
      return tldLocationsCache;
   }

   public void setTldLocationsCache(TldLocationsCache tldC)
   {
      tldLocationsCache = tldC;
   }

   public String getJavaEncoding()
   {
      return javaEncoding;
   }

   public boolean getFork()
   {
      return fork;
   }

   public JspConfig getJspConfig()
   {
      return jspConfig;
   }

   public TagPluginManager getTagPluginManager()
   {
      return tagPluginManager;
   }

   /**
    * Is caching enabled (used for precompilation).
    */
   public boolean isCaching()
   {
      return false;
   }
    
   /**
    * The web-application wide cache for the returned TreeNode
    * by parseXMLDocument in TagLibraryInfoImpl.parseTLD,
    * if isCaching returns true.
    * 
    * @return the Map(String uri, TreeNode tld) instance.
    */
   public Map getCache()
   {
      return null;
   }

   /**
    * Create an EmbeddedServletOptions object using data available from
    * ServletConfig and ServletContext.
    */
   public JspServletOptions(ServletConfig config, ServletContext context)
   {

      Enumeration enumeration = config.getInitParameterNames();
      while (enumeration.hasMoreElements())
      {
         String k = (String) enumeration.nextElement();
         String v = config.getInitParameter(k);
         setProperty(k, v);
      }

      // quick hack
      String validating = config.getInitParameter("validating");
      if ("false".equals(validating)) ParserUtils.validating = false;

      String keepgen = config.getInitParameter("keepgenerated");
      if (keepgen != null)
      {
         if (keepgen.equalsIgnoreCase("true"))
         {
            this.keepGenerated = true;
         }
         else if (keepgen.equalsIgnoreCase("false"))
         {
            this.keepGenerated = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.keepgen"));
         }
      }


      String trimsp = config.getInitParameter("trimSpaces");
      if (trimsp != null)
      {
         if (trimsp.equalsIgnoreCase("true"))
         {
            trimSpaces = true;
         }
         else if (trimsp.equalsIgnoreCase("false"))
         {
            trimSpaces = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.trimspaces"));
         }
      }

      this.isPoolingEnabled = true;
      String poolingEnabledParam
         = config.getInitParameter("enablePooling");
      if (poolingEnabledParam != null
         && !poolingEnabledParam.equalsIgnoreCase("true"))
      {
         if (poolingEnabledParam.equalsIgnoreCase("false"))
         {
            this.isPoolingEnabled = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.enablePooling"));
         }
      }

      String mapFile = config.getInitParameter("mappedfile");
      if (mapFile != null)
      {
         if (mapFile.equalsIgnoreCase("true"))
         {
            this.mappedFile = true;
         }
         else if (mapFile.equalsIgnoreCase("false"))
         {
            this.mappedFile = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.mappedFile"));
         }
      }

      String senderr = config.getInitParameter("sendErrToClient");
      if (senderr != null)
      {
         if (senderr.equalsIgnoreCase("true"))
         {
            this.sendErrorToClient = true;
         }
         else if (senderr.equalsIgnoreCase("false"))
         {
            this.sendErrorToClient = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.sendErrToClient"));
         }
      }

      String debugInfo = config.getInitParameter("classdebuginfo");
      if (debugInfo != null)
      {
         if (debugInfo.equalsIgnoreCase("true"))
         {
            this.classDebugInfo = true;
         }
         else if (debugInfo.equalsIgnoreCase("false"))
         {
            this.classDebugInfo = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.classDebugInfo"));
         }
      }

      String checkInterval = config.getInitParameter("checkInterval");
      if (checkInterval != null)
      {
         try
         {
            this.checkInterval = Integer.parseInt(checkInterval);
         }
         catch (NumberFormatException ex)
         {
            log.warn(Localizer.getMessage("jsp.warning.checkInterval"));
         }
      }

      String modificationTestInterval = config.getInitParameter("modificationTestInterval");
      if (modificationTestInterval != null)
      {
         try
         {
            this.modificationTestInterval = Integer.parseInt(modificationTestInterval);
         }
         catch (NumberFormatException ex)
         {
            log.warn(Localizer.getMessage("jsp.warning.modificationTestInterval"));
         }
      }

      String development = config.getInitParameter("development");
      if (development != null)
      {
         if (development.equalsIgnoreCase("true"))
         {
            this.development = true;
         }
         else if (development.equalsIgnoreCase("false"))
         {
            this.development = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.development"));
         }
      }

      String displaySourceFragment = config.getInitParameter("displaySourceFragment"); 
      if (displaySourceFragment != null)
      {
          if (displaySourceFragment.equalsIgnoreCase("true"))
          {
              this.displaySourceFragment = true;
          }
          else if (displaySourceFragment.equalsIgnoreCase("false"))
          {
              this.displaySourceFragment = false;
          }
          else
          {
              log.warn(Localizer.getMessage("jsp.warning.displaySourceFragment"));
          }
      }

      String suppressSmap = config.getInitParameter("suppressSmap");
      if (suppressSmap != null)
      {
         if (suppressSmap.equalsIgnoreCase("true"))
         {
            isSmapSuppressed = true;
         }
         else if (suppressSmap.equalsIgnoreCase("false"))
         {
            isSmapSuppressed = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.suppressSmap"));
         }
      }

      String dumpSmap = config.getInitParameter("dumpSmap");
      if (dumpSmap != null)
      {
         if (dumpSmap.equalsIgnoreCase("true"))
         {
            isSmapDumped = true;
         }
         else if (dumpSmap.equalsIgnoreCase("false"))
         {
            isSmapDumped = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.dumpSmap"));
         }
      }

      String genCharArray = config.getInitParameter("genStrAsCharArray");
      if (genCharArray != null)
      {
         if (genCharArray.equalsIgnoreCase("true"))
         {
            genStringAsCharArray = true;
         }
         else if (genCharArray.equalsIgnoreCase("false"))
         {
            genStringAsCharArray = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.genchararray"));
         }
      }

      String errBeanClass =
         config.getInitParameter("errorOnUseBeanInvalidClassAttribute");
      if (errBeanClass != null)
      {
         if (errBeanClass.equalsIgnoreCase("true"))
         {
            errorOnUseBeanInvalidClassAttribute = true;
         }
         else if (errBeanClass.equalsIgnoreCase("false"))
         {
            errorOnUseBeanInvalidClassAttribute = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.errBean"));
         }
      }

      String ieClassId = config.getInitParameter("ieClassId");
      if (ieClassId != null)
         this.ieClassId = ieClassId;

      String classpath = config.getInitParameter("classpath");
      if (classpath != null)
         this.classpath = classpath;

      /*
       * scratchdir
       */
      String dir = config.getInitParameter("scratchdir");
      if (dir != null)
      {
         scratchDir = new File(dir);
      }
      else
      {
         // First try the Servlet 2.2 javax.servlet.context.tempdir property
         scratchDir = (File) context.getAttribute(Constants.TMP_DIR);
         if (scratchDir == null)
         {
            // Not running in a Servlet 2.2 container.
            // Try to get the JDK 1.2 java.io.tmpdir property
            dir = System.getProperty("java.io.tmpdir");
            if (dir != null)
               scratchDir = new File(dir);
         }
      }
      if (this.scratchDir == null)
      {
         log.fatal(Localizer.getMessage("jsp.error.no.scratch.dir"));
         return;
      }

      if (!(scratchDir.exists() && scratchDir.canRead() &&
         scratchDir.canWrite() && scratchDir.isDirectory()))
         log.fatal(Localizer.getMessage("jsp.error.bad.scratch.dir",
            scratchDir.getAbsolutePath()));

      this.compiler = config.getInitParameter("compiler");

      String compilerTargetVM = config.getInitParameter("compilerTargetVM");
      if (compilerTargetVM != null)
      {
         this.compilerTargetVM = compilerTargetVM;
      }

      String compilerSourceVM = config.getInitParameter("compilerSourceVM");
      if (compilerSourceVM != null)
      {
         this.compilerSourceVM = compilerSourceVM;
      }

      String javaEncoding = config.getInitParameter("javaEncoding");
      if (javaEncoding != null)
      {
         this.javaEncoding = javaEncoding;
      }

      String compilerClassName = config.getInitParameter("compilerClassName");
      if(compilerClassName != null)
      {
         this.compilerClassName = compilerClassName;
      }
      
      String fork = config.getInitParameter("fork");
      if (fork != null)
      {
         if (fork.equalsIgnoreCase("true"))
         {
            this.fork = true;
         }
         else if (fork.equalsIgnoreCase("false"))
         {
            this.fork = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.fork"));
         }
      }

      String xpoweredBy = config.getInitParameter("xpoweredBy");
      if (xpoweredBy != null)
      {
         if (xpoweredBy.equalsIgnoreCase("true"))
         {
            this.xpoweredBy = true;
         }
         else if (xpoweredBy.equalsIgnoreCase("false"))
         {
            this.xpoweredBy = false;
         }
         else
         {
            log.warn(Localizer.getMessage("jsp.warning.xpoweredBy"));
         }
      }

      /* Setup the global Tag Libraries location cache for this web app. The
         tagLibJarN entries define the jars the cache should scan. 
      */
      String base = "tagLibJar";
      ArrayList tldJars = new ArrayList();
      int count = 0;
      String jarPath = null;
      do
      {
         String name = base + count;
         jarPath = config.getInitParameter(name);
         if( (jarPath != null) && okToAddTagsFromJSFLibs(jarPath, context) )
            tldJars.add(jarPath);
         count ++;
      } while( jarPath != null );
      
      tldLocationsCache = new TagLibCache(context, tldJars);

      // Setup the jsp config info for this web app.
      jspConfig = new JspConfig(context);

      // Create a Tag plugin instance
      tagPluginManager = new TagPluginManager(context);
   }

   private boolean okToAddTagsFromJSFLibs(String jarPath, ServletContext context)
   {
      // if it's not coming from jsf-libs you can always add it
      if (!jarPath.startsWith("jsf-libs")) return true;

      return !JBossJSFConfigureListener.warBundlesJSFImpl(context);
   }

}
