package win.pokedemo;

import org.bukkit.Instrument;
import org.bukkit.Note;

import java.util.Random;

/**
 * Encodes evolution stone ores into NoteBlock states.
 *
 * NOTE: These are purely server-side identifiers. The resource pack can map each
 * (instrument + note) combo to a custom ore model/texture.
 */
public enum EvoOreType {
    FIRE("fire_stone", Instrument.BANJO, Note.natural(0, Note.Tone.C)),
    WATER("water_stone", Instrument.BIT, Note.natural(0, Note.Tone.D)),
    THUNDER("thunder_stone", Instrument.BELL, Note.natural(0, Note.Tone.E)),
    LEAF("leaf_stone", Instrument.FLUTE, Note.natural(0, Note.Tone.F)),
    MOON("moon_stone", Instrument.CHIME, Note.natural(0, Note.Tone.G));

    public final String itemId;
    public final Instrument instrument;
    public final Note note;

    EvoOreType(String itemId, Instrument instrument, Note note) {
        this.itemId = itemId;
        this.instrument = instrument;
        this.note = note;
    }

    public static EvoOreType fromState(Instrument instrument, Note note) {
        for (EvoOreType t : values()) {
            if (t.instrument == instrument && t.note.equals(note)) return t;
        }
        return null;
    }

    public static EvoOreType random(Random r) {
        EvoOreType[] v = values();
        return v[r.nextInt(v.length)];
    }
}
