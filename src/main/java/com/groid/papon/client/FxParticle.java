package com.groid.papon.client;

import com.groid.papon.fx.FxOptions;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Every particle of the boss fight (see ModParticles): one class, a {@link Mode} per type. Animated
 * types play their frames over their life; flat ones (ground warnings, shockwaves, swing arcs) are laid
 * on the ground or at the swing's height instead of facing the camera. Fire, flashes and warnings are
 * drawn at full brightness so they read at night and in the dome.
 */
public class FxParticle extends TextureSheetParticle {
    public enum Mode {
        FIRE(true, false, true, 0), FLAME(true, false, true, 0), BLAST(true, false, true, 0), BURST(true, false, true, 0),
        SMOKE(true, false, false, 0), DUST(true, false, false, 0), SLASH(true, true, true, 0), HIT(true, false, true, 0),
        SHOCK(true, true, true, 0), DECAL(false, true, true, 8), SPARK(false, false, true, 4), CHUNK(false, false, false, 3),
        FLASH(true, false, true, 0);

        final boolean animated, flat, bright;
        final int variants;

        Mode(boolean animated, boolean flat, boolean bright, int variants) {
            this.animated = animated;
            this.flat = flat;
            this.bright = bright;
            this.variants = variants;
        }
    }

    private final Mode mode;
    private final SpriteSet sprites;
    private final float size0;
    private final float grow;
    private final float alpha0;
    private final float yaw;
    private final float spin;

    protected FxParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, FxOptions o, Mode mode,
                         SpriteSet sprites) {
        super(level, x, y, z);
        this.mode = mode;
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.lifetime = Math.max(1, o.life());
        this.size0 = o.size();
        this.quadSize = o.size();
        this.grow = o.grow();
        this.alpha0 = o.alpha();
        this.alpha = mode == Mode.DECAL ? 0F : o.alpha();
        this.yaw = o.yaw() * Mth.DEG_TO_RAD;
        this.spin = mode == Mode.DECAL && o.sprite() == com.groid.papon.fx.Fx.DECAL_SIGIL ? 0.04F : 0F;
        setColor(((o.color() >> 16) & 255) / 255F, ((o.color() >> 8) & 255) / 255F, (o.color() & 255) / 255F);
        this.friction = 0.92F;
        this.hasPhysics = mode == Mode.CHUNK;
        this.gravity = switch (mode) {
            case CHUNK -> 1.0F;
            case SPARK -> 0.35F;
            case SMOKE, DUST -> -0.01F;
            default -> 0F;
        };
        if (mode.animated) {
            setSpriteFromAge(sprites);
        } else {
            int n = mode.variants;
            int k = o.sprite() >= 0 ? Math.min(o.sprite(), n - 1) : random.nextInt(n);
            setSprite(sprites.get(k, n - 1));
        }
        this.roll = mode.flat ? 0 : (mode == Mode.CHUNK || mode == Mode.SMOKE ? random.nextFloat() * Mth.TWO_PI : 0);
        this.oRoll = roll;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) return;
        float f = (float) age / lifetime;
        if (mode.animated) setSpriteFromAge(sprites);
        quadSize = size0 * (1F + (grow - 1F) * f);
        switch (mode) {
            case DECAL -> {
                // fades in, holds (a warning pulses faster as it runs out), and is gone the tick it lands
                float in = Math.min(1F, age / 4F);
                float pulse = 0.8F + 0.2F * Mth.sin(age * (0.3F + f * 0.9F));
                alpha = alpha0 * in * pulse;
            }
            case SHOCK, SLASH, HIT, FLASH -> alpha = alpha0 * (f < 0.6F ? 1F : 1F - (f - 0.6F) / 0.4F);
            case SMOKE, DUST, CHUNK, SPARK -> alpha = alpha0 * (f < 0.5F ? 1F : 1F - (f - 0.5F) / 0.5F);
            default -> {}
        }
        if (spin != 0) {
            oRoll = roll;
            roll += spin;
        }
    }

    @Override
    public void render(VertexConsumer vc, Camera camera, float partialTick) {
        if (!mode.flat) {
            super.render(vc, camera, partialTick);
            return;
        }
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTick, xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(partialTick, yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(partialTick, zo, this.z) - cam.z);
        float s = getQuadSize(partialTick);
        float a = yaw + Mth.lerp(partialTick, oRoll, roll);
        float c = Mth.cos(a) * s, sn = Mth.sin(a) * s;
        // corners of a square in the ground plane, turned by a
        float[][] p = {{-c + sn, -sn - c}, {-c - sn, -sn + c}, {c - sn, sn + c}, {c + sn, sn - c}};
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTick);
        float[][] uv = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
        for (int i = 0; i < 4; i++) {
            vc.vertex(x + p[i][0], y, z + p[i][1]).uv(uv[i][0], uv[i][1]).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        }
        // and the back, so it shows from under a dome floor or a slash from below
        for (int i = 3; i >= 0; i--) {
            vc.vertex(x + p[i][0], y, z + p[i][1]).uv(uv[i][0], uv[i][1]).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return mode.bright ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Mode mode) implements ParticleProvider<FxOptions> {
        @Override
        public Particle createParticle(FxOptions o, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new FxParticle(level, x, y, z, dx, dy, dz, o, mode, sprites);
        }
    }
}
