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

package org.jboss.system.server.profileservice.repository.clustered.local.file;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jboss.logging.Logger;
import org.jboss.system.server.profileservice.repository.clustered.sync.AbstractSynchronizationAction;
import org.jboss.system.server.profileservice.repository.clustered.sync.ByteChunk;
import org.jboss.system.server.profileservice.repository.clustered.sync.ContentModification;
import org.jboss.system.server.profileservice.repository.clustered.sync.SynchronizationReadAction;

/**
 * {@link SynchronizationReadAction} that reads from a {@link File}.
 *
 * @author Brian Stansberry
 * 
 * @version $Revision: $
 */
public class FileReadAction extends AbstractSynchronizationAction<FileBasedSynchronizationActionContext>
      implements SynchronizationReadAction<FileBasedSynchronizationActionContext>
{
   private static final Logger log = Logger.getLogger(FileReadAction.class);
   
   /** 
    * Max file transfer buffer size that we read at a time.
    * This influences the number of times that we will invoke disk read/write file
    * operations versus how much memory we will consume for a file transfer. 
    */
   public static final int MAX_CHUNK_BUFFER_SIZE = 512 * 1024;
   
   private final File file;
   private InputStream stream;
   
   /**
    * Create a new FileReadAction.
    * 
    * @param file the file to read
    * @param context the overall context of the modification
    * @param modification the modification
    */
   public FileReadAction(File file, FileBasedSynchronizationActionContext context, 
                         ContentModification modification)
   {
      super(context, modification);
      if (file == null)
      {
         throw new IllegalArgumentException("Null file");
      }
      this.file = file;
   }
   
   // ------------------------------------  RepositorySynchronizationReadAction

   public ByteChunk getNextBytes() throws IOException
   {
      InputStream is = getInputStream();
      byte[] b = null;
      int read = -1;
      synchronized (is)
      {
         b = new byte[MAX_CHUNK_BUFFER_SIZE];
         read = is.read(b);
      }
      return new ByteChunk(b, read);
   }
   
   // --------------------------------------------------------------  Protected

   @Override
   protected void doCancel()
   {
      safeCloseStream();
   }

   @Override
   protected void doCommit()
   {
      safeCloseStream();
   }

   @Override
   protected void doComplete() throws Exception
   {
      safeCloseStream();
      if (log.isTraceEnabled())
      {
         ContentModification mod = getRepositoryContentModification();
         log.trace("doComplete(): " + mod.getType() + " for " + mod.getItem().getRelativePath());
      }
   }

   @Override
   protected boolean doPrepare()
   {
      safeCloseStream();
      return true;
   }

   @Override
   protected void doRollbackFromCancelled()
   {
      safeCloseStream();
   }

   @Override
   protected void doRollbackFromComplete()
   {
      safeCloseStream();
   }

   @Override
   protected void doRollbackFromOpen()
   {
      safeCloseStream();
   }

   @Override
   protected void doRollbackFromPrepared()
   {
      safeCloseStream();
   }

   @Override
   protected void doRollbackFromRollbackOnly()
   {
      safeCloseStream();
   }

   private synchronized InputStream getInputStream() throws IOException
   {
      State s = getState();
      if (s != State.OPEN && s != State.CANCELLED)
      {
         throw new IllegalStateException("Cannot read when state is " + s);
      }
      
      if (stream == null)
      {
         FileInputStream fis = new FileInputStream(file);
         stream = new BufferedInputStream(fis);
      }
      return stream;
   }
   
   private synchronized void safeCloseStream()
   {
      if (stream != null)
      {
         synchronized (stream)
         {
            try
            {
               stream.close();
            }
            catch (IOException e)
            {
               ContentModification mod = getRepositoryContentModification();
               log.debug("Caught exception closing stream for " + mod.getRootName() + 
                     " " + mod.getItem().getRelativePath(), e);
            }
            finally
            {
               stream = null;
            }
         }
      }
   }

}
