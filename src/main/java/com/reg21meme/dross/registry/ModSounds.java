package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events (shared file: only add your own entries). Each one also needs an entry in
 * {@code assets/dross/sounds.json} (which sound files it plays) and a subtitle in {@code en_us.json}.
 */
public final class ModSounds
{
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Dross.MODID);

    // Boss area: the Cinder Colossus. No new audio files: sounds.json plays vanilla stone, iron golem, ravager, lava
    // and explosion sounds pitched down, for deep rumbling and grinding stone.
    public static final RegistryObject<SoundEvent> COLOSSUS_AMBIENT = register("entity.cinder_colossus.ambient");
    public static final RegistryObject<SoundEvent> COLOSSUS_AMBIENT_CORE = register("entity.cinder_colossus.ambient_core");
    public static final RegistryObject<SoundEvent> COLOSSUS_HURT = register("entity.cinder_colossus.hurt");
    public static final RegistryObject<SoundEvent> COLOSSUS_DEATH = register("entity.cinder_colossus.death");
    public static final RegistryObject<SoundEvent> COLOSSUS_STEP = register("entity.cinder_colossus.step");
    public static final RegistryObject<SoundEvent> COLOSSUS_ROAR = register("entity.cinder_colossus.roar");
    public static final RegistryObject<SoundEvent> COLOSSUS_SWIPE = register("entity.cinder_colossus.swipe");
    public static final RegistryObject<SoundEvent> COLOSSUS_RIP = register("entity.cinder_colossus.rip");
    public static final RegistryObject<SoundEvent> COLOSSUS_THROW = register("entity.cinder_colossus.throw");
    public static final RegistryObject<SoundEvent> COLOSSUS_SLAM = register("entity.cinder_colossus.slam");
    public static final RegistryObject<SoundEvent> COLOSSUS_CHARGE = register("entity.cinder_colossus.charge");
    public static final RegistryObject<SoundEvent> COLOSSUS_GALLOP = register("entity.cinder_colossus.gallop");
    public static final RegistryObject<SoundEvent> COLOSSUS_RUMBLE = register("entity.cinder_colossus.rumble");
    public static final RegistryObject<SoundEvent> COLOSSUS_ERUPT = register("entity.cinder_colossus.erupt");
    public static final RegistryObject<SoundEvent> COLOSSUS_LOB = register("entity.cinder_colossus.lob");
    public static final RegistryObject<SoundEvent> COLOSSUS_POUND = register("entity.cinder_colossus.pound");
    public static final RegistryObject<SoundEvent> COLOSSUS_SHELL_BREAK = register("entity.cinder_colossus.shell_break");
    public static final RegistryObject<SoundEvent> COLOSSUS_COOL = register("entity.cinder_colossus.cool");
    public static final RegistryObject<SoundEvent> COLOSSUS_CRUMBLE = register("entity.cinder_colossus.crumble");
    public static final RegistryObject<SoundEvent> COLOSSUS_ROCK_SHATTER = register("entity.colossus_rock.shatter");
    public static final RegistryObject<SoundEvent> LAVA_BLOB_SPLASH = register("entity.lava_blob.splash");
    // Boss area: the Colossus's minions.
    public static final RegistryObject<SoundEvent> STONE_GOLEM_AMBIENT = register("entity.stone_golem.ambient");
    public static final RegistryObject<SoundEvent> STONE_GOLEM_HURT = register("entity.stone_golem.hurt");
    public static final RegistryObject<SoundEvent> STONE_GOLEM_DEATH = register("entity.stone_golem.death");
    public static final RegistryObject<SoundEvent> STONE_GOLEM_STEP = register("entity.stone_golem.step");
    public static final RegistryObject<SoundEvent> GOLEM_EMERGE = register("entity.stone_golem.emerge");
    public static final RegistryObject<SoundEvent> LAVA_GOLEM_AMBIENT = register("entity.lava_golem.ambient");
    public static final RegistryObject<SoundEvent> LAVA_GOLEM_HURT = register("entity.lava_golem.hurt");
    public static final RegistryObject<SoundEvent> LAVA_GOLEM_DEATH = register("entity.lava_golem.death");
    public static final RegistryObject<SoundEvent> LAVA_GOLEM_FUSE = register("entity.lava_golem.fuse");
    public static final RegistryObject<SoundEvent> LAVA_GOLEM_EXPLODE = register("entity.lava_golem.explode");

    // Dimension area: the Sporepod bursting when something walks into it (vanilla pufferfish and fungus sounds, soft).
    public static final RegistryObject<SoundEvent> SPOREPOD_BURST = register("block.sporepod.burst");

    // Boss area: the Rat King and his rats. All original sounds, made by tools/ratking/sounds.py (the attack versions
    // for the fight are in sounds.json too, listed in boss/ratking/RatKingSounds; they're registered when used).
    public static final RegistryObject<SoundEvent> RAT_KING_AMBIENT = register("entity.rat_king.ambient");
    public static final RegistryObject<SoundEvent> RAT_KING_HURT = register("entity.rat_king.hurt");
    public static final RegistryObject<SoundEvent> RAT_KING_DEATH = register("entity.rat_king.death");
    public static final RegistryObject<SoundEvent> RAT_KING_STARTLE = register("entity.rat_king.startle");
    public static final RegistryObject<SoundEvent> RAT_KING_YAWN = register("entity.rat_king.yawn");
    public static final RegistryObject<SoundEvent> RAT_KING_SNORE = register("entity.rat_king.snore");
    public static final RegistryObject<SoundEvent> RAT_KING_SCREECH = register("entity.rat_king.screech");
    public static final RegistryObject<SoundEvent> RAT_KING_RAGE_WHISTLE_1 = register("entity.rat_king.rage_whistle_1");
    public static final RegistryObject<SoundEvent> RAT_KING_RAGE_WHISTLE_2 = register("entity.rat_king.rage_whistle_2");
    public static final RegistryObject<SoundEvent> RAT_KING_RAGE_WHISTLE_3 = register("entity.rat_king.rage_whistle_3");
    public static final RegistryObject<SoundEvent> RAT_KING_STOMP = register("entity.rat_king.stomp");
    public static final RegistryObject<SoundEvent> RAT_KING_STAFF_SLAM = register("entity.rat_king.staff_slam");
    public static final RegistryObject<SoundEvent> RAT_KING_STEP = register("entity.rat_king.step");
    public static final RegistryObject<SoundEvent> RAT_KING_DIG = register("entity.rat_king.dig");
    public static final RegistryObject<SoundEvent> RAT_KING_DIVE = register("entity.rat_king.dive");
    public static final RegistryObject<SoundEvent> RAT_KING_RUMBLE = register("entity.rat_king.rumble");
    public static final RegistryObject<SoundEvent> RAT_KING_POP = register("entity.rat_king.pop");
    public static final RegistryObject<SoundEvent> RAT_KING_STAFF_GRAB = register("entity.rat_king.staff_grab");
    public static final RegistryObject<SoundEvent> RAT_KING_THUD = register("entity.rat_king.thud");
    public static final RegistryObject<SoundEvent> RAT_KING_STAFF_CLATTER = register("entity.rat_king.staff_clatter");
    public static final RegistryObject<SoundEvent> RAT_KING_CROWN_CLINK = register("entity.rat_king.crown_clink");
    public static final RegistryObject<SoundEvent> RAT_AMBIENT = register("entity.rat.ambient");
    public static final RegistryObject<SoundEvent> RAT_HURT = register("entity.rat.hurt");
    public static final RegistryObject<SoundEvent> RAT_DEATH = register("entity.rat.death");
    public static final RegistryObject<SoundEvent> RAT_STEP = register("entity.rat.step");
    public static final RegistryObject<SoundEvent> ENRAGED_RAT_AMBIENT = register("entity.enraged_rat.ambient");
    public static final RegistryObject<SoundEvent> ENRAGED_RAT_HURT = register("entity.enraged_rat.hurt");
    public static final RegistryObject<SoundEvent> ENRAGED_RAT_DEATH = register("entity.enraged_rat.death");
    public static final RegistryObject<SoundEvent> ENRAGED_RAT_STEP = register("entity.enraged_rat.step");
    // Boss area: the Rat King's fight. Attack versions picked for now (the others stay in sounds.json for the user to
    // choose from): the sweep's staff strike, the cast's plague orb, the ball's burst, his call to his rats; the bites.
    public static final RegistryObject<SoundEvent> RAT_KING_ATTACK_STAFF = register("entity.rat_king.attack.staff_1");
    public static final RegistryObject<SoundEvent> RAT_KING_ATTACK_ORB = register("entity.rat_king.attack.orb_1");
    public static final RegistryObject<SoundEvent> RAT_KING_ATTACK_PLAGUE_BREATH = register("entity.rat_king.attack.plague_breath_2");
    public static final RegistryObject<SoundEvent> RAT_KING_ATTACK_SUMMON = register("entity.rat_king.attack.summon_1");
    public static final RegistryObject<SoundEvent> RAT_ATTACK = register("entity.rat.attack");
    public static final RegistryObject<SoundEvent> ENRAGED_RAT_ATTACK = register("entity.enraged_rat.attack");

    private static RegistryObject<SoundEvent> register(String name)
    {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Dross.MODID, name)));
    }

    private ModSounds() {}
}
