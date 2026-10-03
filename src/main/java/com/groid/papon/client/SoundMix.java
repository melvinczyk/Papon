package com.groid.papon.client;

import com.groid.papon.PaponMod;
import com.groid.papon.boss.PaponBossEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * The fight's mix, so his voice always reads: every one-shot sound played near Chef Papon (his attacks,
 * blocks breaking, hits; anything but his voice, the music and the interface) is brought down a little,
 * and a lot further while he is talking (VoiceDuck). The band and the crowd duck themselves.
 */
@Mod.EventBusSubscriber(modid = PaponMod.MOD_ID, value = Dist.CLIENT)
public final class SoundMix {
    /** How loud the fight's sound effects are next to him (they were drowning him out). */
    private static final float FIGHT = 0.6F;
    private static final double NEAR = 56;

    private SoundMix() {}

    @SubscribeEvent
    public static void onPlay(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null || s instanceof TickableSoundInstance || s.isRelative()) return;
        SoundSource src = s.getSource();
        if (src == SoundSource.MASTER || src == SoundSource.MUSIC || src == SoundSource.RECORDS || src == SoundSource.VOICE
                || src == SoundSource.AMBIENT) return;
        ResourceLocation id = s.getLocation();
        if (PaponMod.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("voice.")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.level.getEntitiesOfClass(PaponBossEntity.class, new AABB(s.getX(), s.getY(), s.getZ(), s.getX(), s.getY(), s.getZ()).inflate(NEAR),
                e -> true).isEmpty()) return;
        float k = FIGHT * Math.min(1F, VoiceDuck.get() + 0.15F);
        event.setSound(new Scaled(s, k));
    }

    /** A sound played as it was, only quieter. */
    private record Scaled(SoundInstance s, float k) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return s.getLocation(); }
        @Override @Nullable public WeighedSoundEvents resolve(SoundManager m) { return s.resolve(m); }
        @Override public Sound getSound() { return s.getSound(); }
        @Override public SoundSource getSource() { return s.getSource(); }
        @Override public boolean isLooping() { return s.isLooping(); }
        @Override public boolean isRelative() { return s.isRelative(); }
        @Override public int getDelay() { return s.getDelay(); }
        @Override public float getVolume() { return s.getVolume() * k; }
        @Override public float getPitch() { return s.getPitch(); }
        @Override public double getX() { return s.getX(); }
        @Override public double getY() { return s.getY(); }
        @Override public double getZ() { return s.getZ(); }
        @Override public Attenuation getAttenuation() { return s.getAttenuation(); }
        @Override public boolean canStartSilent() { return s.canStartSilent(); }
        @Override public boolean canPlaySound() { return s.canPlaySound(); }
    }
}
