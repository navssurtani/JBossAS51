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
package org.jboss.test.scripts.test;

import java.lang.InterruptedException ;
import java.io.OutputStream ;
import java.io.ByteArrayOutputStream ;
import java.io.BufferedReader ;
import java.io.InputStreamReader ;
import java.io.PrintWriter ;
import java.io.StringWriter ;
import java.io.FileReader ;
import java.io.FileWriter ;
import java.io.File ;
import java.io.FileInputStream ;
import java.io.FileOutputStream ;
import java.io.FileNotFoundException ;
import java.io.IOException ;

import java.net.URL ;
import java.net.MalformedURLException ;
import java.net.URLConnection ;
import java.net.HttpURLConnection ;

import org.jboss.test.scripts.support.LogFileAssertionChecker ;
import org.jboss.test.scripts.support.ShellScriptExecutor ;
import org.jboss.test.scripts.support.AsyncShellScriptExecutor ;

import org.jboss.test.JBossTestCase ;
import junit.framework.Assert ;

/**
 * Base class to test command-line scripts.
 * 
 * @author Richard Achmatowicz
 * @version $Revision: 1.0
 */
public abstract class ScriptsTestBase extends JBossTestCase
{
	public static final String SERVER_STARTED_MESSAGE = "Started in" ;
	public static final String SERVER_STOPPED_MESSAGE = "Shutdown complete" ;
	public static final String SERVER_HALT_MESSAGE = "Server halt" ;
	public static final String SERVER_EXIT_MESSAGE = "Server exit" ;
	public static final String SERVER_HALTED_MESSAGE = "halting the JVM now" ;	
	
	ShellScriptExecutor se = null ;
	AsyncShellScriptExecutor ase = null ;
	LogFileAssertionChecker errorLogChecker = null ;
	LogFileAssertionChecker outputLogChecker = null ;
	LogFileAssertionChecker bootLogChecker = null ;
	LogFileAssertionChecker systemLogChecker = null ;
	
	public ScriptsTestBase(String name)
	{
		super(name);
		
		// initialise the script executors
		se = new ShellScriptExecutor() ;
		ase = new AsyncShellScriptExecutor() ;
				
		// initialise the log checkers (error checking here could be improved)
		// these will be initialised to a single server instance, so we canb't change server instances
		// part way through a test suite! :-(
		String logDir = getLogDir() ;
		errorLogChecker = new LogFileAssertionChecker(logDir + "/error.log") ;
		outputLogChecker = new LogFileAssertionChecker(logDir + "/output.log") ;
		bootLogChecker = new LogFileAssertionChecker(logDir + "/boot.log") ;
		systemLogChecker = new LogFileAssertionChecker(logDir + "/system.log") ;
	}

	protected void setUp() throws Exception {
		super.setUp();		
		// setup AsyncShellScriptExecutor to deploy shutdown.sar 
		ase.setUseShutdown(true) ;
		ase.setPathToShutdownSar(getDeployURL("shutdown.sar").getPath()) ;
		ase.setDeployDir(getDeployDir()) ;
	}
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	public ShellScriptExecutor getShellScriptExecutor() {
		return se ;
	}
	public AsyncShellScriptExecutor getAsyncShellScriptExecutor() {
		return ase ;
	}
	public LogFileAssertionChecker getOutputLogChecker() {
		return outputLogChecker ;
	}
	public LogFileAssertionChecker getErrorLogChecker() {
		return errorLogChecker ;
	}
	public LogFileAssertionChecker getBootLogChecker() {
		return bootLogChecker ;
	}
	public LogFileAssertionChecker getSystemLogChecker() {
		return systemLogChecker ;
	}
	
	/* location helpers 
	 * 
	 * these really need to be improved to use JBossTestCase, but unfortunately
	 * JBossTestCase does not give us direct access to the deploy directory
	 * Here, i'm mimicking what they do
	 */
	public String getDistDir() {
		String distDir = System.getProperty("jboss.dist") ;
		if (distDir == null)
			fail("Can't get the JBoss distribution directory") ;
		return distDir ;
	}
	
	/*
	 * how to get the server directory?
	 */
	public String getServerConfig() {
		String serverConfig = System.getProperty("jbosstest.server.config");
	    if (serverConfig == null)
	    {
	       serverConfig = "default";
	    }
		return serverConfig ;
	}
	
	public String getBinDir() {		
		return getDistDir() + "/bin" ;
	}
	
	public String getLogDir() {		
		return getDistDir() + "/server/" + getServerConfig() + "/log" ;
	}
	
