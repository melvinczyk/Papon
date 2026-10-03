package com.groid.papon.boss;

/**
 * What the stands of El Coliseo Papa are doing (synced as the ordinal; append at the end). They
 * are Papons: they are on his side.
 */
public enum CrowdMood {
    /** The usual: on their feet or not, bouncing, waving, the odd fist in the air. */
    CHEER,
    /** Everyone up, arms up, jumping: his entrance, the music hitting, a big hit on you. */
    ROAR,
    /** Fists shaken and thumbs down at you: you hurt him. */
    BOO,
    /** Sitting quiet, looking at the band stand: the band is asleep. */
    HUSH,
    /** "PA-PÓN! PA-PÓN!": fists on the beat. */
    CHANT,
    /** La ola, round the stands. */
    WAVE,
    /** Clapping on the beat. */
    CLAP,
    /** Swooning at him: hands to their faces, a few fainting. */
    SWOON,
    /** He fell: heads down, hats off. */
    MOURN,
    /** On their feet, hands on their heads. */
    GASP;

    public static CrowdMood byId(int id) {
        CrowdMood[] all = values();
        return id >= 0 && id < all.length ? all[id] : CHEER;
    }
}
