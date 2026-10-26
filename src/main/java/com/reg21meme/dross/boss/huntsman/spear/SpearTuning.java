package com.reg21meme.dross.boss.huntsman.spear;

/**
 * Every number of the Moonsilver Spear (the user, October 22, 2026: the Huntsman's spear for players, made with a
 * Moonsilver Ingot, "it has a freezing effect"; it stabs and it throws like a trident and comes back).
 */
public final class SpearTuning
{
    /** A stab's damage, all told (a trident's is 9, an iron sword's 6). */
    public static final double STAB_DAMAGE = 8.0D;
    /** Attack speed modifier: like a trident's, 1.1 blows a second. */
    public static final double ATTACK_SPEED = -2.9D;
    /** A thrown spear's hit (a trident's is 8). */
    public static final float THROW_DAMAGE = 8.0F;
    /** How fast it leaves the hand (a trident's 2.5) and how much it strays (a trident's 1). */
    public static final float THROW_POWER = 2.5F;
    public static final float THROW_INACCURACY = 1.0F;
    /** Ticks it must be drawn back before it's thrown (a trident's 10). */
    public static final int THROW_CHARGE_TICKS = 10;
    /** How hard it pulls back to its thrower once it has hit (a trident with Loyalty II). */
    public static final int RETURN_PULL = 2;
    /** Uses before it breaks (a trident's 250, a diamond sword's 1561). */
    public static final int DURABILITY = 500;
    public static final int ENCHANTABILITY = 10;
    /** The enchantments it takes besides Unbreaking, Mending and Curse of Vanishing (MoonsilverSpearItem). Impaling
     * adds its damage to a thrown spear's hit on anything, not only sea creatures. */
    public static final java.util.Set<net.minecraft.world.item.enchantment.Enchantment> EXTRA_ENCHANTMENTS = java.util.Set.of(
            net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,
            net.minecraft.world.item.enchantment.Enchantments.SMITE,
            net.minecraft.world.item.enchantment.Enchantments.IMPALING,
            net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING,
            net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK,
            net.minecraft.world.item.enchantment.Enchantments.CHANNELING);

    /** What its hit does: frozen solid for this long (4 s: the frost on the screen, shaking, slow) ... */
    public static final int FREEZE_TICKS = 80;
    /** ... and slowed (Slowness II) for as long. */
    public static final int FREEZE_SLOWNESS = 1;
    /** Snowflakes round what it froze. */
    public static final int FREEZE_PARTICLES = 14;

    /** Planted upright (the hunter villages' spears): its outline and collision box, in sixteenths. */
    public static final double PLANTED_HALF_WIDTH = 1.5D;
    /** The Huntsman's own spear is drawn this many times the size of a player's (his stands about 4.5 blocks). */
    public static final float HUNTSMAN_SPEAR_SCALE = 1.8F;

    private SpearTuning() {}
}
