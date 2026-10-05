package com.groid.papon.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A "Z" that drifts up and sideways from a sleeping Papon, growing and fading. */
public class SnoozeParticle extends TextureSheetParticle {
    private final float baseSize;
    private final double sway;

    protected SnoozeParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
        this.lifetime = 50;
        this.gravity = 0;
        this.hasPhysics = false;
        this.baseSize = 0.12F + random.nextFloat() * 0.05F;
        this.quadSize = baseSize;
        this.sway = random.nextDouble() * Mth.TWO_PI;
        this.xd = 0;
        this.yd = 0.025;
        this.zd = 0;
    }

    @Override
    public void tick() {
        super.tick();
        float f = (float) age / lifetime;
        this.quadSize = baseSize * (1F + f * 1.2F);
        this.alpha = f < 0.7F ? 1F : 1F - (f - 0.7F) / 0.3F;
        this.xd = Math.sin(sway + age * 0.15) * 0.012;
        this.zd = Math.cos(sway + age * 0.15) * 0.012;
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            SnoozeParticle p = new SnoozeParticle(level, x, y, z);
            p.pickSprite(sprites);
            return p;
        }
    }
}
