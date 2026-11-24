package me.dogs.dragoncannon;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * One firing of the cannon. Runs once per tick, launches the planned shells,
 * drops them from the sky and spawns a dragon breath cloud where each one lands.
 */
final class CannonFiring extends BukkitRunnable {

    private static final int MAX_TICKS = 20 * 60;

    private static final class Shell {
        final double x;
        final double z;
        final double speed;
        final int launchTick;
        final boolean loud;
        double y;
        double impactY;

        Shell(double x, double z, double speed, int launchTick, boolean loud) {
            this.x = x;
            this.z = z;
            this.speed = speed;
            this.launchTick = launchTick;
            this.loud = loud;
        }
    }

    private final DragonCannonPlugin plugin;
    private final Player shooter;
    private final World world;
    private final List<Shell> pending = new ArrayList<>();
    private final List<Shell> active = new ArrayList<>();

    private final float cloudRadius;
    private final int cloudDuration;
    private final int amplifier;
    private final int particlesPerShell;
    private final int maxShells;
    private final double startY;
    private final double centerX;
    private final double centerZ;

    private int nextPending = 0;
    private int tick = 0;

    CannonFiring(DragonCannonPlugin plugin, Player shooter, CannonMode mode, Location target) {
        this.plugin = plugin;
        this.shooter = shooter;
        this.world = target.getWorld();

        FileConfiguration cfg = plugin.getConfig();
        String prefix = mode.configKey() + ".";

        this.cloudRadius = (float) cfg.getDouble(prefix + "cloud-radius", 3.0);
        this.cloudDuration = Math.max(1, cfg.getInt(prefix + "cloud-duration", 200));
        this.amplifier = Math.max(0, cfg.getInt("damage-amplifier", 1));
        this.particlesPerShell = Math.max(0, cfg.getInt("trail-particles-per-shell", 1));
        this.maxShells = Math.max(1, cfg.getInt("max-shells", 1200));

        double height = cfg.getDouble("start-height", 120.0);
        double top = world.getMaxHeight() - 2;
        this.startY = Math.min(top, target.getY() + height);
        this.centerX = target.getX();
        this.centerZ = target.getZ();

        if (mode == CannonMode.NORMAL) {
            buildRings(cfg);
        } else {
            buildStab(cfg);
        }
    }

