/*
 * Generated by XDoclet - Do not edit!
 */
package org.jboss.test.cmp2.cmrstress.interfaces;

/**
 * Local home interface for Parent.
 */
public interface ParentLocalHome
   extends javax.ejb.EJBLocalHome
{
   public static final String COMP_NAME="java:comp/env/ejb/ParentLocal";
   public static final String JNDI_NAME="ParentLocal";

   public org.jboss.test.cmp2.cmrstress.interfaces.ParentLocal findByPrimaryKey(java.lang.String pk)
      throws javax.ejb.FinderException;

}
