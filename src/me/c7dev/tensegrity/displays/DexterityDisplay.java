package me.c7dev.tensegrity.displays;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Matrix3d;
import org.joml.Vector3f;

import me.c7dev.tensegrity.Dexterity;
import me.c7dev.tensegrity.displays.animation.Animation;
import me.c7dev.tensegrity.displays.animation.RotationAnimation;
import me.c7dev.tensegrity.util.DexBlock;
import me.c7dev.tensegrity.util.DexUtils;
import me.c7dev.tensegrity.util.DoubleHolder;
import me.c7dev.tensegrity.util.Plane;

public class DexterityDisplay {
	
	private Dexterity plugin;
	private Location center;
	private String label;
	private Vector scale;
	private DexterityDisplay parent;
	private boolean started_animations = false;
	private UUID uuid = UUID.randomUUID(), editing_lock;
	private double yaw = 0, pitch = 0, roll = 0;
	
	private List<DexBlock> blocks = new ArrayList<>();
	private List<Animation> animations = new ArrayList<>();
	private List<DexterityDisplay> subdisplays = new ArrayList<>();
	
	public DexterityDisplay(Dexterity plugin) {
		this(plugin, null, new Vector(1, 1, 1), 0, 0);
	}
		
	public DexterityDisplay(Dexterity plugin, Location center, Vector scale, double yaw, double pitch) {
		this.plugin = plugin;
		this.scale = scale;
		this.yaw = yaw;
		this.pitch = pitch;
		if (center == null) recalculateCenter();
		else this.center = center;
	}
	
	public UUID getUniqueId() {
		return uuid;
	}
	
	public boolean equals(DexterityDisplay d) {
		return uuid.equals(d.getUniqueId());
	}
	
	public String getLabel() {
		return label;
	}
	
	public void recalculateCenter() {
		Vector cvec = new Vector(0, 0, 0);
		World w;
		int n = 0;
		if (blocks.size() == 0) {
			w = plugin.getDefaultWorld();
			n = 1;
		}
		else {
			w = blocks.get(0).getLocation().getWorld();
			n = blocks.size();
			for (DexBlock db : blocks) {
				cvec.add(db.getLocation().toVector());
			}
		}
		center = DexUtils.location(w, cvec.multiply(1.0/n));
	}
	
	public void setDefaultLabel() {
		int i =1;
		while (plugin.getDisplayLabels().contains("display-" + i)) i++;
		setLabel("display-" + i);
	}
	
	@Deprecated
	public void forceSetLabel(String s) {
		if (s == null || plugin.getDisplayLabels().contains(s)) return;
		label = s;
	}
	
	public boolean setLabel(String s) {
		if (s == null || plugin.getDisplayLabels().contains(s)) return false;
		if (label != null) plugin.unregisterDisplay(this);
		plugin.registerDisplay(s, this);
		label = s;
		plugin.saveDisplays();
		return true;
	}
		
	public boolean isListed() {
		return label != null;
	}
	
	public List<DexBlock> getBlocks(){
		return blocks;
	}

	public List<Animation> getAnimations(){
		return animations;
	}
	
	public Vector getScale() {
		return scale;
	}

	public void setEntities(List<DexBlock> entities_, boolean recalc_center){
		this.blocks = entities_;
		plugin.unregisterDisplay(this);
		recalculateCenter();
	}
	
	public List<DexterityDisplay> getSubdisplays() {
		return subdisplays;
	}
	
	public DexterityDisplay getParent() {
		return parent;
	}
	
	public void setParent(DexterityDisplay p) {
		if (p == this) {
			Bukkit.getLogger().severe("Tried to set parent to self");
			return;
		}
		parent = p;
	}
	
	public DexterityDisplay getRootDisplay() {
		return rootDisplay(this);
	}
	
	public DexterityDisplay rootDisplay(DexterityDisplay d) {
		if (d.getParent() == null) return this;
		else return rootDisplay(d.getParent());
	}
	
