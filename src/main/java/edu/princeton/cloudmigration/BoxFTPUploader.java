/**
 * 
 */
package edu.princeton.cloudmigration;

import java.io.File;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ResourceBundle;

import org.apache.commons.net.ftp.*;
import org.apache.commons.io.*;

import org.apache.log4j.Logger;

/**
 * @author ratliff
 *
 */
public class BoxFTPUploader {
	
	//Logger for logging messages to a file
	private static Logger logger = Logger.getLogger(BoxFTPUploader.class);
	
	private static final String boxuser;
	private static final String boxpasswd;
	private static final String uploadtargetFolder;
	private static final int MAX_FILE_SIZE;
	
	private String netID;
	
	// Load configuration values
	static 
	{	
		ResourceBundle rb = ResourceBundle.getBundle(DataMigrator.CONFIG_FILE_NAME);
		
		boxuser = rb.getString("boxuser");
		boxpasswd = rb.getString("boxpasswd");
		uploadtargetFolder = rb.getString("uploadtargetFolder");
		MAX_FILE_SIZE = Integer.parseInt(rb.getString("maxfilesize"));
	}
	
	public BoxFTPUploader(String netID)
	{
		this.netID = netID;
	}

	/**
	 * Upload contents of the user's home folder to Box.com
	 */

	public void ftpUploadFiles(File folder2upload)
	{
		int reply;

		logger.info("{"+this.netID+"} Uploading files to Box.com via FTP ...");

		FTPClient ftps = new FTPClient();

		try
		{
			logger.debug("{"+this.netID+"} Attempting to open connection ...");
			ftps.connect("ftp.box.com");

			// After connection attempt, you should check the reply code to verify
			// success.
			reply = ftps.getReplyCode();

			if(!FTPReply.isPositiveCompletion(reply)) {
				ftps.disconnect();
				System.err.println("FTP server refused connection.");
				System.exit(1);
			}

			logger.debug("{"+this.netID+"} Attempting login ...");
			ftps.login(boxuser, boxpasswd);
			ftps.enterLocalPassiveMode();

			ftps.changeWorkingDirectory(uploadtargetFolder);
			ftps.setFileType(FTPClient.BINARY_FILE_TYPE);
			ftps.setBufferSize(4*1024);

			ftpUpload(folder2upload, ftps);

		}
		catch (Exception e)
		{
			logger.error("{"+this.netID+"} Exception encountered while uploading files.", e);
		}
		finally
		{
			if (ftps != null)
			{
				logger.debug("{"+this.netID+"} Disconnecting ...");
				try
				{
					ftps.disconnect();
				}
				catch (IOException ioe)
				{

				}
			}
		}

		logger.info("{"+this.netID+"} Finished uploading files to Box.com!");
	}

	/**
	 * Helper method which does the actual file upload.  This method calls itself recursively in order
	 * to upload contents of subdirectories.
	 * 
	 * @param src
	 * @param ftp
	 * @throws IOException
	 */

	private void ftpUpload(File src, FTPClient ftp) throws IOException 
	{
		if (src.isDirectory()) {
			ftp.makeDirectory(src.getName());
			ftp.changeWorkingDirectory(src.getName());
			for (File file : src.listFiles()) 
			{
				ftpUpload(file, ftp);
			}
			ftp.changeToParentDirectory();
		}
		// If the file is too large, then log error and skip
		else if (src.length() > MAX_FILE_SIZE)
		{
			logger.error("Max file size exceeded: "+src.getAbsolutePath());
		}
		else 
		{
			BufferedInputStream srcStream = null;
			try 
			{
				//srcStream = src.toURI().toURL().openStream();
				srcStream = new BufferedInputStream(new FileInputStream(src), 4*1024);
				ftp.storeFile(src.getName(), srcStream);
			}
			finally 
			{
				IOUtils.closeQuietly(srcStream);
			}
		}
	}
}


