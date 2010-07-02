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
package org.jboss.test.bpel.ws.consumption.partner;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jboss.test.bpel.ws.consumption.partner.spi.Dictionary;
import org.jboss.test.bpel.ws.consumption.partner.spi.DictionaryFactory;
import org.jboss.test.bpel.ws.consumption.partner.types.TDictionaryNotAvailable;
import org.jboss.test.bpel.ws.consumption.partner.types.TQuoteStatus;
import org.jboss.test.bpel.ws.consumption.partner.types.TTextNotTranslatable;

/**
 * @author Alejandro Guizar
 * @version $Revision: 81084 $ $Date: 2008-11-14 12:30:43 -0500 (Fri, 14 Nov 2008) $
 */
public class TextTranslator_Impl implements TextTranslator, Remote {
  
  private static Set clientNames = new HashSet();
  
  private static final Log log = LogFactory.getLog(TextTranslator_Impl.class);

  public String translate(String text, String sourceLanguage, String targetLanguage)
      throws TDictionaryNotAvailable, TTextNotTranslatable, RemoteException {
    Locale sourceLocale = new Locale(sourceLanguage);
    Locale targetLocale = new Locale(targetLanguage);
    DictionaryFactory dictionaryFactory = DictionaryFactory.getInstance(sourceLocale, targetLocale);
    Dictionary dictionary = dictionaryFactory.createDictionary(sourceLocale, targetLocale);
    return dictionary.translate(text);
  }

  public void quoteTranslation(String clientName, String text, 
      String sourceLanguage, String targetLanguage) throws RemoteException {
    log.debug("received quotation request: clientName=" + clientName);
    clientNames.add(clientName);
  }
  
  public TQuoteStatus getQuotationStatus(String clientName) throws RemoteException {
    return clientNames.contains(clientName) ? TQuoteStatus.received : TQuoteStatus.none;
  }
}
