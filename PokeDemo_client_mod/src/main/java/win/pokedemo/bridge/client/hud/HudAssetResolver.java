package win.pokedemo.bridge.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HudAssetResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("PokeDemoBridge/HudAssets");
    private static final Set<String> LOGGED_HELD_MISSES = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, Identifier> SPECIES_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> ITEM_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, PortraitSlice> SPECIES_SLICES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> SPECIES_NATDEX = new ConcurrentHashMap<>();
    private static final Map<String, Integer> SPECIES_ICON_CMDS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> CMD_TO_SPECIES = new ConcurrentHashMap<>();
    private static final Map<String, String> SPECIES_DISPLAY_TO_KEY = new ConcurrentHashMap<>();
    private static final Map<String, String> SPECIES_DISPLAY_RAW_TO_KEY = new ConcurrentHashMap<>();
    private static final Map<Identifier, int[]> IMAGE_SIZES = new ConcurrentHashMap<>();
    private static volatile boolean speciesIndexed = false;
    private static volatile boolean speciesDexIndexed = false;
    private static volatile boolean itemsIndexed = false;
    private static volatile boolean iconCmdsIndexed = false;
    private static volatile boolean speciesDisplayIndexed = false;

    private static final Identifier SHOWDOWN_SHEET = Identifier.of("pokedemo_bridge", "textures/gui/showdown/pokemonicons-sheet.png");
    private static final int SHOWDOWN_ICON_W = 40;
    private static final int SHOWDOWN_ICON_H = 30;
    private static final int SHOWDOWN_COLUMNS = 12;

    private HudAssetResolver() {}

    public static Identifier findSpeciesPortrait(String species) {
        PortraitSlice slice = findSpeciesPortraitSlice(species);
        return slice != null ? slice.texture() : null;
    }

    public static PortraitSlice findSpeciesPortraitSlice(String species) {
        if (species == null || species.isBlank()) return null;
        ensureSpeciesDexIndex();
        String key = normalize(species);
        return findShowdownPortraitSlice(key);
    }




    public static String speciesForCustomModelData(int cmd) {
        if (cmd <= 0) return null;
        ensureIconCmdIndex();
        String direct = CMD_TO_SPECIES.get(cmd);
        if (direct != null) return direct;
        int bestThreshold = Integer.MIN_VALUE;
        String best = null;
        for (Map.Entry<Integer, String> entry : CMD_TO_SPECIES.entrySet()) {
            int threshold = entry.getKey();
            if (threshold <= cmd && threshold > bestThreshold) {
                bestThreshold = threshold;
                best = entry.getValue();
            }
        }
        return best;
    }

    public static String findSpeciesForItemStack(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Integer cmd = extractCustomModelData(stack);
        String byCmd = cmd == null ? null : speciesForCustomModelData(cmd);
        if (byCmd != null) return byCmd;
        ensureSpeciesDisplayIndex();
        String byName = speciesFromDisplayText(stack.getName() != null ? stack.getName().getString() : null);
        if (byName != null) return byName;
        String byLore = speciesFromLore(stack);
        if (byLore != null) return byLore;
        String byDump = speciesFromStackDump(stack);
        if (byDump != null) return byDump;
        try {
            String translation = stack.getItem().getTranslationKey();
            String byTranslation = speciesFromDisplayText(translation);
            if (byTranslation != null) return byTranslation;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Integer findSpeciesIconCmd(String species) {
        if (species == null || species.isBlank()) return null;
        ensureIconCmdIndex();
        String key = normalize(species);
        Integer direct = SPECIES_ICON_CMDS.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, Integer> entry : SPECIES_ICON_CMDS.entrySet()) {
            String k = entry.getKey();
            if (k.equals(key) || k.endsWith(key) || key.endsWith(k)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static Identifier findHeldItemTexture(String heldItemId) {
        if (heldItemId == null || heldItemId.isBlank()) return null;
        ensureItemIndex();

        String original = heldItemId;
        for (String candidate : heldItemCandidates(heldItemId)) {
            if (candidate == null || candidate.isBlank()) continue;
            Identifier directResource = findHeldItemDirectResource(candidate);
            if (directResource != null) return directResource;

            String key = normalize(candidate);
            if (key.isBlank()) continue;
            Identifier direct = ITEM_TEXTURES.get(key);
            if (direct != null) return direct;
            for (Map.Entry<String, Identifier> entry : ITEM_TEXTURES.entrySet()) {
                String k = entry.getKey();
                if (k.equals(key) || k.endsWith(key) || key.endsWith(k) || k.contains(key)) {
                    return entry.getValue();
                }
            }
        }
        String missKey = original.trim();
        if (!missKey.isBlank() && LOGGED_HELD_MISSES.add(missKey)) {
            LOGGER.info("Held item HUD miss: '{}' candidates={} indexed={}", original, heldItemCandidates(original), ITEM_TEXTURES.size());
        }
        return null;
    }

    public static int[] imageSize(Identifier identifier) {
        if (identifier == null) return new int[]{16, 16};
        return IMAGE_SIZES.computeIfAbsent(identifier, HudAssetResolver::readSize);
    }

    private static int[] readSize(Identifier identifier) {
        try {
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            Optional<Resource> res = rm.getResource(identifier);
            if (res.isEmpty()) return new int[]{16, 16};
            try (InputStream in = res.get().getInputStream()) {
                NativeImage image = NativeImage.read(in);
                int[] size = new int[]{Math.max(1, image.getWidth()), Math.max(1, image.getHeight())};
                image.close();
                return size;
            }
        } catch (IOException ignored) {
            return new int[]{16, 16};
        }
    }

    private static void ensureSpeciesIndex() {
        if (speciesIndexed) return;
        synchronized (HudAssetResolver.class) {
            if (speciesIndexed) return;
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            indexSpeciesResources(rm, "textures/block/pokemon");
            indexSpeciesResources(rm, "textures/pokemon");
            speciesIndexed = true;
        }
    }

    private static void ensureIconCmdIndex() {
        if (iconCmdsIndexed) return;
        synchronized (HudAssetResolver.class) {
            if (iconCmdsIndexed) return;
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            for (String path : java.util.List.of("species_icon_cmd.json", "species_icon_map.json")) {
                Identifier id = Identifier.of("pokedemo_bridge", path);
                Optional<Resource> res = rm.getResource(id);
                if (res.isPresent()) {
                    try (InputStream in = res.get().getInputStream(); InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                            if (entry.getValue() != null && entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                                int cmd = entry.getValue().getAsInt();
                                String species = normalize(entry.getKey());
                                SPECIES_ICON_CMDS.put(species, cmd);
                                CMD_TO_SPECIES.putIfAbsent(cmd, species);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            iconCmdsIndexed = true;
        }
    }

    private static void ensureSpeciesDexIndex() {
        if (speciesDexIndexed) return;
        synchronized (HudAssetResolver.class) {
            if (speciesDexIndexed) return;
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            rm.findResources("species", id -> id.getNamespace().equals("pokedemo_bridge") && id.getPath().endsWith(".json"))
                    .forEach((id, resource) -> {
                        try (InputStream in = resource.getInputStream();
                             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                            if (root == null || !root.has("nationalPokedexNumber")) return;
                            int dex = root.get("nationalPokedexNumber").getAsInt();
                            if (dex <= 0) return;
                            String key = normalize(fileStem(id.getPath()));
                            if (!key.isBlank()) SPECIES_NATDEX.putIfAbsent(key, dex);
                            if (root.has("name") && root.get("name").isJsonPrimitive()) {
                                String name = normalize(root.get("name").getAsString());
                                if (!name.isBlank()) SPECIES_NATDEX.putIfAbsent(name, dex);
                            }
                        } catch (Exception ignored) {
                        }
                    });
            speciesDexIndexed = true;
        }
    }

    private static void ensureSpeciesDisplayIndex() {
        if (speciesDisplayIndexed) return;
        synchronized (HudAssetResolver.class) {
            if (speciesDisplayIndexed) return;
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            Identifier aliasId = Identifier.of("pokedemo_bridge", "species_display_aliases.json");
            Optional<Resource> aliasRes = rm.getResource(aliasId);
            if (aliasRes.isPresent()) {
                try (InputStream in = aliasRes.get().getInputStream();
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                        if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) continue;
                        String alias = normalizeDisplay(entry.getKey());
                        String species = normalize(entry.getValue().getAsString());
                        if (!alias.isBlank() && !species.isBlank()) {
                            SPECIES_DISPLAY_TO_KEY.putIfAbsent(alias, species);
                            SPECIES_DISPLAY_RAW_TO_KEY.putIfAbsent(alias, species);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            rm.findResources("species", id -> id.getNamespace().equals("pokedemo_bridge") && id.getPath().endsWith(".json"))
                    .forEach((id, resource) -> {
                        try (InputStream in = resource.getInputStream();
                             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                            String key = normalize(fileStem(id.getPath()));
                            if (!key.isBlank()) {
                                SPECIES_DISPLAY_TO_KEY.putIfAbsent(key, key);
                                SPECIES_DISPLAY_RAW_TO_KEY.putIfAbsent(normalizeDisplay(fileStem(id.getPath())), key);
                            }
                            if (root != null && root.has("name") && root.get("name").isJsonPrimitive()) {
                                String name = normalizeDisplay(root.get("name").getAsString());
                                if (!name.isBlank()) {
                                    SPECIES_DISPLAY_TO_KEY.putIfAbsent(name, key);
                                    SPECIES_DISPLAY_RAW_TO_KEY.putIfAbsent(name, key);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    });
            speciesDisplayIndexed = true;
        }
    }

    private static String speciesFromDisplayText(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = stripFormatting(text);
        java.util.regex.Matcher explicit = java.util.regex.Pattern.compile("(?:species|id)\\s*[:：]\\s*([A-Za-z0-9_-]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(cleaned);
        if (explicit.find()) {
            String raw = normalize(explicit.group(1));
            if (!raw.isBlank()) return raw;
        }
        String key = normalizeDisplay(cleaned);
        if (key.isBlank()) return null;
        String direct = SPECIES_DISPLAY_RAW_TO_KEY.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, String> entry : SPECIES_DISPLAY_RAW_TO_KEY.entrySet()) {
            String k = entry.getKey();
            if (k.equals(key) || k.endsWith(key) || key.endsWith(k) || key.contains(k) || k.contains(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String speciesFromLore(net.minecraft.item.ItemStack stack) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            net.minecraft.item.tooltip.TooltipType type = mc.options.advancedItemTooltips ? net.minecraft.item.tooltip.TooltipType.ADVANCED : net.minecraft.item.tooltip.TooltipType.BASIC;
            java.util.List<net.minecraft.text.Text> tooltip = stack.getTooltip(net.minecraft.item.Item.TooltipContext.DEFAULT, mc.player, type);
            if (tooltip != null) {
                for (net.minecraft.text.Text line : tooltip) {
                    String txt = line == null ? "" : line.getString();
                    String found = speciesFromDisplayText(txt);
                    if (found != null) return found;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object lore = stack.get(net.minecraft.component.DataComponentTypes.LORE);
            if (lore == null) return null;
            for (java.lang.reflect.Method m : lore.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!java.util.List.class.isAssignableFrom(m.getReturnType())) continue;
                Object value = m.invoke(lore);
                if (value instanceof java.util.List<?> list) {
                    for (Object line : list) {
                        String txt = String.valueOf(line);
                        String found = speciesFromDisplayText(txt);
                        if (found != null) return found;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String speciesFromStackDump(net.minecraft.item.ItemStack stack) {
        try {
            String dump = stripFormatting(String.valueOf(stack));
            java.util.regex.Matcher cmdMatcher = java.util.regex.Pattern.compile("(?:custom_model_data|floats|ints|value)[^0-9-]*(-?\\d{5,})", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(dump + " " + String.valueOf(stack.getComponents()));
            if (cmdMatcher.find()) {
                try {
                    String byCmd = speciesForCustomModelData(Integer.parseInt(cmdMatcher.group(1)));
                    if (byCmd != null) return byCmd;
                } catch (Exception ignored) {}
            }
            String direct = speciesFromDisplayText(dump);
            if (direct != null) return direct;
            dump = stripFormatting(String.valueOf(stack.getComponents()));
            direct = speciesFromDisplayText(dump);
            if (direct != null) return direct;
            String normalizedDump = normalizeDisplay(dump);
            if (normalizedDump.isBlank()) return null;
            for (Map.Entry<String, String> entry : SPECIES_DISPLAY_TO_KEY.entrySet()) {
                String alias = entry.getKey();
                if (!alias.isBlank() && normalizedDump.contains(alias)) {
                    return entry.getValue();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String stripFormatting(String text) {
        return text.replaceAll("§.", "").trim();
    }

    private static PortraitSlice findShowdownPortraitSlice(String key) {
        if (key == null || key.isBlank()) return null;
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        if (rm.getResource(SHOWDOWN_SHEET).isEmpty()) return null;
        Integer dex = SPECIES_NATDEX.get(key);
        if (dex == null) {
            for (Map.Entry<String, Integer> entry : SPECIES_NATDEX.entrySet()) {
                String k = entry.getKey();
                if (k.equals(key) || k.endsWith(key) || key.endsWith(k)) {
                    dex = entry.getValue();
                    break;
                }
            }
        }
        if (dex == null || dex <= 0) return null;
        int[] size = imageSize(SHOWDOWN_SHEET);
        int texW = Math.max(SHOWDOWN_ICON_W, size[0]);
        int texH = Math.max(SHOWDOWN_ICON_H, size[1]);
        int index = Math.max(0, dex);
        int u = (index % SHOWDOWN_COLUMNS) * SHOWDOWN_ICON_W;
        int v = (index / SHOWDOWN_COLUMNS) * SHOWDOWN_ICON_H;
        if (u + SHOWDOWN_ICON_W > texW || v + SHOWDOWN_ICON_H > texH) return null;
        return new PortraitSlice(SHOWDOWN_SHEET, u, v, SHOWDOWN_ICON_W, SHOWDOWN_ICON_H, texW, texH);
    }

    private static void ensureItemIndex() {
        if (itemsIndexed) return;
        synchronized (HudAssetResolver.class) {
            if (itemsIndexed) return;
            ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
            indexItemResources(rm, "textures/item/held");
            indexItemResources(rm, "textures/item/berries");
            indexItemResources(rm, "textures/item/balls");
            indexItemResources(rm, "textures/item/apricorns");
            indexItemResources(rm, "textures/item");
            itemsIndexed = true;
        }
    }


    private static void indexSpeciesResources(ResourceManager rm, String root) {
        rm.findResources(root, id -> id.getPath().endsWith(".png"))
                .keySet().stream()
                .sorted(Comparator.<Identifier, Integer>comparing(id -> namespacePriority(id.getNamespace()))
                        .thenComparing(Identifier::toString))
                .forEach(id -> {
                    String path = id.getPath();
                    String file = fileStem(path);
                    String folder = parentStem(path);
                    if (isPreferredSpeciesTexture(file, folder)) {
                        String fileKey = normalize(file);
                        String folderKey = folder != null && !folder.isBlank() ? normalize(folder) : "";
                        putPreferred(SPECIES_TEXTURES, fileKey, id);
                        PortraitSlice slice = buildPortraitSlice(id, folder.isBlank() ? file : folder);
                        if (slice != null) {
                            putPreferred(SPECIES_SLICES, fileKey, slice);
                        }
                        if (folder != null && !folder.isBlank()) {
                            putPreferred(SPECIES_TEXTURES, folderKey, id);
                            if (slice != null) {
                                putPreferred(SPECIES_SLICES, folderKey, slice);
                            }
                        }
                    }
                });
    }

    private static void indexItemResources(ResourceManager rm, String root) {
        rm.findResources(root, id -> id.getPath().endsWith(".png"))
                .keySet().stream()
                .sorted(Comparator.<Identifier, Integer>comparing(id -> namespacePriority(id.getNamespace()))
                        .thenComparing(Identifier::toString))
                .forEach(id -> {
                    String file = fileStem(id.getPath());
                    if (!file.isBlank()) {
                        putPreferred(ITEM_TEXTURES, normalize(file), id);
                    }
                });
    }

    private static void putPreferred(Map<String, Identifier> map, String key, Identifier id) {
        if (key == null || key.isBlank() || id == null) return;
        Identifier existing = map.get(key);
        if (existing == null || namespacePriority(id.getNamespace()) < namespacePriority(existing.getNamespace())) {
            map.put(key, id);
        }
    }

    private static void putPreferred(Map<String, PortraitSlice> map, String key, PortraitSlice slice) {
        if (key == null || key.isBlank() || slice == null) return;
        PortraitSlice existing = map.get(key);
        if (existing == null || namespacePriority(slice.texture().getNamespace()) < namespacePriority(existing.texture().getNamespace())) {
            map.put(key, slice);
        }
    }


    private static PortraitSlice buildPortraitSlice(Identifier textureId, String modelStem) {
        if (textureId == null || modelStem == null || modelStem.isBlank()) return null;
        String normalizedStem = modelStem.toLowerCase(Locale.ROOT);
        Identifier modelId = Identifier.of(textureId.getNamespace(), "models/pokemon/" + normalizedStem + ".json");
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        Optional<Resource> res = rm.getResource(modelId);
        if (res.isEmpty()) {
            return buildFallbackSlice(textureId);
        }
        try (InputStream in = res.get().getInputStream(); InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray groups = root.has("groups") && root.get("groups").isJsonArray() ? root.getAsJsonArray("groups") : null;
            JsonArray elements = root.has("elements") && root.get("elements").isJsonArray() ? root.getAsJsonArray("elements") : null;
            JsonArray texSize = root.has("texture_size") && root.get("texture_size").isJsonArray() ? root.getAsJsonArray("texture_size") : null;
            if (groups == null || elements == null || texSize == null || texSize.size() < 2) {
                return buildFallbackSlice(textureId);
            }
            Set<Integer> headIndices = new HashSet<>();
            for (JsonElement groupEl : groups) {
                collectHeadElementIndices(groupEl, false, headIndices);
            }
            if (headIndices.isEmpty()) {
                return buildFallbackSlice(textureId);
            }
            float bestArea = -1f;
            float[] bestUv = null;
            for (Integer index : headIndices) {
                if (index == null || index < 0 || index >= elements.size()) continue;
                JsonElement elementEl = elements.get(index);
                if (!elementEl.isJsonObject()) continue;
                JsonObject obj = elementEl.getAsJsonObject();
                JsonObject faces = obj.has("faces") && obj.get("faces").isJsonObject() ? obj.getAsJsonObject("faces") : null;
                if (faces == null) continue;
                JsonObject north = faces.has("north") && faces.get("north").isJsonObject() ? faces.getAsJsonObject("north") : null;
                if (north == null || !north.has("uv") || !north.get("uv").isJsonArray()) continue;
                JsonArray uv = north.getAsJsonArray("uv");
                if (uv.size() < 4) continue;
                float x1 = uv.get(0).getAsFloat();
                float y1 = uv.get(1).getAsFloat();
                float x2 = uv.get(2).getAsFloat();
                float y2 = uv.get(3).getAsFloat();
                float area = Math.abs((x2 - x1) * (y2 - y1));
                if (area > bestArea) {
                    bestArea = area;
                    bestUv = new float[]{Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2)};
                }
            }
            if (bestUv == null) {
                return buildFallbackSlice(textureId);
            }
            int texW = Math.max(1, texSize.get(0).getAsInt());
            int texH = Math.max(1, texSize.get(1).getAsInt());
            int u = clamp(Math.round(bestUv[0]), 0, texW - 1);
            int v = clamp(Math.round(bestUv[1]), 0, texH - 1);
            int w = Math.max(1, clamp(Math.round(bestUv[2] - bestUv[0]), 1, texW - u));
            int h = Math.max(1, clamp(Math.round(bestUv[3] - bestUv[1]), 1, texH - v));
            return new PortraitSlice(textureId, u, v, w, h, texW, texH);
        } catch (Exception ignored) {
            return buildFallbackSlice(textureId);
        }
    }

    private static PortraitSlice buildFallbackSlice(Identifier textureId) {
        int[] size = imageSize(textureId);
        int texW = Math.max(1, size[0]);
        int texH = Math.max(1, size[1]);
        int crop = Math.max(1, Math.min(texW, texH));
        int u = Math.max(0, (texW - crop) / 2);
        int v = Math.max(0, (texH - crop) / 2);
        return new PortraitSlice(textureId, u, v, crop, crop, texW, texH);
    }

    private static void collectHeadElementIndices(JsonElement node, boolean inHeadBranch, Set<Integer> into) {
        if (node == null || node.isJsonNull()) return;
        if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isNumber()) {
            if (inHeadBranch) {
                into.add(node.getAsInt());
            }
            return;
        }
        if (!node.isJsonObject()) return;
        JsonObject obj = node.getAsJsonObject();
        String name = obj.has("name") ? obj.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
        boolean nextHead = inHeadBranch || name.contains("head") || name.contains("face") || name.contains("eye");
        if (!obj.has("children") || !obj.get("children").isJsonArray()) return;
        for (JsonElement child : obj.getAsJsonArray("children")) {
            collectHeadElementIndices(child, nextHead, into);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }



    private static Identifier findHeldItemDirectResource(String candidate) {
        String normalizedPath = candidate.trim().toLowerCase(Locale.ROOT)
                .replace("item.pokedemo.", "")
                .replace("pokedemo:", "")
                .replace("held_item=", "")
                .replace("helditem=", "")
                .replace('.', '_')
                .replace('-', '_')
                .replace(' ', '_');
        if (normalizedPath.isBlank()) return null;
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        for (String root : List.of("textures/item/held/", "textures/item/berries/", "textures/item/balls/", "textures/item/apricorns/", "textures/item/")) {
            Identifier id = Identifier.of("pokedemo", root + normalizedPath + ".png");
            if (rm.getResource(id).isPresent()) return id;
        }
        return null;
    }

    private static List<String> heldItemCandidates(String heldItemId) {
        List<String> out = new ArrayList<>();
        out.add(heldItemId);
        String value = heldItemId.trim();
        int colon = value.indexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) {
            out.add(value.substring(colon + 1));
        }
        int lastSlash = value.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 1 < value.length()) {
            out.add(value.substring(lastSlash + 1));
        }
        out.add(value.replace("item.pokedemo.", ""));
        out.add(value.replace("pokedemo:", ""));
        out.add(value.replace("held_item=", ""));
        out.add(value.replace("helditem=", ""));
        out.add(value.replace('-', '_'));
        out.add(value.replace(' ', '_'));
        return out;
    }

    public record PortraitSlice(Identifier texture, int u, int v, int width, int height, int textureWidth, int textureHeight) {}

    private static int namespacePriority(String namespace) {
        if (namespace == null) return 9;
        return switch (namespace) {
            case "pokedemo" -> 0;
            case "pokedemo_bridge" -> 1;
            case "cobblemon" -> 2;
            case "minecraft" -> 5;
            default -> 9;
        };
    }

    private static boolean isPreferredSpeciesTexture(String file, String folder) {
        if (file == null || file.isBlank()) return false;
        String nf = normalize(file);
        if (nf.contains("shiny") || nf.contains("female") || nf.contains("male") || nf.contains("sleep") || nf.contains("blink")) {
            return false;
        }
        if (folder == null || folder.isBlank()) return true;
        String nd = normalize(folder);
        return nd.endsWith(nf) || nf.endsWith(nd) || nd.contains(nf);
    }

    private static String fileStem(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = file.lastIndexOf('.');
        return dot >= 0 ? file.substring(0, dot) : file;
    }

    private static String parentStem(String path) {
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return "";
        String parent = path.substring(0, slash);
        int parentSlash = parent.lastIndexOf('/');
        String dir = parentSlash >= 0 ? parent.substring(parentSlash + 1) : parent;
        int underscore = dir.indexOf('_');
        return underscore >= 0 ? dir.substring(underscore + 1) : dir;
    }


    private static String normalizeDisplay(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String normalize(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Integer extractCustomModelData(net.minecraft.item.ItemStack stack) {
        try {
            Object component = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_MODEL_DATA);
            if (component == null) return null;
            for (String methodName : java.util.List.of("floats", "getFloats", "ints", "getInts", "value", "getValue")) {
                try {
                    java.lang.reflect.Method m = component.getClass().getMethod(methodName);
                    Object value = m.invoke(component);
                    Integer parsed = parseCustomModelValue(value);
                    if (parsed != null) return parsed;
                } catch (NoSuchMethodException ignored) {
                }
            }
            for (java.lang.reflect.Method m : component.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                Object value = m.invoke(component);
                Integer parsed = parseCustomModelValue(value);
                if (parsed != null) return parsed;
            }
        } catch (Throwable ignored) {
        }
        try {
            String dump = String.valueOf(stack.getComponents());
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("custom_model_data[^0-9-]*(-?\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(dump);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Integer parseCustomModelValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return Math.round(n.floatValue());
        if (value instanceof java.util.List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Number n) return Math.round(n.floatValue());
            if (first != null) {
                String cleaned = String.valueOf(first).replaceAll("[^0-9-]", "");
                if (!cleaned.isBlank()) {
                    try { return Integer.parseInt(cleaned); } catch (Exception ignored) {}
                }
            }
        }
        String str = String.valueOf(value);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+").matcher(str);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group()); } catch (Exception ignored) {}
        }
        return null;
    }

}