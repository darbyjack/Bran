package br.net.brjdevs.steven.bran.core.client;

import br.net.brjdevs.steven.bran.core.audio.AudioUtils;
import br.net.brjdevs.steven.bran.core.audio.BranMusicManager;
import br.net.brjdevs.steven.bran.core.audio.GuildMusicManager;
import br.net.brjdevs.steven.bran.core.command.CommandManager;
import br.net.brjdevs.steven.bran.core.currency.ProfileData;
import br.net.brjdevs.steven.bran.core.data.BranDataManager;
import br.net.brjdevs.steven.bran.core.data.Config;
import br.net.brjdevs.steven.bran.core.managers.MessageCache;
import br.net.brjdevs.steven.bran.core.managers.Messenger;
import br.net.brjdevs.steven.bran.core.managers.TaskManager;
import br.net.brjdevs.steven.bran.core.utils.Session;
import br.net.brjdevs.steven.bran.core.utils.Utils;
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
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Bran {
	
	public static Color COLOR = Color.decode("#388BDF");
	private static Bran instance;
	private static JedisPool jedisPool = new JedisPool("localhost", 6379);
	private static SimpleLog LOG = SimpleLog.getLog("BotContainer");
	public File workingDir;
	private TaskManager taskManager;
	private CommandManager commandManager;
	private BranMusicManager playerManager;
	private BranDataManager discordBotData;
    private Client[] shards;
    private DiscordLog discordLog;
	private int totalShards;
	private AtomicLongArray lastEvents;
	private long ownerId;
	private int ownerShardId;
	private Session session;
	private Messenger messenger;
	
	public Bran() throws LoginException, InterruptedException, RateLimitedException {
		instance = this;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth("stup2016");
        }
        this.discordBotData = new BranDataManager();
		this.ownerId = 0;
		this.ownerShardId = 0;
		this.workingDir = new File(System.getProperty("user.dir") + "/data/");
		if (!workingDir.exists() && !workingDir.mkdirs())
			throw new NullPointerException("Could not create config.json");
		this.totalShards = getRecommendedShards();
		this.lastEvents = new AtomicLongArray(totalShards);
        this.shards = new Client[totalShards];
        initShards();
		getOwner();
		this.playerManager = new BranMusicManager();
		this.commandManager = new CommandManager();
		this.discordLog = new DiscordLog();
		this.session = new Session();
		this.messenger = new Messenger();
		this.taskManager = new TaskManager();
        new MessageCache();
    }
	
	public static JedisPool getJedisPool() {
		return jedisPool;
	}
	
	public static Bran getInstance() {
		return instance;
	}
    
    public Client[] getShards() {
        return shards;
	}
	
	public BranDataManager getDataManager() {
		return discordBotData;
	}
	
	public int getShardId(JDA jda) {
		if (jda.getShardInfo() == null) return 0;
		return jda.getShardInfo().getShardId();
	}
    
    public Client getShard(JDA jda) {
        return getShards()[getShardId(jda)];
	}
    
    public ProfileData getProfile(User user) {
        return discordBotData.getData().get().getUserData(user).getProfileData();
    }
	
	public int getTotalShards() {
		return totalShards;
	}
    
    public Client[] getOnlineShards() {
        return Arrays.stream(shards).filter(s -> s.getJDA().getStatus() == Status.CONNECTED).toArray(Client[]::new);
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
	
	public Config getConfig() {
        return getDataManager().getConfig().get();
    }
	
	public List<Guild> getGuilds() {
		return Arrays.stream(shards).map(bot -> bot.getJDA().getGuilds()).flatMap(List::stream).collect(Collectors.toList());
	}
	
	public List<User> getUsers() {
		return Arrays.stream(shards).map(bot -> bot.getJDA().getUsers()).flatMap(List::stream).collect(Collectors.toList());
	}
	
	public User getUserById(String id) {
		return Arrays.stream(shards).map(shard -> shard.getJDA().getUserById(id)).filter(Objects::nonNull).findFirst().orElse(null);
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
	
	public TaskManager getTaskManager() {
		return taskManager;
	}
    
    public synchronized boolean reboot(Client shard) {
        try {
			Map<Long, ImmutablePair<Long, GuildMusicManager>> shardPlayers = new HashMap<>();
			Map<Long, GuildMusicManager> copy = new HashMap<>(playerManager.getMusicManagers());
			copy.forEach((guildId, musicManager) -> {
				Guild guild = shard.getJDA().getGuildById(String.valueOf(guildId));
				if (guild != null) {
					if (guild.getAudioManager().isConnected() || guild.getAudioManager().isAttemptingToConnect()) {
						shardPlayers.put(guildId, new ImmutablePair<>(Long.parseLong(guild.getAudioManager().getConnectedChannel().getId()), musicManager));
						musicManager.getTrackScheduler().setPaused(true);
						playerManager.unregister(guildId);
					}
				}
			});
			shard.getJDA().shutdown(false);
			Utils.sleep(5000);
			shard.restartJDA();
			shardPlayers.forEach((id, pair) -> {
				VoiceChannel channel = shard.getJDA().getVoiceChannelById(String.valueOf(pair.left));
				GuildMusicManager musicManager = pair.right;
				if (channel == null) return;
				channel.getGuild().getAudioManager().setSendingHandler(musicManager.getSendHandler());
				AudioUtils.connect(channel, musicManager.getTrackScheduler().getCurrentTrack().getContext());
				playerManager.getMusicManagers().put(id, musicManager);
				musicManager.getTrackScheduler().setPaused(false);
				
			});
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public CommandManager getCommandManager() {
		return commandManager;
	}
	
	public BranMusicManager getMusicManager() {
		return playerManager;
	}
	
	private int getRecommendedShards() {
		try {
			HttpResponse<JsonNode> shards = Unirest.get("https://discordapp.com/api/gateway/bot")
					.header("Authorization", "Bot " + getConfig().botToken)
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
            shards[i] = new Client(i, totalShards);
            LOG.info("Finished shard #" + i);
			Thread.sleep(5_000L);
		}
        for (Client shard : shards) {
            setLastEvent(shard.getId(), System.currentTimeMillis());
		}
	}
	
	public User getOwner() {
		if (ownerId != 0) return getShards()[ownerShardId].getJDA().getUserById(String.valueOf(ownerId));
        for (Client shard : shards) {
            User u = shard.getJDA().getUserById(getConfig().ownerId);
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
			try {
				if (musicManager.getTrackScheduler().getCurrentTrack() == null) return;
				TextChannel channel = musicManager.getTrackScheduler().getCurrentTrack().getContext();
				if (channel != null && channel.canTalk())
                    channel.sendMessage("Hey, I'm sorry to bother you but I need to restart. I'll be back bigger, stronger and better.").complete();
            } catch (Exception ignored) {
			}
		});
        
        getDataManager().getPolls().update();
        getDataManager().getData().update();
        getDataManager().getConfig().update();
        getDataManager().getHangmanWords().update();
        
        Stream.of(shards).forEach(Client::shutdown);
        
        System.exit(exitCode);
	}
}