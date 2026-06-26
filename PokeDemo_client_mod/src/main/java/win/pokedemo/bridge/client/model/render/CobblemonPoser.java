package win.pokedemo.bridge.client.model.render;

import com.google.gson.*;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CobblemonPoser {
    private static final Pattern BEDROCK = Pattern.compile("bedrock\\('([^']+)'\\)");
    private static final Pattern BEDROCK_STATEFUL = Pattern.compile("bedrockStateful\\('([^']+)'\\s*,\\s*'([^']+)'\\)");
    private static final Pattern BIPED_WALK = Pattern.compile("bipedWalk\\(([^,]+),([^,]+),([^,]+),([^\\)]+)\\)");
    private static final Pattern QUADRUPED_WALK = Pattern.compile("quadrupedWalk\\(([^,]+),([^,]+),([^,]+),([^,]+),([^,]+),([^\\)]+)\\)");
    private static final Pattern WING_FLAP = Pattern.compile("wingFlap\\(([^,]+),([^,]+),([^,]+),([^,]+),([^,]+),([^\\)]+)\\)");

    final String rootBone;
    final float profileScale;
    final float[] profileTranslation;
    final PoseDef standingPose;
    final PoseDef walkingPose;
    final PoseDef runningPose;
    final PoseDef battleStandingPose;
    final PoseDef sleepPose;
    final PoseDef floatPose;
    final PoseDef swimPose;
    final PoseDef surfaceFloatPose;
    final PoseDef surfaceSwimPose;
    final PoseDef hoverPose;
    final PoseDef flyPose;
    final String cryClip;
    final String recoilClip;
    final String physicalClip;
    final String specialClip;
    final String statusClip;
    final String voltTackleClip;
    final String battleCryClip;
    final String battleRecoilClip;
    final String wakeClip;
    final String faintClip;
    final String hitClip;
    final String sendoutClip;
    final String withdrawClip;

    private CobblemonPoser(String rootBone, float profileScale, float[] profileTranslation,
                           PoseDef standingPose, PoseDef walkingPose, PoseDef runningPose,
                           PoseDef battleStandingPose, PoseDef sleepPose,
                           PoseDef floatPose, PoseDef swimPose,
                           PoseDef surfaceFloatPose, PoseDef surfaceSwimPose,
                           PoseDef hoverPose, PoseDef flyPose,
                           String cryClip, String recoilClip, String physicalClip,
                           String specialClip, String statusClip, String voltTackleClip,
                           String battleCryClip, String battleRecoilClip,
                           String wakeClip, String faintClip, String hitClip,
                           String sendoutClip, String withdrawClip) {
        this.rootBone = rootBone;
        this.profileScale = profileScale;
        this.profileTranslation = profileTranslation;
        this.standingPose = standingPose;
        this.walkingPose = walkingPose;
        this.runningPose = runningPose;
        this.battleStandingPose = battleStandingPose;
        this.sleepPose = sleepPose;
        this.floatPose = floatPose;
        this.swimPose = swimPose;
        this.surfaceFloatPose = surfaceFloatPose;
        this.surfaceSwimPose = surfaceSwimPose;
        this.hoverPose = hoverPose;
        this.flyPose = flyPose;
        this.cryClip = cryClip;
        this.recoilClip = recoilClip;
        this.physicalClip = physicalClip;
        this.specialClip = specialClip;
        this.statusClip = statusClip;
        this.voltTackleClip = voltTackleClip;
        this.battleCryClip = battleCryClip;
        this.battleRecoilClip = battleRecoilClip;
        this.wakeClip = wakeClip;
        this.faintClip = faintClip;
        this.hitClip = hitClip;
        this.sendoutClip = sendoutClip;
        this.withdrawClip = withdrawClip;
    }

    static CobblemonPoser load(Identifier poserId, BedrockAnimations animations, CobblemonSpeciesDisplayConfig sizes) throws IOException {
        JsonObject root = null;
        try (InputStream in = openIfExists(poserId)) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    root = JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        return load(root, animations, sizes);
    }

    static CobblemonPoser load(JsonObject root, BedrockAnimations animations, CobblemonSpeciesDisplayConfig sizes) {
        String rootBone = root != null && root.has("rootBone") ? root.get("rootBone").getAsString() : sizes.rootBone;
        float profileScale = root != null && root.has("profileScale") ? root.get("profileScale").getAsFloat() : sizes.profileScale;
        float[] profileTranslation = root != null ? vec3(root.getAsJsonArray("profileTranslation")) : sizes.profileTranslation.clone();
        JsonObject poses = root == null ? null : root.getAsJsonObject("poses");
        JsonObject rootAnims = root == null ? null : root.getAsJsonObject("animations");

        PoseDef standing = choosePose(animations, poseFrom(poses, "standing", "portrait", "ground_idle", "idle"),
                aliases(animations, "ground_idle", "idle", "land_idle", "pose"));
        PoseDef walking = choosePose(animations, poseFrom(poses, "walking", "walk", "ground_walk", "move", "moving"),
                aliases(animations, "ground_walk", "walk", "moving", "move", "ground_walkfast"),
                standing);
        PoseDef running = choosePose(animations, poseFrom(poses, "running", "run", "ground_run", "sprint"),
                aliases(animations, "run", "sprint", "ground_run", "ground_walkfast"),
                walking, standing);
        PoseDef battleStanding = choosePose(animations, poseFrom(poses, "battle-standing", "battle_idle", "battle_standing"),
                aliases(animations, "battle_idle", "battle_standing", "battle_idle_quirk"),
                standing);
        PoseDef sleep = choosePose(animations, poseFrom(poses, "sleep", "ground-sleep", "wild_sleep", "battle-sleep", "air-sleep", "water-sleep"),
                aliases(animations, "sleep", "ground_sleep", "air_sleep", "water_sleep", "surfacewater_sleep"),
                standing);
        PoseDef floatPose = choosePose(animations, poseFrom(poses, "float", "water_idle", "surface_idle", "surface-float", "surfacewater-float"),
                aliases(animations, "water_idle", "surfacewater_idle", "surface_idle", "float"),
                standing);
        PoseDef swimPose = choosePose(animations, poseFrom(poses, "swim", "water-swim", "water_swim", "surface_swim", "surfacewater-swim"),
                aliases(animations, "water_swim", "water_swimfast", "swim", "surfacewater_swim", "surfacewater_swimfast"),
                walking, standing);
        PoseDef surfaceFloat = choosePose(animations, poseFrom(poses, "surface-float", "surfacewater-float", "surface_idle", "mount-surfacewater-float"),
                aliases(animations, "surfacewater_idle", "surface_idle", "float"),
                floatPose, standing);
        PoseDef surfaceSwim = choosePose(animations, poseFrom(poses, "surface-swim", "surfacewater-swim", "surface_swim", "mount-surface-swim", "mount-surfacewater-swim"),
                aliases(animations, "surfacewater_swim", "surfacewater_swimfast", "surface_swim", "swim"),
                swimPose, walking, standing);
        PoseDef hover = choosePose(animations, poseFrom(poses, "hover", "mount-hover", "air_idle"),
                aliases(animations, "air_idle", "hover", "fly_idle", "flying_idle"),
                standing);
        PoseDef fly = choosePose(animations, poseFrom(poses, "fly", "glide", "in-air", "mount-fly", "battle-flying", "air_fly"),
                aliases(animations, "air_fly", "fly", "glide", "flying", "air_move"),
                hover, walking, standing);

        String cry = firstClip(clipFromAnimationMap(rootAnims, "cry"), aliases(animations, "cry"));
        String recoil = firstClip(clipFromAnimationMap(rootAnims, "recoil"), aliases(animations, "recoil", "hurt", "hit", "damage"));
        String physical = firstClip(clipFromAnimationMap(rootAnims, "physical"), aliases(animations, "physical", "attack_physical", "attack", "melee"));
        String special = firstClip(clipFromAnimationMap(rootAnims, "special"), aliases(animations, "special", "attack_special", "ranged", "beam"));
        String status = firstClip(clipFromAnimationMap(rootAnims, "status"), aliases(animations, "status", "attack_status", "cast", "support"));
        String voltTackle = firstClip(clipFromAnimationMap(rootAnims, "volttackle"), aliases(animations, "volttackle", "volt_tackle"));
        String battleCry = firstClip(namedClipFromPose(poses, "battle-standing", "cry"), cry);
        String battleRecoil = firstClip(namedClipFromPose(poses, "battle-standing", "recoil"), recoil);
        String wake = firstClip(clipFromAnimationMap(rootAnims, "wake"), aliases(animations, "wake", "wakeup", "rise"), cry);
        String faint = firstClip(clipFromAnimationMap(rootAnims, "faint"), aliases(animations, "faint", "death", "knockout", "defeat", "downed"));
        String hit = firstClip(clipFromAnimationMap(rootAnims, "hit"), aliases(animations, "hit", "hurt", "damage", "recoil"), recoil);
        String sendout = firstClip(clipFromAnimationMap(rootAnims, "sendout"), aliases(animations, "sendout", "summon", "emerge", "appear"), cry);
        String withdraw = firstClip(clipFromAnimationMap(rootAnims, "withdraw"), aliases(animations, "withdraw", "return", "recall", "despawn"));

        return new CobblemonPoser(rootBone, profileScale, profileTranslation,
                standing, walking, running, battleStanding, sleep, floatPose, swimPose,
                surfaceFloat, surfaceSwim, hover, fly,
                cry, recoil, physical, special, status, voltTackle, battleCry, battleRecoil,
                wake, faint, hit, sendout, withdraw);
    }

    private static PoseDef choosePose(BedrockAnimations animations, PoseDef primary, String clipFallback, PoseDef... poseFallbacks) {
        if (isUsable(primary)) return primary;
        if (clipFallback != null) return new PoseDef(clipFallback, List.of());
        for (PoseDef poseFallback : poseFallbacks) {
            if (isUsable(poseFallback)) return poseFallback;
        }
        return PoseDef.EMPTY;
    }

    private static boolean isUsable(PoseDef def) {
        return def != null && (def.clip != null || (def.procedures != null && !def.procedures.isEmpty()));
    }

    private static String aliases(BedrockAnimations animations, String... aliases) {
        if (animations == null) return null;
        for (String alias : aliases) {
            String found = animations.findClipByAlias(alias);
            if (found != null) return found;
        }
        return null;
    }

    private static String firstClip(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static PoseDef poseFrom(JsonObject poses, String... poseNames) {
        if (poses == null) return null;
        for (String poseName : poseNames) {
            if (!poses.has(poseName) || !poses.get(poseName).isJsonObject()) continue;
            JsonObject pose = poses.getAsJsonObject(poseName);
            JsonArray anims = pose.getAsJsonArray("animations");
            String clip = null;
            List<ProcAnim> procs = new ArrayList<>();
            if (anims != null) {
                for (JsonElement e : anims) {
                    if (!e.isJsonPrimitive()) continue;
                    String s = e.getAsString();
                    if (clip == null) clip = clipFromExpression(s);
                    ProcAnim proc = procFromExpression(s);
                    if (proc != null) procs.add(proc);
                }
            }
            return new PoseDef(clip, procs);
        }
        return null;
    }

    private static String clipFromExpression(String s) {
        Matcher m = BEDROCK_STATEFUL.matcher(s);
        if (m.find()) return "animation." + m.group(1).toLowerCase(Locale.ROOT) + "." + m.group(2);
        m = BEDROCK.matcher(s);
        if (m.find()) return "animation." + m.group(1).toLowerCase(Locale.ROOT) + "." + m.group(2);
        return null;
    }

    private static ProcAnim procFromExpression(String s) {
        Matcher m = BIPED_WALK.matcher(s);
        if (m.find()) {
            return ProcAnim.biped(Float.parseFloat(m.group(1)), Float.parseFloat(m.group(2)), m.group(3), m.group(4));
        }
        m = QUADRUPED_WALK.matcher(s);
        if (m.find()) {
            return ProcAnim.quadruped(Float.parseFloat(m.group(1)), Float.parseFloat(m.group(2)),
                    m.group(3), m.group(4), m.group(5), m.group(6));
        }
        m = WING_FLAP.matcher(s);
        if (m.find()) {
            return ProcAnim.wing(Float.parseFloat(m.group(1)), Float.parseFloat(m.group(2)), Float.parseFloat(m.group(3)),
                    m.group(4).charAt(0), m.group(5), m.group(6));
        }
        return null;
    }

    private static String namedClipFromPose(JsonObject poses, String poseName, String animName) {
        if (poses == null || !poses.has(poseName) || !poses.get(poseName).isJsonObject()) return null;
        JsonObject pose = poses.getAsJsonObject(poseName);
        JsonObject named = pose.getAsJsonObject("namedAnimations");
        return clipFromAnimationMap(named, animName);
    }

    private static String clipFromAnimationMap(JsonObject obj, String name) {
        if (obj == null || name == null || !obj.has(name)) return null;
        JsonElement e = obj.get(name);
        if (!e.isJsonPrimitive()) return null;
        return clipFromExpression(e.getAsString());
    }

    private static float[] vec3(JsonArray arr) {
        float[] out = new float[]{0,0,0};
        if (arr == null) return out;
        for (int i = 0; i < Math.min(3, arr.size()); i++) out[i] = arr.get(i).getAsFloat();
        return out;
    }

    private static InputStream openIfExists(Identifier id) throws IOException {
        String cp = "assets/" + id.getNamespace() + "/" + id.getPath();
        return CobblemonPoser.class.getClassLoader().getResourceAsStream(cp);
    }

    static final class PoseDef {
        static final PoseDef EMPTY = new PoseDef(null, List.of());
        final String clip;
        final List<ProcAnim> procedures;
        PoseDef(String clip, List<ProcAnim> procedures) {
            this.clip = clip;
            this.procedures = procedures == null ? List.of() : List.copyOf(procedures);
        }
    }

    static final class ProcAnim {
        enum Kind { BIPED, QUADRUPED, WING }
        final Kind kind;
        final float speed;
        final float amplitude;
        final float phase;
        final char axis;
        final String[] bones;

        private ProcAnim(Kind kind, float speed, float amplitude, float phase, char axis, String... bones) {
            this.kind = kind;
            this.speed = speed;
            this.amplitude = amplitude;
            this.phase = phase;
            this.axis = axis;
            this.bones = bones;
        }
        static ProcAnim biped(float speed, float amplitude, String left, String right) {
            return new ProcAnim(Kind.BIPED, speed, amplitude, 0.0f, 'x', left, right);
        }
        static ProcAnim quadruped(float speed, float amplitude, String fl, String fr, String bl, String br) {
            return new ProcAnim(Kind.QUADRUPED, speed, amplitude, 0.0f, 'x', fl, fr, bl, br);
        }
        static ProcAnim wing(float speed, float amplitude, float phase, char axis, String left, String right) {
            return new ProcAnim(Kind.WING, speed, amplitude, phase, axis, left, right);
        }
    }
}