	public String getDeployDir() {		
		return getDistDir() + "/server/" + getServerConfig() + "/deploy" ;
	}
	
	public boolean isWindows() {
		String osName = System.getProperty("os.name") ;
		if (osName == null)
			fail("Can't get the operating system name") ;		
		return (osName.indexOf("Windows") > -1) || (osName.indexOf("windows") > -1) ;
	}
	
	/* 
	 * method for constructing command lines for the shell executor, which
	 * in general takes three arguments
	 * String[] command, String[] envp, File workingDirectory
	 * 
	 * This command creates the first argument. 
	 * 
	 * Windows: three tokens need to be passed in String[] command
	 * cmd /c <entire command string to execute>
	 * cmd /c "twiddle.bat -s jnp://192.168.0.100 jsr77" 
	 *  
	 * UNIX: three tokens need to be passed in String[] command
	 * bash -c <entire command string to execute>
	 * bash -c "./twiddle.sh -s jnp://192.168.0.100 jsr77"
	 * 
	 * This arrangement may not be optimal ...
	 */
	public String[] getShellCommand(String commandName, String options, String args) {
		
		String[] shellCommand = new String[3] ;
		String commandLine = null ;
		
		if (commandName == null)
			fail("No command name specified for shell to execute") ;
		
		// set up the base command (platform specific)
		if (isWindows()) {
			shellCommand[0] = "cmd" ;
			shellCommand[1] = "/c" ;
			commandLine = commandName + ".bat" ;
		}
		else {
			shellCommand[0] = "bash" ;
			shellCommand[1] = "-c" ;
			commandLine = "./" + commandName + ".sh" ;
		}
		// add in the rest (platform-independent)
		if (options != null)
			commandLine += " " + options ;
		if (args != null)
			commandLine += " " + args ;
		
		shellCommand[2] = commandLine ;
		
		return shellCommand ;
	}
	
	/* assertion helpers */	
	public void assertOnOutputLog(String string, String failureMessage, boolean useCheckpoint, boolean resetCheckpoint) {
		if (!outputLogChecker.isStringInLog(string, useCheckpoint, resetCheckpoint)) {
			// assertion does not hold
			Assert.fail(failureMessage) ;
		}
	}
	
	public void assertOnErrorLog(String string, String failureMessage, boolean useCheckpoint, boolean resetCheckpoint) {
		if (!errorLogChecker.isStringInLog(string, useCheckpoint, resetCheckpoint)) {
			// assertion does not hold
			Assert.fail(failureMessage) ;
		}
	}
	
	public void assertOnBootLog(String string, String failureMessage, boolean useCheckpoint, boolean resetCheckpoint) {
		if (!bootLogChecker.isStringInLog(string, useCheckpoint, resetCheckpoint)) {
			// assertion does not hold
			Assert.fail(failureMessage) ;
		}
	}
	
	public void assertOnSystemLog(String string, String failureMessage, boolean useCheckpoint, boolean resetCheckpoint) {
		if (!systemLogChecker.isStringInLog(string, useCheckpoint, resetCheckpoint)) {
			// assertion does not hold
			Assert.fail(failureMessage) ;
		}
	}		
			
	/* check if there is a Tomcat connection to the server */
	private static boolean isServerStarted(String host) throws MalformedURLException {
		// URL to Tomcat 
		URL url = new URL("http", host, 8080, "/") ;
		try {
			URLConnection conn = url.openConnection() ;
			if (conn instanceof HttpURLConnection) {
				HttpURLConnection http = (HttpURLConnection) conn ;
				int responseCode = http.getResponseCode();
				
				if (responseCode > 0 && responseCode < 400) {
					return true ;
				}
			}
		}
		catch(IOException e) {
			return false ;
		}
		return false ;
	}
	
