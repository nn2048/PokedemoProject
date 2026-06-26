package win.pokedemo;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal, data-driven move executor.
 *
 * Goal: stop hardcoding every move in BattleManager, and instead interpret a small set of effect "kinds".
 * This is intentionally lightweight so we can expand it gradually.
 */
public final class MoveEngine {
    private MoveEngine() {}

    // Minimal sound-move list to support items like Throat Spray.
    // We only include a conservative set used commonly; extend freely later.
    private static final java.util.Set<String> SOUND_MOVES = new java.util.HashSet<>(java.util.List.of(
            // Gen1 / common
            "growl", "roar", "sing", "supersonic", "screech", "snore",
            "hypervoice", "uproar", "echoedvoice", "perishsong", "healbell",
            // Later-gen but sometimes present in Showdown exports
            "bugbuzz", "boomburst", "metalsound", "grasswhistle"
    ));

    private static final java.util.Set<String> PROTECT_LIKE_MOVES = java.util.Set.of(
            "protect", "detect", "kingsshield", "spikyshield", "banefulbunker", "burningbulwark",
            "obstruct", "silktrap", "maxguard", "matblock", "quickguard", "wideguard", "craftyshield"
    );

    private static String normMoveId(String id) {
        return id == null ? "" : id.toLowerCase(java.util.Locale.ROOT).replace("_", "");
    }

    public static List<String> execute(PokeDemoPlugin plugin,
                                       Player viewer,
                                       BattleSession s,
                                       boolean playerActing,
                                       Move chosenMove,
                                       PokemonInstance atk,
                                       PokemonInstance def,
                                       Species atkS,
                                       Species defS,
                                       String atkName,
                                       String defName) {
        // Ensure ability checks across subsystems share the same battle context.
        if (AbilityEffects.contextSession() != s) {
            return AbilityEffects.withContext(s, () -> execute(plugin, viewer, s, playerActing, chosenMove, atk, def, atkS, defS, atkName, defName));
        }
        List<String> out = new ArrayList<>();
        if (atk != null) { atk.lastMoveFailed = true; atk.destinyBondActive = false; }

        Move move = chosenMove;
        if (atk != null && move != null && !PROTECT_LIKE_MOVES.contains(normMoveId(move.id()))) {
            atk.protectSuccessStreak = 0;
        }

        // Recharge (e.g., Hyper Beam)
        if (atk.rechargeTurns > 0) {
            atk.rechargeTurns--;
            out.add(plugin.getLang().uiFmt("battle.log.recharge", "§e{atk} 需要恢复精力，无法行动！", java.util.Map.of("atk", atkName)));
            return out;
        }

        // Infatuation (Cute Charm / Attract-like). Oblivious prevents it.
        if (atk != null && atk.infatuated) {
            if (AbilityEffects.has(atk, "oblivious")) {
                atk.infatuated = false;
            } else {
                if (Util.RND.nextDouble() < 0.50) {
                    out.add(plugin.getLang().uiFmt("battle.log.attract", "§d{atk} 陷入了爱河，无法行动！", java.util.Map.of("atk", atkName)));
                    return out;
                }
            }
        }

        String previewMoveType = HeldItemEffects.overrideMoveType(atk, atkS, move);
        if (atk != null && move != null && !atk.typeChangeAbilityUsed
                && (AbilityEffects.has(atk, "protean") || AbilityEffects.has(atk, "libero"))) {
            String currentT1 = atk.overrideType1;
            String currentT2 = atk.overrideType2;
            boolean alreadySingle = currentT1 != null && currentT1.equalsIgnoreCase(previewMoveType) && (currentT2 == null || currentT2.isBlank());
            if (previewMoveType != null && !previewMoveType.isBlank() && !alreadySingle) {
                atk.overrideType1 = previewMoveType.toLowerCase(java.util.Locale.ROOT);
                atk.overrideType2 = null;
                atk.typeChangeAbilityUsed = true;
                String ab = AbilityEffects.displayName(atk.abilityId);
                out.add(plugin.getLang().uiFmt("battle.log.ability.type_change", "§6【{ab}】§e{atk} 变成了 {type} 属性！", java.util.Map.of("ab", ab, "atk", atkName, "type", previewMoveType)));
            }
        }

        // Ability-based immunities/absorbs (Levitate / Water Absorb / Volt Absorb / Flash Fire etc.)
        // This is checked after accuracy, before executing move effects.
        if (atk != null && def != null && move != null && move.power() > 0 && !"status".equalsIgnoreCase(move.category())) {
            String moveTypeNow = HeldItemEffects.overrideMoveType(atk, atkS, move);
            if (!AbilityEffects.ignoresDefenderAbility(atk)) {
                AbilityEffects.AbilityImmunityResult imm = AbilityEffects.immunity(def, moveTypeNow);
                if ("ground".equalsIgnoreCase(moveTypeNow) && imm != null && imm.kind == AbilityEffects.AbilityImmunityResult.Kind.IMMUNE
                        && (s.gravityTurns > 0 || (def != null && def.groundedBySmackDown))) {
                    imm = AbilityEffects.AbilityImmunityResult.none();
                }
                if ("ground".equalsIgnoreCase(moveTypeNow) && def != null && (def.magnetRiseTurns > 0 || def.telekinesisTurns > 0)
                        && s.gravityTurns <= 0 && !def.groundedBySmackDown) {
                    out.add(plugin.getLang().uiFmt("battle.log.ground_immune", "§7{def} 漂浮在空中，避开了地面招式！", java.util.Map.of("def", defName)));
                    return out;
                }
                if (imm != null && imm.kind != AbilityEffects.AbilityImmunityResult.Kind.NONE) {
                    if (imm.message != null && !imm.message.isBlank()) out.add(imm.message);
                    switch (imm.kind) {
                        case IMMUNE -> {
                            return out;
                        }
                        case HEAL -> {
                            int max = Math.max(1, def.maxHp(defS));
                            int heal = Math.max(1, max / 4);
                            int before = def.currentHp;
                            def.currentHp = Math.min(max, def.currentHp + heal);
                            if (def.currentHp > before) out.add(plugin.getLang().uiFmt("battle.log.heal_hp", "§a{mon} 回复了 §c{n}§a 点体力！", java.util.Map.of("mon", defName, "n", String.valueOf(def.currentHp - before))));
                            return out;
                        }
                        case BOOST -> {
                            if (imm.boostStat != null && !imm.boostStat.isBlank()) {
                                def.applyStage(imm.boostStat, imm.boostStages);
                            }
                            return out;
                        }
                        case FLASH_FIRE -> {
                            def.flashFireBoost = true;
                            return out;
                        }
                        default -> {}
                    }
                }
            }
        }

        // Trapped by partial-trapping moves (Bind/Wrap/Clamp/Fire Spin)
        // Modern behavior: the victim can still act, but cannot switch/escape.
        // We keep a simple duration counter here.
        if (atk.trappedTurnsRemaining > 0) {
            atk.trappedTurnsRemaining--;
        }

        // Two-turn follow-up: if we're charging a move, force it now (ignore the chosen move).
        if (atk.chargingMoveId != null && atk.chargingTurnsRemaining > 0) {
            Move forced = plugin.getDex().getMoveOrPlaceholder(atk.chargingMoveId);
            move = forced;
            atk.chargingTurnsRemaining--;
            LangManager lang = plugin.getLang();
            String mvName = lang == null ? move.name() : lang.move(move.id(), move.name());
            out.add(plugin.getLang().uiFmt("battle.log.encore", "§7{atk} 继续使用了 §f{move}§7！", java.util.Map.of("atk", atkName, "move", mvName)));
        }

        // Partial-trapping continuation: force repeating the trapping move while turns remain.
        atk.trappingContinuing = false;
        if (atk.trappingMoveId != null && atk.trappingTurnsRemaining > 0) {
            Move forced = plugin.getDex().getMoveOrPlaceholder(atk.trappingMoveId);
            move = forced;
            atk.trappingTurnsRemaining--;
            atk.trappingContinuing = true;
            LangManager lang = plugin.getLang();
            String mvName = lang == null ? move.name() : lang.move(move.id(), move.name());
            out.add(plugin.getLang().uiFmt("battle.log.encore", "§7{atk} 继续使用了 §f{move}§7！", java.util.Map.of("atk", atkName, "move", mvName)));
        } else if (atk.trappingMoveId != null && atk.trappingTurnsRemaining <= 0) {
            atk.trappingMoveId = null;
        }

        boolean wasJustSwitchedIn = atk != null && atk.justSwitchedIn;
        if (atk != null) atk.justSwitchedIn = false;

        // Flinch
        if (atk.flinched) {
            atk.flinched = false;
            out.add(plugin.getLang().uiFmt("battle.log.flinch", "§e{atk} 畏缩了，无法行动！", java.util.Map.of("atk", atkName)));
            return out;
        }

        // Disable: cannot choose the disabled move
        if (atk.disabledTurns > 0 && atk.disabledMoveId != null && move != null && move.id() != null) {
            if (move.id().equalsIgnoreCase(atk.disabledMoveId)) {
                LangManager lang = plugin.getLang();
                String mvName = lang == null ? move.name() : lang.move(move.id(), move.name());
                out.add(plugin.getLang().uiFmt("battle.log.disable", "§7{atk} 的 §f{move}§7 被定身法封印了！", java.util.Map.of("atk", atkName, "move", mvName)));
                return out;
            }
        }

        // Bide: wait for 2 turns, then release double damage taken.
        if (atk.bideActive) {
            if (atk.bideTurnsRemaining > 0) {
                atk.bideTurnsRemaining--;
                out.add(plugin.getLang().uiFmt("battle.log.bide_charging", "§7{atk} 忍耐中……", java.util.Map.of("atk", atkName)));
                return out;
            }
            int dmg = Math.max(1, atk.bideDamageTaken * 2);
            dmg = applyDamageToTarget(def, defS, dmg, out, defName, false);
            out.add(plugin.getLang().uiFmt("battle.log.bide_release", "§c{atk} 释放了忍耐！", java.util.Map.of("atk", atkName)));
            out.add(plugin.getLang().uiFmt("battle.log.deal_damage", "§f造成了 §c{dmg}§f 点伤害！ §7({hp}/{max})", java.util.Map.of("dmg", String.valueOf(dmg), "hp", String.valueOf(def.currentHp), "max", String.valueOf(def.maxHp(defS)))));
            atk.bideActive = false;
            atk.bideTurnsRemaining = 0;
            atk.bideDamageTaken = 0;
            return out;
        }

        // Confusion
        if (atk.confusionTurns > 0) {
            atk.confusionTurns--;
            out.add(plugin.getLang().uiFmt("battle.log.confused", "§d{atk} 混乱了！", java.util.Map.of("atk", atkName)));
            if (Util.RND.nextDouble() < 0.5) {
                int selfDmg = Damage.calcConfusionSelfDamage(atk, atkS);
                atk.currentHp = Math.max(0, atk.currentHp - selfDmg);
                out.add(plugin.getLang().uiFmt("battle.log.confused_hit_self", "§c它撞到了自己！ §f造成了 §c{dmg}§f 点伤害！", java.util.Map.of("dmg", String.valueOf(selfDmg))));
                if (atk.confusionTurns <= 0) out.add(plugin.getLang().uiFmt("battle.log.confusion_end", "§a{atk} 恢复正常了！", java.util.Map.of("atk", atkName)));
                return out;
            }
            if (atk.confusionTurns <= 0) out.add(plugin.getLang().uiFmt("battle.log.confusion_end", "§a{atk} 恢复正常了！", java.util.Map.of("atk", atkName)));
        }

        // Status prevents action (sleep / paralyze)
        if ("sleep".equalsIgnoreCase(atk.status)) {
            if (atk.sleepTurns > 0) {
                atk.sleepTurns--;
                // Early Bird: sleep ends twice as fast (simplified).
                String aa = atk.abilityId == null ? "" : atk.abilityId.toLowerCase(java.util.Locale.ROOT);
                if ("earlybird".equals(aa) && atk.sleepTurns > 0) {
                    atk.sleepTurns--;
                }
                out.add(plugin.getLang().uiFmt("battle.log.sleeping", "§7{atk} 还在睡觉，无法行动！", java.util.Map.of("atk", atkName)));
                if (atk.sleepTurns <= 0) {
                    atk.status = "none";
                    out.add(plugin.getLang().uiFmt("battle.log.woke_up", "§a{atk} 醒来了！", java.util.Map.of("atk", atkName)));
                }
                return out;
            } else {
                atk.status = "none";
            }
        }
        if ("paralyze".equalsIgnoreCase(atk.status)) {
            if (Util.RND.nextDouble() < 0.25) {
                out.add(plugin.getLang().uiFmt("battle.log.paralyzed", "§e{atk} 因为麻痹而无法行动！", java.util.Map.of("atk", atkName)));
                return out;
            }
        }

        // Encore / Taunt / Torment / Heal Block style restrictions.
        if (atk.encoreTurns > 0 && atk.encoreMoveId != null && move != null && move.id() != null
                && !move.id().equalsIgnoreCase(atk.encoreMoveId)) {
            Move forcedEncore = plugin.getDex().getMoveOrPlaceholder(atk.encoreMoveId);
            if (forcedEncore != null && forcedEncore.id() != null) {
                move = forcedEncore;
                LangManager lang = plugin.getLang();
                String mvName = lang == null ? move.name() : lang.move(move.id(), move.name());
                out.add(plugin.getLang().uiFmt("battle.log.encore", "§7{atk} 继续使用了 §f{move}§7！", java.util.Map.of("atk", atkName, "move", mvName)));
            }
        }
        if (atk.tauntTurns > 0 && move != null && "status".equalsIgnoreCase(move.category())) {
            out.add(plugin.getLang().uiFmt("battle.log.taunt.block", "§7{atk} 受到了挑衅，无法使用变化招式！", java.util.Map.of("atk", atkName)));
            return out;
        }
        if (atk.tormentTurns > 0 && atk.lastMoveId != null && move != null && move.id() != null && move.id().equalsIgnoreCase(atk.lastMoveId)) {
            out.add(plugin.getLang().uiFmt("battle.log.torment.block", "§7{atk} 因无理取闹无法连续使用同一招式！", java.util.Map.of("atk", atkName)));
            return out;
        }

        String moveId = move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT);
Move defenderPlannedMove = plannedMoveForSide(plugin, s, !playerActing);
String defenderPlannedId = defenderPlannedMove == null || defenderPlannedMove.id() == null ? "" : defenderPlannedMove.id().toLowerCase(java.util.Locale.ROOT).replace("_", "");
if (atk.powdered) {
    String currentMoveType = HeldItemEffects.overrideMoveType(atk, atkS, move);
    if (atk.electrifiedThisTurn) currentMoveType = "electric";
    else if (s != null && s.ionDelugeActive && "normal".equalsIgnoreCase(currentMoveType)) currentMoveType = "electric";
    if ("fire".equalsIgnoreCase(currentMoveType)) {
        atk.powdered = false;
        int boom = Math.max(1, atk.maxHp(atkS) / 4);
        atk.currentHp = Math.max(0, atk.currentHp - boom);
        out.add(plugin.getLang().uiFmt("battle.log.powder", "§6{atk} 的粉尘爆炸了！", java.util.Map.of("atk", atkName)));
        out.add(plugin.getLang().uiFmt("battle.log.deal_damage", "§f造成了 §c{dmg}§f 点伤害！ §7({hp}/{max})", java.util.Map.of("dmg", String.valueOf(boom), "hp", String.valueOf(atk.currentHp), "max", String.valueOf(atk.maxHp(atkS)))));
        return out;
    }
}
if (atk.throatChopTurns > 0 && isSoundMoveId(moveId)) {
    out.add(plugin.getLang().uiFmt("battle.log.throat_chop.block", "§7{atk} 因喉咙被封而无法使用声音招式！", java.util.Map.of("atk", atkName)));
    return out;
}
if ((atk.noRetreatActive || (s != null && s.fairyLockTurns > 0)) && ("uturn".equals(moveId)||"voltswitch".equals(moveId)||"flipturn".equals(moveId)||"batonpass".equals(moveId)||"partingshot".equals(moveId)||"chillyreception".equals(moveId)||"teleport".equals(moveId))) {
    out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
    return out;
}
boolean defenderSnatchActive = playerActing ? s.wildSnatchActive : s.playerSnatchActive;
if (defenderSnatchActive && isSnatchableMoveId(moveId)) {
    if (playerActing) s.wildSnatchActive = false; else s.playerSnatchActive = false;
    out.add(plugin.getLang().uiFmt("battle.log.snatch.steal", "§d{def} 抢先夺走了招式！", java.util.Map.of("def", defName)));
    out.addAll(execute(plugin, viewer, s, !playerActing, move, def, atk, defS, atkS, defName, atkName));
    return out;
}
if (def != null && def.magicCoatActive && "status".equalsIgnoreCase(move.category()) && isReflectableMoveId(moveId)) {
    def.magicCoatActive = false;
    atk.magicCoatActive = false;
    out.add(plugin.getLang().uiFmt("battle.log.magic_coat.reflect", "§d{def} 把招式反弹了回去！", java.util.Map.of("def", defName)));
    out.addAll(execute(plugin, viewer, s, !playerActing, move, def, atk, defS, atkS, defName, atkName));
    return out;
}
if (def != null && (AbilityEffects.has(def, "magicbounce") || AbilityEffects.has(def, "rebound")) && "status".equalsIgnoreCase(move.category()) && isReflectableMoveId(moveId)
        && !AbilityEffects.ignoresDefenderAbility(atk) && !(AbilityEffects.has(atk, "magicbounce") || AbilityEffects.has(atk, "rebound"))) {
    out.add(plugin.getLang().uiFmt("battle.log.magic_bounce.reflect", "§d{def} 的特性把招式反弹了回去！", java.util.Map.of("def", defName)));
    out.addAll(execute(plugin, viewer, s, !playerActing, move, def, atk, defS, atkS, defName, atkName));
    return out;
}
        if (def != null && def.imprisonActive && move != null && move.id() != null && knowsMove(def, move.id())) {
            out.add(plugin.getLang().uiFmt("battle.log.imprison.block", "§7{atk} 的招式被封印了，无法使用！", java.util.Map.of("atk", atkName)));
            return out;
        }
        if ("focuspunch".equals(moveId) && atk.tookDamageThisTurn) {
            out.add(plugin.getLang().uiFmt("battle.log.focus_punch.fail", "§7{atk} 因为受到了攻击而无法集中精神！", java.util.Map.of("atk", atkName)));
            return out;
        }
        if ("firstimpression".equals(moveId) && !wasJustSwitchedIn) {
            out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
            return out;
        }
        if (s != null && s.terrainTurns > 0 && "psychic".equalsIgnoreCase(s.terrain)
                && move.priority() > 0 && def != null && defS != null && isGroundedForTerrain(def, defS, s)) {
            out.add(plugin.getLang().uiFmt("battle.log.psychic_terrain.block", "§7{def} 受到精神场地保护，先制招式失败了！", java.util.Map.of("def", defName)));
            return out;
        }
if ("suckerpunch".equals(moveId)) {
    if (defenderPlannedMove == null || "status".equalsIgnoreCase(defenderPlannedMove.category())) {
        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
        return out;
    }
}
if ("shelltrap".equals(moveId) && !atk.tookDamageThisTurn) {
    out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
    return out;
}
if ("snatch".equals(moveId)) {
    if (playerActing) s.playerSnatchActive = true; else s.wildSnatchActive = true;
    out.add(plugin.getLang().uiFmt("battle.log.snatch.ready", "§7{atk} 盯上了对手的招式！", java.util.Map.of("atk", atkName)));
    atk.lastMoveFailed = false;
    return out;
}
if ("magiccoat".equals(moveId) || "magic_coat".equals(moveId)) {
    atk.magicCoatActive = true;
    out.add(plugin.getLang().uiFmt("battle.log.magic_coat", "§7{atk} 被魔法反射包裹了！", java.util.Map.of("atk", atkName)));
    atk.lastMoveFailed = false;
    return out;
}
if ("grudge".equals(moveId)) {
    atk.grudgeActive = true;
    out.add(plugin.getLang().uiFmt("battle.log.grudge", "§7{atk} 怀着怨恨盯住了对手！", java.util.Map.of("atk", atkName)));
    atk.lastMoveFailed = false;
    return out;
}
if ("roleplay".equals(moveId) || "role_play".equals(moveId)) {
    if (def == null || def.abilityId == null || def.abilityId.isBlank()) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    if (AbilityEffects.isAbilityChangeBlocked(def.abilityId) || AbilityEffects.isAbilityChangeBlocked(atk.abilityId)) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    atk.setBattleAbility(def.abilityId);
    out.add(plugin.getLang().uiFmt("battle.log.role_play", "§7{atk} 模仿了 {def} 的特性！", java.util.Map.of("atk", atkName, "def", defName)));
    atk.lastMoveFailed = false;
    return out;
}
if ("skillswap".equals(moveId) || "skill_swap".equals(moveId)) {
    String a1 = atk.abilityId, a2 = def == null ? null : def.abilityId;
    if (def == null || (a1 == null && a2 == null)) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    if (AbilityEffects.isAbilityChangeBlocked(a1) || AbilityEffects.isAbilityChangeBlocked(a2)) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    atk.setBattleAbility(a2);
    def.setBattleAbility(a1);
    out.add(plugin.getLang().uiFmt("battle.log.skill_swap", "§7{atk} 与 {def} 交换了特性！", java.util.Map.of("atk", atkName, "def", defName)));
    atk.lastMoveFailed = false;
    return out;
}
if ("simplebeam".equals(moveId) || "simple_beam".equals(moveId)) {
    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    if (AbilityEffects.isAbilityChangeBlocked(def.abilityId)) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    def.setBattleAbility("simple");
    out.add(plugin.getLang().uiFmt("battle.log.simple_beam", "§7{def} 的特性变成了单纯！", java.util.Map.of("def", defName)));
    atk.lastMoveFailed = false;
    return out;
}
if ("worryseed".equals(moveId) || "worry_seed".equals(moveId)) {
    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    if (AbilityEffects.isAbilityChangeBlocked(def.abilityId)) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    def.setBattleAbility("insomnia");
    out.add(plugin.getLang().uiFmt("battle.log.worry_seed", "§7{def} 的特性变成了不眠！", java.util.Map.of("def", defName)));
    atk.lastMoveFailed = false;
    return out;
}
if ("entrainment".equals(moveId)) {
    if (def == null || atk.abilityId == null || atk.abilityId.isBlank()) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    if (AbilityEffects.isAbilityChangeBlocked(def.abilityId) || AbilityEffects.isAbilityChangeBlocked(atk.abilityId)) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    def.setBattleAbility(atk.abilityId);
    out.add(plugin.getLang().uiFmt("battle.log.entrainment", "§7{def} 获得了 {atk} 的特性！", java.util.Map.of("def", defName, "atk", atkName)));
    atk.lastMoveFailed = false;
    return out;
}
if ("magicroom".equals(moveId) || "magic_room".equals(moveId)) {
    s.magicRoomTurns = Math.max(s.magicRoomTurns, 5);
    out.add(plugin.getLang().ui("battle.log.magic_room", "§7魔法空间展开了！"));
    atk.lastMoveFailed = false;
    return out;
}
if ("wonderroom".equals(moveId) || "wonder_room".equals(moveId)) {
    s.wonderRoomTurns = Math.max(s.wonderRoomTurns, 5);
    out.add(plugin.getLang().ui("battle.log.wonder_room", "§7奇妙空间展开了！"));
    atk.lastMoveFailed = false;
    return out;
}
if ("curse".equals(moveId)) {
    boolean ghost = false;
    try { ghost = effectiveTypes(atk, atkS).stream().anyMatch(t -> "ghost".equalsIgnoreCase(t)); } catch (Throwable ignore) {}
    if (ghost) {
        int cost = Math.max(1, atk.maxHp(atkS) / 2);
        if (atk.currentHp <= cost || def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
        atk.currentHp = Math.max(1, atk.currentHp - cost);
        def.nightmareTurns = Math.max(def.nightmareTurns, 4);
        out.add(plugin.getLang().uiFmt("battle.log.curse.ghost", "§7{def} 被诅咒了！", java.util.Map.of("def", defName)));
    } else {
        atk.applyStage("atk", 1); atk.applyStage("def", 1); atk.applyStage("spe", -1);
        out.add(plugin.getLang().uiFmt("battle.log.curse", "§7{atk} 使用了诅咒！", java.util.Map.of("atk", atkName)));
    }
    atk.lastMoveFailed = false;
    return out;
}
if ("snore".equals(moveId) && !"sleep".equalsIgnoreCase(atk.status)) {
    out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
    return out;
}
if (("dreameater".equals(moveId) || "dream_eater".equals(moveId)) && (def == null || !"sleep".equalsIgnoreCase(def.status))) {
    out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
    return out;
}
if ("belch".equals(moveId) && (atk.lastConsumedBerryId == null || atk.lastConsumedBerryId.isBlank())) {
    out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
    return out;
}
if ("counter".equals(moveId)) {
    int last = Math.max(0, atk.lastPhysicalDamageTaken);
    boolean valid = def != null
            && last > 0
            && atk.lastPhysicalDamageTurn == s.turn
            && atk.lastPhysicalDamageSourceUuid != null
            && atk.lastPhysicalDamageSourceUuid.equals(def.uuid);
    if (!valid) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    int dealt = applyDamageToTarget(def, defS, Math.max(1, last * 2), out, defName, false);
    out.add(plugin.getLang().uiFmt("battle.log.bide.release", "§c{atk} 双倍奉还！", java.util.Map.of("atk", atkName)));
    out.add(plugin.getLang().uiFmt("battle.log.damage", "§f造成了 §c{dmg}§f 点伤害！ §7({hp}/{max})", java.util.Map.of("dmg", String.valueOf(dealt), "hp", String.valueOf(def.currentHp), "max", String.valueOf(def.maxHp(defS)))));
    resetRetaliationMemory(atk);
    atk.lastMoveFailed = false;
    return out;
}
if ("mirrorcoat".equals(moveId) || "mirror_coat".equals(moveId)) {
    int last = Math.max(0, atk.lastSpecialDamageTaken);
    boolean valid = def != null
            && last > 0
            && atk.lastSpecialDamageTurn == s.turn
            && atk.lastSpecialDamageSourceUuid != null
            && atk.lastSpecialDamageSourceUuid.equals(def.uuid);
    if (!valid) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    int dealt = applyDamageToTarget(def, defS, Math.max(1, last * 2), out, defName, false);
    out.add(plugin.getLang().uiFmt("battle.log.deal_damage", "§f造成了 §c{dmg}§f 点伤害！ §7({hp}/{max})", java.util.Map.of("dmg", String.valueOf(dealt), "hp", String.valueOf(def.currentHp), "max", String.valueOf(def.maxHp(defS)))));
    resetRetaliationMemory(atk);
    atk.lastMoveFailed = false;
    return out;
}
if ("metalburst".equals(moveId) || "metal_burst".equals(moveId) || "comeuppance".equals(moveId)) {
    int last = Math.max(0, atk.lastDamageTaken);
    boolean valid = def != null
            && last > 0
            && atk.lastDamageTurn == s.turn
            && atk.lastDamageSourceUuid != null
            && atk.lastDamageSourceUuid.equals(def.uuid);
    if (!valid) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    int dealt = applyDamageToTarget(def, defS, Math.max(1, (int) Math.floor(last * 1.5)), out, defName, false);
    out.add(plugin.getLang().uiFmt("battle.log.deal_damage", "§f造成了 §c{dmg}§f 点伤害！ §7({hp}/{max})", java.util.Map.of("dmg", String.valueOf(dealt), "hp", String.valueOf(def.currentHp), "max", String.valueOf(def.maxHp(defS)))));
    resetRetaliationMemory(atk);
    atk.lastMoveFailed = false;
    return out;
}
if ("superfang".equals(moveId) || "super_fang".equals(moveId) || "naturesmadness".equals(moveId) || "natures_madness".equals(moveId) || "ruination".equals(moveId) || "finalgambit".equals(moveId) || "final_gambit".equals(moveId) || "endeavor".equals(moveId)) {
    if (def == null || defS == null || atkS == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
    int dmg;
    if ("finalgambit".equals(moveId) || "final_gambit".equals(moveId)) {
        dmg = Math.max(1, atk.currentHp);
    } else if ("endeavor".equals(moveId)) {
        if (def.currentHp <= atk.currentHp) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); return out; }
        dmg = Math.max(1, def.currentHp - Math.max(1, atk.currentHp));
    } else {
        dmg = Math.max(1, def.currentHp / 2);
    }
    int dealt = applyDamageToTarget(def, defS, dmg, out, defName, false);
    if (def != null && dealt > 0) { recordReactiveDamage(s, atk, def, move, dealt); }
    out.add(plugin.getLang().uiFmt("battle.log.deal_damage", "§f造成了 §c{dmg}§f 点伤害！ §7({hp}/{max})", java.util.Map.of("dmg", String.valueOf(dealt), "hp", String.valueOf(def.currentHp), "max", String.valueOf(def.maxHp(defS)))));
    if ("finalgambit".equals(moveId) || "final_gambit".equals(moveId)) atk.currentHp = 0;
    atk.lastMoveFailed = false;
    return out;
}

