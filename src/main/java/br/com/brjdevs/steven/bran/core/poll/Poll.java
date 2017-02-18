package br.com.brjdevs.steven.bran.core.poll;

import br.com.brjdevs.steven.bran.Client;
import br.com.brjdevs.steven.bran.ClientShard;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Poll {
	private static final List<Poll> polls = new ArrayList<>();
	private final String pollName;
	private final String creatorId;
	private final LinkedList<Option> options;
	private final String channelId;
	private final String guildId;
	@Getter
	@Setter
	private int shardId;
	
	public Poll(String pollName, Member creator, LinkedList<Option> options, TextChannel channel, Client client) {
		this.pollName = pollName;
		this.creatorId = creator.getUser().getId();
		this.options = options;
		this.channelId = channel.getId();
		this.guildId = channel.getGuild().getId();
		setShardId(client.getShardId(channel.getJDA()));
		polls.add(this);
	}
	
	public static List<Poll> getRunningPolls() {
		return polls;
	}
	
	public static Poll getPoll(TextChannel channel) {
		return polls.stream().filter(poll -> poll.getChannelId().equals(channel.getId())).findFirst().orElse(null);
	}
	
	public ClientShard getShard(Client client) {
		return client.getShards()[shardId];
	}
	
	public JDA getJDA(Client client) {
		return getShard(client).getJDA();
	}
	
	public String getGuildId() {
		return guildId;
	}
	
	public Guild getGuild(Client client) {
		return getJDA(client).getGuildById(getGuildId());
	}

	public String getPollName() {
		return pollName.isEmpty() ? "\u00AD" : pollName;
	}

	public String getCreatorId() {
		return creatorId;
	}
	
	public User getCreator(Client client) {
		return getJDA(client).getUserById(getCreatorId());
	}

	public LinkedList<Option> getOptions() {
		return options;
	}
	
	public String getChannelId() {
		return channelId;
	}
	
	public TextChannel getChannel(Client client) {
		return getJDA(client).getTextChannelById(getChannelId());
	}

	public Option getOption(int optionIndex) {
		return options.stream().filter(option -> option.getIndex() == optionIndex).findFirst().orElse(null);
	}
	
	/**
	 * Adds or remove a vote to the {@code Poll}
	 * @return false if vote is removed; true if the vote is added
	 * @throws NullPointerException if the index is invalid.
	 */
	public boolean vote(String userId, int optionIndex) {
		
		Option option = getOption(optionIndex);
		if (option.getVotes().contains(userId)) {
			option.getVotes().remove(userId);
			return false;
		}
		Option option1 = getOption(userId);
		if (option1 != null) {
			option1.getVotes().remove(userId);
		}
		option.getVotes().add(userId);
		return true;
	}
	
	public List<Option> getLeadership() {
		return options.stream().filter(option -> option.getVotes().size() == Collections.max(options.stream().map(o -> o.getVotes().size()).collect(Collectors.toList())) && option.getVotes().size() != 0).collect(Collectors.toList());
	}
	
	public void remove() {
		polls.remove(this);
	}
	
	public Option getOption(String userId) {
		return getOptions().stream().filter(op -> op.getVotes().contains(userId)).findFirst().orElse(null);
	}
}
