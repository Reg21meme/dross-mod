package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.villager.DrossTrader;
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

    /** The Cinder Colossus golem (boss-builder's area). For now only its showcase: /dross showcase golems. */
    public static final RegistryObject<EntityType<CinderColossus>> CINDER_COLOSSUS = ENTITY_TYPES.register("cinder_colossus",
            () -> EntityType.Builder.of(CinderColossus::new, MobCategory.MONSTER)
                    .sized(4.0F, 5.0F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("cinder_colossus"));

    private ModEntities() {}
}
