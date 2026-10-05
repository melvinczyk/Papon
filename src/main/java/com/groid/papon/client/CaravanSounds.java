package com.groid.papon.client;

import com.groid.papon.registry.ModSounds;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The van's engine while it runs: an idle loop and a driving loop, cross-faded and pitched by the
 * van's speed, following it around. One-shots (start, horn, screech, crash, doors) are played by
 * the server or by the door animation.
 */
public final class CaravanSounds {
    private static final Map<Integer, Engine[]> PLAYING = new HashMap<>();

    private CaravanSounds() {}

    public static void tick(CaravanEntity van) {
        Engine[] e = PLAYING.get(van.getId());
        boolean alive = e != null && !e[0].isStopped() && !e[1].isStopped() && e[0].skyline == van.isSkyline();
        if (van.isEngineOn() && !alive) {
            // (a van turned Skyline: the old loops stop themselves on their next tick)
            // the Skyline has its own engine (an RB26), the van its tired V6
            boolean sky = van.isSkyline();
            Engine idle = new Engine(van, (sky ? ModSounds.SKYLINE_IDLE : ModSounds.CARAVAN_IDLE).get(), false);
            Engine drive = new Engine(van, (sky ? ModSounds.SKYLINE_DRIVE : ModSounds.CARAVAN_DRIVE).get(), true);
            Minecraft.getInstance().getSoundManager().play(idle);
            Minecraft.getInstance().getSoundManager().play(drive);
            PLAYING.put(van.getId(), new Engine[]{idle, drive});
        }
        PLAYING.entrySet().removeIf(en -> en.getValue()[0].isStopped() && en.getValue()[1].isStopped());
    }

    /** Speed of a van as the client sees it, blocks per tick. */
    static double speedOf(CaravanEntity van) {
        double dx = van.getX() - van.xo;
        double dz = van.getZ() - van.zo;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static final class Engine extends AbstractTickableSoundInstance {
        private final CaravanEntity van;
        private final boolean driving;
        final boolean skyline;
        private float smooth;

        Engine(CaravanEntity van, SoundEvent sound, boolean driving) {
            super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.van = van;
            this.driving = driving;
            this.skyline = van.isSkyline();
            this.looping = true;
            this.delay = 0;
            this.volume = driving ? 0.01F : 0.9F;
            this.pitch = 1.0F;
            this.x = van.getX();
            this.y = van.getY();
            this.z = van.getZ();
        }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public void tick() {
            if (van.isRemoved() || !van.isEngineOn() || van.isSkyline() != skyline) {
                stop();
                return;
            }
            x = van.getX();
            y = van.getY() + 0.6;
            z = van.getZ();
            float s = (float) Mth.clamp(speedOf(van) / 0.9, 0, 1.2);
            smooth += (s - smooth) * 0.25F;
            if (skyline) {
                // the RB26: the drive loop is already at 5800 rpm on boost, so it starts low and climbs past it
                if (driving) {
                    volume = Mth.clamp(smooth * 1.8F, 0.01F, 1.5F);
                    pitch = 0.55F + smooth * 0.6F;
                } else {
                    volume = Mth.clamp(1.1F - smooth * 0.9F, 0.15F, 1.1F);
                    pitch = 0.95F + smooth * 0.5F;
                }
            } else if (driving) {
                volume = Mth.clamp(smooth * 1.6F, 0.01F, 1.4F);
                pitch = 0.62F + smooth * 0.85F;
            } else {
                volume = Mth.clamp(1.0F - smooth * 0.8F, 0.15F, 1.0F);
                pitch = 0.95F + smooth * 0.35F;
            }
        }
    }
}
