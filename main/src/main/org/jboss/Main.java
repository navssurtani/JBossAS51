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
package org.jboss;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.jboss.bootstrap.ServerLoader;
import org.jboss.bootstrap.spi.Server;
import org.jboss.bootstrap.spi.ServerConfig;
import org.jboss.bootstrap.spi.util.ServerConfigUtil;

/**
 * Provides a command line interface to start the JBoss server.
 *
 * <p>
 * To enable debug or trace messages durring boot change the Log4j
 * configuration to use either <tt>log4j-debug.properties</tt>
 * <tt>log4j-trace.properties</tt> by setting the system property
 * <tt>log4j.configuration</tt>:
 *
 * <pre>
 *   ./run.sh -Dlog4j.configuration=log4j-debug.properties
 * </pre>
 * TODO: Should jdk logging be the default
 *
 * @author <a href="mailto:marc.fleury@jboss.org">Marc Fleury</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @author <a href="mailto:adrian.brock@happeningtimes.com">Adrian Brock</a>
 * @author <a href="mailto:scott.stark@jboss.org">Scott Stark</a>
 * @author <a href="mailto:dimitris@jboss.org">Dimitris Andreadis</a>
 * @version $Revision: 88978 $
 */
public class Main
{
   /** EDU.oswego.cs.dl.util.concurrent */
   private String concurrentLib = "concurrent.jar";

   /** A URL for obtaining microkernel patches */
   private URL bootURL;
   
   /** Extra jars from the /lib location that are added to the start of the boot
    classpath. This can be used to override jboss /lib boot classes.
    */
   private List<String> bootLibraries = new LinkedList<String>();

   /** Extra libraries to load the server with .*/
   private List<String> extraLibraries = new LinkedList<String>();

   /** Extra classpath URLS to load the server with .*/
   private List<URL> extraClasspath = new LinkedList<URL>();

   /**
    * Server properties.  This object holds all of the required
    * information to get the server up and running. Use System
    * properties for defaults.
    */
   private Properties props = new Properties(System.getProperties());
   
   /** The booted server instance */
   private Server server;

   /**
    * Explicit constructor.
    */
   public Main()
   {
      super();
   }

   /**
    * Access the booted server.
    * @return the Server instance.
    */
   public Server getServer()
   {
      return server;
   }

   /**
    * Boot up JBoss.
    *
    * @param args   The command line arguments.
    *
    * @throws Exception    Failed to boot.
    */
   public void boot(final String[] args) throws Exception
   {
	   // remove this when JBAS-6744 is fixed
	   String useUnorderedSequence = System.getProperty("xb.builder.useUnorderedSequence");
	   if(useUnorderedSequence == null)
		   System.setProperty("xb.builder.useUnorderedSequence", "true");
	   
      // First process the command line to pickup custom props/settings
      processCommandLine(args);

      // Initialize the JDK logmanager
      String name = System.getProperty("java.util.logging.manager");
      if (name == null) {
         System.setProperty("java.util.logging.manager",
            "org.jboss.logmanager.LogManager");
      }

      // Auto set HOME_DIR to ../bin/run.jar if not set
      String homeDir = props.getProperty(ServerConfig.HOME_DIR);
      if (homeDir == null)
      {
         String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getFile();
         /* The 1.4 JDK munges the code source file with URL encoding so run
          * this path through the decoder so that is JBoss starts in a path with
          * spaces we don't come crashing down.
         */
         path = URLDecoder.decode(path, "UTF-8");
         File runJar = new File(path);
         File homeFile = runJar.getParentFile().getParentFile();
         homeDir = homeFile.getCanonicalPath();
      }
      props.setProperty(ServerConfig.HOME_DIR, homeDir);

      // Setup HOME_URL too, ServerLoader needs this
      String homeURL = props.getProperty(ServerConfig.HOME_URL);
      if (homeURL == null)
      {
         File file = new File(homeDir);
         homeURL = file.toURI().toURL().toString();
         props.setProperty(ServerConfig.HOME_URL, homeURL);
      }

      // Load the server instance
      ServerLoader loader = new ServerLoader(props);

      /* If there is a patch dir specified make it the first element of the
      loader bootstrap classpath. If its a file url pointing to a dir, then
      add the dir and its contents.
      */
      if (bootURL != null)
      {
         if (bootURL.getProtocol().equals("file"))
         {
            File dir = new File(bootURL.getFile());
            if (dir.exists())
            {
               // Add the local file patch directory
               loader.addURL(dir.toURL());

               // Add the contents of the directory too
               File[] jars = dir.listFiles(new JarFilter());

               for (int j = 0; jars != null && j < jars.length; j++)
               {
                  loader.addURL(jars[j].getCanonicalFile().toURL());
               }
            }
         }
         else
         {
            loader.addURL(bootURL);
         }
      }

      // Add any extra libraries
      for (int i = 0; i < bootLibraries.size(); i++)
      {
         loader.addLibrary(bootLibraries.get(i));
      }

      // Add the jars from the endorsed dir
      loader.addEndorsedJars();

      // jmx UnifiedLoaderRepository needs a concurrent class...
      loader.addLibrary(concurrentLib);

      // Add any extra libraries after the boot libs
      for (int i = 0; i < extraLibraries.size(); i++)
      {
         loader.addLibrary(extraLibraries.get(i));
      }

      // Add any extra classapth URLs
      for (int i = 0; i < extraClasspath.size(); i++)
      {
         loader.addURL(extraClasspath.get(i));
      }

      // Load the server
      ClassLoader parentCL = Thread.currentThread().getContextClassLoader();
      server = loader.load(parentCL);

      // Initialize the server
      server.init(props);

      // Start 'er up mate!
      server.start();
   }

