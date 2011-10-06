package de.xcraft.engelier.XcraftGate;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import de.xcraft.engelier.XcraftGate.Commands.*;
import de.xcraft.engelier.XcraftGate.Generator.Generator;

public class XcraftGate extends JavaPlugin {
	private final ListenerServer pluginListener = new ListenerServer(this);
	private final ListenerPlayer playerListener = new ListenerPlayer(this);
	private final ListenerCreature creatureListener = new ListenerCreature(this);
	private final ListenerEntity entityListener = new ListenerEntity(this);
	private final ListenerWeather weatherListener = new ListenerWeather(this);
	private final ListenerWorld worldListener = new ListenerWorld(this);

	private PluginManager pm = null;
	
	private static final Integer minCBVersion = 1236;

	private SetWorld worlds = new SetWorld(this);
	private SetGate gates = new SetGate(this);
	
	public Map<String, Location> justTeleported = new HashMap<String, Location>();
	public Map<String, Location> justTeleportedFrom = new HashMap<String, Location>();

	public Configuration config = null;

	public final Logger log = Logger.getLogger("Minecraft");
	public final Properties serverconfig = new Properties(); 

	class RunCreatureLimit implements Runnable {
		public void run() {
			for (DataWorld thisWorld: worlds) {
				thisWorld.checkCreatureLimit();
			}
		}
	}
	
	class RunTimeFrozen implements Runnable {
		public void run() {
			for (DataWorld thisWorld: worlds) {
				if (thisWorld.isTimeFrozen()) {
					thisWorld.resetFrozenTime();
				}
			}
		}
	}
	
	class RunCheckWorldInactive implements Runnable {
		@Override
		public void run() {
			for (World thisWorld : getServer().getWorlds()) {
				if (worlds.get(thisWorld).checkInactive() && !thisWorld.getName().equalsIgnoreCase(serverconfig.getProperty("level-name"))) {
					log.info(getNameBrackets() + "World '" + thisWorld.getName() + "' inactive. Unloading.");
					
					worlds.get(thisWorld).unload();
				}
			}						
		}		
	}
	
	class RunLoadAllWorlds implements Runnable {
		@Override
		public void run() {
			for (DataWorld thisWorld : worlds) {
				if (!thisWorld.isLoaded() && (config.getBoolean("dynworld.enabled", false) == false || thisWorld.isSticky())) {
					thisWorld.load();
				}
			}
		}		
	}
	
	public void onDisable() {
		getServer().getScheduler().cancelTasks(this);
		gates.save();
		worlds.save();
	}

	public void onEnable() {
		String cbVersionString = this.getServer().getVersion().replaceAll("^.*b([0-9]+)jnks.*$", "$1");
		if (cbVersionString.length() > 0) {
			if (Integer.parseInt(cbVersionString) < minCBVersion) {
				log.severe(getNameBrackets() + "Sorry. But this version of XcraftGate requires CraftBukkit build " + minCBVersion + " or higher.");
				// disable myself!
				return;
			}
		}
		
		pm = new PluginManager(this);
		
		pm.registerEvent(Event.Type.CREATURE_SPAWN, creatureListener, Event.Priority.Normal);
		pm.registerEvent(Event.Type.ENTITY_REGAIN_HEALTH, entityListener, Event.Priority.Normal);
		pm.registerEvent(Event.Type.FOOD_LEVEL_CHANGE, entityListener, Event.Priority.Normal);
		pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener, Event.Priority.Normal);
		pm.registerEvent(Event.Type.PLUGIN_DISABLE, pluginListener,	Event.Priority.Monitor);
		pm.registerEvent(Event.Type.WEATHER_CHANGE, weatherListener, Event.Priority.Normal);
		pm.registerEvent(Event.Type.WORLD_LOAD, worldListener, Event.Priority.Highest);
		pm.registerEvent(Event.Type.WORLD_UNLOAD, worldListener, Event.Priority.Highest);

		File serverconfigFile = new File("server.properties");
		if (!serverconfigFile.exists()) {
			log.severe(getNameBrackets() + "unable to load server.properties.");
		} else {
			try {
				serverconfig.load(new FileInputStream(serverconfigFile));
			} catch (Exception ex) {
				log.severe(getNameBrackets() + "error loading " + serverconfigFile);
				ex.printStackTrace();
			}
		}

		config = getConfiguration();
		setConfigDefaults();
		worlds.load();
		gates.load();

