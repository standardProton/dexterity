package me.c7dev.dexterity;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;

import me.c7dev.dexterity.api.DexRotation;
import me.c7dev.dexterity.api.DexterityAPI;
import me.c7dev.dexterity.command.DexterityCommand;
import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.displays.animation.SitAnimation;
import me.c7dev.dexterity.util.AxisPair;
import me.c7dev.dexterity.util.ClickedBlockDisplay;
import me.c7dev.dexterity.util.DexBlock;
import me.c7dev.dexterity.util.DexTransformation;
import me.c7dev.dexterity.util.DexUtils;
import me.c7dev.dexterity.util.InteractionCommand;
import me.c7dev.dexterity.util.OrientationKey;
import me.c7dev.dexterity.util.RollOffset;
import net.md_5.bungee.api.ChatColor;

/**
 * Plugin main class
 */
public class Dexterity extends JavaPlugin {
	
	private HashMap<String,DexterityDisplay> displays = new HashMap<>();
	private HashMap<UUID,DexSession> sessions = new HashMap<>();
	private HashMap<UUID,DexBlock> display_map = new HashMap<>();
	private HashMap<UUID, String> unloaded_uuids = new HashMap<>();
	private FileConfiguration lang, defaultLang;
	
	private String chat_color, chat_color2, chat_color3;
	private DexterityAPI api;
	private int max_volume = 25000;
	private WorldEditPlugin we = null;
	private boolean legacy = false, has_unloaded_displays = false;
	private Material wand_item;
	
	public static final String defaultLangName = "en-US.yml";
		
	@Override
	public void onEnable() {
		saveDefaultConfig();
		if (!checkIfLegacy()) {
			Bukkit.getLogger().severe("§cYour server must be on 1.19.4 or higher to be able to use Block Displays! Plugin disabled.");
			return;
		}
		api = new DexterityAPI(this);
		
		loadConfigSettings();		
		
		new DexterityCommand(this);
		new EventListeners(this);
		
		Plugin we_plugin = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
		if (we_plugin != null) we = (WorldEditPlugin) we_plugin;
		
		new BukkitRunnable() { //load post-world, once scheduler is running
			@Override
			public void run() {
				loadDisplays();
			}
		}.runTaskLater(this, 1l);
		
		File schem = new File(getDataFolder().getAbsolutePath() + "/schematics");
		if (!schem.exists()) schem.mkdirs();
		
		wand_item = Material.BLAZE_ROD;
		try {
			wand_item = Material.valueOf(getConfig().getString("wand-item").toUpperCase());
		} catch (Exception ex) {
			Bukkit.getLogger().warning("Could not find material type for Dexterity wand-item: " + getConfig().getString("wand-item"));
		}
	}
	
	@Override
	public void onDisable() {
		api.clearAllMarkers();
		saveDisplays();
	}
	
	public void loadConfigSettings() {
		chat_color = parseChatColor(getConfig().getString("primary-color"));
		chat_color2 = parseChatColor(getConfig().getString("secondary-color"));
		chat_color3 = parseChatColor(getConfig().getString("tertiary-color"));
		int config_mv = getConfig().getInt("max-selection-volume");
		if (config_mv > 0) max_volume = config_mv;
		loadLanguageFile(false);
	}
	
	public void reload() {
		api.clearAllMarkers();
		saveDisplays();
		
		reloadConfig();
		loadConfigSettings();
	}
	
	public DexterityAPI api() {
		return api;
	}
	
	public static DexterityAPI getAPI() {
		Dexterity plugin = Dexterity.getPlugin(Dexterity.class);
		return plugin.api();
	}
	
	public String getChatColor() {
		return chat_color;
	}
	public String getChatColor2() {
		return chat_color2;
	}
	public String getChatColor3() {
		return chat_color3;
	}
	
	public int getMaxVolume() {
		return max_volume;
	}
	
	public boolean usingWorldEdit() {
		return we != null;
	}
	
	public WorldEditPlugin getWorldEdit() {
		return we;
	}
	
	public Region getSelection(Player p) {
		if (we == null) return null;
		try {
			return we.getSession(p).getSelection();
		} catch (Exception ex) {
			return null;
		}
	}
	
	public boolean isLegacy() {
		return legacy;
	}
	
