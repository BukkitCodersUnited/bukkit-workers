package org.randomgd.bukkit.workers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.randomgd.bukkit.workers.info.FarmerInfo;
import org.randomgd.bukkit.workers.info.GolemInfo;
import org.randomgd.bukkit.workers.info.LibrarianInfo;
import org.randomgd.bukkit.workers.info.WorkerInfo;
import org.randomgd.bukkit.workers.util.Configuration;
import org.randomgd.bukkit.workers.util.WorkerCreator;

/**
 * Just a bunch of test.
 */
public class WorkerHandler extends JavaPlugin implements Listener {

	/**
	 * Message displayed if the player doesn't have the permission to interact
	 * with the villagers for job assignment.
	 */
	private static final String NO_JOB_PERMISSION_MESSAGE = ChatColor.RED
			+ "You can't assign jobs to villagers";

	/**
	 * Message displayed if the player doesn't have the permission to give
	 * something to the villagers.
	 */
	private static final String NO_GIVE_PERMISSION_MESSAGE = ChatColor.RED
			+ "You're not allow to give items to villagers";

	/**
	 * Message displayed if trying to interact with a villager that is not
	 * "useful".
	 */
	private static final String NOT_USEFUL_VILLAGER = ChatColor.GRAY
			+ "This is not a useful villager.";

	/**
	 * A map between item and the triggered/chosen profession.
	 */
	private static final Map<Material, WorkerCreator> PROFESSION_TRIGGER = new HashMap<Material, WorkerCreator>();
	{
		PROFESSION_TRIGGER.put(Material.WHEAT, new WorkerCreator(
				Villager.Profession.FARMER, FarmerInfo.class,
				ChatColor.DARK_GRAY + "This villager is now a farmer."));

		PROFESSION_TRIGGER.put(Material.BOOK, new WorkerCreator(
				Villager.Profession.LIBRARIAN, LibrarianInfo.class,
				ChatColor.DARK_GRAY + "This villager is now a librarian."));
	}

	private Configuration configurationHandler;
	/**
	 * Workers informations.
	 */
	private Map<UUID, WorkerInfo> workerStack;

	/**
	 * Constructor.
	 */
	public WorkerHandler() {
		super();
		workerStack = new HashMap<UUID, WorkerInfo>();
	}

