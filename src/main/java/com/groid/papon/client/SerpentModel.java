package com.groid.papon.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.groid.papon.PaponMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A feathered serpent's head or body segment (tools/gen_serpents.py: Dungeon Now Loading's Fairkeeper models as vanilla
 * parts, assets/papon/serpent/&lt;kind&gt;_&lt;part&gt;.json), built into ModelParts exactly as the parts are written, and every
 * part findable by name.
 */
public final class SerpentModel {
    public final ModelPart root;
    private final Map<String, ModelPart> parts = new HashMap<>();
    private static final Map<String, SerpentModel> CACHE = new HashMap<>();

    private SerpentModel(JsonObject j) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        Map<String, String> parentOf = new HashMap<>();
        JsonObject ps = j.getAsJsonObject("parts");
        for (Map.Entry<String, JsonElement> e : ps.entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            String parent = p.get("parent").isJsonNull() ? null : p.get("parent").getAsString();
            PartDefinition into = parent == null ? mesh.getRoot() : defs.get(parent);
            if (into == null) continue;
            CubeListBuilder b = CubeListBuilder.create();
            for (JsonElement ce : p.getAsJsonArray("cubes")) {
                JsonObject c = ce.getAsJsonObject();
                JsonArray uv = c.getAsJsonArray("uv"), box = c.getAsJsonArray("box");
                b.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).mirror(c.get("mirror").getAsBoolean())
                        .addBox(box.get(0).getAsFloat(), box.get(1).getAsFloat(), box.get(2).getAsFloat(),
                                box.get(3).getAsFloat(), box.get(4).getAsFloat(), box.get(5).getAsFloat(),
                                new CubeDeformation(c.get("grow").getAsFloat()));
            }
            JsonArray pose = p.getAsJsonArray("pose");
            defs.put(e.getKey(), into.addOrReplaceChild(e.getKey(), b, PartPose.offsetAndRotation(pose.get(0).getAsFloat(),
                    pose.get(1).getAsFloat(), pose.get(2).getAsFloat(), pose.get(3).getAsFloat(), pose.get(4).getAsFloat(),
                    pose.get(5).getAsFloat())));
            parentOf.put(e.getKey(), parent);
        }
        JsonArray tex = j.getAsJsonArray("texture");
        root = LayerDefinition.create(mesh, tex.get(0).getAsInt(), tex.get(1).getAsInt()).bakeRoot();
        // find every part by name, walking down from the root by the parent chain
        for (String name : defs.keySet()) {
            java.util.ArrayDeque<String> chain = new java.util.ArrayDeque<>();
            for (String n = name; n != null; n = parentOf.get(n)) chain.push(n);
            ModelPart at = root;
            for (String n : chain) at = at.getChild(n);
            parts.put(name, at);
        }
    }

    public static SerpentModel get(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            try (var in = Minecraft.getInstance().getResourceManager().open(PaponMod.id("serpent/" + n + ".json"))) {
                return new SerpentModel(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception ex) {
                PaponMod.LOG.warn("[serpent] could not read serpent/{}.json", n, ex);
                return null;
            }
        });
    }

    @org.jetbrains.annotations.Nullable
    public ModelPart part(String name) { return parts.get(name); }

    public java.util.Set<String> names() { return parts.keySet(); }
}
