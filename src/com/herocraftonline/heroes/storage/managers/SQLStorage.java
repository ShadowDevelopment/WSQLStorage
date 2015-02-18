package com.herocraftonline.heroes.storage.managers;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.herocraftonline.heroes.Heroes;
import com.herocraftonline.heroes.characters.Hero;
import com.herocraftonline.heroes.characters.classes.HeroClass;
import com.herocraftonline.heroes.storage.Storage;
import com.herocraftonline.heroes.storage.managers.util.Database;
import com.herocraftonline.heroes.storage.managers.util.DatabaseConfigBuilder;
import com.herocraftonline.heroes.storage.managers.util.Table;
import com.herocraftonline.heroes.storage.managers.util.Utils;
import com.herocraftonline.heroes.util.Properties;

public final class SQLStorage extends Storage {
	//TODO preUUID version support
	@SuppressWarnings("unused")
	private String nickOrUUID = "name";
	
	private Map<String, Hero> toSave = new ConcurrentHashMap<String, Hero>();
	
	private final long SAVE_INTERVAL;
	private final BukkitTask id;
	private final Conf config;
	private final Database database;
	
	protected final Heroes plugin;

	public static final Table HERO_EXPERIENCE = new Table("HERO_EXPERIENCE", "uuid varchar(36) NOT NULL," + Utils.getHCIntegers());
	public static final Table HERO_MASTERIES = new Table("HERO_MASTERIES", "uuid varchar(36) NOT NULL," + Utils.getHCBooleans());
	public static final Table SKILL_SETTINGS = new Table("SKILL_SETTINGS", "uuid varchar(36) NOT NULL,skill VARCHAR(16),setting VARCHAR(32)");
	public static final Table SKILL_COOLDOWNS = new Table("SKILL_COOLDOWNS", "uuid varchar(36) NOT NULL,skill VARCHAR(16),cooldown INT");
	public static final Table SKILL_BINDS = new Table("SKILL_BINDS", "uuid varchar(36) NOT NULL,material VARCHAR(16),skill VARCHAR(16)");
	public static final Table HERO_SETTINGS = new Table("HERO_SETTINGS", "uuid varchar(36) NOT NULL,class VARCHAR(16),verbose VARCHAR(8),"
			+ "mana INT,secondaryclass VARCHAR(16),suppressed VARCHAR(64)");
	
	private static final List<Table> tables = new ArrayList<Table>();

	public SQLStorage(Heroes plugin) {
		super(plugin, "SQLStorage");
		this.plugin = plugin;
		this.config = new Conf(this);
		this.SAVE_INTERVAL = config.getSaveInterval();

		File sqliteFile = new File(plugin.getDataFolder(), "database.db");
		DatabaseConfigBuilder conf = new DatabaseConfigBuilder(Conf
				.getFileConfig()
				.getConfigurationSection("MySQL"), 
				sqliteFile);
		database = getDatabase(conf);
		
		try {
			database.connect();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		database.registerTable(HERO_EXPERIENCE);
		database.registerTable(HERO_MASTERIES);
		database.registerTable(SKILL_SETTINGS);
		database.registerTable(SKILL_COOLDOWNS);
		database.registerTable(SKILL_BINDS);
		database.registerTable(HERO_SETTINGS);
		
		tables.add(HERO_EXPERIENCE);
		tables.add(HERO_MASTERIES);
		tables.add(SKILL_SETTINGS);
		tables.add(SKILL_COOLDOWNS);
		tables.add(SKILL_BINDS);
		tables.add(HERO_SETTINGS);
		
		id = Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new HeroSaveThread(), SAVE_INTERVAL, SAVE_INTERVAL);
	}

	@Override
	public Hero loadHero(Player player) {
		checkConnection();
		if (toSave.containsKey(player.getName())) {
			Hero fromToSave = toSave.get(player.getName());
			fromToSave.setPlayer(player);
			return fromToSave;
		} else {
			HeroClass playerClass = loadClass(player);
			if (playerClass == null) {
				Heroes.log(Level.INFO, "Invalid class found for " + player.getName() + ". Resetting player.");
				return createNewHero(player);
			} else {
				HeroClass secondClass = loadSecondaryClass(player);
				Hero playerHero = new Hero(plugin, player, playerClass, secondClass);
				loadCooldowns(playerHero);
				loadExperience(playerHero);
				loadBinds(playerHero);
				loadSkillSettings(playerHero);
				
				Object mana = database.get(HERO_SETTINGS, "uuid", "mana", player.getUniqueId());
				Object verbose = database.get(HERO_SETTINGS, "uuid", "verbose", player.getUniqueId());
				playerHero.setMana((mana == null) ? 0 : Integer.valueOf(String.valueOf(mana)));
				playerHero.setVerbose((verbose == null) ? false : ((String) verbose == "true") ? true : false);
				
				Object obj = database.get(HERO_SETTINGS, "uuid", "suppressed", player.getUniqueId());
				if (!(obj != null && obj.equals("null"))) {
					List<String> skills = new ArrayList<String>();
					for (String skill : (String.valueOf(obj)).split(",")) {
						skills.add(skill);
					}
					playerHero.setSuppressedSkills(skills);
				}
				return playerHero;
			}
		}
	}

