package br.com.brjdevs.steven.bran.core.data.bot;

import br.com.brjdevs.steven.bran.Client;
import br.com.brjdevs.steven.bran.Main;
import br.com.brjdevs.steven.bran.core.data.bot.settings.Blacklist;
import br.com.brjdevs.steven.bran.core.data.bot.settings.Profile;
import net.dv8tion.jda.core.entities.User;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BotData {
	
	private final Map<String, List<String>> HangManWords = new HashMap<>();
	private final Blacklist Blacklist = new Blacklist();
	private final Map<String, Profile> profiles = new HashMap<>();
	
	public BotData(Client client) {
		if (!client.workingDir.exists())
			client.workingDir.mkdirs();
	}
	
	public Blacklist getBlacklist() {
		return Blacklist;
	}
	
	public Map<String, Profile> getProfiles() {
		return profiles;
	}
	
	public Profile getProfile(User user) {
		return getProfiles().computeIfAbsent(user.getId(), u -> new Profile(user));
	}
	
	public Map<String, List<String>> getHangManWords() {
		return HangManWords;
	}
	
	public void save(Client client) {
		try {
			File file = new File(client.workingDir, "botData.json");
			if (!file.exists()) assert file.createNewFile();
			BufferedWriter writer = new BufferedWriter(new FileWriter(file));
			writer.write(Main.GSON.toJson(this));
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
