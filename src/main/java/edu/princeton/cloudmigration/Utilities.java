/**
 * 
 */
package edu.princeton.cloudmigration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.Collection;
import java.util.Iterator;
import java.util.ResourceBundle;

import eu.medsea.mimeutil.MimeUtil;
import eu.medsea.mimeutil.MimeType;

import java.net.FileNameMap;
import java.net.URLConnection;

import org.apache.log4j.Logger;

/**
 * This class contains utility methods for the MigrateWebSpaceData application.
 * 
 * @author Mark Ratliff
 *
 */

public class Utilities {
	
	private static Logger logger = Logger.getLogger(Utilities.class);
	
	// MySQL database connection parameters
	private static final String dbserver;
	private static final String dbname;
	private static final String dbuser;
	private static final String dbpasswd;
	
	int filenamelength_indx = 0;
	
	// Load configuration values
	static 
	{	
		ResourceBundle rb = ResourceBundle.getBundle(DataMigrator.CONFIG_FILE_NAME);
		
		dbserver = rb.getString("dbserver");
		dbname = rb.getString("dbname");
		dbuser = rb.getString("dbuser");
		dbpasswd = rb.getString("dbpasswd");
		
		MimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.ExtensionMimeDetector");
	}

	public String getFilename(File file, File rootfolder)
	{
		String filename_result;
		String trimmedfilename;
		
		String fullpath = file.getAbsolutePath();
		
		StringBuffer sb = new StringBuffer(fullpath);
		
		// Remove the working directory from the beginning of the path
		sb.delete(0, rootfolder.getParent().length()+1);
		
		//TODO:  We should prepend the path to the target folder in Box to get the
		//       file name length in Box.
		
		if (sb.length() > 256)
		{
			trimmedfilename = trimFilename(sb);
		}
		else
		{
			trimmedfilename = sb.toString();
		}
		
		// Remove path elements and keep only the filename
		int idx = trimmedfilename.lastIndexOf("/");
		if (idx >= 0)
		{
			trimmedfilename = trimmedfilename.substring(idx + 1);
		}
		
		filename_result = replaceIllegalChars(trimmedfilename);
		
		return filename_result;
	}

	/**
	 * Ensure that filename has no more than 256 characters and no illegal characters
	 *
	 * @param fullpath The full path of the file
	 * @returns null if filename requires no modification
	 */

	public String trimFilename(StringBuffer filepath)
	{		
		boolean hasExtension = false;

		int filenamelength = filepath.length();
		int numchars2delete = filenamelength - 256;
		
		logger.debug("Filename is "+filenamelength+" characters long.  Removing "+numchars2delete+" characters.");
		
		// Determine whether or not the filename has an extension that we want to preserve
		int indexofdot = filepath.lastIndexOf(".");
		int dotfromend = filenamelength - indexofdot;
		
		int indexofslash = filepath.lastIndexOf("/");
		logger.debug("index of final path separater "+indexofslash);
		
		if ((dotfromend == 4) || (dotfromend == 5))
		{
			hasExtension = true;
		}
		
		// Find whether or not we can delete enough characters to fall below the limit	
		if ((filenamelength - indexofslash - dotfromend) < numchars2delete)
		{
			// Just return the index number
			
			return Integer.toString(filenamelength_indx++);
		}

		// Otherwise, trim the appropriate number of characters

		if (hasExtension)
		{
				// Remove characters before the extension
				filepath.delete(indexofdot - numchars2delete - Integer.toString(filenamelength_indx++).length(), indexofdot);
				logger.debug("filename after removing chars "+filepath);
				filepath.insert(251, Integer.toString(filenamelength_indx).toCharArray());
				logger.debug("filename after inserting number "+filepath);
		}
		else
		{
			filepath.delete(filenamelength - numchars2delete - Integer.toString(filenamelength_indx++).length(), indexofdot);
			logger.debug("filename after removing chars "+filepath);
			filepath.insert(251, Integer.toString(filenamelength_indx).toCharArray());
			logger.debug("filename after inserting number "+filepath);
		}
		
		logger.debug("Filename is now "+filepath.length()+" characters long.");
		
		// Delete everything up to the final separator		
		filepath.delete(0, indexofslash+1);
		
		logger.debug("Returning filename "+filepath);
		
		return filepath.toString();

	}
	
	/**
	 * Replace characters declared illegal by Box.com with an underscore.  Return the resulting string.
	 * 
	 * @param filename
	 * @return
	 */
	
	public String replaceIllegalChars(String filename)
	{
		// Replace illegal characters
		String newfilename = filename.replaceAll("[\\\\/\":<>|*\\?]", "_");

		// Filenames cannot end with a "."
		if (newfilename.endsWith("."))
		{
			newfilename = filename.substring(0,filename.length()-1);
		}
		
		if (!newfilename.equals(filename))
		{
			logger.debug("Replaced illegal characters in file name "+filename);
			logger.debug("Resulting file name is: "+newfilename);
		}
		
		return newfilename;		
	}
	
	/**
	 * Retrieve the Mimetype for a file to be uploaded to Google Drive.  The Mimetype must be specified.
	 * 
	 * @param src The file to be uploaded
	 * @return The Mimetype of the file
	 * @throws IOException
	 */
	
	public static String getMimeType(File src) throws IOException
	{
		String type = null;
		
		  String fileUrl = src.getAbsolutePath();

		  logger.debug("Checking MIME-TYPE of "+fileUrl);
		  
		  // Use MimeUtil to determine mime-type by file extension
		  //MimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.MagicMimeMimeDetector");
		  //MimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.ExtensionMimeDetector");
	        
	      Collection<?> mimeTypes = MimeUtil.getMimeTypes(src);
		  
	      /* This is another way to determine mime-type by using native Java classes
	      Iterator iterator = mimeTypes.iterator();
	      
	      while (iterator.hasNext())
	      {
	    	MimeType mimetype = (MimeType) iterator.next();  
	    	type = mimetype.toString();

	    	System.out.println("MimeType = "+type);
	      }
	      */
	      
	      type = mimeTypes.iterator().next().toString();
		  
	      /*
	      FileNameMap fileNameMap = URLConnection.getFileNameMap();
	      String type = fileNameMap.getContentTypeFor(fileUrl);
		  */
		  
	      logger.debug("Matched mime-types: "+mimeTypes);
	      logger.debug("Returning mime-type: "+type);
	      return type;
	}
	
	/**
	 * Obtain a connection to the MySQL database.
	 * 
	 * @return Database Connection
	 * @throws SQLException
	 * @throws Exception
	 */

	public static Connection getMySQLConnection()
	     throws SQLException
	{
		try
		{
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		}
		catch (Exception e)
		{
			logger.error("Unable to load JDBC driver!", e);
			
			throw new SQLException("Unable to load JDBC driver!");
		}

		Connection xy_conn =
			DriverManager.getConnection("jdbc:mysql://"+dbserver+"/"+dbname,
					dbuser, dbpasswd);

		return xy_conn;
	}
	
}