	/* 
	* Wait for the server (started by the AsyncShellExecutor) to start
	* 
	* @throws Exception 	if server does not start successfully
	*  
	* A successful server start means: 
	* (i) we can reach Tomcat 
	* (ii) no exceptions in error log
	* (iii) no ERROR statements in the server log
	* If the server does not start successfully, we call joinShellCommand with a 1 second timout.
	* This will wait for one second and then attempt to shutdown both the server and the bash shell,
	* before throwing an exception. 
	*   
	* NOTE: this is a hack, and assumes the process has been started using the AsynchShellExecutor   
	*/
	protected static void waitForServerStart(AsyncShellScriptExecutor ase, String host, int timeout) throws IOException  {
		boolean serverStarted = false ;
		boolean logsExceptionFree = true ;
		
		int tries = 0 ;	
		while (tries++ < timeout) {
			if (!ase.isRunning()) {
				throw new IOException("Server failed to start. See logs.") ;
			}
			// wait for a sec
			sleepForSeconds(1) ;
			
			if (isServerStarted(host)) {
				serverStarted = true ;
				break ;
			}
		}		
		
		// problem here is that if another server is started:
		// (i) the preceeding code will indicate serverStarted=true
		// (iI) the following code will get executed before the starting server has had a chance to start 
		// (and before exceptions are written).
		// This results in two servers being started, one incompletely.
		// So wait here for 5 seconds to allow the logs to accumulate exceptions.
		sleepForSeconds(5) ;
		
		// check for startup errors in the server logs. We check both because:
		// (i) log4j will write all CONSOLE appender logging to System.in only, and these
		// may contain ERROR log entries corresponding to exceptions
		// (ii) other exceptions in the AS should get written to System.err if not handled
		// via log4j
		if (ase.getOutput().indexOf("ERROR") > -1 || ase.getError().indexOf("Exception") > -1) {
			logsExceptionFree = false ;
		}
		
		// debugging
		//System.out.println("Server started = " + serverStarted) ;
		//System.out.println("logsExceptionFree = " + logsExceptionFree) ;
		//System.out.println("output = " + ase.getOutput()) ;
		//System.out.println("error = " + ase.getError()) ;
		
		// kill the server before going on if not correctly started 
		if (!serverStarted || !logsExceptionFree) {
			// kill process and close streams
			ase.joinShellCommand(1) ;
			
			// now throw exception 
			if (!serverStarted)
				throw new IOException("Server failed to start: couldn't connect to Tomcat. See logs.") ;
			else if (!logsExceptionFree) {
				throw new IOException("Server failed to start: ERROR statements found. See logs.") ;				
			}
		}
		// if we reach here, the server started and no ERRORs were found
	}
	
	/* Wait for the server (started by the AsyncShellExecutor) to stop
	* 
	* @throws Exception 	if server does not stop successfully
	*  
	* A successful server start means: 
	* (i) we see the "VM halted" message in the server log
	* (ii) no ERROR statements in the server log
	* If the server does not stop successfully, we call joinShellCommand with a 1 second timout.
	* This will wait for one second and then attempt to shutdown both the server and the bash shell,
	* before throwing an exception. 
	*/
	protected static void waitForServerStop(AsyncShellScriptExecutor ase, int timeout) throws IOException {
		boolean serverStopped = false ;
		boolean logsExceptionFree = true ;
		boolean haltCalled = false ;

		System.out.println("waitForServerStop: waiting " + timeout + " seconds") ;
		
		int tries = 0 ;
		while (tries++ < timeout) {
			if (!ase.isRunning()) {
				// FIX-ME
				throw new IOException("Server not running on shutdown. Something fishy") ;
			}	
			// check if stopped by inspecting console log
			String currentOutput = ase.getOutput() ;
			if (currentOutput.indexOf(SERVER_STOPPED_MESSAGE) > -1 || currentOutput.indexOf(SERVER_HALTED_MESSAGE) > -1) {
				serverStopped = true ;
			}
			
			// wait for a sec
			sleepForSeconds(1) ;
		}
		
		// Wait here for 5 seconds to allow the logs to accumulate.
		sleepForSeconds(5) ;

		// check for shutdown errors in the server logs. We check both because:
		// (i) log4j will write all CONSOLE appender logging to System.in only, and these
		// may contain ERROR log entries corresponding to exceptions
		// (ii) other exceptions in the AS should get written to System.err if not handled
		// via log4j
		
		// NOTE: when we halt, an ERROR log message is written so we have to be careful
		// 
		if (ase.getOutput().indexOf(SERVER_HALTED_MESSAGE) > -1)
			haltCalled = true ;
		
		if (!haltCalled && (ase.getOutput().indexOf("ERROR") > -1 || ase.getError().indexOf("Exception") > -1)) {
			logsExceptionFree = false ;
		}
		
		// kill the server before going on if not correctly started 
		if (!serverStopped || !logsExceptionFree) {
			// kill process and close streams
			ase.joinShellCommand(1) ;
			
			// now throw exception 
			if (!serverStopped)
				throw new IOException("Server failed to stop: didn't find message in logs. See logs.") ;
			else if (!logsExceptionFree) {
				throw new IOException("Server failed to stop: ERROR statements found. See logs.") ;				
			}
		}
		// if we reach here, the server stopped and no ERRORs were found
	}
	
	private static void sleepForSeconds(int seconds) {
		try {
			Thread.sleep(seconds * 1000) ;
		}
		catch(InterruptedException e) {
		}			
	}	
}
