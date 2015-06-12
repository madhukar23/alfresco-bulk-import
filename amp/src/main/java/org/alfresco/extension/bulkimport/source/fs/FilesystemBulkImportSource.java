/*
 * Copyright (C) 2007-2015 Peter Monks.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This file is part of an unsupported extension to Alfresco.
 * 
 */


package org.alfresco.extension.bulkimport.source.fs;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.alfresco.repo.content.ContentStore;

import org.alfresco.extension.bulkimport.BulkImportCallback;
import org.alfresco.extension.bulkimport.source.AbstractBulkImportSource;
import org.alfresco.extension.bulkimport.source.BulkImportSourceStatus;
import org.alfresco.extension.bulkimport.source.fs.DirectoryAnalyser.AnalysedDirectory;

import static org.alfresco.extension.bulkimport.util.LogUtils.*;
import static org.alfresco.extension.bulkimport.source.fs.FilesystemSourceUtils.*;


/**
 * This class is a Filesystem specific version of a <code>BulkImportSource</code>.
 *
 * @author Peter Monks (pmonks@gmail.com)
 *
 */
public final class FilesystemBulkImportSource
    extends AbstractBulkImportSource
{
    private final static Log log = LogFactory.getLog(FilesystemBulkImportSource.class);
    
    public  final static String IMPORT_SOURCE_NAME = "Default";
    
    private final static String IMPORT_SOURCE_DESCRIPTION   = "This import source reads content, metadata and versions from the <strong>Alfresco server's</strong> filesystem, in the format <a href='####TODO'>described here</a>.";
    private final static String IMPORT_SOURCE_CONFIG_UI_URI = "/bulk/import/fs/config";
    
    private final static String PARAMETER_SOURCE_DIRECTORY = "sourceDirectory";
    
    private final DirectoryAnalyser  directoryAnalyser;
    private final ContentStore       configuredContentStore;
    private final List<ImportFilter> importFilters;
    
    private File sourceDirectory = null;
    
    public FilesystemBulkImportSource(final DirectoryAnalyser  directoryAnalyser,
                                      final ContentStore       configuredContentStore,
                                      final List<ImportFilter> importFilters)
    {
        super(IMPORT_SOURCE_NAME, IMPORT_SOURCE_DESCRIPTION, IMPORT_SOURCE_CONFIG_UI_URI, null);
        
        // PRECONDITIONS
        assert directoryAnalyser      != null : "directoryAnalyser must not be null.";
        assert configuredContentStore != null : "configuredContentStore must not be null.";
        
        // Body
        this.directoryAnalyser      = directoryAnalyser;
        this.configuredContentStore = configuredContentStore;
        this.importFilters          = importFilters;
    }
    
    
    /**
     * @see org.alfresco.extension.bulkimport.source.BulkImportSource#getParameters()
     */
    @Override
    public Map<String, String> getParameters()
    {
        Map<String, String> result = null;
        
        if (sourceDirectory != null)
        {
            result = new HashMap<String, String>();
            result.put("Source directory", sourceDirectory.getAbsolutePath());
        }
        
        return(result);
    }


    /**
     * @see org.alfresco.extension.bulkimport.source.AbstractBulkImportSource#init(org.alfresco.extension.bulkimport.source.BulkImportSourceStatus, java.util.Map)
     */
    @Override
    public void init(final BulkImportSourceStatus importStatus, final Map<String, List<String>> parameters)
    {
        final List<String> sourceDirectoryParameterValues = parameters.get(PARAMETER_SOURCE_DIRECTORY);
        String             sourceDirectoryName            = null;
        
        if (sourceDirectoryParameterValues        == null ||
            sourceDirectoryParameterValues.size() != 1)
        {
            throw new IllegalArgumentException("Mandatory parameter '" + PARAMETER_SOURCE_DIRECTORY + "' was missing, or provided more than once.");
        }
        
        sourceDirectoryName = sourceDirectoryParameterValues.get(0);
        
        if (sourceDirectoryName                 == null ||
            sourceDirectoryName.trim().length() == 0)
        {
            throw new IllegalArgumentException("Source directory was provided, but is empty.");
        }
        
        sourceDirectory = new File(sourceDirectoryName);
        
        if (!sourceDirectory.exists())
        {
            sourceDirectory = null;
            throw new RuntimeException(new FileNotFoundException("Source directory '" + sourceDirectoryName + "' doesn't exist."));  // Checked exceptions == #fail
        }
        
        if (!sourceDirectory.canRead())
        {
            sourceDirectory = null;
            throw new SecurityException("No read access to source directory '" + sourceDirectoryName + "'.");
        }
        
        directoryAnalyser.init(importStatus);
    }


    /**
     * @see org.alfresco.extension.bulkimport.source.BulkImportSource#inPlaceImportPossible()
     */
    @Override
    public boolean inPlaceImportPossible()
    {
        return(isInContentStore(configuredContentStore, sourceDirectory));
    }
    

    /**
     * @see org.alfresco.extension.bulkimport.source.BulkImportSource#scanFolders(org.alfresco.extension.bulkimport.source.BulkImportSourceStatus, org.alfresco.extension.bulkimport.BulkImportCallback)
     */
    @Override
    public void scanFolders(final BulkImportSourceStatus status, final BulkImportCallback callback)
        throws InterruptedException
    {
        scanDirectory(status, callback, sourceDirectory, sourceDirectory, false);
    }


    /**
     * @see org.alfresco.extension.bulkimport.source.BulkImportSource#scanFiles(java.util.Map, org.alfresco.extension.bulkimport.source.BulkImportSourceStatus, org.alfresco.extension.bulkimport.BulkImportCallback)
     */
    @Override
    public void scanFiles(BulkImportSourceStatus status, BulkImportCallback callback)
        throws InterruptedException
    {
        scanDirectory(status, callback, sourceDirectory, sourceDirectory, true);
    }

    
    /**
     * This method actually does the work of scanning.
     */
    private void scanDirectory(final BulkImportSourceStatus status,
                               final BulkImportCallback     callback,
                               final File                   sourceDirectory,
                               final File                   directory,
                               final boolean                submitFiles)
        throws InterruptedException
    {
        if (debug(log)) debug(log, "Scanning directory " + directory.getAbsolutePath() + " for " + (submitFiles ? "Files" : "Folders") + "...");
        
        status.setCurrentlyScanning(sourceDirectory.getAbsolutePath());
                              
        final AnalysedDirectory analysedDirectory = directoryAnalyser.analyseDirectory(sourceDirectory, directory);
        
        if (analysedDirectory != null)
        {
            if (!submitFiles && analysedDirectory.directoryItems != null)
            {
                for (final FilesystemBulkImportItem directoryItem : analysedDirectory.directoryItems)
                {
                    if (!filter(directoryItem))
                    {
                        callback.submit(directoryItem);
                    }
                }
            }

            if (submitFiles && analysedDirectory.fileItems != null)
            {
                for (final FilesystemBulkImportItem fileItem : analysedDirectory.fileItems)
                {
                    if (!filter(fileItem))
                    {
                        callback.submit(fileItem);
                    }
                }
            }
            
            if (debug(log)) debug(log, "Finished scanning directory " + directory.getAbsolutePath() + ".");
            
            // Recurse into subdirectories and scan them too
            if (analysedDirectory.directoryItems != null && analysedDirectory.directoryItems.size() > 0)
            {
                if (debug(log)) debug(log, "Recursing into " + analysedDirectory.directoryItems.size() + " subdirectories of " + directory.getAbsolutePath());
                
                for (final FilesystemBulkImportItem directoryItem : analysedDirectory.directoryItems)
                {
                    scanDirectory(status,
                                  callback,
                                  sourceDirectory,
                                  ((FilesystemVersion)(directoryItem.getVersions().first())).getContentFile(),
                                  submitFiles);
                }
            }
            else
            {
                if (debug(log)) debug(log, directory.getAbsolutePath() + " has no subdirectories.");
            }
        }
    }
    
    
    
    private final boolean filter(final FilesystemBulkImportItem item)
    {
        boolean result = false;
        
        if (importFilters != null)
        {
            for (final ImportFilter importFilter : importFilters)
            {
                if (importFilter.shouldFilter(item))
                {
                    result = true;
                    break;
                }
            }
        }
        
        return(result);
    }
    
}