	private boolean checkIfLegacy() {
		Pattern verpattern = Pattern.compile("\\(MC: (.*)\\)");
		Matcher matcher = verpattern.matcher(Bukkit.getVersion());
		if (matcher.find()) {
			String[] version = matcher.group(1).split("\\.");
			if (version.length > 2) {
				int vernum = Integer.parseInt(version[1]);
				int sub = Integer.parseInt(version[2]);
				if (vernum < 19 || (vernum == 19 && sub < 4)) return false;
				legacy = (vernum < 20 || (vernum == 20 && sub < 2));
			}
		}
		return true;
	}
	
	public <T extends Entity> T spawn(Location loc, Class<T> type, Consumer<T> c) {
		T entity;
		World w = loc.getWorld();
		if (legacy) { //backwards compatability with MC versions 1.20.1 and below
			entity = w.spawn(loc, type);
			c.accept(entity);
		} else {
			entity = w.spawn(loc, type, c);
			if (entity instanceof Display) {
				Display bd = (Display) entity;
				bd.setTeleportDuration(DexBlock.TELEPORT_DURATION);
			}
		}
		return entity;
	}
	
	private String parseChatColor(String s) {
		if (s.startsWith("#")) return ChatColor.of(s).toString();
		return s.replace('&', ChatColor.COLOR_CHAR);
	}
	
	public World getDefaultWorld() {
		return Bukkit.getServer().getWorlds().size() == 0 ? null : Bukkit.getServer().getWorlds().get(0);
	}
	
	@Deprecated
	public void setMappedDisplay(DexBlock b) { //handled by Dexterity - do not use in API
		display_map.put(b.getEntity().getUniqueId(), b);
		if (!b.getUniqueId().equals(b.getEntity().getUniqueId())) display_map.put(b.getUniqueId(), b);
	}
	
	public DexBlock getMappedDisplay(UUID block) {
		return display_map.get(block);
	}
	
	public Material getWandType() {
		return wand_item;
	}
	
	@Deprecated
	public void clearMappedDisplay(DexBlock block) { //handled by Dexterity - do not use in API
		display_map.remove(block.getEntity().getUniqueId());
		display_map.remove(block.getUniqueId());
	}
	
	public String getConfigString(String dir, String def) {
		String r = getConfigString(dir);
		return r == null ? def.replaceAll("&", "§").replaceAll("\\Q[newline]\\E", "\n") : r;
	}
	
	public String getAuthor() {
		return api.getAuthor();
	}

	public String getConfigString(String dir) {
		
		FileConfiguration use = lang;
		if (use == null) {
			if (defaultLang == null) return "§c§o[No language file loaded]";
			use = defaultLang;
		}

		String s = use.getString(dir);
		if (s == null) {
			if (defaultLang != null && use != defaultLang) s = defaultLang.getString(dir);
			if (s == null) {
				Bukkit.getLogger().warning("Could not get value from config: '" + dir + "'");
				return "§c[Language file missing '§c§o" + dir + "§r§c']";
			}
			
			Bukkit.getLogger().warning("Language file is missing '" + dir + "', using the value from the default instead.");
		}
		return s
				.replaceAll("\\Q&^\\E", chat_color)
				.replaceAll("\\Q&**\\E", chat_color3)
				.replaceAll("\\Q&*\\E", chat_color2)
				.replace('&', ChatColor.COLOR_CHAR)
				.replaceAll("\\Q[newline]\\E", "\n")
				.replaceAll("\\n", "\n");
	}
	