    void start() {
        runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------
    // Version 1: concentric circles
    // ------------------------------------------------------------------
    private void buildRings(FileConfiguration cfg) {
        int rings = Math.max(1, cfg.getInt("normal.rings", 10));
        double spacing = Math.max(1.0, cfg.getDouble("normal.ring-spacing", 3.0));
        int delay = Math.max(0, cfg.getInt("normal.ring-delay-ticks", 3));
        double speed = Math.max(0.5, cfg.getDouble("normal.fall-speed", 2.5));

        addShell(centerX, centerZ, speed, 0, true);

        for (int ring = 1; ring <= rings; ring++) {
            double radius = ring * spacing;
            int points = Math.max(6, (int) Math.round(2.0 * Math.PI * radius / spacing));
            // every second circle is rotated half a step so the pattern looks dense like the real cannon
            double offset = (ring % 2 == 0) ? Math.PI / points : 0.0;
            for (int i = 0; i < points; i++) {
                double angle = offset + (2.0 * Math.PI * i) / points;
                double x = centerX + Math.cos(angle) * radius;
                double z = centerZ + Math.sin(angle) * radius;
                addShell(x, z, speed, ring * delay, i == 0);
            }
        }
    }

    // ------------------------------------------------------------------
    // Version 2: stab shot (tight column)
    // ------------------------------------------------------------------
    private void buildStab(FileConfiguration cfg) {
        int shots = Math.max(1, cfg.getInt("stab.shots", 40));
        int interval = Math.max(1, cfg.getInt("stab.interval-ticks", 1));
        double speed = Math.max(0.5, cfg.getDouble("stab.fall-speed", 4.0));
        double spread = Math.max(0.0, cfg.getDouble("stab.radius", 0.0));

        for (int s = 0; s < shots; s++) {
            int launch = s * interval;
            boolean loud = (s % 5 == 0);
            addShell(centerX, centerZ, speed, launch, loud);
            if (spread > 0.0) {
                addShell(centerX + spread, centerZ, speed, launch, false);
                addShell(centerX - spread, centerZ, speed, launch, false);
                addShell(centerX, centerZ + spread, speed, launch, false);
                addShell(centerX, centerZ - spread, speed, launch, false);
            }
        }
    }

    private void addShell(double x, double z, double speed, int launchTick, boolean loud) {
        if (pending.size() >= maxShells) {
            return;
        }
        pending.add(new Shell(x, z, speed, launchTick, loud));
    }

    // ------------------------------------------------------------------
    // Per tick logic
    // ------------------------------------------------------------------
    @Override
    public void run() {
        tick++;
        if (tick > MAX_TICKS) {
            cancel();
            return;
        }

        while (nextPending < pending.size() && pending.get(nextPending).launchTick <= tick) {
            launch(pending.get(nextPending));
            nextPending++;
        }

        Iterator<Shell> it = active.iterator();
        while (it.hasNext()) {
            Shell shell = it.next();
            shell.y -= shell.speed;
            if (shell.y <= shell.impactY) {
                impact(shell);
                it.remove();
            } else {
                trail(shell);
            }
        }

        if (nextPending >= pending.size() && active.isEmpty()) {
            cancel();
        }
    }

    private void launch(Shell shell) {
        int chunkX = ((int) Math.floor(shell.x)) >> 4;
        int chunkZ = ((int) Math.floor(shell.z)) >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return; // don't force-load chunks
        }
        Location from = new Location(world, shell.x, startY, shell.z);
        double maxDistance = startY - world.getMinHeight() + 1.0;
        RayTraceResult hit = world.rayTraceBlocks(from, new Vector(0, -1, 0), maxDistance,
                FluidCollisionMode.NEVER, true);
        shell.y = startY;
        shell.impactY = (hit != null) ? hit.getHitPosition().getY() : world.getMinHeight();
        active.add(shell);
    }

    private void trail(Shell shell) {
        for (int i = 0; i < particlesPerShell; i++) {
            double y = shell.y + (i * shell.speed) / particlesPerShell;
            breath(world, shell.x, y, shell.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void impact(Shell shell) {
        spawnCloud(shell.x, shell.impactY, shell.z);
        breath(world, shell.x, shell.impactY + 0.2, shell.z, 12, 0.6, 0.2, 0.6, 0.02);
        if (shell.loud) {
            world.playSound(new Location(world, shell.x, shell.impactY, shell.z),
                    Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.9f);
        }
    }

    private void spawnCloud(double x, double y, double z) {
        Location location = new Location(world, x, y + 0.05, z);
        world.spawn(location, AreaEffectCloud.class, cloud -> {
            cloud.setRadius(cloudRadius);
            cloud.setDuration(cloudDuration);
            cloud.setRadiusOnUse(-0.5f);
            cloud.setRadiusPerTick(-cloudRadius / cloudDuration);
            cloud.setReapplicationDelay(20);
            cloud.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, amplifier), true);
            cloud.setSource(shooter);
            try {
                cloud.setParticle(Particle.DRAGON_BREATH);
            } catch (RuntimeException ex) {
                // newer versions give the dragon breath particle a "power" value
                cloud.setParticle(Particle.DRAGON_BREATH, 1.0f);
            }
        });
    }

    /** Spawns dragon breath particles, works whether or not this version wants extra particle data. */
    static void breath(World world, double x, double y, double z, int count,
                       double dx, double dy, double dz, double speed) {
        Location location = new Location(world, x, y, z);
        try {
            world.spawnParticle(Particle.DRAGON_BREATH, location, count, dx, dy, dz, speed);
        } catch (IllegalArgumentException ex) {
            world.spawnParticle(Particle.DRAGON_BREATH, location, count, dx, dy, dz, speed, 1.0f);
        }
    }
}
