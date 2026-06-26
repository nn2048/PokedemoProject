package win.pokedemo;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Evolution system (进化系统) - Step 1:
 * - level_up evolutions
 * - manual evolve from Summary GUI button
 * - keep EXP/Level/Nature/IV/EV/Moves
 */
public class EvolutionManager {
    private final PokeDemoPlugin plugin;
    private final Dex dex;
    private final Storage storage;
    private final SummonManager summons;

    public EvolutionManager(PokeDemoPlugin plugin, Dex dex, Storage storage, SummonManager summons) {
        this.plugin = plugin;
        this.dex = dex;
        this.storage = storage;
        this.summons = summons;
    }

    public List<Evolution> getAvailableEvolutions(PokemonInstance p) {
        return getAvailableEvolutions(null, p);
    }

    public List<Evolution> getAvailableEvolutions(UUID ownerId, PokemonInstance p) {
        if (p == null) return List.of();
        if (p.heldItemId != null && p.heldItemId.equalsIgnoreCase("everstone")) return List.of();
        Species s = dex.getSpeciesFlexible(p.speciesId);
        if (s == null) return List.of();
        List<Evolution> out = new ArrayList<>();
        for (Evolution e : s.evolutionsSafe()) {
            if (e == null) continue;
            if (e.isLevelUp() && e.minLevel() > 0 && p.level >= e.minLevel()) {
                if (dex.getSpeciesFlexible(e.result()) != null) out.add(e);
            }
        }
        addSpecialEvolutionResult(out, resolveMoveEvolutionTarget(ownerId, p));
        addSpecialEvolutionResult(out, resolveFriendshipTimeEvolutionTarget(ownerId, p));
        addSpecialEvolutionResult(out, resolveCounterSpecialEvolutionTarget(ownerId, p));
        if (ownerId != null) {
            String sid = p.speciesId == null ? "" : p.speciesId.toLowerCase(Locale.ROOT);
            if ("sneasel".equals(sid) && hasHeldItem(p, "razor_claw") && isNight(ownerId)) addSpecialEvolutionResult(out, "weavile");
            if ("gligar".equals(sid) && hasHeldItem(p, "razor_fang") && isNight(ownerId)) addSpecialEvolutionResult(out, "gliscor");
            if ("happiny".equals(sid) && hasHeldItem(p, "oval_stone") && isDay(ownerId)) addSpecialEvolutionResult(out, "chansey");
            if ("mantyke".equals(sid) && partyHasSpecies(ownerId, "remoraid")) addSpecialEvolutionResult(out, "mantine");
            if ("pancham".equals(sid) && partyHasType(ownerId, "dark")) addSpecialEvolutionResult(out, "pangoro");
            if ("tyrogue".equals(sid)) {
                int atk = calcBattlelessStat(p, s, "atk");
                int def = calcBattlelessStat(p, s, "def");
                if (atk > def) addSpecialEvolutionResult(out, "hitmonlee");
                else if (atk < def) addSpecialEvolutionResult(out, "hitmonchan");
                else addSpecialEvolutionResult(out, "hitmontop");
            }
        }
        return out;
    }

    private void addSpecialEvolutionResult(List<Evolution> out, String resultId) {
        if (out == null || resultId == null || resultId.isBlank()) return;
        if (dex.getSpeciesFlexible(resultId) == null) return;
        for (Evolution e : out) {
            if (e != null && resultId.equalsIgnoreCase(e.result())) return;
        }
        out.add(new Evolution("special", 1, resultId, List.of()));
    }

    public String prettyResultName(String resultId) {
        if (resultId == null) return "未知";
        Species s = dex.getSpeciesFlexible(resultId);
        LangManager lang = PokeDemoPlugin.INSTANCE.getLang();
        if (lang != null) {
            return lang.species(resultId, s != null ? s.name() : null);
        }
        if (s != null && s.name() != null) return s.name();
        return Util.titleCase(resultId);
    }

