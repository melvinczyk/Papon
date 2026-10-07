package com.groid.papon.client;

import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Your point of view from a car's seat (2026-10-06, the user: "further improve the player's point of view in the car").
 * The view turns with the car as it steers (CaravanEntity.positionRider); here, in first person: your head leans with the
 * body's roll in a corner and nods with its pitch as it pulls away and brakes (both eased between ticks: nothing shakes),
 * and the view widens a touch with speed. Behind the Skyline's wheel your hands are on it (CaravanRenderer.hands)
 * instead of a held item floating over the dash.
 */
public final class SkylineCockpit {
    private static float fovK;

    private SkylineCockpit() {}

    /** The car you sit in, seen from your own eyes (no cutscene), or null. */
    static CaravanEntity seat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Cinematic.isActive() || mc.getCameraEntity() != mc.player) return null;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return null;
        return mc.player.getVehicle() instanceof CaravanEntity car ? car : null;
    }

    /** Behind the Skyline's wheel, from your own eyes. */
    static boolean driving(CaravanEntity car) {
        Minecraft mc = Minecraft.getInstance();
        return car.isSkyline() && seat() == car && car.getControllingPassenger() == mc.player;
    }

    static boolean hidesHands() {
        CaravanEntity car = seat();
        return car != null && driving(car);
    }

    static void camera(ViewportEvent.ComputeCameraAngles event) {
        CaravanEntity car = seat();
        if (car == null) return;
        float pt = (float) event.getPartialTick();
        // the body's lean (CaravanEntity.clientVisuals: roll > 0 is its left side down, turning right) and its pitch (> 0
        // nose up, pulling away): your head goes with some of it
        event.setRoll(event.getRoll() - car.bodyRoll(pt) * 0.55F);
        event.setPitch(event.getPitch() - car.bodyPitch(pt) * 0.45F);
    }

    static void fov(ViewportEvent.ComputeFov event) {
        CaravanEntity car = seat();
        float want = car == null ? 0 : (float) Mth.clamp(CaravanSounds.speedOf(car) / 0.8, 0, 1.2);
        fovK += (want - fovK) * 0.04F;
        if (fovK > 0.002F) event.setFOV(event.getFOV() * (1 + 0.11F * fovK * fovK));
    }
}