        // Damp: prevents self-destructing moves for BOTH sides.
        if (("explosion".equals(moveId) || "selfdestruct".equals(moveId))
                && (AbilityEffects.has(atk, "damp") || AbilityEffects.has(def, "damp"))) {
            out.add(plugin.getLang().ui("battle.log.damp", "§6【湿气】§e空气变得潮湿，招式失败了！"));
            return out;
        }

        // Soundproof: immune to sound-based moves. (Mold Breaker ignores this.)
        if (def != null && AbilityEffects.has(def, "soundproof") && SOUND_MOVES.contains(moveId)
                && !AbilityEffects.ignoresDefenderAbility(atk)) {
            out.add(plugin.getLang().uiFmt("battle.log.soundproof", "§6【隔音】§e{def} 对声音招式免疫！", java.util.Map.of("def", defName)));
            return out;
        }
        if (def != null && AbilityEffects.has(def, "bulletproof") && isBulletMoveId(moveId)
                && !AbilityEffects.ignoresDefenderAbility(atk)) {
            out.add(plugin.getLang().uiFmt("battle.log.ability.bulletproof", "§6【防弹】§e{def} 挡下了球弹类招式！", java.util.Map.of("def", defName)));
            return out;
        }
        if (def != null && AbilityEffects.has(def, "windrider") && isWindMoveId(moveId)
                && !AbilityEffects.ignoresDefenderAbility(atk)) {
            def.applyStage("atk", 1);
            out.add(plugin.getLang().uiFmt("battle.log.ability.wind_rider", "§6【乘风】§e{def} 乘上了风，攻击提升了！", java.util.Map.of("def", defName)));
            return out;
        }
        if (def != null && AbilityEffects.blocksStatusMove(def, move, atk)) {
            out.add(plugin.getLang().uiFmt("battle.log.ability.good_as_gold", "§6【黄金之躯】§e{def} 不受变化招式影响！", java.util.Map.of("def", defName)));
            return out;
        }
        if (def != null && AbilityEffects.blocksPriorityMove(def, move)
                && move.priority() > 0
                && !(s != null && s.terrainTurns > 0 && "psychic".equalsIgnoreCase(s.terrain) && defS != null && isGroundedForTerrain(def, defS, s))) {
            String ab = AbilityEffects.displayName(def.abilityId);
            out.add(plugin.getLang().uiFmt("battle.log.ability.priority_block", "§6【{ab}】§e{def} 挡住了先制招式！", java.util.Map.of("ab", ab, "def", defName)));
            return out;
        }

        // Ability: secondary-effect handling (Serene Grace / Sheer Force / Shield Dust).
        final double secondaryChanceMul = AbilityEffects.secondaryChanceMultiplier(atk, move);
        final boolean sheerForceActive = AbilityEffects.hasSheerForce(atk) && AbilityEffects.moveHasSecondaryEffects(move);
        final boolean shieldDustBlocks = AbilityEffects.blocksSecondary(def);

// Truant: every other turn loaf around.
        if (atk != null && AbilityEffects.has(atk, "truant")) {
            if (atk.truantLoafing) {
                atk.truantLoafing = false;
                out.add(plugin.getLang().uiFmt("battle.log.ability.truant", "§6【懒惰】§e{atk} 偷懒了！", java.util.Map.of("atk", atkName)));
                return out;
            }
            atk.truantLoafing = true;
        }

// Record last used move for mechanics like Mirror Move (only if the user can act).
        atk.lastMoveId = move.id();
        try { if (plugin != null && plugin.getEvolutionManager() != null && viewer != null) plugin.getEvolutionManager().onSpecialEvolutionMoveUsed(viewer.getUniqueId(), atk, move.id()); } catch (Throwable ignored) {}

        // Metronome (held item) consecutive-move tracking.
        if (atk != null && "metronome".equalsIgnoreCase(atk.heldItemId) && move.id() != null) {
            String cur = move.id().toLowerCase(java.util.Locale.ROOT);
            if (cur.equalsIgnoreCase(atk.metronomeLastMoveId)) {
                atk.metronomeCount = Math.min(5, atk.metronomeCount + 1);
            } else {
                atk.metronomeCount = 0;
                atk.metronomeLastMoveId = cur;
            }
        }

        // accuracy (skip for forced partial-trap continuation)
        // No Guard: moves used by or against the user never miss.
        if (!atk.trappingContinuing && !AbilityEffects.noGuardActive(atk, def)) {
            double hitChance = move.accuracy();
            // Held items (Wide Lens / BrightPowder / Lax Incense)
            hitChance *= HeldItemEffects.accuracyMultiplier(atk, def);
            // Zoom Lens: if moving after the target
            boolean movedAfterTargetForZoomLens = false;
            try {
                int atkSpe = atk.calcStat(atkS, "spe", atk.ivSpe, atk.evSpe, false);
                int defSpe = def.calcStat(defS, "spe", def.ivSpe, def.evSpe, false);
                atkSpe = (int) Math.floor(atkSpe * atk.stageMultiplier("spe") * HeldItemEffects.speedMultiplier(atk, atkS) * AbilityEffects.speedMultiplier(atk, s) * ((playerActing ? s.playerTailwindTurns > 0 : s.wildTailwindTurns > 0) ? 2.0 : 1.0));
                defSpe = (int) Math.floor(defSpe * def.stageMultiplier("spe") * HeldItemEffects.speedMultiplier(def, defS) * AbilityEffects.speedMultiplier(def, s) * ((playerActing ? s.wildTailwindTurns > 0 : s.playerTailwindTurns > 0) ? 2.0 : 1.0));
                movedAfterTargetForZoomLens = atkSpe < defSpe;
            } catch (Exception ignore) {}
            hitChance *= HeldItemEffects.zoomLensAccuracyMultiplier(atk, movedAfterTargetForZoomLens);
            // Wide Lens (attacker) and BrightPowder/Lax Incense (defender)
            hitChance *= HeldItemEffects.attackerAccuracyMultiplier(atk);
            hitChance *= HeldItemEffects.evasionItemAccuracyMultiplier(def);
            // Wide Lens (attacker) and BrightPowder/Lax Incense (defender)
            hitChance *= HeldItemEffects.attackerAccuracyMultiplier(atk);
            hitChance *= HeldItemEffects.evasionItemAccuracyMultiplier(def);

            // Ability-based accuracy modifiers (e.g., Compound Eyes, Illuminate).
            double abAcc = AbilityEffects.accuracyMultiplier(atk);
            // Hustle should only affect physical moves.
            if (atk != null && "hustle".equalsIgnoreCase(atk.abilityId) && !"physical".equalsIgnoreCase(move.category())) {
                abAcc = 1.0;
            }
            hitChance *= abAcc;
            // Safety Goggles: block powder moves
            if (HeldItemEffects.blocksPowderMoves(def, move)) {
                out.add(plugin.getLang().uiFmt("battle.log.safety_goggles", "§7{def} 的§f防尘护目镜§7挡住了粉末！", java.util.Map.of("def", defName)));
                return out;
            }
            // Overcoat (ability): block powder moves
            if (AbilityEffects.blocksPowderMoves(def, move) && !AbilityEffects.ignoresDefenderAbility(atk)) {
                out.add(plugin.getLang().uiFmt("battle.log.overcoat", "§6【防尘】§e{def} 挡住了粉末！", java.util.Map.of("def", defName)));
                return out;
            }

            // Tangled Feet: while confused, evasion is boosted (we model as halving the opponent's final hit chance).
            if (def != null && AbilityEffects.has(def, "tangledfeet") && def.confusionTurns > 0) {
                hitChance *= 0.5;
            }
            // Sand Veil / Snow Cloak: in sand/hail, user becomes harder to hit (model as 0.8x final hit chance).
            WeatherType ew = WeatherSystem.effectiveWeather(s);

            // Weather accuracy overrides for certain moves
            String mid = move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT);
            if ("thunder".equals(mid) || "hurricane".equals(mid)) {
                if (ew == WeatherType.RAIN) hitChance = 1.0;
                else if (ew == WeatherType.SUN) hitChance = Math.min(hitChance, 0.50);
            } else if ("blizzard".equals(mid)) {
                if (ew == WeatherType.HAIL) hitChance = 1.0;
            }
            if (def != null && ew == WeatherType.SAND && AbilityEffects.has(def, "sandveil")) {
                hitChance *= 0.8;
            }
            if (def != null && ew == WeatherType.HAIL && AbilityEffects.has(def, "snowcloak")) {
                hitChance *= 0.8;
            }
            // Wonder Skin: status moves used against the user have their accuracy lowered to 50% (if higher).
            if (def != null && AbilityEffects.has(def, "wonderskin") && "status".equalsIgnoreCase(move.category())) {
                hitChance = Math.min(hitChance, 0.50);
            }
            // accuracy/evasion stages
            double evasionMul = (def != null && (def.identifiedTarget || def.miracleEyeTarget || AbilityEffects.has(atk, "mindseye"))) ? 1.0 : def.stageMultiplier("evasion");
            hitChance *= atk.stageMultiplier("accuracy") / evasionMul;
            if (def != null && def.telekinesisTurns > 0) hitChance = 1.0;
            if (s.gravityTurns > 0) hitChance *= (5.0 / 3.0);
            hitChance = Math.max(0.01, Math.min(1.0, hitChance));

            if (Util.RND.nextDouble() > hitChance) {
                out.add(plugin.getLang().ui("battle.log.miss", "§e但是没有命中！"));
                // Blunder Policy: if a move misses due to accuracy/evasion, raise Speed sharply and consume.
                if ("blunder_policy".equalsIgnoreCase(atk.heldItemId)) {
                    atk.applyStage("spe", 2);
                    atk.heldItemId = null;
                    out.add(plugin.getLang().uiFmt("battle.log.blunder_policy", "§d{atk} 的§f打空保险§d发动了！速度大幅提升！", java.util.Map.of("atk", atkName)));
                }
                // If a partial-trap attempt misses, clear any trapping state.
                if (atk.trappingMoveId != null) {
                    atk.trappingMoveId = null;
                    atk.trappingTurnsRemaining = 0;
                }
                return out;
            }
        }

        // Overcoat (ability): still blocks powder moves even when accuracy check is skipped (e.g., No Guard).
        if (AbilityEffects.blocksPowderMoves(def, move) && !AbilityEffects.ignoresDefenderAbility(atk)) {
            out.add(plugin.getLang().uiFmt("battle.log.overcoat", "§6【防尘】§e{def} 挡住了粉末！", java.util.Map.of("def", defName)));
            return out;
        }

        // Collect effects (new list first, then legacy fallback)
        List<Map<String, Object>> effects = new ArrayList<>();
        if (move.effectsSafe() != null) effects.addAll(move.effectsSafe());
        if (effects.isEmpty() && move.effect() != null && !move.effect().isEmpty()) {
            effects.add(move.effect());
        }

        String normAbility = AbilityEffects.norm(atk == null ? null : atk.abilityId);
        String moveIdNorm = AbilityEffects.norm(move.id());
        if (atk != null && "stancechange".equals(normAbility) && atk.speciesId != null && atk.speciesId.toLowerCase(java.util.Locale.ROOT).startsWith("aegislash")) {
            if (move.power() > 0 && !"kingsshield".equals(moveIdNorm)) atk.overrideSpeciesId = "aegislashblade";
            else if ("kingsshield".equals(moveIdNorm)) atk.overrideSpeciesId = null;
        }

        // Heal Block: block direct healing/recovery style moves.
        if (atk.healBlockTurns > 0) {
            boolean recoveryMove = hasEffect(effects, "heal") || hasEffect(effects, "drain") || hasEffect(effects, "wish")
                    || hasEffect(effects, "ingrain") || hasEffect(effects, "aqua_ring") || "recover".equalsIgnoreCase(moveId)
                    || "roost".equalsIgnoreCase(moveId) || "slackoff".equalsIgnoreCase(moveId) || "softboiled".equalsIgnoreCase(moveId)
                    || "milkdrink".equalsIgnoreCase(moveId) || "moonlight".equalsIgnoreCase(moveId) || "morningsun".equalsIgnoreCase(moveId)
                    || "synthesis".equalsIgnoreCase(moveId) || "rest".equalsIgnoreCase(moveId);
            if (recoveryMove) {
                out.add(plugin.getLang().uiFmt("battle.log.heal_block.block", "§7{atk} 处于回复封锁状态，无法回复体力！", java.util.Map.of("atk", atkName)));
                return out;
            }
        }

        // Protect-like effects: in this simplified engine we block damaging and directly-targeted status effects.
        if (def != null && def.protectTurnsRemaining > 0 && move != null && move.id() != null) {
            if (move.power() > 0 || moveTargetsOpponent(effects)) {
                String midProtect = move.id().toLowerCase(java.util.Locale.ROOT);
                if (!"feint".equals(midProtect) && !"shadowforce".equals(midProtect) && !"shadow_force".equals(midProtect) && !"phantomforce".equals(midProtect) && !"phantom_force".equals(midProtect)) {
                    if (!AbilityEffects.bypassesProtect(atk, move)) {
                        out.add(plugin.getLang().uiFmt("battle.log.protect", "§7{def} 保护了自己！", java.util.Map.of("def", defName)));
                        return out;
                    }
                }
            }
        }

        // Special: Mirror Move copies the target's last used move.
        if (findEffect(effects, "mirror_move") != null) {
            String src = def.lastMoveId;
            if (src == null || src.isBlank()) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                return out;
            }
            src = src.toLowerCase();
            if ("mirrormove".equals(src) || "metronome".equals(src)) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                return out;
            }
            Move copied = plugin.getDex().getMoveOrPlaceholder(src);
            if (copied == null || copied.id() == null) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                return out;
            }
            if (!plugin.getDex().isMoveAllowed(copied.id())) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                return out;
            }
            // Execute the copied move as if used by the current attacker.
            out.add(plugin.getLang().uiFmt("battle.log.mirror_move", "§7{atk} 使用了鹦鹉学舌！", java.util.Map.of("atk", atkName)));
            out.addAll(execute(plugin, viewer, s, playerActing, copied, atk, def, atkS, defS, atkName, defName));
            return out;
        }

        // --- Weather moves (minimal core) ---
        // We implement by move id to avoid needing full effect parsing.
        String mvId = move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT);
        if ("sunnyday".equals(mvId) || "sunny_day".equals(mvId)) {
            int turns = WeatherSystem.durationForSource(WeatherType.SUN, atk);
            WeatherSystem.setWeather(s, WeatherType.SUN, turns);
            out.add(plugin.getLang().uiFmt("battle.log.weather.sun", "§e阳光变得强烈了！ §7({n}回合)", java.util.Map.of("n", String.valueOf(turns))));
            if (atk != null) atk.lastMoveFailed = false;
            return out;
        }
        if ("raindance".equals(mvId) || "rain_dance".equals(mvId)) {
            int turns = WeatherSystem.durationForSource(WeatherType.RAIN, atk);
            WeatherSystem.setWeather(s, WeatherType.RAIN, turns);
            out.add(plugin.getLang().uiFmt("battle.log.weather.rain", "§b开始下雨了！ §7({n}回合)", java.util.Map.of("n", String.valueOf(turns))));
            if (atk != null) atk.lastMoveFailed = false;
            return out;
        }
        if ("sandstorm".equals(mvId) || "sand_storm".equals(mvId)) {
            int turns = WeatherSystem.durationForSource(WeatherType.SAND, atk);
            WeatherSystem.setWeather(s, WeatherType.SAND, turns);
            out.add(plugin.getLang().uiFmt("battle.log.weather.sandstorm", "§e沙暴刮起来了！ §7({n}回合)", java.util.Map.of("n", String.valueOf(turns))));
            if (atk != null) atk.lastMoveFailed = false;
            return out;
        }
        if ("hail".equals(mvId)) {
            int turns = WeatherSystem.durationForSource(WeatherType.HAIL, atk);
            WeatherSystem.setWeather(s, WeatherType.HAIL, turns);
            out.add(plugin.getLang().uiFmt("battle.log.weather.hail", "§b开始下冰雹了！ §7({n}回合)", java.util.Map.of("n", String.valueOf(turns))));
            if (atk != null) atk.lastMoveFailed = false;
            return out;
        }

        if ("electricterrain".equals(mvId) || "electric_terrain".equals(mvId)) {
            s.terrain = "electric";
            s.terrainTurns = 5;
            out.add(plugin.getLang().ui("battle.log.terrain.electric", "§7电气场地展开了！"));
            atk.lastMoveFailed = false;
            return out;
        }
        if ("grassyterrain".equals(mvId) || "grassy_terrain".equals(mvId)) {
            s.terrain = "grassy";
            s.terrainTurns = 5;
            out.add(plugin.getLang().ui("battle.log.terrain.grassy", "§7青草场地展开了！"));
            atk.lastMoveFailed = false;
            return out;
        }
        if ("mistyterrain".equals(mvId) || "misty_terrain".equals(mvId)) {
            s.terrain = "misty";
            s.terrainTurns = 5;
            out.add(plugin.getLang().ui("battle.log.terrain.misty", "§7薄雾场地展开了！"));
            atk.lastMoveFailed = false;
            return out;
        }
        if ("psychicterrain".equals(mvId) || "psychic_terrain".equals(mvId)) {
            s.terrain = "psychic";
            s.terrainTurns = 5;
            out.add(plugin.getLang().ui("battle.log.terrain.psychic", "§7精神场地展开了！"));
            atk.lastMoveFailed = false;
            return out;
        }
        if ("fairylock".equals(mvId) || "fairy_lock".equals(mvId)) {
            s.fairyLockTurns = Math.max(s.fairyLockTurns, 2);
            out.add(plugin.getLang().ui("battle.log.fairy_lock", "§7仙境之锁封住了战场！"));
            atk.lastMoveFailed = false;
            return out;
        }

        
        
        // Special: Phazing (Roar/Whirlwind) - in this plugin's wild battles, treat as ending the battle.
        if (findEffect(effects, "phaze") != null) {
            if (def != null && AbilityEffects.preventsPhazing(def) && !AbilityEffects.ignoresDefenderAbility(atk)) {
                out.add(plugin.getLang().uiFmt("battle.log.ability.suction_cups", "§6【吸盘】§e{def} 扎稳了身体，没有被吹飞！", java.util.Map.of("def", defName)));
                return out;
            }
            String mvName = (move.name() == null || move.name().isBlank()) ? move.id() : move.name();
            if (playerActing) {
                plugin.battles().endBattle(s.playerId, plugin.getLang().uiFmt("battle.end.blowaway_player", "§e你使用了 {move}，战斗结束。", java.util.Map.of("move", mvName)));
            } else {
                plugin.battles().endBattle(s.playerId, plugin.getLang().uiFmt("battle.end.blowaway_wild", "§e野生 {def} 被 {move} 吹走了！战斗结束。", java.util.Map.of("def", defName, "move", mvName)));
            }
            if (viewer != null) viewer.closeInventory();
            s.finished = true;
            return out;
        }

        // Special: Teleport - Gen1: escapes only in wild battles; here we only have wild battles, so end the battle.
        if (findEffect(effects, "teleport") != null) {
            if (playerActing) {
                plugin.battles().endBattle(s.playerId, plugin.getLang().ui("battle.end.teleport_player", "§e你使用了瞬间移动，成功逃跑！"));
            } else {
                plugin.battles().endBattle(s.playerId, plugin.getLang().uiFmt("battle.end.teleport_wild", "§e野生 {atk} 使用了瞬间移动，逃走了！", java.util.Map.of("atk", atkName)));
            }
            if (viewer != null) viewer.closeInventory();
            s.finished = true;
            return out;
        }

        // Special: Focus Energy (Gen1 bug) - sets a flag that quarters crit chance while active.
        if (findEffect(effects, "focus_energy") != null) {
            if (atk.focusEnergyActive) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7但是失败了。"));
                return out;
            }
            atk.focusEnergyActive = true;
            out.add(plugin.getLang().uiFmt("battle.log.focus_energy", "§a{atk} 集中精神！(Gen1)", java.util.Map.of("atk", atkName)));
            if (atk != null) atk.lastMoveFailed = false;
            return out;
        }

