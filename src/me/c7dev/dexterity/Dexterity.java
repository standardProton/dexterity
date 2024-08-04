package me.c7dev.dexterity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;

import me.c7dev.dexterity.api.DexRotation;
import me.c7dev.dexterity.api.DexterityAPI;
import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.util.AxisPair;
import me.c7dev.dexterity.util.DexBlock;
import me.c7dev.dexterity.util.DexUtils;
import me.c7dev.dexterity.util.OrientationKey;
import me.c7dev.dexterity.util.RollOffset;
import net.md_5.bungee.api.ChatColor;

public class Dexterity extends JavaPlugin {
	
	private HashMap<String,DexterityDisplay> displays = new HashMap<>();
	private HashMap<String,DexterityDisplay> all_displays = new HashMap<>();
	private HashMap<UUID,DexSession> sessions = new HashMap<>();
	private HashMap<UUID,DexBlock> display_map = new HashMap<>();
	private FileConfiguration lang, defaultLang;
	
	private String chat_color, chat_color2, chat_color3;
	private DexterityAPI api;
	private int max_volume = 25000;
	private WorldEditPlugin we = null;
		
	@Override
	public void onEnable() {
		saveDefaultConfig();
		api = new DexterityAPI(this);
		
		loadConfigSettings();
		
		new DexterityCommand(this);
		new EventListeners(this);
		
		
		Plugin we_plugin = Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
		if (we_plugin != null) we = (WorldEditPlugin) we_plugin;
		
		new BukkitRunnable() {
			@Override
			public void run() {
				loadDisplays();				
			}
		}.runTask(this);
	}
	
	@Override
	public void onDisable() {
		saveDisplays();
	}
	
