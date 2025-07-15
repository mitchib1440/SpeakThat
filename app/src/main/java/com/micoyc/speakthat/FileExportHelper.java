package com.micoyc.speakthat;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileExportHelper {
    private static final String TAG = "FileExportHelper";
    
    /**
     * Creates a file in the appropriate directory with fallback support
     * @param context The application context
     * @param subdirectory The subdirectory name (e.g., "exports", "logs")
     * @param filename The filename to create
     * @param content The content to write to the file
     * @return The created file, or null if creation failed
     */
    public static File createExportFile(Context context, String subdirectory, String filename, String content) {
        File exportFile = null;
        Exception lastException = null;
        
        // Try external files directory first (preferred)
        try {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                File exportDir = new File(externalDir, subdirectory);
                if (exportDir.exists() || exportDir.mkdirs()) {
                    exportFile = new File(exportDir, filename);
                    if (writeFileContent(exportFile, content)) {
                        InAppLogger.log("FileExport", "File created successfully in external storage: " + exportFile.getAbsolutePath());
                        return exportFile;
                    }
                }
            }
        } catch (Exception e) {
            lastException = e;
            InAppLogger.logError("FileExport", "External storage failed: " + e.getMessage());
        }
        
        // Fallback to internal files directory
        try {
            File internalDir = new File(context.getFilesDir(), subdirectory);
            if (internalDir.exists() || internalDir.mkdirs()) {
                exportFile = new File(internalDir, filename);
                if (writeFileContent(exportFile, content)) {
                    InAppLogger.log("FileExport", "File created successfully in internal storage: " + exportFile.getAbsolutePath());
                    return exportFile;
                }
            }
        } catch (Exception e) {
            lastException = e;
            InAppLogger.logError("FileExport", "Internal storage failed: " + e.getMessage());
        }
        
        // If both failed, log the error
        String errorMsg = "Failed to create export file. External storage failed: " + 
                         (lastException != null ? lastException.getMessage() : "Unknown error");
        InAppLogger.logError("FileExport", errorMsg);
        Log.e(TAG, errorMsg, lastException);
        
        return null;
    }
    
    /**
     * Writes content to a file with proper error handling
     * @param file The file to write to
     * @param content The content to write
     * @return true if successful, false otherwise
     */
    private static boolean writeFileContent(File file, String content) {
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
            return true;
        } catch (IOException e) {
            InAppLogger.logError("FileExport", "Failed to write file content: " + e.getMessage());
            Log.e(TAG, "Failed to write file content", e);
            return false;
        }
    }
    
    /**
     * Gets the appropriate directory for file exports
     * @param context The application context
     * @param subdirectory The subdirectory name
     * @return The directory file, or null if creation failed
     */
    public static File getExportDirectory(Context context, String subdirectory) {
        // Try external files directory first
        try {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                File exportDir = new File(externalDir, subdirectory);
                if (exportDir.exists() || exportDir.mkdirs()) {
                    return exportDir;
                }
            }
        } catch (Exception e) {
            InAppLogger.logError("FileExport", "External directory creation failed: " + e.getMessage());
        }
        
        // Fallback to internal files directory
        try {
            File internalDir = new File(context.getFilesDir(), subdirectory);
            if (internalDir.exists() || internalDir.mkdirs()) {
                return internalDir;
            }
        } catch (Exception e) {
            InAppLogger.logError("FileExport", "Internal directory creation failed: " + e.getMessage());
        }
        
        return null;
    }
} 