	private void loadLanguageFile(boolean default_lang) {
		String langName;
		if (default_lang) langName = defaultLangName;
		else {
			langName = getConfig().getString("lang-file");
			if (langName == null) {
				langName = defaultLangName;
				Bukkit.getLogger().warning("No language file specified in config, loading default.");
			}
			if (!langName.contains(".")) langName += ".yml";
		}

		//load file
		String dir = this.getDataFolder().getAbsolutePath() + "/" + langName;
		try {
			File f = new File(dir);
			if (f.exists()) lang = YamlConfiguration.loadConfiguration(f);
			else Bukkit.getLogger().warning("Could not find language file '" + langName + "'!");
		} catch (Exception ex) {
			ex.printStackTrace();
			Bukkit.getLogger().severe("Could not load the language file!");
		}
		
		try {
			String langPath = "";
			String[] pathSplit = dir.split("/");
			for (int i = 0; i < pathSplit.length - 1; i++) langPath += pathSplit[i] + "/";

			File df1 = new File(langPath + "/" + defaultLangName);
			if (df1.exists()) {
				defaultLang = YamlConfiguration.loadConfiguration(new InputStreamReader(getResource(defaultLangName)));
			} else { 
				//from scratch
				saveResource(defaultLangName, false);
				File df2 = new File(this.getDataFolder().getAbsolutePath() + "/" + defaultLangName);
				defaultLang = YamlConfiguration.loadConfiguration(df2);
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			Bukkit.getLogger().severe("Could not load the default language file!");
		}
	}
	
	/**
	 * Reload the files in the saved displays folder
	 */
	public void reloadDisplays() {
		saveDisplays();
		loadDisplays();
	}
	
	public File getDisplayFile(String label) {
		return new File(this.getDataFolder().getAbsolutePath() + "/displays/" + label + ".yml");
	}
	
	/**
	 * Loads all saved displays
	 * @return The number of displays that were just loaded
	 */
	private int loadDisplays() {
		File folder = new File(this.getDataFolder().getAbsolutePath() + "/displays/");
		if (!folder.exists()) {
			folder.mkdirs();
			return 0;
		}
		
		displays.clear();
		sessions.clear();
		int display_curr_size = displays.size();
		
		try {
			
			for (File f : folder.listFiles()) loadDisplay(f, true);
			return displays.size() - display_curr_size;
		} catch (Exception ex) {
			ex.printStackTrace();
			Bukkit.getLogger().severe("Could not load Dexterity displays!");
			return 0;
		}
	}
	
	private void loadDisplay(File f, boolean verbose) {
		if (f == null || !f.getName().endsWith(".yml")) return;
		String label = f.getName().replaceAll("\\.yml", "");
		
		FileConfiguration afile = YamlConfiguration.loadConfiguration(f);

		//load entities by uuid
		List<BlockDisplay> blocks = new ArrayList<>();
		boolean missing_blocks = false;
		for (String uuid_str : afile.getStringList("uuids")) {
			UUID uuid = UUID.fromString(uuid_str);
			Entity entity = Bukkit.getEntity(uuid);
			
			if (entity != null && entity instanceof BlockDisplay) {
				blocks.add((BlockDisplay) entity);
			} else {
				unloaded_uuids.put(uuid, label);
				has_unloaded_displays = true;
				missing_blocks = true;
			}
		}
		if (missing_blocks) {
			if (verbose) Bukkit.getLogger().warning("Some of the entities for display '" + label + "' are unloaded!");
			return;
		}
		
		//basic metadata
		Location center = DexUtils.deserializeLocation(afile, "center");
		double sx = afile.getDouble("scale-x");
		double sy = afile.getDouble("scale-y");
		double sz = afile.getDouble("scale-z");
		float base_yaw = (float) afile.getDouble("yaw");
		float base_pitch = (float) afile.getDouble("pitch");
		float base_roll = (float) afile.getDouble("roll");
		Vector scale = new Vector(sx == 0 ? 1 : sx, sy == 0 ? 1 : sy, sz == 0 ? 1 : sz);
		DexterityDisplay disp = new DexterityDisplay(this, center, scale, label);
		disp.setBaseRotation(base_yaw, base_pitch, base_roll);
		
		for (BlockDisplay bd : blocks) {
			disp.addBlock(new DexBlock(bd, disp));
		}
		
		//get click commands
		ConfigurationSection cmd_section = afile.getConfigurationSection("commands");
		if (cmd_section != null) {
			for (String key : cmd_section.getKeys(false)) {
				disp.addCommand(new InteractionCommand(afile.getConfigurationSection("commands." + key)));
			}
		}
		
		//seat
		double seat_y_offset = afile.getDouble("seat-offset", Double.MAX_VALUE);
		if (seat_y_offset < Double.MAX_VALUE) {
			SitAnimation a = new SitAnimation(disp);
			if (seat_y_offset != 0) a.setSeatOffset(new Vector(0, seat_y_offset, 0));
			disp.addAnimation(a);
		}
		
		//display drop item
		String item_schem = afile.getString("item-schem-name");
		if (item_schem != null) disp.setDropItem(afile.getItemStack("item"), item_schem);
		
		//display owners
		List<String> owner_uuids = afile.getStringList("owners");
		if (owner_uuids != null && owner_uuids.size() > 0) {
			List<OfflinePlayer> owners = new ArrayList<>();
			for (String u : owner_uuids) {
				OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(u));
				if (op == null || op.getName() == null) continue;
				owners.add(op);
			}
			disp.setOwners(owners);
		}
		
		new BukkitRunnable() {
			@Override
			public void run() {
				HashMap<OrientationKey, RollOffset> cache = new HashMap<>();
				for (DexBlock db : disp.getBlocks()) {
					db.loadRoll(cache);
				}
			}
		}.runTaskAsynchronously(this);

		//set parent display
		String parent_label = afile.getString("parent");
		if (parent_label != null) {
			DexterityDisplay parent = getDisplay(parent_label);
			if (parent == null && verbose) Bukkit.getLogger().severe("Could not find parent display '" + parent_label + "'!");
			else {
				parent.addSubdisplay(disp);
				disp.setParent(parent);
			}
		}

		displays.put(disp.getLabel(), disp);
	}
	
