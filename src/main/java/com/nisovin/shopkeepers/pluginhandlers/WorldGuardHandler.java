package com.nisovin.shopkeepers.pluginhandlers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.util.Log;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.regions.RegionQuery;

public class WorldGuardHandler {

	public static final String PLUGIN_NAME = "WorldGuard";
	// This flag got originally registered by WorldGuard itself, but this is no longer the case. Other plugins are
	// supposed to register it themselves. One such plugin is for example ChestShop. To not rely on other plugins for
	// registering this flag, we will register it ourselves if no other plugin has registered it yet.
	private static final String FLAG_ALLOW_SHOP = "allow-shop";

	public static Plugin getPlugin() {
		return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
	}

	public static boolean isPluginEnabled() {
		return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
	}

	// Note: WorldGuard only allows registering flags before it got enabled.
	public static void registerAllowShopFlag() {
		if (getPlugin() == null) return; // WorldGuard is not loaded
		Link.registerAllowShopFlag();
	}

	public static boolean isShopAllowed(Player player, Location loc) {
		Plugin wgPlugin = getPlugin();
		if (wgPlugin == null || !wgPlugin.isEnabled()) return true;
		return Link.isShopAllowed(wgPlugin, player, loc);
	}

	// Separate class that gets only accessed if WorldGuard is present. Avoids class loading issues for certain edge
	// cases.
	private static class Link {

		public static void registerAllowShopFlag() {
			Log.debug("Registering WorldGuard flag '" + FLAG_ALLOW_SHOP + "'.");
			try {
				StateFlag allowShopFlag = new StateFlag(FLAG_ALLOW_SHOP, false);
				WorldGuard.getInstance().getFlagRegistry().register(allowShopFlag);
			} catch (FlagConflictException | IllegalStateException e) {
				// another plugin has probably already registered this flag,
				// or this plugin got hard reloaded by some plugin manager plugin
				Log.debug("Couldn't register WorldGuard flag '" + FLAG_ALLOW_SHOP + "': " + e.getMessage());
			}
		}

		public static boolean isShopAllowed(Plugin worldGuardPlugin, Player player, Location loc) {
			assert worldGuardPlugin instanceof WorldGuardPlugin && worldGuardPlugin.isEnabled() && player != null && loc != null;
			WorldGuardPlugin wgPlugin = (WorldGuardPlugin) worldGuardPlugin;
			RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();

			// check if shop flag is set:
			boolean allowShopFlag = false; // false if unset or disallowed

			// get shop flag:
			Flag<?> shopFlag = WorldGuard.getInstance().getFlagRegistry().get(FLAG_ALLOW_SHOP);
			if (shopFlag != null) {
				// check if shop flag is set:
				if (shopFlag instanceof StateFlag) {
					allowShopFlag = query.testState(BukkitAdapter.adapt(loc), null, (StateFlag) shopFlag);
				} else if (shopFlag instanceof BooleanFlag) {
					// value might be null:
					Boolean shopFlagValue = query.queryValue(BukkitAdapter.adapt(loc), null, (BooleanFlag) shopFlag);
					allowShopFlag = (Boolean.TRUE.equals(shopFlagValue));
				} else {
					// unknown flag type, assume unset
				}
			} else {
				// shop flag doesn't exist, assume unset
			}

			if (Settings.requireWorldGuardAllowShopFlag) {
				// allow shops ONLY in regions with the shop flag set:
				return allowShopFlag;
			} else {
				// allow shops in regions where the shop flag is set OR the player can build:
				return (allowShopFlag || query.testState(BukkitAdapter.adapt(loc), wgPlugin.wrapPlayer(player), Flags.BUILD));
			}
		}
	}
}
