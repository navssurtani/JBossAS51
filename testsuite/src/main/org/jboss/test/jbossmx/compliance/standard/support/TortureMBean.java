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
package org.jboss.test.jbossmx.compliance.standard.support;

/**
 * @author  <a href="mailto:trevor@protocool.com">Trevor Squires</a>.
 * @author  <a href="mailto:dimitris@jboss.org">Dimitris Andreadis</a>.
 */

public interface TortureMBean
{
   String getNiceString();
   void setNiceString(String nice);

   boolean isNiceBoolean();
   void setNiceBoolean(boolean nice);

   void setInt(int foo);
   void setIntArray(int[] foo);
   void setNestedIntArray(int[][][] foo);

   void setInteger(Integer foo);
   void setIntegerArray(Integer[] foo);
   void setNestedIntegerArray(Integer[][][] foo);

   int getMyinteger();
   int[] getMyintegerArray();
   int[][][] getMyNestedintegerArray();

   Integer getMyInteger();
   Integer[] getMyIntegerArray();
   Integer[][][] getMyNestedIntegerArray();

   // this should give an isIs
   boolean isready();
   
   // this is an operation really
   Boolean isReady();

   // these should be operations
   boolean ispeachy(int peachy);
   Boolean isPeachy(int peachy);
   String issuer();
   int settlement(String thing);
   void setMulti(String foo, Integer bar);
   String getResult(String source);
   void setNothing();
   void getNothing();

   // ok, we have an attribute called Something
   // and an operation called getSomething...
   void setSomething(String something);
   void getSomething();

   // ooh yesssss
   String[][] doSomethingCrazy(Object[] args, String[] foo, int[][][] goMental);
}
