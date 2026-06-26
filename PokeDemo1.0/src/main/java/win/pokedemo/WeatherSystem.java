package win.pokedemo;

import java.util.Locale;

/**
 * Minimal, single-battle weather core.
 *
 * Scope:
 * - Tracks SUN/RAIN/SAND/HAIL with a simple turn counter.
 * - Provides Cloud Nine/Air Lock style suppression (cloudnine).
 * - Provides end-of-turn residual (sand/hail damage) and simple ability hooks.
 */
public final class WeatherSystem {
    private WeatherSystem() {}

    /** Returns the "effective" weather for calculations (suppressed by Cloud Nine). */
    public static WeatherType effectiveWeather(BattleSession s) {
        if (s == null) return WeatherType.NONE;
        if (s.weather == null || s.weatherTurns <= 0) return WeatherType.NONE;
        // Cloud Nine / Air Lock (we only model cloudnine id) suppresses weather effects.
        if (AbilityEffects.has(s.playerMon, "cloudnine") || AbilityEffects.has(s.wildMon, "cloudnine")
                || AbilityEffects.has(s.playerMon, "airlock") || AbilityEffects.has(s.wildMon, "airlock")) {
            return WeatherType.NONE;
        }
        return s.weather;
    }

    public static void setWeather(BattleSession s, WeatherType type, int turns) {
        if (s == null) return;
        s.weather = (type == null ? WeatherType.NONE : type);
        s.weatherTurns = Math.max(0, turns);
    }


    /** Standard weather duration (5), extended to 8 if the source holds the matching weather rock. */
    public static int durationForSource(WeatherType type, PokemonInstance source) {
        int base = 5;
        if (type == null || source == null) return base;
        String it = source.heldItemId == null ? "" : source.heldItemId.toLowerCase(Locale.ROOT);
        return switch (type) {
            case SUN -> ("heat_rock".equals(it) ? 8 : base);
            case RAIN -> ("damp_rock".equals(it) ? 8 : base);
            case SAND -> ("smooth_rock".equals(it) ? 8 : base);
            case HAIL -> ("icy_rock".equals(it) ? 8 : base);
            default -> base;
        };
    }

    /** Weather power multiplier for a move type, based on the effective weather. */
    public static double movePowerMultiplier(BattleSession s, String moveTypeLower) {
        WeatherType w = effectiveWeather(s);
        if (w == WeatherType.NONE) return 1.0;
        if (moveTypeLower == null) return 1.0;
        String t = moveTypeLower.toLowerCase(Locale.ROOT);
        if (w == WeatherType.RAIN) {
            if ("water".equals(t)) return 1.5;
            if ("fire".equals(t)) return 0.5;
        }
        if (w == WeatherType.SUN) {
            if ("fire".equals(t)) return 1.5;
            if ("water".equals(t)) return 0.5;
        }
        return 1.0;
    }

    public static boolean isType(Species sp, String type) {
        if (sp == null || sp.types() == null || type == null) return false;
        String t = type.toLowerCase(Locale.ROOT);
        for (String s : sp.types()) {
            if (s == null) continue;
            if (s.equalsIgnoreCase(t)) return true;
        }
        return false;
    }

