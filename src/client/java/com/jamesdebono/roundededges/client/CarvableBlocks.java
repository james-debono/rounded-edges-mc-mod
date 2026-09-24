package com.jamesdebono.roundededges.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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

	private static final Set<Block> BLOCKS = new HashSet<>();

	private CarvableBlocks() {
	}

	/** Resolves the list against the block registry; ids this game version doesn't have are skipped and reported. */
	static void init() {
		List<String> missing = new ArrayList<>();
		for (String id : IDS) {
			BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(id)).ifPresentOrElse(BLOCKS::add, () -> missing.add(id));
		}
		RoundedEdgesClient.LOGGER.info("Carving {} block types", BLOCKS.size());
		if (!missing.isEmpty()) {
			RoundedEdgesClient.LOGGER.warn("Unknown blocks in the carvable list, skipped: {}", missing);
		}
	}

	/** Listed, and a full opaque cube (the carving assumes one; anything else is left alone). */
	public static boolean contains(BlockState state) {
		return BLOCKS.contains(state.getBlock()) && state.isSolidRender();
	}
}
