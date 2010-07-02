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
package org.jboss.test.bpel.ws.consumption.partner.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jboss.test.bpel.ws.consumption.partner.resource.ResourceDictionaryFactory;
import org.jboss.test.bpel.ws.consumption.partner.types.TDictionaryNotAvailable;

/**
 * @author Alejandro Guizar
 * @version $Revision: 81084 $ $Date: 2008-11-14 12:30:43 -0500 (Fri, 14 Nov 2008) $
 */
public abstract class DictionaryFactory {
  
  private static List instances = new ArrayList();
  
  public abstract Dictionary createDictionary(Locale sourceLocale, Locale targetLocale);
  
  public abstract boolean acceptsLocales(Locale sourceLocale, Locale targetLocale);
    
  public static DictionaryFactory getInstance(Locale sourceLocale, Locale targetLocale)
  throws TDictionaryNotAvailable {
    for (int i = 0, n = instances.size(); i < n; i++) {
      DictionaryFactory factory = (DictionaryFactory) instances.get(i);
      if (factory.acceptsLocales(sourceLocale, targetLocale)) {
        return factory;
      }
    }
    throw new TDictionaryNotAvailable();
  }
  
  public static void registerInstance(DictionaryFactory instance) {
    instances.add(instance);
  }
  
  static {
    registerInstance(new ResourceDictionaryFactory());
  }
}