/**
 * 
 */
package edu.princeton.cloudmigration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.sql.SQLException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.drive.Drive;
//import com.google.api.services.drive.Drive.Files.List;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;


/**
 * @author ratliff
 *
 */
public class GoogleDriveUploader {
	
	private static Logger logger = Logger.getLogger(GoogleDriveUploader.class);
	
	private static final String gdrive_clientid;
	private static final String gdrive_clientsecret;
	private String accessToken;
	private String refreshToken;
	private static final int retries;
	private static final int MAX_FILE_SIZE;
	private File folder2upload;
	
	GoogleTokens gtokens;
	
	Utilities utils;
	
	// Load configuration values
	static 
	{	
		ResourceBundle rb = ResourceBundle.getBundle(DataMigrator.CONFIG_FILE_NAME);
		
		gdrive_clientid = rb.getString("gdrive_clientid");
		gdrive_clientsecret = rb.getString("gdrive_clientsecret");
		MAX_FILE_SIZE = Integer.parseInt(rb.getString("maxfilesize"));
		retries = Integer.parseInt(rb.getString("boxretries"));
	}
	
	/**
	 * Constructor method
	 * 
	 * @param netid
	 * @throws SQLException
	 */
	public GoogleDriveUploader(String netid) throws SQLException
	{
		gtokens = new GoogleTokens(netid);

		this.accessToken = gtokens.getAccessToken();
		this.refreshToken = gtokens.getRefreshToken();
		
		this.utils = new Utilities();
	}
	
	/**
	 * Begin the upload process from the root folder
	 * 
	 * @param folder2upload
	 * @param targetdir_id
	 * @throws SQLException
	 */
	public void restUploadFiles(File folder2upload, String targetdir_id) throws SQLException
	{
		logger.info("Uploading folder "+folder2upload.getAbsolutePath()+" to Google Drive ...");
		
		this.folder2upload = folder2upload;

		HttpTransport httpTransport = new NetHttpTransport();
		
	    JsonFactory jsonFactory = new JacksonFactory();
	    GoogleCredential credential = new GoogleCredential.Builder()
	    .setClientSecrets(gdrive_clientid, gdrive_clientsecret)
	    .setJsonFactory(jsonFactory).setTransport(httpTransport).build()
	    .setRefreshToken(refreshToken).setAccessToken(accessToken);
	    
	    // If a new access token was generated, save it to the database
	    if (!credential.getAccessToken().equals(accessToken))
	    {
	    	logger.debug("FIRST: Received new Google access token.  Updating database ...");
	    	gtokens.updateAccessToken(credential.getAccessToken());
	    }
	    
	    logger.debug("Access token expires in: "+credential.getExpiresInSeconds()+" seconds.");
	    
	    //Create a new authorized API client
	    Drive service = new Drive.Builder(httpTransport, jsonFactory, credential).build();
	    
		try
		{
// Test upload of an invidivual file
//java.io.File problemfile = new java.io.File("/Users/ratliff/tmp/yaya/ratliff/Testing Folder/Report/Testing.xapp/Page 2.xapp/Page 2 level 2.xapp/wiki.css");
//restUpload(problemfile, "0B_mg3zK26fS3T2pxeEVlM0Fiak0", service);		

			restUpload(folder2upload, targetdir_id, service);
		}
		catch (GoogleJsonResponseException gjre)
		{
			logger.error("Error while uploading to Google.", gjre);
			logger.error(gjre.getDetails());
			System.exit(-1);
		}
		catch (IOException ioe)
		{
			logger.error("Error while uploading to Google.", ioe);
			System.exit(-1);
		}

		
	    if (!credential.getAccessToken().equals(accessToken))
	    {
	    	logger.debug("SECOND: Received new Google access token.  Updating database ...");
	    	gtokens.updateAccessToken(credential.getAccessToken());
	    }

	}

