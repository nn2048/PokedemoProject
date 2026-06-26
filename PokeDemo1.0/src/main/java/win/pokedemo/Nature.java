package win.pokedemo;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Cobblemon-like nature (性格).
 *
 * <p>In this demo plugin we mainly store and display nature. Stat modifiers are
 * provided for future extension (e.g., +atk -def).</p>
 */
public enum Nature {
    HARDY("勤奋", null, null),
    LONELY("怕寂寞", "atk", "def"),
    BRAVE("勇敢", "atk", "spe"),
    ADAMANT("固执", "atk", "spa"),
    NAUGHTY("顽皮", "atk", "spd"),

    BOLD("大胆", "def", "atk"),
    DOCILE("坦率", null, null),
    RELAXED("悠闲", "def", "spe"),
    IMPISH("淘气", "def", "spa"),
    LAX("乐天", "def", "spd"),

    TIMID("胆小", "spe", "atk"),
    HASTY("急躁", "spe", "def"),
    SERIOUS("认真", null, null),
    JOLLY("爽朗", "spe", "spa"),
    NAIVE("天真", "spe", "spd"),

    MODEST("内敛", "spa", "atk"),
    MILD("慢吞吞", "spa", "def"),
    QUIET("冷静", "spa", "spe"),
    BASHFUL("害羞", null, null),
    RASH("马虎", "spa", "spd"),

    CALM("温和", "spd", "atk"),
    GENTLE("温顺", "spd", "def"),
    SASSY("自大", "spd", "spe"),
    CAREFUL("慎重", "spd", "spa"),
    QUIRKY("浮躁", null, null);

    public final String zhName;
    /** stat id to boost: hp/atk/def/spa/spd/spe (null means neutral) */
    public final String plus;
    /** stat id to reduce */
    public final String minus;

    Nature(String zhName, String plus, String minus) {
        this.zhName = zhName;
        this.plus = plus;
        this.minus = minus;
    }

    public static Nature fromId(String id) {
        if (id == null) return HARDY;
        try {
            return Nature.valueOf(id.trim().toUpperCase());
        } catch (Exception ignored) {
            return HARDY;
        }
    }

    public String id() {
        return name().toLowerCase();
    }

    public static Nature random(Random rnd) {
        List<Nature> all = Arrays.asList(values());
        return all.get(rnd.nextInt(all.size()));
    }
}