   /**
    * Shutdown the booted Server instance.
    *
    */
   public void shutdown()
   {
      server.shutdown();
   }

   private URL makeURL(String urlspec) throws MalformedURLException
   {
      urlspec = urlspec.trim();

      URL url;

      try
      {
         url = new URL(urlspec);
         if (url.getProtocol().equals("file"))
         {
            // make sure the file is absolute & canonical file url
            File file = new File(url.getFile()).getCanonicalFile();
            url = file.toURL();
         }
      }
      catch (Exception e)
      {
         // make sure we have a absolute & canonical file url
         try
         {
            File file = new File(urlspec).getCanonicalFile();
            url = file.toURL();
         }
         catch (Exception n)
         {
            throw new MalformedURLException(n.toString());
         }
      }

      return url;
   }

   /** Process the command line... */
   private void processCommandLine(final String[] args) throws Exception
   {
      // set this from a system property or default to jboss
      String programName = System.getProperty("program.name", "jboss");
      String sopts = "-:hD:d:p:n:c:Vj::B:L:C:P:b:g:u:m:l:";
      LongOpt[] lopts =
      {
         new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'),
         new LongOpt("bootdir", LongOpt.REQUIRED_ARGUMENT, null, 'd'),
         new LongOpt("patchdir", LongOpt.REQUIRED_ARGUMENT, null, 'p'),
         new LongOpt("netboot", LongOpt.REQUIRED_ARGUMENT, null, 'n'),
         new LongOpt("configuration", LongOpt.REQUIRED_ARGUMENT, null, 'c'),
         new LongOpt("version", LongOpt.NO_ARGUMENT, null, 'V'),
         new LongOpt("jaxp", LongOpt.REQUIRED_ARGUMENT, null, 'j'),
         new LongOpt("bootlib", LongOpt.REQUIRED_ARGUMENT, null, 'B'),
         new LongOpt("library", LongOpt.REQUIRED_ARGUMENT, null, 'L'),
         new LongOpt("classpath", LongOpt.REQUIRED_ARGUMENT, null, 'C'),
         new LongOpt("properties", LongOpt.REQUIRED_ARGUMENT, null, 'P'),
         new LongOpt("host", LongOpt.REQUIRED_ARGUMENT, null, 'b'),
         new LongOpt("partition", LongOpt.REQUIRED_ARGUMENT, null, 'g'),
         new LongOpt("udp", LongOpt.REQUIRED_ARGUMENT, null, 'u'),
         new LongOpt("mcast_port", LongOpt.REQUIRED_ARGUMENT, null, 'm'),
         new LongOpt("log", LongOpt.REQUIRED_ARGUMENT, null, 'l'),
      };

      Getopt getopt = new Getopt(programName, args, sopts, lopts);
      int code;
      String arg;
      if (System.getProperty(ServerConfig.SERVER_BIND_ADDRESS) == null)
      {
         // ServerConfig.SERVER_BIND_ADDRESS could have been defined via 
         // run.conf and so we don't wanna override that. 
         props.setProperty(ServerConfig.SERVER_BIND_ADDRESS, "127.0.0.1");
         System.setProperty(ServerConfig.SERVER_BIND_ADDRESS, "127.0.0.1");
      }
      while ((code = getopt.getopt()) != -1)
      {
         switch (code)
         {
            case ':':
            case '?':
               // for now both of these should exit with error status
               System.exit(1);
               break; // for completeness

            case 1:
               // this will catch non-option arguments
               // (which we don't currently care about)
               System.err.println(programName +
                                  ": unused non-option argument: " +
                                  getopt.getOptarg());
               break; // for completeness

            case 'h':
               // show command line help
               System.out.println("usage: " + programName + " [options]");
               System.out.println();
               System.out.println("options:");
               System.out.println("    -h, --help                    Show this help message");
               System.out.println("    -V, --version                 Show version information");
               System.out.println("    --                            Stop processing options");
               System.out.println("    -D<name>[=<value>]            Set a system property");
               System.out.println("    -d, --bootdir=<dir>           Set the boot patch directory; Must be absolute or url");
               System.out.println("    -p, --patchdir=<dir>          Set the patch directory; Must be absolute or url");
               System.out.println("    -n, --netboot=<url>           Boot from net with the given url as base");
               System.out.println("    -c, --configuration=<name>    Set the server configuration name");
               System.out.println("    -B, --bootlib=<filename>      Add an extra library to the front bootclasspath");
               System.out.println("    -L, --library=<filename>      Add an extra library to the loaders classpath");
               System.out.println("    -C, --classpath=<url>         Add an extra url to the loaders classpath");
               System.out.println("    -P, --properties=<url>        Load system properties from the given url");
               System.out.println("    -b, --host=<host or ip>       Bind address for all JBoss services");
               System.out.println("    -g, --partition=<name>        HA Partition name (default=DefaultDomain)");
               System.out.println("    -m, --mcast_port=<ip>         UDP multicast port; only used by JGroups");
               System.out.println("    -u, --udp=<ip>                UDP multicast address");
               System.out.println("    -l, --log=<log4j|jdk>         Specify the logger plugin type");
               System.out.println();
               System.exit(0);
               break; // for completeness

            case 'D':
            {
               // set a system property
               arg = getopt.getOptarg();
               String name, value;
               int i = arg.indexOf("=");
               if (i == -1)
               {
                  name = arg;
                  value = "true";
               }
               else
               {
                  name = arg.substring(0, i);
                  value = arg.substring(i + 1, arg.length());
               }
               System.setProperty(name, value);
               // Ensure setting the old bind.address property also sets the new
               // jgroups.bind_addr property, otherwise jgroups may ignore it
               if ("bind.address".equals(name))
               {
                  // Wildcard address is not valid for JGroups
                  String addr = ServerConfigUtil.fixRemoteAddress(value);
                  System.setProperty("jgroups.bind_addr", addr);
               }
               else if ("jgroups.bind_addr".equals(name))
               {
                  // Wildcard address is not valid for JGroups
                  String addr = ServerConfigUtil.fixRemoteAddress(value);
                  System.setProperty("jgroups.bind_addr", addr);
               }
               break;
            }

            case 'd':
               // set the boot patch URL
               bootURL = makeURL(getopt.getOptarg());
               break;

            case 'p':
            {
               // set the patch URL
               URL patchURL = makeURL(getopt.getOptarg());
               props.put(ServerConfig.PATCH_URL, patchURL.toString());

               break;
            }

            case 'n':
               // set the net boot url
               arg = getopt.getOptarg();

               // make sure there is a trailing '/'
               if (!arg.endsWith("/")) arg += "/";

               props.put(ServerConfig.HOME_URL, new URL(arg).toString());
               break;

            case 'c':
               // set the server name
               arg = getopt.getOptarg();
               props.put(ServerConfig.SERVER_NAME, arg);
               break;

            case 'V':
            {
               // Package information for org.jboss
               Package jbossPackage = Package.getPackage("org.jboss");

               // show version information
               System.out.println("JBoss " + jbossPackage.getImplementationVersion());
               System.out.println();
               System.out.println("Distributable under LGPL license.");
               System.out.println("See terms of license at gnu.org.");
               System.out.println();
               System.exit(0);
               break; // for completness
            }

            case 'j':
               // Show an error and exit
               System.err.println(programName + ": option '-j, --jaxp' no longer supported");
               System.exit(1);
               break; // for completness

            case 'B':
               arg = getopt.getOptarg();
               bootLibraries.add(arg);
               break;

            case 'L':
               arg = getopt.getOptarg();
               extraLibraries.add(arg);
               break;

            case 'C':
            {
               URL url = makeURL(getopt.getOptarg());
               extraClasspath.add(url);
               break;
            }

            case 'P':
            {
               // Set system properties from url/file
               URL url = makeURL(getopt.getOptarg());
               Properties props = System.getProperties();
               props.load(url.openConnection().getInputStream());
               break;
            }
            case 'b':
               arg = getopt.getOptarg();
               props.put(ServerConfig.SERVER_BIND_ADDRESS, arg);
               System.setProperty(ServerConfig.SERVER_BIND_ADDRESS, arg);
               // used by JGroups; only set if not set via -D so users
               // can use a different interface for cluster communication
               // There are 2 versions of this property, deprecated bind.address
               // and the new version, jgroups.bind_addr
               String bindAddress = System.getProperty("bind.address");
               if (bindAddress == null)
               {
                  // Wildcard address is not valid for JGroups
                  bindAddress = ServerConfigUtil.fixRemoteAddress(arg);
                  System.setProperty("bind.address", bindAddress);
               }
               bindAddress = System.getProperty("jgroups.bind_addr");
               if (bindAddress == null)
               {
                  // Wildcard address is not valid for JGroups
                  bindAddress = ServerConfigUtil.fixRemoteAddress(arg);
                  System.setProperty("jgroups.bind_addr", bindAddress);
               }
               
               // Set the java.rmi.server.hostname if not set
               String rmiHost = System.getProperty("java.rmi.server.hostname");
               if( rmiHost == null )
               {
                  rmiHost = ServerConfigUtil.fixRemoteAddress(arg);
                  System.setProperty("java.rmi.server.hostname", rmiHost);
               }
               break;
               
            case 'g':
               arg = getopt.getOptarg();
               props.put(ServerConfig.PARTITION_NAME_PROPERTY, arg);
               System.setProperty(ServerConfig.PARTITION_NAME_PROPERTY, arg);
               break;
               
            case 'u':
               arg = getopt.getOptarg();
               props.put(ServerConfig.PARTITION_UDP_PROPERTY, arg);
               System.setProperty(ServerConfig.PARTITION_UDP_PROPERTY, arg);
               // the new jgroups property name
               System.setProperty("jgroups.udp.mcast_addr", arg);
               break;
               
            case 'm':
               arg = getopt.getOptarg();
               props.put(ServerConfig.PARTITION_UDP_PORT_PROPERTY, arg);
               System.setProperty(ServerConfig.PARTITION_UDP_PORT_PROPERTY, arg);
               break;
               
            case 'l':
            {
               arg = getopt.getOptarg();
               String logPlugin = arg;
               if( arg.equalsIgnoreCase("log4j") )
                  logPlugin = "org.jboss.logging.Log4jLoggerPlugin";
               else if( arg.equalsIgnoreCase("jdk") )
                  logPlugin = "org.jboss.logging.jdk.JDK14LoggerPlugin";
               System.setProperty("org.jboss.logging.Logger.pluginClass", logPlugin);
               break;
            }
            
            default:
               // this should not happen,
               // if it does throw an error so we know about it
               throw new Error("unhandled option code: " + code);
         }
      }

      // Fix up other bind addresses
      String bindAddress = System.getProperty(ServerConfig.SERVER_BIND_ADDRESS);
      if (System.getProperty("java.rmi.server.hostname") == null)
         System.setProperty("java.rmi.server.hostname", bindAddress);
      if (System.getProperty("jgroups.bind_addr") == null)
         System.setProperty("jgroups.bind_addr", bindAddress);
      
      // Enable jboss.vfs.forceCopy by default, if unspecified
      if (System.getProperty("jboss.vfs.forceCopy") == null)
         System.setProperty("jboss.vfs.forceCopy", "true");
   }

   /**
    * This is where the magic begins.
    *
    * <P>Starts up inside of a "jboss" thread group to allow better
    *    identification of JBoss threads.
    *
    * @param args    The command line arguments.
    * @throws Exception for any error
    */
   public static void main(final String[] args) throws Exception
   {
      Runnable worker = new Runnable() {
            public void run()
            {
               try
               {
                  Main main = new Main();
                  main.boot(args);
               }
               catch (Exception e)
               {
                  System.err.println("Failed to boot JBoss:");
                  e.printStackTrace();
               }
            }

         };

      ThreadGroup threads = new ThreadGroup("jboss");
      new Thread(threads, worker, "main").start();
   }

   /**
    * This method is here so that if JBoss is running under
    * Alexandria (An NT Service Installer), Alexandria can shutdown
    * the system down correctly.
    * 
    * @param argv the arguments
    */
   public static void systemExit(String argv[])
   {
      System.exit(0);
   }

   static class JarFilter implements FilenameFilter
   {
      public boolean accept(File dir, String name)
      {
         return name.endsWith(".jar");
      }
   }
}