// Special: Transform copies the target's species (battle-only), types, stat stages, and moves.
        if (findEffect(effects, "transform") != null) {
            // If already transformed, fail like Gen1.
            if (atk.overrideSpeciesId != null) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                return out;
            }

            // Lock HP before applying the species override: in Gen1, Transform does not change max HP.
            try {
                Species baseS = plugin.getDex().getSpecies(atk.speciesId);
                if (baseS != null) {
                    atk.lockedMaxHp = atk.maxHp(baseS);
                    // Ensure current HP does not exceed locked max (safety).
                    atk.currentHp = Math.min(atk.currentHp, atk.lockedMaxHp);
                }
            } catch (Exception ignore) {
            }
            atk.overrideSpeciesId = def.effectiveSpeciesId();

            // Copy current types: if target has temporary type overrides (Conversion etc.) use them,
            // otherwise copy the target's species types (defS is the already-resolved effective species for defender).
            try {
                java.util.List<String> tps = defS == null ? java.util.List.of() : defS.types();
                String t1 = (def.overrideType1 != null) ? def.overrideType1 : (tps.size() >= 1 ? tps.get(0) : null);
                String t2 = (def.overrideType2 != null) ? def.overrideType2 : (tps.size() >= 2 ? tps.get(1) : null);
                atk.overrideType1 = t1;
                atk.overrideType2 = t2;
            } catch (Exception ignore) {
                atk.overrideType1 = def.overrideType1;
                atk.overrideType2 = def.overrideType2;
            }

            // Copy stat stages (Gen1 Transform copies opponent's current stat modifiers; HP stays as-is).
            atk.stageAtk = def.stageAtk;
            atk.stageDef = def.stageDef;
            atk.stageSpa = def.stageSpa;
            atk.stageSpd = def.stageSpd;
            atk.stageSpe = def.stageSpe;
            atk.stageAccuracy = def.stageAccuracy;
            atk.stageEvasion = def.stageEvasion;

            // Copy moves (battle-only) and set PP to 5 for each copied move (Gen1 behavior).
            atk.overrideMoves = PokemonInstance.deepCopyMoveSlots(def.effectiveMoves());
            while (atk.overrideMoves.size() < 4) atk.overrideMoves.add(null);
            for (int i = 0; i < atk.overrideMoves.size(); i++) {
                MoveSlot ms = atk.overrideMoves.get(i);
                if (ms == null) continue;
                ms.basePp = 5;
                ms.ppUpsUsed = 0;
                ms.recalcMaxPp();
                ms.pp = 5;
            }

            out.add(plugin.getLang().uiFmt("battle.log.transform", "§b{atk} 变身成了 {def}！", java.util.Map.of("atk", atkName, "def", defName)));
            if (atk != null) atk.lastMoveFailed = false;
            return out;
        }

        // Special: Mimic replaces the user's Mimic slot with the target's last used move (battle-only).
        if (findEffect(effects, "mimic") != null) {
            String src = def.lastMoveId;
            if (src == null || src.isBlank()) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                return out;
            }
            src = src.toLowerCase();
            if ("mimic".equals(src) || "transform".equals(src) || "metronome".equals(src) || "mirrormove".equals(src) || "struggle".equals(src)) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                return out;
            }
            Move copied = plugin.getDex().getMoveOrPlaceholder(src);
            if (copied == null || copied.id() == null) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                return out;
            }

            // Ensure we switch to battle-only moveset (so we don't pollute saves).
            atk.ensureBattleMovesFromBase();

            // Replace the first slot that matches Mimic; fallback to slot 0.
            int replaceIdx = 0;
            java.util.List<MoveSlot> mvList = atk.effectiveMoves();
            for (int i = 0; i < mvList.size(); i++) {
                MoveSlot ms = mvList.get(i);
                if (ms != null && ms.moveId != null && ms.moveId.equalsIgnoreCase(move.id())) { replaceIdx = i; break; }
            }
            MoveSlot newSlot = MoveSlot.of(copied);
            newSlot.basePp = 5;
            newSlot.ppUpsUsed = 0;
            newSlot.recalcMaxPp();
            newSlot.pp = 5;
            // Keep list size safe.
            while (mvList.size() < 4) mvList.add(null);
            mvList.set(replaceIdx, newSlot);

            LangManager lang = plugin.getLang();
            String mvName = lang == null ? copied.name() : lang.move(copied.id(), copied.name());
            out.add(plugin.getLang().uiFmt("battle.log.learn_temp", "§d{atk} 学会了 §f{move}§d！（战斗结束后失效）", java.util.Map.of("atk", atkName, "move", mvName)));
            if (atk != null) atk.lastMoveFailed = false;
            return out;
        }

        // Special: Metronome randomly uses another move.
        if (findEffect(effects, "metronome") != null) {
            java.util.List<Move> pool = new java.util.ArrayList<>();
            for (Move m2 : plugin.getDex().allMoves()) {
                if (m2 == null || m2.id() == null) continue;
                String id = m2.id().toLowerCase();
                if (!plugin.getDex().isMoveAllowed(id)) continue;
                if ("metronome".equals(id) || "struggle".equals(id) || "mirrormove".equals(id) || "transform".equals(id) || "mimic".equals(id)) continue;
                pool.add(m2);
            }
            if (pool.isEmpty()) {
                out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                return out;
            }
            Move picked = pool.get(Util.RND.nextInt(pool.size()));
            LangManager lang = plugin.getLang();
            String mvName = lang == null ? picked.name() : lang.move(picked.id(), picked.name());
            out.add(plugin.getLang().uiFmt("battle.log.metronome", "§7{atk} 挥指使出了 §f{move}§7！", java.util.Map.of("atk", atkName, "move", mvName)));
            if (atk != null) atk.lastMoveFailed = false;
            out.addAll(execute(plugin, viewer, s, playerActing, picked, atk, def, atkS, defS, atkName, defName));
            return out;
        }

// Pre-check: starting a two-turn move (charge turn)
        boolean forcedNoCharge = false;
        if (atk.chargingMoveId == null) {
            for (Map<String, Object> ef : effects) {
                if (ef == null || ef.isEmpty()) continue;
                String kind = asString(ef.get("kind"), asString(ef.get("id"), null));
                if (kind == null) continue;
                kind = kind.toLowerCase();
                if (!"two_turn".equals(kind)) continue;
                String chargeText = asString(ef.get("chargeText"), null);
                // Power Herb: skip the charging turn once.
                if ("power_herb".equalsIgnoreCase(atk.heldItemId)) {
                    atk.heldItemId = null;
                    forcedNoCharge = true;
                    out.add(plugin.getLang().uiFmt("battle.log.power_herb", "§e{atk} 的§f强力香草§e让它立刻行动！", java.util.Map.of("atk", atkName)));
                    break;
                }
                // Sun: Solar Beam fires immediately (no charging).
                WeatherType ew2 = WeatherSystem.effectiveWeather(s);
                String mid2 = move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT);
                if ("solarbeam".equals(mid2) && ew2 == WeatherType.SUN) {
                    forcedNoCharge = true;
                    out.add(plugin.getLang().ui("battle.log.harsh_sun_now", "§e阳光很强烈！"));
                    break;
                }
                atk.chargingMoveId = move.id();
                atk.chargingTurnsRemaining = (int) asDouble(ef.get("chargeTurns"), 1);
                if (chargeText != null) out.add(chargeText.replace("{user}", atkName));
                else out.add(plugin.getLang().uiFmt("battle.log.charging", "§7{atk} 蓄力中……", java.util.Map.of("atk", atkName)));
                return out;
            }
        }

        // Execute
        boolean didDamage = false;
        int damageDone = 0;
        int hitsDone = 0;
        boolean wonderGuardBlockedMove = false;

        // Special damage rules
        boolean hasOhko = hasEffect(effects, "ohko");
        Map<String, Object> fixedEf = findEffect(effects, "fixed_damage");

        // OHKO takes priority
        if (hasOhko) {
            String moveType = HeldItemEffects.overrideMoveType(atk, atkS, move);
            double eff = TypeChart.effectiveness(moveType, defS.types());
            if (eff == 0.0) {
                out.add(plugin.getLang().ui("battle.log.no_effect", "§7没有效果！"));
            } else {
                // OHKO: if target has a substitute, it breaks it instead of fainting.
                int dmg = def.maxHp(defS);
                int dealt = applyDamageToTarget(def, defS, dmg, out, defName, false);
                if (def != null && dealt > 0) { recordReactiveDamage(s, atk, def, move, dealt); def.rageFistHits++; }

                // Anger Point only triggers on critical hits. OHKO moves are not treated as critical hits.
                didDamage = true;
                damageDone = dealt;
                out.add(plugin.getLang().ui("battle.log.ohko", "§c一击必杀！！"));
            }
        } else if (fixedEf != null && !"status".equalsIgnoreCase(move.category())) {
            String moveType = HeldItemEffects.overrideMoveType(atk, atkS, move);
            double eff = TypeChart.effectiveness(moveType, defS.types());
            if (eff == 0.0) {
                out.add(plugin.getLang().ui("battle.log.no_effect", "§7没有效果！"));
            } else {
                int dmg = 0;
                String mode = asString(fixedEf.get("mode"), "amount");
                if ("level".equalsIgnoreCase(mode)) {
                    dmg = Math.max(1, atk.level);
                } else if ("half".equalsIgnoreCase(mode)) {
                    dmg = Math.max(1, def.currentHp / 2);
                } else {
                    dmg = (int) asDouble(fixedEf.get("amount"), 0);
                    dmg = Math.max(1, dmg);
                }
                int dealt = applyDamageToTarget(def, defS, dmg, out, defName, false);
                if (def != null && dealt > 0) { recordReactiveDamage(s, atk, def, move, dealt); def.rageFistHits++; }
                didDamage = true;
                damageDone = dealt;
                hitsDone = 1;

                // Track damage taken for Bide / Counter-like moves
                if (def.bideActive) def.bideDamageTaken += dealt;

                if (eff > 1.0) out.add(plugin.getLang().ui("battle.log.super_effective", "§c效果拔群！"));
                else if (eff < 1.0) out.add(plugin.getLang().ui("battle.log.not_very_effective", "§7效果不太好…"));
                out.add(plugin.getLang().uiFmt("battle.log.deal_damage", "§f造成了 §c{dmg}§f 点伤害！ §7({hp}/{max})", java.util.Map.of("dmg", String.valueOf(dmg), "hp", String.valueOf(def.currentHp), "max", String.valueOf(def.maxHp(defS)))));
            }
        } else if (!"status".equalsIgnoreCase(move.category())) {
            // Determine multi-hit
            int minHits = 1;
            int maxHits = 1;
            Map<String, Object> mhEf = findEffect(effects, "multi_hit");
            if (mhEf != null) {
                minHits = (int) asDouble(mhEf.get("min"), asDouble(mhEf.get("hits"), 2));
                maxHits = (int) asDouble(mhEf.get("max"), asDouble(mhEf.get("hits"), minHits));
                minHits = Math.max(1, minHits);
                maxHits = Math.max(minHits, maxHits);
            }

            int total = 0;
            int hits;
            // Skill Link: for 2-5 hit moves, always hit 5 times.
            if (atk != null && AbilityEffects.has(atk, "skilllink") && minHits == 2 && maxHits == 5) {
                hits = 5;
            }
            // Loaded Dice: for 2-5 hit moves, make it likely to hit 4-5 times.
            // We implement the modern behavior (guaranteed 4-5) for simplicity.
            else if (atk != null && "loaded_dice".equalsIgnoreCase(atk.heldItemId)
                    && minHits == 2 && maxHits == 5) {
                hits = 4 + Util.RND.nextInt(2); // 4-5
            } else {
                hits = (minHits == maxHits) ? minHits : (minHits + Util.RND.nextInt(maxHits - minHits + 1));
            }
            // --- Dynamic move power (A module extensions) ---
            // Fling: power depends on held item; always single-hit physical Dark in our model.
            String _mid = (move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT));
            Move moveForDamage = move;
            if ("pursuit".equals(_mid) && (defenderPlannedId.equals("uturn") || defenderPlannedId.equals("voltswitch") || defenderPlannedId.equals("flipturn") || defenderPlannedId.equals("partingshot") || defenderPlannedId.equals("batonpass") || defenderPlannedId.equals("teleport") || defenderPlannedId.equals("chillyreception"))) {
                moveForDamage = new Move(move.id(), move.name(), move.type(), move.category(), Math.max(1, move.power() * 2), move.accuracy(), move.pp(), move.priority(), move.num(), move.effect(), move.effects());
            }
            if (("eruption".equals(_mid) || "waterspout".equals(_mid)) && atk != null && atkS != null) {
                int maxHp = Math.max(1, atk.maxHp(atkS));
                int dynPower = Math.max(1, (150 * Math.max(0, atk.currentHp)) / maxHp);
                moveForDamage = new Move(move.id(), move.name(), move.type(), move.category(), dynPower, move.accuracy(), move.pp(), move.priority(), move.num(), move.effect(), move.effects());
            }
            String flingItemId = null;
            int flingPower = 0;
            boolean isFling = "fling".equals(_mid);
            if ("electroball".equals(_mid) && atk != null && def != null && atkS != null && defS != null) {
                int atkSpe = Math.max(1, (int) Math.floor(atk.calcStat(atkS, "spe", atk.ivSpe, atk.evSpe, false) * atk.stageMultiplier("spe")));
                int defSpe = Math.max(1, (int) Math.floor(def.calcStat(defS, "spe", def.ivSpe, def.evSpe, false) * def.stageMultiplier("spe")));
                double ratio = (double) atkSpe / (double) defSpe;
                int dynPower = ratio >= 4.0 ? 150 : ratio >= 3.0 ? 120 : ratio >= 2.0 ? 80 : ratio > 1.0 ? 60 : 40;
                moveForDamage = new Move(move.id(), move.name(), move.type(), move.category(), dynPower, move.accuracy(), move.pp(), move.priority(), move.num(), move.effect(), move.effects());
            }
            if ("gyroball".equals(_mid) && atk != null && def != null && atkS != null && defS != null) {
                int atkSpe = Math.max(1, (int) Math.floor(atk.calcStat(atkS, "spe", atk.ivSpe, atk.evSpe, false) * atk.stageMultiplier("spe")));
                int defSpe = Math.max(1, (int) Math.floor(def.calcStat(defS, "spe", def.ivSpe, def.evSpe, false) * def.stageMultiplier("spe")));
                int dynPower = Math.max(1, Math.min(150, (25 * defSpe) / atkSpe));
                moveForDamage = new Move(move.id(), move.name(), move.type(), move.category(), dynPower, move.accuracy(), move.pp(), move.priority(), move.num(), move.effect(), move.effects());
            }
            if (isFling) {
                flingItemId = atk == null ? null : atk.heldItemId;
                if (flingItemId == null || flingItemId.isBlank() || HeldItemEffects.isUnremovableItemId(flingItemId)) {
                    out.add(plugin.getLang().uiFmt("battle.log.throw_no_item", "§6【投掷】§e{atk} 没有可投掷的道具！", java.util.Map.of("atk", atkName)));
                    hits = 0;
                } else {
                    flingPower = HeldItemEffects.flingPower(flingItemId);
                    // Build a temporary Move with adjusted power/type/category.
                    moveForDamage = new Move(move.id(), move.name(), "dark", "physical", flingPower, move.accuracy(), move.pp(), move.priority(), move.num(), move.effect(), move.effects());
                    hits = 1;
                }
            }
            for (int i = 0; i < hits; i++) {
                if (def.currentHp <= 0) break;
                String defAbilityNorm = AbilityEffects.norm(def.abilityId);
                if (moveForDamage.power() > 0 && !AbilityEffects.ignoresDefenderAbility(atk)) {
                    if ("disguise".equals(defAbilityNorm) && !def.disguiseBroken) {
                        def.disguiseBroken = true;
                        def.overrideSpeciesId = "mimikyubusted";
                        out.add(plugin.getLang().uiFmt("battle.log.ability.disguise", "§6【Disguise】§e{def} took the hit in disguise!", java.util.Map.of("def", defName)));
                        continue;
                    }
                    if ("iceface".equals(defAbilityNorm) && !def.iceFaceBroken && "physical".equalsIgnoreCase(moveForDamage.category())) {
                        def.iceFaceBroken = true;
                        def.overrideSpeciesId = "eiscuenoice";
                        out.add(plugin.getLang().uiFmt("battle.log.ability.ice_face", "§6【Ice Face】§e{def} protected itself with its ice face!", java.util.Map.of("def", defName)));
                        continue;
                    }
                }
                String moveTypeForHit = HeldItemEffects.overrideMoveType(atk, atkS, moveForDamage);
                double effForHit = TypeChart.effectiveness(moveTypeForHit, defS.types());
                if (effForHit == 0.0 && def != null && def.identifiedTarget
                        && ("normal".equalsIgnoreCase(moveTypeForHit) || "fighting".equalsIgnoreCase(moveTypeForHit))) {
                    effForHit = 1.0;
                }
                if (effForHit == 0.0 && def != null && def.miracleEyeTarget && "psychic".equalsIgnoreCase(moveTypeForHit)) {
                    effForHit = 1.0;
                }
                if (effForHit == 0.0 && atk != null && (AbilityEffects.has(atk, "scrappy") || AbilityEffects.has(atk, "mindseye"))
                        && defS.types().stream().anyMatch(t -> "ghost".equalsIgnoreCase(t))) {
                    if ("normal".equalsIgnoreCase(moveTypeForHit) || "fighting".equalsIgnoreCase(moveTypeForHit)) {
                        effForHit = 1.0;
                    }
                }
                if (def != null && def.tarShotActive && "fire".equalsIgnoreCase(moveTypeForHit)) effForHit *= 2.0;
                if (AbilityEffects.blocksWonderGuardDamage(def, atk, moveForDamage, moveTypeForHit, effForHit)) {
                    wonderGuardBlockedMove = true;
                    LangManager langWg = plugin.getLang();
                    out.add(langWg == null
                            ? ("§6【神奇守护】§e" + defName + " 挡住了攻击！")
                            : langWg.uiFmt("battle.log.ability.wonder_guard_block", "§6【神奇守护】§e{def} 挡住了攻击！", java.util.Map.of("def", defName)));
                    if (i == 0) out.add(plugin.getLang().ui("battle.log.no_effect", "§7没有效果！"));
                    continue;
                }
                int dmg = Damage.calcDamage(atk, def, atkS, defS, moveForDamage);
                // Knock Off: if the target has a removable item, damage is boosted (like modern gens).
                if (("knockoff".equals(_mid) || "knock_off".equals(_mid)) && def != null) {
                    String it = def.heldItemId;
                    boolean removable = it != null && !it.isBlank() && !HeldItemEffects.isUnremovableItemId(it);
                    boolean stickyBlocked = removable && AbilityEffects.preventsItemRemoval(def) && !(atk != null && AbilityEffects.ignoresDefenderAbility(atk));
                    if (removable && !stickyBlocked && dmg > 0) {
                        dmg = Math.max(1, (int) Math.floor(dmg * 1.5));
                    }
                }
                // Weather move power modifiers (Rain/Sun)
                try {
                    String mt = HeldItemEffects.overrideMoveType(atk, atkS, move);
                    double wm = WeatherSystem.movePowerMultiplier(s, mt);
                    if (wm != 1.0 && dmg > 0) dmg = Math.max(1, (int) Math.floor(dmg * wm));
                    // Solar Beam: power is halved in non-sunny weather.
                    WeatherType ew3 = WeatherSystem.effectiveWeather(s);
                    String mid3 = move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT);
                    if ("solarbeam".equals(mid3) && ew3 != WeatherType.NONE && ew3 != WeatherType.SUN && dmg > 0) {
                        dmg = Math.max(1, (int) Math.floor(dmg * 0.5));
                    }
                } catch (Throwable ignored) {}
                
                // Weather-dependent ability damage modifiers (Solar Power / Sand Force etc)
                try {
                    String mt2 = HeldItemEffects.overrideMoveType(atk, atkS, move);
                    double am = AbilityEffects.weatherAttackerDamageMultiplier(atk, move, s, mt2);
                    if (am != 1.0 && dmg > 0) dmg = Math.max(1, (int) Math.floor(dmg * am));
                    double dm = AbilityEffects.weatherDefenderDamageMultiplier(def, defS, move, s);
                    if (dm != 1.0 && dmg > 0) dmg = Math.max(1, (int) Math.floor(dmg * dm));
                } catch (Throwable ignored) {}
