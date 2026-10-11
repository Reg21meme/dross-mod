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
                    })
                    .build());

    private ModCreativeTabs() {}
}