    private boolean consumeRegularPokeBall(Player owner) {
        if (owner == null) return false;
        org.bukkit.inventory.PlayerInventory inv = owner.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            org.bukkit.inventory.ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == org.bukkit.Material.AIR) continue;
            String id = plugin.getItems().getItemId(it);
            if (!"poke_ball".equalsIgnoreCase(id)) continue;
            int amt = Math.max(0, it.getAmount() - 1);
            if (amt <= 0) inv.setItem(i, null);
            else it.setAmount(amt);
            return true;
        }
        return false;
    }

    private void maybeSpawnShedinja(Player owner, PokemonInstance original, String oldSpeciesId, String targetSpeciesId) {
        if (owner == null || original == null) return;
        if (!"nincada".equalsIgnoreCase(oldSpeciesId) || !"ninjask".equalsIgnoreCase(targetSpeciesId)) return;

        PlayerProfile prof = storage.getProfile(owner.getUniqueId());
        if (prof == null) return;
        if (prof.party == null || prof.party.size() >= 6) {
            owner.sendMessage("§7土居忍士进化了，但队伍没有空位，因此没有获得脱壳忍者。");
            return;
        }
        if (!consumeRegularPokeBall(owner)) {
            owner.sendMessage("§7土居忍士进化了，但背包中没有额外的普通精灵球，因此没有获得脱壳忍者。");
            return;
        }

        Species shedinjaSpecies = dex.getSpeciesFlexible("shedinja");
        if (shedinjaSpecies == null) {
            owner.sendMessage("§7脱壳忍者的数据当前未导入，跳过额外生成。");
            return;
        }

        PokemonInstance shed = original.deepCopyPersisted();
        shed.uuid = java.util.UUID.randomUUID();
        shed.speciesId = shedinjaSpecies.id();
        shed.speciesName = shedinjaSpecies.name();
        shed.nickname = null;
        shed.ballId = "poke_ball";
        shed.currentHp = 1;
        shed.status = "none";
        shed.uiLocked = false;
        shed.uiLockReason = "";
        shed.overrideMoves = null;
        shed.overrideSpeciesId = null;
        shed.overrideType1 = null;
        shed.overrideType2 = null;
        shed.lockedMaxHp = null;
        prof.party.add(shed);
        storage.markDirty(owner.getUniqueId());
        owner.sendMessage("§d特殊进化：你额外获得了 §f脱壳忍者§d！");
    }

    /**
     * Evolve the pokemon to the given species id.
     * @return true if evolved
     */
    public boolean evolveNow(Player owner, PokemonInstance p, String targetSpeciesId) {
        if (owner == null || p == null || targetSpeciesId == null) return false;

        // Everstone: prevents evolution while held.
        if (p.heldItemId != null && p.heldItemId.equalsIgnoreCase("everstone")) {
            owner.sendMessage(plugin.getLang().uiFmt("evo.blocked_everstone", "§6[进化] §e{mon} §7携带了§f不变之石§7，无法进化。", java.util.Map.of("mon", p.displayName())));
            return false;
        }
        targetSpeciesId = targetSpeciesId.toLowerCase(Locale.ROOT);

        String oldSpeciesId = p.speciesId;
        String oldAbilityId = p.abilityId;
        Species oldS = dex.getSpeciesFlexible(oldSpeciesId);
        Species newS = dex.getSpeciesFlexible(targetSpeciesId);
        if (newS == null) return false;

        int oldMax = oldS != null ? Math.max(1, p.maxHp(oldS)) : Math.max(1, p.currentHp);
        double ratio = oldMax > 0 ? (p.currentHp / (double) oldMax) : 1.0;

        // swap species
        p.speciesId = newS.id();
        p.speciesName = newS.name();

        // Ability inheritance across evolution:
        // Preserve hidden-ability status or normal ability slot index when possible.
        try {
            if (oldSpeciesId != null && oldAbilityId != null && !oldAbilityId.isBlank()) {
                boolean wasHidden = dex.isHiddenAbility(oldSpeciesId, oldAbilityId);
                java.util.List<String> oldNormals = dex.getNormalAbilityIds(oldSpeciesId);
                int slot = -1; // -1 hidden, 0/1 normal
                if (!wasHidden && oldNormals != null) {
                    for (int i = 0; i < oldNormals.size(); i++) {
                        if (oldAbilityId.equalsIgnoreCase(oldNormals.get(i))) { slot = i; break; }
                    }
                }

                if (wasHidden) {
                    String nh = dex.getHiddenAbilityId(p.speciesId);
                    if (nh != null && !nh.isBlank()) {
                        p.abilityId = nh;
                    } else {
                        // fallback to first normal ability
                        java.util.List<String> nn = dex.getNormalAbilityIds(p.speciesId);
                        if (nn != null && !nn.isEmpty()) p.abilityId = nn.get(0);
                    }
                } else if (slot >= 0) {
                    java.util.List<String> nn = dex.getNormalAbilityIds(p.speciesId);
                    if (nn != null && !nn.isEmpty()) {
                        p.abilityId = nn.get(Math.min(slot, nn.size() - 1));
                    }
                } else {
                    // If we can't determine a slot, keep the ability if it exists in new species; otherwise pick a normal.
                    boolean exists = false;
                    java.util.List<String> nn = dex.getNormalAbilityIds(p.speciesId);
                    if (nn != null) {
                        for (String a : nn) {
                            if (oldAbilityId.equalsIgnoreCase(a)) { exists = true; break; }
                        }
                    }
                    String nh = dex.getHiddenAbilityId(p.speciesId);
                    if (!exists && nh != null && oldAbilityId.equalsIgnoreCase(nh)) exists = true;
                    if (!exists) {
                        if (nn != null && !nn.isEmpty()) p.abilityId = nn.get(0);
                        else if (nh != null && !nh.isBlank()) p.abilityId = nh;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Recompute current HP proportionally to new max HP
        int newMax = Math.max(1, p.maxHp(newS));
        p.currentHp = Math.max(1, (int) Math.round(newMax * ratio));
        if (p.currentHp > newMax) p.currentHp = newMax;

        // Learnable moves on evolution (simple): if slots available, fill
        if (newS != null) {
            // actual learnable moves come from evolution entry; handled by caller if needed
        }

        storage.markDirty(owner.getUniqueId());

        // Update live summoned entity if present
        Wolf wolf = findSummonedWolf(owner.getUniqueId(), p.uuid);
        if (wolf != null && wolf.isValid() && !wolf.isDead()) {
            // refresh 3D model / label / health
            summons.refreshSummonedAppearance(wolf, p, newS);
        }

        // clear special evolution progress after success
        p.rageFistUses = 0;
        p.psyshieldBashUses = 0;
        p.barbBarrageUses = 0;
        p.basculinRecoilHp = 0;
        p.bisharpLeaderDefeats = 0;
        p.evolutionStepCounter = 0;
        if (!"gholdengo".equalsIgnoreCase(p.speciesId)) p.gimmighoulCoins = 0;

        owner.sendMessage(plugin.getLang().uiFmt("evo.success", "§6[进化] §a{from} §7→ §b{to} §a进化成功！", java.util.Map.of("from", (oldS != null ? oldS.name() : oldSpeciesId), "to", newS.name())));
        maybeSpawnShedinja(owner, p, oldSpeciesId, newS.id());
        return true;
    }

    public void notifyIfCanEvolve(UUID ownerId, PokemonInstance p) {
        Player pl = Bukkit.getPlayer(ownerId);
        if (pl == null || !pl.isOnline()) return;
        List<Evolution> evos = getAvailableEvolutions(ownerId, p);
        if (evos.isEmpty()) return;
        Evolution e = evos.get(0);
        pl.sendMessage(plugin.getLang().uiFmt("evo.ready", "§e{mon} §6似乎可以进化了！§7（打开精灵详情点击“进化”）§8 -> {to}", java.util.Map.of("mon", p.displayName(), "to", prettyResultName(e.result()))));
    }

    private static final int FRIENDSHIP_EVOLVE_THRESHOLD = 160;

    private boolean knowsMove(PokemonInstance p, String moveId) {
        return p != null && moveId != null && p.knowsMove(moveId);
    }

    private boolean knowsMoveType(PokemonInstance p, String moveType) {
        if (p == null || moveType == null) return false;
        for (MoveSlot ms : p.moves) {
            if (ms == null || ms.moveId == null || ms.moveId.isBlank()) continue;
            Move m = dex.getMove(ms.moveId);
            if (m != null && m.type() != null && moveType.equalsIgnoreCase(m.type())) return true;
        }
        return false;
    }

    private boolean hasHeldItem(PokemonInstance p, String itemId) {
        return p != null && p.heldItemId != null && itemId != null && p.heldItemId.equalsIgnoreCase(itemId);
    }

    private boolean partyHasSpecies(UUID ownerId, String speciesId) {
        PlayerProfile prof = ownerId == null ? null : storage.getProfile(ownerId);
        if (prof == null || prof.party == null || speciesId == null) return false;
        for (PokemonInstance x : prof.party) {
            if (x != null && x.speciesId != null && x.speciesId.equalsIgnoreCase(speciesId)) return true;
        }
        return false;
    }

    private boolean partyHasType(UUID ownerId, String typeId) {
        PlayerProfile prof = ownerId == null ? null : storage.getProfile(ownerId);
        if (prof == null || prof.party == null || typeId == null) return false;
        for (PokemonInstance x : prof.party) {
            if (x == null) continue;
            Species sx = dex.getSpeciesFlexible(x.speciesId);
            if (sx == null || sx.types() == null) continue;
            for (String t : sx.types()) {
                if (typeId.equalsIgnoreCase(t)) return true;
            }
        }
        return false;
    }

    private int calcBattlelessStat(PokemonInstance p, Species s, String stat) {
        if (p == null) return 0;
        return switch (stat.toLowerCase(Locale.ROOT)) {
            case "atk" -> p.calcStat(s, "atk", p.ivAtk, p.evAtk, false);
            case "def" -> p.calcStat(s, "def", p.ivDef, p.evDef, false);
            case "spa" -> p.calcStat(s, "spa", p.ivSpa, p.evSpa, false);
            case "spd" -> p.calcStat(s, "spd", p.ivSpd, p.evSpd, false);
            case "spe" -> p.calcStat(s, "spe", p.ivSpe, p.evSpe, false);
            default -> 0;
        };
    }

    private long currentWorldTime(Player owner) {
        World w = owner != null ? owner.getWorld() : null;
        if (w == null) {
            try {
                if (!Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
            } catch (Throwable ignored) {}
        }
        return w != null ? (w.getTime() % 24000L) : 6000L;
    }

    private boolean isDay(Player owner) {
        long t = currentWorldTime(owner);
        return t >= 0L && t < 12300L;
    }

    private boolean isDay(UUID ownerId) {
        Player pl = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        return isDay(pl);
    }

    private boolean isNight(Player owner) {
        long t = currentWorldTime(owner);
        return t >= 12300L && t < 24000L;
    }

    private boolean isNight(UUID ownerId) {
        Player pl = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        return isNight(pl);
    }

    private boolean isMale(PokemonInstance p) {
        return p != null && p.gender != null && p.gender.equalsIgnoreCase("M");
    }

    private boolean isFemale(PokemonInstance p) {
        return p != null && p.gender != null && p.gender.equalsIgnoreCase("F");
    }

    private boolean hasFriendship(PokemonInstance p, int threshold) {
        return p != null && p.friendshipValue() >= threshold;
    }

    private String resolveFriendshipTimeEvolutionTarget(UUID ownerId, PokemonInstance p) {
        if (p == null || p.speciesId == null) return null;
        String sid = p.speciesId.toLowerCase(Locale.ROOT);
        boolean day = isDay(ownerId);
        boolean night = isNight(ownerId);
        if (!hasFriendship(p, FRIENDSHIP_EVOLVE_THRESHOLD)) return null;
        return switch (sid) {
            case "pichu" -> "pikachu";
            case "cleffa" -> "clefairy";
            case "igglybuff" -> "jigglypuff";
            case "togepi" -> "togetic";
            case "azurill" -> "marill";
            case "golbat" -> "crobat";
            case "chansey" -> "blissey";
            case "buneary" -> "lopunny";
            case "woobat" -> "swoobat";
            case "swadloon" -> "leavanny";
            case "munchlax" -> "snorlax";
            case "type_null", "type: null", "type_null_" -> "silvally";
            case "eevee" -> knowsMoveType(p, "fairy") ? null : (day ? "espeon" : (night ? "umbreon" : null));
            case "budew" -> day ? "roselia" : null;
            case "chingling" -> night ? "chimecho" : null;
            case "riolu" -> day ? "lucario" : null;
            case "snom" -> night ? "frosmoth" : null;
            default -> null;
        };
    }

    private boolean isHisuianQwilfish(PokemonInstance p) {
        if (p == null) return false;
        String sid = p.speciesId == null ? "" : p.speciesId.toLowerCase(Locale.ROOT);
        String sn = p.speciesName == null ? "" : p.speciesName.toLowerCase(Locale.ROOT);
        return sid.contains("qwilfish") && (sid.contains("hisui") || sid.contains("hisuian") || sn.contains("hisui") || sn.contains("洗翠"));
    }

    private boolean isWhiteStripedBasculin(PokemonInstance p) {
        if (p == null) return false;
        String sid = p.speciesId == null ? "" : p.speciesId.toLowerCase(Locale.ROOT);
        String sn = p.speciesName == null ? "" : p.speciesName.toLowerCase(Locale.ROOT);
        return sid.contains("basculin") && (sid.contains("whitestriped") || sid.contains("white_striped") || sn.contains("white-striped") || sn.contains("white striped") || sn.contains("白条纹") || sn.contains("白纹"));
    }

    private boolean isGimmighoul(PokemonInstance p) {
        return p != null && p.speciesId != null && p.speciesId.toLowerCase(Locale.ROOT).contains("gimmighoul");
    }

    private String resolveCounterSpecialEvolutionTarget(UUID ownerId, PokemonInstance p) {
        if (p == null || p.speciesId == null) return null;
        String sid = p.speciesId.toLowerCase(Locale.ROOT);
        if ("primeape".equals(sid) && p.rageFistUses >= 20) return "annihilape";
        if ("stantler".equals(sid) && p.psyshieldBashUses >= 20) return "wyrdeer";
        if (isHisuianQwilfish(p) && p.barbBarrageUses >= 20) return "overqwil";
        if (isWhiteStripedBasculin(p) && p.basculinRecoilHp >= 294) return "basculegion";
        if ("bisharp".equals(sid) && p.bisharpLeaderDefeats >= 3) return "kingambit";
        if ("pawmo".equals(sid) && p.evolutionStepCounter >= 1000) return "pawmot";
        if ("bramblin".equals(sid) && p.evolutionStepCounter >= 1000) return "brambleghast";
        if ("rellor".equals(sid) && p.evolutionStepCounter >= 1000) return "rabsca";
        if (isGimmighoul(p) && p.gimmighoulCoins >= 999) return "gholdengo";
        return null;
    }

    public void onSpecialEvolutionMoveUsed(UUID ownerId, PokemonInstance p, String moveId) {
        if (p == null || moveId == null) return;
        String mid = moveId.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        boolean changed = false;
        if ("ragefist".equals(mid)) { p.rageFistUses++; changed = true; }
        else if ("psyshieldbash".equals(mid)) { p.psyshieldBashUses++; changed = true; }
        else if ("barbbarrage".equals(mid) && isHisuianQwilfish(p)) { p.barbBarrageUses++; changed = true; }
        if (changed && ownerId != null) storage.markDirty(ownerId);
    }

    public void onBasculinRecoil(UUID ownerId, PokemonInstance p, int damage) {
        if (p == null || damage <= 0 || !isWhiteStripedBasculin(p)) return;
        p.basculinRecoilHp += damage;
        if (ownerId != null) storage.markDirty(ownerId);
    }

    public void onBisharpLeaderDefeat(UUID ownerId, PokemonInstance p) {
        if (p == null || p.speciesId == null || !"bisharp".equalsIgnoreCase(p.speciesId)) return;
        p.bisharpLeaderDefeats++;
        if (ownerId != null) storage.markDirty(ownerId);
    }

    public void onEvolutionSteps(UUID ownerId, PokemonInstance p, int steps) {
        if (p == null || steps <= 0 || p.speciesId == null) return;
        String sid = p.speciesId.toLowerCase(Locale.ROOT);
        if (!"pawmo".equals(sid) && !"bramblin".equals(sid) && !"rellor".equals(sid)) return;
        p.evolutionStepCounter += steps;
        if (ownerId != null) storage.markDirty(ownerId);
    }

    public void addGimmighoulCoins(UUID ownerId, PokemonInstance p, int amount) {
        if (p == null || amount <= 0 || !isGimmighoul(p)) return;
        p.gimmighoulCoins = Math.min(999, p.gimmighoulCoins + amount);
        if (ownerId != null) storage.markDirty(ownerId);
    }

    public void onFaint(UUID ownerId, PokemonInstance p) {
        if (p == null) return;
        boolean changed = false;
        if (isWhiteStripedBasculin(p) && p.basculinRecoilHp != 0) { p.basculinRecoilHp = 0; changed = true; }
        if (changed && ownerId != null) storage.markDirty(ownerId);
    }

    private String resolveMoveEvolutionTarget(UUID ownerId, PokemonInstance p) {
        if (p == null || p.speciesId == null) return null;
        String sid = p.speciesId.toLowerCase(Locale.ROOT);
        if ("eevee".equals(sid) && hasFriendship(p, FRIENDSHIP_EVOLVE_THRESHOLD) && knowsMoveType(p, "fairy")) {
            return "sylveon";
        }
        return switch (sid) {
            case "lickitung" -> knowsMove(p, "rollout") ? "lickilicky" : null;
            case "tangela" -> knowsMove(p, "ancientpower") ? "tangrowth" : null;
            case "aipom" -> knowsMove(p, "doublehit") ? "ambipom" : null;
            case "yanma" -> knowsMove(p, "ancientpower") ? "yanmega" : null;
            case "bonsly" -> knowsMove(p, "mimic") ? "sudowoodo" : null;
            case "mime jr.", "mime_jr", "mimejr" -> knowsMove(p, "mimic") ? "mrmime" : null;
            case "piloswine" -> knowsMove(p, "ancientpower") ? "mamoswine" : null;
            case "poipole" -> knowsMove(p, "dragonpulse") ? "naganadel" : null;
            case "steenee" -> knowsMove(p, "stomp") ? "tsareena" : null;
            case "clobbopus" -> knowsMove(p, "taunt") ? "grapploct" : null;
            case "dunsparce" -> knowsMove(p, "hyperdrill") ? "dudunsparce" : null;
            case "girafarig" -> knowsMove(p, "twinbeam") ? "farigiraf" : null;
            case "dipplin" -> knowsMove(p, "dragoncheer") ? "hydrapple" : null;
            default -> null;
        };
    }

    private Wolf findSummonedWolf(UUID ownerId, UUID pokemonUuid) {
        if (ownerId == null || pokemonUuid == null) return null;
        SummonManager.State st = summons.getState(ownerId);
        if (st == null) return null;
        for (UUID entId : st.activeEntityBySlot.values()) {
            if (entId == null) continue;
            Entity e = Bukkit.getEntity(entId);
            if (e instanceof Wolf w) {
                UUID pu = summons.getPokemonUuidFromEntity(w);
                if (pokemonUuid.equals(pu)) return w;
            }
        }
        return null;
    }
    private String resolveSimpleItemEvolutionTarget(Player owner, PokemonInstance p, String itemId) {
        if (p == null || p.speciesId == null || itemId == null) return null;
        String sid = p.speciesId.toLowerCase(Locale.ROOT);
        String it = itemId.toLowerCase(Locale.ROOT);
        boolean night = isNight(owner);
        return switch (it) {
            case "fire_stone" -> switch (sid) {
                case "vulpix" -> "ninetales";
                case "growlithe" -> "arcanine";
                case "eevee" -> "flareon";
                case "pansear" -> "simisear";
                case "capsakid" -> "scovillain";
                default -> null;
            };
            case "water_stone" -> switch (sid) {
                case "poliwhirl" -> "poliwrath";
                case "shellder" -> "cloyster";
                case "staryu" -> "starmie";
                case "eevee" -> "vaporeon";
                case "lombre" -> "ludicolo";
                case "panpour" -> "simipour";
                default -> null;
            };
            case "thunder_stone" -> switch (sid) {
                case "pikachu" -> "raichu";
                case "eevee" -> "jolteon";
                case "charjabug" -> "vikavolt";
                case "eelektrik" -> "eelektross";
                case "magneton" -> "magnezone";
                case "nosepass" -> "probopass";
                case "tadbulb" -> "bellibolt";
                default -> null;
            };
            case "leaf_stone" -> switch (sid) {
                case "gloom" -> "vileplume";
                case "weepinbell" -> "victreebel";
                case "exeggcute" -> "exeggutor";
                case "eevee" -> "leafeon";
                case "nuzleaf" -> "shiftry";
                case "pansage" -> "simisage";
                default -> null;
            };
            case "moon_stone" -> switch (sid) {
                case "nidorina" -> "nidoqueen";
                case "nidorino" -> "nidoking";
                case "clefairy" -> "clefable";
                case "jigglypuff" -> "wigglytuff";
                case "munna" -> "musharna";
                case "skitty" -> "delcatty";
                default -> null;
            };
            case "sun_stone" -> switch (sid) {
                case "gloom" -> "bellossom";
                case "sunkern" -> "sunflora";
                case "cottonee" -> "whimsicott";
                case "petilil" -> "lilligant";
                case "helioptile" -> "heliolisk";
                default -> null;
            };
            case "shiny_stone" -> switch (sid) {
                case "togetic" -> "togekiss";
                case "roselia" -> "roserade";
                case "minccino" -> "cinccino";
                case "floette" -> "florges";
                default -> null;
            };
            case "dusk_stone" -> switch (sid) {
                case "misdreavus" -> "mismagius";
                case "murkrow" -> "honchkrow";
                case "lampent" -> "chandelure";
                case "doublade" -> "aegislash";
                default -> null;
            };
            case "dawn_stone" -> switch (sid) {
                case "kirlia" -> isMale(p) ? "gallade" : null;
                case "snorunt" -> isFemale(p) ? "froslass" : null;
                default -> null;
            };
            case "ice_stone" -> switch (sid) {
                case "eevee" -> "glaceon";
                case "cetoddle" -> "cetitan";
                case "crabrawler" -> "crabominable";
                default -> null;
            };
            case "tart_apple" -> "applin".equals(sid) ? "flapple" : null;
            case "sweet_apple" -> "applin".equals(sid) ? "appletun" : null;
            case "syrupy_apple" -> "applin".equals(sid) ? "dipplin" : null;
            case "auspicious_armor" -> "charcadet".equals(sid) ? "armarouge" : null;
            case "malicious_armor" -> "charcadet".equals(sid) ? "ceruledge" : null;
            case "metal_alloy" -> "duraludon".equals(sid) ? "archaludon" : null;
            case "black_augurite" -> "scyther".equals(sid) ? "kleavor" : null;
            case "scroll_of_darkness" -> "kubfu".equals(sid) ? "urshifu wushu_style=single_strike" : null;
            case "scroll_of_waters" -> "kubfu".equals(sid) ? "urshifu wushu_style=rapid_strike" : null;
            case "cracked_pot", "chipped_pot" -> "sinistea".equals(sid) ? "polteageist" : null;
            case "unremarkable_teacup", "masterpiece_teacup" -> "poltchageist".equals(sid) ? "sinistcha" : null;
            case "peat_block" -> ("ursaring".equals(sid) && night) ? "ursaluna" : null;
            case "up_grade" -> "porygon".equals(sid) ? "porygon2" : null;
            case "dubious_disc" -> "porygon2".equals(sid) ? "porygon_z" : null;
            case "protector" -> "rhydon".equals(sid) ? "rhyperior" : null;
            case "reaper_cloth" -> "dusclops".equals(sid) ? "dusknoir" : null;
            case "electirizer" -> "electabuzz".equals(sid) ? "electivire" : null;
            case "magmarizer" -> "magmar".equals(sid) ? "magmortar" : null;
            case "dragon_scale" -> "seadra".equals(sid) ? "kingdra" : null;
            case "metal_coat" -> switch (sid) {
                case "onix" -> "steelix";
                case "scyther" -> "scizor";
                default -> null;
            };
            case "prism_scale" -> "feebas".equals(sid) ? "milotic" : null;
            case "sachet" -> "spritzee".equals(sid) ? "aromatisse" : null;
            case "whipped_dream" -> "swirlix".equals(sid) ? "slurpuff" : null;
            case "deep_sea_tooth" -> "clamperl".equals(sid) ? "huntail" : null;
            case "deep_sea_scale" -> "clamperl".equals(sid) ? "gorebyss" : null;
            case "kings_rock" -> switch (sid) {
                case "poliwhirl" -> "politoed";
                case "slowpoke" -> "slowking";
                default -> null;
            };
            case "oval_stone" -> "happiny".equals(sid) ? "chansey" : null;
            case "leaders_crest" -> "bisharp".equals(sid) ? "kingambit" : null;
            case "razor_claw" -> ("sneasel".equals(sid) && night) ? "weavile" : null;
            case "razor_fang" -> ("gligar".equals(sid) && night) ? "gliscor" : null;
            default -> null;
        };
    }

    /**
     * Item-based evolution for simple "use item -> evolve now" cases.
     * This intentionally covers the direct-use evolutions first, and keeps
     * gender / time / biome / moon-phase / regional branching for later passes.
     */
    public boolean tryEvolveWithItem(Player owner, PokemonInstance p, String itemId) {
        if (owner == null || p == null || itemId == null) return false;

        String target = resolveSimpleItemEvolutionTarget(owner, p, itemId);
        if (target == null) return false;

        if (dex.getSpeciesFlexible(target) == null) {
            owner.sendMessage("§e该进化目标当前数据包中不存在：§f" + target + "§e。请先确认对应物种已导入。");
            return false;
        }

        boolean ok = evolveNow(owner, p, target);
        if (ok) {
            owner.sendMessage(plugin.getLang().uiFmt("evo.you_evolved", "§a你的精灵进化成了 §e{to}§a！", java.util.Map.of("to", prettyResultName(target))));
        }
        return ok;
    }
}
