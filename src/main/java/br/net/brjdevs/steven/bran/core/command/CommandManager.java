package br.net.brjdevs.steven.bran.core.command;

import br.net.brjdevs.steven.bran.core.client.Bran;
import br.net.brjdevs.steven.bran.core.command.enums.Category;
import br.net.brjdevs.steven.bran.core.command.interfaces.ICommand;
import br.net.brjdevs.steven.bran.core.command.interfaces.ITreeCommand;
import br.net.brjdevs.steven.bran.core.managers.Permissions;
import br.net.brjdevs.steven.bran.core.sql.SQLAction;
import br.net.brjdevs.steven.bran.core.sql.SQLDatabase;
import br.net.brjdevs.steven.bran.core.utils.Utils;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.utils.SimpleLog;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.awt.*;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CommandManager {
    
    private static Map<ICommand, String> help = new HashMap<>();
    private static List<ICommand> commands = new ArrayList<>();
    private final SimpleLog LOG = SimpleLog.getLog("Command Manager");
	
	public CommandManager() {
        try {
            SQLDatabase.getInstance().run((conn) -> {
                try {
                    conn.prepareStatement("CREATE TABLE IF NOT EXISTS CMDLOG (" +
                            "id int NOT NULL AUTO_INCREMENT, " +
                            "cmd text, " +
                            "arguments text, " +
                            "userid text, " +
                            "channelid text, " +
                            "guildid text, " +
                            "date bigint, " +
                            "successful int," +
                            "sessionId bigint, " +
                            "PRIMARY KEY (id)" +
                            ");").execute();
                    conn.prepareStatement("ALTER TABLE CMDLOG AUTO_INCREMENT=1;").execute();
                    
                } catch (SQLException exception) {
                    SQLAction.LOGGER.log(exception);
                }
            }).queue();
        } catch (SQLException e) {
            SQLAction.LOGGER.log(e);
        }
        new Thread(this::load).start();
    }
    
    public void addCommand(ICommand command) {
        if (command != null) {
            commands.add(command);
            generateHelp(command);
        }
    }
    
    public MessageEmbed getHelp(ICommand command, Member m) {
        return new EmbedBuilder().setColor(m != null && m.getColor() != null ? m.getColor() : Color.decode("#D68A38")).setDescription(help.get(command)).build();
    }
    
    public String generateHelp(ICommand command) {
        try {
            String desc = "";
            desc += command.getCategory().getEmoji() + " **| " + command.getCategory().getKey() + "**\n**Command:** " + command.getName() + "\n";
            desc += "**Description:** " + command.getDescription() + "\n";
            if (command.getArguments() != null) {
                desc += "**Arguments:** " + (command.getArguments().length != 0 ? (String.join(" ", Arrays.stream(command.getArguments()).map(arg -> (arg.isOptional() ? "<" : "[") + arg.getType().getSimpleName() + ": " + arg.getName() + (arg.isOptional() ? ">" : "]")).toArray(String[]::new))) : "No arguments required.") + '\n';
                desc += "            *Please note: do **NOT** include <> or []*\n";
            }
            desc += "**Required Permission(s):** " + String.join(", ", Permissions.toCollection(command.getRequiredPermission())) + "\n";
            if (command instanceof ITreeCommand) {
                desc += "**Parameters**:\n";
                Set<Category> categories = ((ITreeCommand) command).getSubCommands().stream().map(ICommand::getCategory).collect(Collectors.toSet());
                for (Category category : categories) {
                    List<ICommand> commands = ((ITreeCommand) command).getSubCommands().stream().filter(cmd -> cmd.getCategory() == category).collect(Collectors.toList());
                    if (commands.isEmpty()) continue;
                    desc += category.getEmoji() + " **| " + category.getKey() + "**\n";
                    for (ICommand cmd : commands)
                        desc += "          **" + cmd.getAliases()[0] + "** " + (cmd.getArguments() != null ? (String.join(" ", Arrays.stream(cmd.getArguments()).map(arg -> (arg.isOptional() ? "<" : "[") + arg.getType().getSimpleName() + ": " + arg.getName() + (arg.isOptional() ? ">" : "]")).toArray(String[]::new))) : "") + " - " + (cmd instanceof ITreeCommand ? "Use `" + cmd.getHelp() + "` to get help on this command!" : cmd.getDescription()) + "\n";
                    desc += '\n';
                }
            }
            if (command.getExample() != null)
                desc += "**Example:** " + command.getExample();
            help.put(command, desc);
            return desc;
        } catch (Exception e) {
            return null;
        }
    }
	
	public List<ICommand> getCommands() {
		return commands;
    }
	
	public List<ICommand> getCommands(Category category) {
		return getCommands().stream().filter(cmd -> cmd.getCategory() == category).collect(Collectors.toList());
    }
	
	public ICommand getCommand(ITreeCommand tree, String alias) {
		return tree.getSubCommands().stream().filter(sub -> Arrays.stream(sub.getAliases()).anyMatch(s -> s.equals(alias))).findFirst().orElse(null);
	}
	
	public ICommand getCommand(String alias) {
		return getCommands().stream().filter(cmd -> Arrays.stream(cmd.getAliases()).anyMatch(s -> s.equals(alias))).findFirst().orElse(null);
	}
	
	private void load() {
        String url = "br.net.brjdevs.steven.bran.cmds";
        Reflections reflections = new Reflections(new ConfigurationBuilder().setUrls(ClasspathHelper.forPackage(url)).setScanners(new SubTypesScanner(),
			    new TypeAnnotationsScanner(), new MethodAnnotationsScanner()).filterInputsBy(new FilterBuilder().includePackage(url)));
	    Set<Method> commands = reflections.getMethodsAnnotatedWith(Command.class);
	    commands.forEach(method -> {
		    Class clazz = method.getDeclaringClass();
		    method.setAccessible(true);
		    if (!method.getReturnType().equals(ICommand.class)) {
			    LOG.fatal("Method annotated with Command.class returns " + method.getReturnType().getSimpleName() + " instead of ICommand.class.");
			    return;
		    }
	    	try {
			    ICommand command = (ICommand) method.invoke(null);
			    if (command.getAliases().length == 0) {
				    LOG.fatal("Attempted to register ICommand without aliases. (" + clazz.getSimpleName() + ")");
				    return;
			    }
                if (Utils.isEmpty(command.getDescription())) {
                    LOG.fatal("Attempted to register ICommand without description. (" + clazz.getSimpleName() + ")");
				    return;
			    }
                if (Utils.isEmpty(command.getName())) {
                    LOG.fatal("Attempted to register ICommand without name. (" + clazz.getSimpleName() + ")");
				    return;
			    }
			    if (command instanceof ITreeCommand && ((ITreeCommand) command).getSubCommands() != null && ((ITreeCommand) command).getSubCommands().isEmpty()) {
				    LOG.fatal("Attempted to register Tree ICommand without SubCommands. (" + clazz.getSimpleName() + ")");
				    return;
			    }
			    if (command.getCategory() == Category.UNKNOWN) {
				    LOG.fatal("Registered ICommand with UNKNOWN Category. (" + clazz.getSimpleName() + ")");
			    }
			    addCommand(command);
		    } catch (Exception e) {
	    		LOG.log(e);
		    }
		    method.setAccessible(false);
	    });
		LOG.info("Finished loading all Commands.");
	}
    
    public void log(ICommand cmd, String args, User user, MessageChannel channel, Guild guild, boolean successful) {
        try {
            SQLDatabase.getInstance().run((conn) -> {
                try {
                    PreparedStatement statement = conn.prepareStatement("INSERT INTO CMDLOG " +
                            "(cmd, arguments, userid, channelid, guildid, date, successful, sessionId) VALUES(" +
                            "?, " +
                            "?, " +
                            "?, " +
                            "?, " +
                            "?, " +
                            "?, " +
                            "?," +
                            "? " +
                            ");");
                    statement.setString(1, cmd.getName());
                    statement.setString(2, args);
                    statement.setString(3, user.getId());
                    statement.setString(4, channel.getId());
                    statement.setString(5, guild == null ? null : guild.getId());
                    statement.setLong(6, System.currentTimeMillis());
                    statement.setInt(7, successful ? 1 : 0);
                    statement.setLong(8, Bran.getInstance().getSessionId());
                    
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    SQLAction.LOGGER.log(exception);
                }
            }).queue();
        } catch (SQLException e) {
            SQLAction.LOGGER.log(e);
        }
    }
}