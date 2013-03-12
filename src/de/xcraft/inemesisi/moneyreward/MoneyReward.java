package de.xcraft.inemesisi.moneyreward;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import de.xcraft.inemesisi.moneyreward.Msg.Key;


public class MoneyReward extends JavaPlugin {

	private EventListener				eventlistener	= new EventListener(this);
	private ConfigManager				cfg				= new ConfigManager(this);
	private Economy						economy			= null;
	private Permission					permission		= null;
	private Essentials					essentials		= null;
	public Map<Player, RewardPlayer>	players			= new HashMap<Player, RewardPlayer>();
	public List<Integer>				blacklist		= new ArrayList<Integer>();

	@Override
	public void onDisable() {
		for (World world : this.getServer().getWorlds()) {
			for (Entity e : world.getEntities()) {
				if (blacklist.contains(e.getEntityId())) {
					if (cfg.isremoveBlacklistedOnChunkunload()) {
						e.remove();
					}
				}
			}
		}
		Messenger.info(blacklist.size() + " Mobs blacklisted");
		Messenger.info(eventlistener.added + " Mobs added");
		Messenger.info(eventlistener.denied + " Mobs denied");
		Messenger.info(eventlistener.despawned + " Mobs despawned");
		cfg.save();
		Messenger.info("disabled!");
	}

	@Override
	public void onEnable() {
		PluginManager pm = this.getServer().getPluginManager();
		pm.registerEvents(eventlistener, this);
		this.setupEconomy();
		this.setupPermissions();
		cfg.load();
		Msg.init(this);
		if (cfg.isUseEssentials()) {
			this.setupEssentials();
		}
		for (Player player : this.getServer().getOnlinePlayers()) {
			players.put(player, new RewardPlayer(player));
		}
		if (cfg.isOnlineRewardActive()) {
			this.startScheduler();
		}
		Messenger.info("enabled!");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String cmdLabel, String[] args) {
		if (args.length == 0) { return true; }
		if (args[0].equals("reload") && sender.hasPermission("XcraftMoneyReward.Reload")) {
			cfg.load();
			sender.sendMessage(ChatColor.DARK_AQUA + "[" + this.getName() + "] " + ChatColor.GRAY + " config reloaded!");
		}
		if (args[0].equals("info") && sender.hasPermission("XcraftMoneyReward.Mob")) {
			sender.sendMessage(blacklist.size() + " Mobs blacklisted");
			sender.sendMessage(eventlistener.added + " Mobs added");
			sender.sendMessage(eventlistener.denied + " Mobs denied");
			sender.sendMessage(eventlistener.despawned + " Mobs despawned");
		}
		return true;
	}

	private boolean setupEconomy() {
		RegisteredServiceProvider<Economy> economyProvider = this.getServer().getServicesManager().getRegistration(
				net.milkbowl.vault.economy.Economy.class);
		if (economyProvider != null) {
			economy = economyProvider.getProvider();
		}
		return economy != null;
	}

	private boolean setupPermissions() {
		RegisteredServiceProvider<Permission> permissionProvider = this.getServer().getServicesManager()
				.getRegistration(net.milkbowl.vault.permission.Permission.class);
		if (permissionProvider != null) {
			permission = permissionProvider.getProvider();
		}
		return permission != null;
	}

	private boolean setupEssentials() {
		essentials = (Essentials) this.getServer().getPluginManager().getPlugin("Essentials");
		return essentials != null;
	}

	private void startScheduler() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		int sec = cal.get(Calendar.SECOND);
		sec = 60 - sec;
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {

			@Override
			public void run() {
				MoneyReward.this.updateOnlineTime();
			}
		}, sec * 20, 60 * 20); // every minute
	}

	public void updateOnlineTime() {
		for (Player player : players.keySet()) {
			if (!player.hasPermission(PermissionNode.ONLINE.get())) { return; }
			if (this.getCfg().isUseEssentials()) {
				User user = essentials.getUser(player);
				if ((user != null) && user.isAfk()) {
					continue;
				}
			}
			RewardPlayer rp = players.get(player);
			rp.onlinetime++;
			if (rp.onlinetime == cfg.getOnlineRewardIntervall()) {
				double reward = cfg.getOnlineReward(player);
				if (this.reward(player.getName(), reward) && this.getCfg().isOnlineRewardNotify()) {
					Messenger.tellPlayer(player, Msg.REWARD_DAILY.toString(Key.$Player$(player.getName()), Key
							.$Reward$(this.getEconomy().format(reward)), Key.$Mob$));
				}
			}
		}
	}

	public boolean reward(String player, double amount) {
		if (amount > 0) {
			this.getEconomy().depositPlayer(player, amount);
			return true;
		}
		if (amount < 0) {
			this.getEconomy().withdrawPlayer(player, -amount);
			return true;
		}
		return false;
	}

	public Economy getEconomy() {
		return economy;
	}

	public Permission getPermission() {
		return permission;
	}

	public ConfigManager getCfg() {
		return cfg;
	}
}
