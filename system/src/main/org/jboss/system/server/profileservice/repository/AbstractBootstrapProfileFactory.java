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
package org.jboss.system.server.profileservice.repository;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.profileservice.spi.Profile;
import org.jboss.profileservice.spi.ProfileKey;
import org.jboss.profileservice.spi.metadata.ProfileKeyMetaData;
import org.jboss.profileservice.spi.metadata.ProfileMetaData;
import org.jboss.profileservice.spi.metadata.SubProfileMetaData;

/**
 * The abstract profile factory.
 * 
 * @author <a href="mailto:emuckenh@redhat.com">Emanuel Muckenhuber</a>
 * @version $Revision: 86174 $
 */
public abstract class AbstractBootstrapProfileFactory
{
   /** The profile map. */
   private Map<ProfileKey, ProfileMetaData> profileMap = new HashMap<ProfileKey, ProfileMetaData>();
   
   /** The profiles map. */
   private Map<ProfileKey, List<ProfileMetaData>> profilesMetaData = new HashMap<ProfileKey, List<ProfileMetaData>>();
   
   /** The profile factory. */
   private AbstractProfileFactory profileFactory;
   
   /** The logger */
   protected final Logger log = Logger.getLogger(getClass()); 

   public AbstractProfileFactory getProfileFactory()
   {
      return profileFactory;
   }
   
   public void setProfileFactory(AbstractProfileFactory profileFactory)
   {
      this.profileFactory = profileFactory;
   }
   
   /**
    * Create the meta data required for this profile.
    * 
    * @param key the root profile key.
    * @throws Exception
    */
   protected abstract void createProfileMetaData(ProfileKey key, URL url) throws Exception;  
   
   /**
    * Create the profiles required for this profile. 
    * 
    * @param rootKey the root profile key.
    * @param uri the profile root uri for parsing.
    * @return a collection of profiles
    * @throws Exception
    */
   public Collection<Profile> createProfiles(ProfileKey rootKey, URL url) throws Exception
   {
      if(rootKey == null)
         throw new IllegalArgumentException("Null profile key");
         
      // Create the profile meta data.
      createProfileMetaData(rootKey, url);
      
      Map<ProfileKey, Profile> profiles = new HashMap<ProfileKey, Profile>();
      // Create the real profiles
      createProfiles(profiles, rootKey);
      
      return profiles.values();
   }

   /**
    * Create profiles
    * 
    * @param profiles the profiles map 
    * @param key the ProfileKey
    * @throws Exception
    */
   public void createProfiles(Map<ProfileKey, Profile> profiles, ProfileKey key) throws Exception
   {
      ProfileMetaData profileMetaData = this.profileMap.get(key);
      if(profileMetaData == null)
         throw new IllegalStateException("Could not find metaData for key: " + key);

      List<ProfileKey> subProfiles = new ArrayList<ProfileKey>();
      
      // Create the profile
      createProfile(profiles, subProfiles, key, profileMetaData);

   }

   /**
    * Create a Profile with it's sub-profiles.
    * 
    * @param profiles the profiles map
    * @param subProfiles the sub-profiles list
    * @param key the ProfileKey
    * @param profileMetaData the profile meta data
    * @throws Exception
    */
   public void createProfile(Map<ProfileKey, Profile> profiles, List<ProfileKey> subProfiles, ProfileKey key, ProfileMetaData profileMetaData) throws Exception
   {
      // Don't process a profile twice
      if(profiles.containsKey(key))
         return;
      
      if(log.isTraceEnabled())
         log.trace("Creating profile for key: " + key);
      
      // First recursively process the sub profiles
      processSubProfiles(profiles, subProfiles, profileMetaData.getSubprofiles());
      
      // Create the profile
      // Provide a new copy of the keys
      Profile profile = profileFactory.createProfile(key, profileMetaData, new ArrayList<ProfileKey>(subProfiles));
      
      // Add to the profileMap
      profiles.put(key, profile);
      
      // FIXME add a implicit dependency for the next
      if(subProfiles.contains(key) == false)
         subProfiles.add(key);
   }
   