	public void loadConfigSettings() {
		chat_color = parseChatColor(getConfig().getString("primary-color"));
		chat_color2 = parseChatColor(getConfig().getString("secondary-color"));
		chat_color3 = parseChatColor(getConfig().getString("tertiary-color"));
		int config_mv = getConfig().getInt("max-selection-volume");
		if (config_mv > 0) max_volume = config_mv;
		loadLanguageFile(false);
		//TODO wand item type
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
	
	private String parseChatColor(String s) {
		if (s.startsWith("#")) return ChatColor.of(s).toString();
		return s.replace('&', ChatColor.COLOR_CHAR);
	}
	
	public World getDefaultWorld() {
		return Bukkit.getServer().getWorlds().size() == 0 ? null : Bukkit.getServer().getWorlds().get(0);
	}
	
	@Deprecated
	public void setMappedDisplay(DexBlock b) {
		display_map.put(b.getEntity().getUniqueId(), b);
	}
	public DexBlock getMappedDisplay(UUID block) {
		return display_map.get(block);
	}
	@Deprecated
	public void clearMappedDisplay(UUID block) {
		display_map.remove(block);
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
			Bukkit.getLogger().warning("Could not get value from config: '" + dir + "'");
			return "§c[Language file missing '§c§o" + dir + "§r§c']";
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
		String defaultName = "en-US.yml";
		if (default_lang) langName = defaultName;
		else {
			langName = getConfig().getString("lang-path");
			if (langName == null) {
				langName = defaultName;
				Bukkit.getLogger().warning("No language file specified in config, loading default.");
			}
			if (!langName.contains(".")) langName += ".yml";
		}

		String dir = this.getDataFolder().getAbsolutePath() + "/" + langName;
		try {
			File f = new File(dir);
			if (f.exists()) lang = YamlConfiguration.loadConfiguration(f);
			else Bukkit.getLogger().warning("Could not find language file '" + langName + "'!");
		} catch (Exception ex) {
			ex.printStackTrace();
			Bukkit.getLogger().severe("Could not load the language file!");
		}
		
		if (!langName.equals(defaultName) || lang == null) {
			try {
				String langPath = "";
				String[] pathSplit = dir.split("/");
				for (int i = 0; i < pathSplit.length - 1; i++) langPath += pathSplit[i] + "/";

				File df1 = new File(langPath + "/" + defaultName);
				if (df1.exists()) {
					defaultLang = YamlConfiguration.loadConfiguration(df1);
				} else { //from scratch
					saveResource(defaultName, false);
					File df2 = new File(this.getDataFolder().getAbsolutePath() + "/" + defaultName);
					defaultLang = YamlConfiguration.loadConfiguration(df2);
				}

			} catch (Exception ex) {
				ex.printStackTrace();
				Bukkit.getLogger().severe("Could not load the default language file!");
			}
		}
	}
	
	private void purgeHelper(DexterityDisplay d) {
		if (d.getBlocks().size() > 0) return;
		if (d.getSubdisplays().size() == 0) d.remove(false);
		else {
			for (DexterityDisplay sub : d.getSubdisplays()) purgeHelper(sub);
		}
	}
	
	private int loadDisplays() { //load from displays.yml
		File folder = new File(this.getDataFolder().getAbsolutePath() + "/displays/");
		if (!folder.exists()) {
			folder.mkdirs();
			return 0;
		}
		
		displays.clear();
		sessions.clear();
		int display_count = 0;
		
		try {
			
			for (File f : folder.listFiles()) {
				if (!f.getName().endsWith(".yml")) continue;
				String label = f.getName().replaceAll("\\.yml", "");
 			
				FileConfiguration afile = YamlConfiguration.loadConfiguration(f);

				List<BlockDisplay> blocks = new ArrayList<>();
				boolean missing_blocks = false;
				for (String uuid : afile.getStringList("uuids")) {
					Entity entity = Bukkit.getEntity(UUID.fromString(uuid));
					if (entity != null && entity instanceof BlockDisplay) {
						blocks.add((BlockDisplay) entity);
					} else missing_blocks = true;
				}
				if (missing_blocks) {
					Bukkit.getLogger().warning("Some of the blocks for display '" + label + "' are missing!");
				}

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
					disp.getBlocks().add(new DexBlock(bd, disp));
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

				String parent_label = afile.getString("parent");
				if (parent_label != null) {
					DexterityDisplay parent = getDisplay(parent_label);
					if (parent == null) Bukkit.getLogger().severe("Could not find parent display '" + parent_label + "'!");
					else {
						parent.getSubdisplays().add(disp);
						disp.setParent(parent);
					}
				}

				if (disp.getParent() == null) displays.put(disp.getLabel(), disp);
				all_displays.put(disp.getLabel(), disp);
			}

			//purge empty displays if any were loaded
			DexterityDisplay[] allLabeled = new DexterityDisplay[displays.size()];
			int i = 0;
			for (Entry<String,DexterityDisplay> entry : displays.entrySet()) {
				allLabeled[i] = entry.getValue();
				i++;
			}
			for (DexterityDisplay disp : allLabeled) purgeHelper(disp);

			return display_count;

		} catch (Exception ex) {
			ex.printStackTrace();
			Bukkit.getLogger().severe("Could not load Dexterity displays!");
			return 0;
		}
	}
	
	public void saveDisplays() {
		for (DexterityDisplay disp : getDisplays()) {
			saveDisplay(disp);
		}
	}
	
	public void saveDisplay(DexterityDisplay disp) {
		
		if (!disp.isSaved() || disp.getLabel().length() == 0) return; //TODO auto-generate label
		
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
		
		afile.set("center", disp.getCenter().serialize());
		if (disp.getScale().getX() != 1) afile.set("scale-x", disp.getScale().getX());
		if (disp.getScale().getY() != 1) afile.set("scale-y", disp.getScale().getY());
		if (disp.getScale().getZ() != 1) afile.set("scale-z", disp.getScale().getZ());
		
		if (disp.getRotationManager() != null) {
			try {
				DexRotation rot = disp.getRotationManager();
				AxisPair a = new AxisPair(rot.getXAxis(), rot.getZAxis());
				Vector res = a.getPitchYawRoll();
				if (res.getY() != 0) afile.set("yaw", res.getY());
				if (res.getX() != 0) afile.set("pitch", res.getX());
				if (res.getZ() != 0) afile.set("roll", res.getZ());
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		List<String> uuids = new ArrayList<>();
		for (DexBlock db : disp.getBlocks()) uuids.add(db.getEntity().getUniqueId().toString());
		afile.set("uuids", uuids);
				
		if (disp.getParent() != null) afile.set("parent", disp.getParent().getLabel());
		
		try {
			afile.save(f);
		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.getLogger().severe("Could not save '" + disp.getLabel() + "' display!");
		}
		
		for (DexterityDisplay sub : disp.getSubdisplays()) saveDisplay(sub);
	}
	
	public void registerDisplay(String label, DexterityDisplay d) {
		if (label == null || d == null) throw new IllegalArgumentException("Parameters cannot be null!");
		if (all_displays.containsKey(label) && all_displays.get(label) != d) return;
		if (d.getParent() == null) displays.put(label, d);
		all_displays.put(label, d);
		saveDisplay(d);
	}
	
	public void unregisterDisplay(DexterityDisplay d) {
		unregisterDisplay(d, false);
	}
	
	public void unregisterDisplay(DexterityDisplay d, boolean from_merge) {
		if (!d.isSaved()) return;
		if (!from_merge) all_displays.remove(d.getLabel());
		displays.remove(d.getLabel());
		
		try {
			File f = new File(this.getDataFolder().getAbsolutePath() + "/displays/" + d.getLabel() + ".yml");
			if (f.exists()) f.delete();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	//////////////////////////////////////////////////////////
	
	public Set<String> getDisplayLabels(){
		return all_displays.keySet();
		//return new ArrayList<DexterityDisplay>(displays.values());
	}
	
	public Collection<DexterityDisplay> getDisplays() {
		return displays.values();
	}
	
	public DexterityDisplay getDisplay(String label) {
		if (!all_displays.containsKey(label)) return null;
		return all_displays.get(label);
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
		sessions.remove(u);
	}
	
	public void setEditSession(UUID u, DexSession s) {
		sessions.put(u, s);
	}
	

}
