package com.nisovin.shopkeepers.commands.shopkeepers;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.shopkeeper.DefaultShopTypes;
import com.nisovin.shopkeepers.api.shopkeeper.admin.AdminShopCreationData;
import com.nisovin.shopkeepers.api.shopobjects.DefaultShopObjectTypes;
import com.nisovin.shopkeepers.commands.lib.CommandArgs;
import com.nisovin.shopkeepers.commands.lib.CommandContext;
import com.nisovin.shopkeepers.commands.lib.CommandException;
import com.nisovin.shopkeepers.commands.lib.CommandInput;
import com.nisovin.shopkeepers.commands.lib.PlayerCommand;
import com.nisovin.shopkeepers.commands.lib.arguments.IntegerArgument;
import com.nisovin.shopkeepers.commands.lib.arguments.OptionalArgument;

class CommandDebugCreateShops extends PlayerCommand {

	private final static String ARGUMENT_SHOP_COUNT = "shopCount";

	private final SKShopkeepersPlugin plugin;

	CommandDebugCreateShops(SKShopkeepersPlugin plugin) {
		super("debugCreateShops");
		this.plugin = plugin;

		// set permission:
		this.setPermission(ShopkeepersPlugin.DEBUG_PERMISSION);

		// set description:
		this.setDescription("Creates lots of shopkeepers for stress testing.");

		// hidden debugging command:
		this.setHiddenInParentHelp(true);

		// arguments:
		this.addArgument(new OptionalArgument(new IntegerArgument(ARGUMENT_SHOP_COUNT)));
	}

	@Override
	protected void execute(CommandInput input, CommandContext context, CommandArgs args) throws CommandException {
		assert (input.getSender() instanceof Player);
		Player player = (Player) input.getSender();
		int shopCount = context.getOrDefault(ARGUMENT_SHOP_COUNT, 10);

		player.sendMessage(ChatColor.GREEN + "Creating " + shopCount + " shopkeepers, starting here!");
		Location curSpawnLocation = player.getLocation();
		for (int i = 0; i < shopCount; i++) {
			plugin.handleShopkeeperCreation(AdminShopCreationData.create(player, DefaultShopTypes.ADMIN(),
					DefaultShopObjectTypes.LIVING().get(EntityType.VILLAGER), curSpawnLocation.clone(), null));
			curSpawnLocation.add(2, 0, 0);
		}
		player.sendMessage(ChatColor.GREEN + "Done!");
	}
}
