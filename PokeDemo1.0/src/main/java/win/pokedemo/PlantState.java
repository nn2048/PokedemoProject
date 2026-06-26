package win.pokedemo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Instrument;
import org.bukkit.Note;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps our plant growth stages onto NoteBlock (instrument + note) states that the resource pack overrides.
 *
 * Why this exists:
 * - The client-side resource pack replaces *specific* NoteBlock states with our berry/apricorn block models.
 * - If we set the wrong instrument/note, Minecraft will render a normal Note Block.
 */
public final class PlantState {

    private PlantState() {}

    /** Model -> (instrument,note) mapping loaded from /plant_note_variants.json (bundled in the plugin jar). */
    private static final Map<String, State> MODEL_TO_STATE = new HashMap<>();

    private static final class State {
        final Instrument instrument;
        final Note note;
        State(Instrument instrument, Note note) {
            this.instrument = instrument;
            this.note = note;
        }
    }

    /**
     * Load the model->variant mapping once.
     * The json file format is: { "pokedemo:block/berry/oran_young": "instrument=basedrum,note=6,powered=false", ... }
     */
    private static void ensureLoaded() {
        if (!MODEL_TO_STATE.isEmpty()) return;

        try (InputStream in = PlantState.class.getResourceAsStream("/plant_note_variants.json")) {
            if (in == null) return;
            Type t = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> map = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), t);
            if (map == null) return;

            for (Map.Entry<String, String> e : map.entrySet()) {
                String model = e.getKey();
                String variant = e.getValue();
                if (model == null || variant == null) continue;
                State s = parseVariant(variant);
                if (s != null) MODEL_TO_STATE.put(model, s);
            }
        } catch (Throwable ignored) {
            // fail silently; plants will just look like note blocks.
        }
    }

    private static State parseVariant(String variant) {
        // instrument=basedrum,note=1,powered=false
        String instName = null;
        Integer note = null;
        for (String part : variant.split(",")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String k = part.substring(0, idx).trim();
            String v = part.substring(idx + 1).trim();
            if ("instrument".equalsIgnoreCase(k)) instName = v;
            if ("note".equalsIgnoreCase(k)) {
                try { note = Integer.parseInt(v); } catch (NumberFormatException ignored) {}
            }
        }
        if (instName == null || note == null) return null;

        Instrument instrument = resolveInstrument(instName);
        if (instrument == null) return null;
        return new State(instrument, new Note(note));
    }

    /**
     * Resource pack uses Mojang blockstate strings (e.g. "basedrum", "bit").
     * Bukkit's Instrument enum names differ between versions; we resolve by name with fallbacks.
     */
    private static Instrument resolveInstrument(String blockstateName) {
        if (blockstateName == null) return null;
        // Mojang blockstate uses lowercase tokens like "basedrum", "snare", "harp".
        // Bukkit/Paper's Instrument enum naming has changed across versions (underscores, synonyms).
        // We resolve by normalizing both sides and matching.
        String want = blockstateName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (want.isEmpty()) return null;

        Instrument best = null;
        for (Instrument inst : Instrument.values()) {
            String have = inst.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (have.equals(want)) {
                return inst;
            }
            // Synonyms / historical renames
            if (want.equals("basedrum") && have.equals("bassdrum")) best = inst;
            if (want.equals("harp") && (have.equals("piano") || have.equals("harp"))) best = inst;
        }
        return best;
    }

    /**
     * Returns the note/instrument that should be applied for a plant at the given stage.
     *
     * Berry stages: 0 sprout, 1 young, 2 mature
     * Apricorn stages: 0 stage0, 1 stage1, 2 stage2, 3 color_mature
     */
    public static Resolved forPlant(PlantManager.PlantKind kind, String id, int stage) {
        ensureLoaded();

        String model;
        if (kind == PlantManager.PlantKind.BERRY) {
            // Stage 0 is a shared sprout model.
            if (stage <= 0) model = "pokedemo:block/berry/sprout";
            else if (stage == 1) model = "pokedemo:block/berry/" + id + "_young";
            else model = "pokedemo:block/berry/" + id + "_mature";
        } else {
            // Apricorn
            if (stage <= 0) model = "pokedemo:block/apricorn/stage0";
            else if (stage == 1) model = "pokedemo:block/apricorn/stage1";
            else if (stage == 2) model = "pokedemo:block/apricorn/stage2";
            else model = "pokedemo:block/apricorn/" + id + "_mature";
        }

        State s = MODEL_TO_STATE.get(model);

        // Resource packs often only provide the "sprout" (berries) / "stage0" (apricorns) override.
        // If a later stage mapping is missing, fall back to the earliest stage model rather than
        // rendering a vanilla note block.
        if (s == null) {
            if (kind == PlantManager.PlantKind.BERRY) {
                s = MODEL_TO_STATE.get("pokedemo:block/berry/sprout");
            } else {
                s = MODEL_TO_STATE.get("pokedemo:block/apricorn/stage0");
            }
        }

        if (s == null) {
            // Last resort: vanilla note block.
            Instrument fallback = resolveInstrument("HARP");
            if (fallback == null) fallback = resolveInstrument("PIANO");
            return new Resolved(fallback, new Note(0));
        }

        return new Resolved(s.instrument, s.note);
    }

    /** Simple value holder used by PlantManager. */
    public static final class Resolved {
        public final Instrument instrument;
        public final Note note;
        public Resolved(Instrument instrument, Note note) {
            this.instrument = instrument;
            this.note = note;
        }
    }
}
