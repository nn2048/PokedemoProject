package win.pokedemo.bridge.client.model.render;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.minecraft.client.model.*;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal static Bedrock geo -> ModelPart converter.
 * Purposefully ignores animation for now and focuses on getting a correct static pose.
 */
public final class BedrockGeoModel {
    private static final Gson GSON = new Gson();

    public static LoadedModel load(Identifier resource) throws IOException {
        String cp = "assets/" + resource.getNamespace() + "/" + resource.getPath();
        try (InputStream in = BedrockGeoModel.class.getClassLoader().getResourceAsStream(cp)) {
            if (in == null) throw new IOException("Missing geo resource: " + resource);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                GeoRoot root = GSON.fromJson(reader, GeoRoot.class);
                if (root == null || root.geometry == null || root.geometry.isEmpty()) {
                    throw new IOException("Invalid geo: no minecraft:geometry entries in " + resource);
                }
                Geometry geo = root.geometry.get(0);
                return buildLoaded(geo);
            }
        }
    }

    private static LoadedModel buildLoaded(Geometry geo) {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        Map<String, List<Bone>> children = new HashMap<>();
        List<Bone> allBones = geo.bones == null ? List.of() : geo.bones;
        for (Bone bone : allBones) {
            children.computeIfAbsent(bone.parent == null ? "__ROOT__" : bone.parent, k -> new ArrayList<>()).add(bone);
        }
        float[] rootPivot = new float[]{0.0f, 0.0f, 0.0f};
        Map<String, List<String>> bonePaths = new HashMap<>();
        for (Bone bone : children.getOrDefault("__ROOT__", List.of())) {
            addBone(root, bone, rootPivot, children, List.of(), bonePaths);
        }

        int tw = geo.description != null && geo.description.textureWidth > 0 ? geo.description.textureWidth : 128;
        int th = geo.description != null && geo.description.textureHeight > 0 ? geo.description.textureHeight : 64;
        float visibleW = geo.description != null && geo.description.visibleBoundsWidth > 0 ? geo.description.visibleBoundsWidth : 2.0f;
        float visibleH = geo.description != null && geo.description.visibleBoundsHeight > 0 ? geo.description.visibleBoundsHeight : 2.0f;
        float[] visibleOffset = geo.description != null && geo.description.visibleBoundsOffset != null && geo.description.visibleBoundsOffset.size() >= 3
                ? vec3(geo.description.visibleBoundsOffset)
                : new float[]{0.0f, 0.0f, 0.0f};
        TexturedModelData tmd = TexturedModelData.of(data, tw, th);
        ModelPart rootModel = tmd.createModel();
        return new LoadedModel(rootModel, tw, th, visibleW, visibleH, visibleOffset[0], visibleOffset[1], visibleOffset[2], bonePaths);
    }

    private static void addBone(ModelPartData parentPart, Bone bone, float[] parentPivot, Map<String, List<Bone>> children, List<String> parentPath, Map<String, List<String>> bonePaths) {
        float[] pivot = vec3(bone.pivot);
        float relPivotX = pivot[0] - parentPivot[0];
        float relPivotY = parentPivot[1] - pivot[1];
        float relPivotZ = pivot[2] - parentPivot[2];

        float[] rotDeg = vec3(bone.rotation);
        float pitch = (float) Math.toRadians(rotDeg[0]);
        float yaw = (float) Math.toRadians(rotDeg[1]);
        float roll = (float) Math.toRadians(rotDeg[2]);

        String boneName = safeName(bone.name);
        List<String> thisPath = new ArrayList<>(parentPath);
        thisPath.add(boneName);
        bonePaths.put(boneName, thisPath);
        ModelPartData thisPart = parentPart.addChild(
                boneName,
                ModelPartBuilder.create(),
                ModelTransform.of(relPivotX, relPivotY, relPivotZ, pitch, yaw, roll)
        );

        if (bone.cubes != null) {
            int cubeIndex = 0;
            for (Cube cube : bone.cubes) {
                addCube(thisPart, pivot, cube, safeName(bone.name) + "_cube_" + cubeIndex++);
            }
        }

        for (Bone child : children.getOrDefault(bone.name, List.of())) {
            addBone(thisPart, child, pivot, children, thisPath, bonePaths);
        }
    }

    private static void addCube(ModelPartData bonePart, float[] bonePivot, Cube cube, String partName) {
        float[] origin = vec3(cube.origin);
        float[] size = vec3(cube.size);
        float inflate = cube.inflate == null ? 0.0f : cube.inflate.floatValue();
        int[] uv = cubeUv(cube.uv);
        float[] cubePivotAbs = cube.pivot != null && cube.pivot.size() >= 3 ? vec3(cube.pivot) : origin;
        float relPivotX = cubePivotAbs[0] - bonePivot[0];
        float relPivotY = bonePivot[1] - cubePivotAbs[1];
        float relPivotZ = cubePivotAbs[2] - bonePivot[2];

        float localX = origin[0] - cubePivotAbs[0];
        float localY = cubePivotAbs[1] - origin[1] - size[1];
        float localZ = origin[2] - cubePivotAbs[2];

        float[] cubeRotDeg = vec3(cube.rotation);
        float pitch = (float) Math.toRadians(cubeRotDeg[0]);
        float yaw = (float) Math.toRadians(cubeRotDeg[1]);
        float roll = (float) Math.toRadians(cubeRotDeg[2]);

        bonePart.addChild(
                safeName(partName),
                ModelPartBuilder.create().uv(uv[0], uv[1]).cuboid(localX, localY, localZ, size[0], size[1], size[2], new Dilation(inflate)),
                ModelTransform.of(relPivotX, relPivotY, relPivotZ, pitch, yaw, roll)
        );
    }

    private static int[] cubeUv(Object uvObj) {
        if (uvObj instanceof List<?> list && list.size() >= 2 && list.get(0) instanceof Number n0 && list.get(1) instanceof Number n1) {
            return new int[]{n0.intValue(), n1.intValue()};
        }
        return new int[]{0, 0};
    }

    private static float[] vec3(List<Number> list) {
        if (list == null || list.size() < 3) return new float[]{0.0f, 0.0f, 0.0f};
        return new float[]{list.get(0).floatValue(), list.get(1).floatValue(), list.get(2).floatValue()};
    }

    private static String safeName(String s) {
        return s == null || s.isBlank() ? "bone" : s;
    }

    private static ModelPart resolvePath(ModelPart root, List<String> path) {
        ModelPart current = root;
        try {
            for (String segment : path) {
                current = current.getChild(segment);
            }
            return current;
        } catch (Exception ignored) {
            return null;
        }
    }

    public record LoadedModel(ModelPart root, int texW, int texH, float visibleW, float visibleH, float visibleOffsetX, float visibleOffsetY, float visibleOffsetZ,
                              Map<String, List<String>> bonePaths) {
        public float groundOffsetY() {
            return visibleOffsetY - (visibleH * 0.5f);
        }

        public ModelPart renderRoot(String rootBone) {
            if (rootBone == null || rootBone.isBlank()) return root;
            try {
                return root.getChild(rootBone);
            } catch (Exception ignored) {
                return root;
            }
        }

        public ModelPart findPart(String bone) {
            List<String> path = bonePaths.get(bone);
            return path == null ? null : resolvePath(root, path);
        }

        public void resetPose() {
            root.traverse().forEach(ModelPart::resetTransform);
            root.resetTransform();
        }
    }

    private static final class GeoRoot {
        @SerializedName("minecraft:geometry")
        List<Geometry> geometry;
    }

    private static final class Geometry {
        Description description;
        List<Bone> bones;
    }

    private static final class Description {
        @SerializedName("texture_width") int textureWidth;
        @SerializedName("texture_height") int textureHeight;
        @SerializedName("visible_bounds_width") float visibleBoundsWidth;
        @SerializedName("visible_bounds_height") float visibleBoundsHeight;
        @SerializedName("visible_bounds_offset") List<Number> visibleBoundsOffset;
    }

    private static final class Bone {
        String name;
        String parent;
        List<Number> pivot;
        List<Number> rotation;
        List<Cube> cubes;
    }

    private static final class Cube {
        List<Number> origin;
        List<Number> size;
        List<Number> pivot;
        List<Number> rotation;
        Object uv;
        Float inflate;
    }
}