    /** Apply end-of-turn weather residuals and weather-based ability effects. */
    public static void applyEndOfTurn(PokeDemoPlugin plugin, BattleSession s, Species pS, Species wS, String pName, String wName, java.util.List<String> out) {
        if (s == null || out == null) return;
        WeatherType w = effectiveWeather(s);

        // Weather-based effects only apply when effective weather exists.
        if (w != WeatherType.NONE) {
            // Ability: Hydration - cures major status at end of turn in rain.
            if (w == WeatherType.RAIN) {
                cureHydration(s.playerMon, pName, out);
                cureHydration(s.wildMon, wName, out);
            }

            // Ability: Rain Dish - heals 1/16 in rain.
            if (w == WeatherType.RAIN) {
                rainDishHeal(s.playerMon, pS, pName, out);
                rainDishHeal(s.wildMon, wS, wName, out);
            }

            // Ability: Ice Body - heals 1/16 in hail.
            if (w == WeatherType.HAIL) {
                iceBodyHeal(s.playerMon, pS, pName, out);
                iceBodyHeal(s.wildMon, wS, wName, out);
            }

            // Ability: Dry Skin - heal in rain, hurt in sun.
            if (w == WeatherType.RAIN) {
                drySkinWeather(s.playerMon, pS, pName, out, true);
                drySkinWeather(s.wildMon, wS, wName, out, true);
            } else if (w == WeatherType.SUN) {
                drySkinWeather(s.playerMon, pS, pName, out, false);
                drySkinWeather(s.wildMon, wS, wName, out, false);
            }

            // Ability: Solar Power - in sun, lose 1/8 HP at end of turn.
            if (w == WeatherType.SUN) {
                solarPowerDrain(s.playerMon, pS, pName, out);
                solarPowerDrain(s.wildMon, wS, wName, out);
            }

            // Sand/Hail residual damage (Magic Guard blocks).
            if (w == WeatherType.SAND) {
                sandDamage(s.playerMon, pS, pName, out);
                sandDamage(s.wildMon, wS, wName, out);
            } else if (w == WeatherType.HAIL) {
                hailDamage(s.playerMon, pS, pName, out);
                hailDamage(s.wildMon, wS, wName, out);
            }
        }

        // Ability: Harvest - regrow last consumed berry at end of turn.
        harvestRegrow(s, s.playerMon, pName, out);
        harvestRegrow(s, s.wildMon, wName, out);

        decWeatherTimer(s, out);
    }

    private static void harvestRegrow(BattleSession s, PokemonInstance mon, String name, java.util.List<String> out) {
        if (mon == null || mon.currentHp <= 0) return;
        if (!AbilityEffects.has(mon, "harvest")) return;
        if (mon.heldItemId != null && !mon.heldItemId.isBlank()) return;
        String last = mon.lastConsumedBerryId;
        if (last == null || last.isBlank()) return;
        // In mainline: 50% chance, 100% in sun. Weather suppression already reflected in effectiveWeather().
        boolean sunny = effectiveWeather(s) == WeatherType.SUN;
        double chance = sunny ? 1.0 : 0.5;
        if (Util.RND.nextDouble() > chance) return;
        mon.heldItemId = last;
        out.add("§6【收获】§a" + name + " 重新获得了树果！");
    }

    private static void decWeatherTimer(BattleSession s, java.util.List<String> out) {
        if (s == null) return;
        if (s.weather == null || s.weather == WeatherType.NONE) {
            s.weatherTurns = 0;
            return;
        }
        if (s.weatherTurns > 0) {
            s.weatherTurns--;
            if (s.weatherTurns <= 0) {
                s.weatherTurns = 0;
                WeatherType ended = s.weather;
                s.weather = WeatherType.NONE;
                // End message
                switch (ended) {
                                        case SUN -> out.add("§7阳光恢复正常了。");
                                        case RAIN -> out.add("§7雨停了。");
                                        case SAND -> out.add("§7沙暴平息了。");
                                        case HAIL -> out.add("§7冰雹停止了。");
                    default -> {}
                }
            }
        }
    }

    private static void cureHydration(PokemonInstance mon, String name, java.util.List<String> out) {
        if (mon == null) return;
        if (!AbilityEffects.has(mon, "hydration")) return;
        String st = mon.status == null ? "none" : mon.status.toLowerCase(Locale.ROOT);
        if (!"none".equals(st)) {
            mon.status = "none";
            mon.toxicCounter = 0;
            out.add("§6【湿润之躯】§a" + name + " 治愈了异常状态！");
        }
    }

