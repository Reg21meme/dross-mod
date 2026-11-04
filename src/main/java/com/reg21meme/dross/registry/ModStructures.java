package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.fire.cave.HermitCavePiece;
import com.reg21meme.dross.boss.fire.cave.HermitCavePlacement;
import com.reg21meme.dross.boss.fire.cave.HermitCaveStructure;
import com.reg21meme.dross.boss.huntsman.village.HunterDecorFilter;
import com.reg21meme.dross.boss.huntsman.village.HunterTerrace;
import com.reg21meme.dross.boss.huntsman.village.HunterVillagePiece;
import com.reg21meme.dross.boss.huntsman.village.HunterVillageStructure;
import com.reg21meme.dross.dimension.rimefall.RimefallLakePiece;
import com.reg21meme.dross.dimension.rimefall.RimefallLakeStructure;
import com.reg21meme.dross.dimension.sewer.RatCavePiece;
import com.reg21meme.dross.dimension.sewer.SewerGrandPipePiece;
import com.reg21meme.dross.dimension.sewer.SewerNodePiece;
import com.reg21meme.dross.dimension.sewer.SewerPipePiece;
import com.reg21meme.dross.dimension.sewer.SewerStructure;
import com.reg21meme.dross.dimension.sewer.SewerTunnelPiece;
import com.reg21meme.dross.dimension.sites.BossSitePlacement;
import com.reg21meme.dross.portal.DrossHubPiece;
import com.reg21meme.dross.portal.DrossHubPlacement;
import com.reg21meme.dross.portal.DrossHubStructure;
import com.reg21meme.dross.villager.RiftChapelPiece;
import com.reg21meme.dross.villager.RiftChapelPlacement;
import com.reg21meme.dross.villager.RiftChapelStructure;
import com.reg21meme.dross.world.PortalSitePiece;
import com.reg21meme.dross.world.PortalSitePlacement;
import com.reg21meme.dross.world.PortalSiteStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
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
    public static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Dross.MODID);

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

    // Dimension area: dross:rimefall_lake, each Rimefall patch's big frozen lake (dimension/rimefall), and its one piece
    public static final RegistryObject<StructureType<RimefallLakeStructure>> RIMEFALL_LAKE = STRUCTURE_TYPES.register("rimefall_lake",
            () -> () -> RimefallLakeStructure.CODEC);
    public static final RegistryObject<StructurePieceType> RIMEFALL_LAKE_PIECE = STRUCTURE_PIECES.register("rimefall_lake",
            () -> (StructurePieceType.ContextlessType) RimefallLakePiece::new);

    // Boss area: dross:hunter_village, Rimefall's hunter villages (the Huntsman's and regular ones; boss/huntsman/village),
    // and its one piece
    public static final RegistryObject<StructureType<HunterVillageStructure>> HUNTER_VILLAGE = STRUCTURE_TYPES.register("hunter_village",
            () -> () -> HunterVillageStructure.CODEC);
    public static final RegistryObject<StructurePieceType> HUNTER_VILLAGE_PIECE = STRUCTURE_PIECES.register("hunter_village",
            () -> (StructurePieceType.ContextlessType) HunterVillagePiece::new);

    // Boss area: dross:hunter_decor, leaves out the decorations a hunter village didn't roll (boss/huntsman/village)
    public static final RegistryObject<StructureProcessorType<HunterDecorFilter>> HUNTER_DECOR = STRUCTURE_PROCESSORS.register("hunter_decor",
            () -> () -> HunterDecorFilter.CODEC);

    // Boss area: dross:hunter_terrace, puts each part of a hunter village on its own ground (boss/huntsman/village)
    public static final RegistryObject<StructureProcessorType<HunterTerrace>> HUNTER_TERRACE = STRUCTURE_PROCESSORS.register("hunter_terrace",
            () -> () -> HunterTerrace.CODEC);

    // Boss area: dross:hermit_cave, the Cinder Hermit's small caves in the Cinder Wastes (boss/fire/cave): the structure,
    // its one piece (a template), and its placement (only in the chunks boss/fire/cave/HermitCaveSites planned a cave)
    public static final RegistryObject<StructureType<HermitCaveStructure>> HERMIT_CAVE = STRUCTURE_TYPES.register("hermit_cave",
            () -> () -> HermitCaveStructure.CODEC);
    public static final RegistryObject<StructurePieceType> HERMIT_CAVE_PIECE = STRUCTURE_PIECES.register("hermit_cave",
            () -> (StructurePieceType.StructureTemplateType) HermitCavePiece::new);
    public static final RegistryObject<StructurePlacementType<HermitCavePlacement>> HERMIT_CAVE_PLACEMENT = STRUCTURE_PLACEMENTS.register("hermit_cave",
            () -> () -> HermitCavePlacement.CODEC);

    // Portal area: dross:dross_hub, the hub at Dross 0,0 (the Shattered Spire with the survivors' camp and its lit exit
    // portal; portal/DrossHub*), made with the land: the structure, its one piece, and its placement (only in the chunk
    // holding the spire's middle)
    public static final RegistryObject<StructureType<DrossHubStructure>> DROSS_HUB = STRUCTURE_TYPES.register("dross_hub",
            () -> () -> DrossHubStructure.CODEC);
    public static final RegistryObject<StructurePieceType> DROSS_HUB_PIECE = STRUCTURE_PIECES.register("dross_hub",
            () -> (StructurePieceType.ContextlessType) DrossHubPiece::new);
    public static final RegistryObject<StructurePlacementType<DrossHubPlacement>> DROSS_HUB_PLACEMENT = STRUCTURE_PLACEMENTS.register("dross_hub",
            () -> () -> DrossHubPlacement.CODEC);

    // World site area: dross:portal_site, the Overworld's portal site (the Fallen Cathedral round its unlit Dross frame;
    // world/PortalSite*), one per world at the spot the seed picks, made with the land: the structure, its one piece, and its
    // placement (only in the chunk holding the site column)
    public static final RegistryObject<StructureType<PortalSiteStructure>> PORTAL_SITE = STRUCTURE_TYPES.register("portal_site",
            () -> () -> PortalSiteStructure.CODEC);
    public static final RegistryObject<StructurePieceType> PORTAL_SITE_PIECE = STRUCTURE_PIECES.register("portal_site",
            () -> (StructurePieceType.ContextlessType) PortalSitePiece::new);
    public static final RegistryObject<StructurePlacementType<PortalSitePlacement>> PORTAL_SITE_PLACEMENT = STRUCTURE_PLACEMENTS.register("portal_site",
            () -> () -> PortalSitePlacement.CODEC);

    // Villager area: dross:rift_chapel, the Dross trader's Rift Chapels (villager/RiftChapel*), rare in the Overworld's
    // countryside on a seeded grid, each with its trader, made with the land: the structure, its one piece, and its
    // placement (vanilla's random_spread, kept off the villages and the portal site)
    public static final RegistryObject<StructureType<RiftChapelStructure>> RIFT_CHAPEL = STRUCTURE_TYPES.register("rift_chapel",
            () -> () -> RiftChapelStructure.CODEC);
    public static final RegistryObject<StructurePieceType> RIFT_CHAPEL_PIECE = STRUCTURE_PIECES.register("rift_chapel",
            () -> (StructurePieceType.ContextlessType) RiftChapelPiece::new);
    public static final RegistryObject<StructurePlacementType<RiftChapelPlacement>> RIFT_CHAPEL_PLACEMENT = STRUCTURE_PLACEMENTS.register("rift_chapel",
            () -> () -> RiftChapelPlacement.CODEC);

    private ModStructures() {}
}
