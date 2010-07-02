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
package org.jboss.test.bpel.ws.consumption.partner.resource;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jboss.test.bpel.ws.consumption.partner.spi.Dictionary;
import org.jboss.test.bpel.ws.consumption.partner.spi.DictionaryFactory;

/**
 * @author Alejandro Guizar
 * @version $Revision: 81084 $ $Date: 2008-11-14 12:30:43 -0500 (Fri, 14 Nov 2008) $
 */
public class ResourceDictionaryFactory extends DictionaryFactory {
	
	private static final Log log = LogFactory.getLog(ResourceDictionaryFactory.class);

  /** {@inheritDoc} */
  public boolean acceptsLocales(Locale sourceLocale, Locale targetLocale) {
    return getBundle(sourceLocale, targetLocale) != null;
  }
  
  /** {@inheritDoc} */
  public Dictionary createDictionary(Locale sourceLocale, Locale targetLocale) {
    return new ResourceDictionary(getBundle(sourceLocale, targetLocale));
  }
  
  protected ResourceBundle getBundle(Locale sourceLocale, Locale targetLocale) {
  	String sourceLanguage = sourceLocale.getLanguage();
  	log.debug("loading bundle: sourceLanguage=" + sourceLanguage + ", targetLocale=" + targetLocale);
  	ResourceBundle bundle;
    try {
    	bundle = ResourceBundle.getBundle(getBaseName(sourceLanguage), targetLocale);
    	String bundleLanguage = bundle.getLocale().getLanguage();
    	if (bundleLanguage.equals(targetLocale.getLanguage())) {
      	log.debug("loaded bundle: bundleLanguage=" + bundleLanguage);
    	}
    	else {
    	  bundle = null;
    	  log.debug("loaded bundle, but it does not correspond to the target locale: " +
    	  		"bundleLanguage=" + bundleLanguage);
    	}
    }
    catch (MissingResourceException e) {
      bundle = null;
    	log.debug("bundle not found", e);
    }
    return bundle;
  }
  
  protected String getBaseName(String sourceLanguage) {
    StringBuffer baseName = new StringBuffer(getClass().getName());
    baseName.setLength(baseName.lastIndexOf(".") + 1);
    baseName.append(sourceLanguage);
    return baseName.toString();
  }
}
