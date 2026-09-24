package com.jamesdebono.roundededges.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.jamesdebono.roundededges.carve.EdgeMask;

/**
 * The blocks that get carved: naturally generated terrain, not building materials. Kept in code rather than a block
 * tag because tags come from the server, and this client-only mod must also work on servers that don't have it.
 */
public final class CarvableBlocks {
	private static final String[] IDS = {
			// Stone and friends
			"stone", "granite", "diorite", "andesite", "deepslate", "tuff", "calcite", "dripstone_block",
			"smooth_basalt", "infested_stone", "infested_deepslate", "cinnabar", "sulfur", "potent_sulfur",
			// Ores and ore-vein blocks
			"coal_ore", "deepslate_coal_ore", "copper_ore", "deepslate_copper_ore", "iron_ore", "deepslate_iron_ore",
			"gold_ore", "deepslate_gold_ore", "redstone_ore", "deepslate_redstone_ore", "emerald_ore",
			"deepslate_emerald_ore", "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore",
			"nether_gold_ore", "nether_quartz_ore", "ancient_debris", "raw_iron_block", "raw_copper_block",
			// Soil
			"dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mycelium", "mud", "clay", "moss_block",
			"pale_moss_block",
			// Sand and gravel
			"sand", "red_sand", "suspicious_sand", "gravel", "suspicious_gravel", "sandstone", "red_sandstone",
			// Badlands terracotta (the colours that generate there)
			"terracotta", "white_terracotta", "orange_terracotta", "yellow_terracotta", "brown_terracotta",
			"red_terracotta", "light_gray_terracotta",
			// Snow and ice (opaque kinds only)
			"snow_block", "packed_ice", "blue_ice",
			// Nether
			"netherrack", "soul_sand", "soul_soil", "basalt", "blackstone", "magma_block", "crimson_nylium",
			"warped_nylium", "glowstone",
			// End and misc
			"end_stone", "obsidian", "crying_obsidian", "bedrock", "sculk", "amethyst_block", "budding_amethyst",
	};

	/** Tree leaves: cut by the normal exposure rule, so canopies round off where they meet air. */
	private static final String[] LEAVES = {
			"oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves",
			"mangrove_leaves", "cherry_leaves", "azalea_leaves", "flowering_azalea_leaves", "pale_oak_leaves",
	};

	/** Tree trunks (and stripped ones): always cut the 4 edges along their axis, like the round sides of a real log. */
	private static final String[] LOGS = {
			"oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log", "mangrove_log",
			"cherry_log", "pale_oak_log", "crimson_stem", "warped_stem",
			"stripped_oak_log", "stripped_spruce_log", "stripped_birch_log", "stripped_jungle_log",
			"stripped_acacia_log", "stripped_dark_oak_log", "stripped_mangrove_log", "stripped_cherry_log",
			"stripped_pale_oak_log", "stripped_crimson_stem", "stripped_warped_stem",
	};

	/** 15/16-tall ground: cut from their real top. */
	private static final String[] SHORT = {"dirt_path", "farmland"};

	private static final Set<Block> BLOCKS = new HashSet<>();
	private static final Set<Block> LOG_BLOCKS = new HashSet<>();
	private static final Set<Block> SHORT_BLOCKS = new HashSet<>();

	private CarvableBlocks() {
	}

	/** Resolves the lists against the block registry; ids this game version doesn't have are skipped and reported. */
	static void init() {
		List<String> missing = new ArrayList<>();
		resolve(IDS, BLOCKS, missing);
		resolve(LEAVES, BLOCKS, missing);
		resolve(LOGS, LOG_BLOCKS, missing);
		resolve(SHORT, SHORT_BLOCKS, missing);
		BLOCKS.addAll(LOG_BLOCKS);
		BLOCKS.addAll(SHORT_BLOCKS);
		RoundedEdgesClient.LOGGER.info("Carving {} block types ({} logs)", BLOCKS.size(), LOG_BLOCKS.size());
		if (!missing.isEmpty()) {
			RoundedEdgesClient.LOGGER.warn("Unknown blocks in the carvable lists, skipped: {}", missing);
		}
	}

	private static void resolve(String[] ids, Set<Block> into, List<String> missing) {
		for (String id : ids) {
			BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(id)).ifPresentOrElse(into::add, () -> missing.add(id));
		}
	}

	/**
	 * Listed, and a shape the carving can work on: an opaque full cube, leaves (full-cube models with see-through
	 * textures), or 15/16-tall ground. Anything else is left alone.
	 */
	public static boolean contains(BlockState state) {
		Block block = state.getBlock();
		return BLOCKS.contains(block) && (state.isSolidRender() || block instanceof LeavesBlock || SHORT_BLOCKS.contains(block));
	}

	public static boolean isLeaves(BlockState state) {
		return state.getBlock() instanceof LeavesBlock;
	}

	/** 15/16 tall (dirt path, farmland). */
	public static boolean isShort(BlockState state) {
		return SHORT_BLOCKS.contains(state.getBlock());
	}

	/** For logs, the edges along the log's axis (always cut); -1 for blocks that follow the exposure rule. */
	public static int fixedMask(BlockState state) {
		if (!LOG_BLOCKS.contains(state.getBlock()) || !state.hasProperty(BlockStateProperties.AXIS)) {
			return -1;
		}
		return EdgeMask.alongAxis(switch (state.getValue(BlockStateProperties.AXIS)) {
			case Y -> 0;
			case Z -> 1;
			case X -> 2;
		});
	}
}
