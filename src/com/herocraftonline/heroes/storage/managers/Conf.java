package com.herocraftonline.heroes.storage.managers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;

import com.herocraftonline.heroes.Heroes;

public final class Conf {
	private boolean SQL_Enabled = false;
	private String SQL_Host = "127.0.0.1";
	private int SQL_Port = 3306;
	private String SQL_User = "user";
	private String SQL_Password = "password";
	private String SQL_Database = "database";
	private long SQL_Save = 6000L;

	private static File configFile;
	private static YamlConfiguration config;
	
	public Conf(SQLStorage storage) {
		configFile = new File(storage.plugin.getDataFolder(), "database.yml");
		try {
			checkConfig();
		} catch (Exception e) {
			e.printStackTrace();
		}
		loadConfig();
	}

	public void checkConfig() throws Exception {
		if (!configFile.exists()) {
			InputStream fis = Conf.class.getResourceAsStream("/resource/" + configFile.getName());
			FileOutputStream fos = new FileOutputStream(configFile);
			try {
				byte[] buf = new byte[1024];
				int i = 0;
				while ((i = fis.read(buf)) != -1) {
					fos.write(buf, 0, i);
				}
			} catch (Exception e) {
				throw e;
			} finally {
				if (fis != null) {
					fis.close();
				}
				if (fos != null) {
					fos.close();
				}
			}
		}
	}
	protected void loadConfig() {
		try {
			config = YamlConfiguration.loadConfiguration(configFile);
			SQL_Enabled = config.getBoolean("MySQL.Enabled");
			SQL_Host = config.getString("MySQL.Host");
			SQL_Port = config.getInt("MySQL.Port");
			SQL_User = config.getString("MySQL.User");
			SQL_Password = config.getString("MySQL.Password");
			SQL_Database = config.getString("MySQL.Database");
			SQL_Save = config.getLong("Save-Interval");
		} catch (Exception e) {
			Heroes.log(Level.INFO, "[Heroes] Failed to load database.yml");
			if (Heroes.properties.debug)
				e.printStackTrace();
		}
	}
	public static YamlConfiguration getFileConfig() { return config; }
	
	public boolean isEnabled() { return SQL_Enabled; }
	public String getHost() { return SQL_Host; }
	public int getPort() { return SQL_Port; }
	public String getUser() { return SQL_User; }
	public String getPassword() { return SQL_Password; }
	public String getDatabase() { return SQL_Database; }
	public long getSaveInterval() { return SQL_Save; }
}
