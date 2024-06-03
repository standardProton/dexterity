package me.c7dev.tensegrity.displays.animation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.c7dev.tensegrity.Dexterity;
import me.c7dev.tensegrity.displays.DexterityDisplay;

public class RideAnimation extends Animation {
	
	private Location start_loc;
	private double speed = 2.0/20;
	private boolean x_enabled = true, y_enabled = true, z_enabled = true, teleport_when_done = false;
	private Snowball mount;
	private Player p;
	private Vector seat_offset = new Vector(0, 0, 0);

	public RideAnimation(DexterityDisplay display, Dexterity plugin) {
		super(display, plugin, 1);
		
		spawnMount();
		
		setFrameRate(1);
		
		start_loc = display.getCenter();
						
		super.setRunnable(new BukkitRunnable() {
			@Override
			public void run() {
				if (p == null || isPaused()) return;
				if (!p.isOnline() || mount.getPassengers().size() == 0) {
					if (mount.isDead()) getDisplay().teleport(new Vector(0, 1, 0));
					stop();
					return;
				}
				
				Vector dir = p.getLocation().getDirection();
				if (!x_enabled) dir.setX(0);
				if (!y_enabled) dir.setY(0);
				if (!z_enabled) dir.setZ(0);
				dir.normalize().multiply(speed);
				
				display.teleport(dir);
				
				mount.setVelocity(dir);
			}
		});
	}
	
	private void spawnMount() {
		mount = getDisplay().getCenter().getWorld().spawn(getDisplay().getCenter().clone().add(seat_offset), Snowball.class, a -> {
			//a.addPassenger(p);
			//a.setVisible(false);
			//a.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false));
			a.setSilent(true);
			a.setGravity(false);
		});
	}
	
	public boolean mount(Player player) {
		if (p != null) return false;
		if (mount == null || mount.isDead()) spawnMount();
		p = player;
		mount.addPassenger(player);
		return true;
	}
	public void dismount() {
		if (p == null) return;
		if (mount != null) mount.removePassenger(p);
	}
	
	public void setXEnabled(boolean b) {
		x_enabled = b;
	}
	public void setYEnabled(boolean b) {
		y_enabled = b;
	}
	public void setZEnabled(boolean b) {
		z_enabled = b;
	}
	public void setSpeed(double blocks_per_second) {
		speed = blocks_per_second / 20;
	}
	public double getSpeed() {
		return 20*speed;
	}
	public void setTeleportBackOnDismount(boolean b) {
		teleport_when_done = b;
	}
	
	public void setSeatOffset(Vector v) {
		Vector diff = v.clone().subtract(seat_offset);
		if (mount != null) mount.teleport(mount.getLocation().add(diff));
		seat_offset = v;
	}
	public Vector getSeatOffset() {
		return seat_offset;
	}
	
	@Override
	public void stop() {
		super.kill();
		p = null;
		if (mount != null) mount.remove();
		if (teleport_when_done) getDisplay().teleport(start_loc);
	}

}