   /**
    * Process the sub-profiles.
    * 
    * @param profiles the profiles map
    * @param subProfileKeys the sub-profiles
    * @param subProfilesMetaData a list of sub-profile metadata 
    * @throws Exception
    */
   private void processSubProfiles(Map<ProfileKey, Profile> profiles, List<ProfileKey> subProfileKeys, List<SubProfileMetaData> subProfilesMetaData) throws Exception
   {
      if(subProfilesMetaData == null || subProfilesMetaData.isEmpty())
         return;
      
      for(SubProfileMetaData subProfile : subProfilesMetaData)
      {
         ProfileKey subProfileKey = createProfileKey(subProfile);
         if(this.profileMap.containsKey(subProfileKey))
         {
            createProfile(profiles, subProfileKeys, subProfileKey, this.profileMap.get(subProfileKey));
         }
         else if(this.profilesMetaData.containsKey(subProfileKey))
         {
            List<ProfileMetaData> subProfileAliases = this.profilesMetaData.get(subProfileKey);
            processSubProfileAlias(profiles, subProfileKeys, subProfileAliases);
         }
         else
         {
            throw new IllegalStateException("Could not resolve profile meta data for key: " + subProfileKey);
         }
      }     
   }
   
   /**
    * Process the profiles alias for a sub-profile.
    * 
    * @param profiles the profiles map
    * @param subProfileKeys the sub-profiles
    * @param subprofileAliases the profile meta data for the sub-profiles
    * @throws Exception
    */
   private void processSubProfileAlias(Map<ProfileKey, Profile> profiles, List<ProfileKey> subProfileKeys, List<ProfileMetaData> subprofileAliases) throws Exception
   {
      if(subprofileAliases == null || subprofileAliases.isEmpty())
         return;
      
      for(ProfileMetaData metaData : subprofileAliases)
      {
         ProfileKey key = createProfileKey(metaData);
         createProfile(profiles, subProfileKeys, key, metaData);
      }
   }
   
   /**
    * Add profile meta data.
    * 
    * @param key the profile key.
    * @param metaData the profile meta data.
    */
   protected void addProfile(ProfileKey key, ProfileMetaData metaData)
   {
      // The keys have be unique
      if(this.profileMap.containsKey(key))
         throw new IllegalStateException("Duplicate key: " + key);

      if(this.profilesMetaData.containsKey(key))
         throw new IllegalStateException("Duplicate key: " + key);
      
      this.profileMap.put(key, metaData);
   }
   
   /**
    * Add the profiles meta data, which is basically is a reference to
    * a list of profile meta data.
    * 
    * @param key the profile key.
    * @param metaData a list of profile meta data.
    */
   protected void addProfiles(ProfileKey key, List<ProfileMetaData> metaData)
   {
      if(this.profileMap.containsKey(key))
      {
         // Ignore the default key, which gets generated for <profiles/>
         if(ProfileKey.DEFAULT.equals(key.getDomain())
               && ProfileKey.DEFAULT.equals(key.getServer())
               && ProfileKey.DEFAULT.equals(key.getName()))
         {
            return;
         }
         else
         {
            throw new IllegalStateException("Duplicate key: " + key);  
         }
      }

      // Create a entry for <profiles>
      List<ProfileMetaData> profileList = this.profilesMetaData.get(key);
      if(profileList == null)
      {
         profileList = new ArrayList<ProfileMetaData>();
         this.profilesMetaData.put(key, profileList);
      }
      
      if(metaData != null && metaData.isEmpty() == false)
      {
         profileList.addAll(metaData);
      }
   }
   
   public static ProfileKey createProfileKey(ProfileKeyMetaData md)
   {
      return new ProfileKey(md.getDomain(), md.getServer(), md.getName());
   }
}