boolean wasCrit = Damage.lastWasCrit();

                // Screens reduction (simplified Gen1: halve physical/special)
                boolean defIsPlayer = !playerActing;
                if ("physical".equalsIgnoreCase(move.category())) {
                    int turns = defIsPlayer ? s.playerReflectTurns : s.wildReflectTurns;
                    if (turns > 0) dmg = Math.max(1, dmg / 2);
                    int veil = defIsPlayer ? s.playerAuroraVeilTurns : s.wildAuroraVeilTurns;
                    if (veil > 0) dmg = Math.max(1, dmg / 2);
                } else if ("special".equalsIgnoreCase(move.category())) {
                    int turns = defIsPlayer ? s.playerLightScreenTurns : s.wildLightScreenTurns;
                    if (turns > 0) dmg = Math.max(1, dmg / 2);
                    int veil = defIsPlayer ? s.playerAuroraVeilTurns : s.wildAuroraVeilTurns;
                    if (veil > 0) dmg = Math.max(1, dmg / 2);
                }

                int dealt = applyDamageToTarget(def, defS, dmg, out, defName, false);
                if (def != null && dealt > 0) { recordReactiveDamage(s, atk, def, move, dealt); def.rageFistHits++; }
                didDamage = true;
                total += dealt;
                hitsDone++;

                // Track damage taken for Bide / Counter-like moves
                if (def.bideActive) def.bideDamageTaken += dealt;

                // Only show effectiveness once (on first hit)
                if (i == 0) {
                    String moveTypeMsg = HeldItemEffects.overrideMoveType(atk, atkS, move);
                    double effMsg = TypeChart.effectiveness(moveTypeMsg, defS.types());
                    if (effMsg == 0.0) out.add(plugin.getLang().ui("battle.log.no_effect", "§7没有效果！"));
                    else if (effMsg > 1.0) out.add(plugin.getLang().ui("battle.log.super_effective", "§c效果拔群！"));
                    else if (effMsg < 1.0) out.add(plugin.getLang().ui("battle.log.not_very_effective", "§7效果不太好…"));
                }
            }

            damageDone = total;
            if (hitsDone > 1) out.add(plugin.getLang().uiFmt("battle.log.hit_times", "§f命中了 §e{n}§f 次！", java.util.Map.of("n", String.valueOf(hitsDone))));
            out.add(plugin.getLang().uiFmt("battle.log.deal_damage", "§f造成了 §c{dmg}§f 点伤害！ §7({hp}/{max})", java.util.Map.of("dmg", String.valueOf(total), "hp", String.valueOf(def.currentHp), "max", String.valueOf(def.maxHp(defS)))));
        }

        // Reactive held items on taking damage.
        if (didDamage && damageDone > 0) {
            // Track pre-hit item for post-processing (e.g. Eject Button auto switch)
            String preDefItem = def == null ? null : def.heldItemId;
            String moveTypePost = HeldItemEffects.overrideMoveType(atk, atkS, move);

            // Justified: when hit by a Dark-type move, raise Attack.
            if (def != null && def.currentHp > 0 && AbilityEffects.has(def, "justified") && "dark".equalsIgnoreCase(moveTypePost)) {
                int before = def.stageAtk;
                def.applyStage("atk", 1);
                if (def.stageAtk != before) out.add(plugin.getLang().uiFmt("battle.log.ability.justified", "§6【正义之心】§e{def} 的攻击提升了！", java.util.Map.of("def", defName)));
            }

            // Anger Point: if struck by a critical hit, max out Attack.
            if (def != null && def.currentHp > 0 && Damage.lastWasCrit()
                    && !AbilityEffects.ignoresDefenderAbility(atk)
                    && AbilityEffects.has(def, "angerpoint")) {
                if (def.stageAtk < 6) {
                    def.stageAtk = 6;
                    out.add(plugin.getLang().uiFmt("battle.log.ability.anger_point", "§6【愤怒穴位】§e{def} 的攻击最大化了！", java.util.Map.of("def", defName)));
                }
            }

            // Cute Charm: contact moves may infatuate the attacker (approx).
            // Blocked by Protective Pads / Punching Glove on attacker; prevented by Oblivious.
            boolean contact = atk != null && def != null
                    && "physical".equalsIgnoreCase(move.category())
                    && atk.currentHp > 0
                    && !"protective_pads".equalsIgnoreCase(atk.heldItemId)
                    && !"punching_glove".equalsIgnoreCase(atk.heldItemId)
                    && !AbilityEffects.contactBlockedByLongReach(atk);
            if (contact && !AbilityEffects.ignoresDefenderAbility(atk)
                    && AbilityEffects.has(def, "cutecharm")
                    && atk != null && !atk.infatuated
                    && !AbilityEffects.has(atk, "oblivious")) {
                String gA = atk.gender == null ? "" : atk.gender;
                String gD = def.gender == null ? "" : def.gender;
                boolean opp = ("M".equalsIgnoreCase(gA) || "F".equalsIgnoreCase(gA))
                        && ("M".equalsIgnoreCase(gD) || "F".equalsIgnoreCase(gD))
                        && !gA.equalsIgnoreCase(gD);
                if (opp && Util.RND.nextDouble() < 0.30) {
                    atk.infatuated = true;
                    out.add(plugin.getLang().uiFmt("battle.log.ability.cute_charm", "§6【迷人之躯】§d{atk} 陷入了爱河！", java.util.Map.of("atk", atkName)));
                }
            }
            out.addAll(HeldItemEffects.onDamagedByMove(def, defS, defName, move, damageDone, TypeChart.effectiveness(moveTypePost, defS.types())));

            // Eject Button: if it was consumed and defender is the player's active Pokémon, force switch.
            if (preDefItem != null && "eject_button".equalsIgnoreCase(preDefItem)
                    && def != null && def.currentHp > 0 && def.heldItemId == null
                    && s != null && s.playerMon == def) {
                out.addAll(forceAutoSwitch(plugin, viewer, s, plugin.getLang().ui("battle.log.item.escape_button", "§e逃脱按键让你撤回了宝可梦！")));
            }
            if (def != null && def.emergencyExitTriggered && def.currentHp > 0 && s != null && s.playerMon == def) {
                def.emergencyExitTriggered = false;
                out.addAll(forceAutoSwitch(plugin, viewer, s, "§e特性让你撤回了宝可梦！"));
            }

		// Rocky Helmet: recoil on contact (simplified as any physical damaging move).
		// Protective Pads / Punching Glove: prevent contact effects on the attacker.
		if (didDamage && damageDone > 0 && atk != null && def != null
				&& "physical".equalsIgnoreCase(move.category())
				&& "rocky_helmet".equalsIgnoreCase(def.heldItemId)
				&& atk.currentHp > 0
				&& !"protective_pads".equalsIgnoreCase(atk.heldItemId)
				&& !"punching_glove".equalsIgnoreCase(atk.heldItemId)
				&& !AbilityEffects.has(atk, "magicguard")) {
			int recoil = Math.max(1, atk.maxHp(atkS) / 6);
			atk.currentHp = Math.max(0, atk.currentHp - recoil);
			try { if (plugin != null && plugin.getEvolutionManager() != null && viewer != null) plugin.getEvolutionManager().onBasculinRecoil(viewer.getUniqueId(), atk, recoil); } catch (Throwable ignored) {}
			out.add(plugin.getLang().uiFmt("battle.log.item.rocky_helmet", "§6{def} 的§f凸凸头盔§6反弹了伤害！ §7(-{n})", java.util.Map.of("def", defName, "n", String.valueOf(recoil))));
		}

		// Red Card: when the holder is hit by a damaging move, force the attacker out (player side only).
		if (didDamage && damageDone > 0 && atk != null && def != null
				&& "red_card".equalsIgnoreCase(def.heldItemId)
				&& s != null && s.playerMon == atk
				&& atk.currentHp > 0) {
			def.heldItemId = null; // consume
			out.add(plugin.getLang().uiFmt("battle.log.item.eject_button", "§e{def} 的§f红牌§e发动了！", java.util.Map.of("def", defName)));
			out.addAll(forceAutoSwitch(plugin, viewer, s, plugin.getLang().ui("battle.log.item.eject_button_force", "§e红牌把你的宝可梦弹回去了！")));
		}
        }

        if (didDamage && damageDone > 0 && atk != null && def != null && move != null && move.id() != null) {
            String midAfter = move.id().toLowerCase(java.util.Locale.ROOT).replace("_", "");
            if ("wakeupslap".equals(midAfter) && "sleep".equalsIgnoreCase(def.status)) {
                def.status = "none";
                def.sleepTurns = 0;
                out.add(plugin.getLang().uiFmt("battle.log.wakeup_slap", "§7{def} 被打醒了！", java.util.Map.of("def", defName)));
            }
            if ("smellingsalts".equals(midAfter) && "paralyze".equalsIgnoreCase(def.status)) {
                def.status = "none";
                out.add(plugin.getLang().uiFmt("battle.log.smelling_salts", "§7{def} 的麻痹被治好了！", java.util.Map.of("def", defName)));
            }
            String usedType = HeldItemEffects.overrideMoveType(atk, atkS, move);
            if ("weatherball".equals(midAfter)) {
                WeatherType ew4 = WeatherSystem.effectiveWeather(s);
                if (ew4 == WeatherType.SUN) usedType = "fire";
                else if (ew4 == WeatherType.RAIN) usedType = "water";
                else if (ew4 == WeatherType.SAND) usedType = "rock";
                else if (ew4 == WeatherType.HAIL) usedType = "ice";
            }
            if (atk.chargeActive && "electric".equalsIgnoreCase(usedType) && move.power() > 0) {
                atk.chargeActive = false;
            }
            if (atk.laserFocusTurns > 0 && move.power() > 0) atk.laserFocusTurns = 0;
            atk.electrifiedThisTurn = false;
        }

if (didDamage && damageDone > 0 && atk != null && def != null && def.currentHp <= 0 && def.grudgeActive) {
    zeroMovePp(atk, move.id());
    def.grudgeActive = false;
    out.add(plugin.getLang().uiFmt("battle.log.grudge.apply", "§7{def} 的怨恨让 {atk} 的招式 PP 归零了！", java.util.Map.of("def", defName, "atk", atkName)));
}

if (didDamage && damageDone > 0 && atk != null && def != null && def.currentHp <= 0 && def.destinyBondActive && atk.currentHp > 0) {
    atk.currentHp = 0;
    def.destinyBondActive = false;
    out.add(plugin.getLang().uiFmt("battle.log.destiny_bond.apply", "§c{def} 用同命带走了 {atk}！", java.util.Map.of("def", defName, "atk", atkName)));
}

        // Shell Bell: heal after dealing damage.
        if (didDamage && damageDone > 0 && atk != null && "shell_bell".equalsIgnoreCase(atk.heldItemId)) {
            int heal = Math.max(1, damageDone / 8);
            int before = atk.currentHp;
            atk.currentHp = Math.min(atk.maxHp(atkS), atk.currentHp + heal);
            int got = atk.currentHp - before;
            if (got > 0) out.add(plugin.getLang().uiFmt("battle.log.item.shell_bell", "§a{atk} 的§f贝壳之铃§a回复了 {n} 点体力！", java.util.Map.of("atk", atkName, "n", String.valueOf(got))));
        }

        
        // King's Rock / Razor Fang: 10% flinch chance on damaging moves (blocked by Substitute / Covert Cloak).
        if (didDamage && damageDone > 0 && atk != null && def != null && def.currentHp > 0) {
            if (def.substituteHp <= 0 && HeldItemEffects.canItemCauseFlinch(atk) && !"status".equalsIgnoreCase(move.category()) && !def.flinched) {
                double r = Util.RND.nextDouble();
                if (r < 0.10) {
                    if ("covert_cloak".equalsIgnoreCase(def.heldItemId)) {
                        out.add(plugin.getLang().uiFmt("battle.log.item.covert_cloak", "§7但是{def} 的§f密探斗篷§7阻止了追加畏缩！", java.util.Map.of("def", defName)));
                    } else {
					boolean inner = !AbilityEffects.ignoresDefenderAbility(atk) && AbilityEffects.has(def, "innerfocus");
					if (!inner) {
							def.flinched = true;
							out.add(plugin.getLang().uiFmt("battle.log.flinched_short", "§e{def} 畏缩了！", java.util.Map.of("def", defName)));
						} else {
							out.add(plugin.getLang().uiFmt("battle.log.ability.steadfast", "§6【不屈之心】§e{def} 没有畏缩！", java.util.Map.of("def", defName)));
						}
                    }
                }
            }
        }

        // Stench: 10% flinch chance on damaging moves (same block rules as King's Rock / secondary flinch).
        if (didDamage && damageDone > 0 && atk != null && def != null && def.currentHp > 0) {
            if (def.substituteHp <= 0
                    && AbilityEffects.has(atk, "stench")
                    && !"status".equalsIgnoreCase(move.category())
                    && !def.flinched) {
                double r = Util.RND.nextDouble();
                if (r < 0.10) {
                    if ("covert_cloak".equalsIgnoreCase(def.heldItemId)) {
                        out.add(plugin.getLang().uiFmt("battle.log.item.covert_cloak", "§7但是{def} 的§f密探斗篷§7阻止了追加畏缩！", java.util.Map.of("def", defName)));
                    } else {
                        boolean inner = !AbilityEffects.ignoresDefenderAbility(atk) && AbilityEffects.has(def, "innerfocus");
                        if (!inner) {
                            def.flinched = true;
                            out.add(plugin.getLang().uiFmt("battle.log.ability.flinch", "§6【{ability}】§e{def} 畏缩了！", java.util.Map.of("ability", plugin.getLang().abilityName("stench", "恶臭"), "def", defName)));
                        } else {
                            out.add(plugin.getLang().uiFmt("battle.log.ability.noflinch", "§6【{ability}】§e{def} 没有畏缩！", java.util.Map.of("ability", plugin.getLang().abilityName("innerfocus", "精神力"), "def", defName)));
                        }
                    }
                }
            }
        }

        if (atk != null) atk.lastMoveFailed = false;

