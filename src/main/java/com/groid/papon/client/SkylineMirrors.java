package com.groid.papon.client;

import com.groid.papon.PaponConfig;
import com.groid.papon.PaponMod;
import com.groid.papon.vehicle.CaravanEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * The Skyline's mirrors work (2026-10-07, the user: "make the mirrors (rearview, right side, left side) work like real
 * mirrors"): each glass shows the world as it would reflect it to whoever looks at it, live.
 *
 * A planar mirror seen from an eye E shows what an eye at E's reflection E' sees through the glass: so each mirror is the
 * world drawn again (LevelRenderer.renderLevel into its own framebuffer, the game's main target and camera swapped for it)
 * from E', looking along the glass's normal, its frustum the glass itself (off axis, its near plane the glass's plane:
 * nothing behind the mirror, the door or the windshield, gets in). The glass's corners then map to the picture's corners,
 * whatever the eye; move your head and the view in it shifts as in a real one. Your own body is drawn in it, the car's
 * flank in the side mirrors, the rear window in the rear-view one.
 *
 * CaravanRenderer asks ({@link #plan}) for each glass it draws in the main pass, with its corners from your eyes; after the
 * frame ({@link #render}, RenderTickEvent END) the nearest car's mirrors are drawn for the next one (one frame behind),
 * the rear-view every frame, the side ones in turn (config skyline.mirrorPasses passes a frame: 0 paints them as before).
 * Off under a shader pack (Oculus) and Fabulous graphics, whose pipelines are the screen's size, and for good if a pass
 * ever throws; then the glass is painted.
 */
public final class SkylineMirrors {
    /** The three glasses: their mesh groups (tools/gen_skyline.py), how often they matter, the picture's width at scale 1. */
    public enum Glass {
        REAR("mirror_rear", 2F, 448), LEFT("mirror_left", 1F, 224), RIGHT("mirror_right", 1F, 224);

        final String group;
        final float weight;
        final int width;

        Glass(String group, float weight, int width) {
            this.group = group;
            this.weight = weight;
            this.width = width;
        }

        public String group() { return group; }
    }

    /** Further than this from your eyes a mirror is painted. */
    private static final float RANGE = 14F;
    /** How far a mirror shows the world (blocks): culled past it, fogged into it (a world drawn whole again cost too much). */
    private static final float FAR = 64F;
    /** A picture older than this (frames) is not shown: it was of another moment. */
    private static final int FRESH = 12;

    private static final View[] VIEWS = new View[Glass.values().length];
    private static final Request[] ASKED = new Request[Glass.values().length];
    private static int frame, liveCar = -1, askedCar = -1;
    private static float askedDist;
    private static boolean rendering, broken;
    private static final MirrorCamera CAMERA = new MirrorCamera();
    private static Field mainTarget, mainCamera;
    private static Method irisApi, irisInUse;
    private static boolean irisLooked;

    static {
        for (Glass g : Glass.values()) {
            VIEWS[g.ordinal()] = new View(g);
            ASKED[g.ordinal()] = new Request();
        }
    }

    private SkylineMirrors() {}

    /** A pass's time on the CPU, averaged (ms; AutoShot logs it). */
    public static float passMillis;
    /** Passes drawn so far. */
    public static int passCount;

    /** Inside a mirror's pass: the glass is painted (it cannot show itself), your hands left off the wheel. */
    public static boolean rendering() { return rendering; }

    private static final class View {
        final Glass glass;
        final ResourceLocation id;
        TextureTarget target;
        int rendered = -1000, car = -1;

        View(Glass glass) {
            this.glass = glass;
            this.id = PaponMod.id("skyline_mirror/" + glass.name().toLowerCase(java.util.Locale.ROOT));
        }

        boolean fresh(int carId) { return target != null && car == carId && frame - rendered <= FRESH; }
    }

    /** One glass as asked for this frame: the eye behind it, in the world, and the glass's frame round it. */
    private static final class Request {
        boolean on;
        double ex, ey, ez;
        final Vector3f fwd = new Vector3f(), up = new Vector3f();
        float l, r, b, t, near;
    }

    /**
     * A glass about to be drawn in the main pass. {@code p}: its four corners from your eyes (the camera at the origin,
     * already turned by its rotation, as the renderer's vertices are), {@code n} its normal (out of the glass, toward you),
     * {@code carUp} the car's up. Returns the corners' coordinates in its live picture (u, v four times) or null: paint it.
     */
    public static float[] plan(CaravanEntity van, Glass glass, Vector3f[] p, Vector3f n, Vector3f carUp) {
        if (rendering || broken || passes() <= 0) return null;
        Vector3f c = new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]).mul(0.25F);
        float d = -c.dot(n);                                    // the eye's height over the glass's plane
        float dist = c.length();
        if (d < 0.01F || dist > RANGE) return null;            // seen from behind (its housing hides it) or far
        // the picture's up: the glass's edge nearest the car's up, square to the normal; its right across
        Vector3f e1 = new Vector3f(p[1]).sub(p[0]), e2 = new Vector3f(p[2]).sub(p[1]);
        Vector3f e = Math.abs(e1.dot(carUp)) / Math.max(e1.length(), 1e-6F) >= Math.abs(e2.dot(carUp)) / Math.max(e2.length(), 1e-6F) ? e1 : e2;
        if (e.dot(carUp) < 0) e.negate();
        Vector3f up = new Vector3f(e).sub(new Vector3f(n).mul(e.dot(n))).normalize();
        Vector3f right = new Vector3f(n).cross(up).normalize();
        Vector3f eye = new Vector3f(n).mul(-2 * d);             // your eye reflected in the glass's plane
        float[] xs = new float[4], ys = new float[4];
        float l = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = Float.MAX_VALUE, t = -Float.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            Vector3f q = new Vector3f(p[k]).sub(eye);
            xs[k] = q.dot(right);
            ys[k] = q.dot(up);
            l = Math.min(l, xs[k]); r = Math.max(r, xs[k]); b = Math.min(b, ys[k]); t = Math.max(t, ys[k]);
        }
        if (r - l < 1e-4F || t - b < 1e-4F) return null;
        float[] uv = new float[8];
        for (int k = 0; k < 4; k++) {
            uv[k * 2] = (xs[k] - l) / (r - l);
            uv[k * 2 + 1] = (ys[k] - b) / (t - b);              // a framebuffer's v runs up from its bottom row
        }
        if (onScreen(p)) ask(van, glass, dist, eye, n, up, l, r, b, t, d);
        return VIEWS[glass.ordinal()].fresh(van.getId()) && van.getId() == liveCar ? uv : null;
    }

    /** Whether any corner falls in the main view (a mirror off the screen is not drawn again). */

    private static boolean onScreen(Vector3f[] p) {
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Vector4f v = new Vector4f();
        for (Vector3f q : p) {
            proj.transform(v.set(q.x, q.y, q.z, 1F));
            if (v.w > 0.01F && Math.abs(v.x) <= v.w * 1.05F && Math.abs(v.y) <= v.w * 1.05F) return true;
        }
        return false;
    }

    /** Keeps the nearest car's requests (one car's mirrors are drawn: the one you sit in or stand by). */
    private static void ask(CaravanEntity van, Glass glass, float dist, Vector3f eye, Vector3f n, Vector3f up, float l, float r, float b, float t, float d) {
        if (askedCar != van.getId()) {
            if (askedCar != -1 && dist >= askedDist) return;
            for (Request q : ASKED) q.on = false;
            askedCar = van.getId();
            askedDist = dist;
        }
        askedDist = Math.min(askedDist, dist);
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Matrix3f inv = RenderSystem.getInverseViewRotationMatrix();       // view -> world (this frame's main view)
        Vector3f w = inv.transform(new Vector3f(eye));
        Request q = ASKED[glass.ordinal()];
        q.on = true;
        q.ex = cam.x + w.x; q.ey = cam.y + w.y; q.ez = cam.z + w.z;
        inv.transform(q.fwd.set(n)).normalize();
        inv.transform(q.up.set(up)).normalize();
        q.l = l; q.r = r; q.b = b; q.t = t; q.near = d;
    }

    private static int passes() { return PaponConfig.MIRROR_PASSES.get(); }

    /** After the frame: draws the asked-for mirrors of the nearest car, the stalest first (the rear-view counts double). */
    public static void render(float partial) {
        frame++;
        int car = askedCar;
        askedCar = -1;
        Minecraft mc = Minecraft.getInstance();
        if (car == -1 || broken || mc.level == null || mc.player == null || !supported()) {
            liveCar = -1;
            return;
        }
        int budget = Mth.clamp(passes(), 0, Glass.values().length);
        for (int pass = 0; pass < budget; pass++) {
            View best = null;
            float score = 0;
            for (View v : VIEWS) {
                if (!ASKED[v.glass.ordinal()].on) continue;
                float s = (v.car == car ? Math.min(frame - v.rendered, 1000) : 1000) * v.glass.weight;
                if (s > score) { score = s; best = v; }
            }
            if (best == null) break;
            Request q = ASKED[best.glass.ordinal()];
            q.on = false;
            long t0 = System.nanoTime();
            if (!draw(mc, best, q, partial)) return;
            passMillis += ((System.nanoTime() - t0) / 1e6F - passMillis) * 0.1F;
            passCount++;
            best.rendered = frame;
            best.car = car;
        }
        for (Request q : ASKED) q.on = false;
        liveCar = car;
    }

    /** Fabulous graphics and shader packs draw through screen-sized targets of their own: their mirrors stay painted. */
    private static boolean supported() {
        if (Minecraft.useShaderTransparency()) return false;
        try {
            if (!irisLooked) {
                irisLooked = true;
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance");
                irisInUse = api.getMethod("isShaderPackInUse");
            }
            if (irisApi != null && (Boolean) irisInUse.invoke(irisApi.invoke(null))) return false;
        } catch (Throwable ignored) {
            irisApi = null;
        }
        return true;
    }

    /** One mirror's pass: the level drawn from the reflected eye into its framebuffer. False if it failed (mirrors off). */
    private static boolean draw(Minecraft mc, View view, Request q, float partial) {
        float aspect = (q.r - q.l) / (q.t - q.b);
        float scale = PaponConfig.MIRROR_RESOLUTION.get().floatValue();
        int w = Mth.clamp((int) (view.glass.width * scale), 32, 2048), h = Mth.clamp((int) (w / aspect), 16, 2048);
        RenderTarget main = mc.getMainRenderTarget();
        Camera camera = mc.gameRenderer.getMainCamera();
        boolean swapped = false;
        RenderSystem.backupProjectionMatrix();
        Matrix3f invView = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            if (mainTarget == null) {
                mainTarget = ObfuscationReflectionHelper.findField(Minecraft.class, "f_91042_");
                mainCamera = ObfuscationReflectionHelper.findField(net.minecraft.client.renderer.GameRenderer.class, "f_109054_");
            }
            if (view.target == null || Math.abs(view.target.width - w) > 4 || Math.abs(view.target.height - h) > 4) {
                if (view.target == null) view.target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
                else view.target.resize(w, h, Minecraft.ON_OSX);
                view.target.setFilterMode(GL11.GL_LINEAR);
                mc.getTextureManager().register(view.id, new Picture(view));
            }
            Vec3 eye = new Vec3(q.ex, q.ey, q.ez);
            CAMERA.place(mc, eye, q.fwd, partial);
            PoseStack pose = new PoseStack();
            pose.mulPose(new Matrix4f().setLookAlong(q.fwd, q.up).getNormalizedRotation(new Quaternionf()));
            // the glass as the frustum's window, a hair past it (its own plane would clip the glass's edge in the picture)
            float n = Math.max(0.01F, q.near * 1.002F), k = n / q.near;
            Matrix4f proj = new Matrix4f().frustum(q.l * k, q.r * k, q.b * k, q.t * k, n, mc.gameRenderer.getDepthFar());
            mainTarget.set(mc, view.target);
            mainCamera.set(mc.gameRenderer, CAMERA);
            swapped = true;
            rendering = true;
            view.target.setClearColor(0, 0, 0, 1);
            view.target.clear(Minecraft.ON_OSX);
            view.target.bindWrite(true);
            RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setInverseViewRotationMatrix(new Matrix3f(pose.last().normal()).invert());
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            // culled with a frustum square round the view axis enclosing the glass: the off-axis one leaves the axis out (a
            // side mirror's), and vanilla backs its culling frustum up along that axis until it holds the camera's cube
            // (Frustum.offsetToFullyIncludeCameraCube): with the axis outside, forever (the game hung)
            // (forever too when it is so narrow that the cube only fits further back than its far plane reaches: the
            // rear-view glass is a thin strip; at least 30 degrees each way, 64 + 16 blocks deep, it fits 24 back)
            float tx = Mth.clamp(Math.max(Math.abs(q.l), Math.abs(q.r)) / q.near, 0.6F, 20F), ty = Mth.clamp(Math.max(Math.abs(q.b), Math.abs(q.t)) / q.near, 0.6F, 20F);
            Matrix4f cull = new Matrix4f().frustum(-tx * 0.05F, tx * 0.05F, -ty * 0.05F, ty * 0.05F, 0.05F, Math.min(FAR + 16F, mc.gameRenderer.getDepthFar()));
            mc.levelRenderer.prepareCullFrustum(pose, eye, cull);
            mc.levelRenderer.renderLevel(pose, partial, Util.getNanos(), false, CAMERA, mc.gameRenderer, mc.gameRenderer.lightTexture(), proj);
            // the level leaves its alpha as it pleases (the sky's clear is 0): the picture is opaque
            view.target.bindWrite(true);
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0, 0, 0, 1);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.colorMask(true, true, true, true);
            return true;
        } catch (Throwable e) {
            broken = true;
            liveCar = -1;
            PaponMod.LOG.error("Skyline mirrors failed; they are painted from now on", e);
            return false;
        } finally {
            rendering = false;
            if (swapped) {
                try {
                    mainTarget.set(mc, main);
                    mainCamera.set(mc.gameRenderer, camera);
                } catch (IllegalAccessException e) {
                    PaponMod.LOG.error("Skyline mirrors could not give the main target back", e);
                }
            }
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setInverseViewRotationMatrix(invView);
            main.bindWrite(true);
        }
    }

    /** Inside a mirror's pass the terrain fog closes in at {@link #FAR}, where its culling stops (ClientEvents.onRenderFog). */
    static void fog(net.minecraftforge.client.event.ViewportEvent.RenderFog event) {
        if (!rendering || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) return;
        float far = Math.min(event.getFarPlaneDistance(), FAR);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.55F));
        event.setCanceled(true);
    }

    /** The mirror's picture as a texture the render types can name: the framebuffer's colour, filtered smooth. */
    private static final class Picture extends AbstractTexture {
        private final View view;

        Picture(View view) { this.view = view; }

        @Override public int getId() { return view.target == null ? 0 : view.target.getColorTextureId(); }
        @Override public void load(ResourceManager manager) {}
        @Override public void releaseId() {}
        @Override public void setFilter(boolean blur, boolean mipmap) { super.setFilter(true, false); }
    }

    /** The texture a glass's live picture is drawn with. */
    public static ResourceLocation texture(Glass glass) { return VIEWS[glass.ordinal()].id; }

    /** A camera at the reflected eye: detached (your own body is drawn), turned to the glass's normal. */
    private static final class MirrorCamera extends Camera {
        void place(Minecraft mc, Vec3 eye, Vector3f fwd, float partial) {
            var who = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
            setup(mc.level, who, true, false, partial);
            setPosition(eye);
            setRotation((float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z)), (float) Math.toDegrees(Math.asin(Mth.clamp(-fwd.y, -1F, 1F))));
        }
    }
}