	@Override
	public void saveHero(Hero hero, boolean now) {
		checkConnection();
		if (database.contains(HERO_SETTINGS, "uuid", hero.getPlayer().getUniqueId())) {
			database.update(HERO_SETTINGS, "uuid", "class", hero.getPlayer().getUniqueId(), hero.getHeroClass().toString());
			database.update(HERO_SETTINGS, "uuid", "verbose", hero.getPlayer().getUniqueId(), hero.isVerbose());
			database.update(HERO_SETTINGS, "uuid", "mana", hero.getPlayer().getUniqueId(), hero.getMana());
			if (hero.getSecondClass() != null) {
				database.update(HERO_SETTINGS, "uuid", "secondary-class", hero.getPlayer().getUniqueId(), hero.getSecondClass().toString());
			}
			database.update(HERO_SETTINGS, "uuid", "suppressed", hero.getPlayer().getUniqueId(), Utils.getSuppressedSkills(hero));
		} else {
			database.set(HERO_SETTINGS, hero.getPlayer().getUniqueId(), hero.getHeroClass().toString(), (hero.isVerbose()) ? "true" : "false", hero.getMana(), 
				(hero.getSecondClass() == null) ? "none" : hero.getSecondClass().toString(), Utils.getSuppressedSkills(hero));
		}

		saveSkillSettings(hero);
		saveCooldowns(hero);
		saveExperience(hero);
		saveBinds(hero);
	}