	public void saveDisplays() {
		try {
			for (DexterityDisplay disp : getDisplays()) {
				saveDisplay(disp);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public void saveDisplay(DexterityDisplay disp) {
		
		if (!disp.isSaved() || disp.getLabel().length() == 0 || disp.getBlocksCount() == 0) return;
		
		File f = new File(this.getDataFolder().getAbsoluteFile() + "/displays/" + disp.getLabel() + ".yml");
		try {
			if (!f.exists()) f.createNewFile();
		} catch (Exception ex) {
			ex.printStackTrace();
			Bukkit.getLogger().severe("Could not save display " + disp.getLabel() + "!");
			return;
		}
		FileConfiguration afile = YamlConfiguration.loadConfiguration(f);
		for (String s : afile.getKeys(false)) afile.set(s, null);
		
		//basic metadata
		afile.set("center", disp.getCenter().serialize());
		if (disp.getScale().getX() != 1) afile.set("scale-x", disp.getScale().getX());
		if (disp.getScale().getY() != 1) afile.set("scale-y", disp.getScale().getY());
		if (disp.getScale().getZ() != 1) afile.set("scale-z", disp.getScale().getZ());
		
		SitAnimation seat = (SitAnimation) disp.getAnimation(SitAnimation.class);
		if (seat != null) afile.set("seat-offset", seat.getSeatOffset().getY());
		
		OfflinePlayer[] owners = disp.getOwners();
		if (owners.length > 0) {
			List<String> uuids = new ArrayList<>();
			for (OfflinePlayer owner : owners) uuids.add(owner.getUniqueId().toString());
			afile.set("owners", uuids);
		}

		if (disp.getRotationManager() != null) {
			DexRotation rot = disp.getRotationManager();
			AxisPair a = new AxisPair(rot.getXAxis(), rot.getZAxis());
			Vector res = a.getPitchYawRoll();
			if (res.getY() != 0) afile.set("yaw", res.getY());
			if (res.getX() != 0) afile.set("pitch", res.getX());
			if (res.getZ() != 0) afile.set("roll", res.getZ());
		}
		
		//click commands
		if (disp.getCommandCount() > 0) {
			afile.set("commands", null);
			InteractionCommand[] cmds = disp.getCommands();
			for (int i = 0; i < cmds.length; i++) {
				afile.set("commands.cmd-" + (i+1), cmds[i].serialize());
			}
		}
		
		//drop item
		ItemStack item = disp.getDropItem();
		if (item != null) {
			afile.set("item-schem-name", disp.getDropItemSchematicName());
			afile.set("item", item);
		}
		
		//save block uuids
		List<String> uuids = new ArrayList<>();
		DexBlock[] blocks = disp.getBlocks();
		if (blocks.length > 0) {
			for (DexBlock db : disp.getBlocks()) uuids.add(db.getEntity().getUniqueId().toString());
			afile.set("uuids", uuids);
		} else {
			Bukkit.getLogger().warning("JAR modified, skipping save of " + disp.getLabel());
			return;
		}
				
		if (disp.getParent() != null) afile.set("parent", disp.getParent().getLabel());
		
		try {
			afile.save(f);
		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.getLogger().severe("Could not save '" + disp.getLabel() + "' display!");
		}
		
		for (DexterityDisplay sub : disp.getSubdisplays()) saveDisplay(sub);
	}
	
	/**
	 * Searches for missing block displays once the chunk has been loaded by a player
	 * @param c
	 */
	public void processUnloadedDisplaysInChunk(Chunk c) {
		if (!has_unloaded_displays) return;
		
		Set<String> unloaded_labels = new HashSet<>();
		for (Entity entity : c.getEntities()) {
			if (!(entity instanceof BlockDisplay)) continue;
			String label = unloaded_uuids.get(entity.getUniqueId());
			if (label != null && !unloaded_labels.contains(label)) unloaded_labels.add(label);
		}
		
		for (String label : unloaded_labels) { //possible displays that can now be loaded
			File f = getDisplayFile(label);
			loadDisplay(f, true); //won't load if not all displays are there
			DexterityDisplay d = getDisplay(label);
			if (d != null) {
				for (DexBlock db : d.getBlocks()) unloaded_uuids.remove(db.getEntity().getUniqueId());
			}
		}
		
		if (unloaded_uuids.size() == 0) has_unloaded_displays = false;
	}
	
	/**
	 * Returns true if it is possible that there is a display that is not loaded due to the server requiring a player to load the chunk's entities
	 * @return
	 */
	public boolean hasUnloadedDisplays() {
		return has_unloaded_displays;
	}
	
	/**
	 * Deletes unneeded display files that do not have any blocks loaded
	 * @return The number of files that were purged
	 */
	public int purgeUnloadedDisplays() {
		int count = 0;
		
		List<DexterityDisplay> allRootNodes = new LinkedList<>();
		for (Entry<String,DexterityDisplay> entry : displays.entrySet()) {
			if (entry.getValue().getParent() != null) continue;
			allRootNodes.add(entry.getValue());
		}
		for (DexterityDisplay disp : allRootNodes) {
			if (purgeHelper(disp)) count++;
		}
		
		has_unloaded_displays = false;
		List<String> unique_labels = new ArrayList<>();
		unloaded_uuids.entrySet().forEach(e -> {
			if (!unique_labels.contains(e.getValue())) unique_labels.add(e.getValue());
		});
		unloaded_uuids.clear();
		for (String label : unique_labels) {
			File f = getDisplayFile(label);
			try {
				if (f != null) {
					f.delete();
					count++;
				}
			} catch (Exception ex) {
				
			}
		}
		
		Bukkit.getLogger().warning("Purged " + count + " saved display files that could not be loaded");
		return count;
	}
	
	private boolean purgeHelper(DexterityDisplay d) {
		for (DexBlock db : d.getBlocks()) if (Bukkit.getEntity(db.getUniqueId()) != null) return false;
		
		boolean r = false;
		if (d.getSubdisplayCount() == 0) { //don't remove parent displays even if they have no blocks
			d.remove(false);
			r = true;
		}
		else {
			for (DexterityDisplay sub : d.getSubdisplays()) r = purgeHelper(sub) || r;
		}
		return r;
	}
	
	/**
	 * Maps the label to the display. For API use, see {@link DexterityDisplay#setLabel(String)}
	 */
	public void registerDisplay(String label, DexterityDisplay d) {
		if (label == null || d == null) throw new IllegalArgumentException("Parameters cannot be null!");
		if (displays.containsKey(label) && displays.get(label) != d) return;
		displays.put(label, d);
		saveDisplay(d);
	}
	
	/**
	 * Unmaps the label to the display. For API use, see {@link DexterityDisplay#setLabel(String)} for null label
	 */
	public void unregisterDisplay(DexterityDisplay d) {
		unregisterDisplay(d, false);
	}
	
	/**
	 * Unmaps the label to the display. For API use, see {@link DexterityDisplay#setLabel(String)} for null label
	 */
	public void unregisterDisplay(DexterityDisplay d, boolean from_merge) {
		if (!d.isSaved()) return;
		displays.remove(d.getLabel());
		
		try {
			File f = new File(this.getDataFolder().getAbsolutePath() + "/displays/" + d.getLabel() + ".yml");
			if (f.exists()) f.delete();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public String getNextLabel(String s) {
		if (!displays.containsKey(s)) return s;
		return getNextLabelHelper(s, 1);
	}
	
	private String getNextLabelHelper(String s, int num) {
		if (!displays.containsKey(s + "-" + num)) return s + "-" + num;
		return getNextLabelHelper(s, num+1);
	}
	
	/**
	 * Emulates how a block display can be placed on one of the faces of another block display, such as when a player clicks a block.
	 * @param clicked
	 * @param place_data
	 * @return
	 */
	public BlockDisplay putBlock(ClickedBlockDisplay clicked, BlockData place_data) {
		DexBlock clicked_db = this.getMappedDisplay(clicked.getBlockDisplay().getUniqueId());
		DexterityDisplay clicked_display = null;
		if (clicked_db != null) clicked_display = clicked_db.getDexterityDisplay();
		
		Vector placingDimensions = DexUtils.getBlockDimensions(place_data);

		Vector blockscale = DexUtils.vector(clicked.getBlockDisplay().getTransformation().getScale());
		Vector blockdimensions = DexUtils.getBlockDimensions(clicked.getBlockDisplay().getBlock());

		//calculate dimensions of clicked block display
		Vector avgPlaceDimensions;
		if (clicked.getBlockFace() == BlockFace.DOWN) {
			avgPlaceDimensions = blockdimensions.clone().multiply(0.5).add(placingDimensions).add(new Vector(-0.5, -0.5, -0.5));
		}
		else {
			placingDimensions.setY(1); //account for block's y axis asymmetry
			avgPlaceDimensions = blockdimensions.clone().add(placingDimensions).multiply(0.5);
		}

		Vector offset = DexUtils.hadimard(blockscale, avgPlaceDimensions);

		Vector dir = clicked.getNormal();
		Vector delta = dir.clone().multiply(DexUtils.faceToDirectionAbs(clicked.getBlockFace(), offset));

		DexTransformation trans = (clicked_db == null ? new DexTransformation(clicked.getBlockDisplay().getTransformation()) : clicked_db.getTransformation());

		Location fromLoc = clicked.getDisplayCenterLocation();
		if (clicked.getBlockFace() != BlockFace.UP && clicked.getBlockFace() != BlockFace.DOWN) fromLoc.add(clicked.getUpDir().multiply((blockscale.getY()/2)*(1 - blockdimensions.getY())));


		BlockDisplay b = this.spawn(fromLoc.clone().add(delta), BlockDisplay.class, a -> {
			a.setBlock(place_data);
			trans.setScale(blockscale);
			if (clicked.getRollOffset() == null) trans.setDisplacement(blockscale.clone().multiply(-0.5));
			else trans.setDisplacement(blockscale.clone().multiply(-0.5).add(clicked.getRollOffset().getOffset()));
			a.setTransformation(trans.build());
		});

		if (clicked_display != null) {
			DexBlock new_db = new DexBlock(b, clicked_display, clicked_db.getRoll());
			new_db.getTransformation().setDisplacement(new_db.getTransformation().getDisplacement().subtract(clicked_db.getTransformation().getRollOffset()));
			new_db.getTransformation().setRollOffset(clicked_db.getTransformation().getRollOffset().clone());
			clicked_display.addBlock(new_db);
		}
		return b;
	}
	
	//////////////////////////////////////////////////////////
	
	public Set<String> getDisplayLabels(){
		return displays.keySet();
	}
	
	public Set<String> getDisplayLabels(Player p){
		Set<String> r = new HashSet<>();
		for (Entry<String,DexterityDisplay> entry : displays.entrySet()) {
			if (entry.getValue().hasOwner(p)) r.add(entry.getKey());
		}
		return r;
	}
	
	public Collection<DexterityDisplay> getDisplays() {
		Collection<DexterityDisplay> r = new ArrayList<>();
		for (Entry<String, DexterityDisplay> entry : displays.entrySet()) {
			if (entry.getValue().getParent() == null) r.add(entry.getValue());
		}
		return r;
	}
	
	public DexterityDisplay getDisplay(String label) {
		if (!displays.containsKey(label)) return null;
		return displays.get(label);
	}
	
	public Set<Entry<UUID, DexSession>> editSessionIter() {
		return sessions.entrySet();
	}
	
	public DexSession getEditSession(UUID u) {
		DexSession s = sessions.get(u);
		if (s == null) {
			Player p = Bukkit.getPlayer(u);
			if (p == null) return null;
			s = new DexSession(p, this);
		}
		return s;
	}
	
	public void deleteEditSession(UUID u) {
		DexSession session = sessions.get(u);
		if (session == null) return;
		session.removeAxes();
		sessions.remove(u);
	}
	
	public void setEditSession(UUID u, DexSession s) {
		deleteEditSession(u);
		sessions.put(u, s);
	}
	

}
