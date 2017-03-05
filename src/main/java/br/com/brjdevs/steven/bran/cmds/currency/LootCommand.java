package br.com.brjdevs.steven.bran.cmds.currency;

import br.com.brjdevs.steven.bran.core.command.Command;
import br.com.brjdevs.steven.bran.core.command.builders.CommandBuilder;
import br.com.brjdevs.steven.bran.core.command.enums.Category;
import br.com.brjdevs.steven.bran.core.command.interfaces.ICommand;
import br.com.brjdevs.steven.bran.core.currency.BankAccount;
import br.com.brjdevs.steven.bran.core.currency.ItemStack;
import br.com.brjdevs.steven.bran.core.currency.TextChannelGround;
import br.com.brjdevs.steven.bran.core.managers.RateLimiter;
import br.com.brjdevs.steven.bran.core.utils.Emojis;

import java.util.List;

public class LootCommand {
	
	private static final RateLimiter RATELIMITER = new RateLimiter(10000);
	@Command
	private static ICommand loot() {
		return new CommandBuilder(Category.CURRENCY)
				.setAliases("loot")
				.setName("Loot Command")
				.setDescription("Loots stuff from the ground!")
				.setPrivateAvailable(false)
				.setAction((event) -> {
					if (!RATELIMITER.process(event.getAuthor())) {
						event.sendMessage("Hey, slow down a little bit there buddy! Let other people loot too!").queue();
						return;
					}
					TextChannelGround ground = TextChannelGround.of(event.getTextChannel());
					List<ItemStack> items = ground.collectItems();
					int money = ground.collectMoney();
					if (items.isEmpty() && money <= 0) {
						event.sendMessage("Nothing to loot here.").queue();
						return;
					}
					if (!event.getUserData().getProfile().takeStamina(5)) {
						event.sendMessage("You are too tired of walking, why don't you take a rest while your stamina regenerates?").queue();
						return;
					}
					StringBuilder sb = new StringBuilder().append(Emojis.PARTY_POPPER + " ");
					sb.append("You walk a little and find ");
					if (!items.isEmpty()) {
						items.forEach(stack -> {
							ItemStack s = new ItemStack(stack.getItem(), 0);
							for (int i = 0; i < stack.getAmount(); i++) {
								if (!event.getUserData().getProfile().getInventory().put(stack.getItem())) {
									ground.dropItem(stack.getItem());
								} else {
									s = s.join(new ItemStack(stack.getItem(), 1));
								}
							}
							sb.append(s.toString());
						});
					}
					if (money > 0) {
						if (!event.getUserData().getProfile().getBankAccount().addCoins(money, BankAccount.MAIN_BANK)) {
							ground.dropMoney(money);
						} else {
							if (!sb.toString().equals(Emojis.PARTY_POPPER + " You walk a little and find "))
								sb.append(" and ");
							sb.append(money).append(" coins");
						}
					}
					sb.append("!");
					event.sendMessage(sb.toString()).queue();
				})
				.build();
	}
}