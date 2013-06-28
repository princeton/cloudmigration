/**
 * Copyright © 2013 - Trustees of Princeton University
 * 
 * @author Mark Ratliff
 * 
 */

package edu.princeton.cloudmigration;

import java.io.File;
import java.util.ResourceBundle;
import java.util.ArrayList;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

import org.apache.log4j.Logger;

/**
 *  
 * This application can be used for migrating data in user's Xythos home folders into Google Drive or Box.com home folders
 * 
 */
public class DataMigrator implements Runnable {

	public static final String config_file_name = "datamigrator";
	
	//  Logger for logging messages to a file
	private static Logger logger = Logger.getLogger(DataMigrator.class);

	private static final String cloudprovider;
	private static final String uploadMethod;

	private static final int numthreads;

	private String netID;
	private String zipFileName;
	

	// Load configuration values
	static 
	{	
		ResourceBundle rb = ResourceBundle.getBundle(config_file_name);
		
		uploadMethod = rb.getString("uploadMethod");
		cloudprovider = rb.getString("cloudprovider");
		numthreads = Integer.parseInt(rb.getString("numthreads"));	
	}


	/**
	 * This method obtains the list of accounts to be migrated from the MySQL database
	 * and then schedule migration jobs in a WorkQueue.
	 * 
	 * @param args
	 */
	public static void main(String args[])
	{
		// Define a work queue while defining the number of threads that should run simultaneously
		WorkQueue wq = new WorkQueue(numthreads);


		// Get the list of accounts that are to be migrated
		String[] netidlist = DataMigrator.getAccountsToMigrate();
		
//Testing
//String[] netidlist = {"jingfu"};

		logger.info("Migrating "+netidlist.length+" accounts");

		// Migrate accounts in the list
		for (int i=0; i<netidlist.length; i++)
		{			
			DataMigrator migrator = new DataMigrator(netidlist[i]);
			wq.execute(migrator);
		}

		wq.finishWork();
	}

	/**
	 * Constructor
	 * 
	 * @param netID -- The netID for the user whose data will be migrated
	 */

	public DataMigrator(String netID)
	{	
		this.netID = netID;
	}

	/**
	 * This method is called when the job is executed from the WorkQueue
	 * 
	 */

	public void run()
	{
		logger.info("Begin migration for netID: "+this.netID);
	
		File folder2upload = null;

//Testing
//folder2upload = new File("/Users/ratliff/tmp/WebSpace_Data/ratliff_2013-03-23-191458");	
//folder2upload = new File("/Users/ratliff/tmp/WebSpace_Data/non-iso-latin-chars/");	
		folder2upload = new File("/Users/ratliff/tmp/WebSpace_Data/cmok_2013-06-27-134304");
		
		/*
		try
		{
			// Download data from source system (Xythos in this case)
			XythosDownloader xd = new XythosDownloader(this.netID);

			folder2upload = xd.download();
		}
		catch (NoSuchXythosUserException nsxu)
		{
			logger.error("User ["+this.netID+"] cannot be found in Xythos!");
			logger.error("Aborting migration!");
			//TODO:  record status in database
			
			return;
		}
		catch (XythosMaxSizeException xfmse)
		{
			logger.error("Xythos folder is too large to download!");
			logger.error("Aborting migration!");
			//TODO:  record status in database
			
			return;
		}
		catch (SQLException sqle)
		{
			logger.error("Unable to retrieve user information from Xythos database!", sqle);
			logger.error("Aborting migration!");
			//TODO:  record status in database
			
			return;
		}
*/
//System.exit(0);
		
		// If target is Box.com
		if (cloudprovider.equals("Box.com"))
		{
			if (uploadMethod.equals("FTP"))
			{
				BoxFTPUploader uploader = new BoxFTPUploader(this.netID);

				uploader.ftpUploadFiles(folder2upload);
			}
			else if (uploadMethod.equals("REST"))
			{
				BoxRESTUploader uploader = new BoxRESTUploader();

				uploader.restUploadFiles(folder2upload);
			}
			else
			{
				logger.fatal("uploadMethod must be set to either FTP or REST!");
				logger.fatal("Aborting!!");
				System.exit(-1);
			}
		}
		
		// If target is Google Drive
		else if (cloudprovider.equals("GoogleDrive"))
		{
			try 
			{
				//GoogleDriveUploader uploader = new GoogleDriveUploader(this.netID);
				GoogleDriveUploader uploader = new GoogleDriveUploader("csgsun8");

				uploader.restUploadFiles(folder2upload, null);
			}
			catch (GoogleQuotaException gqe)
			{
				logger.error("Not enough free space in Google Drive!");
			}
			catch (SQLException sqle)
			{
				logger.error("Trouble connecting to MySQL database", sqle);
			}
		}
		else
		{
			logger.fatal("cloudprovider must be set to either Box.com or GoogleDrive!");
			logger.fatal("Aborting!!");
			System.exit(-1);
		}

		recordStatus();

		logger.info("Finished migration for netID: "+this.netID);
	}

	/**
	 * Get the list of netIDs that should be migrated
	 */

	private static String[] getAccountsToMigrate()
	{
		logger.info("Retrieving list of accounts to migrate ...");
		Connection dbconn = null;
		ArrayList<String> netid_list = new ArrayList<String>();
		String[] netid_array = {};
		
		try
		{
			dbconn = Utilities.getMySQLConnection();

			Statement stmt = dbconn.createStatement();
			ResultSet rs = stmt.executeQuery("select netid from migration_queue where status = 'migrate'");

			while (rs.next())
			{
				netid_list.add(rs.getString("netid"));
			}

		}
		catch (Exception e)
		{
			logger.error("Cannot connect to database server", e);
		}
		finally
		{
			if (dbconn != null)
			{
				try
				{
					dbconn.close ();
				}
				catch (Exception e) { }
			}
		}

		if (netid_list.size() > 0)
		{
			logger.info("Found "+netid_list.size()+" accounts to migrate.");
			netid_array = netid_list.toArray(netid_array);
		}

		return netid_array;
	}



	/**
	 * Record the resulting status of attempted migration in the MySQL database
	 * 
	 */
	private void recordStatus()
	{
		Connection dbconn = null;

		try
		{
			dbconn = Utilities.getMySQLConnection();

			Statement stmt = dbconn.createStatement();
			stmt.execute("update migration_queue set completiondate=NOW(), status='completed' where netid='"+this.netID+"'");
		}
		catch (Exception e)
		{
			logger.error("Cannot connect to database server", e);
		}
		finally
		{
			if (dbconn != null)
			{
				try
				{
					dbconn.close ();
				}
				catch (Exception e) { }
			}
		}

	}

}
