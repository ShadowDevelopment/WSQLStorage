package com.herocraftonline.heroes.storage.managers.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class Database {
	private static final String MYSQL_URL_TEMPLATE = "jdbc:mysql://%s/%s?user=%s&password=%s";
	private static final String SQLITE_URL_TEMPLATE = "jdbc:sqlite:%s";
	private final String connectionUrl;
	private final String driver;
	private Connection connection;
	
	public Database(Plugin databaseFactory, DatabaseConfigBuilder builder) {
		this.driver = builder.getDriver();
		if(builder.getFile() != null) {
			//Use SQLITE
			this.connectionUrl = String.format(SQLITE_URL_TEMPLATE, builder.getFile());
		} else {
			//Use MYSQL
			this.connectionUrl = String.format(MYSQL_URL_TEMPLATE, builder.getUrl(), builder.getDatabase(), builder.getUser(), builder.getPassword());
		}
	}
	
	/**
	 * Register a table.
	 * 
	 * @param table Table to register.
	 */
	public void registerTable(Table table) {
		try {
			PreparedStatement ps = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + table.getName() + table.getUsage());
			ps.executeUpdate();
		} catch (SQLException e) {
			log("Failed to register table", e);
		}
	}
	
	/**
	 * Delete a table
	 * 
	 * @param table Name of table
	 */
	public void deleteTable(String table) {
		try {
			PreparedStatement ps = connection.prepareStatement("DROP TABLE " + table);
			ps.executeUpdate();
		} catch (SQLException e) {
			log("Failed to delete table", e);
		}
	}
	
	/**
	 * Rename a table
	 * 
	 * @param oldName Old name
	 * @param newName New name
	 */
	public void renameTable(String oldName, String newName) {
		try {
			PreparedStatement ps = connection.prepareStatement("RENAME " + oldName + " TO " + newName);
			ps.executeUpdate();
		} catch (SQLException e) {
			log("Failed to rename table", e);
		}
	}
	
	/**
	 * Check if a certain table exists
	 * 
	 * @param table Name of table
	 * @return Table exists?
	 */
	public boolean tableExists(String table) {
		try {
			DatabaseMetaData metadata = connection.getMetaData();
			ResultSet result = metadata.getTables(null, null, table, null);
			return result.next();
		} catch (SQLException e) {
			log("Failed to check database", e);
			return false;
		}
	}
	
	/**
	 * Check if a certain column in a table exists
	 * 
	 * @param table Name of table
	 * @param column Name of column
	 * @return Column exists?
	 */
	public boolean columnExists(String table, String column) {
		try {
			DatabaseMetaData metadata = connection.getMetaData();
			ResultSet result = metadata.getColumns(null, null, table, column);
			return result.next();
		} catch (SQLException e) {
			log("Failed to check database", e);
			return false;
		}
	}
	
	public void set(Object one, Object two, String column, Table table) {
		try {
			PreparedStatement ps = connection.prepareStatement("INSERT INTO " + table.getName() + "( uuid," + column + " )" + " VALUES( " + one + "," + two + " );");
			ps.executeUpdate();
		} catch (SQLException e) {
			log("Failed to insert data to database", e);
		}
	}
	
	/**
	 * Insert new data to the database
	 * 
	 * @param table Table to insert data in
	 * @param values Values to insert
	 */
	public void set(Table table, Object... values) {
		try {
			String valueCount = "";
			for(int i = 0; i < values.length; i++) {
				valueCount += "?";
				if(i < (values.length - 1))
					valueCount += ",";
			}
			
			PreparedStatement ps = connection.prepareStatement("INSERT INTO " + table.getName() + table.getValues() + " VALUES(" + valueCount + ");");
			for(int i = 0; i < values.length; i++) {
				ps.setObject(i + 1, values[i]);
			}
			
			ps.executeUpdate();
		} catch (SQLException e) {
			log("Failed to insert data to database", e);
		}
	}
	
	/**
	 * Get a value from a table.
	 * 
	 * @param table Table to get value from
	 * @param index Column to search with.
	 * @param toGet Value of column to look for
	 * @param value The value to search with.
	 * @return Value of found, NULL if not.
	 */
	public HashMap<String, Object> get(Table table, String skill, String level, String name, Object nameValue) {
		try {
			PreparedStatement ps = connection.prepareStatement("SELECT " + skill + "," + level + " FROM " + table.getName() + " WHERE " + name + "=?;");
			ps.setObject(1, nameValue);
			ResultSet result = ps.executeQuery();
			HashMap<String, Object> skills = new HashMap<>();
			while (result.next()) {
				skills.put(result.getString(skill), result.getObject(level));
//				for (Map.Entry<String, Integer> addedSkill : skills.entrySet()) {
//					Heroes.log(Level.INFO, addedSkill.toString());
//				}
			}
			return skills;
		} catch (SQLException e) {
			log("Failed to get data from database", e);
		}
		
		return null;
	}
	
	public List<String> getTableNames(Table table) {
		try {
			PreparedStatement ps = connection.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_name=?;");
			ps.setObject(1, table.getName());
			ResultSet result = ps.executeQuery();
			List<String> list = new ArrayList<String>();
			if (result.next()) {
				list.add(result.getString("column_name"));
			}
			return list;
		} catch (SQLException e) {
			log("Failed to get data from database", e);
		}
		return null;
	}

	/**
	 * Get a value from a table.
	 * 
	 * @param table Table to get value from
	 * @param index Column to search with.
	 * @param toGet Value of column to look for
	 * @param value The value to search with.
	 * @return Value of found, NULL if not.
	 */
	public Object get(Table table, String index, String toGet, Object value) {
		try {
			PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table.getName() + " WHERE " + index + "=?;");
			ps.setObject(1, value);
			ResultSet result = ps.executeQuery();
			if(result.next()) {
				return result.getObject(toGet);
			}
		} catch (SQLException e) {
			log("Failed to get data from database", e);
		}
		
		return null;
	}

	/**
	 * Check if a value exists in the database
	 * 
	 * @param table Table to check from.
	 * @param index Index of the value
	 * @param value Value of the index
	 * @return TRUE if found, FALSE if not
	 */
	public boolean contains(Table table, String index, Object value) {
		try {
			PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table.getName() + " WHERE " + index + "=?;");
			ps.setObject(1, value);
			ResultSet result = ps.executeQuery();
			return result.next();
		} catch (SQLException e) {
			log("Faield to check database", e);
			return false;
		}
	}
	
	/**
	 * Check if a value exists in the database
	 * 
	 * @param table Table to check from.
	 * @param index Index of the value
	 * @param value Value of the index
	 * @return TRUE if found, FALSE if not
	 */
	public boolean contains(Table table, String index, String name, Object value, Object nameValue) {
		try {
			PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table.getName() + " WHERE " + index + "=? AND " + name + "=?;");
			ps.setObject(1, value);
			ps.setObject(2, nameValue);
			ResultSet result = ps.executeQuery();
			return result.next();
		} catch (SQLException e) {
			log("Faield to check database", e);
			return false;
		}
	}

	/**
	 * Update a value in the database
	 * 
	 * @param table Table to update
	 * @param index Index to search with
	 * @param toUpdate Index to update
	 * @param indexValue Value to swarch with.
	 * @param updateValue New value to update.
	 */
	public void update(Table table, String index, String toUpdate, Object indexValue, Object updateValue) {
		try {
			PreparedStatement ps = connection.prepareStatement("UPDATE " + table.getName() + " SET " + toUpdate + "=? WHERE " + index + "=?;");
			ps.setObject(1, updateValue);
			ps.setObject(2, indexValue);
			ps.executeUpdate();
		} catch (SQLException e) {
			log("Failed to update database", e);
		}
	}
	
	/**
	 * Update a value in the database
	 * 
	 * @param table Table to update
	 * @param index Index to search with
	 * @param toUpdate Index to update
	 * @param name Index of player name
	 * @param indexValue Value to swarch with.
	 * @param updateValue New value to update.
	 * @param nameValue Value of player
	 */
	public void update(Table table, String index, String toUpdate, String name, Object indexValue, Object updateValue, Object nameValue) {
		try {
			PreparedStatement ps = connection.prepareStatement("UPDATE " + table.getName() + " SET " + toUpdate + "=? WHERE " + index + "=? AND " + name + "=?;");
			ps.setObject(1, updateValue);
			ps.setObject(2, indexValue);
			ps.setObject(3, nameValue);
			ps.executeUpdate();
		} catch (SQLException e) {
			log("Failed to update database", e);
		}
	}

	/**
	 * Remove data from the database
	 * 
	 * @param table Table to remove from.
	 * @param index Index to search with.
	 * @param value Value to search with.
	 */
	public void remove(Table table, String index, Object value) {
		try {
			PreparedStatement ps = connection.prepareStatement("DELTE FROM " + table.getName() + " WHERE " + index + "=?;");
			ps.setObject(1, value);
			ps.executeUpdate();
		} catch (SQLException e) {
			log("Failed to remove from database", e);
		}
	}
	
	/**
	 * Returns the database connection.
	 * 
	 * @return Database connection.
	 */
	public Connection getConnection() {
		return connection;
	}
	
	/**
	 * Connects to database.
	 * 
	 * @throws SQLException
	 */
	public void connect() throws SQLException {
		try {
			Class.forName(driver);
		} catch(Throwable t) {
			log("Failed to load database driver", t);
			return;
		}
		
		this.connection = DriverManager.getConnection(connectionUrl);
	}
	
	/**
	 * Checks if the database is currently connected.
	 * 
	 * @return Is database connected?
	 */
	public boolean isConnected() {
		return connection != null;
	}
	
	/**
	 * Closes the database.
	 */
	public void close() {
		try {
			if(isConnected()) {
				connection.close();
				this.connection = null;
			}
		} catch(SQLException e) {
			log("Failed to close database", e);
		}
	}
	
	private void log(String message, Throwable t) {
		Bukkit.getLogger().log(Level.SEVERE, message, t);
	}
}