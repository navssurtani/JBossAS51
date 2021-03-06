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
package org.jboss.test.security.proxy;

import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.Principal;
import javax.ejb.EJBContext;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.jboss.test.security.test.NamespacePermission;
import org.jboss.test.security.interfaces.IProjRepository;

/** A simple stateful security proxy example for the ProjRepository bean.

@see javax.naming.Name
@see javax.naming.directory.Attributes
@see org.jboss.test.security.test.ejbs.project.interfaces.IProjRepository

@author Scott_Stark@displayscape.com
@version $Revision: 81036 $
*/
public class ProjRepositorySecurityProxy2 implements IProjRepository
{
   org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(getClass());
   
    /**
     * @label bean
     * @clientRole state sink
     * @supplierRole state source 
     */
    private IProjRepository projRepository;
    private EJBContext ctx;

    public void setEJBContext(EJBContext ctx)
    {
        this.ctx = ctx;
        log.debug("ProjRepositorySecurityProxy2.setEJBContext, ctx="+ctx);
    }
    public void setBean(Object bean)
    {
        projRepository = (IProjRepository) bean;
        log.debug("ProjRepositorySecurityProxy2.setBean, bean="+projRepository);
    }

    public void ejbCreate(Name projectName)
    {
        Principal user = ctx.getCallerPrincipal();
        String userID = user.getName();
        log.debug("ProjRepositorySecurityProxy2.ejbCreate, projectName="+projectName);
        // Only scott or starksm can create project sessions
        if( userID.equals("scott") == false && userID.equals("starksm") == false )
            throw new SecurityException("Invalid project userID: "+userID);
    }

// --- Begin IProjRepository interface methods
    public void createFolder(Name folderPath)
    {
        log.debug("ProjRepositorySecurityProxy2.createFolder, folderPath="+folderPath);
    }
    
    public void deleteFolder(Name folderPath,boolean recursive)
    {
        log.debug("ProjRepositorySecurityProxy2.deleteFolder, folderPath="+folderPath);
    }
    
    public void createItem(Name itemPath,Attributes attributes)
    {
        log.debug("ProjRepositorySecurityProxy2.createItem, itemPath="+itemPath);
    }
    
    public void updateItem(Name itemPath,Attributes attributes)
    {
        log.debug("ProjRepositorySecurityProxy2.updateItem, itemPath="+itemPath);
    }
    
    public void deleteItem(Name itemPath)
    {
        Principal user = ctx.getCallerPrincipal();
        String userID = user.getName();
        log.debug("ProjRepositorySecurityProxy2.deleteItem, itemPath="+itemPath);
        // Only the item owner can delete it
        String owner = null;
        try
        {
            Attributes attributes = projRepository.getItem(itemPath);
            if( attributes != null )
            {
                Attribute attr = attributes.get("owner");
                if( attr != null )
                    owner = (String) attr.get();
            }
        }
        catch(Exception e)
        {
            log.debug("failed", e);
            throw new SecurityException("Failed to obtain owner for: "+itemPath);
        }

        if( owner == null )
            throw new SecurityException("No owner assigned to: "+itemPath);
        if( owner.equals(userID) == false )
            throw new SecurityException("User: "+userID+" is not the owner of: "+itemPath);
    }

    public Attributes getItem(Name itemPath)
    {
        NamespacePermission p = new NamespacePermission(itemPath, "r---");
        AccessController.checkPermission(p);
        log.debug("ProjRepositorySecurityProxy2.getItem, itemPath="+itemPath);
        return null;
    }
// --- End IProjRepository interface methods

}
