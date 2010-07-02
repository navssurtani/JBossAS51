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
package org.jboss.proxy.ejb.handle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.ejb.EJBHome;
import javax.ejb.EJBObject;
import javax.ejb.spi.HandleDelegate;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;
import javax.rmi.CORBA.Stub;

import org.jboss.util.NestedRuntimeException;
import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.ORB;
import org.omg.CORBA.portable.Delegate;
import org.omg.CORBA.portable.ObjectImpl;

/**
 * <P>Implementation of the javax.ejb.spi.HandleDelegate interface</P>
 * 
 * <P>The HandleDelegate interface is implemented by the EJB container. 
 * It is used by portable implementations of javax.ejb.Handle and
 * javax.ejb.HomeHandle. It is not used by EJB components or by client components.
 * It provides methods to serialize and deserialize EJBObject and EJBHome
 * references to streams.</P>
 * 
 * <P>The HandleDelegate object is obtained by JNDI lookup at the reserved name
 * "java:comp/HandleDelegate".</P> 
 *
 * @author  Dimitris.Andreadis@jboss.org
 * @author  adrian@jboss.com
 * @version $Revision: 81030 $
 */
public class HandleDelegateImpl
    implements HandleDelegate
{
   public static HandleDelegate getDelegate()
   {
      try
      {
         InitialContext ctx = new InitialContext();
         return (HandleDelegate) ctx.lookup("java:comp/HandleDelegate");
      }
      catch (NamingException e)
      {
         throw new NestedRuntimeException(e);
      }  
   }

   // HandleDelegate implementation ---------------------------------

   public void writeEJBObject(EJBObject ejbObject, ObjectOutputStream oostream)
      throws IOException
   {
      oostream.writeObject(ejbObject);
   }
   
   public EJBObject readEJBObject(ObjectInputStream oistream)
      throws IOException, ClassNotFoundException
   {
      Object ejbObject = oistream.readObject();
      reconnect(ejbObject);
      return (EJBObject) PortableRemoteObject.narrow(ejbObject, EJBObject.class);
   }

   public void writeEJBHome(EJBHome ejbHome, ObjectOutputStream oostream)
      throws IOException
   {
      oostream.writeObject(ejbHome);
   }

   public EJBHome readEJBHome(ObjectInputStream oistream)
     throws IOException, ClassNotFoundException
   {
      Object ejbHome = oistream.readObject();
      reconnect(ejbHome);
      return (EJBHome) PortableRemoteObject.narrow(ejbHome, EJBHome.class);
   }
   
   protected void reconnect(Object object) throws IOException
   {
      if (object instanceof ObjectImpl)
      {
         try
         {
            // Check we are still connected
            ObjectImpl objectImpl = (ObjectImpl) object;
            objectImpl._get_delegate();
         }
         catch (BAD_OPERATION e)
         {
            try
            {
               // Reconnect
               Stub stub = (Stub) object;
               ORB orb = (ORB) new InitialContext().lookup("java:comp/ORB");
               stub.connect(orb);
            }
            catch (NamingException ne)
            {
               throw new IOException("Unable to lookup java:comp/ORB");
            }
         }
      }
      else
         throw new IOException("Not an ObjectImpl " + object.getClass().getName());
   }
}
