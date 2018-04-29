package com;

import java.io.IOException;
import java.sql.*;

public class DataBaseConnection {
	
	private Connection conn = null;

	public Connection getConn() {
		if (conn != null) return conn; //singleton pattern
		else try {
			Class.forName("com.mysql.jdbc.Driver");
			String connectionString = "jdbc:mysql:URL?useSSL=false";
			String username = "root";
			String password = "root";
			conn = DriverManager.getConnection(connectionString, username, password); 
		} catch (Exception e) {
			System.out.println("Database error: in getConn function!" + e.getMessage());
		}
		return conn;
	}

	public void close() {
		try {
			this.conn.close();
		} catch (Exception e) {
			System.out.println("Database error: in close function!" + e.getMessage());
		}
	}

	public ResultSet executeQuery(String sql) {
		ResultSet rs = null;
		try {
			System.out.println(sql);
			rs = null;
			Connection conn = this.getConn();
			Statement stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
		} catch (SQLException ex) {
			System.out.println("Database error: in executeQuery function!" + ex.getMessage());
			ex.printStackTrace();
		}
		return rs;
	}

	public boolean executeUpdate(String strSQL) {
		try {
			//System.out.println(strSQL);
			Connection conn = this.getConn();
			Statement stmt = conn.createStatement();
			stmt.executeUpdate(strSQL);
		
		} catch (SQLException ex) {
			System.err.println("Database error: in executeUpdate function!" + ex.getMessage());
		}
		return true;
	}
	
	//Test MySQL connection
	public static void main(String args[]) throws IOException, SQLException{
		DataBaseConnection dbc = new DataBaseConnection();
        // Create and execute a SELECT SQL statement.  
        String selectSql = "SELECT * from dialog_contents LIMIT 10;";  
        ResultSet resultSet = dbc.executeQuery(selectSql);
        while(resultSet.next()){
        	System.out.println(resultSet.getInt(1) + "\t" + resultSet.getInt(2) + "\t" 
        			+ resultSet.getInt(3) + "\t" + resultSet.getString(4));
        }
	}
}
