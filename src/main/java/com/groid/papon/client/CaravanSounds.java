package com.groid.papon.client;

import com.groid.papon.registry.ModSounds;
import com.groid.papon.vehicle.CaravanEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A car's engine as you hear it (redone 2026-10-06, the user: "the current vehicle sounds are annoying and overly loud...
 * entering the Skyline should feel like entering the cockpit of an extremely premium, powerful, cinematic vehicle... not a
 * bunch of loud vehicle sounds playing at once"). It was two loops cross-faded and pitched by speed, the Skyline's drive
 * loop a 5800 rpm scream with a turbo whine baked in, as loud as the game allows.
 *
 * Now each running car has an {@link Sim}: a simulated engine, its rpm from the speed through a gearbox (six gears in the
 * Skyline, the van's four-speed automatic), up-shifting at the top of a gear (higher with your foot down) and down at the
 * bottom, the throttle from the driver's keys (yours) or from how it moves (his, anyone else's), the turbos' boost building
 * behind the throttle and dumped as you lift. Its layers are loops recorded at set rpm (tools/gen_skyline_sounds.py), all at
 * one loudness, pitched to the rpm between them and cross-faded by it: the six on the throttle, the overrun's burble off it,
 * the turbos only on boost, the road and the wind by speed. Every shift is heard (a clack, a dip in the engine as the
 * throttle is cut, the blow-off if it was on boost), a downshift off the throttle blips, a lift from high revs now and then
 * pops. From the cockpit (you in it, in first person) the engine is a second set of those layers, insulated (no fizz, the
 * shell's boom under it) and not placed in the world, so it sits steady round you; outside it is out there in the world.
 * Everything sits a little under his voice (VoiceDuck) and under the music.
 */
public final class CaravanSounds {
    private static final Map<Integer, Rig> RIGS = new HashMap<>();
    private static final RandomSource RNG = RandomSource.create();

    private CaravanSounds() {}

    public static void tick(CaravanEntity van) {
        RIGS.values().removeIf(r -> {
            if (r.van.isRemoved() || r.van.level() != Minecraft.getInstance().level) {
                r.stop();
                return true;
            }
            return false;
        });
        Rig rig = RIGS.get(van.getId());
        if (rig != null && rig.skyline != van.isSkyline()) {
            rig.stop();
            RIGS.remove(van.getId());
            rig = null;
        }
        if (rig == null) {
            if (!van.isEngineOn()) return;
            rig = new Rig(van);
            RIGS.put(van.getId(), rig);
        }
        rig.tick();
        if (rig.finished()) {
            rig.stop();
            RIGS.remove(van.getId());
        }
    }

    /** The engine's rpm as this client simulates it (0 while it is off): the Skyline's tach. */
    public static float rpm(CaravanEntity van) {
        Rig r = RIGS.get(van.getId());
        return r == null ? 0 : r.sim.rpm;
    }

    /** Speed of a van as the client sees it, blocks per tick. */
    static double speedOf(CaravanEntity van) {
        double dx = van.getX() - van.xo;
        double dz = van.getZ() - van.zo;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ the engine

    /** One car's engine: rpm, gear, throttle, boost, load; its events (shifts, blips, pops) played as they happen. */
    static final class Sim {
        final boolean sky;
        final float idle, redline, upFull, upPart, down;
        /** Speed (blocks a tick) at the redline in each gear. */
        final float[] top;
        float rpm, throttle, boost, load, along, lastThrottle;
        /** How fast it is gaining speed (eased): once it stops (at its top speed) it shifts up early and cruises low. */
        float gain;
        int gear = 1, cut, shiftCd, popCd, popsLeft, popNext;
        double prevAlong;

        Sim(boolean sky) {
            this.sky = sky;
            if (sky) {
                idle = 950; redline = 8000; upFull = 7500; upPart = 5300; down = 2700;
                top = new float[]{0.16F, 0.30F, 0.47F, 0.68F, 0.98F, 1.45F};
            } else {
                idle = 720; redline = 6000; upFull = 5200; upPart = 3700; down = 1800;
                top = new float[]{0.22F, 0.45F, 0.8F, 1.5F};
            }
        }

        float inGear(float s) { return 900 + s / top[gear - 1] * (redline - 900); }

        void tick(CaravanEntity van, Rig rig) {
            Vec3 d = new Vec3(van.getX() - van.xo, 0, van.getZ() - van.zo);
            along = (float) d.dot(van.forward());
            float s = Math.abs(along);
            Minecraft mc = Minecraft.getInstance();
            float want;
            if (van.getControllingPassenger() == mc.player && mc.player != null) {
                want = mc.player.zza * (along < -0.02F ? -1 : 1);                 // backing up on S is throttle too
            } else {
                double acc = (along - prevAlong) * Math.signum(along == 0 ? 1 : along);
                want = s < 0.02F ? 0 : acc > 0.0015 ? 1 : acc < -0.004 ? -0.5F : s > 0.3F ? 0.35F : 0.1F;
            }
            gain += ((float) (s - Math.abs(prevAlong)) - gain) * 0.2F;
            prevAlong = along;
            if (!van.isEngineOn()) want = 0;
            throttle += (want - throttle) * 0.35F;
            float foot = Math.max(0, throttle);
            if (shiftCd > 0) shiftCd--;
            if (s < 0.02F) gear = 1;
            // the gearbox
            if (van.isEngineOn() && shiftCd <= 0 && s > 0.02F) {
                // foot down and still pulling: to the top of the gear; done gaining (its top speed) or easy on it: early,
                // so it cruises in a high gear at moderate revs instead of droning at the top of a low one
                boolean pulling = foot > 0.6F && gain > 0.0015F;
                if (gear < top.length && inGear(s) > (pulling ? upFull : upPart)) {
                    gear++;
                    cut = 3;
                    shiftCd = 9;
                    rig.oneShot(sky ? ModSounds.SKYLINE_SHIFT::get : null, 0.32F, 0.95F + RNG.nextFloat() * 0.1F);
                    if (sky && boost > 0.45F) rig.oneShot(ModSounds.SKYLINE_BOV::get, 0.25F * boost, 1.05F + RNG.nextFloat() * 0.1F);
                } else if (gear > 1 && inGear(s) < down) {
                    gear--;
                    shiftCd = 9;
                    if (sky && foot < 0.3F) rig.oneShot(ModSounds.SKYLINE_BLIP::get, 0.3F, 0.95F + RNG.nextFloat() * 0.1F);
                }
            }
            float target;
            if (!van.isEngineOn()) target = 0;
            else if (s < 0.02F) target = idle + foot * 5200;                         // stood still: revving in neutral
            else {
                target = Math.max(idle, inGear(s));
                if (gear == 1 && s < 0.06F) target = Math.max(target, idle + 2400 * foot);   // the clutch slipping off the line
            }
            rpm += (target - rpm) * (target > rpm ? 0.4F : van.isEngineOn() ? 0.25F : 0.12F);
            if (cut > 0) {
                cut--;
                rpm *= 0.97F;
            }
            float l = cut > 0 ? foot * 0.3F : foot;
            load += (l - load) * 0.3F;
            if (sky) {
                float b = foot > 0.3F ? Mth.clamp((rpm - 2600) / 2800F, 0, 1) * foot : 0;
                boost += (b - boost) * (b > boost ? 0.07F : 0.3F);
                if (cut > 0) boost *= 0.7F;
                // lifting off from high revs: now and then a few soft pops out of the exhaust
                if (popCd > 0) popCd--;
                if (lastThrottle > 0.6F && throttle < 0.15F && rpm > 4200 && popCd <= 0 && RNG.nextFloat() < 0.5F) {
                    popsLeft = 2 + RNG.nextInt(3);
                    popNext = 2;
                    popCd = 90;
                }
                if (popsLeft > 0 && --popNext <= 0) {
                    popsLeft--;
                    popNext = 2 + RNG.nextInt(5);
                    rig.oneShot(ModSounds.SKYLINE_POP::get, 0.3F + RNG.nextFloat() * 0.25F, 0.85F + RNG.nextFloat() * 0.3F);
                }
            }
            lastThrottle = throttle;
        }
    }

    // ------------------------------------------------------------------ what you hear

    /** Layer kinds: the van's two loops, the Skyline's engine (on the throttle, off it), its turbos, the road, the wind. */
    private static final int IDLE = 0, LOW = 1, ON = 2, OFF = 3, TURBO = 5, ROAD = 6, WIND = 7;
    private static final float[] VAN_BASE = {720, 2400}, VAN_WIDTH = {700, 1700};

    /**
     * A running car's layers, outside and in the cockpit, and its engine. The Skyline's engine (2026-10-06) is eight loops
     * on the throttle and eight off it, recorded every 600 to 1300 rpm (ModSounds.SKYLINE_RPM): only the two either side of
     * the rpm play, cross-faded at equal power and pitched at most a fifth off what they were recorded at (they were four, a
     * layer pitched up to 1.6x: its exhaust's resonances slid up with it, a chipmunk of an engine); the throttle cross-fades
     * the on-load pair into the overrun's pair. A layer starts when it is first heard and stops once it has been silent a
     * while, so a car is never more than a handful of sounds.
     */
    static final class Rig {
        final CaravanEntity van;
        final boolean skyline;
        final Sim sim;
        final List<Layer> outside = new ArrayList<>(), inside = new ArrayList<>();
        /** 0 outside .. 1 in the cockpit (eased); the ticks each set has been silent. */
        float in;
        int outQuiet, inQuiet, age;
        /** The engine's own fade in and out (the start one-shot covers its first moment). */
        float fade;
        final float[] weights = new float[2];
        /** Each Skyline engine layer's share of the engine right now (index: rpm point, + n off the throttle). */
        final float[] engine = new float[ModSounds.SKYLINE_RPM.length * 2];

        Rig(CaravanEntity van) {
            this.van = van;
            this.skyline = van.isSkyline();
            this.sim = new Sim(skyline);
            in = cockpit() ? 1 : 0;
        }

        boolean cockpit() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.getVehicle() == van && mc.options.getCameraType() == CameraType.FIRST_PERSON;
        }

        void tick() {
            age++;
            sim.tick(van, this);
            in += ((cockpit() ? 1 : 0) - in) * 0.25F;
            fade = van.isEngineOn() ? Math.min(1, fade + 1 / 20F) : Math.max(0, fade - 1 / 14F);
            float r = sim.rpm;
            if (skyline) mixEngine(r);
            else {
                for (int i = 0; i < 2; i++) {
                    float z = (r - VAN_BASE[i]) / VAN_WIDTH[i];
                    weights[i] = (float) Math.exp(-z * z);
                }
                if (r < VAN_BASE[0]) weights[0] = 1;
                if (r > VAN_BASE[1]) weights[1] = 1;
                float sum = Mth.sqrt(Math.max(weights[0] * weights[0] + weights[1] * weights[1], 1e-6F));
                weights[0] /= sum;
                weights[1] /= sum;
            }
            manage(outside, false, 1 - in);
            manage(inside, true, in);
        }

        /** The two rpm points either side of the rpm, equal power, split between the throttle's pair and the overrun's. */
        private void mixEngine(float r) {
            int[] rpm = ModSounds.SKYLINE_RPM;
            int n = rpm.length;
            java.util.Arrays.fill(engine, 0);
            int i = 0;
            while (i < n - 2 && r > rpm[i + 1]) i++;
            float t = Mth.clamp((r - rpm[i]) / (float) (rpm[i + 1] - rpm[i]), 0, 1);
            float a = Mth.cos(t * Mth.HALF_PI), b = Mth.sin(t * Mth.HALF_PI);
            // off the throttle above a fast idle: the overrun
            float off = (1 - sim.load) * Mth.clamp((r - 1400) / 900F, 0, 1);
            float on = Mth.sqrt(1 - off), ov = Mth.sqrt(off);
            engine[i] = a * on;
            engine[i + 1] = b * on;
            engine[n + i] = a * ov;
            engine[n + i + 1] = b * ov;
        }

        private void manage(List<Layer> set, boolean cabin, float share) {
            boolean heard = share > 0.01F && fade > 0;
            if (heard) {
                if (set.isEmpty()) start(set, cabin);
                // the engine's layers: start the ones heard now that are not playing
                if (skyline) {
                    for (int k = 0; k < engine.length; k++) {
                        if (engine[k] * share < 0.03F) continue;
                        boolean playing = false;
                        for (Layer l : set) playing |= (l.kind == ON || l.kind == OFF) && l.index == k && !l.isStopped();
                        if (!playing) add(set, engineSound(k, cabin), k < engine.length / 2 ? ON : OFF, k, cabin);
                    }
                }
            }
            if (cabin) inQuiet = heard ? 0 : inQuiet + 1;
            else outQuiet = heard ? 0 : outQuiet + 1;
            if (!set.isEmpty() && (cabin ? inQuiet : outQuiet) > 40) {
                for (Layer l : set) l.end();
                set.clear();
            }
            set.removeIf(Layer::isStopped);
        }

        private SoundEvent engineSound(int k, boolean cabin) {
            int n = ModSounds.SKYLINE_RPM.length;
            boolean on = k < n;
            return (on ? cabin ? ModSounds.SKYLINE_ON_IN : ModSounds.SKYLINE_ON : cabin ? ModSounds.SKYLINE_OFF_IN : ModSounds.SKYLINE_OFF)[k % n].get();
        }

        private void start(List<Layer> set, boolean cabin) {
            if (skyline) {
                add(set, ModSounds.SKYLINE_TURBO.get(), TURBO, 0, cabin);
                add(set, ModSounds.SKYLINE_ROAD.get(), ROAD, 0, cabin);
                if (cabin) add(set, ModSounds.SKYLINE_WIND.get(), WIND, 0, true);
            } else {
                add(set, ModSounds.CARAVAN_IDLE.get(), IDLE, 0, cabin);
                add(set, ModSounds.CARAVAN_DRIVE.get(), LOW, 0, cabin);
            }
        }

        private void add(List<Layer> set, SoundEvent sound, int kind, int index, boolean cabin) {
            Layer l = new Layer(this, sound, kind, index, cabin);
            set.add(l);
            Minecraft.getInstance().getSoundManager().play(l);
        }

        /** How loud a layer is right now (before the set's share and the fade). */
        float volume(int kind, int index, boolean cabin) {
            float s = Math.min(1.4F, Math.abs(sim.along));
            float duck = 0.6F + 0.4F * VoiceDuck.get();
            float dip = sim.cut > 0 ? 0.6F : 1F;
            if (!skyline) {
                float lvl = (cabin ? 0.5F : 0.7F) * duck * dip;
                return kind == IDLE ? lvl * weights[0] : lvl * weights[1] * (0.55F + 0.45F * sim.load);
            }
            float eng = (cabin ? 0.7F : 0.9F) * duck * dip;
            return switch (kind) {
                case ON -> eng * engine[index] * (0.7F + 0.3F * sim.load);
                case OFF -> eng * engine[index] * 0.85F;
                case TURBO -> (cabin ? 0.3F : 0.36F) * duck * (float) Math.pow(sim.boost, 1.5);
                case ROAD -> (cabin ? 0.32F : 0.26F) * duck * Mth.clamp(s / 0.6F, 0, 1);
                case WIND -> 0.3F * duck * Mth.clamp(s, 0, 1) * Mth.clamp(s, 0, 1);
                default -> 0;
            };
        }

        float pitch(int kind, int index) {
            float r = Math.max(sim.rpm, 1);
            float s = Math.min(1.4F, Math.abs(sim.along));
            int[] rpm = ModSounds.SKYLINE_RPM;
            return switch (kind) {
                case IDLE, LOW -> Mth.clamp(r / VAN_BASE[kind], 0.5F, 2F);
                case ON, OFF -> Mth.clamp(r / rpm[index % rpm.length], 0.5F, 2F);
                case TURBO -> 0.7F + 0.8F * sim.boost;
                case ROAD -> 0.8F + 0.35F * s;
                case WIND -> 0.85F + 0.25F * s;
                default -> 1;
            };
        }

        /** A shift, a blip, a pop: from the car (or, in the cockpit, round you). */
        void oneShot(Supplier<SoundEvent> sound, float volume, float pitch) {
            if (sound == null) return;
            Minecraft mc = Minecraft.getInstance();
            float v = volume * (0.6F + 0.4F * VoiceDuck.get());
            if (in > 0.5F) mc.getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), pitch, v * 1.15F));
            else mc.getSoundManager().play(new SimpleSoundInstance(sound.get(), SoundSource.NEUTRAL, v, pitch, RNG, van.getX(), van.getY() + 0.5, van.getZ()));
        }

        boolean finished() { return !van.isEngineOn() && fade <= 0 && sim.rpm < 60; }

        void stop() {
            for (Layer l : outside) l.end();
            for (Layer l : inside) l.end();
            outside.clear();
            inside.clear();
        }
    }

    /** One loop of a car's sound, following it (or, from the cockpit, not placed: round you); an engine layer ends once unheard. */
    private static final class Layer extends AbstractTickableSoundInstance {
        private final Rig rig;
        private final int kind, index;
        private final boolean cabin;
        private int quiet;

        Layer(Rig rig, SoundEvent sound, int kind, int index, boolean cabin) {
            super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.rig = rig;
            this.kind = kind;
            this.index = index;
            this.cabin = cabin;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0001F;
            this.pitch = rig.pitch(kind, index);
            if (cabin) {
                this.relative = true;
                this.attenuation = Attenuation.NONE;
                this.x = this.y = this.z = 0;
            } else {
                this.x = rig.van.getX();
                this.y = rig.van.getY() + 0.6;
                this.z = rig.van.getZ();
            }
        }

        void end() { stop(); }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public void tick() {
            if (rig.van.isRemoved()) {
                stop();
                return;
            }
            if (!cabin) {
                x = rig.van.getX();
                y = rig.van.getY() + 0.6;
                z = rig.van.getZ();
            }
            float share = cabin ? rig.in : 1 - rig.in;
            float v = rig.volume(kind, index, cabin) * share * rig.fade;
            volume = Math.max(0.0001F, v);
            pitch = rig.pitch(kind, index);
            if (kind == ON || kind == OFF) {
                quiet = v < 0.004F ? quiet + 1 : 0;
                if (quiet > 15) stop();
            }
        }
    }
}
