package com.nisovin.shopkeepers.ui.defaults;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

import com.nisovin.shopkeepers.Log;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.Shopkeeper;
import com.nisovin.shopkeepers.ShopkeepersAPI;
import com.nisovin.shopkeepers.TradingRecipe;
import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.events.OpenTradeEvent;
import com.nisovin.shopkeepers.events.ShopkeeperTradeCompletedEvent;
import com.nisovin.shopkeepers.events.ShopkeeperTradeEvent;
import com.nisovin.shopkeepers.ui.UIHandler;
import com.nisovin.shopkeepers.ui.UIType;
import com.nisovin.shopkeepers.util.Utils;

public class TradingHandler extends UIHandler {

	public TradingHandler(UIType uiType, Shopkeeper shopkeeper) {
		super(uiType, shopkeeper);
	}

	@Override
	protected boolean canOpen(Player player) {
		assert player != null;
		if (!Utils.hasPermission(player, ShopkeepersAPI.TRADE_PERMISSION)) {
			Log.debug("Blocked trade window opening from " + player.getName() + ": missing trade permission");
			Utils.sendMessage(player, Settings.msgMissingTradePerm);
			return false;
		}
		return true;
	}

	@Override
	protected boolean openWindow(Player player) {
		final Shopkeeper shopkeeper = this.getShopkeeper();
		OpenTradeEvent event = new OpenTradeEvent(player, shopkeeper);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			Log.debug("Trade window not opened: cancelled by another plugin");
			return false;
		}