// Type Gems: consume after boosting a damaging move.
        if (didDamage && damageDone > 0 && atk != null) {
            out.addAll(HeldItemEffects.tryConsumeGem(atk, atkName, move, damageDone));
        }


        // Wonder Guard: if a damaging move was completely blocked, none of that attack's additional effects should trigger.
        boolean suppressDirectMoveEffects = wonderGuardBlockedMove && move != null
                && move.category() != null
                && !"status".equalsIgnoreCase(move.category())
                && !didDamage;

        // Apply effects
        if (!suppressDirectMoveEffects) for (Map<String, Object> ef : effects) {
            if (ef == null || ef.isEmpty()) continue;
            String kind = asString(ef.get("kind"), asString(ef.get("id"), null));
            if (kind == null) continue;
            kind = kind.toLowerCase();

            switch (kind) {
                case "substitute" -> {
                    // Gen1 Substitute: spend 1/4 max HP to create a substitute that blocks many effects.
                    int max = atk.maxHp(atkS);
                    int cost = Math.max(1, max / 4);
                    if (atk.substituteHp > 0) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        break;
                    }
                    if (atk.currentHp <= cost) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        break;
                    }
                    atk.currentHp = Math.max(0, atk.currentHp - cost);
                    atk.substituteHp = cost;
                    out.add(plugin.getLang().uiFmt("battle.log.substitute.create", "§7{atk} 制造了替身！", java.util.Map.of("atk", atkName)));
                }
                case "recharge" -> {
                    // Gen1 Recharge: if the move didn't KO the target, user must recharge next turn.
                    if (didDamage && def.currentHp > 0) {
                        atk.rechargeTurns = 1;
                        out.add(plugin.getLang().uiFmt("battle.log.recharge_short", "§e{atk} 需要恢复精力！", java.util.Map.of("atk", atkName)));
                    }
                }
                case "disable" -> {
                    // Gen1 Disable: disable target's last used move (fallback to a random known move).
                    if (def.substituteHp > 0 && !AbilityEffects.has(atk, "infiltrator")) { out.add(plugin.getLang().ui("battle.log.substitute.block", "§7但是替身挡住了！")); break; }
                    String src = def.lastMoveId;
                    if (src == null || src.isBlank()) {
                        // fallback to random non-null move slot
                        java.util.List<MoveSlot> ms = def.effectiveMoves();
                        java.util.List<String> ids = new java.util.ArrayList<>();
                        for (MoveSlot mslot : ms) {
                            if (mslot != null && mslot.moveId != null && !mslot.moveId.isBlank()) ids.add(mslot.moveId.toLowerCase());
                        }
                        if (!ids.isEmpty()) src = ids.get(Util.RND.nextInt(ids.size()));
                    }
                    if (src == null || src.isBlank()) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    if (def != null && AbilityEffects.blocksMentalMove(def, "disable")) {
                        out.add(plugin.getLang().uiFmt("battle.log.ability.aroma_veil", "§6【芳香幕】§e{def} 不受该效果影响！", java.util.Map.of("def", defName)));
                        break;
                    }
                    def.disabledMoveId = src.toLowerCase();
                    def.disabledTurns = 1 + Util.RND.nextInt(8);
                    out.add(plugin.getLang().uiFmt("battle.log.disable.applied", "§7{def} 的招式被定身法封印了！", java.util.Map.of("def", defName)));

                    // Mental Herb: cures certain mental restrictions. We implement the most relevant one in our ruleset: Disable.
                    if (def != null && "mental_herb".equalsIgnoreCase(def.heldItemId)) {
                        def.heldItemId = null;
                        def.disabledMoveId = null;
                        def.disabledTurns = 0;
                        out.add(plugin.getLang().uiFmt("battle.log.disable.mentalherb", "§e{def} 的§f心灵香草§e发动了！定身法效果被解除！", java.util.Map.of("def", defName)));
                    }
                }
                case "partial_trap" -> {
                    // Bind/Wrap/Clamp/Fire Spin: lock target (simplified) and repeat the move for 2-5 turns.
                    if (!didDamage) break;
                    if (def.currentHp <= 0) break;

                    // Binding Band: increases damage dealt by trapping moves.
                    // Since our Gen1-style partial trap repeats the move, we model this as an additional fixed chunk.
                    if (atk != null && "binding_band".equalsIgnoreCase(atk.heldItemId) && def.currentHp > 0) {
                        int extra = Math.max(1, def.maxHp(defS) / 8);
                        def.currentHp = Math.max(0, def.currentHp - extra);
                        out.add(plugin.getLang().uiFmt("battle.log.bind.gripclaw", "§6{atk} 的§f紧缚带§6让束缚伤害提高了！ §7(-{dmg})", java.util.Map.of("atk", atkName, "dmg", String.valueOf(extra))));
                        if (def.currentHp <= 0) break;
                    }

                    // Grip Claw: trapping moves last 7 turns (modern behavior). We apply a simple fixed duration.
                    int turns;
                    if (atk != null && "grip_claw".equalsIgnoreCase(atk.heldItemId)) {
                        turns = 7;
                    } else {
                        turns = 2 + Util.RND.nextInt(4); // 2-5
                    }
                    atk.trappingMoveId = move.id();
                    atk.trappingTurnsRemaining = Math.max(0, turns - 1);
                    def.trappedTurnsRemaining = Math.max(0, turns - 1);
                    out.add(plugin.getLang().uiFmt("battle.log.bind.applied", "§e{def} 被束缚住了！", java.util.Map.of("def", defName)));
                }
                case "bide" -> {
                    if (atk.bideActive) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    atk.bideActive = true;
                    atk.bideTurnsRemaining = 2;
                    atk.bideDamageTaken = 0;
                    out.add(plugin.getLang().uiFmt("battle.log.bide.start", "§7{atk} 开始忍耐！", java.util.Map.of("atk", atkName)));
                }
                case "counter" -> {
                    // Handled earlier as a dedicated reactive move before generic damage/effect flow.
                }
                case "haze" -> {
                    atk.resetBattleStages();
                    def.resetBattleStages();
                    out.add(plugin.getLang().ui("battle.log.haze", "§7黑雾吹散了能力变化！"));
                }
                case "mist" -> {
                    atk.mistTurnsRemaining = 5;
                    out.add(plugin.getLang().uiFmt("battle.log.mist", "§7白雾保护着 {atk}！", java.util.Map.of("atk", atkName)));
                }
                case "protect" -> {
                    int streak = Math.max(0, atk.protectSuccessStreak);
                    double successChance = streak <= 0 ? 1.0 : Math.pow(1.0 / 3.0, streak);
                    if (Util.RND.nextDouble() >= successChance) {
                        atk.protectSuccessStreak = 0;
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        break;
                    }
                    atk.protectTurnsRemaining = 1;
                    atk.protectSuccessStreak = streak + 1;
                    out.add(plugin.getLang().uiFmt("battle.log.protect_use", "§7{atk} 进入了保护状态！", java.util.Map.of("atk", atkName)));
                }
                case "side_condition" -> {
                    String which = asString(ef.get("which"), "").toLowerCase(java.util.Locale.ROOT);
                    int turns = (int) asDouble(ef.get("turns"), 5);
                    if (atk != null && AbilityEffects.has(atk, "persistent") && ("safeguard".equals(which) || "tailwind".equals(which) || "aurora_veil".equals(which) || "lucky_chant".equals(which))) {
                        turns += 2;
                    }
                    if (atk != null && "light_clay".equalsIgnoreCase(atk.heldItemId) && turns == 5 && ("safeguard".equals(which) || "aurora_veil".equals(which))) {
                        turns = 8;
                    }
                    if (playerActing) {
                        if ("safeguard".equals(which)) s.playerSafeguardTurns = Math.max(s.playerSafeguardTurns, turns);
                        else if ("tailwind".equals(which)) s.playerTailwindTurns = Math.max(s.playerTailwindTurns, turns);
                        else if ("aurora_veil".equals(which)) s.playerAuroraVeilTurns = Math.max(s.playerAuroraVeilTurns, turns);
                        else if ("lucky_chant".equals(which)) s.playerLuckyChantTurns = Math.max(s.playerLuckyChantTurns, turns);
                    } else {
                        if ("safeguard".equals(which)) s.wildSafeguardTurns = Math.max(s.wildSafeguardTurns, turns);
                        else if ("tailwind".equals(which)) s.wildTailwindTurns = Math.max(s.wildTailwindTurns, turns);
                        else if ("aurora_veil".equals(which)) s.wildAuroraVeilTurns = Math.max(s.wildAuroraVeilTurns, turns);
                        else if ("lucky_chant".equals(which)) s.wildLuckyChantTurns = Math.max(s.wildLuckyChantTurns, turns);
                    }
                    String condName = switch (which) {
                        case "safeguard" -> "神秘守护";
                        case "tailwind" -> "顺风";
                        case "aurora_veil" -> "极光幕";
                        case "lucky_chant" -> "幸运咒语";
                        default -> which;
                    };
                    out.add(plugin.getLang().uiFmt("battle.log.side_condition", "§7{atk} 的队伍受到了 {cond} 的保护！", java.util.Map.of("atk", atkName, "cond", condName)));
                }
                case "wish" -> {
                    int heal = Math.max(1, atk.maxHp(atkS) / 2);
                    if (playerActing) {
                        s.playerWishTurns = Math.max(s.playerWishTurns, 1);
                        s.playerWishHeal = heal;
                    } else {
                        s.wildWishTurns = Math.max(s.wildWishTurns, 1);
                        s.wildWishHeal = heal;
                    }
                    out.add(plugin.getLang().uiFmt("battle.log.wish", "§7{atk} 许下了愿望！", java.util.Map.of("atk", atkName)));
                }
                case "ingrain" -> {
                    atk.ingrainActive = true;
                    out.add(plugin.getLang().uiFmt("battle.log.ingrain", "§7{atk} 扎根于地面！", java.util.Map.of("atk", atkName)));
                }
                case "aqua_ring" -> {
                    atk.aquaRingActive = true;
                    out.add(plugin.getLang().uiFmt("battle.log.aqua_ring", "§7水流环围绕着 {atk}！", java.util.Map.of("atk", atkName)));
                }
                case "heal_block" -> {
                    if (def != null) {
                        if (def != null && AbilityEffects.blocksMentalMove(def, "heal_block")) {
                            out.add(plugin.getLang().uiFmt("battle.log.ability.aroma_veil", "§6【芳香幕】§e{def} 不受该效果影响！", java.util.Map.of("def", defName)));
                            break;
                        }
                        def.healBlockTurns = Math.max(def.healBlockTurns, (int) asDouble(ef.get("turns"), 5));
                        out.add(plugin.getLang().uiFmt("battle.log.heal_block", "§7{def} 受到了回复封锁！", java.util.Map.of("def", defName)));
                    }
                }
                case "nightmare" -> {
                    if (def != null) {
                        String st = def.status == null ? "none" : def.status.toLowerCase(java.util.Locale.ROOT);
                        if ("sleep".equals(st)) {
                            def.nightmareTurns = Math.max(def.nightmareTurns, (int) asDouble(ef.get("turns"), 3));
                            out.add(plugin.getLang().uiFmt("battle.log.nightmare", "§8{def} 陷入了恶梦！", java.util.Map.of("def", defName)));
                        } else {
                            out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        }
                    }
                }
                case "taunt" -> {
                    if (def != null) {
                        if (def != null && AbilityEffects.blocksMentalMove(def, "taunt")) {
                            out.add(plugin.getLang().uiFmt("battle.log.ability.aroma_veil", "§6【芳香幕】§e{def} 不受该效果影响！", java.util.Map.of("def", defName)));
                            break;
                        }
                        def.tauntTurns = Math.max(def.tauntTurns, (int) asDouble(ef.get("turns"), 3));
                        out.add(plugin.getLang().uiFmt("battle.log.taunt", "§7{def} 受到了挑衅！", java.util.Map.of("def", defName)));
                    }
                }
                case "torment" -> {
                    if (def != null) {
                        if (def != null && AbilityEffects.blocksMentalMove(def, "torment")) {
                            out.add(plugin.getLang().uiFmt("battle.log.ability.aroma_veil", "§6【芳香幕】§e{def} 不受该效果影响！", java.util.Map.of("def", defName)));
                            break;
                        }
                        def.tormentTurns = Math.max(def.tormentTurns, (int) asDouble(ef.get("turns"), 3));
                        out.add(plugin.getLang().uiFmt("battle.log.torment", "§7{def} 陷入了无理取闹状态！", java.util.Map.of("def", defName)));
                    }
                }
                case "encore" -> {
                    if (def != null && def.lastMoveId != null && !def.lastMoveId.isBlank()) {
                        if (def != null && AbilityEffects.blocksMentalMove(def, "encore")) {
                            out.add(plugin.getLang().uiFmt("battle.log.ability.aroma_veil", "§6【芳香幕】§e{def} 不受该效果影响！", java.util.Map.of("def", defName)));
                            break;
                        }
                        def.encoreTurns = Math.max(def.encoreTurns, (int) asDouble(ef.get("turns"), 3));
                        def.encoreMoveId = def.lastMoveId;
                        out.add(plugin.getLang().uiFmt("battle.log.encore_apply", "§7{def} 只能继续使用同一招式了！", java.util.Map.of("def", defName)));
                    } else {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                    }
                }
                case "perish_song" -> {
                    atk.perishSongTurns = 3;
                    if (def != null) def.perishSongTurns = 3;
                    out.add(plugin.getLang().ui("battle.log.perish_song", "§5灭亡之歌响起了！"));
                }
                case "attract" -> {
                    if (def != null) {
                        def.infatuated = true;
                        out.add(plugin.getLang().uiFmt("battle.log.attract_inflict", "§d{def} 陷入了爱河！", java.util.Map.of("def", defName)));
                    }
                }
                case "cure_status_party" -> {
                    if (playerActing) {
                        PlayerProfile prof = plugin.getStorage().getProfile(s.playerId);
                        if (prof != null && prof.party != null) {
                            for (PokemonInstance p : prof.party) {
                                if (p == null) continue;
                                p.status = "none";
                                p.sleepTurns = 0;
                                p.toxicCounter = 0;
                            }
                        }
                    } else if (atk != null) {
                        atk.status = "none";
                        atk.sleepTurns = 0;
                        atk.toxicCounter = 0;
                    }
                    out.add(plugin.getLang().uiFmt("battle.log.team_cure", "§a{atk} 治愈了异常状态！", java.util.Map.of("atk", atkName)));
                }
                case "weather" -> {
                    String which = asString(ef.get("weather"), "").toLowerCase(java.util.Locale.ROOT);
                    if ("sunnyday".equals(which) || "sun".equals(which)) {
                        int turns = WeatherSystem.durationForSource(WeatherType.SUN, atk);
                        WeatherSystem.setWeather(s, WeatherType.SUN, turns);
                        out.add(plugin.getLang().uiFmt("battle.log.weather.sun", "§e阳光变得强烈了！ §7({n}回合)", java.util.Map.of("n", String.valueOf(turns))));
                    } else if ("raindance".equals(which) || "rain".equals(which)) {
                        int turns = WeatherSystem.durationForSource(WeatherType.RAIN, atk);
                        WeatherSystem.setWeather(s, WeatherType.RAIN, turns);
                        out.add(plugin.getLang().uiFmt("battle.log.weather.rain", "§b开始下雨了！ §7({n}回合)", java.util.Map.of("n", String.valueOf(turns))));
                    } else if ("sandstorm".equals(which) || "sand".equals(which)) {
                        int turns = WeatherSystem.durationForSource(WeatherType.SAND, atk);
                        WeatherSystem.setWeather(s, WeatherType.SAND, turns);
                        out.add(plugin.getLang().uiFmt("battle.log.weather.sandstorm", "§e沙暴刮起来了！ §7({n}回合)", java.util.Map.of("n", String.valueOf(turns))));
                    } else if ("hail".equals(which) || "snowscape".equals(which)) {
                        int turns = WeatherSystem.durationForSource(WeatherType.HAIL, atk);
                        WeatherSystem.setWeather(s, WeatherType.HAIL, turns);
                        out.add(plugin.getLang().uiFmt("battle.log.weather.hail", "§b开始下冰雹了！ §7({n}回合)", java.util.Map.of("n", String.valueOf(turns))));
                    }
                }
                case "self_switch" -> {
                    if (playerActing) {
                        out.addAll(forceAutoSwitch(plugin, viewer, s, plugin.getLang().uiFmt("battle.log.self_switch", "§e{atk} 返回了，准备换上下一只精灵！", java.util.Map.of("atk", atkName))));
                    } else {
                        out.add(plugin.getLang().uiFmt("battle.log.self_switch_wild", "§7{atk} 想要撤退，但野外逻辑暂未处理自动换位。", java.util.Map.of("atk", atkName)));
                    }
                }
                case "gastro_acid" -> {
                    if (def != null) {
                        def.abilitySuppressed = true;
                        out.add(plugin.getLang().uiFmt("battle.log.gastro_acid", "§7{def} 的特性被抑制了！", java.util.Map.of("def", defName)));
                    }
                }
                case "defog" -> {
                    if (playerActing) {
                        s.wildStealthRock = false;
                        s.playerStealthRock = false;
                        s.wildSpikesLayers = 0;
                        s.playerSpikesLayers = 0;
                        s.wildToxicSpikesLayers = 0;
                        s.playerToxicSpikesLayers = 0;
                        s.wildStickyWeb = false;
                        s.playerStickyWeb = false;
                        s.wildReflectTurns = 0;
                        s.wildLightScreenTurns = 0;
                        s.wildSafeguardTurns = 0;
                        s.wildTailwindTurns = 0;
                        s.wildAuroraVeilTurns = 0;
                    } else {
                        s.playerStealthRock = false;
                        s.wildStealthRock = false;
                        s.playerSpikesLayers = 0;
                        s.wildSpikesLayers = 0;
                        s.playerToxicSpikesLayers = 0;
                        s.wildToxicSpikesLayers = 0;
                        s.playerStickyWeb = false;
                        s.wildStickyWeb = false;
                        s.playerReflectTurns = 0;
                        s.playerLightScreenTurns = 0;
                        s.playerSafeguardTurns = 0;
                        s.playerTailwindTurns = 0;
                        s.playerAuroraVeilTurns = 0;
                    }
                    if (def != null) {
                        def.stageEvasion = Math.max(-6, def.stageEvasion - 1);
                    }
                    out.add(plugin.getLang().ui("battle.log.defog", "§7白雾吹散了场上的屏障与陷阱！"));
                }
                case "gravity" -> {
                    s.gravityTurns = Math.max(s.gravityTurns, (int) asDouble(ef.get("turns"), 5));
                    out.add(plugin.getLang().ui("battle.log.gravity", "§8重力变强了！"));
                }
                case "smack_down" -> {
                    if (def != null && didDamage) {
                        def.groundedBySmackDown = true;
                        out.add(plugin.getLang().uiFmt("battle.log.smack_down", "§7{def} 被击落到地面了！", java.util.Map.of("def", defName)));
                    }
                }
                case "foresight" -> {
                    if (def != null) {
                        def.identifiedTarget = true;
                        out.add(plugin.getLang().uiFmt("battle.log.foresight", "§7{def} 被识破了！", java.util.Map.of("def", defName)));
                    }
                }
                case "miracle_eye" -> {
                    if (def != null) {
                        def.miracleEyeTarget = true;
                        out.add(plugin.getLang().uiFmt("battle.log.miracle_eye", "§7{def} 被奇迹之眼看穿了！", java.util.Map.of("def", defName)));
                    }
                }
                case "heal_target" -> {
                    if (def != null && defS != null) {
                        if (def.healBlockTurns > 0) {
                            out.add(plugin.getLang().uiFmt("battle.log.heal_block.block", "§7{def} 处于回复封锁状态，无法回复体力！", java.util.Map.of("def", defName)));
                        } else {
                            double pct = asDouble(ef.get("percent"), 0.5);
                            int heal = (int) Math.max(1, def.maxHp(defS) * pct);
                            def.currentHp = Math.min(def.maxHp(defS), def.currentHp + heal);
                            out.add(plugin.getLang().uiFmt("battle.log.heal_target", "§a{def} 回复了体力！ §7({hp}/{max})", java.util.Map.of("def", defName, "hp", String.valueOf(def.currentHp), "max", String.valueOf(def.maxHp(defS)))));
                        }
                    }
                }
                case "charge" -> {
                    atk.chargeActive = true;
                    atk.applyStage("spd", 1);
                    out.add(plugin.getLang().uiFmt("battle.log.charge", "§b{atk} 开始蓄电了！特防提升了！", java.util.Map.of("atk", atkName)));
                }
                case "stockpile" -> {
                    if (atk.stockpileCount >= 3) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        break;
                    }
                    atk.stockpileCount++;
                    atk.applyStage("def", 1);
                    atk.applyStage("spd", 1);
                    out.add(plugin.getLang().uiFmt("battle.log.stockpile", "§7{atk} 蓄力了！(层数 {n})", java.util.Map.of("atk", atkName, "n", String.valueOf(atk.stockpileCount))));
                }
                case "spit_up" -> {
                    if (atk.stockpileCount <= 0) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        break;
                    }
                    int count = Math.max(1, atk.stockpileCount);
                    Move powered = new Move(move.id(), move.name(), move.type(), move.category(), 100 * count, move.accuracy(), move.pp(), move.priority(), move.num(), move.effect(), move.effects());
                    int dmg = Damage.calcDamage(atk, def, atkS, defS, powered);
                    dmg = applyDamageToTarget(def, defS, dmg, out, defName, false);
                    out.add(plugin.getLang().uiFmt("battle.log.deal_damage", "§f造成了 §c{dmg}§f 点伤害！ §7({hp}/{max})", java.util.Map.of("dmg", String.valueOf(dmg), "hp", String.valueOf(def.currentHp), "max", String.valueOf(def.maxHp(defS)))));
                    atk.stageDef = Math.max(-6, atk.stageDef - count);
                    atk.stageSpd = Math.max(-6, atk.stageSpd - count);
                    atk.stockpileCount = 0;
                }
                case "swallow" -> {
                    if (atk.stockpileCount <= 0) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        break;
                    }
                    int max = atk.maxHp(atkS);
                    int heal = atk.stockpileCount >= 3 ? max : (atk.stockpileCount == 2 ? Math.max(1, max / 2) : Math.max(1, max / 4));
                    int before = atk.currentHp;
                    atk.currentHp = Math.min(max, atk.currentHp + heal);
                    atk.stageDef = Math.max(-6, atk.stageDef - atk.stockpileCount);
                    atk.stageSpd = Math.max(-6, atk.stageSpd - atk.stockpileCount);
                    atk.stockpileCount = 0;
                    if (atk.currentHp > before) out.add(plugin.getLang().uiFmt("battle.log.heal_hp", "§a{mon} 回复了 §c{n}§a 点体力！", java.util.Map.of("mon", atkName, "n", String.valueOf(atk.currentHp - before))));
                }
                case "imprison" -> {
                    atk.imprisonActive = true;
                    out.add(plugin.getLang().uiFmt("battle.log.imprison", "§7{atk} 封印了对手的招式！", java.util.Map.of("atk", atkName)));
                }
                case "healing_wish" -> {
                    if (playerActing) s.playerHealingWishPending = true; else s.wildHealingWishPending = true;
                    atk.currentHp = 0;
                    out.add(plugin.getLang().uiFmt("battle.log.healing_wish", "§d{atk} 献出了自己，留下了治愈之愿！", java.util.Map.of("atk", atkName)));
                }
                case "revival_blessing" -> {
                    int revived = reviveFirstFaintedPartyMember(plugin, s, playerActing);
                    if (revived <= 0) out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                    else out.add(plugin.getLang().ui("battle.log.revival_blessing", "§d倒下的同伴恢复了战斗能力！"));
                }
                case "embargo" -> {
                    if (def != null) {
                        def.embargoTurns = Math.max(def.embargoTurns, (int) asDouble(ef.get("turns"), 5));
                        out.add(plugin.getLang().uiFmt("battle.log.embargo", "§7{def} 被禁止使用道具了！", java.util.Map.of("def", defName)));
                    }
                }
                case "laser_focus" -> {
                    atk.laserFocusTurns = Math.max(atk.laserFocusTurns, (int) asDouble(ef.get("turns"), 1));
                    out.add(plugin.getLang().uiFmt("battle.log.laser_focus", "§7{atk} 瞄准了要害！", java.util.Map.of("atk", atkName)));
                }
                case "pain_split" -> {
                    if (def == null || atkS == null || defS == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    int avg = Math.max(1, (atk.currentHp + def.currentHp) / 2);
                    atk.currentHp = Math.min(atk.maxHp(atkS), avg);
                    def.currentHp = Math.min(def.maxHp(defS), avg);
                    out.add(plugin.getLang().ui("battle.log.pain_split", "§7双方平分了体力！"));
                }
                case "guard_split" -> {
                    if (def == null || atkS == null || defS == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    int aDef = atk.calcStat(atkS, "def", atk.ivDef, atk.evDef, false);
                    int dDef = def.calcStat(defS, "def", def.ivDef, def.evDef, false);
                    int aSpd = atk.calcStat(atkS, "spd", atk.ivSpd, atk.evSpd, false);
                    int dSpd = def.calcStat(defS, "spd", def.ivSpd, def.evSpd, false);
                    atk.stageDef = def.stageDef = 0;
                    atk.stageSpd = def.stageSpd = 0;
                    out.add(plugin.getLang().ui("battle.log.guard_split", "§7双方平分了防御能力！"));
                }
                case "power_split" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    atk.stageAtk = def.stageAtk = (atk.stageAtk + def.stageAtk) / 2;
                    atk.stageSpa = def.stageSpa = (atk.stageSpa + def.stageSpa) / 2;
                    out.add(plugin.getLang().ui("battle.log.power_split", "§7双方平分了攻击能力！"));
                }
                case "speed_swap" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    int t = atk.stageSpe; atk.stageSpe = def.stageSpe; def.stageSpe = t;
                    out.add(plugin.getLang().uiFmt("battle.log.speed_swap", "§7{atk} 与 {def} 交换了速度变化！", java.util.Map.of("atk", atkName, "def", defName)));
                }
                case "power_trick" -> {
                    atk.powerTrickActive = !atk.powerTrickActive;
                    out.add(plugin.getLang().uiFmt("battle.log.power_trick", "§7{atk} 交换了攻击与防御！", java.util.Map.of("atk", atkName)));
                }
                case "psycho_shift" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    String st = atk.status == null ? "none" : atk.status.toLowerCase(java.util.Locale.ROOT);
                    if (st.isBlank() || "none".equals(st) || (def.status != null && !"none".equalsIgnoreCase(def.status))) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    def.status = atk.status;
                    def.sleepTurns = atk.sleepTurns;
                    atk.status = "none";
                    atk.sleepTurns = 0;
                    atk.toxicCounter = 0;
                    out.add(plugin.getLang().uiFmt("battle.log.psycho_shift", "§7{atk} 把异常状态转移给了 {def}！", java.util.Map.of("atk", atkName, "def", defName)));
                }
                case "swap_items" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    String tmp = atk.heldItemId; atk.heldItemId = def.heldItemId; def.heldItemId = tmp;
                    out.add(plugin.getLang().uiFmt("battle.log.trick", "§7{atk} 与 {def} 交换了道具！", java.util.Map.of("atk", atkName, "def", defName)));
                }
                case "bestow" -> {
                    if (def == null || atk.heldItemId == null || atk.heldItemId.isBlank() || (def.heldItemId != null && !def.heldItemId.isBlank())) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    def.heldItemId = atk.heldItemId; atk.heldItemId = null;
                    out.add(plugin.getLang().uiFmt("battle.log.bestow", "§7{atk} 把道具交给了 {def}！", java.util.Map.of("atk", atkName, "def", defName)));
                }
                case "recycle" -> {
                    if (atk.heldItemId != null && !atk.heldItemId.isBlank()) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    String rec = atk.lastConsumedBerryId;
                    if (rec == null || rec.isBlank()) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    atk.heldItemId = rec;
                    out.add(plugin.getLang().uiFmt("battle.log.recycle", "§7{atk} 回收了道具！", java.util.Map.of("atk", atkName)));
                }
                case "powder" -> {
                    if (def != null) {
                        def.powdered = true;
                        out.add(plugin.getLang().uiFmt("battle.log.powder.apply", "§7{def} 被洒上了粉尘！", java.util.Map.of("def", defName)));
                    }
                }
                case "electrify" -> {
                    if (def != null) {
                        def.electrifiedThisTurn = true;
                        out.add(plugin.getLang().uiFmt("battle.log.electrify", "§7{def} 的招式被电气化了！", java.util.Map.of("def", defName)));
                    }
                }
                case "ion_deluge" -> {
                    s.ionDelugeActive = true;
                    out.add(plugin.getLang().ui("battle.log.ion_deluge", "§7离子洪流弥漫了战场！"));
                }
                case "tar_shot" -> {
                    if (def != null) {
                        def.tarShotActive = true;
                        def.applyStage("spe", -1);
                        out.add(plugin.getLang().uiFmt("battle.log.tar_shot", "§7{def} 被焦油缠住了！", java.util.Map.of("def", defName)));
                    }
                }
                case "octolock" -> {
                    if (def != null) {
                        def.trappedTurnsRemaining = Math.max(def.trappedTurnsRemaining, 99);
                        def.applyStage("def", -1);
                        def.applyStage("spd", -1);
                        out.add(plugin.getLang().uiFmt("battle.log.octolock", "§7{def} 被死死缠住了！", java.util.Map.of("def", defName)));
                    }
                }
                case "no_retreat" -> {
                    if (atk.noRetreatActive) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    atk.noRetreatActive = true;
                    atk.applyStage("atk", 1); atk.applyStage("def", 1); atk.applyStage("spa", 1); atk.applyStage("spd", 1); atk.applyStage("spe", 1);
                    out.add(plugin.getLang().uiFmt("battle.log.no_retreat", "§7{atk} 破釜沉舟了！", java.util.Map.of("atk", atkName)));
                }
                case "remove_item" -> {
                    if (def == null || def.heldItemId == null || def.heldItemId.isBlank()) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    def.lastConsumedBerryId = def.heldItemId;
                    def.heldItemId = null;
                    out.add(plugin.getLang().uiFmt("battle.log.remove_item", "§7{def} 的道具被化掉了！", java.util.Map.of("def", defName)));
                }
                case "jaw_lock" -> {
                    if (def != null) {
                        atk.trappedTurnsRemaining = Math.max(atk.trappedTurnsRemaining, 4);
                        def.trappedTurnsRemaining = Math.max(def.trappedTurnsRemaining, 4);
                        out.add(plugin.getLang().uiFmt("battle.log.jaw_lock", "§7{atk} 与 {def} 被死死咬住，无法交换！", java.util.Map.of("atk", atkName, "def", defName)));
                    }
                }
                case "throat_chop" -> {
                    if (def != null) {
                        def.throatChopTurns = Math.max(def.throatChopTurns, (int) asDouble(ef.get("turns"), 2));
                        out.add(plugin.getLang().uiFmt("battle.log.throat_chop", "§7{def} 的喉咙被封住了！", java.util.Map.of("def", defName)));
                    }
                }
                case "burn_up" -> {
                    java.util.List<String> cur = currentTypes(atk, atkS);
                    if (!cur.stream().anyMatch(t -> "fire".equalsIgnoreCase(t))) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    cur.removeIf(t -> "fire".equalsIgnoreCase(t));
                    atk.overrideType1 = cur.isEmpty() ? "normal" : cur.get(0);
                    atk.overrideType2 = cur.size() >= 2 ? cur.get(1) : null;
                    out.add(plugin.getLang().uiFmt("battle.log.burn_up", "§7{atk} 烧尽了自己的火焰属性！", java.util.Map.of("atk", atkName)));
                }
                case "double_shock" -> {
                    java.util.List<String> cur = currentTypes(atk, atkS);
                    if (!cur.stream().anyMatch(t -> "electric".equalsIgnoreCase(t))) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    cur.removeIf(t -> "electric".equalsIgnoreCase(t));
                    atk.overrideType1 = cur.isEmpty() ? "normal" : cur.get(0);
                    atk.overrideType2 = cur.size() >= 2 ? cur.get(1) : null;
                    out.add(plugin.getLang().uiFmt("battle.log.double_shock", "§7{atk} 失去了自己的电属性！", java.util.Map.of("atk", atkName)));
                }
                case "magnet_rise" -> {
                    atk.magnetRiseTurns = Math.max(atk.magnetRiseTurns, (int) asDouble(ef.get("turns"), 5));
                    out.add(plugin.getLang().uiFmt("battle.log.magnet_rise", "§7{atk} 飘浮起来了！", java.util.Map.of("atk", atkName)));
                }
                case "telekinesis" -> {
                    if (def != null) {
                        def.telekinesisTurns = Math.max(def.telekinesisTurns, (int) asDouble(ef.get("turns"), 3));
                        out.add(plugin.getLang().uiFmt("battle.log.telekinesis", "§7{def} 被念力托了起来！", java.util.Map.of("def", defName)));
                    }
                }
                case "salt_cure" -> {
                    if (def != null && didDamage) {
                        def.saltCureTurns = Math.max(def.saltCureTurns, (int) asDouble(ef.get("turns"), 4));
                        out.add(plugin.getLang().uiFmt("battle.log.salt_cure", "§6{def} 被盐腌住了！", java.util.Map.of("def", defName)));
                    }
                }
                case "destiny_bond" -> {
                    atk.destinyBondActive = true;
                    out.add(plugin.getLang().uiFmt("battle.log.destiny_bond", "§7{atk} 正等待与对手同归于尽！", java.util.Map.of("atk", atkName)));
                }
                case "shed_tail" -> {
                    int maxHp = Math.max(1, atk.maxHp(atkS));
                    int cost = Math.max(1, maxHp / 2);
                    if (atk.currentHp <= cost) {
                        out.add(plugin.getLang().uiFmt("battle.log.fail", "§7{atk} 体力不足，招式失败了！", java.util.Map.of("atk", atkName)));
                        break;
                    }
                    if (atk.substituteHp > 0) {
                        out.add(plugin.getLang().uiFmt("battle.log.fail", "§7{atk} 已经有替身了！", java.util.Map.of("atk", atkName)));
                        break;
                    }
                    atk.currentHp = Math.max(1, atk.currentHp - cost);
                    atk.substituteHp = cost;
                    out.add(plugin.getLang().uiFmt("battle.log.substitute", "§7{atk} 制造了一个替身！", java.util.Map.of("atk", atkName)));
                    if (playerActing) out.addAll(forceAutoSwitch(plugin, viewer, s, plugin.getLang().uiFmt("battle.log.self_switch", "§e{atk} 返回了，准备换上下一只精灵！", java.util.Map.of("atk", atkName))));
                }

                case "psych_up" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    atk.stageAtk = def.stageAtk;
                    atk.stageDef = def.stageDef;
                    atk.stageSpa = def.stageSpa;
                    atk.stageSpd = def.stageSpd;
                    atk.stageSpe = def.stageSpe;
                    atk.stageAccuracy = def.stageAccuracy;
                    atk.stageEvasion = def.stageEvasion;
                    out.add(plugin.getLang().uiFmt("battle.log.psych_up", "§7{atk} 复制了 {def} 的能力变化！", java.util.Map.of("atk", atkName, "def", defName)));
                }
                case "heart_swap" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    int t;
                    t = atk.stageAtk; atk.stageAtk = def.stageAtk; def.stageAtk = t;
                    t = atk.stageDef; atk.stageDef = def.stageDef; def.stageDef = t;
                    t = atk.stageSpa; atk.stageSpa = def.stageSpa; def.stageSpa = t;
                    t = atk.stageSpd; atk.stageSpd = def.stageSpd; def.stageSpd = t;
                    t = atk.stageSpe; atk.stageSpe = def.stageSpe; def.stageSpe = t;
                    t = atk.stageAccuracy; atk.stageAccuracy = def.stageAccuracy; def.stageAccuracy = t;
                    t = atk.stageEvasion; atk.stageEvasion = def.stageEvasion; def.stageEvasion = t;
                    out.add(plugin.getLang().uiFmt("battle.log.heart_swap", "§7{atk} 与 {def} 交换了能力变化！", java.util.Map.of("atk", atkName, "def", defName)));
                }
                case "power_swap" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    int t = atk.stageAtk; atk.stageAtk = def.stageAtk; def.stageAtk = t;
                    t = atk.stageSpa; atk.stageSpa = def.stageSpa; def.stageSpa = t;
                    out.add(plugin.getLang().uiFmt("battle.log.power_swap", "§7{atk} 与 {def} 交换了攻击能力变化！", java.util.Map.of("atk", atkName, "def", defName)));
                }
                case "guard_swap" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    int t = atk.stageDef; atk.stageDef = def.stageDef; def.stageDef = t;
                    t = atk.stageSpd; atk.stageSpd = def.stageSpd; def.stageSpd = t;
                    out.add(plugin.getLang().uiFmt("battle.log.guard_swap", "§7{atk} 与 {def} 交换了防御能力变化！", java.util.Map.of("atk", atkName, "def", defName)));
                }
                case "topsy_turvy" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    def.stageAtk = -def.stageAtk;
                    def.stageDef = -def.stageDef;
                    def.stageSpa = -def.stageSpa;
                    def.stageSpd = -def.stageSpd;
                    def.stageSpe = -def.stageSpe;
                    def.stageAccuracy = -def.stageAccuracy;
                    def.stageEvasion = -def.stageEvasion;
                    out.add(plugin.getLang().uiFmt("battle.log.topsy_turvy", "§7{def} 的能力变化被彻底颠倒了！", java.util.Map.of("def", defName)));
                }
                case "type_change" -> {
                    PokemonInstance target = "self".equalsIgnoreCase(asString(ef.get("target"), "target")) ? atk : def;
                    String t1 = asString(ef.get("type1"), "normal").toLowerCase(java.util.Locale.ROOT);
                    if (target == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    target.overrideType1 = t1;
                    target.overrideType2 = null;
                    String who = target == atk ? atkName : defName;
                    out.add(plugin.getLang().uiFmt("battle.log.type_change", "§7{who} 的属性变成了 {type}！", java.util.Map.of("who", who, "type", plugin.getLang().typeName(t1))));
                }
                case "type_add" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    String add = asString(ef.get("type"), "normal").toLowerCase(java.util.Locale.ROOT);
                    java.util.List<String> cur = currentTypes(def, defS);
                    if (cur.contains(add)) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    def.overrideType1 = cur.isEmpty() ? add : cur.get(0);
                    def.overrideType2 = add;
                    out.add(plugin.getLang().uiFmt("battle.log.type_add", "§7{def} 被附加了 {type} 属性！", java.util.Map.of("def", defName, "type", plugin.getLang().typeName(add))));
                }
                case "reflect_type" -> {
                    if (def == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    java.util.List<String> cur = currentTypes(def, defS);
                    if (cur.isEmpty()) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    atk.overrideType1 = cur.get(0);
                    atk.overrideType2 = cur.size() >= 2 ? cur.get(1) : null;
                    out.add(plugin.getLang().uiFmt("battle.log.reflect_type", "§7{atk} 复制了 {def} 的属性！", java.util.Map.of("atk", atkName, "def", defName)));
                }
                case "conversion2" -> {
                    String srcMoveId = def == null ? null : def.lastMoveId;
                    Move srcMove = (srcMoveId == null || srcMoveId.isBlank()) ? null : plugin.getDex().getMoveOrPlaceholder(srcMoveId);
                    String incomingType = srcMove == null ? null : HeldItemEffects.overrideMoveType(def, defS, srcMove);
                    java.util.List<String> candidates = java.util.List.of("normal","fire","water","electric","grass","ice","fighting","poison","ground","flying","psychic","bug","rock","ghost","dragon","dark","steel","fairy");
                    String chosen = null;
                    if (incomingType != null && !incomingType.isBlank()) {
                        for (String cand : candidates) {
                            if (TypeChart.effectiveness(incomingType, java.util.List.of(cand)) < 1.0) { chosen = cand; break; }
                        }
                    }
                    if (chosen == null) { out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。")); break; }
                    atk.overrideType1 = chosen;
                    atk.overrideType2 = null;
                    out.add(plugin.getLang().uiFmt("battle.log.conversion2", "§7{atk} 的属性变成了更有利的 {type}！", java.util.Map.of("atk", atkName, "type", plugin.getLang().typeName(chosen))));
                }

                case "room" -> {
                    String which = asString(ef.get("which"), "").toLowerCase(java.util.Locale.ROOT);
                    int turns = (int) asDouble(ef.get("turns"), 5);
                    if ("trick_room".equals(which)) {
                        s.trickRoomTurns = Math.max(s.trickRoomTurns, turns);
                        out.add(plugin.getLang().ui("battle.log.trick_room", "§7戏法空间展开了！"));
                    } else if ("magic_room".equals(which)) {
                        s.magicRoomTurns = Math.max(s.magicRoomTurns, turns);
                        out.add(plugin.getLang().ui("battle.log.magic_room", "§7魔法空间展开了！"));
                    } else if ("wonder_room".equals(which)) {
                        s.wonderRoomTurns = Math.max(s.wonderRoomTurns, turns);
                        out.add(plugin.getLang().ui("battle.log.wonder_room", "§7奇妙空间展开了！"));
                    } else {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                    }
                }
                case "conversion" -> {
                    // Gen1 Conversion: change user type to the type of its first move (Showdown/original behavior).
                    MoveSlot slot0 = (atk.moves != null && !atk.moves.isEmpty()) ? atk.moves.get(0) : null;
                    if (slot0 == null || slot0.moveId == null) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        break;
                    }
                    Move ref = plugin.getDex().getMoveOrPlaceholder(slot0.moveId);
                    if (ref == null || ref.type() == null) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        break;
                    }
                    atk.overrideType1 = ref.type().toLowerCase();
                    atk.overrideType2 = null;
                    out.add(plugin.getLang().ui("battle.log.conversion", "§7属性改变了。"));
                }
                case "heal" -> {
                    double pct = asDouble(ef.get("percent"), 0.5);
                    String mid = move == null || move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT).replace("_", "");
                    BattleSession ctxHeal = s;
                    WeatherType ew = ctxHeal == null ? WeatherType.NONE : WeatherSystem.effectiveWeather(ctxHeal);
                    if ("moonlight".equals(mid) || "morningsun".equals(mid) || "synthesis".equals(mid)) {
                        pct = (ew == WeatherType.SUN) ? (2.0 / 3.0)
                                : ((ew == WeatherType.RAIN || ew == WeatherType.SAND || ew == WeatherType.HAIL) ? 0.25 : 0.5);
                    } else if ("shoreup".equals(mid)) {
                        pct = (ew == WeatherType.SAND) ? (2.0 / 3.0) : 0.5;
                    }
                    int heal = (int) Math.max(1, atk.maxHp(atkS) * pct);
                    atk.currentHp = Math.min(atk.maxHp(atkS), atk.currentHp + heal);
                    if (move != null && move.id() != null && ("roost".equalsIgnoreCase(move.id()) || "roost".equalsIgnoreCase(move.id().replace("_", "")))) {
                        java.util.List<String> ts = atkS == null ? java.util.List.of() : atkS.types();
                        boolean hasFlying = false;
                        for (String t : ts) if ("flying".equalsIgnoreCase(t)) { hasFlying = true; break; }
                        if (hasFlying) atk.roostSuppressFlying = true;
                    }
                    out.add(plugin.getLang().uiFmt("battle.log.heal.generic", "§a回复了体力！ §7({hp}/{max})", java.util.Map.of("hp", String.valueOf(atk.currentHp), "max", String.valueOf(atk.maxHp(atkS)))));
                }
                case "drain" -> {
                    if (!didDamage) break;
                    double pct = asDouble(ef.get("percent"), 0.5);
                    double mul = 1.0;
                    // Big Root: boosts HP restored from draining moves by 30%.
                    if (atk != null && "big_root".equalsIgnoreCase(atk.heldItemId)) {
                        mul = 1.3;
                    }
                    int heal = (int) Math.max(1, Math.floor(damageDone * pct * mul));
                    // Liquid Ooze: draining hurts the attacker instead of healing.
                    if (def != null && AbilityEffects.has(def, "liquidooze")) {
                        int before = atk.currentHp;
                        atk.currentHp = Math.max(0, atk.currentHp - heal);
                        out.add(plugin.getLang().uiFmt("battle.log.ability.liquidooze", "§6【{ability}】§c{atk} 因吸取受到了伤害！ §7(-{dmg})", java.util.Map.of("ability", plugin.getLang().abilityName("liquidooze", "污泥浆"), "atk", atkName, "dmg", String.valueOf(before - atk.currentHp))));
                    } else {
                        atk.currentHp = Math.min(atk.maxHp(atkS), atk.currentHp + heal);
                        out.add(plugin.getLang().uiFmt("battle.log.drain.heal", "§a吸取回复了体力！ §7(+{heal})", java.util.Map.of("heal", String.valueOf(heal))));
                    }
                }
                case "set_status" -> {
                    String status = asString(ef.get("status"), null);
                    double chance = asDouble(ef.get("chance"), 1.0);
                    int fixedTurns = (int) asDouble(ef.get("turns"), 0);
                    boolean target = !"self".equalsIgnoreCase(asString(ef.get("target"), "target"));
                    PokemonInstance who = target ? def : atk;
                    String whoName = target ? defName : atkName;

                    // Substitute blocks most opponent-targeted status moves.
                    if (target && who.substituteHp > 0 && !AbilityEffects.has(atk, "infiltrator")) {
                        out.add(plugin.getLang().ui("battle.log.substitute.block", "§7但是替身挡住了！"));
                        break;
                    }

                    // Substitute blocks most opponent-targeted status moves
                    if (target && def.substituteHp > 0 && !AbilityEffects.has(atk, "infiltrator")) {
                        out.add(plugin.getLang().ui("battle.log.substitute.block", "§7但是替身挡住了！"));
                        break;
                    }

                    if (status == null) break;
                    boolean safeguarded = target && ((playerActing ? s.wildSafeguardTurns > 0 : s.playerSafeguardTurns > 0));
                    if (safeguarded) {
                        out.add(plugin.getLang().ui("battle.log.safeguard", "§7神秘守护保护了目标！"));
                        break;
                    }
                    if (!"none".equalsIgnoreCase(who.status)) break;

                    // Covert Cloak: blocks secondary status effects from damaging moves.
                    if (target && "covert_cloak".equalsIgnoreCase(who.heldItemId) && !"status".equalsIgnoreCase(move.category())) {
                        out.add(plugin.getLang().uiFmt("battle.log.item.covertcloak", "§7但是{who} 的§f密探斗篷§7阻止了追加效果！", java.util.Map.of("who", whoName)));
                        break;
                    }
                    // Shield Dust: blocks secondary effects from damaging moves.
                    if (target && shieldDustBlocks && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        out.add(plugin.getLang().uiFmt("battle.log.ability.shielddust", "§6【{ability}】§e{who} 不会受到追加效果影响！", java.util.Map.of("ability", plugin.getLang().abilityName("shielddust", "鳞粉"), "who", whoName)));
                        break;
                    }
                    // Sheer Force removes secondary effects entirely.
                    if (target && sheerForceActive && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        break;
                    }
                    // Serene Grace: double chance for secondary effects.
                    if (target && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        chance = Math.min(1.0, chance * secondaryChanceMul);
                    }
                    if (shieldDustBlocks && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        out.add(plugin.getLang().uiFmt("battle.log.ability.shielddust_noflinch", "§6【{ability}】§e{def} 不会畏缩！（追加效果无效）", java.util.Map.of("ability", plugin.getLang().abilityName("shielddust", "鳞粉"), "def", defName)));
                        break;
                    }
                    if (sheerForceActive && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        break;
                    }
                    if (chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        chance = Math.min(1.0, chance * secondaryChanceMul);
                    }
                    if (Util.RND.nextDouble() > chance) break;

                    status = status.toLowerCase();
                    // Leaf Guard: in sun, prevents major status.
                    if (target && AbilityEffects.blocksMajorStatus(who, s) && !AbilityEffects.ignoresDefenderAbility(atk)) {
                        out.add(plugin.getLang().uiFmt("battle.log.ability.leafguard", "§6【{ability}】§e{who} 在阳光下不会陷入异常状态！", java.util.Map.of("ability", plugin.getLang().abilityName("leafguard", "叶绿守护"), "who", whoName)));
                        break;
                    }
                    // Ability-based status immunities
                    if (target && AbilityEffects.isStatusImmune(who, status)) {
                        out.add(plugin.getLang().uiFmt("battle.log.status.immune", "§6【特性】§e{who} 对该异常状态免疫！", java.util.Map.of("who", whoName)));
                        break;
                    }
                    Species whoS = target ? defS : atkS;
                    if (s != null && s.terrainTurns > 0 && who != null && whoS != null && isGroundedForTerrain(who, whoS, s)) {
                        String terr = s.terrain == null ? "" : s.terrain.toLowerCase(java.util.Locale.ROOT);
                        if ("electric".equals(terr) && "sleep".equals(status)) {
                            out.add(plugin.getLang().uiFmt("battle.log.terrain.sleep_block", "§7{who} 受到电气场地保护，无法陷入睡眠！", java.util.Map.of("who", whoName)));
                            break;
                        }
                        if ("misty".equals(terr)) {
                            out.add(plugin.getLang().uiFmt("battle.log.terrain.status_block", "§7{who} 受到薄雾场地保护，异常状态被挡住了！", java.util.Map.of("who", whoName)));
                            break;
                        }
                    }
                    boolean statusSet = false;
                    if ("sleep".equals(status)) {
                        who.status = "sleep";
                        who.sleepTurns = fixedTurns > 0 ? fixedTurns : (2 + Util.RND.nextInt(3));
                        out.add(plugin.getLang().uiFmt("battle.log.status.sleep", "§b{who} 睡着了！", java.util.Map.of("who", whoName)));
                        statusSet = true;
                    } else if ("poison".equals(status)) {
                        boolean blockedByType = false;
                        try {
                            java.util.List<String> tps = whoS == null ? java.util.List.of() : whoS.types();
                            blockedByType = tps.stream().anyMatch(t -> "poison".equalsIgnoreCase(t) || "steel".equalsIgnoreCase(t));
                        } catch (Throwable ignored) {}
                        if (blockedByType && !AbilityEffects.allowsCorrosion(target ? atk : def)) {
                            out.add(plugin.getLang().uiFmt("battle.log.status.immune", "§7{who} 免疫了中毒！", java.util.Map.of("who", whoName)));
                            break;
                        }
                        who.status = "poison";
                        out.add(plugin.getLang().uiFmt("battle.log.status.poison", "§a{who} 中毒了！", java.util.Map.of("who", whoName)));
                        statusSet = true;
                    } else if ("toxic".equals(status)) {
                        boolean blockedByType = false;
                        try {
                            java.util.List<String> tps = whoS == null ? java.util.List.of() : whoS.types();
                            blockedByType = tps.stream().anyMatch(t -> "poison".equalsIgnoreCase(t) || "steel".equalsIgnoreCase(t));
                        } catch (Throwable ignored) {}
                        if (blockedByType && !AbilityEffects.allowsCorrosion(target ? atk : def)) {
                            out.add(plugin.getLang().uiFmt("battle.log.status.immune", "§7{who} 免疫了中毒！", java.util.Map.of("who", whoName)));
                            break;
                        }
                        who.status = "toxic";
                        who.toxicCounter = 1;
            out.add(plugin.getLang().uiFmt("battle.status.toxic", "§a{mon} 中了剧毒！", java.util.Map.of("mon", whoName)));
                        statusSet = true;
                    } else if ("burn".equals(status)) {
                        who.status = "burn";
            out.add(plugin.getLang().uiFmt("battle.status.burn", "§c{mon} 被灼伤了！", java.util.Map.of("mon", defName)));
                        statusSet = true;
                    } else if ("paralyze".equals(status)) {
                        who.status = "paralyze";
            out.add(plugin.getLang().uiFmt("battle.status.paralysis", "§e{mon} 麻痹了！", java.util.Map.of("mon", defName)));
                        statusSet = true;
                    } else if ("freeze".equals(status)) {
                        who.status = "freeze";
                        out.add(plugin.getLang().uiFmt("battle.log.status.freeze", "§b{who} 冻结了！", java.util.Map.of("who", whoName)));
                        statusSet = true;
                    }

                    if (statusSet) {
                        // Ability reactions (e.g., Synchronize)
                        out.addAll(AbilityEffects.onStatusApplied(who, whoS, whoName,
                                target ? atk : def, target ? atkS : defS, target ? atkName : defName,
                                status));
                        out.addAll(HeldItemEffects.onStatusApplied(who, whoS, whoName));
                    }
                }
                case "screen" -> {
                    String which = asString(ef.get("which"), null);
                    int turns = (int) asDouble(ef.get("turns"), 5);
                    if (which == null) break;
                    which = which.toLowerCase();

                    // Light Clay: extends Reflect/Light Screen duration from 5 to 8 turns.
                    if (atk != null && "light_clay".equalsIgnoreCase(atk.heldItemId) && turns == 5) {
                        turns = 8;
                    }
                    if ("reflect".equals(which)) {
                        if (playerActing) s.playerReflectTurns = turns;
                        else s.wildReflectTurns = turns;
                        out.add(plugin.getLang().uiFmt("battle.log.reflect", "§b反射壁张开了！ §7({turns}回合)", java.util.Map.of("turns", String.valueOf(turns))));
                    }
                    if ("light_screen".equals(which) || "lightscreen".equals(which)) {
                        if (playerActing) s.playerLightScreenTurns = turns;
                        else s.wildLightScreenTurns = turns;
                        out.add(plugin.getLang().uiFmt("battle.log.lightscreen", "§b光墙张开了！ §7({turns}回合)", java.util.Map.of("turns", String.valueOf(turns))));
                    }
                }
                case "leech_seed" -> {
                    if (def.substituteHp > 0 && !AbilityEffects.has(atk, "infiltrator")) { out.add(plugin.getLang().ui("battle.log.substitute.block", "§7但是替身挡住了！")); break; }
                    if (def.leechSeeded) break;
                    def.leechSeeded = true;
                    def.leechSeedByPlayer = playerActing;
                    out.add(plugin.getLang().ui("battle.log.leechseed", "§a种子寄生在了对手身上！"));
                }
                case "stat_stage" -> {
                    String stat = asString(ef.get("stat"), null);
                    int stages = (int) asDouble(ef.get("stages"), 1);
                    double chance = asDouble(ef.get("chance"), 1.0);
                    boolean target = !"self".equalsIgnoreCase(asString(ef.get("target"), "target"));
                    PokemonInstance who = target ? def : atk;
                    String whoName = target ? defName : atkName;
                    if (stat == null) break;
                    stat = stat.toLowerCase();

                    // Clear Amulet prevents opponent lowering stats (Gen9 item; we support the core prevention).
                    if (target && stages < 0 && "clear_amulet".equalsIgnoreCase(who.heldItemId)) {
                        out.add(plugin.getLang().uiFmt("battle.log.item.clearamulet", "§7但是{who} 的§f清净坠饰§7阻止了能力被降低！", java.util.Map.of("who", whoName)));
                        break;
                    }

                    // Mist prevents opponent lowering stats
                    if (target && stages < 0 && who.mistTurnsRemaining > 0) {
                        out.add(plugin.getLang().uiFmt("battle.log.mist_protect", "§7但是白雾保护着 {who}！", java.util.Map.of("who", whoName)));
                        break;
                    }
                    // Substitute blocks most opponent-targeted stat changes
                    if (target && who.substituteHp > 0 && !AbilityEffects.has(atk, "infiltrator")) {
                        out.add(plugin.getLang().ui("battle.log.substitute.block", "§7但是替身挡住了！"));
                        break;
                    }

                    // Covert Cloak: blocks secondary stat drops from damaging moves.
                    // We approximate "secondary" as having chance < 1 on a non-status move.
                    if (target && stages < 0 && chance < 0.999 && "covert_cloak".equalsIgnoreCase(who.heldItemId)
                            && !"status".equalsIgnoreCase(move.category())) {
                        out.add(plugin.getLang().uiFmt("battle.log.item.covertcloak", "§7但是{who} 的§f密探斗篷§7阻止了追加效果！", java.util.Map.of("who", whoName)));
                        break;
                    }
                    // Shield Dust blocks secondary stat drops.
                    if (target && stages < 0 && shieldDustBlocks && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        out.add(plugin.getLang().uiFmt("battle.log.ability.shielddust", "§6【{ability}】§e{who} 不会受到追加效果影响！", java.util.Map.of("ability", plugin.getLang().abilityName("shielddust", "鳞粉"), "who", whoName)));
                        break;
                    }
                    // Sheer Force removes secondary effects.
                    if (target && stages < 0 && sheerForceActive && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        break;
                    }
                    // Serene Grace boosts secondary-effect chance.
                    if (target && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        chance = Math.min(1.0, chance * secondaryChanceMul);
                    }
                    int appliedStages = stages;
                    if (AbilityEffects.has(who, "contrary")) appliedStages = -appliedStages;
                    if (target && stages < 0 && AbilityEffects.preventsStatDrops(who) && !AbilityEffects.ignoresDefenderAbility(atk)) {
                        out.add(plugin.getLang().uiFmt("battle.log.ability.block_stat_drop", "§6【{ab}】§e{who} 的能力不会被降低！", java.util.Map.of("ab", AbilityEffects.displayName(who.abilityId), "who", whoName)));
                        break;
                    }
                    if (target && stages < 0 && AbilityEffects.reflectsStatDrops(who) && !AbilityEffects.ignoresDefenderAbility(atk) && atk != null) {
                        atk.applyStage(stat, stages);
                        out.add(plugin.getLang().uiFmt("battle.log.ability.mirror_armor", "§6【Mirror Armor】§e{who} reflected the stat drop back at {atk}!", java.util.Map.of("who", whoName, "atk", atkName)));
                    } else {
                        who.applyStage(stat, appliedStages);
                        out.add(plugin.getLang().uiFmt("battle.log.stat_change", "§d{who} 的 {stat}{dir}了！", java.util.Map.of("who", whoName, "stat", plugin.getLang().statName(stat), "dir", (appliedStages > 0 ? plugin.getLang().ui("battle.word.rose", "提升") : plugin.getLang().ui("battle.word.fell", "降低")))));
                    }

                    // Mirror Herb: when the opponent boosts their own stats, copy once then consume.
                    // We trigger only on self-targeted boosts.
                    if (!target && stages > 0) {
                        PokemonInstance holder = def; // opponent of the booster
                        String holderName = defName;
                        if (holder != null && "mirror_herb".equalsIgnoreCase(holder.heldItemId) && holder.currentHp > 0) {
                            holder.applyStage(stat, stages);
                            holder.heldItemId = null;
                            out.add(plugin.getLang().uiFmt("battle.log.item.mirrorherb", "§e{holder} 的§f模仿香草§e发动了！能力变化被复制了！", java.util.Map.of("holder", holderName)));
                        }
                        if (holder != null && AbilityEffects.has(holder, "opportunist") && holder.currentHp > 0) {
                            holder.applyStage(stat, stages);
                            out.add(plugin.getLang().uiFmt("battle.log.ability.opportunist", "§6【跟风】§e{holder} 也提升了相同的能力！", java.util.Map.of("holder", holderName)));
                        }
                    }
                    if (stages < 0) {
                        out.addAll(HeldItemEffects.onStatLowered(who, whoName));

                        // Defiant / Competitive: when opponent lowers stats, sharply boost.
                        if (target) {
                            AbilityEffects.onOpponentStatLowered(who, whoName, out);
                        }

                        // Eject Pack: when stats are lowered by the opponent, consume and force switch.
                        // We only trigger when this is a target-side drop (i.e. opponent caused) and holder is the player's mon.
                        if (target && "eject_pack".equalsIgnoreCase(who.heldItemId) && who.currentHp > 0 && s.playerMon == who) {
                            who.heldItemId = null;
                            out.add(plugin.getLang().uiFmt("battle.log.item.ejectpack", "§e{who} 的§f逃脱背包§e发动了！", java.util.Map.of("who", whoName)));
                            out.addAll(forceAutoSwitch(plugin, viewer, s, plugin.getLang().ui("battle.log.item.ejectpack_hint", "§e逃脱背包让你撤回了宝可梦！")));
                        }
                    }
                }
                case "recoil" -> {
                    if (!didDamage) break;
                    double pct = asDouble(ef.get("percent"), 0.25);
                    int recoil = (int) Math.max(1, Math.floor(damageDone * pct));
                    if (AbilityEffects.preventsRecoil(atk)) { recoil = 0; }

                    atk.currentHp = Math.max(0, atk.currentHp - recoil);
                    try { if (plugin != null && plugin.getEvolutionManager() != null && viewer != null) plugin.getEvolutionManager().onBasculinRecoil(viewer.getUniqueId(), atk, recoil); } catch (Throwable ignored) {}
                    out.add(plugin.getLang().uiFmt("battle.log.recoil", "§c{atk} 受到了反作用力！ §7(-{dmg})", java.util.Map.of("atk", atkName, "dmg", String.valueOf(recoil))));
                }
                case "flinch" -> {
                    double chance = asDouble(ef.get("chance"), 0.1);
                    if (def.currentHp <= 0) break;
                    if (def.substituteHp > 0 && !AbilityEffects.has(atk, "infiltrator")) { out.add(plugin.getLang().ui("battle.log.substitute.block", "§7但是替身挡住了！")); break; }
                    // Covert Cloak: blocks secondary effects such as flinch.
                    if ("covert_cloak".equalsIgnoreCase(def.heldItemId) && !"status".equalsIgnoreCase(move.category())) {
                        out.add(plugin.getLang().uiFmt("battle.item.covert_cloak.block_secondary", "§7但是{def} 的§f密探斗篷§7阻止了追加效果！", java.util.Map.of("def", defName)));
                        break;
                    }
                    if (Util.RND.nextDouble() > chance) break;
                    boolean inner = !AbilityEffects.ignoresDefenderAbility(atk) && AbilityEffects.has(def, "innerfocus");
                    if (!inner) {
                        def.flinched = true;
                        out.add(plugin.getLang().uiFmt("battle.log.flinched_short", "§e{def} 畏缩了！", java.util.Map.of("def", defName)));
                        // Steadfast: Speed +1 when flinched.
                        if (AbilityEffects.has(def, "steadfast")) {
                            def.applyStage("spe", 1);
                            out.add(plugin.getLang().uiFmt("battle.log.ability.steadfast", "§6【{ability}】§e{def} 的速度提升了！", java.util.Map.of("ability", plugin.getLang().abilityName("steadfast", "不挠之心"), "def", defName)));
                        }
                    } else {
                        out.add(plugin.getLang().uiFmt("battle.log.ability.steadfast", "§6【不屈之心】§e{def} 没有畏缩！", java.util.Map.of("def", defName)));
                    }
                }
                case "confusion" -> {
                    double chance = asDouble(ef.get("chance"), 1.0);
                    boolean target = !"self".equalsIgnoreCase(asString(ef.get("target"), "target"));
                    PokemonInstance who = target ? def : atk;
                    String whoName = target ? defName : atkName;
                    if (target && def.substituteHp > 0 && !AbilityEffects.has(atk, "infiltrator")) { out.add(plugin.getLang().ui("battle.log.substitute.block", "§7但是替身挡住了！")); break; }
                    if (who.confusionTurns > 0) break;

                    // Own Tempo: immune to confusion.
                    if (AbilityEffects.has(who, "owntempo")) {
                        out.add(plugin.getLang().uiFmt("battle.log.ability.owntempo", "§6【{ability}】§e{who} 不会混乱！", java.util.Map.of("ability", plugin.getLang().abilityName("owntempo", "我行我素"), "who", whoName)));
                        break;
                    }

                    // Covert Cloak: blocks secondary confusion from damaging moves.
                    if (target && chance < 0.999 && "covert_cloak".equalsIgnoreCase(who.heldItemId)
                            && !"status".equalsIgnoreCase(move.category())) {
                        out.add(plugin.getLang().uiFmt("battle.log.item.covertcloak", "§7但是{who} 的§f密探斗篷§7阻止了追加效果！", java.util.Map.of("who", whoName)));
                        break;
                    }
                    if (target && shieldDustBlocks && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        out.add(plugin.getLang().uiFmt("battle.log.ability.shielddust", "§6【{ability}】§e{who} 不会受到追加效果影响！", java.util.Map.of("ability", plugin.getLang().abilityName("shielddust", "鳞粉"), "who", whoName)));
                        break;
                    }
                    if (target && sheerForceActive && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        break;
                    }
                    if (target && chance < 0.999 && !"status".equalsIgnoreCase(move.category())) {
                        chance = Math.min(1.0, chance * secondaryChanceMul);
                    }
                    if (Util.RND.nextDouble() > chance) break;
                    int min = (int) asDouble(ef.get("min"), 1);
                    int max = (int) asDouble(ef.get("max"), 4);
                    if (max < min) max = min;
                    who.confusionTurns = min + Util.RND.nextInt(max - min + 1);
                    out.add(plugin.getLang().uiFmt("battle.log.status.confusion", "§d{who} 混乱了！", java.util.Map.of("who", whoName)));
                    Species whoS = target ? defS : atkS;
                    out.addAll(HeldItemEffects.onConfusionApplied(who, whoS, whoName));
                }
                case "rest" -> {
                    if (s != null && s.terrainTurns > 0 && "electric".equalsIgnoreCase(s.terrain) && isGroundedForTerrain(atk, atkS, s)) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                        break;
                    }
                    // Gen1: heal to full, sleep for 2 turns
                    atk.currentHp = atk.maxHp(atkS);
                    atk.status = "sleep";
                    atk.sleepTurns = 2;
                    out.add(plugin.getLang().uiFmt("battle.status.sleep.fell", "§b{mon} 睡着了！", java.util.Map.of("mon", atkName)));
                    out.add(plugin.getLang().uiFmt("battle.common.healed", "§a{mon} 体力恢复了！", java.util.Map.of("mon", atkName)));
                }
                case "selfdestruct" -> {
                    // Gen1: user faints after dealing damage
                    if (didDamage) {
                        atk.currentHp = 0;
                        out.add(plugin.getLang().uiFmt("battle.common.fainted", "§c{mon} 倒下了！", java.util.Map.of("mon", atkName)));
                    }
                }
                case "fixed_damage", "ohko", "multi_hit", "two_turn", "transform", "mimic", "metronome" -> {
                    // handled earlier
                }
                default -> {
                    // Unknown effect kind: ignore for now.
                }
            }
        }

        
        // Ability: after-damage contact/status effects
        if (didDamage && damageDone > 0 && atk != null && def != null && move != null) {
            boolean contact = "physical".equalsIgnoreCase(move.category()) && move.power() > 0 && !AbilityEffects.contactBlockedByLongReach(atk);
            out.addAll(AbilityEffects.onAfterDamagingHit(atk, atkS, atkName, def, defS, defName, move, damageDone, contact));
            if (def.currentHp <= 0) {
                out.addAll(AbilityEffects.onFaint(def, defS, defName, atk, atkS, atkName, move, contact));
                out.addAll(AbilityEffects.onKnockOut(atk, atkS, atkName, def, defS, defName));
            }
        }

