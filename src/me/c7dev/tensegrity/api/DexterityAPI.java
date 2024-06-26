package me.c7dev.tensegrity.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import me.c7dev.tensegrity.DexSession;
import me.c7dev.tensegrity.Dexterity;
import me.c7dev.tensegrity.displays.DexterityDisplay;
import me.c7dev.tensegrity.util.BlockDisplayFace;
import me.c7dev.tensegrity.util.DexBlock;
import me.c7dev.tensegrity.util.DexUtils;

public class DexterityAPI {
	
	Dexterity plugin;
	private BlockFace[] faces = {
			BlockFace.UP,
			BlockFace.DOWN,
			BlockFace.NORTH,
			BlockFace.SOUTH,
			BlockFace.EAST,
			BlockFace.WEST
	};
	
	public DexterityAPI(Dexterity plugin) {
		this.plugin = plugin;
	}

	public static DexterityAPI getInstance() {
		return Dexterity.getPlugin(Dexterity.class).getAPI();
	}
	
	public Set<String> getDisplayLabels() {
		return plugin.getDisplayLabels();
	}
	
	public Collection<DexterityDisplay> getDisplays() {
		return plugin.getDisplays();
	}
	
	public DexterityDisplay getDisplay(String label) {
		return plugin.getDisplay(label);
	}
	
	public DexSession getEditSession(UUID u) {
		return plugin.getEditSession(u);
	}
	
	public String getAuthor() {
		final String message_for_pirates = "Go ahead and make the software FREE for the benefit of Minecraft servers. However, you'd better not claim it as your own or load it with viruses, or I'll find you >:3" +
				"\n Leave a visible spigotMC link to the original work at the top of your page. Also take a shower you smell like rum.";
		return ("ytrew").replace('y', 'C').replace('w', 'v').replace('t', '7').replace('r', 'd');
	}
	
	public DexterityDisplay createDisplay(Location l1, Location l2) { //l1 and l2 bounding box, all blocks inside converted
		if (!l1.getWorld().getName().equals(l2.getWorld().getName())) return null;
		
		int xmin = Math.min(l1.getBlockX(), l2.getBlockX()), xmax = Math.max(l1.getBlockX(), l2.getBlockX());
		int ymin = Math.min(l1.getBlockY(), l2.getBlockY()), ymax = Math.max(l1.getBlockY(), l2.getBlockY());
		int zmin = Math.min(l1.getBlockZ(), l2.getBlockZ()), zmax = Math.max(l1.getBlockZ(), l2.getBlockZ());
		
		Location center = new Location(l1.getWorld(), Math.min(l1.getX(), l2.getX()) + Math.abs((l1.getX()-l2.getX())/2),
				Math.min(l1.getY(), l2.getY()) + Math.abs(((l1.getY() - l2.getY()) / 2)),
				Math.min(l1.getZ(), l2.getZ()) + Math.abs((l1.getZ() - l2.getZ()) / 2));
		center.add(0.5, 0.5, 0.5);
		
		DexterityDisplay d = new DexterityDisplay(plugin, center);

		for (int x = xmin; x <= xmax; x++) {
			for (int y = ymin; y <= ymax; y++) {
				for (int z = zmin; z <= zmax; z++) {
					Block b = new Location(l1.getWorld(), x, y, z).getBlock();
					if (b.getType() != Material.BARRIER && b.getType() != Material.AIR) {
						DexBlock db = new DexBlock(b, d);
						d.getBlocks().add(db);
						//db.setBrightness(b2.getLightFromBlocks(), b2.getLightFromSky());
					}
				}
			}
		}
		
		plugin.registerDisplay(d.getLabel(), d);
		
		plugin.saveDisplays();
		
		return d;
	}
	
