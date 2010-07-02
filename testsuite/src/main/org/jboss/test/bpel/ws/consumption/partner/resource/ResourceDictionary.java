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

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.jboss.test.bpel.ws.consumption.partner.spi.Dictionary;
import org.jboss.test.bpel.ws.consumption.partner.types.TDocument;
import org.jboss.test.bpel.ws.consumption.partner.types.TDocumentBody;
import org.jboss.test.bpel.ws.consumption.partner.types.TDocumentHead;
import org.jboss.test.bpel.ws.consumption.partner.types.TTextNotTranslatable;

/**
 * @author Alejandro Guizar
 * @version $Revision: 81084 $ $Date: 2008-11-14 12:30:43 -0500 (Fri, 14 Nov 2008) $
 */
public class ResourceDictionary implements Dictionary {
  
  private final ResourceBundle bundle;
  
  public ResourceDictionary(ResourceBundle bundle) {
    this.bundle = bundle;
  }
  
  /** {@inheritDoc} */
  public String translate(String text) throws TTextNotTranslatable {
    try {
      return bundle.getString(text);
    }
    catch (MissingResourceException e) {
      throw new TTextNotTranslatable(text);
    }
  }

  /** {@inheritDoc} */
  public TDocument translate(TDocument document) throws TTextNotTranslatable {
    TDocumentHead transHead = new TDocumentHead();
    transHead.setTitle(bundle.getString(document.getHead().getTitle()));
    transHead.setLanguage(bundle.getLocale().getLanguage());
    
    String[] paragraphs = document.getBody().getParagraph();
    String[] transParagraphs = new String[paragraphs.length];
    for (int i = 0; i < paragraphs.length; i++) {
      String paragraph = paragraphs[i];
      try {
        transParagraphs[i] = bundle.getString(paragraph);
      }
      catch (MissingResourceException e) {
        throw new TTextNotTranslatable(paragraph);
      }
    }
    
    TDocumentBody transBody = new TDocumentBody();
    transBody.setParagraph(transParagraphs);
    
    TDocument targetDocument = new TDocument();
    targetDocument.setHead(transHead);
    targetDocument.setBody(transBody);
    
    return targetDocument;
  }
}
