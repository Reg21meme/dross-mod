package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.golem.ColossusRock;
import com.reg21meme.dross.boss.golem.LavaBlob;
import com.reg21meme.dross.boss.golem.LavaGolem;
import com.reg21meme.dross.boss.golem.StoneGolem;
import com.reg21meme.dross.boss.huntsman.AlphaMoose;
import com.reg21meme.dross.boss.huntsman.Bola;
import com.reg21meme.dross.boss.huntsman.HuntSpear;
import com.reg21meme.dross.boss.huntsman.spear.ThrownMoonsilverSpear;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import com.reg21meme.dross.boss.ratking.PlagueBall;
import com.reg21meme.dross.boss.ratking.PoisonCloud;
import com.reg21meme.dross.boss.ratking.Rat;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.boss.rimefall.Moose;
import com.reg21meme.dross.boss.rimefall.PondSkater;
import com.reg21meme.dross.boss.rimefall.RimefallCandidate;
import com.reg21meme.dross.boss.rimefall.Rimehorn;
import com.reg21meme.dross.villager.DrossTrader;
import com.reg21meme.dross.villager.Survivor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Dross.MODID);

    public static final RegistryObject<EntityType<DrossTrader>> TRADER = ENTITY_TYPES.register("dross_trader",
            () -> EntityType.Builder.of(DrossTrader::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("dross_trader"));

    /**
     * The Cinder Colossus golem mini-boss (boss-builder's area): the spawn egg, /dross boss summon colossus, and the
     * showcase golems (/dross showcase boss_designs colossus). It never spawns by itself.
     */
    public static final RegistryObject<EntityType<CinderColossus>> CINDER_COLOSSUS = ENTITY_TYPES.register("cinder_colossus",
            () -> EntityType.Builder.of(CinderColossus::new, MobCategory.MONSTER)
                    .sized(4.0F, 5.0F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("cinder_colossus"));

    // Boss area: the Cinder Colossus's minions (summoned in its fight, about a block tall)
    public static final RegistryObject<EntityType<StoneGolem>> STONE_GOLEM = ENTITY_TYPES.register("stone_golem",
            () -> EntityType.Builder.<StoneGolem>of(StoneGolem::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.15F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build("stone_golem"));
    public static final RegistryObject<EntityType<LavaGolem>> LAVA_GOLEM = ENTITY_TYPES.register("lava_golem",
            () -> EntityType.Builder.<LavaGolem>of(LavaGolem::new, MobCategory.MONSTER)
                    .sized(0.85F, 1.1F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build("lava_golem"));

    // Boss area: what the Cinder Colossus throws (a rock that never places a block) and spits (lava blobs)
    public static final RegistryObject<EntityType<ColossusRock>> COLOSSUS_ROCK = ENTITY_TYPES.register("colossus_rock",
            () -> EntityType.Builder.<ColossusRock>of(ColossusRock::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .fireImmune()
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .build("colossus_rock"));
    public static final RegistryObject<EntityType<LavaBlob>> LAVA_BLOB = ENTITY_TYPES.register("lava_blob",
            () -> EntityType.Builder.<LavaBlob>of(LavaBlob::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F)
                    .fireImmune()
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .build("lava_blob"));

    /**
     * Villager area: the hub's two survivors in the Dross, the Blind Oracle and the Ragged Seeker (one type; their role,
     * saved on each, picks the skin and name). Spawned once per world at the hub by villager.SurvivorSpawner.
     */
    public static final RegistryObject<EntityType<Survivor>> SURVIVOR = ENTITY_TYPES.register("survivor",
            () -> EntityType.Builder.of(Survivor::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("survivor"));

    /**
     * Boss area: the Rat King, the Plague Necromancer's servant: his fight (spawn egg, /dross boss summon ratking) and
     * the design showcase (/dross showcase boss_designs ratking). About 1.5 blocks wide with his cape and staff, 3 to 4 tall.
     */
    public static final RegistryObject<EntityType<RatKing>> RAT_KING = ENTITY_TYPES.register("rat_king",
            () -> EntityType.Builder.<RatKing>of(RatKing::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.4F)
                    .clientTrackingRange(10)
                    .build("rat_king"));

    // Boss area: the Rat King's rats, a regular rat and the enraged breed (he calls them in his fight; /dross showcase rats)
    public static final RegistryObject<EntityType<Rat>> RAT = ENTITY_TYPES.register("rat",
            () -> EntityType.Builder.<Rat>of(Rat::new, MobCategory.MONSTER)
                    .sized(0.55F, 0.5F)
                    .clientTrackingRange(8)
                    .build("rat"));
    public static final RegistryObject<EntityType<Rat>> ENRAGED_RAT = ENTITY_TYPES.register("enraged_rat",
            () -> EntityType.Builder.<Rat>of(Rat::new, MobCategory.MONSTER)
                    .sized(0.8F, 0.75F)
                    .clientTrackingRange(8)
                    .build("enraged_rat"));

    // Boss area: the Rat King's poison ball (his cast) and the poison cloud it bursts into
    public static final RegistryObject<EntityType<PlagueBall>> PLAGUE_BALL = ENTITY_TYPES.register("plague_ball",
            () -> EntityType.Builder.<PlagueBall>of(PlagueBall::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .build("plague_ball"));
    public static final RegistryObject<EntityType<PoisonCloud>> POISON_CLOUD = ENTITY_TYPES.register("poison_cloud",
            () -> EntityType.Builder.<PoisonCloud>of(PoisonCloud::new, MobCategory.MISC)
                    .sized(1.0F, 0.5F)
                    .fireImmune()
                    .clientTrackingRange(6)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("poison_cloud"));

    /**
     * Boss area: the Rimefall boss candidates, frozen models of the Ice Necromancer's 22 possible servants for
     * /dross showcase boss_designs rimefall. One type wears any of the 22 looks; its hitbox follows the look (from the
     * size of a player to about 15 blocks tall), so the size given here is only its first one.
     */
    public static final RegistryObject<EntityType<RimefallCandidate>> RIMEFALL_CANDIDATE = ENTITY_TYPES.register("rimefall_candidate",
            () -> EntityType.Builder.<RimefallCandidate>of(RimefallCandidate::new, MobCategory.MISC)
                    .sized(2.0F, 2.6F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build("rimefall_candidate"));

    /**
     * Boss area: Rimefall's animals (CLAUDE.md step 14), the only mobs that spawn there: the Rimehorn (a woolly rhino
     * about 4.5 blocks tall and 8 long, in packs; its square hitbox covers its body, its head and horn reach past it),
     * the wild moose (about 3.5-4 blocks tall with its antlers) and the Pond Skater (its body; each leg has a small
     * hitbox of its own, a PondSkaterPart).
     */
    public static final RegistryObject<EntityType<Rimehorn>> RIMEHORN = ENTITY_TYPES.register("rimehorn",
            () -> EntityType.Builder.<Rimehorn>of(Rimehorn::new, MobCategory.CREATURE)
                    .sized(3.4F, 4.2F)
                    .clientTrackingRange(10)
                    .build("rimehorn"));
    public static final RegistryObject<EntityType<Moose>> MOOSE = ENTITY_TYPES.register("moose",
            () -> EntityType.Builder.<Moose>of(Moose::new, MobCategory.CREATURE)
                    .sized(2.0F, 3.3F)
                    .clientTrackingRange(10)
                    .build("moose"));
    public static final RegistryObject<EntityType<PondSkater>> POND_SKATER = ENTITY_TYPES.register("pond_skater",
            () -> EntityType.Builder.<PondSkater>of(PondSkater::new, MobCategory.MONSTER)
                    .sized(1.44F, 1.84F)   // its body at RimefallMobTuning.SKATER_SCALE (0.8) of the model's 1.8 x 2.3
                    .clientTrackingRange(10)
                    .build("pond_skater"));

    /**
     * Boss area: the Huntsman ({@code dross:huntsman}, about 4.5 blocks to his hood on foot) and his Alpha Moose
     * ({@code dross:alpha_moose}, about 5.3 to its antler tips), who rides it in stage 1 of his fight (CLAUDE.md step
     * 14): his fight (a spawn egg, {@code /dross boss summon huntsman}) and on show
     * ({@code /dross showcase boss_designs huntsman}).
     */
    public static final RegistryObject<EntityType<Huntsman>> HUNTSMAN = ENTITY_TYPES.register("huntsman",
            () -> EntityType.Builder.<Huntsman>of(Huntsman::new, MobCategory.MONSTER)
                    .sized(1.2F, 4.4F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build("huntsman"));
    public static final RegistryObject<EntityType<AlphaMoose>> ALPHA_MOOSE = ENTITY_TYPES.register("alpha_moose",
            () -> EntityType.Builder.<AlphaMoose>of(AlphaMoose::new, MobCategory.MONSTER)
                    .sized(2.8F, 4.6F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build("alpha_moose"));
    // Boss area: the Huntsman's bola (a weighted rope of ice he throws: it snares what it hits)
    public static final RegistryObject<EntityType<Bola>> BOLA = ENTITY_TYPES.register("bola",
            () -> EntityType.Builder.<Bola>of(Bola::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .build("bola"));
    // Boss area: the Huntsman's thrown spear (it flies back to his hand; drawn with the Moonsilver Spear's model)
    public static final RegistryObject<EntityType<HuntSpear>> HUNT_SPEAR = ENTITY_TYPES.register("hunt_spear",
            () -> EntityType.Builder.<HuntSpear>of(HuntSpear::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("hunt_spear"));

    // Boss area: a thrown Moonsilver Spear (a player's: it comes back to its thrower, like a trident with Loyalty)
    public static final RegistryObject<EntityType<ThrownMoonsilverSpear>> MOONSILVER_SPEAR = ENTITY_TYPES.register("moonsilver_spear",
            () -> EntityType.Builder.<ThrownMoonsilverSpear>of(ThrownMoonsilverSpear::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("moonsilver_spear"));

    private ModEntities() {}
}
