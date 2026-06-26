package win.pokedemo.bridge.client.model.render;

import com.google.gson.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class BedrockAnimations {
    private final Map<String, Clip> clips;

    private BedrockAnimations(Map<String, Clip> clips) { this.clips = clips; }

    static BedrockAnimations load(Identifier id) throws IOException {
        try (InputStream in = open(id); InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject anims = root.getAsJsonObject("animations");
            Map<String, Clip> clips = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject ao = e.getValue().getAsJsonObject();
                boolean loop = ao.has("loop") && ao.get("loop").getAsBoolean();
                float len = ao.has("animation_length") ? ao.get("animation_length").getAsFloat() : 2.0f;
                Map<String, BoneAnim> bones = new HashMap<>();
                JsonObject bo = ao.getAsJsonObject("bones");
                if (bo != null) {
                    for (Map.Entry<String, JsonElement> be : bo.entrySet()) {
                        if (!be.getValue().isJsonObject()) continue;
                        JsonObject bvo = be.getValue().getAsJsonObject();
                        AnimChannel rot = readChannel(bvo.get("rotation"));
                        AnimChannel pos = readChannel(bvo.get("position"));
                        if (rot != null || pos != null) bones.put(be.getKey(), new BoneAnim(rot, pos));
                    }
                }
                clips.put(e.getKey(), new Clip(loop, len, bones));
            }
            return new BedrockAnimations(clips);
        }
    }


    boolean hasClip(String clipName) {
        return clipName != null && clips.containsKey(clipName);
    }

    Set<String> clipNames() {
        return java.util.Collections.unmodifiableSet(clips.keySet());
    }

    String findClipByAlias(String alias) {
        if (alias == null || alias.isBlank()) return null;
        String wanted = alias.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        for (String clip : clips.keySet()) {
            String tail = clip.substring(clip.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            if (tail.equals(wanted)) return clip;
        }
        for (String clip : clips.keySet()) {
            String tail = clip.substring(clip.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            if (tail.contains(wanted)) return clip;
        }
        return null;
    }

    void apply(String clipName, BedrockGeoModel.LoadedModel model, float seconds) {
        Clip clip = clips.get(clipName);
        if (clip == null) return;
        float t = clip.loop && clip.length > 0 ? (seconds % clip.length) : Math.min(seconds, clip.length);
        for (Map.Entry<String, BoneAnim> e : clip.bones.entrySet()) {
            ModelPart part = model.findPart(e.getKey());
            if (part == null) continue;
            BoneAnim ba = e.getValue();
            if (ba.rot != null) {
                double[] rv = ba.rot.eval(t);
                part.pitch += (float)Math.toRadians(rv[0]);
                part.yaw += (float)Math.toRadians(rv[1]);
                part.roll += (float)Math.toRadians(rv[2]);
            }
            if (ba.pos != null) {
                double[] pv = ba.pos.eval(t);
                try {
                    part.originX += (float)pv[0];
                    part.originY += (float)pv[1];
                    part.originZ += (float)pv[2];
                } catch (Throwable ignored) {
                    // Some mappings expose these fields under different names. Rotation is the important part.
                }
            }
        }
    }

    private static AnimChannel readChannel(JsonElement el) {
        if (el == null) return null;
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() < 3) return null;
            Expr[] out = new Expr[3];
            for (int i=0;i<3;i++) out[i] = Expr.parse(arr.get(i));
            return t -> new double[]{out[0].eval(t), out[1].eval(t), out[2].eval(t)};
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            java.util.NavigableMap<Double, Expr[]> keyed = new java.util.TreeMap<>();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String k = e.getKey();
                if (k.equals("lerp_mode") || k.equals("pre") || k.equals("post")) continue;
                try {
                    double time = Double.parseDouble(k);
                    JsonElement ve = e.getValue();
                    Expr[] vec = null;
                    if (ve.isJsonArray()) {
                        JsonArray arr = ve.getAsJsonArray();
                        if (arr.size() >= 3) {
                            vec = new Expr[]{Expr.parse(arr.get(0)), Expr.parse(arr.get(1)), Expr.parse(arr.get(2))};
                        }
                    } else if (ve.isJsonObject()) {
                        JsonObject vo = ve.getAsJsonObject();
                        JsonElement post = vo.has("post") ? vo.get("post") : vo.has("vector") ? vo.get("vector") : null;
                        if (post != null && post.isJsonArray()) {
                            JsonArray arr = post.getAsJsonArray();
                            if (arr.size() >= 3) vec = new Expr[]{Expr.parse(arr.get(0)), Expr.parse(arr.get(1)), Expr.parse(arr.get(2))};
                        }
                    }
                    if (vec != null) keyed.put(time, vec);
                } catch (NumberFormatException ignored) {}
            }
            if (!keyed.isEmpty()) {
                return t -> {
                    Map.Entry<Double, Expr[]> floor = keyed.floorEntry((double)t);
                    Map.Entry<Double, Expr[]> ceil = keyed.ceilingEntry((double)t);
                    if (floor == null) floor = keyed.firstEntry();
                    if (ceil == null) ceil = keyed.lastEntry();
                    if (floor == ceil || Math.abs(ceil.getKey() - floor.getKey()) < 1.0e-6) {
                        Expr[] v = floor.getValue();
                        return new double[]{v[0].eval(t), v[1].eval(t), v[2].eval(t)};
                    }
                    double span = ceil.getKey() - floor.getKey();
                    double alpha = Math.max(0.0, Math.min(1.0, (t - floor.getKey()) / span));
                    Expr[] a = floor.getValue();
                    Expr[] b = ceil.getValue();
                    return new double[]{
                            a[0].eval(t) + (b[0].eval(t) - a[0].eval(t)) * alpha,
                            a[1].eval(t) + (b[1].eval(t) - a[1].eval(t)) * alpha,
                            a[2].eval(t) + (b[2].eval(t) - a[2].eval(t)) * alpha
                    };
                };
            }
            if (obj.has("post") || obj.has("pre")) {
                JsonElement base = obj.has("post") ? obj.get("post") : obj.get("pre");
                return readChannel(base);
            }
        }
        return null;
    }

    private static InputStream open(Identifier id) throws IOException {
        String cp = "assets/" + id.getNamespace() + "/" + id.getPath();
        InputStream in = BedrockAnimations.class.getClassLoader().getResourceAsStream(cp);
        if (in == null) throw new IOException("Missing animation resource: " + id);
        return in;
    }

    private record Clip(boolean loop, float length, Map<String, BoneAnim> bones) {}
    private record BoneAnim(AnimChannel rot, AnimChannel pos) {}

    interface AnimChannel { double[] eval(float t); }

    interface Expr { double eval(double t);
        static Expr parse(JsonElement el) {
            if (el == null) return x -> 0.0;
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                double v = el.getAsDouble(); return x -> v;
            }
            if (el.isJsonPrimitive()) return new Parser(el.getAsString()).parse();
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("post")) return parse(o.get("post"));
                if (o.has("pre")) return parse(o.get("pre"));
            }
            return x -> 0.0;
        }
    }

    private static final class Parser {
        private final String s; private int p;
        Parser(String s) { this.s = s.replace("q.anim_time", "t").replace("math.", "").replace(" ", ""); }
        Expr parse() { Expr e = expr(); return e == null ? x->0.0 : e; }
        private Expr expr() { Expr a = term(); while (peek('+')||peek('-')) { char op=s.charAt(p++); Expr b=term(); Expr aa=a; a = op=='+' ? x->aa.eval(x)+b.eval(x) : x->aa.eval(x)-b.eval(x);} return a; }
        private Expr term() { Expr a = factor(); while (peek('*')||peek('/')) { char op=s.charAt(p++); Expr b=factor(); Expr aa=a; a = op=='*' ? x->aa.eval(x)*b.eval(x) : x->aa.eval(x)/b.eval(x);} return a; }
        private Expr factor() {
            if (peek('+')) { p++; return factor(); }
            if (peek('-')) { p++; Expr e=factor(); return x->-e.eval(x); }
            if (peek('(')) { p++; Expr e=expr(); if (peek(')')) p++; return e; }
            if (isAlpha()) {
                String name = ident();
                if (name.equals("t")) return x->x;
                if (peek('(')) {
                    p++; Expr arg = expr(); if (peek(')')) p++;
                    return switch (name) {
                        case "sin" -> x->Math.sin(Math.toRadians(arg.eval(x)));
                        case "cos" -> x->Math.cos(Math.toRadians(arg.eval(x)));
                        default -> x->0.0;
                    };
                }
                return x->0.0;
            }
            return number();
        }
        private Expr number() {
            int st=p; while (p<s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p)=='.')) p++;
            double v = st==p ? 0.0 : Double.parseDouble(s.substring(st,p));
            return x->v;
        }
        private boolean peek(char c){ return p<s.length() && s.charAt(p)==c; }
        private boolean isAlpha(){ return p<s.length() && (Character.isAlphabetic(s.charAt(p)) || s.charAt(p)=='_'); }
        private String ident(){ int st=p; while (p<s.length() && (Character.isAlphabetic(s.charAt(p))||s.charAt(p)=='_'||Character.isDigit(s.charAt(p)))) p++; return s.substring(st,p); }
    }
}
