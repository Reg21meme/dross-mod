package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophyBlockEntity;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyBlockEntity;
import com.reg21meme.dross.boss.trophy.ColossusTrophyBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Dross.MODID);

    // Boss area: the Cinder Colossus trophy (holds its GeckoLib figure)
    @SuppressWarnings("DataFlowIssue") // vanilla's builder takes a null data-fixer type
    public static final RegistryObject<BlockEntityType<ColossusTrophyBlockEntity>> COLOSSUS_TROPHY = BLOCK_ENTITIES.register("colossus_trophy",
            () -> BlockEntityType.Builder.of(ColossusTrophyBlockEntity::new, ModBlocks.COLOSSUS_TROPHY.get()).build(null));

    // Boss area: the Rat King trophy (holds its GeckoLib figure)
    @SuppressWarnings("DataFlowIssue") // vanilla's builder takes a null data-fixer type
    public static final RegistryObject<BlockEntityType<RatKingTrophyBlockEntity>> RAT_KING_TROPHY = BLOCK_ENTITIES.register("rat_king_trophy",
            () -> BlockEntityType.Builder.of(RatKingTrophyBlockEntity::new, ModBlocks.RAT_KING_TROPHY.get()).build(null));

    // Boss area: the Huntsman trophy (holds its GeckoLib figure)
    @SuppressWarnings("DataFlowIssue") // vanilla's builder takes a null data-fixer type
    public static final RegistryObject<BlockEntityType<HuntsmanTrophyBlockEntity>> HUNTSMAN_TROPHY = BLOCK_ENTITIES.register("huntsman_trophy",
            () -> BlockEntityType.Builder.of(HuntsmanTrophyBlockEntity::new, ModBlocks.HUNTSMAN_TROPHY.get()).build(null));

    private ModBlockEntities() {}
}
