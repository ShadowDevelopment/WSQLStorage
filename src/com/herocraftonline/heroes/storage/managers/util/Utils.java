package com.herocraftonline.heroes.storage.managers.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.herocraftonline.heroes.Heroes;
import com.herocraftonline.heroes.characters.Hero;

public class Utils {
	public static String getSuppressedSkills(Hero hero) {
		StringBuilder builder = new StringBuilder();
		if (hero.getSuppressedSkills() != null) {
			for (String hClass : hero.getSuppressedSkills()) {
				builder.append(hClass.replace(".yml", "") + " BOOLEAN,");
			}
			builder.deleteCharAt(builder.length() - 1);
		} else {
			builder.append("null");
		}
		return builder.toString();
	}
	
	public static String getHCIntegers() {
		StringBuilder builder = new StringBuilder();
		for (String hClass : getHeroClasses()) {
				builder.append(hClass.replace(".yml", "") + " REAL DEFAULT `0`,");
		}
		builder.deleteCharAt(builder.length() - 1);
		return builder.toString();
	}
	
	public static String getHCBooleans() {
		StringBuilder builder = new StringBuilder();
		for (String hClass : getHeroClasses()) {
				builder.append(hClass.replace(".yml", "") + " BOOLEAN,");
		}
		builder.deleteCharAt(builder.length() - 1);
		return builder.toString();
	}
	
	public static List<String> getHeroClasses() {
		List<String> results = new ArrayList<String>();
		File[] files = new File(Heroes.getInstance().getDataFolder() + File.separator + "classes").listFiles();
		for (File file : files) {
			if (file.isFile()) {
				results.add(file.getName().replace(".yml", ""));
			}
		}
		return results;
	}
}
