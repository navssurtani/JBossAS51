/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.test.jbossts.crash;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.jboss.logging.Logger;


/**
 * Helper SLSB for playing (initiate, update, ...) with test entity.
 * 
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 * @version $Revision: 1.1 $
 */
@Stateless
public class TestEntityHelper implements TestEntityHelperRem, TestEntityHelperLocal
{
   public static final String REMOTE_JNDI_NAME      = TestEntityHelper.class.getSimpleName() + "/remote";
   public static final String LOCAL_JNDI_NAME       = TestEntityHelper.class.getSimpleName() + "/local";
   
   public static final int TEST_ENTITY_INIT_VALUE   =  1;
   
   private static Logger log = Logger.getLogger(TestEntityHelper.class);

   
   @PersistenceContext
   EntityManager em;

   /**
    * Initiates test entity with <code>entityPK</code> key to the {@link #TEST_ENTITY_INIT_VALUE}.
    * @param entityPK primary key of test entity
    * @return initiated test entity
    */
   @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
   public TestEntity initTestEntity(String entityPK)
   {
      TestEntity entity = em.find(TestEntity.class, entityPK);
      
      if (entity == null)
      {
         entity = new TestEntity(entityPK, TEST_ENTITY_INIT_VALUE);
         em.persist(entity);
      }
      else
      {
         entity.setA(TEST_ENTITY_INIT_VALUE);
      }
      
      return entity;
   }

   /**
    * Finds test entity with <code>entityPK</code> key.
    * @param entityPK primary key of test entity
    * @return test entity
    */
   public TestEntity getTestEntity(String entityPK)
   {
      TestEntity entity = em.find(TestEntity.class, entityPK);
      log.debug("TestEntityHelper#getTestEntity(" + entityPK + ") = " + entity);
      
      return entity;
   }

   /**
    * Updates test entity, i.e. increments its value.
    * @param entityPK primary key of test entity
    * @return true in success, false otherwise
    */
   @TransactionAttribute(TransactionAttributeType.REQUIRED)
   public boolean updateTestEntity(String entityPK)
   {
      try
      {
         TestEntity entity = em.find(TestEntity.class, entityPK);
         entity.setA(entity.getA() + 1);
         return true;
      } 
      catch (Exception e)
      {
         log.error("Cannot update a test entity", e);
      }
      return false;
   }
   
}
