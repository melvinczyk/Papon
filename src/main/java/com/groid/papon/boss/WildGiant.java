package com.groid.papon.boss;

import com.groid.papon.fx.Fx;
import com.groid.papon.network.PaponNetwork;
import com.groid.papon.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * El Gigante on its own (2026-10-06, the user: "make el gigante a summonable entity with its attacks and movements and give it a
 * spawn egg"): summoned with its egg or /summon, out of any fight, it digs itself up out of the ground where it was put and hunts
 * the nearest player (with none in survival, the nearest monster) until its rift closes and the dead drag it back under. The
 * boss's giant (PaponBossEntity.tickGiantLayer, tickGiantHazards, tickGiantGrab) ported without the arena: the floor is the
 * ground under it, it wades over the land after you, and what the walls did (swiped into them) any block you are flung into
 * does. Same acts, tells, shares of your health; no orders from him.
 */
final class WildGiant {
    private static final int BLUE = 0x40E2FF, TRACK = 0, SWEEP = 1;
    /** How far it hunts (blocks from where it stands), how near La Mano's wade stops short of you, its solid middle. */
    private static final double RANGE = 80, GRAB_REACH = 24, BODY = 6.5;

    private final SpiritGiantEntity g;
    private final RandomSource random;
    @Nullable private LivingEntity target;
    private int retarget;
    private Vec3 aim;
    private boolean laserPending, sweepSide;
    private int chargeT, laserLen, laserMode;
    @Nullable private Vec3 sweepAt, sweepAcross;
    private final UUID[] beams = new UUID[2];
    private int tellAct = -1;
    private float tellAt;
    private boolean tellDone = true;
    private final Set<Integer> swipeHit = new HashSet<>();
    private final Map<Integer, Long> bodyHit = new HashMap<>(), mercy = new HashMap<>(), ringHit = new HashMap<>();
    private final Map<UUID, Long> flung = new HashMap<>();
    /** Rings rolling out of where its hands land: x, y, z, the tick they start (jump them). */
    private final List<double[]> rings = new ArrayList<>();
    private int next = 50, lastMove = -1, relocateCd = 200, nearTicks;
    private int grabVictim = -1;
    private boolean grabFreed;
    @Nullable private Vec3 grabMark;
    private final Vec3[] hist = new Vec3[11];
    private int histN;

    WildGiant(SpiritGiantEntity g) {
        this.g = g;
        this.random = RandomSource.create();
        this.aim = g.position();
    }

    void tick(ServerLevel level) {
        if (--retarget <= 0 || target != null && !valid(target)) {
            retarget = 10;
            LivingEntity was = target;
            target = pick(level);
            if (target != was) histN = 0;
        }
        if (target != null) hist[histN++ % hist.length] = target.position();
        hazards(level);
        tickRings(level);
        tickFlung(level);
        int act = g.act();
        if (act == SpiritGiantEntity.SINK || act == SpiritGiantEntity.RISE) return;
        if (relocateCd > 0) relocateCd--;
        if (act != SpiritGiantEntity.IDLE) return;
        if (target == null) {
            Player p = level.getNearestPlayer(g.base().x, g.base().y, g.base().z, RANGE, true);
            if (p != null && g.tickCount % 4 == 0) g.aim(p.getEyePosition());
            return;
        }
        if (g.tickCount % 4 == 0) g.aim(target.position().add(0, 1, 0));
        int st = g.fury();
        double d = flatTo(target);
        // it hunts you: over the land toward you, to within its hands' reach, the ground under it followed
        if (d > 14) g.stalk(target.position(), st >= 3 ? 0.22 : 0.17, st >= 3 ? 3.5F : 2.5F);
        else g.stalk(target.position(), 0, 2.5F);
        Vec3 b = g.base();
        double y = ground(b, b.y);
        if (Math.abs(y - b.y) > 0.01) g.place(new Vec3(b.x, b.y + Mth.clamp(y - b.y, -0.3, 0.3), b.z));
        // under it is no place to stand: a second and a half within its reach and it answers
        nearTicks = d < 15 ? nearTicks + 1 : 0;
        if (nearTicks > (st >= 3 ? 18 : 30) && next > 8) {
            next = 8;
            nearTicks = 0;
        }
        // far off: it reaches across for you (La Mano) or throws itself at you
        if (relocateCd <= 0 && d > 44) {
            if (random.nextInt(5) < 3) grab(level, target);
            else lunge(level, target);
            relocateCd = 260;
            next = 40;
            return;
        }
        if (--next > 0) return;
        next = st >= 3 ? 50 + random.nextInt(30) : st == 2 ? 85 + random.nextInt(40) : 105 + random.nextInt(45);
        List<Integer> moves = new ArrayList<>();
        if (d < 15) moves.addAll(List.of(2, 2, 0));
        else if (d < 32) moves.addAll(List.of(5, 5, 0, 1));
        else moves.addAll(List.of(4, 4, 1));
        if (st >= 3) moves.add(d < 32 ? 3 : 4);
        if (moves.stream().anyMatch(m1 -> m1 != lastMove)) moves.removeIf(m0 -> m0 == lastMove);
        int m = moves.get(random.nextInt(moves.size()));
        lastMove = m;
        switch (m) {
            case 0 -> swipe(target, random.nextBoolean() ? SpiritGiantEntity.SWIPE_L : SpiritGiantEntity.SWIPE_R);
            case 1 -> laser(st >= 3 ? 20 : 26, st >= 3 ? 56 : 46, random.nextInt(3) == 0 ? SWEEP : TRACK);
            case 2 -> slam(level, target, false);
            case 4 -> grab(level, target);
            case 5 -> lunge(level, target);
            default -> slam(level, target, true);
        }
    }

    /** Dev and testing: one of its acts at whoever it is after now (returns false: no such act, or nobody to do it to). */
    boolean force(String move) {
        if (!(g.level() instanceof ServerLevel level) || target == null || g.act() == SpiritGiantEntity.SINK || g.act() == SpiritGiantEntity.RISE) return false;
        if (g.act() != SpiritGiantEntity.IDLE) g.act(SpiritGiantEntity.IDLE);
        switch (move) {
            case "lunge" -> lunge(level, target);
            case "grab" -> grab(level, target);
            case "swipe" -> swipe(target, SpiritGiantEntity.SWIPE_L);
            case "slam" -> slam(level, target, false);
            case "slam2" -> slam(level, target, true);
            case "laser" -> laser(26, 46, TRACK);
            default -> { return false; }
        }
        next = 200;
        return true;
    }

    // ------------------------------------------------------------------ who, where

    private boolean hittable(LivingEntity e) {
        if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand || e instanceof PaponBossEntity || e instanceof FoilPotatoEntity
                || e instanceof com.groid.papon.vehicle.CaravanEntity) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return !(e instanceof com.groid.papon.entity.npc.TacoNpc) && !(e instanceof com.groid.papon.entity.WanderingPaponEntity);
    }

    private boolean valid(LivingEntity e) { return hittable(e) && flatTo(e) < RANGE + 16 && Math.abs(e.getY() - g.base().y) < 48; }

    /** The nearest player it can hurt; with none, the nearest monster. */
    @Nullable
    private LivingEntity pick(ServerLevel level) {
        Vec3 b = g.base();
        AABB box = new AABB(b, b).inflate(RANGE, 48, RANGE);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : level.players()) {
            if (!hittable(p) || !box.contains(p.position())) continue;
            double dd = p.distanceToSqr(b);
            if (dd < bd) { bd = dd; best = p; }
        }
        if (best != null) return best;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box.deflate(RANGE * 0.4, 0, RANGE * 0.4), e -> e instanceof Enemy && hittable(e))) {
            double dd = e.distanceToSqr(b);
            if (dd < bd) { bd = dd; best = e; }
        }
        return best;
    }

    private List<LivingEntity> victims(ServerLevel level) {
        Vec3 b = g.base();
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(b, b).inflate(RANGE, 40, RANGE), this::hittable);
    }

    /** The ground at {@code p} (the top of whatever stands there), no further than 24 blocks from {@code near}. */
    private double ground(Vec3 p, double near) {
        double h = g.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
        return Mth.clamp(h, near - 24, near + 24);
    }

    private Vec3 onGround(Vec3 p) { return new Vec3(p.x, ground(p, g.base().y), p.z); }

    private double flatTo(LivingEntity e) { return Math.sqrt(e.distanceToSqr(g.base().x, e.getY(), g.base().z)); }

    /** Where the target will be in {@code ahead} ticks, from where it has been. */
    private Vec3 predict(LivingEntity t, int ahead) {
        if (histN < hist.length) return t.position();
        Vec3 old = hist[histN % hist.length];
        Vec3 v = t.position().subtract(old).scale(1.0 / (hist.length - 1));
        v = new Vec3(Mth.clamp(v.x, -0.6, 0.6), 0, Mth.clamp(v.z, -0.6, 0.6));
        return t.position().add(v.scale(ahead));
    }

    private static double segDist(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double k = len2 < 1e-8 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
        return p.distanceTo(a.add(ab.scale(k)));
    }

    /** A share of {@code e}'s health, through armour (not while it is in its mercy after La Mano). */
    private boolean hit(LivingEntity e, float frac) {
        if (!hittable(e)) return false;
        Long m = mercy.get(e.getId());
        if (m != null) {
            if (m > g.level().getGameTime()) return false;
            mercy.remove(e.getId());
        }
        return Mechanics.trueHit(e, frac, g, true);
    }

    private void fling(LivingEntity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
        flung.put(e.getUUID(), g.level().getGameTime() + 40);
    }

    /** Anyone flung who hits a wall (any block in the way) on the way: 12% and stunned. */
    private void tickFlung(ServerLevel level) {
        if (flung.isEmpty()) return;
        long now = level.getGameTime();
        flung.entrySet().removeIf(en -> {
            if (now > en.getValue()) return true;
            if (!(level.getEntity(en.getKey()) instanceof LivingEntity e) || !e.isAlive()) return true;
            if (!e.horizontalCollision) return false;
            e.invulnerableTime = 0;
            if (hit(e, 0.12F)) Mechanics.stun(e);
            Vec3 at = e.position().add(0, 1, 0);
            Fx.hit(level, at, 2.4F, BLUE);
            Fx.chunks(level, at, 16, 0.35F, 0x2C2438, 0.6);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8F, 0.5F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0F, 0.5F);
            if (e instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.9F, 16);
            e.setDeltaMovement(e.getDeltaMovement().multiply(-0.2, 0.2, -0.2));
            e.hurtMarked = true;
            return true;
        });
    }

    /** The rings out of where its hands land, rolling out at 2 blocks a tick to 20: only the grounded are caught (12%). */
    private void tickRings(ServerLevel level) {
        if (rings.isEmpty()) return;
        long now = level.getGameTime();
        rings.removeIf(r -> (now - (long) r[3]) * 2.0 > 20);
        for (double[] r : rings) {
            if (now < (long) r[3]) continue;
            double rad = (now - (long) r[3]) * 2.0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(r[0], r[1], r[2], r[0], r[1], r[2]).inflate(rad + 2, 3, rad + 2), this::hittable)) {
                if (!e.onGround() || Math.abs(e.getY() - r[1]) > 2.5) continue;          // jumped it
                double dd = Math.sqrt(Mth.square(e.getX() - r[0]) + Mth.square(e.getZ() - r[2]));
                if (Math.abs(dd - rad) > 1.1) continue;
                Long last = ringHit.get(e.getId());
                if (last != null && last == (long) r[3]) continue;
                if (hit(e, 0.12F)) {
                    ringHit.put(e.getId(), (long) r[3]);
                    Vec3 away = new Vec3(e.getX() - r[0], 0, e.getZ() - r[2]);
                    away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                    e.setDeltaMovement(away.scale(1.1).add(0, 0.5, 0));
                    e.hurtMarked = true;
                }
            }
        }
    }

    private void ring(Vec3 at, long start) { rings.add(new double[]{at.x, at.y, at.z, start}); }

    // ------------------------------------------------------------------ its moves

    private void laser(int charge, int len, int mode) {
        g.act(SpiritGiantEntity.CHARGE);
        laserPending = true;
        chargeT = charge;
        laserLen = len;
        laserMode = mode;
        sweepSide = random.nextBoolean();
        if (target != null) aim = onGround(target.position());
    }

    private void slam(ServerLevel level, LivingEntity t, boolean both) {
        Vec3 at = onGround(predict(t, both ? 26 : 18));
        if (both) {
            Vec3 side = g.right().scale(3.6);
            g.handTarget(0, onGround(at.add(side)));
            g.handTarget(1, onGround(at.subtract(side)));
            Fx.warnClosing(level, at, 9.5F, BLUE, SpiritGiantEntity.SLAM2_HIT);
            g.act(SpiritGiantEntity.SLAM_BOTH);
        } else {
            int i = at.subtract(g.base()).dot(g.right()) > 0 ? 1 : 0;
            g.handTarget(i, at);
            Fx.warnClosing(level, at, 5.5F, BLUE, SpiritGiantEntity.SLAM_HIT);
            g.act(i == 0 ? SpiritGiantEntity.SLAM_L : SpiritGiantEntity.SLAM_R);
        }
    }

    private void swipe(LivingEntity t, int act) {
        g.swipe(act, Mth.clamp(flatTo(t), 12, 38));
        swipeHit.clear();
    }

    private void lunge(ServerLevel level, LivingEntity t) {
        Vec3 base = g.base();
        Vec3 p = predict(t, SpiritGiantEntity.LUNGE_GO + 8);
        Vec3 d = new Vec3(p.x - base.x, 0, p.z - base.z);
        if (d.lengthSqr() < 1e-4) d = g.forward();
        double len = Mth.clamp(d.length() - 6, 6, 22);
        Vec3 to = onGround(base.add(d.normalize().scale(len)));
        g.lungeTo(to);
        swipeHit.clear();
        Vec3 dir = to.subtract(base).multiply(1, 0, 1).normalize();
        Fx.warnLine(level, base, to.add(dir.scale(16)), 13F, BLUE, SpiritGiantEntity.LUNGE_GO + 4);
        Vec3 h = g.head(0);
        level.playSound(null, h.x, h.y, h.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 6.0F, 0.35F);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 6.0F, 0.6F);
    }

    /** La Mano: it wades to within reach of you and its hand comes down on where you will be. */
    private void grab(ServerLevel level, LivingEntity t) {
        Vec3 base = g.base();
        Vec3 you = onGround(t.position());
        Vec3 d = you.subtract(base).multiply(1, 0, 1);
        double dist = d.length();
        Vec3 to = base;
        if (dist > GRAB_REACH) to = onGround(base.add(d.normalize().scale(dist - GRAB_REACH)));
        g.grab(to, you, random.nextBoolean() ? 0 : 1);
        grabMark = you;
        level.playSound(null, you.x, you.y, you.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 4.0F, 0.5F);
    }

    /** Its last grab at you as the dead drag it under (SpiritGiantEntity.drag). */
    void grasp(ServerLevel level, Vec3 mark) { impact(level, mark, 5.0, 0.18F); }

    private void impact(ServerLevel level, Vec3 mark, double r, float share) {
        Vec3 at = onGround(mark);
        Fx.shock(level, at, (float) r + 1.5F, BLUE);
        Fx.scorch(level, at, (float) r, 200);
        Fx.spark(level, at.add(0, 0.6, 0), 20, 1.6, 0.5F, 0x9FF4FF);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r + 1, 5, r + 1), this::hittable)) {
            if (e.distanceToSqr(at.x, e.getY(), at.z) > Mth.square(r + e.getBbWidth() / 2) || e.getY() > at.y + 5) continue;
            if (hit(e, share)) {
                Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.setDeltaMovement(away.scale(1.2).add(0, 0.8, 0));
                e.hurtMarked = true;
                Mechanics.stun(e);
            }
        }
    }

    // ------------------------------------------------------------------ its beams

    @Nullable
    private BeamEntity beam(ServerLevel level, int i) {
        return beams[i] != null && level.getEntity(beams[i]) instanceof BeamEntity b && b.isAlive() ? b : null;
    }

    void clearBeams(ServerLevel level) {
        boolean was = false;
        for (int i = 0; i < 2; i++) {
            BeamEntity b = beam(level, i);
            if (b != null) b.discard();
            was |= beams[i] != null;
            beams[i] = null;
        }
        if (was) {
            var stop = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(ModSounds.GIANT_LASER_BURN.getId(), SoundSource.HOSTILE);
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(g.base()) < 300 * 300) p.connection.send(stop);
        }
    }

    // ------------------------------------------------------------------ what it hits (PaponBossEntity.tickGiantHazards)

    private void hazards(ServerLevel level) {
        int act = g.act();
        float at = g.actTime(0);
        int st = g.fury();
        double floor = g.base().y;
        if (act == SpiritGiantEntity.SINK) {
            laserPending = false;
            if (beams[0] != null || beams[1] != null) clearBeams(level);
            return;
        }
        // its body is solid: whoever gets in among its ribs is thrown back out (6%)
        if (g.risen(0) > 0.55F && act != SpiritGiantEntity.RISE) {
            Vec3 core = g.base().add(g.forward().scale(1.2));
            long now = level.getGameTime();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(core, core).inflate(BODY + 2, 24, BODY + 2), this::hittable)) {
                if (e.isPassenger() || e.getY() > floor + 21 || e.getY() < floor - 2) continue;
                double dx = e.getX() - core.x, dz = e.getZ() - core.z, flat = Math.sqrt(dx * dx + dz * dz);
                if (flat > BODY + e.getBbWidth() / 2) continue;
                Vec3 out = flat < 1e-3 ? g.forward() : new Vec3(dx / flat, 0, dz / flat);
                e.setDeltaMovement(out.scale(1.3).add(0, 0.45, 0));
                e.hurtMarked = true;
                Long last = bodyHit.get(e.getId());
                if (last == null || now - last > 12) {
                    bodyHit.put(e.getId(), now);
                    if (hit(e, 0.06F)) {
                        Fx.hit(level, e.position().add(0, 1, 0), 2.0F, BLUE);
                        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 3.0F, 0.4F);
                        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 3.0F, 0.6F);
                    }
                    if (next > 6) next = 6;
                }
            }
        }
        // every attack opens with its own cue
        boolean fresh = act != tellAct || at < tellAt - 0.5F;
        if (fresh) tellDone = false;
        tellAct = act;
        tellAt = at;
        if (!tellDone && at >= (act == SpiritGiantEntity.SLAM_BOTH ? 8 : 0)) {
            tellDone = true;
            tell(level, act);
        }
        if (act == SpiritGiantEntity.CHARGE) {
            if (target != null) aim = aim.lerp(onGround(target.position()), 0.12);
            g.aim(aim);
            if ((int) at % 4 == 0) {
                for (int s : new int[]{-1, 1}) {
                    Vec3 eye = g.eye(s, 0);
                    Fx.warnLine(level, onGround(eye), aim, 1.4F, BLUE, 5);
                }
            }
            if (laserPending && at >= chargeT) {
                laserPending = false;
                g.act(SpiritGiantEntity.LASER);
                act = SpiritGiantEntity.LASER;
                at = 0;
                clearBeams(level);
                for (int i = 0; i < 2; i++) {
                    int side = i == 0 ? -1 : 1;
                    BeamEntity b = BeamEntity.create(level, g.eye(side, 0), aim, 3.4F + 0.3F * (st - 1), BLUE).life(400).eye(g, side);
                    b.setMode(BeamEntity.FIRE);
                    level.addFreshEntity(b);
                    beams[i] = b.getUUID();
                }
                // swept across the ground through where you are, side to side
                Vec3 across = aim.subtract(g.base()).multiply(1, 0, 1);
                across = across.lengthSqr() < 1e-4 ? g.right() : new Vec3(-across.z, 0, across.x).normalize();
                sweepAt = aim;
                sweepAcross = across.scale(sweepSide ? 1 : -1);
                Vec3 h = g.head(0);
                level.playSound(null, h.x, h.y, h.z, ModSounds.GIANT_LASER_FIRE.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
                level.playSound(null, h.x, h.y, h.z, ModSounds.GIANT_LASER_BURN.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
                level.playSound(null, h.x, h.y, h.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 8.0F, 0.4F);
                PaponNetwork.shake(level, aim, 140, 1.0F, laserLen);
            }
        }
        if (act == SpiritGiantEntity.LASER) {
            if (at >= laserLen) {
                g.act(SpiritGiantEntity.IDLE);
                clearBeams(level);
                level.playSound(null, aim.x, aim.y, aim.z, ModSounds.GIANT_LASER_END.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
            } else {
                if (laserMode == SWEEP && sweepAt != null && sweepAcross != null) {
                    double k = at / Math.max(1.0, laserLen);
                    Vec3 toward = sweepAt.subtract(g.base()).multiply(1, 0, 1);
                    toward = toward.lengthSqr() < 1e-4 ? g.forward() : toward.normalize();
                    aim = onGround(sweepAt.add(sweepAcross.scale((k * 2 - 1) * 22)).add(toward.scale(-8 * Math.sin(k * Math.PI))));
                } else if (target != null) {
                    Vec3 want = onGround(target.position());
                    Vec3 d = want.subtract(aim);
                    double step = 0.4 + 0.08 * st;
                    aim = d.length() < step ? want : aim.add(d.normalize().scale(step));
                }
                g.aim(aim);
                for (int i = 0; i < 2; i++) {
                    BeamEntity b = beam(level, i);
                    if (b != null) b.set(g.eye(i == 0 ? -1 : 1, 0), aim);
                }
                if ((int) at % 3 == 0) {
                    Fx.shock(level, aim, 3.4F, BLUE);
                    Fx.scorch(level, aim, 3.2F, 220);
                    Fx.spark(level, aim.add(0, 0.5, 0), 8, 1.5, 0.55F, 0x9FF4FF);
                }
                if ((int) at % 10 == 3) level.playSound(null, aim.x, aim.y, aim.z, ModSounds.GIANT_LASER_SIZZLE.get(), SoundSource.HOSTILE, 8.0F, 0.9F + random.nextFloat() * 0.2F);
                if ((int) at % 4 == 0) PaponNetwork.shake(level, aim, 70, 0.35F, 6);
                if ((int) at % 5 == 0) {
                    for (LivingEntity e : victims(level)) {
                        Vec3 mid = e.position().add(0, e.getBbHeight() * 0.5, 0);
                        double dist = Math.min(segDist(mid, g.eye(-1, 0), aim), segDist(mid, g.eye(1, 0), aim));
                        double flat = Math.sqrt(e.distanceToSqr(aim.x, e.getY(), aim.z));
                        if (dist < 1.9 + e.getBbWidth() / 2 || flat < 3.3 && Math.abs(e.getY() - aim.y) < 4) {
                            if (hit(e, 0.16F)) {
                                e.setSecondsOnFire(3);
                                e.setDeltaMovement(e.getDeltaMovement().add(e.position().subtract(aim).multiply(1, 0, 1).normalize().scale(0.6)).add(0, 0.35, 0));
                                e.hurtMarked = true;
                            }
                        }
                    }
                }
            }
        } else if (beams[0] != null || beams[1] != null) {
            clearBeams(level);
        }
        if (act == SpiritGiantEntity.SWIPE_L || act == SpiritGiantEntity.SWIPE_R) {
            int i = act == SpiritGiantEntity.SWIPE_L ? 0 : 1;
            double r = g.swipeRadius();
            Vec3 f = g.forward(), rt = g.right(), base = g.base();
            if (at < SpiritGiantEntity.SWIPE_FROM && (int) at % 4 == 0) {
                for (int k = 0; k <= 12; k++) {
                    double ang = SpiritGiantEntity.swipeAngle(i, 0) + (SpiritGiantEntity.swipeAngle(i, 99) - SpiritGiantEntity.swipeAngle(i, 0)) * k / 12.0;
                    Fx.warn(level, onGround(base.add(f.scale(Math.cos(ang) * r)).add(rt.scale(-Math.sin(ang) * r))), 4.8F, BLUE, 5);
                }
            }
            if (at >= SpiritGiantEntity.SWIPE_FROM - 1 && at <= SpiritGiantEntity.SWIPE_TO + 1) {
                double a0 = SpiritGiantEntity.swipeAngle(i, at - 1.2F), a1 = SpiritGiantEntity.swipeAngle(i, at);
                double lo = Math.min(a0, a1) - 0.08, hi = Math.max(a0, a1) + 0.08;
                Vec3 hand = g.hand(i, 0), handWas = g.hand(i, -1.2F);
                Vec3 motion = hand.subtract(handWas).multiply(1, 0, 1);
                motion = motion.lengthSqr() < 1e-4 ? rt : motion.normalize();
                if ((int) at == SpiritGiantEntity.SWIPE_FROM + 4) {
                    level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.4F);
                    PaponNetwork.shake(level, hand, 90, 0.7F, 14);
                }
                if ((int) at % 2 == 0) Fx.dust(level, onGround(hand), 4.0, 10);
                for (LivingEntity e : victims(level)) {
                    if (e.getY() > floor + 6 || e.getY() < floor - 8 || swipeHit.contains(e.getId())) continue;
                    Vec3 rel = e.position().subtract(base);
                    double fc = rel.dot(f), rc = rel.dot(rt);
                    double dist = Math.sqrt(fc * fc + rc * rc), ang = Math.atan2(-rc, fc);
                    if (Math.abs(dist - r) > 5.2 + e.getBbWidth() / 2 || ang < lo || ang > hi) continue;
                    swipeHit.add(e.getId());
                    if (hit(e, 0.2F)) {
                        Vec3 out = rel.multiply(1, 0, 1).normalize();
                        fling(e, motion.scale(2.2).add(out.scale(0.9)).normalize().scale(2.7).add(0, 0.55, 0));
                        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 3.0F, 0.4F);
                        Fx.hit(level, e.position().add(0, 1, 0), 2.6F, BLUE);
                    }
                }
            }
        }
        if ((act == SpiritGiantEntity.SLAM_L || act == SpiritGiantEntity.SLAM_R) && (int) at == SpiritGiantEntity.SLAM_HIT) {
            Vec3 mark = g.handMark(act == SpiritGiantEntity.SLAM_L ? 0 : 1);
            impact(level, mark, 5.0, 0.24F);
            ring(mark, level.getGameTime());
            Fx.shock(level, mark, 20F, BLUE);
        }
        if (act == SpiritGiantEntity.SLAM_BOTH && (int) at == SpiritGiantEntity.SLAM2_HIT) {
            Vec3 mid = g.handMark(0).lerp(g.handMark(1), 0.5);
            for (int i = 0; i < 2; i++) impact(level, g.handMark(i), 5.5, 0.28F);
            long now = level.getGameTime();
            for (int k = 0; k < 3; k++) ring(mid, now + k * 7);
            Fx.shock(level, mid, 22F, BLUE);
        }
        if (act == SpiritGiantEntity.GRAB) tickGrab(level, (int) at);
        // La Embestida: its chest ploughing through the ground and its raking hands sweep up whoever is in its way
        if (act == SpiritGiantEntity.LUNGE && at >= SpiritGiantEntity.LUNGE_GO - 1 && at <= SpiritGiantEntity.LUNGE_STOP + 2) {
            Vec3 f = g.forward();
            Vec3 chest = g.base().add(f.scale(6));
            for (LivingEntity e : victims(level)) {
                if (e.getY() > chest.y + 6 || e.getY() < chest.y - 8 || swipeHit.contains(e.getId())) continue;
                double dc = Math.sqrt(e.distanceToSqr(chest.x, e.getY(), chest.z));
                Vec3 h0 = g.hand(0, 0), h1 = g.hand(1, 0);
                double dh = Math.min(Math.sqrt(e.distanceToSqr(h0.x, e.getY(), h0.z)), Math.sqrt(e.distanceToSqr(h1.x, e.getY(), h1.z)));
                if (dc > 7.0 + e.getBbWidth() / 2 && dh > 4.2) continue;
                swipeHit.add(e.getId());
                if (hit(e, 0.2F)) {
                    Vec3 side = e.position().subtract(chest).multiply(1, 0, 1);
                    side = side.lengthSqr() < 1e-4 ? g.right() : side.normalize();
                    fling(e, f.scale(1.8).add(side.scale(1.2)).normalize().scale(2.4).add(0, 0.7, 0));
                    Mechanics.stun(e);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 3.0F, 0.4F);
                    Fx.hit(level, e.position().add(0, 1, 0), 2.6F, BLUE);
                }
            }
        }
    }

    private void tell(ServerLevel level, int act) {
        Vec3 h = g.head(0);
        net.minecraft.sounds.SoundEvent s = switch (act) {
            case SpiritGiantEntity.CHARGE -> ModSounds.GIANT_LASER_CHARGE.get();
            case SpiritGiantEntity.GRAB -> ModSounds.GIANT_TELL_GRAB.get();
            case SpiritGiantEntity.SWIPE_L, SpiritGiantEntity.SWIPE_R -> ModSounds.GIANT_TELL_SWIPE.get();
            case SpiritGiantEntity.SLAM_L, SpiritGiantEntity.SLAM_R, SpiritGiantEntity.SLAM_BOTH -> ModSounds.GIANT_TELL_SLAM.get();
            case SpiritGiantEntity.LUNGE -> ModSounds.GIANT_TELL_LUNGE.get();
            default -> null;
        };
        if (s != null) level.playSound(null, h.x, h.y, h.z, s, SoundSource.HOSTILE, 8.0F, 1.0F);
    }

    /** La Mano, what it does to you (PaponBossEntity.tickGiantGrab): caught, hoisted, its eyes point-blank, slammed down. */
    private void tickGrab(ServerLevel level, int at) {
        int i = g.grabHand();
        if (at < SpiritGiantEntity.GRAB_WADE - 1 && target != null) {
            g.aim(target.position().add(0, 1, 0));
            if (at % 3 == 0) Fx.zone(level, onGround(predict(target, SpiritGiantEntity.GRAB_CLOSE - at)), 4.6F, 0x1A0A14, 4, 0.75F);
        }
        if (at == SpiritGiantEntity.GRAB_WADE - 1) {
            Vec3 m = target != null ? onGround(predict(target, SpiritGiantEntity.GRAB_CLOSE - at)) : grabMark != null ? grabMark : g.handMark(i);
            g.handTarget(i, m);
            grabMark = m;
            Fx.warnClosing(level, m, 4.4F, 0xFF2046, SpiritGiantEntity.GRAB_CLOSE - at);
            Fx.zone(level, m, 4.4F, 0x1A0A14, SpiritGiantEntity.GRAB_CLOSE - at, 0.85F);
            level.playSound(null, m.x, m.y, m.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3.0F, 0.4F);
        }
        if (at == SpiritGiantEntity.GRAB_CLOSE) {
            grabVictim = -1;
            grabFreed = false;
            Vec3 m = g.handMark(i);
            LivingEntity caught = null;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(m, m).inflate(5, 5, 5), this::hittable)) {
                if (e.distanceToSqr(m.x, e.getY(), m.z) > Mth.square(3.8 + e.getBbWidth() / 2) || e.getY() > m.y + 4.5) continue;
                if (caught == null && !e.isPassenger() && (e instanceof Player || target == e)) {
                    caught = e;
                    continue;
                }
                if (hit(e, 0.15F)) {
                    Vec3 away = e.position().subtract(m).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                    e.setDeltaMovement(away.scale(1.3).add(0, 0.7, 0));
                    e.hurtMarked = true;
                }
            }
            if (caught != null) {
                g.hold(caught);
                g.setStruggleNeed(10 + 3 * g.fury());
                grabVictim = caught.getId();
                hit(caught, 0.08F);
                caught.invulnerableTime = 0;
                level.playSound(null, m.x, m.y, m.z, SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 3.0F, 0.4F);
                level.playSound(null, m.x, m.y, m.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 3.0F, 0.5F);
                if (caught instanceof ServerPlayer sp) PaponNetwork.shake(sp, 1.2F, 14);
            } else {
                Fx.scorch(level, m, 4.5F, 260);
                ring(m, level.getGameTime());
            }
        }
        if (!g.caught()) return;
        LivingEntity le = grabVictim >= 0 && level.getEntity(grabVictim) instanceof LivingEntity v ? v : null;
        boolean held = le != null && g.holds(le);
        Vec3 h = g.hand(i, 0);
        if (le != null && !held && !grabFreed && at < SpiritGiantEntity.GRAB_SLAM) {
            // wriggled free: out of its fist and falling, gently
            grabFreed = true;
            le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 120, 0, false, false, true));
            Vec3 out = le.position().subtract(g.head(0)).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? g.forward() : out.normalize();
            le.setDeltaMovement(out.scale(0.5).add(0, 0.3, 0));
            le.hurtMarked = true;
            level.playSound(null, h.x, h.y, h.z, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.HOSTILE, 3.0F, 0.6F);
            level.playSound(null, h.x, h.y, h.z, SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 4.0F, 0.3F);
            Fx.spark(level, h, 24, 1.4, 0.5F, 0x9FF4FF);
        }
        if (at == SpiritGiantEntity.GRAB_LIFT && held) {
            Vec3 hd = g.head(0);
            level.playSound(null, hd.x, hd.y, hd.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0F, 0.32F);
            PaponNetwork.shake(level, hd, 120, 0.7F, 24);
        }
        if (at == SpiritGiantEntity.GRAB_BRING - 6) {
            Vec3 hd = g.head(0);
            level.playSound(null, hd.x, hd.y, hd.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 8.0F, 0.45F);
            level.playSound(null, hd.x, hd.y, hd.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 5.0F, 0.4F);
        }
        if (at > SpiritGiantEntity.GRAB_BRING && at < SpiritGiantEntity.GRAB_BLAST && at % 3 == 0) {
            for (int s2 : new int[]{-1, 1}) Fx.spark(level, g.eye(s2, 0), 4, 0.6, 0.35F, 0x9FF4FF);
            if (at % 9 == 0 && held && le instanceof ServerPlayer sp) PaponNetwork.shake(sp, 0.2F + 0.5F * (at - SpiritGiantEntity.GRAB_BRING)
                    / (SpiritGiantEntity.GRAB_BLAST - SpiritGiantEntity.GRAB_BRING), 8);
        }
        if (at == SpiritGiantEntity.GRAB_BLAST) {
            // point-blank, into its own fist (empty or not)
            for (int k = 0; k < 2; k++) {
                int side = k == 0 ? -1 : 1;
                BeamEntity b = BeamEntity.create(level, g.eye(side, 0), h, 4.2F, BLUE).life(12).eye(g, side);
                b.setMode(BeamEntity.FIRE);
                level.addFreshEntity(b);
            }
            Fx.flash(level, h, 4F);
            Fx.shock(level, h, 6F, BLUE);
            Fx.spark(level, h, 40, 2.2, 0.8F, 0xFFFFFF);
            Vec3 hd = g.head(0);
            level.playSound(null, hd.x, hd.y, hd.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 8.0F, 0.5F);
            level.playSound(null, h.x, h.y, h.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 6.0F, 0.6F);
            PaponNetwork.shake(level, h, 140, 1.0F, 24);
            if (held) {
                hit(le, 0.26F);
                le.invulnerableTime = 0;
                le.setSecondsOnFire(4);
                if (le instanceof ServerPlayer sp) PaponNetwork.shake(sp, 1.6F, 30);
            }
            Vec3 m = onGround(g.base().add(g.forward().scale(15)).add(g.right().scale(i == 0 ? 4 : -4)));
            g.handTarget(i, m);
            Fx.warnClosing(level, m, 6.5F, 0xFF2046, SpiritGiantEntity.GRAB_SLAM - at);
        }
        if (at == SpiritGiantEntity.GRAB_SLAM) {
            Vec3 m = g.handMark(i);
            if (held) {
                g.letGo();
                le.teleportTo(m.x, m.y + 0.1, m.z);
                le.setDeltaMovement(0, -0.2, 0);
                le.hurtMarked = true;
                hit(le, 0.14F);
                le.invulnerableTime = 0;
                Mechanics.stun(le);
                if (le instanceof Player p) p.setPose(net.minecraft.world.entity.Pose.SWIMMING);
                if (le instanceof ServerPlayer sp) PaponNetwork.shake(sp, 1.8F, 34);
                // the recovery window: nothing of its touches you for a moment
                mercy.put(le.getId(), level.getGameTime() + 50);
                next = Math.max(next, 60);
            }
            g.debris(level, m, 14, 0.8);
            Fx.flash(level, m.add(0, 1, 0), 4F);
            Fx.chunks(level, m.add(0, 0.5, 0), 36, 0.9F, 0x2C2438, 1.4);
            Fx.shock(level, m, 14F, BLUE);
            Fx.scorch(level, m, 6F, 400);
            long now = level.getGameTime();
            ring(m, now);
            ring(m, now + 8);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(m, m).inflate(6, 4, 6), this::hittable)) {
                if (e == le || e.distanceToSqr(m.x, e.getY(), m.z) > 25) continue;
                if (hit(e, 0.16F)) {
                    Vec3 away = e.position().subtract(m).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                    e.setDeltaMovement(away.scale(1.4).add(0, 0.8, 0));
                    e.hurtMarked = true;
                }
            }
            level.playSound(null, m.x, m.y, m.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 8.0F, 0.35F);
            level.playSound(null, m.x, m.y, m.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 5.0F, 0.3F);
            level.playSound(null, m.x, m.y, m.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 6.0F, 0.4F);
            PaponNetwork.shake(level, m, 140, 1.4F, 30);
            next = Math.max(next, 90);
        }
    }
}