    private static void rainDishHeal(PokemonInstance mon, Species sp, String name, java.util.List<String> out) {
        if (mon == null || sp == null) return;
        if (!AbilityEffects.has(mon, "raindish")) return;
        if (mon.currentHp <= 0) return;
        if (AbilityEffects.has(mon, "magicguard")) return; // doesn't matter, but keep consistent
        int max = Math.max(1, mon.maxHp(sp));
        int heal = Math.max(1, max / 16);
        int before = mon.currentHp;
        mon.currentHp = Math.min(max, mon.currentHp + heal);
        if (mon.currentHp > before) out.add("§6【雨盘】§a" + name + " 回复了 §c" + (mon.currentHp - before) + "§a 点体力！");
    }

    private static void iceBodyHeal(PokemonInstance mon, Species sp, String name, java.util.List<String> out) {
        if (mon == null || sp == null) return;
        if (!AbilityEffects.has(mon, "icebody")) return;
        if (mon.currentHp <= 0) return;
        int max = Math.max(1, mon.maxHp(sp));
        int heal = Math.max(1, max / 16);
        int before = mon.currentHp;
        mon.currentHp = Math.min(max, mon.currentHp + heal);
        if (mon.currentHp > before) out.add("§6【冰冻之躯】§a" + name + " 回复了 §c" + (mon.currentHp - before) + "§a 点体力！");
    }

    private static void drySkinWeather(PokemonInstance mon, Species sp, String name, java.util.List<String> out, boolean rain) {
        if (mon == null || sp == null) return;
        if (!AbilityEffects.has(mon, "dryskin")) return;
        if (mon.currentHp <= 0) return;
        int max = Math.max(1, mon.maxHp(sp));
        int amt = Math.max(1, max / 8);
        if (rain) {
            int before = mon.currentHp;
            mon.currentHp = Math.min(max, mon.currentHp + amt);
            if (mon.currentHp > before) out.add("§6【干燥皮肤】§a" + name + " 在雨中回复了 §c" + (mon.currentHp - before) + "§a 点体力！");
        } else {
            if (AbilityEffects.has(mon, "magicguard")) return;
            mon.currentHp = Math.max(0, mon.currentHp - amt);
            out.add("§6【干燥皮肤】§c" + name + " 在强光下受到了 §c" + amt + "§c 点伤害！");
        }
    }

    private static void solarPowerDrain(PokemonInstance mon, Species sp, String name, java.util.List<String> out) {
        if (mon == null || sp == null) return;
        if (!AbilityEffects.has(mon, "solarpower")) return;
        if (mon.currentHp <= 0) return;
        if (AbilityEffects.has(mon, "magicguard")) return;
        int max = Math.max(1, mon.maxHp(sp));
        int dmg = Math.max(1, max / 8);
        mon.currentHp = Math.max(0, mon.currentHp - dmg);
        out.add("§6【太阳之力】§c" + name + " 受到了 §c" + dmg + "§c 点伤害！");
    }

    private static void sandDamage(PokemonInstance mon, Species sp, String name, java.util.List<String> out) {
        if (mon == null || sp == null) return;
        if (mon.currentHp <= 0) return;
        if (AbilityEffects.has(mon, "magicguard")) return;
        // Immunities
        if (isType(sp, "rock") || isType(sp, "ground") || isType(sp, "steel")) return;
        if (AbilityEffects.has(mon, "overcoat")) return;
        int max = Math.max(1, mon.maxHp(sp));
        int dmg = Math.max(1, max / 16);
        mon.currentHp = Math.max(0, mon.currentHp - dmg);
        out.add("§e沙暴袭击了 " + name + "！§c(-" + dmg + ")");
    }

    private static void hailDamage(PokemonInstance mon, Species sp, String name, java.util.List<String> out) {
        if (mon == null || sp == null) return;
        if (mon.currentHp <= 0) return;
        if (AbilityEffects.has(mon, "magicguard")) return;
        if (isType(sp, "ice")) return;
        if (AbilityEffects.has(mon, "overcoat")) return;
        int max = Math.max(1, mon.maxHp(sp));
        int dmg = Math.max(1, max / 16);
        mon.currentHp = Math.max(0, mon.currentHp - dmg);
        out.add("§b冰雹砸向了 " + name + "！§c(-" + dmg + ")");
    }
}