	public boolean containsSubdisplay(DexterityDisplay d) {
		if (subdisplays.contains(d)) return true;
		for (DexterityDisplay child : subdisplays) {
			if (child.containsSubdisplay(d)) return true;
		}
		return false;
	}
	
	public boolean canHardMerge() {
		return !hasStartedAnimations();
	}
	
	public boolean hasStartedAnimations() {
		return started_animations;
	}
	
	public boolean hardMerge(DexterityDisplay subdisplay) {
		if (!subdisplay.getCenter().getWorld().getName().equals(center.getWorld().getName()) ||
			!subdisplay.canHardMerge() || !canHardMerge()) return false;
		plugin.unregisterDisplay(subdisplay);
		for (DexBlock b : subdisplay.getBlocks()) {
			b.setDexterityDisplay(this);
			blocks.add(b);
		}
		for (DexterityDisplay subdisp : subdisplay.getSubdisplays()) {
			subdisp.merge(this, null);
		}
		return true;
	}
	
	public DexterityDisplay merge(DexterityDisplay newparent, String new_group) {
		if (newparent == this || newparent.getLabel().equals(label) || subdisplays.contains(newparent) || parent != null) return null;
		if (rootDisplay(this).containsSubdisplay(newparent)) return null;
		if (!newparent.getCenter().getWorld().getName().equals(center.getWorld().getName())) return null;
		if (new_group != null && plugin.getDisplayLabels().contains(new_group)) return null;
		if (label == null || newparent.getLabel() == null) return null;
		
		plugin.unregisterDisplay(this, true);
		stopAnimations(true);
		newparent.stopAnimations(true);
		Vector c2v = center.toVector().add(newparent.getCenter().toVector()).multiply(0.5); //midpoint
		Location c2 = new Location(center.getWorld(), c2v.getX(), c2v.getY(), c2v.getZ());
		newparent.setCenter(c2);
		
		DexterityDisplay r;
		if (new_group == null) {
			newparent.getSubdisplays().add(this);
			setParent(newparent);
			r = newparent;
		} else {
			plugin.unregisterDisplay(newparent, true);
			DexterityDisplay p = new DexterityDisplay(plugin);
			p.setLabel(new_group);
			
			setParent(p);
			newparent.setParent(p);
			p.getSubdisplays().add(this);
			p.getSubdisplays().add(newparent);
			r = p;
		}
		
		plugin.saveDisplays();
		return r;
	}
	
	public UUID getEditingLock() {
		return editing_lock;
	}
	
	public void setEditingLock(UUID u) {
		editing_lock = u;
	}
	
	public void unmerge() {
		if (parent == null) return;
		parent.getSubdisplays().remove(this);
		parent = null;
		plugin.registerDisplay(label, this);
		plugin.saveDisplays();
	}
	
	public void remove(boolean restore) {
		if (parent != null) parent.getSubdisplays().remove(this);
		removeHelper(restore);
		plugin.saveDisplays();
	}
	
	private void removeHelper(boolean restore) {
		for (DexBlock b : blocks) {
			if (restore) {
				Location loc = DexUtils.blockLoc(b.getEntity().getLocation());
				loc.getBlock().setBlockData(b.getEntity().getBlock());
			}
			b.getEntity().remove();
		}
		
		plugin.unregisterDisplay(this);
		
		for (DexterityDisplay subdisplay : subdisplays.toArray(new DexterityDisplay[subdisplays.size()])) subdisplay.remove(restore);
	}
	
	public int getGroupSize() {
		if (label == null) return 1;
		return getGroupSize(this);
	}
	public int getGroupSize(DexterityDisplay s) {
		int i = 1;
		for (DexterityDisplay sub : s.getSubdisplays()) i += getGroupSize(sub);
		return i;
	}
	
	public Dexterity getPlugin() {
		return plugin;
	}
	
	public Location getCenter() {
		return center.clone();
	}
	