	private void loadBinds(Hero hero) {
		HashMap<String, Object> map = database.get(SKILL_BINDS, "skill", "material", "uuid", hero.getPlayer().getUniqueId());
		if (map != null) {
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				if (entry.getKey() == null)
					continue;
				try {
					Material item = Material.valueOf(entry.getKey());
					String bind = (String) entry.getValue();
					if (bind != null) {
						hero.bind(item, bind.split(" "));
					}
				} catch (IllegalArgumentException e) {
					Heroes.log(Level.WARNING, new StringBuilder().append(entry.getKey()).append(" isn't a valid Item to bind a Skill to.").toString());
				}
			}
		}
	}

	private HeroClass loadClass(Player player) {
		HeroClass playerClass = null;
		HeroClass defaultClass = plugin.getClassManager().getDefaultClass();
		String hClass = String.valueOf(database.get(HERO_SETTINGS, "uuid", "class", player.getUniqueId()));
		if (hClass != null) {
			playerClass = plugin.getClassManager().getClass(hClass);
			if (playerClass == null) {
				playerClass = defaultClass;
			} else if(!playerClass.isPrimary()) {
				playerClass = defaultClass;
			}
		} else {
			playerClass = defaultClass;
		}
		return playerClass;
	}

	private HeroClass loadSecondaryClass(Player player) {
		HeroClass playerClass = null;
		String hClass = String.valueOf(database.get(HERO_SETTINGS, "uuid", "secondaryclass", player.getUniqueId()));
		if (hClass != null) {
			playerClass = plugin.getClassManager().getClass(hClass);
			if (playerClass == null || !playerClass.isSecondary()) {
				Heroes.log(Level.SEVERE, "Invalid secondary class was defined for " + player.getName() + " resetting to nothing!");
				return null;
			}
		}
		return playerClass;
	}

	private void loadCooldowns(Hero hero) {
		HashMap<String, Object> map = database.get(SKILL_COOLDOWNS, "skill", "cooldown", "uuid", hero.getPlayer().getUniqueId());
		if (map != null) {
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				if (entry.getKey() == null)
					continue;
				try {
					String skill = entry.getKey();
					long cooldown = (long) entry.getValue();
					if ((hero.hasAccessToSkill(skill)) && (cooldown > System.currentTimeMillis())) {
						hero.setCooldown(skill, cooldown);
					}
				} catch (IllegalArgumentException e) {
					Heroes.log(Level.WARNING, new StringBuilder().append(entry.getKey()).append(" isn't a valid Item to bind a Skill to.").toString());
				}
			}
		}
	}

	private void loadExperience(Hero hero) {
		for (String expColumn : Utils.getHeroClasses()) {
			if (expColumn.equals("uuid")) {
				continue;
			}
			Object obj = database.get(HERO_EXPERIENCE, expColumn, "uuid", hero.getPlayer().getUniqueId());
			double exp = (obj == null) ? 0.0D : (double) obj;
			HeroClass heroClass = plugin.getClassManager().getClass(expColumn);
			if ((heroClass != null) && (hero.getExperience(heroClass) == 0.0D)) {
				if (exp > Properties.maxExp) {
					exp = Properties.maxExp;
				}
				hero.setExperience(heroClass, exp);
			}
		}
	}

	private void loadSkillSettings(Hero hero) {
		HashMap<String, Object> expMap = database.get(SKILL_SETTINGS, "skill", "setting", "uuid", hero.getPlayer().getUniqueId());
		for (Map.Entry<String, Object> entry : expMap.entrySet()) {
			for (String string : String.valueOf(entry.getValue()).split("|")) {
				String key = null;
				for (String set : string.split("#")) {
					if (key == null) {
						key = set;
					} else {
						hero.setSkillSetting(entry.getKey(), key, set);
						key = null;
					}
				}
			}
		}
	}

	private void saveBinds(Hero hero) {
		for (Map.Entry<Material, String[]> entry : hero.getBinds().entrySet()) {
			if (database.contains(SKILL_BINDS, "uuid", "skill", hero.getPlayer().getUniqueId(), entry.getKey())) {
				database.update(SKILL_BINDS, "skill", "material", "uuid", entry.getKey(), entry.getValue(), hero.getPlayer().getUniqueId());
			} else {
				database.set(SKILL_BINDS, hero.getPlayer().getUniqueId(), entry.getKey(), entry.getValue());
			}
		}
	}

	private void saveCooldowns(Hero hero) {
		for (Map.Entry<String, Long> entry : hero.getCooldowns().entrySet()) {
			if (entry.getValue() > System.currentTimeMillis()) {
				if (database.contains(SKILL_COOLDOWNS, "uuid", "skill", hero.getPlayer().getUniqueId(), entry.getKey())) {
					database.update(SKILL_COOLDOWNS, "skill", "cooldown", "uuid", entry.getKey(), entry.getValue(), hero.getPlayer().getUniqueId());
				} else {
					database.set(SKILL_COOLDOWNS, hero.getPlayer().getUniqueId(), entry.getKey(), entry.getValue());
				}
				if (Heroes.properties.debug) {
					Heroes.debugLog(Level.INFO, new StringBuilder().append(hero.getName()).append(": - ").append(entry.getKey()).append(" @ ").append(entry.getValue()).toString());
				}
			}
		}
	}

	private void saveExperience(Hero hero) {
		for (Map.Entry<String, Double> entry : hero.getExperienceMap().entrySet()) {
			if (database.contains(HERO_EXPERIENCE, "uuid",hero.getPlayer().getUniqueId())) {
				database.update(HERO_EXPERIENCE, "uuid", entry.getKey(), hero.getPlayer().getUniqueId(), entry.getValue());
			} else {
				database.set(hero.getPlayer().getUniqueId().toString(), entry.getValue(), entry.getKey(), HERO_EXPERIENCE);
			}
		}
	}

	private void saveSkillSettings(Hero hero) {
		for (Map.Entry<String, ConfigurationSection> entry : hero.getSkillSettings().entrySet()) {
			String settings = "";
			Iterator<String> i = entry.getValue().getKeys(true).iterator();
			while (i.hasNext()) {
				if (i.hasNext()) {
					settings += i.next() + "#" + entry.getValue().get(i.next()) + "|";
				} else {
					settings += i.next() + "#" + entry.getValue().get(i.next());
				}
			}
			if (database.contains(SKILL_SETTINGS, "uuid", "skill", hero.getPlayer().getUniqueId(), entry.getKey())) {
				database.update(SKILL_SETTINGS, "skill", "setting", "uuid", entry.getKey(), settings, hero.getPlayer().getUniqueId());
			} else {
				database.set(SKILL_SETTINGS, hero.getPlayer().getUniqueId(), entry.getKey(), settings);
			}
		}
	}
	
	private void checkConnection() {
		if (!database.isConnected()) {
			try {
				database.connect();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static Database getDatabase(DatabaseConfigBuilder builder) {
		return new Database(Heroes.getInstance(), builder);
	}

	@Override
	public void shutdown() {
		id.cancel();
		for (Hero hero : plugin.getCharacterManager().getHeroes()) {
			saveHero(hero, true);
		}
	}

	protected class HeroSaveThread implements Runnable {
		@Override
		public void run() {
			if (!toSave.isEmpty()) {
				for (Map.Entry<String, Hero> toSave : toSave.entrySet()) {
					saveHero(toSave.getValue(), true);
				}
			}
		}
	}
}
