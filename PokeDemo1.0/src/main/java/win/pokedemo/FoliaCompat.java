package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Small compatibility layer: runs a task at a specific location using Folia RegionScheduler when available,
 * otherwise falls back to Bukkit main thread scheduler (Paper/Purpur).
 */
public final class FoliaCompat {
    private static boolean checked = false;
    private static Method regionSchedulerRun = null;
    private static Object regionScheduler = null;

    private static void check() {
        if (checked) return;
        checked = true;
        try {
            // Bukkit.getRegionScheduler() exists on Folia.
            Method mGet = Bukkit.class.getMethod("getRegionScheduler");
            regionScheduler = mGet.invoke(null);
            // RegionScheduler#run(Plugin, Location, Consumer)
            regionSchedulerRun = regionScheduler.getClass().getMethod("run", Plugin.class, Location.class, Consumer.class);
        } catch (Throwable t) {
            regionScheduler = null;
            regionSchedulerRun = null;
        }
    }

    public static void runAt(Plugin plugin, Location loc, Runnable action) {
        check();
        if (regionScheduler != null && regionSchedulerRun != null) {
            try {
                regionSchedulerRun.invoke(regionScheduler, plugin, loc, (Consumer<Object>) (task) -> action.run());
                return;
            } catch (Throwable ignored) {
            }
        }
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private FoliaCompat() {}
}
