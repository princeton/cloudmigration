package edu.princeton.cloudmigration;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.net.URL;
import java.net.URLEncoder;
import javax.net.ssl.HttpsURLConnection;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.text.SimpleDateFormat;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

import org.apache.log4j.Logger;

public class XythosDownloader {
	
//  Logger for logging messages to a file
	private static Logger logger = Logger.getLogger(XythosDownloader.class);
	
	private static final String webspaceServerName;
	private static final String zipFolder;
	private static final String unZipFolder;
	private static final String webspacebasicauthcreds;
	
	private String netID;
	private String zipFileName;
	
	// Load configuration values
	static 
	{	
		ResourceBundle rb = ResourceBundle.getBundle(DataMigrator.CONFIG_FILE_NAME);

		webspaceServerName = rb.getString("webspaceServerName");
		zipFolder = rb.getString("zipFolder");
		unZipFolder = rb.getString("unZipFolder");
		webspacebasicauthcreds = rb.getString("webspacebasicauthcreds");
	}
		

	/**
	 * Constructor
	 * 
	 * @param netID -- The netID for the user whose data will be migrated
	 */

	public XythosDownloader(String netID)
	{	
		this.netID = netID;

		this.zipFileName = zipFolder + this.netID + ".zip";
	}
	
	/**
	 * Download user data
	 * 
	 * @returns Folder containing the downloaded data
	 */
	public File download()
	{
		//TODO:  What if user account doesn't even exist in WebSpace?
		
		getZip();
		unZip();
		File unzippedfolder = renameUnzippedFolder();
		
		return unzippedfolder;
	}


	/**
	 * Retrieve a Zip file with the contents of the user's home folder in WebSpace.
	 */

	private void getZip()
	{
		//TODO:  Before downloading, should we look to see how much data the user has?  Abort if too large?

		try 
		{
			logger.info("{"+this.netID+"} Downloading Zip file from Xythos ...");

			//logger.debug("{"+this.netID+"} Setting up authentication ...");
			//Authenticator.setDefault (new MyAuthenticator());

			// Construct POST data
			String data = URLEncoder.encode("numFiles", "UTF-8") + "=" + URLEncoder.encode("1", "UTF-8");
			data += "&" + URLEncoder.encode("subaction", "UTF-8") + "=" + URLEncoder.encode("zip", "UTF-8");
			data += "&" + URLEncoder.encode("a1", "UTF-8") + "=" + URLEncoder.encode("download", "UTF-8");
			data += "&" + URLEncoder.encode("file0", "UTF-8") + "=" + URLEncoder.encode("/users/"+this.netID, "UTF-8");
			data += "&" + URLEncoder.encode("workingdir", "UTF-8") + "=" + URLEncoder.encode("/users", "UTF-8");


			// Send data
			logger.debug("{"+this.netID+"} Obtaining HTTP connection ...");
			URL url = new URL("https://"+webspaceServerName+"/xythoswfs/webui/users.zip");
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setRequestProperty("Accept-Encoding","gzip,deflate,sdch");
			conn.setRequestProperty("Accept","*/*");
			conn.setRequestProperty("Authorization", "Basic "+webspacebasicauthcreds);
			conn.setDoOutput(true);

			logger.debug("{"+this.netID+"} POSTing data: " + data);
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(data);
			wr.flush();

			logger.debug("{"+this.netID+"} HTTP Response Code: " + conn.getResponseCode());
			//logger.info("Cipher Suite : " + conn.getCipherSuite());

			// Write the response to a file
			logger.debug("{"+this.netID+"} Writing Zip file: "+this.zipFileName);
			File f = new File(this.zipFileName);
			f.createNewFile();
			BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(f));
			BufferedInputStream is = new BufferedInputStream(conn.getInputStream(), 4*1024);

			byte buf[]=new byte[4 * 1024];
			int len;
			while((len=is.read(buf))>0)
			{
				//logger.info("Writing "+len+" bytes of data ...");
				os.write(buf,0,len);
			}


			wr.close();
			is.close();
			os.flush();
			os.close();

			logger.info("{"+this.netID+"} Finished downloading Zip file!");

		} catch (Exception e) 
		{
			logger.error("{"+this.netID+"} Exception encountered while downloading Zipfile", e);
		}
	}
	

	/**
	 * Unzip the retrieved Zip file
	 */

	private void unZip ()
	{
		int BUFFER = 2*2048;

		ZipFile zipfile = null;

		logger.info("{"+this.netID+"} Extracting Zip file ...");

		try {
			BufferedOutputStream dest = null;
			BufferedInputStream is = null;
			ZipEntry entry;
			zipfile = new ZipFile(this.zipFileName);
			Enumeration e = zipfile.entries();

			while(e.hasMoreElements()) 
			{
				entry = (ZipEntry) e.nextElement();
				//logger.debug("Extracting: " +entry);
				if (entry.isDirectory())
				{
					//logger.debug("====== Making dir ====== "+entry.getName());
					File dir = new File(unZipFolder+entry.getName());
					if (! dir.exists())
					{
						dir.mkdirs();
					}
				}
				else
				{
					try
					{
						is = new BufferedInputStream(zipfile.getInputStream(entry));
						int count;
						byte data[] = new byte[BUFFER];
						// If the directory containing this file does not already exist, then make it
						File f = new File (unZipFolder+entry.getName());
						File dir = f.getParentFile();
						if (! dir.exists())
						{
							dir.mkdirs();
						}

						FileOutputStream fos = new FileOutputStream(unZipFolder+entry.getName());
						dest = new BufferedOutputStream(fos, BUFFER);
						while ((count = is.read(data, 0, BUFFER)) != -1) 
						{
							dest.write(data, 0, count);
						}
						dest.flush();
						dest.close();
						is.close();
					}
					catch (Exception ee)
					{
						//TODO:  If the error message contains "No space left on device", then abort.

						// Otherwise, log error
						logger.error("{"+this.netID+"} Problems extracting file: "+entry.getName(), ee);
					}
				}

			}

			zipfile.close();

			// Now that the data is unzipped, delete the zipfile

			File zfile = new File(this.zipFileName);
			zfile.delete();

		} catch(Exception e) {
			logger.error("{"+this.netID+"} Exception encountered while unzipping file!", e);
		}
		finally
		{
			if (zipfile != null) 
			{
				try { zipfile.close(); } catch (Exception e) { /* Do nothing */ }
			}
		}

		logger.info("{"+this.netID+"} Finished extracting Zip file.");
	}

	/**
	 * After the user's folder has been unzipped, append a date-time string to the end of the name
	 */
	private File renameUnzippedFolder()
	{
		String origname = unZipFolder+"/"+this.netID;

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HHmmss");

		String newname = origname+"_"+sdf.format(new java.util.Date());

		File origfile = new File(origname);
		File newfile = new File(newname);

		logger.info("Renaming "+origfile.getPath()+" to "+newfile.getPath());

		origfile.renameTo(newfile);

		return newfile;
	}
	
	
}

