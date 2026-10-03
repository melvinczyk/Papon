package com.groid.papon.entity.npc;

import com.groid.papon.entity.Speech;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Set;

/**
 * Who is in line at the Taco Truck, and what each of them does there.
 *
 * Every customer orders at the window, waits while the Papon cooks, takes the taco, then walks
 * back down the line to whoever is standing in it and reacts to the food in character before
 * wandering off. The line is the countdown to the boss fight, so these timings are the clock:
 * a customer occupies the window for orderTicks + cookCycles * COOK + SERVE ticks.
 *
 * Animation names are suffixes in customer.animation.json (humans) or papon_npc.animation.json
 * (Papons); reactTicks must equal the react animation's length. Lines are lang keys, timed in
 * ticks from the start of the reaction; "speech.papon.<id>.<line>". Spanish lines have a
 * ".gloss" translation that chat prints under them.
 */
public enum Customer {
    DON_POLLO("don_pollo", true, ChatFormatting.GOLD, 70, 1, 120, 0.88F,
            new Speech.Voice(SoundEvents.CHICKEN_AMBIENT, 0.85F, 0.5F, 4), true,
            List.of(new Line(8, "leave", 64), new Line(78, "phone", 42))),
    EDP445("edp445", true, ChatFormatting.DARK_GREEN, 60, 2, 160, 1.15F,
            new Speech.Voice(SoundEvents.VILLAGER_AMBIENT, 0.62F, 0.22F, 8), false,
            List.of(new Line(30, "leave", 70), new Line(118, "chant", 36)),
            Set.of("walk", "order", "receive", "walk_taco", "hold_taco")),
    BADLANDS_CHUGS("badlands_chugs", true, ChatFormatting.RED, 60, 2, 140, 1.1F,
            new Speech.Voice(SoundEvents.VILLAGER_AMBIENT, 0.5F, 0.22F, 8), false,
            List.of(new Line(0, "watch", 40), new Line(88, "leave", 44)),
            Set.of("walk", "order", "receive", "walk_taco", "hold_taco")),
    REVIEWBRAH("reviewbrah", true, ChatFormatting.AQUA, 90, 1, 120, 0.88F,
            new Speech.Voice(SoundEvents.VILLAGER_TRADE, 0.82F, 0.4F, 4), false,
            List.of(new Line(36, "leave", 58), new Line(94, "signoff", 26))),
    PAPON("customer_papon", false, ChatFormatting.YELLOW, 50, 1, 50, 1.0F,
            new Speech.Voice(SoundEvents.WANDERING_TRADER_AMBIENT, 1.45F, 0.35F, 4), true,
            List.of(new Line(4, "leave", 46)));

    /** Ticks of one cooking pass at the griddle (the vendor's cook animation). */
    public static final int COOK = 80;
    /** Ticks the vendor takes to hand the taco over (serve animation; the customer's receive). */
    public static final int SERVE = 30;
    /** Number of different leave lines a Papon customer picks from. */
    public static final int PAPON_LINES = 4;

    public final String id;
    public final boolean human;
    public final ChatFormatting color;
    public final int orderTicks;
    public final int cookCycles;
    public final int reactTicks;
    public final float renderScale;
    public final Speech.Voice voice;
    public final boolean spanish;
    public final List<Line> lines;
    /** Shared animations ("walk", "order", ...) this character has his own version of ("walk_<id>"). */
    public final Set<String> own;

    Customer(String id, boolean human, ChatFormatting color, int orderTicks, int cookCycles, int reactTicks,
             float renderScale, Speech.Voice voice, boolean spanish, List<Line> lines) {
        this(id, human, color, orderTicks, cookCycles, reactTicks, renderScale, voice, spanish, lines, Set.of());
    }

    Customer(String id, boolean human, ChatFormatting color, int orderTicks, int cookCycles, int reactTicks,
             float renderScale, Speech.Voice voice, boolean spanish, List<Line> lines, Set<String> own) {
        this.id = id;
        this.human = human;
        this.color = color;
        this.orderTicks = orderTicks;
        this.cookCycles = cookCycles;
        this.reactTicks = reactTicks;
        this.renderScale = renderScale;
        this.voice = voice;
        this.spanish = spanish;
        this.lines = lines;
        this.own = own;
    }

    public String key(String line) {
        return "speech.papon." + (this == PAPON ? "papon" : id) + "." + line;
    }

    /** Full GeckoLib animation name for one of this customer's animations ("walk", "order", ...). */
    public String anim(String name) {
        if (own.contains(name)) name = name + "_" + id;
        return human ? "animation.customer." + name : "animation.papon_npc.customer_" + name;
    }

    /** This customer's own idle while waiting (props in hand), without the file prefix. */
    public String idleAnim() {
        return human ? "idle_" + id : "idle";
    }

    public String reactAnim() {
        return human ? "react_" + id : "react";
    }

    /** A spoken line, {@code at} ticks into the reaction, showing for {@code ticks}. */
    public record Line(int at, String name, int ticks) {}
}
