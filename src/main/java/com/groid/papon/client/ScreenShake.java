package com.groid.papon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Camera shake, Cataclysm style: each shake has a source point, a radius and a magnitude, and
 * fades over its duration; the camera wobbles by the sum of them, weaker the farther away it is.
 */
public final class ScreenShake {
    private record Shake(Vec3 at, float radius, float magnitude, int ticks, int[] left) {}

    private static final List<Shake> SHAKES = new ArrayList<>();

    private ScreenShake() {}

    public static void add(Vec3 at, float radius, float magnitude, int ticks) {
        SHAKES.add(new Shake(at, radius, magnitude, ticks, new int[]{ticks}));
    }

    public static void tick() {
        for (Iterator<Shake> it = SHAKES.iterator(); it.hasNext(); ) {
            Shake s = it.next();
            if (--s.left[0] <= 0) it.remove();
        }
    }

    /** Current strength at the camera, 0 when nothing is shaking. */
    public static float strength(Vec3 camera, float partial) {
        float total = 0;
        for (Shake s : SHAKES) {
            double d = camera.distanceTo(s.at);
            float falloff = (float) Math.max(0, 1 - d / (s.radius * 2));
            float time = Math.max(0, (s.left[0] - partial) / s.ticks);
            total += s.magnitude * falloff * time;
        }
        return Math.min(total, 1.5F);
    }

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        if (SHAKES.isEmpty()) return;
        float k = strength(event.getCamera().getPosition(), (float) event.getPartialTick());
        if (k <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + (float) event.getPartialTick();
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 2.7) + Math.sin(t * 5.3) * 0.5) * k * 4F);
        event.setPitch(event.getPitch() + (float) (Math.cos(t * 3.1) + Math.sin(t * 6.1) * 0.5) * k * 4F);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 4.2) * k * 3F);
    }
}
