package com.groid.papon.boss;

/**
 * Who is who in the band, by place on the stage (Coliseum.bandSpot): the singer up front at his
 * microphone with his requinto, three trumpets on the riser, three violins, guitar, vihuela and the
 * guitarron; and the coro, three backup singers on the riser with a hand mic and a maraca, who
 * sing with him wherever the song has its harmonies. Each has his own build (some taller, some a
 * lot rounder) and his instrument's bones. Synced as the ordinal: append at the end.
 */
public enum BandRole {
    SINGER("singer", "requinto", 1.12F, 1.22F),
    TRUMPET_1("trumpet", "trumpet", 1.0F, 1.02F),
    TRUMPET_2("trumpet", "trumpet", 1.28F, 0.92F),
    TRUMPET_3("trumpet", "trumpet", 0.9F, 1.16F),
    VIOLIN_1("violin", "violin", 1.04F, 1.06F),
    VIOLIN_2("violin", "violin", 0.9F, 1.22F),
    VIOLIN_3("violin", "violin", 1.3F, 0.96F),
    GUITAR("guitar", "guitar", 1.1F, 1.0F),
    VIHUELA("guitar", "vihuela", 0.94F, 0.94F),
    GUITARRON("guitar", "guitarron", 1.46F, 1.1F),
    CORO_1("coro", "maraca", 1.06F, 1.08F),
    CORO_2("coro", "maraca", 0.92F, 1.2F),
    CORO_3("coro", "maraca", 1.22F, 0.98F);

    /** Which set of animations he uses (grab_, ready_, play_). */
    public final String kind;
    /** His instrument's bone (and its "_floor" copy). */
    public final String instrument;
    public final float width;
    public final float height;

    BandRole(String kind, String instrument, float width, float height) {
        this.kind = kind;
        this.instrument = instrument;
        this.width = width;
        this.height = height;
    }

    public static BandRole byId(int id) {
        BandRole[] all = values();
        return id >= 0 && id < all.length ? all[id] : SINGER;
    }

    public boolean singer() {
        return this == SINGER;
    }

    /** The one who falls straight back asleep and has to have his chair kicked. */
    public boolean dozer() {
        return this == GUITARRON;
    }

    /** Who switches the stage's floodlights on at nightfall (and off at dawn). */
    public boolean lamplighter() {
        return this == CORO_3;
    }

    public boolean coro() {
        return kind.equals("coro");
    }

    /** "grab_guitar" etc.: the singer's requinto uses the guitarists' animations. */
    public String animKind() {
        return kind.equals("singer") ? "guitar" : kind;
    }
}