		// create and open trading window:
		String title = this.getInventoryTitle();
		return NMSManager.getProvider().openTradeWindow(title, shopkeeper.getTradingRecipes(player), player);
	}

	protected String getInventoryTitle() {
		String title = this.getShopkeeper().getName();
		if (title == null || title.isEmpty()) {
			title = Settings.msgTradingTitleDefault;
		}
		return Settings.msgTradingTitlePrefix + title;
	}

	@Override
	public boolean isWindow(Inventory inventory) {
		return inventory instanceof MerchantInventory;
	}

	@Override
	protected void onInventoryClose(InventoryCloseEvent event, Player player) {
		// nothing to do by default
	}

	@Override
	protected void onInventoryClick(InventoryClickEvent event, Player player) {
		assert event != null && player != null;
		final Shopkeeper shopkeeper = this.getShopkeeper();
		String playerName = player.getName();
		if (event.isCancelled()) {
			Log.debug("Some plugin has cancelled the click in trading window for "
					+ playerName + " at " + shopkeeper.getPositionString() + ".");
			return;
		}

		int rawSlot = event.getRawSlot();

		// prevent special clicks:
		boolean unwantedSpecialClick = false;
		InventoryAction action = event.getAction();
		if (action == InventoryAction.COLLECT_TO_CURSOR) {
			unwantedSpecialClick = true;
		} else if (rawSlot == 2) {
			// TODO allow certain special clicks on the result slot again?
			if (!event.isLeftClick() || (event.isShiftClick() && !this.isShiftTradeAllowed(event))) {
				unwantedSpecialClick = true;
			}
		}

		if (unwantedSpecialClick) {
			Log.debug("Prevented special click in trading window by " + playerName + " at " + shopkeeper.getPositionString() + ".");
			event.setCancelled(true);
			Utils.updateInventoryLater(player);
			return;
		}

		// result slot clicked?
		if (rawSlot != 2) {
			return;
		}

		MerchantInventory inventory = (MerchantInventory) event.getInventory();
		ItemStack resultItem = inventory.getItem(2);
		if (Utils.isEmpty(resultItem)) {
			Log.debug("Not handling trade: There is no item in the clicked result slot (no trade available).");
			return; // no trade available
		}

		ItemStack item1 = inventory.getItem(0);
		// use null here instead of air, consistent behavior with previous versions:
		ItemStack item2 = Utils.getNullIfEmpty(inventory.getItem(1));

		// minecraft is also allowing the trade, if the second offered item matches the first required one and the first
		// slot is empty:
		// so let's as well assume the item in slot 2 would be in the currently empty slot 1
		if (Utils.isEmpty(item1)) {
			item1 = item2;
			item2 = null;
		}

		// find the recipe minecraft is using for the trade:
		TradingRecipe usedRecipe = NMSManager.getProvider().getUsedTradingRecipe(inventory);

		// validate the used recipe:
		boolean invalidRecipe = false;
		if (usedRecipe == null) {
			// this shouldn't happen..
			Log.debug("Invalid trade by " + playerName + " with shopkeeper at " + shopkeeper.getPositionString() + ": "
					+ "Minecraft offered a trade, but we didn't find the used recipe!");
			invalidRecipe = true;
		} else if (!usedRecipe.getResultItem().equals(resultItem)) {
			// this shouldn't happen..
			Log.debug("Invalid trade by " + playerName + " with shopkeeper at " + shopkeeper.getPositionString() + ": "
					+ "The trade result item doesn't match the expected item of the used trading recipe!");
			invalidRecipe = true;
		}
		if (invalidRecipe) {
			event.setCancelled(true);
			Utils.updateInventoryLater(player);
			return;
		}

		ItemStack requiredItem1 = usedRecipe.getItem1();
		ItemStack requiredItem2 = usedRecipe.getItem2();

		// detecting and preventing issue due to minecraft bug MC-81687 (traded items not being properly removed):
		// TODO should be fixed in newer versions (1.9+), remove when no longer needed
		if (NMSManager.getProvider().getVersionId().startsWith("1_8_")) {
			assert requiredItem1 != null && item1 != null;
			if (Utils.isSimilar(item1, item2)) {
				assert requiredItem2 != null && item2 != null;
				if (item1.getAmount() < requiredItem1.getAmount() || item2.getAmount() < requiredItem2.getAmount()) {
					Log.debug("Preventing trade by " + playerName + " with shopkeeper at " + shopkeeper.getPositionString() + ": "
							+ "Due to a minecraft bug (MC-81687), which players can use to exploit, this trade might not get properly handled.");
					event.setCancelled(true);
					Utils.updateInventoryLater(player);
					return;
				}
			}
		}

		if (Settings.useStrictItemComparison) {
			// verify the recipe items are perfectly matching:
			if (!this.isStrictMatchingRecipeItems(requiredItem1, requiredItem2, item1, item2)) {
				if (Settings.debug) { // additional check so we don't do the item comparisons if not really needed
					Log.debug("Invalid trade by " + playerName + " with shopkeeper at " + shopkeeper.getPositionString() + " using strict item comparison:");
					Log.debug("Used recipe: " + Utils.getSimpleRecipeInfo(usedRecipe));
					Log.debug("Recipe item 1: " + (Utils.isSimilar(requiredItem1, item1) ? "similar" : "not similar"));
					Log.debug("Recipe item 2: " + (Utils.isSimilar(requiredItem2, item2) ? "similar" : "not similar"));
				}
				event.setCancelled(true);
				Utils.updateInventoryLater(player);
				return;
			}
		}

		ItemStack cursor = event.getCursor();
		if (!Utils.isEmpty(cursor)) {
			// minecraft doesn't handle the trading in case the cursor cannot hold the resulting items
			// so we have to make sure that our trading logic is as well not run:
			if (!cursor.isSimilar(resultItem) || cursor.getAmount() + resultItem.getAmount() > cursor.getMaxStackSize()) {
				Log.debug("Skip trade by " + playerName + " with shopkeeper at " + shopkeeper.getPositionString() + ": the cursor cannot carry the resulting items");
				event.setCancelled(true); // making sure minecraft really doesn't process the trading
				return;
			}
		}

		// call trade event, giving other plugins a chance to cancel the trade before the shopkeeper processes it:
		ShopkeeperTradeEvent tradeEvent = new ShopkeeperTradeEvent(shopkeeper, player, event, usedRecipe);
		Bukkit.getPluginManager().callEvent(tradeEvent);
		if (tradeEvent.isCancelled()) {
			assert event.isCancelled();
			Log.debug("Trade was cancelled by some other plugin.");
			return;
		}

		// let shopkeeper handle the purchase:
		this.onPurchaseClick(event, player, usedRecipe, item1, item2);

		// call trade-completed event:
		ShopkeeperTradeCompletedEvent tradeCompletedEvent = new ShopkeeperTradeCompletedEvent(tradeEvent);
		Bukkit.getPluginManager().callEvent(tradeCompletedEvent);
	}

	// whether or not the player can buy via shift click on the result slot:
	protected boolean isShiftTradeAllowed(InventoryClickEvent event) {
		return false; // not allowed by default, just in case
	}

	/**
	 * Called when a player is trying to trade.
	 * 
	 * <p>
	 * Note: The offered items are the items the trading player provided. They can slightly differ from the items from
	 * the trading recipe, depending on item comparison of minecraft and shopkeeper settings.
	 * </p>
	 * 
	 * @param event
	 *            the inventory click event
	 * @param player
	 *            the player
	 * @param tradingRecipe
	 *            The trading recipe minecraft is using for the trade.
	 * @param offered1
	 *            The first offered item. If the first slot was empty, this is the item from the second slot.
	 * @param offered2
	 *            The second offered item. If the first slot was empty, this is null.
	 */
	protected void onPurchaseClick(InventoryClickEvent event, Player player, TradingRecipe tradingRecipe, ItemStack offered1, ItemStack offered2) {
		// nothing to do by default
	}

	protected int getAmountAfterTaxes(int amount) {
		if (Settings.taxRate == 0) return amount;
		int taxes = 0;
		if (Settings.taxRoundUp) {
			taxes = (int) Math.ceil((double) amount * (Settings.taxRate / 100F));
		} else {
			taxes = (int) Math.floor((double) amount * (Settings.taxRate / 100F));
		}
		return amount - taxes;
	}

	private boolean isStrictMatchingRecipeItems(ItemStack required1, ItemStack required2, ItemStack offered1, ItemStack offered2) {
		return Utils.isSimilar(required1, offered1) && Utils.isSimilar(required2, offered2);
	}
}
