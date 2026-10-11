package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.trophy.ColossusTrophies;
import com.reg21meme.dross.enchant.NecromancyScheme;
import com.reg21meme.dross.quest.DrossGuideBookItem;
import com.reg21meme.dross.quest.WeatheredLetterItem;
import com.reg21meme.dross.villager.DrossCompass;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs
{
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Dross.MODID);

    /**
     * The "Dross" creative tab. Its items are grouped by kind, in this order (the user's request); add a new item to
     * the end of the group it belongs to:
     * <ol>
     *   <li>Blocks: the portal frame and other building blocks</li>
     *   <li>Quest items: the Weathered Letter, Dross Compass, Rift Key, Guide Book</li>
     *   <li>Trophies: every design of every boss trophy</li>
     *   <li>Spawn eggs: the trader, the bosses and their minions, and any other mob</li>
     *   <li>Weapons and tools: the Admin Sword and the like</li>
     *   <li>Enchanted books: every level of Necromancy, then every level of Deathforged</li>
     *   <li>Materials: ore materials and the like (Raw Moonsilver, Moonsilver Ingot, Stormite Shard)</li>
     * </ol>
     */
    public static final RegistryObject<CreativeModeTab> DROSS = CREATIVE_TABS.register("dross",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dross"))
                    .icon(() -> new ItemStack(ModItems.DROSS_PORTAL_FRAME.get()))
                    .displayItems((parameters, output) -> {
                        // 1. Blocks
                        // Portal area: the Dross portal frame (creative only: no recipe, can't be mined)
                        output.accept(ModItems.DROSS_PORTAL_FRAME.get());
                        // Dimension area: the Dross's stone and ores, the silverwood set, Stormvine, then Silvermoss and the plants
                        output.accept(ModItems.DUSKSTONE.get());
                        output.accept(ModItems.DUSKSTONE_COAL_ORE.get());
                        output.accept(ModItems.DUSKSTONE_GOLD_ORE.get());
                        output.accept(ModItems.MOONSILVER_ORE.get());
                        output.accept(ModItems.STORMITE_ORE.get());
                        output.accept(ModItems.SILVERWOOD_LOG.get());
                        output.accept(ModItems.SILVERWOOD_WOOD.get());
                        output.accept(ModItems.STRIPPED_SILVERWOOD_LOG.get());
                        output.accept(ModItems.STRIPPED_SILVERWOOD_WOOD.get());
                        output.accept(ModItems.SILVERWOOD_PLANKS.get());
                        output.accept(ModItems.SILVERWOOD_LEAVES.get());
                        output.accept(ModItems.SILVERWOOD_SAPLING.get());
                        output.accept(ModItems.STORMVINE.get());
                        output.accept(ModItems.SILVERMOSS.get());
                        output.accept(ModItems.GLIMMER_FERN.get());
                        output.accept(ModItems.WISPBLOOM.get());
                        // Dimension area: the other biomes' blocks, biome by biome: Cinder Wastes, Blightwood, Rimefall,
                        // Nether Scar, then the Gravecrags
                        output.accept(ModItems.ASH.get());
                        output.accept(ModItems.ASH_LAYER.get());
                        output.accept(ModItems.CHARRED_LOG.get());
                        output.accept(ModItems.EMBERWEED.get());
                        output.accept(ModItems.CINDER_VENT.get());
                        output.accept(ModItems.SPORELOAM.get());
                        output.accept(ModItems.BLIGHTCAP_BLOCK.get());
                        output.accept(ModItems.ROTTING_BLIGHTCAP.get());
                        output.accept(ModItems.BLIGHTCAP_STEM.get());
                        output.accept(ModItems.GLOOMCAP.get());
                        output.accept(ModItems.SPOREPOD.get());
                        output.accept(ModItems.HOARFROST.get());
                        output.accept(ModItems.FROSTBITTEN_LOG.get());
                        output.accept(ModItems.FROSTBLOOM.get());
                        output.accept(ModItems.ICICLE.get());
                        output.accept(ModItems.SCARRED_NETHERRACK.get());
                        output.accept(ModItems.SCAR_ROOTS.get());
                        output.accept(ModItems.SOULWICK.get());
                        output.accept(ModItems.SCAR_VENT.get());
                        output.accept(ModItems.CRAGSTONE.get());
                        output.accept(ModItems.STORM_LICHEN.get());
                        output.accept(ModItems.CRAG_THISTLE.get());

                        // 2. Quest items
                        // Quest area: a Weathered Letter (gets its text once it's in your inventory)
                        output.accept(WeatheredLetterItem.createBlank());
                        // Villager area: a Dross Compass (it gets its target once it's in your inventory)
                        output.accept(DrossCompass.createBlank());
                        // Portal area: the Rift Key (throw it into a frame to light the portal)
                        output.accept(ModItems.RIFT_KEY.get());
                        // Quest area: the Dross Guide Book
                        output.accept(DrossGuideBookItem.create());

                        // 3. Trophies
                        // Boss area: the Cinder Colossus trophy, every design (the boss drops design ColossusTuning.DROP_DESIGN)
                        for (ColossusTrophies.Design design : ColossusTrophies.DESIGNS)
                        {
                            output.accept(design.stack());
                        }

                        // 4. Spawn eggs
                        // Villager area: the Dross Trader
                        output.accept(ModItems.DROSS_TRADER_SPAWN_EGG.get());
                        // Boss area: the Cinder Colossus (a fighting Colossus) and its minions
                        output.accept(ModItems.CINDER_COLOSSUS_SPAWN_EGG.get());
                        output.accept(ModItems.STONE_GOLEM_SPAWN_EGG.get());
                        output.accept(ModItems.LAVA_GOLEM_SPAWN_EGG.get());

                        // 5. Weapons and tools
                        // Villager area: the test-only Admin Sword
                        output.accept(ModItems.ADMIN_SWORD.get());

                        // 6. Enchanted books
                        // Enchantments area: a book for every level of Necromancy (I-V), then Deathforged (I-X).
                        // Necromancy books are marked as the five-level scheme, so they're never moved up a level.
                        for (int level = 1; level <= ModEnchantments.NECROMANCY.get().getMaxLevel(); level++)
                        {
                            output.accept(NecromancyScheme.createBook(level));
                        }
                        for (int level = 1; level <= ModEnchantments.DEATHFORGED.get().getMaxLevel(); level++)
                        {
                            output.accept(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ModEnchantments.DEATHFORGED.get(), level)));
                        }

                        // 7. Materials
                        // Dimension area: the Dross's ore materials
                        output.accept(ModItems.RAW_MOONSILVER.get());
                        output.accept(ModItems.MOONSILVER_INGOT.get());
                        output.accept(ModItems.STORMITE_SHARD.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
