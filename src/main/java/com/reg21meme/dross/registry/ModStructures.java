package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.sewer.RatCavePiece;
import com.reg21meme.dross.dimension.sewer.SewerGrandPipePiece;
import com.reg21meme.dross.dimension.sewer.SewerNodePiece;
import com.reg21meme.dross.dimension.sewer.SewerPipePiece;
import com.reg21meme.dross.dimension.sewer.SewerStructure;
import com.reg21meme.dross.dimension.sewer.SewerTunnelPiece;
import com.reg21meme.dross.dimension.sites.BossSitePlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Structures: the kinds of world-generation structure the mod adds (used by data/dross/worldgen/structure/*.json), and the
 * kinds of piece they're built from (saved with the chunks, so their ids must never change).
 */
public final class ModStructures
{
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Dross.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Dross.MODID);
    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Dross.MODID);

    // Dimension area: dross:sewers, the sewer networks under Blightwood (structures dross:sewers and dross:rat_cave)
    public static final RegistryObject<StructureType<SewerStructure>> SEWERS = STRUCTURE_TYPES.register("sewers", () -> () -> SewerStructure.CODEC);

    // Dimension area: the sewers' pieces: a straight tunnel, a junction (crossing, room, pool room, hall, rough cave), a pipe
    // entrance, and the rat cave
    public static final RegistryObject<StructurePieceType> SEWER_TUNNEL = STRUCTURE_PIECES.register("sewer_tunnel",
            () -> (StructurePieceType.ContextlessType) SewerTunnelPiece::new);
    public static final RegistryObject<StructurePieceType> SEWER_NODE = STRUCTURE_PIECES.register("sewer_node",
            () -> (StructurePieceType.ContextlessType) SewerNodePiece::new);
    public static final RegistryObject<StructurePieceType> SEWER_PIPE = STRUCTURE_PIECES.register("sewer_pipe",
            () -> (StructurePieceType.ContextlessType) SewerPipePiece::new);
    public static final RegistryObject<StructurePieceType> RAT_CAVE = STRUCTURE_PIECES.register("rat_cave",
            () -> (StructurePieceType.ContextlessType) RatCavePiece::new);

    // Dimension area: the Rat King's sewer's one grand entrance (a crowned pipe, the poisoned ground round it)
    public static final RegistryObject<StructurePieceType> SEWER_GRAND_PIPE = STRUCTURE_PIECES.register("sewer_grand_pipe",
            () -> (StructurePieceType.ContextlessType) SewerGrandPipePiece::new);

    // Dimension area: dross:boss_site, structures that start only where the boss-site planner (dimension/sites/BossSites)
    // put a site (the sewers' structure sets)
    public static final RegistryObject<StructurePlacementType<BossSitePlacement>> BOSS_SITE_PLACEMENT = STRUCTURE_PLACEMENTS.register("boss_site",
            () -> () -> BossSitePlacement.CODEC);

    private ModStructures() {}
}
