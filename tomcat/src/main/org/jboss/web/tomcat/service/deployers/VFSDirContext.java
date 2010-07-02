/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 


package org.jboss.web.tomcat.service.deployers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;

import javax.naming.CompositeName;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NotContextException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.AttributeModificationException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributesException;
import javax.naming.directory.InvalidSearchControlsException;
import javax.naming.directory.InvalidSearchFilterException;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;

import org.apache.naming.NamingContextBindingsEnumeration;
import org.apache.naming.NamingContextEnumeration;
import org.apache.naming.NamingEntry;
import org.apache.naming.resources.BaseDirContext;
import org.apache.naming.resources.Resource;
import org.apache.naming.resources.ResourceAttributes;
import org.jboss.logging.Logger;
import org.jboss.virtual.VirtualFile;

/**
 * VFS Directory Context implementation.
 *
 * @author Remy Maucherat
 * @version $Revision: 85945 $ $Date: 2009-03-16 15:45:12 -0400 (Mon, 16 Mar 2009) $
 */

public class VFSDirContext extends BaseDirContext {

	   private static final Logger log = Logger.getLogger(VFSDirContext.class);
    
    // ----------------------------------------------------------- Constructors


	   /**
	    * Builds a WAR directory context using the given environment.
	    */
	   public VFSDirContext() {
		   super();
	   }


	   /**
	    * Builds a WAR directory context using the given environment.
	    */
	   public VFSDirContext(Hashtable env) {
		   super(env);
	   }


	   /**
	    * Constructor used for returning fake subcontexts.
	    */
	   protected VFSDirContext(VirtualFile base) {
		   this.base = base;
	   }


    // ----------------------------------------------------- Instance Variables


    /**
     * The virtual file.
     */
    protected VirtualFile base = null;


    // ------------------------------------------------------------- Properties
    
    
    /**
     * Set the virtual file root.
     */
    public void setVirtualFile(VirtualFile base) {
        this.base = base;
    }
    
    
    // --------------------------------------------------------- Public Methods


    /**
     * Release any resources allocated for this directory context.
     */
    public void release() {

        if (base != null) {
            base.close();
        }
        base = null;
        super.release();

    }


    // -------------------------------------------------------- Context Methods


    /**
     * Retrieves the named object.
     * 
     * @param name the name of the object to look up
     * @return the object bound to name
     * @exception NamingException if a naming exception is encountered
     */
    public Object lookup(String name)
        throws NamingException {
        return lookup(new CompositeName(name));
    }


    /**
     * Retrieves the named object. If name is empty, returns a new instance 
     * of this context (which represents the same naming context as this 
     * context, but its environment may be modified independently and it may 
     * be accessed concurrently).
     * 
     * @param name the name of the object to look up
     * @return the object bound to name
     * @exception NamingException if a naming exception is encountered
     */
    public Object lookup(Name name)
        throws NamingException {
        if (name.isEmpty())
            return this;
        VirtualFile entry = null;
        try {
            entry = treeLookup(name);
        } catch (IOException e) {
            NamingException ex = new NamingException
                (sm.getString("resources.notFound", name));
            ex.initCause(e);
            throw ex;
        }
        if (entry == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));
        
