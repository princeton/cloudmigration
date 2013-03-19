package edu.princeton.cloudmigration;

/**
 * Application for migrating data in user's WebSpace home folders into Google Drive or Box.com home folders
 * 
 * @author Mark Ratliff -- Princeton University
 * 
 */

import java.io.File;

import java.util.*;

import java.sql.*;

import org.apache.log4j.Logger;

public class DataMigrator implements Runnable {

	public static final String CONFIG_FILE_NAME = "MigrateWebSpaceData";
	
	//  Logger for logging messages to a file
	private static Logger logger = Logger.getLogger(DataMigrator.class);

	private static final String uploadMethod;

	private static final int numthreads;

	private static final String boxtargetdirid;

	private String netID;
	private String zipFileName;
	

	// Load configuration values
	static 
	{	
		ResourceBundle rb = ResourceBundle.getBundle(DataMigrator.CONFIG_FILE_NAME);

		boxtargetdirid = rb.getString("boxtargetdirid");
		
		numthreads = Integer.parseInt(rb.getString("numthreads"));
		uploadMethod = rb.getString("uploadMethod");
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
		
		// Download data from source system (Xythos in this case)
		XythosDownloader xd = new XythosDownloader(this.netID);
		
		//File unzippedfolder = xd.download();
		File unzippedfolder = new File("/Users/ratliff/tmp/yaya/ratliff");


		// Upload data to target system using specified protocol
		if (uploadMethod.equals("FTP"))
		{
			BoxFTPUploader uploader = new BoxFTPUploader(this.netID);
			
			uploader.ftpUploadFiles(unzippedfolder);
		}
		else if (uploadMethod.equals("REST"))
		{
			BoxRESTUploader uploader = new BoxRESTUploader();
			
			uploader.restUploadFiles(unzippedfolder, boxtargetdirid);
		}
		else if (uploadMethod.equals("GDRIVE"))
		{
			try 
			{
				GoogleDriveUploader uploader = new GoogleDriveUploader(this.netID);

				uploader.restUploadFiles(unzippedfolder, null);
			}
			catch (SQLException sqle)
			{
				logger.error("Trouble connecting to MySQL database", sqle);
			}
		}
		else
		{
			logger.fatal("uploadMethod must be set to either REST or FTP!");
			logger.fatal("Aborting!!");
			System.exit(-1);
		}


		//recordStatus();

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

		// For testing
		//String[] netid_array = {"ratliff"};

		
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