	/**
	 * Upload an individual file or folder.  If it is a folder, then create and call self recursively on contents.
	 * 
	 * @param src
	 * @param parent_folder_id
	 * @param service
	 * @throws IOException
	 */
	private void restUpload(File src, String parent_folder_id, Drive service) throws IOException
	{
		int retrynum;
		Random randomGenerator = new Random();
		
		// If this is a directory, then create the directory and then call this method again for each folder/file within this directory
		if (src.isDirectory())
		{
			String dirname = utils.getFilename(src, folder2upload);
			//String dirname = src.getName();

		    // Create a folder
			com.google.api.services.drive.model.File folder = new com.google.api.services.drive.model.File();
		    folder.setTitle(dirname);
		    folder.setDescription("Migrated");
		    folder.setMimeType("application/vnd.google-apps.folder");
		    
		    // If there is a parent folder, then set it
		    if (parent_folder_id != null)
		    {
		    	folder.setParents(Arrays.asList(new ParentReference().setId(parent_folder_id)));
		    }
		    
		    retrynum = 0;
		    com.google.api.services.drive.model.File gfolder = null;
		    
		    while (retrynum <= this.retries)
		    {
				logger.debug("Creating directory: "+dirname);
				logger.debug("   Parent folder id: "+parent_folder_id);
				logger.debug("   Attempt number: "+(retrynum+1));
				
		    	try
		    	{
		    		gfolder = service.files().insert(folder).execute();
		    		break;
		    	}
		    	//catch (GoogleJsonResponseException gjre)
		    	catch (IOException gjre)
		    	{
		    		logger.error("Exception while uploading to Google.", gjre);
					//logger.error(gjre.getDetails());
					
					// If the directory was created, then do not retry
					gfolder = getFile("application/vnd.google-apps.folder", dirname, parent_folder_id, service);
					
					if (gfolder != null)
					{
						logger.debug("Looks like the directory was created, despite the exception that was thrown!");
						break;
					}
					
					logger.debug("Doesn't look like directory: "+dirname+" was created!  Will retry ...");
					
					retrynum += 1;
					
					if (retrynum > this.retries)
					{
						throw gjre;
					}
					
					// Take a brief nap before retrying
					try
					{
						Thread.sleep((1 << retrynum) * 1000 + randomGenerator.nextInt(1001));
					}
					catch (InterruptedException ie)
					{
						// Do Nothing
					}
		    	}
		    }
		    
    		// Now upload the contents of the directory
			String createdFolderId = gfolder.getId();
			for (File file : src.listFiles()) 
			{
				restUpload(file, createdFolderId, service);
			}
		}
		// This is not a directory, so it must be a file.
		
		// First check to see if the file is too large.  If so, then log error and skip.
		else if (src.length() > this.MAX_FILE_SIZE)
		{
			logger.error("Cannot upload file: "+src.getAbsolutePath());
			logger.error("     Reason: max file size exceeded.");
		}

		// Next check to see that file has data.  If it doesn't Google will refuse it
		else if (src.length() == 0)
		{
			logger.error("Cannot upload file: "+src.getAbsolutePath());
			logger.error("     Reason:  zero length file.");
		}		

		// Upload the file
		else
		{
			// Upload a file
			String filename = utils.getFilename(src, folder2upload);
			//String filename = src.getName();
			String mimetype = Utilities.getMimeType(src);
		    
		    //Insert a file  
			com.google.api.services.drive.model.File body = new com.google.api.services.drive.model.File();
		    body.setTitle(filename);
		    body.setDescription("Migrated");
		    body.setMimeType(mimetype);
		    body.setParents(Arrays.asList(new ParentReference().setId(parent_folder_id)));
		    
		    FileContent mediaContent = new FileContent("text/plain", src);

		    retrynum = 0;
		    
		    while (retrynum <= this.retries)
		    {
		    	logger.debug("Uploading file: "+filename);
				logger.debug("   Parent folder id: "+parent_folder_id);
				logger.debug("   Attempt number: "+(retrynum+1));
				
		    	try
		    	{
		    		com.google.api.services.drive.model.File file = service.files().insert(body, mediaContent).execute();
		    		break;
		    	}
		    	//catch (GoogleJsonResponseException gjre)
		    	catch (IOException gjre)
		    	{
		    		logger.error("Exception while uploading to Google.", gjre);
					//logger.error(gjre.getDetails());
					
					// If the file was created, then do not retry
					if (getFile(mimetype, filename, parent_folder_id, service) != null)
					{
						logger.debug("Looks like the file was created, despite the exception that was thrown!");
						break;
					}
					
					logger.debug("Doesn't look like file: "+filename+" was created!  Will retry ...");
					
					retrynum += 1;
					
					if (retrynum > this.retries)
					{
						throw gjre;
					}
					
					// Take a brief nap before retrying
					try
					{
						Thread.sleep((1 << retrynum) * 1000 + randomGenerator.nextInt(1001));
					}
					catch (InterruptedException ie)
					{
						// Do Nothing
					}
		    	}
		    }

		}
	}
	
	
	private com.google.api.services.drive.model.File getFile(String mime_type, String file_name, String parent_folder_id, Drive service) 
		throws IOException
	{		
		String query = "trashed = false and title = '"+file_name+"' and '"+parent_folder_id+"' in parents";
		
		if(mime_type.equals("application/vnd.google-apps.folder"))
		{
			query += "mimeType = 'application/vnd.google-apps.folder'";
		}
		
		logger.debug("Searching for file: "+file_name+" using query string: "+query);
		
		Drive.Files.List request = service.files().list().setQ(query);
		
		FileList files = null;
		
		try
		{
			files = request.execute();
		}
		catch (IOException ioe)
		{
			logger.error("Exception encountered when searching for file: "+file_name, ioe);
			return null;
		}
		
		List<com.google.api.services.drive.model.File> list = new ArrayList<com.google.api.services.drive.model.File>();
		list.addAll(files.getItems());
		
		if (list.size() == 0)
		{
			logger.debug("getFile() was unable to find file: "+file_name);
			return null;
		}
		
		logger.debug(list.size()+" files were found.  Returning the first one");
		
		Iterator<com.google.api.services.drive.model.File> iter = list.iterator();

		com.google.api.services.drive.model.File file = (com.google.api.services.drive.model.File) iter.next();
		
		return file;
	}

}
