package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.golem.ColossusRock;
import com.reg21meme.dross.boss.golem.LavaBlob;
import com.reg21meme.dross.boss.golem.LavaGolem;
import com.reg21meme.dross.boss.golem.StoneGolem;
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

    private ModEntities() {}
}