	public void setCenter(Location loc) {
		center = loc;
	}
	
	public void startAnimations() {
		if (hasStartedAnimations()) return;
		started_animations = true;
		for (Animation a : animations) {
			a.start();
		}
	}
	
	public void stopAnimations(boolean force) {
		started_animations = false;
		for (Animation a : animations) {
			if (force) a.kill();
			else a.stop();
		}
	}
	
	public void teleport(Location loc) {
		Vector diff = new Vector(loc.getX() - center.getX(), loc.getY() - center.getY(), loc.getZ() - center.getZ());
		//else diff = new Vector(loc.getX() - center.getX(), loc.getY() - center.getY(), loc.getZ() - center.getZ());
		teleport(diff);
	}
	
	public void teleport(Vector diff) {
		center.add(diff);
		for (DexBlock b : blocks) {
			b.move(diff);
		}
		for (DexterityDisplay subd : subdisplays) subd.teleport(diff);
	}
	
	public void setGlow(Color c, boolean propegate) {
		if (c == null) {
			for (DexBlock b : blocks) b.getEntity().setGlowing(false);
		} else {
			for (DexBlock b : blocks) {
				b.getEntity().setGlowColorOverride(c);
				b.getEntity().setGlowing(true);
			}
		}
		if (propegate) {
			for (DexterityDisplay d : subdisplays) d.setGlow(c, true);
		}
	}
	
	public void setScale(float s) {
		setScale(new Vector(s, s, s));
	}
		
	public void setScale(Vector s) {
		if (s.getX() == 0 && s.getY() == 0 && s.getZ() == 0) return;
		Vector v = new Vector(s.getX() / scale.getX(), s.getY() / scale.getY(), s.getZ() / scale.getZ());
		Vector sd = v.clone().add(new Vector(-1, -1, -1));
		Vector3f trans_disp = DexUtils.vector(s.clone().multiply(-0.5)); //DexUtils.vector(v.clone().add(scale.clone().multiply(-0.5))).mul(0.5f);
		for (DexBlock db : blocks) {
			Vector disp = db.getEntity().getLocation().toVector().subtract(center.toVector());
//			displacement.setX((displacement.getX() * (x - 1)) + (x >= 0 ? -0.5 : Math.abs(x) - 0.5));
//			displacement.setY((displacement.getY() * (y - 1)) + (y >= 0 ? -0.5 : Math.abs(y) - 0.5));
//			displacement.setZ((displacement.getZ() * (z - 1)) + (z >= 0 ? -0.5 : Math.abs(z) - 0.5));
			
			//Vector diff = new Vector(disp.getX()*(sd.getX()-1), disp.getY()*(sd.getY()-1), disp.getZ()*(sd.getZ()-1));
			Vector diff = DexUtils.hadimard(disp, sd);
						
			db.move(diff);
			
			db.getTransformation()
					.setDisplacement(trans_disp)
					.setScale(DexUtils.vector(s));
			db.updateTransformation();
		}
		scale = s;
		for (DexterityDisplay sub : subdisplays) sub.setScale(v);
	}
	
	public double getYaw() {
		return yaw;
	}
	
	public double getPitch() {
		return pitch;
	}
	
	public double getRoll() {
		return roll;
	}
	
	public void rotate(float yaw_deg, float pitch_deg) {
		if (yaw_deg == 0 && pitch_deg == 0) return;
		setRotation((float) yaw + yaw_deg, (float) pitch + pitch_deg);
	}
	