        try {
            if (!isFile(entry)) {
                return new VFSDirContext(entry);
            } else {
                return new VFSResource(entry);
            }
        } catch (IOException e) {
            NamingException ex = new NamingException
                (sm.getString("resources.notFound", name));
            ex.initCause(e);
            throw ex;
        }
    }


    /**
     * Unbinds the named object. Removes the terminal atomic name in name 
     * from the target context--that named by all but the terminal atomic 
     * part of name.
     * <p>
     * This method is idempotent. It succeeds even if the terminal atomic 
     * name is not bound in the target context, but throws 
     * NameNotFoundException if any of the intermediate contexts do not exist. 
     * 
     * @param name the name to bind; may not be empty
     * @exception NameNotFoundException if an intermediate context does not 
     * exist
     * @exception NamingException if a naming exception is encountered
     */
    public void unbind(String name)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Binds a new name to the object bound to an old name, and unbinds the 
     * old name. Both names are relative to this context. Any attributes 
     * associated with the old name become associated with the new name. 
     * Intermediate contexts of the old name are not changed.
     * 
     * @param oldName the name of the existing binding; may not be empty
     * @param newName the name of the new binding; may not be empty
     * @exception NameAlreadyBoundException if newName is already bound
     * @exception NamingException if a naming exception is encountered
     */
    public void rename(String oldName, String newName)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Enumerates the names bound in the named context, along with the class 
     * names of objects bound to them. The contents of any subcontexts are 
     * not included.
     * <p>
     * If a binding is added to or removed from this context, its effect on 
     * an enumeration previously returned is undefined.
     * 
     * @param name the name of the context to list
     * @return an enumeration of the names and class names of the bindings in 
     * this context. Each element of the enumeration is of type NameClassPair.
     * @exception NamingException if a naming exception is encountered
     */
    public NamingEnumeration list(String name)
        throws NamingException {
        return list(new CompositeName(name));
    }


    /**
     * Enumerates the names bound in the named context, along with the class 
     * names of objects bound to them. The contents of any subcontexts are 
     * not included.
     * <p>
     * If a binding is added to or removed from this context, its effect on 
     * an enumeration previously returned is undefined.
     * 
     * @param name the name of the context to list
     * @return an enumeration of the names and class names of the bindings in 
     * this context. Each element of the enumeration is of type NameClassPair.
     * @exception NamingException if a naming exception is encountered
     */
    public NamingEnumeration list(Name name)
        throws NamingException {
        if (name.isEmpty()) {
            try {
                return new NamingContextEnumeration(list(base).iterator());
            } catch (IOException e) {
                NamingException ex = new NamingException
                    (sm.getString("resources.notFound", name));
                ex.initCause(e);
                throw ex;
            }
        }
        VirtualFile entry = null;
        try {
            entry = treeLookup(name);
        } catch (IOException e) {
            NamingException ex = new NamingException
                (sm.getString("resources.notFound", name));
            ex.initCause(e);
            throw ex;
        }
        if (entry == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));
        try {
            return new NamingContextEnumeration(list(entry).iterator());
        } catch (IOException e) {
            NamingException ex = new NamingException
                (sm.getString("resources.notFound", name));
            ex.initCause(e);
            throw ex;
        }
    }


    /**
     * Enumerates the names bound in the named context, along with the 
     * objects bound to them. The contents of any subcontexts are not 
     * included.
     * <p>
     * If a binding is added to or removed from this context, its effect on 
     * an enumeration previously returned is undefined.
     * 
     * @param name the name of the context to list
     * @return an enumeration of the bindings in this context. 
     * Each element of the enumeration is of type Binding.
     * @exception NamingException if a naming exception is encountered
     */
    public NamingEnumeration listBindings(String name)
        throws NamingException {
        return listBindings(new CompositeName(name));
    }


    /**
     * Enumerates the names bound in the named context, along with the 
     * objects bound to them. The contents of any subcontexts are not 
     * included.
     * <p>
     * If a binding is added to or removed from this context, its effect on 
     * an enumeration previously returned is undefined.
     * 
     * @param name the name of the context to list
     * @return an enumeration of the bindings in this context. 
     * Each element of the enumeration is of type Binding.
     * @exception NamingException if a naming exception is encountered
     */
    public NamingEnumeration listBindings(Name name)
        throws NamingException {
        if (name.isEmpty()) {
            try {
                return new NamingContextBindingsEnumeration(list(base).iterator(),
                        this);
            } catch (IOException e) {
                NamingException ex = new NamingException
                    (sm.getString("resources.notFound", name));
                ex.initCause(e);
                throw ex;
            }
        }
        VirtualFile entry = null;
        try {
            entry = treeLookup(name);
        } catch (IOException e) {
            NamingException ex = new NamingException
                (sm.getString("resources.notFound", name));
            ex.initCause(e);
            throw ex;
        }
        if (entry == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));
        try {
            return new NamingContextBindingsEnumeration(list(entry).iterator(),
                    this);
        } catch (IOException e) {
            NamingException ex = new NamingException
                (sm.getString("resources.notFound", name));
            ex.initCause(e);
            throw ex;
        }
    }


    /**
     * Destroys the named context and removes it from the namespace. Any 
     * attributes associated with the name are also removed. Intermediate 
     * contexts are not destroyed.
     * <p>
     * This method is idempotent. It succeeds even if the terminal atomic 
     * name is not bound in the target context, but throws 
     * NameNotFoundException if any of the intermediate contexts do not exist. 
     * 
     * In a federated naming system, a context from one naming system may be 
     * bound to a name in another. One can subsequently look up and perform 
     * operations on the foreign context using a composite name. However, an 
     * attempt destroy the context using this composite name will fail with 
     * NotContextException, because the foreign context is not a "subcontext" 
     * of the context in which it is bound. Instead, use unbind() to remove 
     * the binding of the foreign context. Destroying the foreign context 
     * requires that the destroySubcontext() be performed on a context from 
     * the foreign context's "native" naming system.
     * 
     * @param name the name of the context to be destroyed; may not be empty
     * @exception NameNotFoundException if an intermediate context does not 
     * exist
     * @exception NotContextException if the name is bound but does not name 
     * a context, or does not name a context of the appropriate type
     */
    public void destroySubcontext(String name)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Retrieves the named object, following links except for the terminal 
     * atomic component of the name. If the object bound to name is not a 
     * link, returns the object itself.
     * 
     * @param name the name of the object to look up
     * @return the object bound to name, not following the terminal link 
     * (if any).
     * @exception NamingException if a naming exception is encountered
     */
    public Object lookupLink(String name)
        throws NamingException {
        // Note : Links are not supported
        return lookup(name);
    }


    /**
     * Retrieves the full name of this context within its own namespace.
     * <p>
     * Many naming services have a notion of a "full name" for objects in 
     * their respective namespaces. For example, an LDAP entry has a 
     * distinguished name, and a DNS record has a fully qualified name. This 
     * method allows the client application to retrieve this name. The string 
     * returned by this method is not a JNDI composite name and should not be 
     * passed directly to context methods. In naming systems for which the 
     * notion of full name does not make sense, 
     * OperationNotSupportedException is thrown.
     * 
     * @return this context's name in its own namespace; never null
     * @exception OperationNotSupportedException if the naming system does 
     * not have the notion of a full name
     * @exception NamingException if a naming exception is encountered
     */
    public String getNameInNamespace()
        throws NamingException {
        return docBase;
    }


    // ----------------------------------------------------- DirContext Methods


    /**
     * Retrieves selected attributes associated with a named object. 
     * See the class description regarding attribute models, attribute type 
     * names, and operational attributes.
     * 
     * @return the requested attributes; never null
     * @param name the name of the object from which to retrieve attributes
     * @param attrIds the identifiers of the attributes to retrieve. null 
     * indicates that all attributes should be retrieved; an empty array 
     * indicates that none should be retrieved
     * @exception NamingException if a naming exception is encountered
     */
    public Attributes getAttributes(String name, String[] attrIds)
        throws NamingException {
        return getAttributes(new CompositeName(name), attrIds);
    }


    /**
     * Retrieves all of the attributes associated with a named object. 
     * 
     * @return the set of attributes associated with name. 
     * Returns an empty attribute set if name has no attributes; never null.
     * @param name the name of the object from which to retrieve attributes
     * @exception NamingException if a naming exception is encountered
     */
    public Attributes getAttributes(Name name, String[] attrIds)
        throws NamingException {
        
        VirtualFile entry = null;
        if (name.isEmpty()) {
            entry = base;
        } else {
            try {
                entry = treeLookup(name);
            } catch (IOException e) {
                NamingException ex = new NamingException
                    (sm.getString("resources.notFound", name));
                ex.initCause(e);
                throw ex;
            }
        }
        if (entry == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));
        
        ResourceAttributes attrs = new ResourceAttributes();
        try {
            attrs.setCreationDate(new Date(entry.getLastModified()));
            attrs.setName(entry.getName());
            if (isFile(entry))
                attrs.setResourceType("");
            attrs.setContentLength(entry.getSize());
            attrs.setLastModified(entry.getLastModified());
        } catch (IOException e) {
            NamingException ex = new NamingException
            (sm.getString("resources.notFound", name));
            ex.initCause(e);
            throw ex;
        }

        return attrs;
        
    }


    /**
     * Modifies the attributes associated with a named object. The order of 
     * the modifications is not specified. Where possible, the modifications 
     * are performed atomically.
     * 
     * @param name the name of the object whose attributes will be updated
     * @param mod_op the modification operation, one of: ADD_ATTRIBUTE, 
     * REPLACE_ATTRIBUTE, REMOVE_ATTRIBUTE
     * @param attrs the attributes to be used for the modification; may not 
     * be null
     * @exception AttributeModificationException if the modification cannot be
     * completed successfully
     * @exception NamingException if a naming exception is encountered
     */
    public void modifyAttributes(String name, int mod_op, Attributes attrs)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Modifies the attributes associated with a named object using an an 
     * ordered list of modifications. The modifications are performed in the 
     * order specified. Each modification specifies a modification operation 
     * code and an attribute on which to operate. Where possible, the 
     * modifications are performed atomically.
     * 
     * @param name the name of the object whose attributes will be updated
     * @param mods an ordered sequence of modifications to be performed; may 
     * not be null
     * @exception AttributeModificationException if the modification cannot be
     * completed successfully
     * @exception NamingException if a naming exception is encountered
     */
    public void modifyAttributes(String name, ModificationItem[] mods)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Binds a name to an object, along with associated attributes. If attrs 
     * is null, the resulting binding will have the attributes associated 
     * with obj if obj is a DirContext, and no attributes otherwise. If attrs 
     * is non-null, the resulting binding will have attrs as its attributes; 
     * any attributes associated with obj are ignored.
     * 
     * @param name the name to bind; may not be empty
     * @param obj the object to bind; possibly null
     * @param attrs the attributes to associate with the binding
     * @exception NameAlreadyBoundException if name is already bound
     * @exception InvalidAttributesException if some "mandatory" attributes 
     * of the binding are not supplied
     * @exception NamingException if a naming exception is encountered
     */
    public void bind(String name, Object obj, Attributes attrs)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Binds a name to an object, along with associated attributes, 
     * overwriting any existing binding. If attrs is null and obj is a 
     * DirContext, the attributes from obj are used. If attrs is null and obj 
     * is not a DirContext, any existing attributes associated with the object
     * already bound in the directory remain unchanged. If attrs is non-null, 
     * any existing attributes associated with the object already bound in 
     * the directory are removed and attrs is associated with the named 
     * object. If obj is a DirContext and attrs is non-null, the attributes 
     * of obj are ignored.
     * 
     * @param name the name to bind; may not be empty
     * @param obj the object to bind; possibly null
     * @param attrs the attributes to associate with the binding
     * @exception InvalidAttributesException if some "mandatory" attributes 
     * of the binding are not supplied
     * @exception NamingException if a naming exception is encountered
     */
    public void rebind(String name, Object obj, Attributes attrs)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Creates and binds a new context, along with associated attributes. 
     * This method creates a new subcontext with the given name, binds it in 
     * the target context (that named by all but terminal atomic component of 
     * the name), and associates the supplied attributes with the newly 
     * created object. All intermediate and target contexts must already 
     * exist. If attrs is null, this method is equivalent to 
     * Context.createSubcontext().
     * 
     * @param name the name of the context to create; may not be empty
     * @param attrs the attributes to associate with the newly created context
     * @return the newly created context
     * @exception NameAlreadyBoundException if the name is already bound
     * @exception InvalidAttributesException if attrs does not contain all 
     * the mandatory attributes required for creation
     * @exception NamingException if a naming exception is encountered
     */
    public DirContext createSubcontext(String name, Attributes attrs)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Retrieves the schema associated with the named object. The schema 
     * describes rules regarding the structure of the namespace and the 
     * attributes stored within it. The schema specifies what types of 
     * objects can be added to the directory and where they can be added; 
     * what mandatory and optional attributes an object can have. The range 
     * of support for schemas is directory-specific.
     * 
     * @param name the name of the object whose schema is to be retrieved
     * @return the schema associated with the context; never null
     * @exception OperationNotSupportedException if schema not supported
     * @exception NamingException if a naming exception is encountered
     */
    public DirContext getSchema(String name)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Retrieves a context containing the schema objects of the named 
     * object's class definitions.
     * 
     * @param name the name of the object whose object class definition is to 
     * be retrieved
     * @return the DirContext containing the named object's class 
     * definitions; never null
     * @exception OperationNotSupportedException if schema not supported
     * @exception NamingException if a naming exception is encountered
     */
    public DirContext getSchemaClassDefinition(String name)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Searches in a single context for objects that contain a specified set 
     * of attributes, and retrieves selected attributes. The search is 
     * performed using the default SearchControls settings.
     * 
     * @param name the name of the context to search
     * @param matchingAttributes the attributes to search for. If empty or 
     * null, all objects in the target context are returned.
     * @param attributesToReturn the attributes to return. null indicates 
     * that all attributes are to be returned; an empty array indicates that 
     * none are to be returned.
     * @return a non-null enumeration of SearchResult objects. Each 
     * SearchResult contains the attributes identified by attributesToReturn 
     * and the name of the corresponding object, named relative to the 
     * context named by name.
     * @exception NamingException if a naming exception is encountered
     */
    public NamingEnumeration search(String name, Attributes matchingAttributes,
                                    String[] attributesToReturn)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Searches in a single context for objects that contain a specified set 
     * of attributes. This method returns all the attributes of such objects. 
     * It is equivalent to supplying null as the atributesToReturn parameter 
     * to the method search(Name, Attributes, String[]).
     * 
     * @param name the name of the context to search
     * @param matchingAttributes the attributes to search for. If empty or 
     * null, all objects in the target context are returned.
     * @return a non-null enumeration of SearchResult objects. Each 
     * SearchResult contains the attributes identified by attributesToReturn 
     * and the name of the corresponding object, named relative to the 
     * context named by name.
     * @exception NamingException if a naming exception is encountered
     */
    public NamingEnumeration search(String name, Attributes matchingAttributes)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Searches in the named context or object for entries that satisfy the 
     * given search filter. Performs the search as specified by the search 
     * controls.
     * 
     * @param name the name of the context or object to search
     * @param filter the filter expression to use for the search; may not be 
     * null
     * @param cons the search controls that control the search. If null, 
     * the default search controls are used (equivalent to 
     * (new SearchControls())).
     * @return an enumeration of SearchResults of the objects that satisfy 
     * the filter; never null
     * @exception InvalidSearchFilterException if the search filter specified 
     * is not supported or understood by the underlying directory
     * @exception InvalidSearchControlsException if the search controls 
     * contain invalid settings
     * @exception NamingException if a naming exception is encountered
     */
    public NamingEnumeration search(String name, String filter, 
                                    SearchControls cons)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * Searches in the named context or object for entries that satisfy the 
     * given search filter. Performs the search as specified by the search 
     * controls.
     * 
     * @param name the name of the context or object to search
     * @param filterExpr the filter expression to use for the search. 
     * The expression may contain variables of the form "{i}" where i is a 
     * nonnegative integer. May not be null.
     * @param filterArgs the array of arguments to substitute for the 
     * variables in filterExpr. The value of filterArgs[i] will replace each 
     * occurrence of "{i}". If null, equivalent to an empty array.
     * @param cons the search controls that control the search. If null, the 
     * default search controls are used (equivalent to (new SearchControls())).
     * @return an enumeration of SearchResults of the objects that satisy the 
     * filter; never null
     * @exception ArrayIndexOutOfBoundsException if filterExpr contains {i} 
     * expressions where i is outside the bounds of the array filterArgs
     * @exception InvalidSearchControlsException if cons contains invalid 
     * settings
     * @exception InvalidSearchFilterException if filterExpr with filterArgs 
     * represents an invalid search filter
     * @exception NamingException if a naming exception is encountered
     */
    public NamingEnumeration search(String name, String filterExpr, 
                                    Object[] filterArgs, SearchControls cons)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    // ------------------------------------------------------ Protected Methods

    
    /**
     * Check if a VirtualFile is a regular file or a folder.
     */
    protected boolean isFile(VirtualFile file)
       throws IOException {
       return (file.isLeaf() || (file.getSize() > 0));
    }
    

    /**
     * Entry tree lookup.
     */
    protected VirtualFile treeLookup(Name name)
        throws IOException {
        if (base == null)
            return null;
        if (name.isEmpty())
            return base;
        VirtualFile currentFile = base;
        for (int i = 0; i < name.size(); i++) {
            if (name.get(i).length() == 0)
                continue;
            currentFile = currentFile.getChild(name.get(i));
            if (currentFile == null)
                return null;
        }
        return currentFile;
    }


    /**
     * List children as objects.
     */
    protected ArrayList<NamingEntry> list(VirtualFile entry)
        throws IOException {
        
        ArrayList<NamingEntry> entries = new ArrayList<NamingEntry>();
        if (!isFile(entry)) {
            Iterator<VirtualFile> children = entry.getChildren().iterator();
            NamingEntry namingEntry = null;

            while (children.hasNext()) {
                VirtualFile current = children.next();
                Object object = null;
                if (!isFile(current)) {
                    object = new VFSDirContext(current);
                } else {
                    object = new VFSResource(current);
                }
                namingEntry = new NamingEntry
                (current.getName(), object, NamingEntry.ENTRY);
                entries.add(namingEntry);
            }
        }
        
        return entries;
        
    }


    // ------------------------------------------------ WARResource Inner Class


    /**
     * This specialized resource implementation avoids opening the IputStream
     * to the WAR right away.
     */
    protected class VFSResource extends Resource {
        
        
        // -------------------------------------------------------- Constructor
        
        
        public VFSResource(VirtualFile entry) {
            this.entry = entry;
        }
        
        
        // --------------------------------------------------- Member Variables
        
        
        protected VirtualFile entry;
        
        
        // ----------------------------------------------------- Public Methods
        
        
        /**
         * Content accessor.
         * 
         * @return InputStream
         */
        public InputStream streamContent()
            throws IOException {
            if (binaryContent == null) {
                inputStream = entry.openStream();
            }
            return super.streamContent();
        }
        
        
    }


}

