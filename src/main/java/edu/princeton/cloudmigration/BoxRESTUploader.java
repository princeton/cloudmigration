/**
 * Copyright © 2013 - Trustees of Princeton University
 * 
 * @author Mark Ratliff
 * 
 */

package edu.princeton.cloudmigration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import cn.com.believer.songyuanframework.openapi.storage.box.BoxExternalAPI;
import cn.com.believer.songyuanframework.openapi.storage.box.factories.BoxRequestFactory;
import cn.com.believer.songyuanframework.openapi.storage.box.functions.CreateFolderRequest;
import cn.com.believer.songyuanframework.openapi.storage.box.functions.CreateFolderResponse;
import cn.com.believer.songyuanframework.openapi.storage.box.functions.UploadRequest;
import cn.com.believer.songyuanframework.openapi.storage.box.functions.UploadResponse;
import cn.com.believer.songyuanframework.openapi.storage.box.impl.simple.SimpleBoxImpl;
import cn.com.believer.songyuanframework.openapi.storage.box.objects.BoxException;

/**
 * @author ratliff
 *
 */
public class BoxRESTUploader {
	
	private static Logger logger = Logger.getLogger(BoxRESTUploader.class);
	
	private static String config_file_name = "boxcom";
	
	private static final String box_auth_token;
	private static final String box_api_key;
	private static final String boxtargetdirid;
	private static final int boxretries;
	private static final int MAX_FILE_SIZE;
	
	private File folder2upload;
	
	Utilities utils;
	
	// Load configuration values
	static 
	{	
		ResourceBundle rb = ResourceBundle.getBundle(config_file_name);

		box_api_key = rb.getString("boxapikey");
		box_auth_token = rb.getString("boxauthtoken");
		boxtargetdirid = rb.getString("boxtargetdirid");
		boxretries = Integer.parseInt(rb.getString("boxretries"));
		MAX_FILE_SIZE = Integer.parseInt(rb.getString("maxfilesize"));
	}
	
	public BoxRESTUploader()
	{		
		this.utils = new Utilities();
	}
	
	public void restUploadFiles(File folder2upload)
	{
		logger.info("Uploading folder "+folder2upload.getAbsolutePath()+" to Box.com via REST ...");
		
		this.folder2upload = folder2upload;

		// the global API interface
		BoxExternalAPI iBoxExternalAPI = new SimpleBoxImpl();

		try
		{
			restUpload(folder2upload, boxtargetdirid, iBoxExternalAPI);
		}
		catch (BoxException be)
		{
			logger.error("Box reported an error.", be);
		}
		catch (IOException ioe)
		{
			logger.error("Error while uploading via REST.", ioe);
		}

	}

	private void restUpload(File src, String parent_folder_id, BoxExternalAPI iBoxExternalAPI) throws BoxException, IOException
	{
		if (src.isDirectory())
		{
			String dirname = utils.getFilename(src, folder2upload);

			logger.debug("Creating directory: "+dirname);

			// create a folder
			CreateFolderRequest createFolderRequest = BoxRequestFactory.createCreateFolderRequest(this.box_api_key,
					this.box_auth_token, parent_folder_id, dirname, false);
			CreateFolderResponse createFolderResponse = iBoxExternalAPI.createFolder(createFolderRequest);
			String createfolder_status = createFolderResponse.getStatus();

			// If the folder was created, then upload contents, otherwise log an error
			if (createfolder_status.equals("create_ok"))
			{
				String createdFolderId = createFolderResponse.getFolder().getFolderId();

				for (File file : src.listFiles()) 
				{
					restUpload(file, createdFolderId, iBoxExternalAPI);
				}
			}
			else
			{
				logger.error("There was a problem creating folder "+src.getAbsolutePath()+"  Status = "+createfolder_status);
				logger.error("  Folder contents will not be uploaded!");
			}

		}
		// If the file is too large, then log error and skip
		else if (src.length() > this.MAX_FILE_SIZE)
		{
			logger.error("Max file size exceeded for file: "+src.getAbsolutePath());
		}
		else
		{
			// Upload a file
			String filename = utils.getFilename(src, folder2upload);

			logger.debug("Uploading file: "+filename);

			Map<String,File> fileMap = new HashMap<String,File>();
			fileMap.put(filename, src);
			UploadRequest uploadRequest = BoxRequestFactory.createUploadRequest(this.box_auth_token, true, parent_folder_id,
					fileMap);

			// Try to upload the file, retrying the number of times configured to do so if necessary
			for (int i=0; i<this.boxretries; i++)
			{
				try
				{
					UploadResponse uploadResponse = iBoxExternalAPI.upload(uploadRequest);

					String upload_status = uploadResponse.getStatus();
					if (!upload_status.equals("upload_ok"))
					{
						logger.error("Problems uploading file: "+src.getAbsolutePath());
					}
				}
				catch (BoxException be)
				{
					// If this is the final try, then throw the exception
					//TODO:  Should this exception be recorded in a list instead and then the code
					//       allowed to move on to the next file?
					if (i == this.boxretries)
					{
						throw be;
					}
				}
			}

			//UploadResponse uploadResponse = iBoxExternalAPI.upload(uploadRequest);
			//UploadResult uploadResult = (UploadResult) uploadResponse.getUploadResultList().get(0);
			//String uploadedFileId = uploadResult.getFile().getFileId();

			//logger.info("File ID of file uploaded via REST: "+uploadedFileId);
		}
	}

}
