/*
 * Generated by XDoclet - Do not edit!
 */
package org.jboss.test.proxycompiler.beans.interfaces;

/**
 * Home interface for ProxyCompilerTest.
 */
public interface ProxyCompilerTestHome
   extends javax.ejb.EJBHome
{
   public static final String COMP_NAME="java:comp/env/ejb/ProxyCompilerTest";
   public static final String JNDI_NAME="ProxyCompilerTest";

   public org.jboss.test.proxycompiler.beans.interfaces.ProxyCompilerTest create(java.lang.Integer pk)
      throws javax.ejb.CreateException,java.rmi.RemoteException;

   public java.util.Collection findAll()
      throws javax.ejb.FinderException,java.rmi.RemoteException;

   public org.jboss.test.proxycompiler.beans.interfaces.ProxyCompilerTest findByPrimaryKey(java.lang.Integer pk)
      throws javax.ejb.FinderException,java.rmi.RemoteException;

}
