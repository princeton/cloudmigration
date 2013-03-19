package edu.princeton.cloudmigration;

import java.sql.*;

import org.apache.log4j.Logger;

public class GoogleTokens {
	
	String accessToken;
	String refreshToken;
	String netid;
	
	private static Logger logger = Logger.getLogger(GoogleTokens.class);
	
	public GoogleTokens(String netid) throws SQLException
	{
		this.netid = netid;
		
		Connection dbconn = Utilities.getMySQLConnection();
		Statement stmt = dbconn.createStatement();
		ResultSet rs = stmt.executeQuery("select accesstoken, refreshtoken from google_auth where netid='"+
				netid+"'");

		while (rs.next())
		{
			accessToken = rs.getString("accesstoken");
			refreshToken = rs.getString("refreshtoken");
		}
		
		dbconn.close();
		
		logger.debug("["+netid+"] Google access token "+accessToken);
		logger.debug("["+netid+"] Google refresh token "+refreshToken);
	}
	
	/**
	 * @return the netid
	 */
	public String getNetID() {
		return netid;
	}

	/**
	 * @return the accessToken
	 */
	public String getAccessToken() {
		return accessToken;
	}

	/**
	 * @return the refreshToken
	 */
	public String getRefreshToken() {
		return refreshToken;
	}

	/**
	 * @param accessToken the accessToken to set
	 */
	public void updateAccessToken(String accessToken) throws SQLException {
		
		logger.debug("Updating Google accesstoken from "+this.accessToken+" to "+accessToken);
		this.accessToken = accessToken;
		
		Connection dbconn = Utilities.getMySQLConnection();
		Statement stmt = dbconn.createStatement();
		stmt.executeUpdate("update google_auth set accesstoken='"+accessToken+"' where netid='"+
				netid+"'");
		dbconn.close();
	}

	/**
	 * @param refreshToken the refreshToken to set
	 */
	/*
	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}
	*/
}