	@Override
	public void onEnable() {
		FileConfiguration configuration = getConfig();

		// Get configuration.
		int period = configuration.getInt("period");
		configurationHandler = new Configuration(configuration);

		// Get worker information from disk.
		getWorkerInfoFromDisk();

		// Update the worker information with configuration.
		for (WorkerInfo i : workerStack.values()) {
			i.setConfiguration(configurationHandler);
		}

		// Launch the BEAST !
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this,
				new Runnable() {
					@Override
					public void run() {
						// Villagers are wandering ... oooh the great life.
						// They will fix blocks nearby, according to their
						// profession.
						for (World world : getServer().getWorlds()) {
							browseEntities(Villager.class, world);
							browseEntities(IronGolem.class, world);
						}
					}
				}, 10, period);
	}

	@SuppressWarnings("unchecked")
	private void getWorkerInfoFromDisk() {
		// Populate the worker map.
		File directory = getDataFolder();
		if (!directory.exists()) {
			directory.mkdir();
		}
		if (directory.exists() && directory.isDirectory()) {
			// We can work now.
			String path = String.format("%s%cworkers.dat", directory.getPath(),
					File.separatorChar);
			File dataFile = new File(path);
			if (dataFile.exists() && dataFile.canRead() && dataFile.isFile()) {
				try {
					ObjectInputStream input = new ObjectInputStream(
							new FileInputStream(dataFile));
					Object result = input.readObject();
					// Type erasure, all that stuff ... not good ... noooot
					// good.
					input.close();
					workerStack = (Map<UUID, WorkerInfo>) result;
				} catch (Exception ex) {
					// Ouch ...
					System.out
							.println("Can't load informations about our fellow workers");
					ex.printStackTrace();
				}
			}
		}
	}

	@Override
	public void onDisable() {
		// Populate the worker map.
		File directory = getDataFolder();
		if (!directory.exists()) {
			directory.mkdir();
		}
		if (directory.exists() && directory.isDirectory()) {
			// We can work now.
			String path = String.format("%s%cworkers.dat", directory.getPath(),
					File.separatorChar);
			File dataFile = new File(path);
			try {
				if (!dataFile.exists()) {
					dataFile.createNewFile();
				}
				ObjectOutputStream output = new ObjectOutputStream(
						new FileOutputStream(dataFile));
				output.writeObject(workerStack);
				output.flush();
				output.close();
			} catch (Exception ex) {
				// Ouch ...
				System.out
						.println("Can't write informations about our fellow workers");
				ex.printStackTrace();
			}
		}
	}

	@EventHandler
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		Player player = event.getPlayer();

		Entity entity = event.getRightClicked();
		EntityType entityType = entity.getType();
		ItemStack stack = player.getItemInHand();
		Material material = stack.getType();

		if (entityType.equals(EntityType.VILLAGER)) {
			Villager villager = (Villager) entity;
			UUID id = villager.getUniqueId();
			WorkerInfo info = workerStack.get(id);
			boolean reassign = true;
			if (info != null) {
				if (!player.hasPermission("usefulvillagers.give")) {
					player.sendMessage(NO_GIVE_PERMISSION_MESSAGE);
					return;
				}
				reassign = !give(info, player, stack, material);
			}

			if (reassign) {
				WorkerCreator creator = PROFESSION_TRIGGER.get(material);
				if (creator != null) {
					Villager.Profession profession = creator.getProfession();
					if ((profession != null)
							&& (!(profession.equals(villager.getProfession()) && (info != null)))) {
						// It's ok, we can convert it !
						if (!player.hasPermission("usefulvillagers.jobassign")) {
							player.sendMessage(NO_JOB_PERMISSION_MESSAGE);
							return;
						}
						villager.setProfession(profession);
						info = creator.create();
						info.setConfiguration(configurationHandler);
						workerStack.put(id, info);
						player.sendMessage(creator.getMessage());
					}
				}
			}

			if (info == null) {
				player.sendMessage(NOT_USEFUL_VILLAGER);
			}
		} else if (entityType.equals(EntityType.IRON_GOLEM)) {
			UUID uuid = entity.getUniqueId();
			WorkerInfo currentInfo = workerStack.get(uuid);
			if (material.equals(Material.TORCH)) {
				if (currentInfo == null) {
					currentInfo = new GolemInfo();
					workerStack.put(uuid, currentInfo);
				}
				give(currentInfo, player, stack, material);
			} else if (material.equals(Material.STICK)) {
				if (currentInfo != null) {
					currentInfo.printInfoToPlayer(player);
				}
			}
		}
	}

	private boolean give(WorkerInfo info, Player player, ItemStack stack,
			Material material) {
		boolean result = info.give(material, player);

		if (result) {
			int sAmount = stack.getAmount();
			stack.setAmount(sAmount - 1);
			player.setItemInHand(stack);
		}
		return result;
	}

	protected <T extends Entity> void browseEntities(Class<T> api,
			final World world) {
		Collection<T> entities = world.getEntitiesByClass(api);
		final Collection<T> browseable = new LinkedList<T>(); // Avoid
																// concurrent
		// modification.
		browseable.addAll(entities);

		this.getServer().getScheduler()
				.scheduleAsyncDelayedTask(this, new Runnable() {
					@Override
					public void run() {
						browseEntities(world, browseable);
					}
				});
	}

	protected <T extends Entity> void browseEntities(World world,
			final Collection<T> browseable) {
		// ## No overcost compared to using the workerInfo keys ?
		for (T i : browseable) {
			// Look at the surrounding.
			UUID id = i.getUniqueId();
			WorkerInfo info = workerStack.get(id);
			if (info != null) {
				Location currentLocation = i.getLocation();
				int x = currentLocation.getBlockX();
				int y = currentLocation.getBlockY();
				int z = currentLocation.getBlockZ();
				// It's always nice to know where we are.
				info.perform(i, x, y, z, world);
			}
		}

		browseable.clear();
	}
}