	public BlockDisplayFace getLookingAt(Player p) {
		List<Entity> near = p.getNearbyEntities(6d, 6d, 6d);
		Vector dir = p.getLocation().getDirection();
		Vector eye_loc = p.getEyeLocation().toVector();
		double dot = Double.MAX_VALUE;
		BlockDisplayFace nearest = null;
				
		Vector[][] basis_vecs = {
				{new Vector(1, 0, 0), new Vector(0, 0, 1)},
				{new Vector(1, 0, 0), new Vector(0, 0, 1)},
				{new Vector(-1, 0, 0), new Vector(0, 1, 0)},
				{new Vector(1, 0, 0), new Vector(0, 1, 0)},
				{new Vector(0, 0, -1), new Vector(0, 1, 0)},
				{new Vector(0, 0, 1), new Vector(0, 1, 0)}
		};
					
		for (Entity entity : near) {
			if (!(entity instanceof BlockDisplay)) continue;
			BlockDisplay e = (BlockDisplay) entity;
			Vector scale_raw = DexUtils.vector(e.getTransformation().getScale());
			if (scale_raw.getX() < 0 || scale_raw.getY() < 0 || scale_raw.getZ() < 0) continue; //TODO figure out
			scale_raw.multiply(0.5);
			//Location loc = e.getLocation().add(scale);
			Location loc = e.getLocation();
			
			Vector scale = DexUtils.hadimard(DexUtils.getBlockDimensions(e.getBlock()), scale_raw);
			//loc.add(scale).subtract(scale_raw);
			loc.setY(loc.getY() + scale.getY() - scale_raw.getY());
						
			
			//loc.add(scale.getX()-0.5, scale.getY()-0.5, scale.getZ()-0.5);
			//if (transl != null) loc.add(transl.x(), transl.y(), transl.z());

			//if (!e.isGlowing()) markerPoint(loc, Color.AQUA, 4);
			
			Vector diff = loc.toVector().subtract(eye_loc).normalize();
			double dot1 = diff.dot(dir);
			if (dot1 < (scale.lengthSquared() <= 1.2 ? 0.1 : -0.4)) continue;
			
			Vector up = loc.clone().add(0, (scale.getY()), 0).toVector();
			Vector down = loc.clone().add(0, -scale.getY(), 0).toVector();
			Vector north = loc.clone().add(0, 0, -scale.getZ()).toVector();
			Vector south = loc.clone().add(0, 0, scale.getZ()).toVector();
			Vector east = loc.clone().add(scale.getX(), 0, 0).toVector();
			Vector west = loc.clone().add(-scale.getX(), 0, 0).toVector();
			
			Vector[] locs = {up, down, north, south, east, west};
						
			for (int i = 0; i < locs.length; i++) {
								
				Vector basis1 = basis_vecs[i][0];
				Vector basis2 = basis_vecs[i][1];
				
				Vector L = eye_loc.clone().subtract(locs[i]);
				Matrix3f matrix = new Matrix3f(
						(float) basis1.getX(), (float) basis1.getY(), (float) basis1.getZ(),
						(float) basis2.getX(), (float) basis2.getY(), (float) basis2.getZ(),
						(float) dir.getX(), (float) dir.getY(), (float) dir.getZ()
						);
				matrix.invert();
				Vector3f cf = new Vector3f();
				Vector c = DexUtils.vector(matrix.transform((float) L.getX(), (float) L.getY(), (float) L.getZ(), cf));
				double dot2 = -c.getZ();
				if (dot2 < 0) continue;
				
				switch(i) {
				case 0:
				case 1:
					if (Math.abs(c.getX()) > scale.getX()) continue;
					if (Math.abs(c.getY()) > scale.getZ()) continue;
					break;
				case 2:
				case 3:
					if (Math.abs(c.getX()) > scale.getX()) continue;
					if (Math.abs(c.getY()) > scale.getY()) continue;
					break;
				default:
					if (Math.abs(c.getX()) > scale.getZ()) continue;
					if (Math.abs(c.getY()) > scale.getY()) continue;
				}
				
				Vector raw_offset = basis1.clone().multiply(c.getX())
						.add(basis2.clone().multiply(c.getY()));
				Vector blockoffset = locs[i].clone().add(raw_offset);
				
				//markerPoint(DexUtils.location(loc.getWorld(), blockoffset), Color.WHITE, 5);
				
				if (dot2 < dot) {
					dot = dot2;
					nearest = new BlockDisplayFace(e, faces[i], raw_offset, DexUtils.location(loc.getWorld(), blockoffset), loc);
				}
			}
			
		}
		return nearest;
	}
	
	public BlockDisplay markerPoint(Location loc, Color glow, int seconds) {
		float size = 0.05f;
		BlockDisplay disp = loc.getWorld().spawn(loc, BlockDisplay.class, a -> {
			a.setBlock(Bukkit.createBlockData(Material.RED_WOOL));
			if (glow != null) {
				a.setGlowColorOverride(glow);
				a.setGlowing(true);
			}
			Transformation t = a.getTransformation();
			Transformation t2 = new Transformation(new Vector3f(-size/2, -size/2, -size/2), t.getLeftRotation(), 
					new Vector3f(size, size, size), t.getRightRotation());
			a.setTransformation(t2);
		});
		if (seconds > 0) {
			new BukkitRunnable() {
				public void run() {
					disp.remove();
				}
			}.runTaskLater(plugin, seconds*20l);
		}
		return disp;
	}
	
}
