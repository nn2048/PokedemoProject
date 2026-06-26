package win.pokedemo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class SpeciesMovementRegistry {
    private final PokeDemoPlugin plugin;
    private final Map<String, SpeciesMovementProfile> profiles = new ConcurrentHashMap<>();

    public SpeciesMovementRegistry(PokeDemoPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        profiles.clear();
        Path root = plugin.getDataFolder().toPath().resolve("species_raw");
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(this::loadOne);
        } catch (IOException e) {
            plugin.getLogger().warning("[PokeDemoMotion] Failed to scan species_raw: " + e.getMessage());
        }
    }

    public SpeciesMovementProfile get(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) return SpeciesMovementProfile.DEFAULT;
        return profiles.getOrDefault(speciesId.toLowerCase(Locale.ROOT), SpeciesMovementProfile.DEFAULT);
    }

    private void loadOne(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String id = inferId(root, path);
            if (id == null || id.isBlank()) return;
            JsonObject behaviour = obj(root, "behaviour");
            JsonObject moving = behaviour == null ? null : obj(behaviour, "moving");
            JsonObject resting = behaviour == null ? null : obj(behaviour, "resting");
            SpeciesMovementProfile p = parseProfile(moving, resting);
            profiles.put(id.toLowerCase(Locale.ROOT), p);
        } catch (Throwable ignored) {}
    }

    private SpeciesMovementProfile parseProfile(JsonObject moving, JsonObject resting) {
        boolean canWalk = true;
        boolean canFly = false;
        boolean canSwimWater = true;
        boolean canBreatheUnderwater = false;
        boolean canWalkOnWater = false;
        boolean canSwimLava = false;
        boolean canWalkOnLava = false;
        boolean avoidsWater = false;
        boolean avoidsLand = false;
        boolean canSleep = false;
        boolean sleepAnyTime = false;
        double walkSpeed = 0.23D;
        double swimSpeed = 0.30D;
        double flySpeed = 0.30D;
        double hoverHeight = 1.15D;
        if (moving != null) {
            JsonObject walk = obj(moving, "walk");
            if (walk != null) {
                if (walk.has("canWalk")) canWalk = bool(walk.get("canWalk"), true);
                if (walk.has("walkSpeed")) walkSpeed = num(walk.get("walkSpeed"), walkSpeed);
                if (walk.has("avoidsWater")) avoidsWater = bool(walk.get("avoidsWater"), avoidsWater);
                if (walk.has("avoidsLand")) avoidsLand = bool(walk.get("avoidsLand"), avoidsLand);
            }
            JsonObject fly = obj(moving, "fly");
            if (fly != null) {
                canFly = !fly.has("canFly") || bool(fly.get("canFly"), true);
                if (fly.has("flySpeedHorizontal")) flySpeed = num(fly.get("flySpeedHorizontal"), flySpeed);
                if (fly.has("flySpeed")) flySpeed = num(fly.get("flySpeed"), flySpeed);
                if (fly.has("avoidsWater")) avoidsWater = bool(fly.get("avoidsWater"), avoidsWater);
            }
            JsonObject swim = obj(moving, "swim");
            if (swim != null) {
                canSwimWater = !swim.has("canSwimInWater") || bool(swim.get("canSwimInWater"), true);
                if (swim.has("swimSpeed")) swimSpeed = num(swim.get("swimSpeed"), swimSpeed);
                if (swim.has("canBreatheUnderwater")) canBreatheUnderwater = bool(swim.get("canBreatheUnderwater"), false);
                if (swim.has("canWalkOnWater")) canWalkOnWater = bool(swim.get("canWalkOnWater"), false);
                if (swim.has("canSwimInLava")) canSwimLava = bool(swim.get("canSwimInLava"), false);
                if (swim.has("canWalkOnLava")) canWalkOnLava = bool(swim.get("canWalkOnLava"), false);
                if (swim.has("avoidsWater")) avoidsWater = bool(swim.get("avoidsWater"), avoidsWater);
                if (swim.has("avoidsLand")) avoidsLand = bool(swim.get("avoidsLand"), avoidsLand);
            }
            if (canFly && !canWalk) hoverHeight = 1.45D;
            else if (canFly) hoverHeight = 1.10D;
            if (avoidsLand && canSwimWater) hoverHeight = 0.85D;
        }
        if (resting != null) {
            canSleep = bool(resting.get("canSleep"), false);
            JsonArray times = resting.has("times") && resting.get("times").isJsonArray() ? resting.getAsJsonArray("times") : null;
            sleepAnyTime = containsTime(times, "any") || containsTime(times, "all") || times == null || times.isEmpty();
        }
        return new SpeciesMovementProfile(canWalk, canFly, canSwimWater, canBreatheUnderwater, canWalkOnWater, canSwimLava, canWalkOnLava, avoidsWater, avoidsLand, walkSpeed, swimSpeed, flySpeed, hoverHeight, canSleep, sleepAnyTime);
    }

    private static String inferId(JsonObject root, Path path) {
        if (root.has("id") && !root.get("id").isJsonNull()) {
            String id = root.get("id").getAsString();
            if (id != null && !id.isBlank()) return id.trim();
        }
        if (root.has("name") && !root.get("name").isJsonNull()) {
            String name = root.get("name").getAsString();
            if (name != null && !name.isBlank()) return normalizeId(name);
        }
        String file = path.getFileName().toString();
        if (file.endsWith(".json")) file = file.substring(0, file.length() - 5);
        return normalizeId(file);
    }

    private static String normalizeId(String s) {
        if (s == null) return null;
        return s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static boolean containsTime(JsonArray arr, String wanted) {
        if (arr == null || wanted == null) return false;
        for (JsonElement el : arr) {
            try {
                if (wanted.equalsIgnoreCase(el.getAsString())) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static JsonObject obj(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
    }

    private static boolean bool(JsonElement el, boolean def) {
        try {
            if (el == null || el.isJsonNull()) return def;
            if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
            return Boolean.parseBoolean(el.getAsString());
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static double num(JsonElement el, double def) {
        try {
            if (el == null || el.isJsonNull()) return def;
            if (el.getAsJsonPrimitive().isNumber()) return el.getAsDouble();
            return Double.parseDouble(el.getAsString().trim());
        } catch (Throwable ignored) {
            return def;
        }
    }
}
