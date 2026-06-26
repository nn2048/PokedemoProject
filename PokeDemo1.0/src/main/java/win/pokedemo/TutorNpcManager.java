package win.pokedemo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TutorNpcManager implements Listener {
    private final PokeDemoPlugin plugin;
    private final Storage storage;
    private final Dex dex;
    private final LearnsetManager learnsets;
    private final TmManager tmManager;
    private final NamespacedKey keyTutorNpc;
    private final NamespacedKey keyTutorTimed;
    private final NamespacedKey keyTutorMoves;
    private final Random rnd = new Random();
    private final List<String> tutorMovePool = new ArrayList<>();
    private UUID activeTutor = null;

    public TutorNpcManager(PokeDemoPlugin plugin, Storage storage, Dex dex, LearnsetManager learnsets, TmManager tmManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.dex = dex;
        this.learnsets = learnsets;
        this.tmManager = tmManager;
        this.keyTutorNpc = new NamespacedKey(plugin, "tutor_npc");
        this.keyTutorTimed = new NamespacedKey(plugin, "tutor_timed");
        this.keyTutorMoves = new NamespacedKey(plugin, "tutor_moves");
        loadTutorMovePool();
    }

    public NamespacedKey getTutorKey() { return keyTutorNpc; }

    public void start() {
        cleanupTimedTutors();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRandomSpawn, 20L * 30L, 20L * 600L);
    }

    public void shutdown() { cleanupTimedTutors(); }

    public void cleanupTimedTutors() {
        activeTutor = null;
        for (World w : Bukkit.getWorlds()) {
            for (Villager v : w.getEntitiesByClass(Villager.class)) {
                Byte b = v.getPersistentDataContainer().get(keyTutorNpc, PersistentDataType.BYTE);
                Byte timed = v.getPersistentDataContainer().get(keyTutorTimed, PersistentDataType.BYTE);
                if (b != null && b != 0 && timed != null && timed != 0) v.remove();
            }
        }
    }

    public int cleanupTutorsInChunk(org.bukkit.Chunk chunk) {
        if (chunk == null) return 0;
        int removed = 0;
        if (activeTutor != null) {
            Entity e = chunk.getWorld().getEntity(activeTutor);
            if (e != null && e.isValid() && e.getChunk().getX() == chunk.getX() && e.getChunk().getZ() == chunk.getZ() && e.getWorld().equals(chunk.getWorld())) {
                activeTutor = null;
            }
        }
        for (Entity ent : chunk.getEntities()) {
            if (!(ent instanceof Villager v)) continue;
            Byte b = v.getPersistentDataContainer().get(keyTutorNpc, PersistentDataType.BYTE);
            if (b != null && b != 0) { v.remove(); removed++; }
        }
        return removed;
    }

    public int cleanupAllTutors() {
        int removed = 0;
        activeTutor = null;
        for (World w : Bukkit.getWorlds()) {
            for (Villager v : w.getEntitiesByClass(Villager.class)) {
                Byte b = v.getPersistentDataContainer().get(keyTutorNpc, PersistentDataType.BYTE);
                if (b != null && b != 0) { v.remove(); removed++; }
            }
        }
        return removed;
    }

    private void loadTutorMovePool() {
        tutorMovePool.clear();
        try (InputStream in = TutorNpcManager.class.getClassLoader().getResourceAsStream("default_data/moves_raw/tutor_alias_moves.json")) {
            if (in == null) return;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = obj.getAsJsonArray("moves");
            if (arr == null) return;
            for (var el : arr) {
                String id = el.getAsString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (id.isBlank()) continue;
                if (!dex.isMoveAllowed(id)) continue;
                tutorMovePool.add(id);
            }
        } catch (Throwable ignored) {}
    }

    private void tickRandomSpawn() {
        try {
            if (getActiveTutorEntity() != null) return;
            List<Player> online = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) if (p.isOnline() && !p.isDead()) online.add(p);
            if (online.isEmpty()) return;
            if (rnd.nextInt(6) != 0) return;
            Player near = online.get(rnd.nextInt(online.size()));
            Location loc = findSpawnNear(near);
            if (loc == null) return;
            spawnTutorNpc(loc, true, null);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Tutor] random spawn tick failed: " + t.getMessage());
        }
    }

    private Entity getActiveTutorEntity() {
        if (activeTutor == null) return null;
        for (World w : Bukkit.getWorlds()) {
            Entity e = w.getEntity(activeTutor);
            if (e != null && e.isValid()) return e;
        }
        activeTutor = null;
        return null;
    }

    private Location findSpawnNear(Player p) {
        World w = p.getWorld();
        for (int i = 0; i < 18; i++) {
            int dx = rnd.nextInt(33) - 16;
            int dz = rnd.nextInt(33) - 16;
            int x = p.getLocation().getBlockX() + dx;
            int z = p.getLocation().getBlockZ() + dz;
            int y = w.getHighestBlockYAt(x, z);
            if (y <= w.getMinHeight()) continue;
            Block feet = w.getBlockAt(x, y + 1, z);
            Block head = w.getBlockAt(x, y + 2, z);
            Block ground = w.getBlockAt(x, y, z);
            if (!ground.getType().isSolid()) continue;
            if (!feet.isPassable() || !head.isPassable()) continue;
            return feet.getLocation().add(0.5, 0, 0.5);
        }
        return p.getLocation().clone().add(1.5, 0, 0.5);
    }

    public void spawnTutorNpc(Location loc, boolean timed, CommandSender feedbackTo) {
        if (loc == null || loc.getWorld() == null) return;
        if (timed) {
            Entity existing = getActiveTutorEntity();
            if (existing != null) existing.remove();
        }
        List<String> fixedMoves = randomTutorMoves();
        Villager v = loc.getWorld().spawn(loc, Villager.class, villager -> {
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);
            try { villager.setGravity(true); } catch (Throwable ignored) {}
            villager.setCustomNameVisible(true);
            villager.setCustomName("§b招式教学师");
            try { villager.setProfession(Villager.Profession.LIBRARIAN); } catch (Throwable ignored) {}
            villager.getPersistentDataContainer().set(keyTutorNpc, PersistentDataType.BYTE, (byte) 1);
            villager.getPersistentDataContainer().set(keyTutorTimed, PersistentDataType.BYTE, timed ? (byte) 1 : (byte) 0);
            villager.getPersistentDataContainer().set(keyTutorMoves, PersistentDataType.STRING, encodeTutorMoves(fixedMoves));
        });
        if (timed) activeTutor = v.getUniqueId();
        if (feedbackTo != null) feedbackTo.sendMessage((timed ? "§a已召唤限时招式教学师：§f" : "§a已召唤常驻招式教学师：§f") + v.getLocation().getBlockX() + " " + v.getLocation().getBlockY() + " " + v.getLocation().getBlockZ());
        if (timed) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    Entity ent = getActiveTutorEntity();
                    if (ent != null && ent.getUniqueId().equals(v.getUniqueId())) {
                        ent.remove();
                        activeTutor = null;
                    }
                } catch (Throwable ignored) {}
            }, 20L * 600L);
        }
    }


    private String encodeTutorMoves(List<String> moves) {
        if (moves == null || moves.isEmpty()) return "";
        return String.join(",", moves);
    }

    private List<String> decodeTutorMoves(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split(",")) {
            String id = part == null ? "" : part.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!id.isBlank()) out.add(id);
        }
        return out;
    }

    private List<String> getFixedTutorMoves(Entity ent) {
        if (ent == null) return randomTutorMoves();
        String raw = ent.getPersistentDataContainer().get(keyTutorMoves, PersistentDataType.STRING);
        List<String> decoded = decodeTutorMoves(raw);
        if (!decoded.isEmpty()) return decoded;
        List<String> generated = randomTutorMoves();
        ent.getPersistentDataContainer().set(keyTutorMoves, PersistentDataType.STRING, encodeTutorMoves(generated));
        return generated;
    }
    private List<String> randomTutorMoves() {
        ArrayList<String> src = new ArrayList<>(tutorMovePool);
        Collections.shuffle(src, rnd);
        int count = Math.max(3, Math.min(10, 3 + rnd.nextInt(8)));
        if (src.size() > count) return new ArrayList<>(src.subList(0, count));
        return src;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity ent = e.getRightClicked();
        if (ent == null) return;
        Byte b = ent.getPersistentDataContainer().get(keyTutorNpc, PersistentDataType.BYTE);
        if (b == null || b == 0) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        try { if (plugin.battles() != null && plugin.battles().isInBattle(p.getUniqueId())) { p.sendMessage("§c战斗中不能使用该功能。"); return; } } catch (Throwable ignored) {}
        TutorGui.openMoveList(p, getFixedTutorMoves(ent));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity ent = e.getEntity();
        Byte b = ent.getPersistentDataContainer().get(keyTutorNpc, PersistentDataType.BYTE);
        if (b != null && b != 0) e.setCancelled(true);
    }

    public void handleMoveListClick(Player player, GuiHolder holder, int rawSlot) {
        if (rawSlot == 49 || rawSlot == 53) { player.closeInventory(); return; }
        if (holder.tutorMoves == null || holder.tutorMoves.isEmpty()) return;
        int idx = -1;
        int[] usable = new int[]{10,11,12,13,14,15,16,28,29,30};
        for (int i = 0; i < usable.length; i++) if (usable[i] == rawSlot) { idx = i; break; }
        if (idx < 0 || idx >= holder.tutorMoves.size()) return;
        String moveId = holder.tutorMoves.get(idx);
        TutorGui.openPartySelect(player, storage, moveId, holder.tutorMoves);
    }

    public void handlePartySelectClick(Player player, GuiHolder holder, int rawSlot) {
        if (rawSlot == 26) { TutorGui.openMoveList(player, holder.tutorMoves == null ? randomTutorMoves() : holder.tutorMoves); return; }
        if (holder.tutorSelectedMove == null || holder.tutorSelectedMove.isBlank()) return;
        if (rawSlot < 0 || rawSlot >= 6) return;
        PlayerProfile prof = storage.getProfile(player.getUniqueId());
        if (prof == null || rawSlot >= prof.party.size()) return;
        PokemonInstance mon = prof.party.get(rawSlot);
        if (mon == null || mon.isEgg) return;
        String moveId = holder.tutorSelectedMove.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        Species sp = dex.getSpeciesFlexible(mon.speciesId);
        if (sp == null) { player.sendMessage("§c未知精灵数据。" ); return; }
        if (!tmManager.ensureLoaded()) { player.sendMessage("§c教学招式数据缺失，无法判定可学习性。" ); return; }
        if (!tmManager.canLearnTutor(sp.id(), moveId)) {
            player.sendMessage("§7" + mon.displayName() + " 无法向教学师学习 §f" + plugin.getLang().move(moveId, null) + "§7。");
            return;
        }
        if (mon.knowsMove(moveId)) {
            player.sendMessage("§7" + mon.displayName() + " 已经学会了 §f" + plugin.getLang().move(moveId, null) + "§7。");
            return;
        }
        if (mon.moves != null && mon.moves.size() < 4) {
            learnsets.tryLearnMoveFromItem(player.getUniqueId(), mon, moveId);
            storage.markDirty(player.getUniqueId());
            player.closeInventory();
            return;
        }
        try { plugin.getPendingItemConsumeManager().setPending(player.getUniqueId(), "__tutor__", moveId, false); } catch (Throwable ignored) {}
        learnsets.tryLearnMoveFromItem(player.getUniqueId(), mon, moveId);
        storage.markDirty(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> UtilGui.openMoveLearn(player, storage, mon.uuid, true));
    }
}
