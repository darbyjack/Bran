package br.com.brjdevs.steven.bran;

import br.com.brjdevs.steven.bran.core.audio.MusicManager;
import br.com.brjdevs.steven.bran.core.audio.MusicPlayerManager;
import br.com.brjdevs.steven.bran.core.audio.utils.AudioUtils;
import br.com.brjdevs.steven.bran.core.command.CommandManager;
import br.com.brjdevs.steven.bran.core.data.DataManager;
import br.com.brjdevs.steven.bran.core.data.bot.BotData;
import br.com.brjdevs.steven.bran.core.data.bot.Config;
import br.com.brjdevs.steven.bran.core.data.bot.settings.Profile;
import br.com.brjdevs.steven.bran.core.itemManager.ItemContainer;
import br.com.brjdevs.steven.bran.core.managers.Messenger;
import br.com.brjdevs.steven.bran.core.managers.TaskManager;
import br.com.brjdevs.steven.bran.core.managers.jenkins.Jenkins;
import br.com.brjdevs.steven.bran.core.poll.PollPersistence;
import br.com.brjdevs.steven.bran.core.utils.Session;
import br.com.brjdevs.steven.bran.core.utils.Util;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDA.Status;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.utils.SimpleLog;
import org.apache.commons.lang3.tuple.ImmutablePair;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BotContainer {
	
	private static SimpleLog LOG = SimpleLog.getLog("BotContainer");
	public Config config;
	public BotData data;
	public File workingDir;
	public PollPersistence pollPersistence;
	public TaskManager taskManager;
	public DataManager dataManager;
	public CommandManager commandManager;
	public MusicPlayerManager playerManager;
	public Jenkins jenkins;
	private Bot[] shards;
	private DiscordLog discordLog;
	private int totalShards;
	private AtomicLongArray lastEvents;
	private long ownerId;
	private int ownerShardId;
	private Session session;
	private Messenger messenger;
	
	public BotContainer() throws LoginException, InterruptedException, RateLimitedException {
		this.ownerId = 0;
		this.ownerShardId = 0;
		this.workingDir = new File(System.getProperty("user.dir") + "/data/");
		if (!workingDir.exists() && !workingDir.mkdirs())
			throw new NullPointerException("Could not create config.json");
		this.config = Config.newConfig(this);
		this.data = new BotData(this);
		this.totalShards = getRecommendedShards();
		this.lastEvents = new AtomicLongArray(totalShards);
		this.shards = new Bot[totalShards];
		initShards();
		getOwner();
		this.playerManager = new MusicPlayerManager(this);
		this.pollPersistence = new PollPersistence(this);
		this.dataManager = new DataManager(this);
		this.commandManager = new CommandManager(this);
		this.discordLog = new DiscordLog(this);
		this.session = new Session(this);
		this.messenger = new Messenger(this);
		this.taskManager = new TaskManager(this);
		this.jenkins = new Jenkins(this);
		ItemContainer.loadItems();
	}
	
	public Bot[] getShards() {
		return shards;
	}
	
	public int getShardId(JDA jda) {
		if (jda.getShardInfo() == null) return 0;
		return jda.getShardInfo().getShardId();
	}
	
	public Profile getProfile(User user) {
		return data.getProfile(user);
	}
	
	public Map<String, Profile> getProfiles() {
		return data.getProfiles();
	}
	
	public int getTotalShards() {
		return totalShards;
	}
	
	public Bot[] getOnlineShards() {
		return Arrays.stream(shards).filter(s -> s.getJDA().getStatus() == Status.CONNECTED).toArray(Bot[]::new);
	}
	
	public DiscordLog getDiscordLog() {
		return discordLog;
	}
	
	public void setLastEvent(int shardId, long time) {
		lastEvents.set(shardId, time);
	}
	
	public Session getSession() {
		return session;
	}
	
	public Messenger getMessenger() {
		return messenger;
	}
	
	public List<Guild> getGuilds() {
		return Arrays.stream(shards).map(bot -> bot.getJDA().getGuilds()).flatMap(List::stream).collect(Collectors.toList());
	}
	
	public List<User> getUsers() {
		return Arrays.stream(shards).map(bot -> bot.getJDA().getUsers()).flatMap(List::stream).collect(Collectors.toList());
	}
	
	public List<TextChannel> getTextChannels() {
		return Arrays.stream(shards).map(bot -> bot.getJDA().getTextChannels()).flatMap(List::stream).collect(Collectors.toList());
	}
	
	public List<VoiceChannel> getVoiceChannels() {
		return Arrays.stream(shards).map(bot -> bot.getJDA().getVoiceChannels()).flatMap(List::stream).collect(Collectors.toList());
	}
	
	public long getResponseTotal() {
		return Arrays.stream(shards).map(bot -> bot.getJDA().getResponseTotal()).mapToLong(Long::longValue).sum();
	}
	
	public AtomicLongArray getLastEvents() {
		return lastEvents;
	}
	
	public synchronized boolean reboot(Bot shard) {
		try {
			Map<Long, ImmutablePair<Long, MusicManager>> shardPlayers = new HashMap<>();
			Map<Long, MusicManager> copy = new HashMap<>(playerManager.getMusicManagers());
			copy.forEach((guildId, musicManager) -> {
				Guild guild = shard.getJDA().getGuildById(String.valueOf(guildId));
				if (guild != null) {
					if (guild.getAudioManager().isConnected() || guild.getAudioManager().isAttemptingToConnect()) {
						shardPlayers.put(guildId, new ImmutablePair<>(Long.parseLong(guild.getAudioManager().getConnectedChannel().getId()), musicManager));
						TextChannel channel = musicManager.getTrackScheduler().getCurrentTrack().getContext(shard.getJDA());
						if (channel != null && channel.canTalk())
							channel.sendMessage("I'm going to reboot this shard (#" + shard.getId() + "), I'll be right back...").queue();
						musicManager.getTrackScheduler().setPaused(true);
						playerManager.unregister(guildId);
					}
				}
			});
			shard.getJDA().shutdown(false);
			Util.sleep(5000);
			shard.restartJDA();
			shardPlayers.forEach((id, pair) -> {
				VoiceChannel channel = shard.getJDA().getVoiceChannelById(String.valueOf(pair.left));
				MusicManager musicManager = pair.right;
				if (channel == null) return;
				channel.getGuild().getAudioManager().setSendingHandler(musicManager.getSendHandler());
				AudioUtils.connect(channel, musicManager.getTrackScheduler().getCurrentTrack().getContext(shard.getJDA()), this);
				playerManager.getMusicManagers().put(id, musicManager);
				musicManager.getTrackScheduler().setPaused(false);
				TextChannel context = musicManager.getTrackScheduler().getCurrentTrack().getContext(shard.getJDA());
				if (context != null && context.canTalk())
					context.sendMessage("Rebooted Shard, resuming the player...").queue();
				
			});
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	private int getRecommendedShards() {
		try {
			HttpResponse<JsonNode> shards = Unirest.get("https://discordapp.com/api/gateway/bot")
					.header("Authorization", "Bot " + config.getToken())
					.header("Content-Type", "application/json")
					.asJson();
			return shards.getBody().getObject().getInt("shards");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 1;
	}
	
	private void initShards() throws LoginException, InterruptedException, RateLimitedException {
		for (int i = 0; i < shards.length; i++) {
			LOG.info("Starting shard #" + i + " of " + shards.length);
			shards[i] = new Bot(i, totalShards, this);
			LOG.info("Finished shard #" + i);
			Thread.sleep(5_000L);
		}
		for (Bot shard : shards) {
			setLastEvent(shard.getId(), System.currentTimeMillis());
		}
	}
	
	public User getOwner() {
		if (ownerId != 0) return getShards()[ownerShardId].getJDA().getUserById(String.valueOf(ownerId));
		for (Bot shard : shards) {
			User u = shard.getJDA().getUserById(config.getOwnerId());
			if (u != null) {
				ownerId = Long.parseLong(u.getId());
				break;
			}
		}
		if (ownerId == 0) LOG.fatal("Could not find Owner.");
		return getShards()[ownerShardId].getJDA().getUserById(String.valueOf(ownerId));
	}
	
	public int calcShardId(long discordGuildId) {
		return (int) ((discordGuildId >> 22) % totalShards);
	}
	
	public void shutdownAll(int exitCode) {
		
		playerManager.getMusicManagers().forEach((guildId, musicManager) -> {
			Bot shard = musicManager.getShard();
			TextChannel channel = musicManager.getTrackScheduler().getCurrentTrack().getContext(shard.getJDA());
			if (channel == null) return;
			channel.sendMessage("Hey, I'm sorry to bother you but I need to restart. I'll be back bigger, strong and better.").queue();
		});
		if (!pollPersistence.savePolls()) LOG.info("Could not complete PollPersistence savePolls.");
		
		dataManager.saveData();
		
		Stream.of(shards).forEach(Bot::shutdown);
		
		System.exit(exitCode);
	}
}
