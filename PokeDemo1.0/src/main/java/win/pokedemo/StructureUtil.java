package win.pokedemo;

import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Cross-core helper for structure lookup.
 *
 * Purpur/Paper 1.21.x provides World#locateNearestStructure, but the parameter
 * type differs across mappings (Structure / StructureType / generator.structure.Structure).
 * We use reflection so the plugin can stay compatible.
 */
public final class StructureUtil {
    private StructureUtil() {}

    /**
     * @param structureName enum constant name, e.g. "VILLAGE", "PILLAGER_OUTPOST".
     */
    public static boolean isNearStructure(World world, Location origin, String structureName, int radius, boolean findUnexplored) {
        if (world == null || origin == null || structureName == null || structureName.isBlank()) return false;
        String name = structureName.trim().toUpperCase(Locale.ROOT);

        // Try known parameter enum classes in order.
        Object structureEnum = tryEnum("org.bukkit.generator.structure.Structure", name);
        Class<?> enumClass = (structureEnum != null) ? structureEnum.getClass() : null;

        if (structureEnum == null) {
            structureEnum = tryEnum("org.bukkit.Structure", name);
            enumClass = (structureEnum != null) ? structureEnum.getClass() : null;
        }
        if (structureEnum == null) {
            structureEnum = tryEnum("org.bukkit.StructureType", name);
            enumClass = (structureEnum != null) ? structureEnum.getClass() : null;
        }

        if (structureEnum == null || enumClass == null) return false;

        try {
            Method m = world.getClass().getMethod("locateNearestStructure", Location.class, enumClass, int.class, boolean.class);
            Object result = m.invoke(world, origin, structureEnum, radius, findUnexplored);
            if (result == null) return false;
            // Some cores return Location, others return StructureSearchResult
            if (result instanceof Location) return true;
            try {
                Method getLocation = result.getClass().getMethod("getLocation");
                Object loc = getLocation.invoke(result);
                return loc instanceof Location;
            } catch (Throwable ignored) {
                return true; // unknown non-null result; assume found
            }
        } catch (NoSuchMethodException nsme) {
            // Some forks use locateNearestStructure(Location, Structure, int) without findUnexplored
            try {
                Method m = world.getClass().getMethod("locateNearestStructure", Location.class, enumClass, int.class);
                Object result = m.invoke(world, origin, structureEnum, radius);
                return result != null;
            } catch (Throwable ignored) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isNearAnyStructure(World world, Location origin, java.util.List<String> structureNames, int radius, boolean findUnexplored) {
        if (structureNames == null || structureNames.isEmpty()) return false;
        for (String s : structureNames) {
            if (isNearStructure(world, origin, s, radius, findUnexplored)) return true;
        }
        return false;
    }

    private static Object tryEnum(String className, String constant) {
        try {
            Class<?> c = Class.forName(className);
            if (!c.isEnum()) return null;
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object e = Enum.valueOf((Class<? extends Enum>) c, constant);
            return e;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
