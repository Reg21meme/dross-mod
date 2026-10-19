package com.reg21meme.dross.boss.rimefall;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real Minecraft blocks some candidates are drawn with (the Fisher's bait cabin, {@link RimefallDesigns.Placed}):
 * their block states, written like a command's ({@code minecraft:spruce_stairs[facing=east,half=bottom]}), read once
 * each and kept. A state that can't be read (a typo) comes out as air (nothing drawn) and is logged once.
 */
public final class RimefallBlocks
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();

    private RimefallBlocks() {}

    /** The block state written this way; throws if it can't be read. */
    public static BlockState parse(String state) throws CommandSyntaxException
    {
        return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
    }

    /** The block state written this way (read once and kept), or air if it can't be read. */
    public static BlockState stateOf(String state)
    {
        return STATES.computeIfAbsent(state, text -> {
            try
            {
                return parse(text);
            }
            catch (CommandSyntaxException e)
            {
                LOGGER.error("Rimefall candidate: can't read the block state '{}': {}", text, e.getMessage());
                return Blocks.AIR.defaultBlockState();
            }
        });
    }
}