// Throat Spray: after using a sound-based move, raise SpA by 1 and consume.
        if (atk != null && atk.currentHp > 0 && "throat_spray".equalsIgnoreCase(atk.heldItemId)) {
            String mid = move == null ? null : move.id();
            if (mid != null && SOUND_MOVES.contains(mid.toLowerCase(java.util.Locale.ROOT))) {
                atk.applyStage("spa", 1);
                atk.heldItemId = null;
                out.add(plugin.getLang().uiFmt("battle.item.throat_spray.activate", "§d{mon} 的§f爽喉喷雾§d发动了！特攻提升了！", java.util.Map.of("mon", atkName)));
            }
        }

        String finalMoveIdNorm = AbilityEffects.norm(move.id());
        if (atk != null && AbilityEffects.has(atk, "gulpmissile")) {
            if ("surf".equals(finalMoveIdNorm) || "dive".equals(finalMoveIdNorm)) {
                int max = Math.max(1, atk.maxHp(atkS));
                atk.overrideSpeciesId = (atk.currentHp * 2 <= max) ? "cramorantgorging" : "cramorantgulping";
            }
        }

        // Clear charging after executing the second turn
        if (atk.chargingMoveId != null && atk.chargingTurnsRemaining <= 0) {
            atk.chargingMoveId = null;
            atk.chargingTurnsRemaining = 0;
        }


        // Life Orb recoil: after using a damaging move, lose 10% max HP (minimum 1).
        // - Magic Guard prevents this recoil.
        // - Sheer Force + Life Orb: no recoil if a removable secondary effect was suppressed.
        if (didDamage && damageDone > 0 && atk != null && "life_orb".equalsIgnoreCase(atk.heldItemId)) {
            if (AbilityEffects.has(atk, "magicguard")) {
                // no recoil
            } else if (sheerForceActive) {
                // no recoil
            } else {
                int maxHp = Math.max(1, atk.maxHp(atkS));
                int recoil = Math.max(1, maxHp / 10);
                int before = atk.currentHp;
                atk.currentHp = Math.max(0, atk.currentHp - recoil);
                try { if (plugin != null && plugin.getEvolutionManager() != null && viewer != null) plugin.getEvolutionManager().onBasculinRecoil(viewer.getUniqueId(), atk, recoil); } catch (Throwable ignored) {}
                if (atk.currentHp < before) {
            out.add(plugin.getLang().uiFmt("battle.item.life_orb.damage", "§c{mon} 因§f生命宝珠§c受到了 §c{dmg}§c 点伤害。", java.util.Map.of("mon", atkName, "dmg", String.valueOf(before - atk.currentHp))));
                }
            }
        }

        if (didDamage && damageDone > 0 && def != null && def.currentHp <= 0 && atk != null && viewer != null && plugin != null && plugin.getEvolutionManager() != null) {
            try {
                String atkSid = atk.speciesId == null ? "" : atk.speciesId.toLowerCase(java.util.Locale.ROOT);
                String defSid = def.speciesId == null ? "" : def.speciesId.toLowerCase(java.util.Locale.ROOT);
                if ("bisharp".equals(atkSid) && "bisharp".equals(defSid) && "kings_rock".equalsIgnoreCase(def.heldItemId)) {
                    plugin.getEvolutionManager().onBisharpLeaderDefeat(viewer.getUniqueId(), atk);
                }
                if (def.currentHp <= 0) {
                    plugin.getEvolutionManager().onFaint(null, def);
                }
            } catch (Throwable ignored) {}
        }

        // Shell Bell: heals the user by 1/8 of the damage dealt (min 1), after dealing damage.
        if (didDamage && damageDone > 0 && atk != null && atkS != null && "shell_bell".equalsIgnoreCase(atk.heldItemId)) {
            int maxHp = Math.max(1, atk.maxHp(atkS));
            int heal = Math.max(1, damageDone / 8);
            int before = atk.currentHp;
            atk.currentHp = Math.min(maxHp, atk.currentHp + heal);
            if (atk.currentHp > before) {
            out.add(plugin.getLang().uiFmt("battle.item.shell_bell.heal", "§a{mon} 的§f贝壳之铃§a回复了 §c{heal}§a 点体力。", java.util.Map.of("mon", atkName, "heal", String.valueOf(atk.currentHp - before))));
            }
        }


        
        // --- Item interaction moves (A module): Knock Off / Thief / Covet / Trick / Switcheroo ---
        // These are implemented by move id for reliability with Showdown imports.
        // They operate on PokemonInstance.heldItemId and are battle-safe.
        String mvItem = move.id() == null ? "" : move.id().toLowerCase(java.util.Locale.ROOT);
        if (!mvItem.isBlank()) {
            // Helper lambdas
            java.util.function.Function<String, Boolean> isUnremovable = (itemId) -> HeldItemEffects.isUnremovableItemId(itemId);


            java.util.function.BiFunction<PokemonInstance, PokemonInstance, Boolean> blockedByStickyHold = (taker, holder) -> {
                if (holder == null) return false;
                if (!AbilityEffects.preventsItemRemoval(holder)) return false;
                // Mold Breaker / Teravolt / Turboblaze ignore Sticky Hold.
                return !(taker != null && AbilityEffects.ignoresDefenderAbility(taker));
            };

            java.util.function.BiConsumer<PokemonInstance, String> onLoseItem = (mon, oldItem) -> {
                if (mon == null) return;
                if (oldItem != null && (mon.heldItemId == null || mon.heldItemId.isBlank())) {
                    if (AbilityEffects.has(mon, "unburden")) {
                        mon.unburdenActive = true;
                    }
                }
            };

            // Knock Off: remove target's item (does not steal).
            if ("knockoff".equals(mvItem) || "knock_off".equals(mvItem)) {
                if (def != null && def.currentHp > 0 && didDamage && damageDone > 0) {
                    String item = def.heldItemId;
                    if (item != null && !item.isBlank()
                            && !isUnremovable.apply(item)
                            && !blockedByStickyHold.apply(atk, def)) {
                        def.heldItemId = null;
                        onLoseItem.accept(def, item);
                        out.add("§6【拍落】§e" + defName + " 的§f" + plugin.getLang().item(item, item) + "§e 被打落了！");
                        // Drop the item to the player as a physical item (quality-of-life).
                        // If inventory is full, it will drop on the ground.
                        if (viewer != null && plugin != null) {
                            ItemDef defItem = plugin.getItemRegistry().get(item);
                            if (defItem != null) {
                                org.bukkit.inventory.ItemStack stack = plugin.getItems().createItem(defItem, plugin.getLang(), 1);
                                if (stack != null) {
                                    java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> left = viewer.getInventory().addItem(stack);
                                    if (!left.isEmpty()) {
                                        viewer.getWorld().dropItemNaturally(viewer.getLocation(), stack);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Thief / Covet: steal target's item if attacker holds none and move dealt damage.
            if ("thief".equals(mvItem) || "covet".equals(mvItem)) {
                if (atk != null && def != null && didDamage && damageDone > 0 && atk.currentHp > 0 && def.currentHp > 0) {
                    if (atk.heldItemId == null || atk.heldItemId.isBlank()) {
                        String item = def.heldItemId;
                        if (item != null && !item.isBlank()
                                && !isUnremovable.apply(item)
                                && !blockedByStickyHold.apply(atk, def)) {
                            def.heldItemId = null;
                            atk.heldItemId = item;
                            onLoseItem.accept(def, item);
                            out.add("§6【" + ("thief".equals(mvItem) ? "小偷" : "渴望") + "】§e" + atkName + " 偷走了 " + defName + " 的§f" + plugin.getLang().item(item, item) + "§e！");
                        }
                    }
                }
            }

            // Trick / Switcheroo: swap held items (status move). Fails if either side has an unremovable item,
            // or if target has Sticky Hold (unless taker ignores abilities).
            if ("trick".equals(mvItem) || "switcheroo".equals(mvItem)) {
                if (atk != null && def != null && atk.currentHp > 0 && def.currentHp > 0) {
                    String aIt = atk.heldItemId;
                    String dIt = def.heldItemId;
                    boolean aLock = aIt != null && !aIt.isBlank() && isUnremovable.apply(aIt);
                    boolean dLock = dIt != null && !dIt.isBlank() && isUnremovable.apply(dIt);
                    if (blockedByStickyHold.apply(atk, def)) {
            out.add(plugin.getLang().uiFmt("battle.ability.sticky_hold.block_swap", "§6【黏着】§e{mon} 的道具无法被交换！", java.util.Map.of("mon", defName)));
                    } else if (aLock || dLock) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                    } else {
                        atk.heldItemId = dIt;
                        def.heldItemId = aIt;
                        onLoseItem.accept(atk, aIt);
                        onLoseItem.accept(def, dIt);
            out.add(plugin.getLang().uiFmt("battle.move.trick.switch_items", "§6【{move}】§e双方的道具被交换了！", java.util.Map.of("move", plugin.getLang().moveName("trick".equals(mvItem) ? "trick" : "switcheroo"))));
                    }
                }
            }
            // Bestow: give your held item to the target (status move). Fails if target already has an item or item is unremovable.
            if ("bestow".equals(mvItem)) {
                if (atk != null && def != null && atk.currentHp > 0 && def.currentHp > 0) {
                    String aIt = atk.heldItemId;
                    String dIt = def.heldItemId;
                    if (aIt == null || aIt.isBlank()) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                    } else if (dIt != null && !dIt.isBlank()) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                    } else if (isUnremovable.apply(aIt)) {
                        out.add(plugin.getLang().ui("battle.log.fail", "§7失败了。"));
                    } else {
                        atk.heldItemId = null;
                        def.heldItemId = aIt;
                        onLoseItem.accept(atk, aIt);
                        out.add("§6【传递礼物】§e" + atkName + " 把§f" + plugin.getLang().item(aIt, aIt) + "§e 交给了 " + defName + "！");
                    }
                }
            }

            // Bug Bite / Pluck: if the target has a berry, eat it immediately and apply its effect to the attacker.
            if ("bugbite".equals(mvItem) || "pluck".equals(mvItem)) {
                if (atk != null && def != null && didDamage && damageDone > 0 && atk.currentHp > 0 && def.currentHp > 0) {
                    String item = def.heldItemId;
                    if (item != null && !item.isBlank() && item.toLowerCase(java.util.Locale.ROOT).contains("berry")
                            && !isUnremovable.apply(item)
                            && !blockedByStickyHold.apply(atk, def)) {
                        def.heldItemId = null;
                        onLoseItem.accept(def, item);
                        out.add("§6【" + ("bugbite".equals(mvItem) ? "虫咬" : "啄食") + "】§e" + atkName + " 吃掉了 " + defName + " 的§f" + plugin.getLang().item(item, item) + "§e！");
                        out.addAll(HeldItemEffects.forceEatBerryNow(plugin, atk, atkS, atkName, item));
                    }
                }
            }

            // Fling: throw your held item to deal damage and consume it; some items inflict a status.
            if ("fling".equals(mvItem)) {
                if (atk != null && def != null && didDamage && damageDone > 0 && atk.currentHp > 0 && def.currentHp > 0) {
                    String item = atk.heldItemId;
                    if (item != null && !item.isBlank() && !isUnremovable.apply(item)) {
                        atk.heldItemId = null;
                        onLoseItem.accept(atk, item);
                        out.add("§6【投掷】§e" + atkName + " 投掷了§f" + plugin.getLang().item(item, item) + "§e！");
                        // Status side-effects (simplified but useful)
                        String st = null;
                        if ("flame_orb".equalsIgnoreCase(item)) st = "burn";
                        else if ("toxic_orb".equalsIgnoreCase(item)) st = "toxic";
                        else if ("poison_barb".equalsIgnoreCase(item)) st = "poison";
                        else if ("light_ball".equalsIgnoreCase(item)) st = "paralyze";

                        if (st != null && (def.status == null || "none".equalsIgnoreCase(def.status))) {
                            if (!AbilityEffects.isStatusImmune(def, st)) {
                                if ("toxic".equals(st)) {
                                    def.status = "toxic";
                                    def.toxicCounter = 1;
            out.add(plugin.getLang().uiFmt("battle.status.toxic", "§a{mon} 中了剧毒！", java.util.Map.of("mon", defName)));
                                } else if ("poison".equals(st)) {
                                    def.status = "poison";
            out.add(plugin.getLang().uiFmt("battle.status.poison", "§a{mon} 中毒了！", java.util.Map.of("mon", defName)));
                                } else if ("burn".equals(st)) {
                                    def.status = "burn";
            out.add(plugin.getLang().uiFmt("battle.status.burn", "§c{mon} 被灼伤了！", java.util.Map.of("mon", defName)));
                                } else if ("paralyze".equals(st)) {
                                    def.status = "paralyze";
            out.add(plugin.getLang().uiFmt("battle.status.paralysis", "§e{mon} 麻痹了！", java.util.Map.of("mon", defName)));
                                }
                                // allow berries held by defender to react (if any were present)
                                out.addAll(HeldItemEffects.onStatusApplied(def, defS, defName));
                            }
                        }
                    }
                }
            }

        }


        return out;
    }

    static void resetRetaliationMemory(PokemonInstance mon) {
        if (mon == null) return;
        mon.lastPhysicalDamageTaken = 0;
        mon.lastPhysicalDamageSourceUuid = null;
        mon.lastPhysicalDamageTurn = -1;
        mon.lastSpecialDamageTaken = 0;
        mon.lastSpecialDamageSourceUuid = null;
        mon.lastSpecialDamageTurn = -1;
        mon.lastDamageTaken = 0;
        mon.lastDamageSourceUuid = null;
        mon.lastDamageTurn = -1;
    }

    private static void recordReactiveDamage(BattleSession s, PokemonInstance attacker, PokemonInstance target, Move move, int dealt) {
        if (s == null || attacker == null || target == null || move == null || dealt <= 0) return;
        target.tookDamageThisTurn = true;
        target.lastDamageTaken = dealt;
        target.lastDamageSourceUuid = attacker.uuid;
        target.lastDamageTurn = s.turn;
        String cat = move.category() == null ? "" : move.category().toLowerCase(java.util.Locale.ROOT);
        if ("physical".equals(cat)) {
            target.lastPhysicalDamageTaken = dealt;
            target.lastPhysicalDamageSourceUuid = attacker.uuid;
            target.lastPhysicalDamageTurn = s.turn;
        } else if ("special".equals(cat)) {
            target.lastSpecialDamageTaken = dealt;
            target.lastSpecialDamageSourceUuid = attacker.uuid;
            target.lastSpecialDamageTurn = s.turn;
        }
    }

    /**
     * Apply damage to a target, respecting Substitute (no overflow).
     * Returns the amount of damage "dealt" for reporting / Bide / Counter tracking.
     */
    private static int applyDamageToTarget(PokemonInstance target, Species targetS, int dmg,
                                          List<String> out, String targetName,
                                          boolean silent) {
        if (dmg <= 0) return 0;
        PokeDemoPlugin plugin = PokeDemoPlugin.INSTANCE;
        LangManager lang = plugin != null ? plugin.getLang() : null;
        // Substitute takes damage instead of the Pokémon.
        if (target.substituteHp > 0) {
            int before = target.substituteHp;
            target.substituteHp = Math.max(0, target.substituteHp - dmg);
            int dealt = Math.min(before, dmg);
            if (!silent) {
                out.add(lang != null ? lang.uiFmt("battle.substitute.took_damage", "§7替身替 {mon} 承受了伤害！", java.util.Map.of("mon", targetName)) : ("§7替身替 " + targetName + " 承受了伤害！"));
                if (target.substituteHp <= 0) out.add(lang != null ? lang.ui("battle.substitute.broke", "§7替身破碎了！") : "§7替身破碎了！");
            }
            return dealt;
        }
        int beforeHp = target.currentHp;

        // Ability: Sturdy (at full HP, survive a would-be OHKO)
        if (targetS != null && AbilityEffects.sturdySaves(target, targetS, dmg)) {
            target.currentHp = 1;
            if (!silent) out.add(lang != null ? lang.uiFmt("battle.ability.sturdy.hold_on", "§6【结实】§e{mon} 挺住了！(1 HP)", java.util.Map.of("mon", targetName)) : ("§6【结实】§e" + targetName + " 挺住了！(1 HP)"));
            return beforeHp - target.currentHp;
        }

        // Focus Sash / Focus Band: prevent fainting at 1 HP (simplified)
        if (beforeHp > 0 && dmg >= beforeHp && target.substituteHp <= 0) {
            String hid = target.heldItemId == null ? "" : target.heldItemId.toLowerCase(java.util.Locale.ROOT);
            boolean sash = hid.equals("focus_sash");
            boolean band = hid.equals("focus_band");
            if (sash) {
                // only if at full HP
                if (beforeHp == target.maxHp(targetS)) {
                    target.currentHp = 1;
                    target.heldItemId = null;
                    if (!silent) out.add(lang != null ? lang.uiFmt("battle.item.focus_sash.hold_on", "§e{mon} 靠§f气势披带§e撑住了！(1 HP)", java.util.Map.of("mon", targetName)) : ("§e" + targetName + " 靠§f气势披带§e撑住了！(1 HP)"));
                    return beforeHp - target.currentHp;
                }
            } else if (band) {
                if (Util.RND.nextDouble() < 0.10) {
                    target.currentHp = 1;
                    if (!silent) out.add(lang != null ? lang.uiFmt("battle.item.focus_band.hold_on", "§e{mon} 靠§f气势头带§e撑住了！(1 HP)", java.util.Map.of("mon", targetName)) : ("§e" + targetName + " 靠§f气势头带§e撑住了！(1 HP)"));
                    return beforeHp - target.currentHp;
                }
            }
        }

        target.currentHp = Math.max(0, target.currentHp - dmg);
        if (target.illusionActive && target.currentHp < beforeHp) {
            target.illusionActive = false;
            target.overrideSpeciesId = null;
        }
        if (target.currentHp <= 0 && !target.battleFaintedCounted) {
            BattleSession ctx = AbilityEffects.contextSession();
            if (ctx != null) {
                if (target == ctx.playerMon) ctx.playerPartyFaintedCount++;
                else if (target == ctx.wildMon) ctx.wildPartyFaintedCount++;
            }
            target.battleFaintedCounted = true;
        }
        return Math.min(beforeHp, dmg);
    }

    private static int reviveFirstFaintedPartyMember(PokeDemoPlugin plugin, BattleSession s, boolean playerActing) {
        if (!playerActing) return 0;
        try {
            PlayerProfile prof = plugin.getStorage().getProfile(s.playerId);
            if (prof == null) return 0;
            for (PokemonInstance p : prof.party) {
                if (p == null || p.isEgg) continue;
                if (s.playerMon != null && p.uuid != null && p.uuid.equals(s.playerMon.uuid)) continue;
                if (p.currentHp <= 0) {
                    Species sp = plugin.getDex().getSpecies(p.effectiveSpeciesId());
                    if (sp == null) continue;
                    p.currentHp = Math.max(1, p.maxHp(sp) / 2);
                    p.status = "none";
                    p.sleepTurns = 0;
                    p.toxicCounter = 0;
                    return 1;
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static boolean knowsMove(PokemonInstance mon, String moveId) {
        if (mon == null || moveId == null) return false;
        for (MoveSlot ms : mon.effectiveMoves()) {
            if (ms != null && ms.moveId != null && moveId.equalsIgnoreCase(ms.moveId)) return true;
        }
        return false;
    }

    private static boolean hasEffect(List<Map<String, Object>> effects, String id) {
        return findEffect(effects, id) != null;
    }

    private static Map<String, Object> findEffect(List<Map<String, Object>> effects, String id) {
        if (effects == null) return null;
        for (Map<String, Object> ef : effects) {
            if (ef == null) continue;
            String kind = asString(ef.get("kind"), asString(ef.get("id"), null));
            if (kind == null) continue;
            if (id.equalsIgnoreCase(kind)) return ef;
        }
        return null;
    }

    /**
     * Force the player's side to switch to the next available Pokémon.
     * This is a simplified implementation used by items like Eject Button / Eject Pack.
     */
    private static List<String> forceAutoSwitch(PokeDemoPlugin plugin, Player viewer, BattleSession s, String hintLine) {
        List<String> out = new ArrayList<>();
        if (plugin == null || viewer == null || s == null || s.playerId == null) return out;
        PlayerProfile prof = plugin.getStorage().getProfile(s.playerId);
        if (prof == null || prof.party == null || prof.party.isEmpty()) return out;

        // Find current active index by UUID.
        int cur = -1;
        if (s.playerMon != null && s.playerMon.uuid != null) {
            for (int i = 0; i < prof.party.size(); i++) {
                PokemonInstance p = prof.party.get(i);
                if (p != null && s.playerMon.uuid.equals(p.uuid)) {
                    cur = i;
                    break;
                }
            }
        }

        // Choose next alive Pokémon.
        int next = -1;
        for (int i = 0; i < prof.party.size(); i++) {
            if (i == cur) continue;
            PokemonInstance p = prof.party.get(i);
            if (p == null) continue;
            if (p.currentHp > 0) { next = i; break; }
        }
        if (next < 0) return out; // nothing to switch

        PokemonInstance newMon = prof.party.get(next);
        if (newMon == null) return out;

        // Apply switch.
        if (hintLine != null && !hintLine.isBlank()) out.add(hintLine);
        String nm = (newMon.nickname != null && !newMon.nickname.isBlank()) ? newMon.nickname
                : (newMon.speciesName != null && !newMon.speciesName.isBlank() ? newMon.speciesName : newMon.speciesId);
        out.add(plugin.getLang().uiFmt("battle.log.send_out", "§b你派出了 {mon}！", java.util.Map.of("mon", nm)));
        s.playerMon = newMon;
        s.playerParticipants.add(newMon.uuid);
        // Reset transient per-turn flags for new mon.
        newMon.flinched = false;
        newMon.battleFaintedCounted = false;
        newMon.justSwitchedIn = true;
        newMon.typeChangeAbilityUsed = false;

        // Close/open GUI will be handled by BattleManager's normal end-of-turn update.
        return out;
    }

    private static boolean moveTargetsOpponent(List<Map<String, Object>> effects) {
        if (effects == null || effects.isEmpty()) return false;
        for (Map<String, Object> ef : effects) {
            if (ef == null || ef.isEmpty()) continue;
            String kind = asString(ef.get("kind"), asString(ef.get("id"), "")).toLowerCase(java.util.Locale.ROOT);
            String target = asString(ef.get("target"), "target").toLowerCase(java.util.Locale.ROOT);
            if ("self".equals(target)) continue;
            if ("screen".equals(kind) || "weather".equals(kind) || "side_condition".equals(kind) || "wish".equals(kind)
                    || "ingrain".equals(kind) || "aqua_ring".equals(kind)) continue;
            return true;
        }
        return false;
    }


    private static java.util.List<String> currentTypes(PokemonInstance mon, Species sp) {
        if (mon != null && mon.overrideType1 != null && !mon.overrideType1.isBlank()) {
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            out.add(mon.overrideType1.toLowerCase(java.util.Locale.ROOT));
            if (mon.overrideType2 != null && !mon.overrideType2.isBlank()) out.add(mon.overrideType2.toLowerCase(java.util.Locale.ROOT));
            return out;
        }
        if (sp == null || sp.types() == null) return java.util.List.of();
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String t : sp.types()) {
            if (t != null && !t.isBlank()) out.add(t.toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }


private static boolean isReflectableMoveId(String moveId) {
    String id = moveId == null ? "" : moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "");
    return java.util.Set.of(
            "toxic","poisonpowder","stunspore","sleeppowder","thunderwave","willowisp","leechseed",
            "taunt","torment","encore","disable","healblock","gastroacid","confuseray","swagger",
            "attract","magicpowder","soak","simplebeam","worryseed","entrainment","glares","lovelykiss",
            "hypnosis","spore"
    ).contains(id);
}

private static boolean isSnatchableMoveId(String moveId) {
    String id = moveId == null ? "" : moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "");
    return java.util.Set.of(
            "recover","softboiled","milkdrink","roost","slackoff","healorder","wish","moonlight",
            "morningsun","synthesis","shoreup","rest","swallow","stockpile","focusenergy","curse",
            "aromatherapy","healbell","junglehealing","lunarblessing","ingrain","aquaring","protect",
            "detect","kingsshield","spikyshield","banefulbunker","burningbulwark","obstruct","silktrap",
            "maxguard","matblock","safeguard","tailwind","auroraveil","luckychant","charge","magnetrise"
    ).contains(id);
}

private static Move plannedMoveForSide(PokeDemoPlugin plugin, BattleSession s, boolean playerSide) {
    if (s == null || plugin == null) return null;
    String id = playerSide ? s.playerPlannedMoveId : s.wildPlannedMoveId;
    return (id == null || id.isBlank()) ? null : plugin.getDex().getMoveOrPlaceholder(id);
}

private static void zeroMovePp(PokemonInstance mon, String moveId) {
    if (mon == null || moveId == null || moveId.isBlank()) return;
    java.util.List<MoveSlot> slots = mon.effectiveMoves();
    if (slots == null) return;
    for (MoveSlot ms : slots) {
        if (ms != null && ms.moveId != null && ms.moveId.equalsIgnoreCase(moveId)) {
            ms.pp = 0;
            return;
        }
    }
}

private static boolean isBulletMoveId(String moveId) {
    String id = moveId == null ? "" : moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "");
    return java.util.Set.of(
            "acidspray","aura sphere","aurasphere","beakblast","bulletseed","eggbomb","electroball",
            "energyball","focusblast","gyroball","iceball","magnetbomb","mistball","mudbomb",
            "octazooka","pollenpuff","pyroball","rockwrecker","seedbomb","shadowball","sludgebomb",
            "weatherball","zapcannon"
    ).contains(id);
}

private static boolean isWindMoveId(String moveId) {
    String id = moveId == null ? "" : moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "");
    return java.util.Set.of(
            "aircutter","bleakwindstorm","fairywind","heatwave","hurricane","icywind","ominouswind",
            "petalblizzard","razorwind","sandsearstorm","springtidestorm","tailwind","twister",
            "wildboltstorm"
    ).contains(id);
}

private static boolean isSoundMoveId(String moveId) {
    String id = moveId == null ? "" : moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "");
    return SOUND_MOVES.contains(id) || java.util.Set.of("boomburst","bugbuzz","clangingscales","disarmingvoice","echoedvoice","eerieimpulse","growl","healbell","hypervoice","metal sound","metalsound","nobleroar","partingshot","perishsong","roar","screech","sing","snarl","snore","sparklingaria","supersonic","uproar").contains(id);
}

private static boolean isGroundedForTerrain(PokemonInstance mon, Species sp, BattleSession s) {
    if (mon == null || sp == null) return false;
    if (s != null && s.gravityTurns > 0) return true;
    if (mon.groundedBySmackDown || mon.roostSuppressFlying) return true;
    if (mon.magnetRiseTurns > 0 || mon.telekinesisTurns > 0) return false;
    java.util.List<String> ts = effectiveTypes(mon, sp);
    if (ts.stream().anyMatch(t -> "flying".equalsIgnoreCase(t))) return false;
    if (mon.abilityId != null && mon.abilityId.equalsIgnoreCase("levitate") && !mon.abilitySuppressed) return false;
    if (mon.heldItemId != null && mon.heldItemId.equalsIgnoreCase("air_balloon")) return false;
    return true;
}

private static boolean canUseHealingMoveSimple(String moveId) {
    String id = moveId == null ? "" : moveId.toLowerCase(java.util.Locale.ROOT).replace("_", "");
    return java.util.Set.of("recover","roost","softboiled","milkdrink","slackoff","healorder","moonlight","morningsun","synthesis","shoreup","rest","wish","healpulse","healpulse","swallow").contains(id);
}

    private static String asString(Object o, String def) {
        if (o == null) return def;
        return String.valueOf(o);
    }

    private static double asDouble(Object o, double def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception ignored) {
            return def;
        }
    }

    private static String statToCn(String stat) {
        // Deprecated: keep for compatibility. Prefer LangManager.statName().
        return stat;
    }


private static java.util.List<String> effectiveTypes(PokemonInstance mon, Species sp) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (mon != null && mon.overrideType1 != null && !mon.overrideType1.isBlank()) out.add(mon.overrideType1);
    else if (sp != null && sp.types() != null && !sp.types().isEmpty() && sp.types().get(0) != null) out.add(sp.types().get(0));
    if (mon != null && mon.overrideType2 != null && !mon.overrideType2.isBlank()) {
        if (out.stream().noneMatch(t -> t.equalsIgnoreCase(mon.overrideType2))) out.add(mon.overrideType2);
    } else if (sp != null && sp.types() != null && sp.types().size() >= 2 && sp.types().get(1) != null) {
        String t2 = sp.types().get(1);
        if (out.stream().noneMatch(t -> t.equalsIgnoreCase(t2))) out.add(t2);
    }
    if (mon != null && mon.roostSuppressFlying) {
        out.removeIf(t -> "flying".equalsIgnoreCase(t));
    }
    return out;
}


}