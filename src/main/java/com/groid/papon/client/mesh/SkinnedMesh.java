package com.groid.papon.client.mesh;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.groid.papon.PaponMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A triangle mesh skinned onto named GeckoLib bones, loaded from assets/papon/meshes/<name>.json
 * (written by tools/gen_edp_mesh.py and tools/gen_chugs_mesh.py). Positions are in rig pixels exactly
 * as a .geo.json would place a cube (right at -X, front at -Z, feet at y=0); each vertex follows up to
 * two bones.
 *
 * Loaded lazily on first render and dropped on resource reload.
 */
public final class SkinnedMesh {
    private static final Map<String, Optional<SkinnedMesh>> CACHE = new HashMap<>();

    public final ResourceLocation texture;
    public final String[] bones;
    /** GeckoLib model space, in blocks: x already mirrored the way GeckoLib bakes cubes. */
    public final float[] positions;
    public final float[] normals;
    public final float[] uvs;
    public final int[] indices;
    public final int[] joints;
    public final float[] weights;
    /** Corners per polygon in {@link #indices}: 4 (quads; a triangle repeats its last corner) or 3. */
    public final int corners;

    private SkinnedMesh(JsonObject json) {
        texture = new ResourceLocation(json.get("texture").getAsString());
        JsonArray b = json.getAsJsonArray("bones");
        bones = new String[b.size()];
        for (int i = 0; i < bones.length; i++) bones[i] = b.get(i).getAsString();
        positions = floats(json.getAsJsonArray("positions"));
        normals = floats(json.getAsJsonArray("normals"));
        uvs = floats(json.getAsJsonArray("uvs"));
        indices = ints(json.getAsJsonArray("indices"));
        joints = ints(json.getAsJsonArray("joints"));
        weights = floats(json.getAsJsonArray("weights"));
        corners = json.has("corners") ? json.get("corners").getAsInt() : 3;
        for (int i = 0; i < positions.length; i += 3) {
            positions[i] = -positions[i] / 16F;
            positions[i + 1] /= 16F;
            positions[i + 2] /= 16F;
            normals[i] = -normals[i];
        }
        for (int i = 1; i < uvs.length; i += 2) uvs[i] = 1F - uvs[i];   // OBJ-style v -> texture v
    }

    public int vertexCount() { return positions.length / 3; }

    @Nullable
    public static synchronized SkinnedMesh get(String name) {
        return CACHE.computeIfAbsent(name, SkinnedMesh::load).orElse(null);
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    private static Optional<SkinnedMesh> load(String name) {
        ResourceLocation id = PaponMod.id("meshes/" + name + ".json");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) {
            PaponMod.LOG.error("Mesh {} is missing", id);
            return Optional.empty();
        }
        try (Reader r = res.get().openAsReader()) {
            return Optional.of(new SkinnedMesh(JsonParser.parseReader(r).getAsJsonObject()));
        } catch (Exception e) {
            PaponMod.LOG.error("Mesh {} could not be read", id, e);
            return Optional.empty();
        }
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
        return out;
    }
}
