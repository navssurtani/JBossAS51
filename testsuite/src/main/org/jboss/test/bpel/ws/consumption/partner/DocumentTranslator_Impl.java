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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jboss.test.bpel.ws.consumption.partner.spi.Dictionary;
import org.jboss.test.bpel.ws.consumption.partner.spi.DictionaryFactory;
import org.jboss.test.bpel.ws.consumption.partner.types.TDictionaryNotAvailable;
import org.jboss.test.bpel.ws.consumption.partner.types.TDocument;
import org.jboss.test.bpel.ws.consumption.partner.types.TQuotationRequest;
import org.jboss.test.bpel.ws.consumption.partner.types.TQuoteStatus;
import org.jboss.test.bpel.ws.consumption.partner.types.TStatusRequest;
import org.jboss.test.bpel.ws.consumption.partner.types.TStatusResponse;
import org.jboss.test.bpel.ws.consumption.partner.types.TTextNotTranslatable;
import org.jboss.test.bpel.ws.consumption.partner.types.TTranslationRequest;

/**
 * @author Alejandro Guizar
 * @version $Revision: 81084 $ $Date: 2008-11-14 12:30:43 -0500 (Fri, 14 Nov 2008) $
 */
public class DocumentTranslator_Impl implements DocumentTranslator, Remote {
  
  private static Map quotationRequests = new HashMap();
  
  private static final Log log = LogFactory.getLog(DocumentTranslator_Impl.class);

  public TDocument translate(TTranslationRequest translationRequest)
  throws TDictionaryNotAvailable, TTextNotTranslatable, RemoteException {
    TDocument document = translationRequest.getDocument();
    Locale sourceLocale = new Locale(document.getHead().getLanguage());
    Locale targetLocale = new Locale(translationRequest.getTargetLanguage());
    DictionaryFactory dictionaryFactory = DictionaryFactory.getInstance(sourceLocale, targetLocale);
    Dictionary dictionary = dictionaryFactory.createDictionary(sourceLocale, targetLocale);
    return dictionary.translate(document);
  }

  public void quoteTranslation(TQuotationRequest quotationRequest) throws RemoteException {
    String clientName = quotationRequest.getClientName();
    log.debug("received quotation request: clientName=" + clientName);
    quotationRequests.put(clientName, quotationRequest);
  }
  
  public TStatusResponse getQuotationStatus(TStatusRequest statusRequest) 
  throws RemoteException {
    TStatusResponse statusResponse = new TStatusResponse();
    TQuoteStatus quoteStatus = quotationRequests.containsKey(statusRequest.getClientName()) ?
        TQuoteStatus.received : TQuoteStatus.none;
    statusResponse.setStatus(quoteStatus);
	  return statusResponse;
  }
}