	public void rotateQ(double x, double y, double z) {
		for (DexBlock b : blocks) {
			b.setTransformation(b.getTransformation().setDisplacement(new Vector3f(0, 0, 0)));
		}
		pitch += x;
		yaw += y;
		roll += z;
		Vector centerv = center.toVector().add(scale.clone().multiply(0.5));
		plugin.getAPI().markerPoint(DexUtils.location(center.getWorld(), centerv), Color.LIME, 8);
		double gamma = Math.toRadians(x), beta = Math.toRadians(y), alpha = Math.toRadians(z);
		
		Matrix3d rotmat = new Matrix3d(
				Math.cos(alpha)*Math.cos(beta), (Math.cos(alpha)*Math.sin(beta)*Math.sin(gamma)) - (Math.sin(alpha)*Math.cos(gamma)), (Math.cos(alpha)*Math.sin(beta)*Math.cos(gamma)) + (Math.sin(alpha)*Math.sin(beta)),
				Math.sin(alpha)*Math.cos(beta), (Math.sin(alpha)*Math.sin(beta)*Math.sin(gamma)) + (Math.cos(alpha)*Math.cos(gamma)), (Math.sin(alpha)*Math.sin(beta)*Math.cos(gamma)) - (Math.cos(alpha)*Math.sin(gamma)),
				-Math.sin(beta), Math.cos(beta)*Math.sin(gamma), Math.cos(beta)*Math.cos(gamma)
				).transpose();
		
		for (DexBlock b : blocks) {
			Vector3f oldOffset = DexUtils.vector(b.getEntity().getLocation().toVector().subtract(centerv));
			Vector3f newOffset = new Vector3f();
			rotmat.transform(oldOffset, newOffset);
			Location loc = b.getLocation().add(DexUtils.vector(newOffset.sub(oldOffset)));
//			plugin.getAPI().markerPoint(b.getEntity().getLocation(), Color.RED, 8);
//			plugin.getAPI().markerPoint(loc, Color.ORANGE, 8);
			b.teleport(loc);
			b.setRotation(Math.sin(Math.toRadians(pitch)), Math.sin(Math.toRadians(yaw)), Math.sin(Math.toRadians(roll)));
		}
	}
	
	public void setRotation(float yaw_deg, float pitch_deg) {
		float oldPitchDeg = (float) this.pitch, oldYawDeg = (float) this.yaw;
		float oldYaw = (float) Math.toRadians(oldYawDeg);
		double yaw = Math.toRadians(yaw_deg), pitch = Math.toRadians(pitch_deg - oldPitchDeg);
		
		if (pitch == 0 && Math.abs(yaw - oldYaw) < 0.0001) return;
		
		Matrix3d undoYawMat = new Matrix3d(
				Math.cos(oldYaw), 0f, Math.sin(oldYaw),
				0f, 1f, 0f,
				-Math.sin(oldYaw), 0f, Math.cos(oldYaw)).transpose();
//		Matrix3d pitchMat = new Matrix3d(1f, 0f, 0f,
//				0f, Math.cos(pitch), -Math.sin(pitch),
//				0f, Math.sin(pitch), Math.cos(pitch)).transpose();	
//		Matrix3d yawMat = new Matrix3d(
//				(float) Math.cos(yaw), 0f, -Math.sin(yaw),
//				0f, 1f, 0f,
//				Math.sin(yaw), 0f, Math.cos(yaw)).transpose();
		Matrix3d applyrot = DexUtils.rotMat(pitch, yaw, 0);
				
		//Matrix3d rotmat = yawMat.mul(pitchMat).mul(undoYawMat);
		Matrix3d rotmat = applyrot.mul(undoYawMat);
				
		Vector centerv = center.toVector();
		for (DexBlock b : blocks) {
			Vector3f oldOffset = DexUtils.vector(b.getEntity().getLocation().toVector().subtract(centerv));
			Vector3f newOffset = new Vector3f();
			rotmat.transform(oldOffset, newOffset);
			Location loc = b.getLocation().add(DexUtils.vector(newOffset.sub(oldOffset)));
			loc.setYaw(loc.getYaw() + yaw_deg - oldYawDeg);
			loc.setPitch(loc.getPitch() + pitch_deg - oldPitchDeg);
			b.teleport(loc);
			//b.setRotation(0, 0, Math.toRadians(20));
		}
		
		this.yaw = yaw_deg;
		this.pitch = pitch_deg;
	}

}
