package br.net.brjdevs.steven.bran;

import br.net.brjdevs.steven.bran.core.client.Bran;
import br.net.brjdevs.steven.bran.core.client.Client;
import br.net.brjdevs.steven.bran.core.client.DiscordLog.Level;
import br.net.brjdevs.steven.bran.core.utils.Hastebin;
import br.net.brjdevs.steven.bran.core.utils.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Arrays;

public class Main {
	
	public static Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
	public static Bran bran;
	
	public static void main(String[] args) {
		try {
			bran = new Bran();
			Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
				throwable.printStackTrace();
				String url = Hastebin.post(Utils.getStackTrace(throwable));
				bran.getDiscordLog().logToDiscord("Uncaught exception in Thread " + thread.getName(), "An unexpected `" + throwable.getClass().getSimpleName() + "` occurred.\nMessage: " + throwable.getMessage() + "\nStackTrace: " + url, Level.FATAL);
			});
			
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
                    Arrays.stream(bran.getShards()).forEach(Client::shutdown);
                } catch (Exception e) {
				}
			}));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}