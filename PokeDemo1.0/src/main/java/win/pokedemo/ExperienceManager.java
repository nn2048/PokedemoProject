package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Minimal EXP system (经验值系统).
 *
 * - Wild pokemon are wolves with KEY_WILD + species + level in PDC.
 * - Summoned pokemon are wolves with KEY_OWNER + KEY_PUUID.
 *
 * When a summoned pokemon defeats another pokemon (wild or summoned),
 * it gains exp according to a simple Pokemon-like formula.
 */
public class ExperienceManager implements Listener {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final Storage storage;
    private final SummonManager summons;
    private final EvolutionManager evolutions;
    private final LearnsetManager learnsets;

    public ExperienceManager(PokeDemoPlugin plugin, Dex dex, Storage storage, SummonManager summons, EvolutionManager evolutions, LearnsetManager learnsets) {
        this.plugin = plugin;
        this.dex = dex;
        this.storage = storage;
        this.summons = summons;
        this.evolutions = evolutions;
        this.learnsets = learnsets;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Wolf defeatedWolf)) return;

        // Determine defeated pokemon data
        Defeated defeated = getDefeated(defeatedWolf);
        if (defeated == null) return; // not a pokemon

        // Find attacker (only grant exp if attacker is a summoned pokemon)
        EntityDamageByEntityEvent last = null;
        if (defeatedWolf.getLastDamageCause() instanceof EntityDamageByEntityEvent by) {
            last = by;
        }
        if (last == null) return;
        Entity damager = last.getDamager();
        if (!(damager instanceof Wolf attackerWolf)) return;
        if (!summons.isSummonedPokemonEntity(attackerWolf)) return;

        UUID attackerOwner = summons.getOwnerUuidFromEntity(attackerWolf);
        UUID attackerPuuid = summons.getPokemonUuidFromEntity(attackerWolf);
        if (attackerOwner == null || attackerPuuid == null) return;

        // Optional: PvP exp switch
        boolean allowPvP = plugin.getConfig().getBoolean("exp.allow-exp-from-pvp", true);
        if (!allowPvP && defeated.isPlayerOwned) {
            return;
        }

        Species defeatedSpecies = dex.getSpecies(defeated.speciesId);
        if (defeatedSpecies == null) return;

        long gained = calculateExp(defeatedSpecies, defeated.level);
        if (gained <= 0) return;

        PlayerProfile prof = storage.getProfile(attackerOwner);
        PokemonInstance attacker = prof.findByUuid(attackerPuuid);
        if (attacker == null) return;
        try {
            if ("bisharp".equalsIgnoreCase(attacker.speciesId)
                    && "bisharp".equalsIgnoreCase(defeated.speciesId)
                    && defeated.isPlayerOwned
                    && defeated.instance != null
                    && "kings_rock".equalsIgnoreCase(defeated.instance.heldItemId)) {
                evolutions.onBisharpLeaderDefeat(attackerOwner, attacker);
            }
        } catch (Throwable ignored) {}
        Species attackerSpecies = dex.getSpecies(attacker.speciesId);
        if (attackerSpecies == null) return;

        // Lucky Egg: boost EXP gained (modern: 1.5x).
        if (attacker.heldItemId != null && attacker.heldItemId.equalsIgnoreCase("lucky_egg")) {
            gained = Math.max(1, (long) Math.floor(gained * 1.5));
        }

        // EV gain (努力值掉落)
        int evBefore = attacker.evHp + attacker.evAtk + attacker.evDef + attacker.evSpa + attacker.evSpd + attacker.evSpe;
        applyEvGain(attacker, defeatedSpecies);
        int evAfter = attacker.evHp + attacker.evAtk + attacker.evDef + attacker.evSpa + attacker.evSpd + attacker.evSpe;


        long beforeTotal = attacker.totalExp;
        int beforeLv = attacker.level;

        attacker.totalExp = Math.max(attacker.totalExp, ExpCurve.totalExpAtLevel(attackerSpecies.expGroup(), attacker.level));
        attacker.totalExp += gained;
        int newLv = ExpCurve.levelForTotalExp(attackerSpecies.expGroup(), attacker.totalExp);
        if (newLv > attacker.level) {
            attacker.level = newLv;
            attacker.addFriendship(2);
            // Heal on level up (demo friendly)
            attacker.currentHp = attacker.maxHp(attackerSpecies);
        }
        storage.markDirty(attackerOwner);

        // Apply stat changes to live entity on next tick
        // - Level up: always refresh.
        // - EV gain without level up: refresh max HP (and label) so players feel EVs are real.
        if (newLv != beforeLv || evAfter != evBefore) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!attackerWolf.isValid() || attackerWolf.isDead()) return;
                int maxHp = Math.max(1, attacker.maxHp(attackerSpecies));
                var attr = attackerWolf.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) attr.setBaseValue(maxHp);
                // If no level up, keep current HP ratio as much as possible
                double cur = attackerWolf.getHealth();
                double oldMax = attr != null ? attr.getBaseValue() : maxHp;
                if (newLv != beforeLv) {
                    attackerWolf.setHealth(maxHp);
                } else {
                    // clamp
                    attackerWolf.setHealth(Math.min(maxHp, Math.max(1.0, cur)));
                }
                // update label if carrier exists
                summons.refreshCarrierLabel(attackerWolf, attacker);
            });
        }

        // Notify player
        var player = Bukkit.getPlayer(attackerOwner);
        if (player != null && player.isOnline()) {
            if (newLv > beforeLv) {
                player.sendMessage(plugin.getLang().uiFmt("exp.gain_levelup", "§a{mon} 获得了 §e{exp}§a 经验值！§7 Lv.{from} → §bLv.{to}", java.util.Map.of("mon", attacker.displayName(), "exp", String.valueOf(gained), "from", String.valueOf(beforeLv), "to", String.valueOf(newLv))));
                evolutions.notifyIfCanEvolve(attackerOwner, attacker);
                if (learnsets != null) learnsets.onLevelUp(attackerOwner, attacker, attackerSpecies, beforeLv, newLv);
            } else {
                player.sendMessage(plugin.getLang().uiFmt("exp.gain", "§a{mon} 获得了 §e{exp}§a 经验值！", java.util.Map.of("mon", attacker.displayName(), "exp", String.valueOf(gained))));
            }
        }
    }

    private long calculateExp(Species defeated, int defeatedLevel) {
        double mult = plugin.getConfig().getDouble("exp.global-multiplier", 1.0);
        int baseYield = Math.max(1, defeated.baseExpYield());
        int lvl = Util.clamp(defeatedLevel, 1, 100);
        double raw = (baseYield * (double) lvl) / 7.0;
        long gained = (long) Math.floor(raw * mult);
        return Math.max(1, gained);
    }


    private void applyEvGain(PokemonInstance winner, Species defeatedSpecies) {
        boolean enabled = plugin.getConfig().getBoolean("ev.enabled", true);
        if (!enabled) return;
        if (winner == null || defeatedSpecies == null) return;

        double mult = plugin.getConfig().getDouble("ev.multiplier", 1.0);
        int perStatCap = plugin.getConfig().getInt("ev.per-stat-cap", 252);
        int totalCap = plugin.getConfig().getInt("ev.total-cap", 510);

        // Base yields from defeated species
        int yHp = Math.max(0, defeatedSpecies.ev("hp"));
        int yAtk = Math.max(0, defeatedSpecies.ev("atk"));
        int yDef = Math.max(0, defeatedSpecies.ev("def"));
        int ySpa = Math.max(0, defeatedSpecies.ev("spa"));
        int ySpd = Math.max(0, defeatedSpecies.ev("spd"));
        int ySpe = Math.max(0, defeatedSpecies.ev("spe"));

        // Apply multiplier and floor
        yHp = (int)Math.floor(yHp * mult);
        yAtk = (int)Math.floor(yAtk * mult);
        yDef = (int)Math.floor(yDef * mult);
        ySpa = (int)Math.floor(ySpa * mult);
        ySpd = (int)Math.floor(ySpd * mult);
        ySpe = (int)Math.floor(ySpe * mult);

        // Held-item EV modifiers (Macho Brace / Power items)
        int[] mod = HeldItemEffects.modifyEvYields(winner, yHp, yAtk, yDef, ySpa, ySpd, ySpe);
        yHp = mod[0]; yAtk = mod[1]; yDef = mod[2]; ySpa = mod[3]; ySpd = mod[4]; ySpe = mod[5];

        if (yHp + yAtk + yDef + ySpa + ySpd + ySpe <= 0) return;

        // Current totals
        int curTotal = winner.evHp + winner.evAtk + winner.evDef + winner.evSpa + winner.evSpd + winner.evSpe;
        if (curTotal >= totalCap) return;

        // Apply per-stat cap and total cap
        winner.evHp = clampEvAdd(winner.evHp, yHp, perStatCap);
        winner.evAtk = clampEvAdd(winner.evAtk, yAtk, perStatCap);
        winner.evDef = clampEvAdd(winner.evDef, yDef, perStatCap);
        winner.evSpa = clampEvAdd(winner.evSpa, ySpa, perStatCap);
        winner.evSpd = clampEvAdd(winner.evSpd, ySpd, perStatCap);
        winner.evSpe = clampEvAdd(winner.evSpe, ySpe, perStatCap);

        // Re-check total cap and trim if exceeded (trim in a stable order)
        int newTotal = winner.evHp + winner.evAtk + winner.evDef + winner.evSpa + winner.evSpd + winner.evSpe;
        if (newTotal > totalCap) {
            int over = newTotal - totalCap;
            over = trimEv(winner, over);
        }
    }

    private int clampEvAdd(int cur, int add, int cap) {
        if (add <= 0) return cur;
        return Math.min(cap, cur + add);
    }

    private int trimEv(PokemonInstance p, int over) {
        // Trim in order: spe, spa, atk, def, spd, hp
        int[] vals = new int[]{p.evSpe, p.evSpa, p.evAtk, p.evDef, p.evSpd, p.evHp};
        for (int i = 0; i < vals.length && over > 0; i++) {
            int take = Math.min(vals[i], over);
            vals[i] -= take;
            over -= take;
        }
        p.evSpe = vals[0];
        p.evSpa = vals[1];
        p.evAtk = vals[2];
        p.evDef = vals[3];
        p.evSpd = vals[4];
        p.evHp = vals[5];
        return over;
    }

    private Defeated getDefeated(Wolf wolf) {
        // Wild
        Byte wild = wolf.getPersistentDataContainer().get(plugin.KEY_WILD, PersistentDataType.BYTE);
        if (wild != null && wild == (byte) 1) {
            String sid = wolf.getPersistentDataContainer().get(plugin.KEY_SPECIES, PersistentDataType.STRING);
            Integer lv = wolf.getPersistentDataContainer().get(plugin.KEY_LEVEL, PersistentDataType.INTEGER);
            if (sid == null || lv == null) return null;
            return new Defeated(sid, lv, false, null);
        }
        // Summoned
        if (!summons.isSummonedPokemonEntity(wolf)) return null;
        UUID owner = summons.getOwnerUuidFromEntity(wolf);
        UUID puuid = summons.getPokemonUuidFromEntity(wolf);
        if (owner == null || puuid == null) return null;
        PlayerProfile prof = storage.getProfile(owner);
        PokemonInstance p = prof.findByUuid(puuid);
        if (p == null) return null;
        return new Defeated(p.speciesId, p.level, true, p);
    }

    private static final class Defeated {
        final String speciesId;
        final int level;
        final boolean isPlayerOwned;
        final PokemonInstance instance;

        Defeated(String speciesId, int level, boolean isPlayerOwned, PokemonInstance instance) {
            this.speciesId = speciesId;
            this.level = level;
            this.isPlayerOwned = isPlayerOwned;
            this.instance = instance;
        }
    }
}