		for(World thisWorld : getServer().getWorlds()) {
			worlds.onWorldLoad(thisWorld);
		}
		
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new RunCreatureLimit(), 600, 600);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new RunTimeFrozen(), 200, 200);
		
		if (config.getBoolean("dynworld.enabled", false)) {
			getServer().getScheduler().scheduleSyncRepeatingTask(this, new RunCheckWorldInactive(), config.getInt("dynworld.checkInterval", 60) * 20, config.getInt("dynworld.checkInterval", 60) * 20);
		}
		
		getServer().getScheduler().scheduleSyncDelayedTask(this, new RunLoadAllWorlds());
		getServer().getScheduler().scheduleSyncDelayedTask(this, pm);
		
		try {
			getCommand("gate").setExecutor(new CommandHandlerGate(this));
			getCommand("gworld").setExecutor(new CommandHandlerWorld(this));
		} catch (Exception ex) {
			log.warning(getNameBrackets() + "getCommand().setExecutor() failed! Seems I got enabled by another plugin. Nag the bukkit team about this!");
		}
	}
	
	public Configuration getConfig(String fileName) {
		return getConfig(getConfigFile(fileName));
	}
	
	public Configuration getConfig(File file) {
		Configuration ret = new Configuration(file);
		ret.load();
		
		return ret;
	}
	
	public File getConfigFile(String fileName) {
		File configFile = new File(getDataFolder(), fileName);

		if (!configFile.exists()) {
			try {
				getDataFolder().mkdir();
				getDataFolder().setWritable(true);
				getDataFolder().setExecutable(true);
				
				configFile.createNewFile();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		return configFile;
	}
	
	private void setConfigDefaults() {
		config.getBoolean("dynworld.enabled", false);
		config.getInt("dynworld.checkInterval", 60);
		config.getInt("dynworld.maxInactiveTime", 300);
		
		config.getInt("biomes.desert.chanceCactus", 1);
		config.getInt("biomes.desert.chanceDeadShrub", 2);
		config.getInt("biomes.forest.chanceLakeWater", 1);
		config.getInt("biomes.forest.chanceTreeNormal", 32);
		config.getInt("biomes.forest.chanceTreeBig", 2);
		config.getInt("biomes.forest.chanceTreeBirch", 32);
		config.getInt("biomes.forest.chanceTreeRedwood", 16);
		config.getInt("biomes.forest.chanceTreeTallRedwood", 2);
		config.getInt("biomes.forest.chanceFlowerYellow", 4);
		config.getInt("biomes.forest.chanceFlowerRedRose", 4);
		config.getInt("biomes.forest.chanceGrassTall", 50);
		config.getInt("biomes.plains.chanceTreeNormal", 1);
		config.getInt("biomes.plains.chanceFlowerYellow", 10);
		config.getInt("biomes.plains.chanceFlowerRedRose", 10);
		config.getInt("biomes.plains.chanceGrassTall", 150);
		config.getInt("biomes.rainforest.chanceLakeWater", 3);
		config.getInt("biomes.rainforest.chanceTreeNormal", 28);
		config.getInt("biomes.rainforest.chanceTreeBig", 2);
		config.getInt("biomes.rainforest.chanceTreeBirch", 28);
		config.getInt("biomes.rainforest.chanceTreeRedwood", 32);
		config.getInt("biomes.rainforest.chanceTreeTallRedwood", 2);
		config.getInt("biomes.rainforest.chanceFlowerYellow", 5);
		config.getInt("biomes.rainforest.chanceFlowerRedRose", 5);
		config.getInt("biomes.rainforest.chanceGrassFern", 30);
		config.getInt("biomes.rainforest.chanceGrassTall", 70);
		config.getInt("biomes.savanna.chanceTreeNormal", 1);
		config.getInt("biomes.seasonalforest.chanceLakeWater", 2);
		config.getInt("biomes.seasonalforest.chanceTreeNormal", 32);
		config.getInt("biomes.seasonalforest.chanceTreeBig", 2);
		config.getInt("biomes.seasonalforest.chanceTreeBirch", 32);
		config.getInt("biomes.seasonalforest.chanceTreeRedwood", 28);
		config.getInt("biomes.seasonalforest.chanceTreeTallRedwood", 2);
		config.getInt("biomes.seasonalforest.chanceFlowerYellow", 4);
		config.getInt("biomes.seasonalforest.chanceFlowerRedRose", 4);
		config.getInt("biomes.seasonalforest.chanceGrassTall", 70);
		config.getInt("biomes.shrubland.chanceLakeLava", 1);
		config.getInt("biomes.shrubland.chanceTreeNormal", 3);
		config.getInt("biomes.shrubland.chanceGrassShrub", 5);
		config.getInt("biomes.swampland.chanceSugarCane", 75);
		config.getInt("biomes.swampland.chanceLakeWater", 10);
		config.getInt("biomes.taiga.chanceTreeRedwood", 4);
		config.getInt("biomes.taiga.chanceGrassTall", 2);
		config.getInt("biomes.tundra.chanceLakeWater", 1);
		
		config.save();
	}

	public boolean onCommand(CommandSender sender, Command cmd,	String commandLabel, String[] args) {
		if (cmd.getName().equalsIgnoreCase("gate")) {
			getCommand("gate").setExecutor(new CommandHandlerGate(this));
			getCommand("gate").execute(sender, commandLabel, args);
			return true;
		} else if (cmd.getName().equalsIgnoreCase("gworld")) {
			getCommand("gworld").setExecutor(new CommandHandlerWorld(this));						
			getCommand("gworld").execute(sender, commandLabel, args);
			return true;
		} else {
			return false;
		}
	}
	
	public String getNameBrackets() {
		return "[" + this.getDescription().getFullName() + "] ";
	}
	
	public SetWorld getWorlds() {
		return worlds;
	}
	
	public SetGate getGates() {
		return gates;
	}
	
	public PluginManager getPluginManager() {
		return pm;
	}
	
	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
		for (Generator thisGen : Generator.values()) {
			if (thisGen.toString().equalsIgnoreCase(id)) {
				return thisGen.getChunkGenerator(this);
			}
		}

		return null;
	}
}
