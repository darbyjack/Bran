package br.net.brjdevs.steven.bran.core.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mashape.unirest.http.Unirest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WeatherSearch {
	private static final String URL = "https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20weather.forecast%20where%20woeid%20in%20(select%20woeid%20from%20geo.places(1)%20where%20text%3D\"{query}\")&format=json&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys";
	public static JsonElement search(String query) {
		try {
			String url = URL.replace("{query}", URLEncoder.encode(query, "UTF-8"));
			FutureTask<Object> task = new FutureTask<>(() -> {
				try {
					return Unirest.get(url).asString().getBody();
				} catch (Exception e) {
					return e;
				}
			});
			task.run();
			Object obj;
			try {
				obj = task.get(10, TimeUnit.SECONDS);
			} catch (TimeoutException | ExecutionException | InterruptedException e) {
				throw new RuntimeException("Yahoo API didn't respond.");
			}
			if (obj instanceof Exception) {
				throw new RuntimeException((Exception) obj);
			}
			return new JsonParser().parse((String) obj)
					.getAsJsonObject().get("query").getAsJsonObject()
					.get("results");
		} catch (UnsupportedEncodingException ignored) {
		}
		return null;
	}
}
