package com.nisovin.shopkeepers.shopkeeper.admin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.ShopkeeperCreateException;
import com.nisovin.shopkeepers.api.shopkeeper.admin.AdminShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.admin.AdminShopkeeper;
import com.nisovin.shopkeepers.api.ui.DefaultUITypes;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.ui.defaults.SKDefaultUITypes;
import com.nisovin.shopkeepers.ui.defaults.TradingHandler;
import com.nisovin.shopkeepers.util.Log;
import com.nisovin.shopkeepers.util.Utils;

public abstract class AbstractAdminShopkeeper extends AbstractShopkeeper implements AdminShopkeeper {

	public static class AdminShopTradingHandler extends TradingHandler {

		protected AdminShopTradingHandler(AbstractAdminShopkeeper shopkeeper) {
			super(SKDefaultUITypes.TRADING(), shopkeeper);
		}

		@Override
		public AbstractAdminShopkeeper getShopkeeper() {
			return (AbstractAdminShopkeeper) super.getShopkeeper();
		}

		@Override
		protected boolean canOpen(Player player) {
			if (!super.canOpen(player)) return false;

			// check trading permission:
			String tradePermission = this.getShopkeeper().getTradePremission();
			if (tradePermission != null && !Utils.hasPermission(player, tradePermission)) {
				Log.debug("Blocked trading UI opening for " + player.getName() + ": Missing custom trade permission '" + tradePermission + "'.");
				Utils.sendMessage(player, Settings.msgMissingCustomTradePerm);
				return false;
			}
			return true;
		}
	}

	// null indicates that no additional permission is required:
	protected String tradePermission = null;

	/**
	 * Creates a not yet initialized {@link AbstractAdminShopkeeper} (for use in sub-classes).
	 * <p>
	 * See {@link AbstractShopkeeper} for details on initialization.
	 * 
	 * @param id
	 *            the shopkeeper id
	 */
	protected AbstractAdminShopkeeper(int id) {
		super(id);
	}

	/**
	 * Expects an {@link AdminShopCreationData}.
	 */
	@Override
	protected void loadFromCreationData(ShopCreationData shopCreationData) throws ShopkeeperCreateException {
		super.loadFromCreationData(shopCreationData);
	}

	@Override
	protected void setup() {
		if (this.getUIHandler(DefaultUITypes.TRADING()) == null) {
			this.registerUIHandler(new AdminShopTradingHandler(this));
		}
		super.setup();
	}

	@Override
	protected void loadFromSaveData(ConfigurationSection configSection) throws ShopkeeperCreateException {
		super.loadFromSaveData(configSection);
		// load trade permission:
		tradePermission = configSection.getString("tradePerm", null);
	}

	@Override
	public void save(ConfigurationSection configSection) {
		super.save(configSection);
		// save trade permission:
		configSection.set("tradePerm", tradePermission);
	}

	@Override
	public String getTradePremission() {
		return tradePermission;
	}

	@Override
	public void setTradePermission(String tradePermission) {
		if (tradePermission != null && tradePermission.isEmpty()) {
			tradePermission = null;
		}
		this.tradePermission = tradePermission;
		this.markDirty();
	}
}
