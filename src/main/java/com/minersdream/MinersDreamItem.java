package com.minersdream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;

/**
 * The Miner's Dream item clears all non-ore blocks in an 11-wide × 70-long area
 * starting 1 block in front of the player in the direction they are facing.
 */
public class MinersDreamItem extends Item {
	/** Width of the clearing area (centered on the player's forward axis). */
	private static final int WIDTH = 11;
	/** Length of the clearing area in the player's facing direction. */
	private static final int LENGTH = 70;
	/** Half-width for symmetrical expansion around center axis. */
	private static final int HALF_WIDTH = WIDTH / 2;

	// Vanilla ore tag (minecraft:ores)
	private static final TagKey<Block> VANILLA_ORES_TAG = TagKey.create(
		Registries.BLOCK,
		Identifier.withDefaultNamespace("ores")
	);

	// Conventional ore tag (c:ores) used by Fabric API and cross-mod compatibility
	private static final TagKey<Block> CONVENTIONAL_ORES_TAG = TagKey.create(
		Registries.BLOCK,
		Identifier.fromNamespaceAndPath("c", "ores")
	);

	public MinersDreamItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		ItemStack stack = player.getItemInHand(hand);

		// Determine the horizontal facing direction of the player
		Direction facing = player.getDirection();
		BlockPos playerPos = player.blockPosition();

		// Calculate the perpendicular (right) direction for width expansion
		Direction right = facing.getClockWise();

		// Starting position: 1 block in front of the player
		BlockPos startPos = playerPos.relative(facing, 1);

		int removedCount = 0;

		// Iterate through the 11-wide × 70-long area, full height of player (3 blocks tall)
		for (int forward = 0; forward < LENGTH; forward++) {
			for (int sideways = -HALF_WIDTH; sideways <= HALF_WIDTH; sideways++) {
				// Clear a 4-block tall column (feet level, body, head, above head) to allow passage
				for (int y = 0; y <= 3; y++) {
					BlockPos targetPos = startPos
						.relative(facing, forward)
						.relative(right, sideways)
						.above(y);

					BlockState state = serverLevel.getBlockState(targetPos);

				// Only remove natural blocks found in caves/mines
					if (!isMineBlock(state.getBlock())) {
						continue;
					}

					serverLevel.destroyBlock(targetPos, false);
					removedCount++;
				}
			}
		}

		// Place torches every 7 blocks along the center of the path, starting 3 blocks away
		for (int forward = 3; forward < LENGTH; forward += 7) {
			BlockPos torchPos = startPos
				.relative(facing, forward);

			BlockState floorState = serverLevel.getBlockState(torchPos.below());
			if (!floorState.isAir() && !isOreBlock(floorState)) {
				serverLevel.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState());
			}
		}

		// Seal all tunnel boundaries: replace only fluid on boundary faces with stone
		// to permanently prevent lava and water from flowing into the cleared area
		for (int forward = 0; forward < LENGTH; forward++) {
			BlockPos forwardPos = startPos.relative(facing, forward);

			// Seal ceiling face (y = 4, full width)
			for (int sideways = -HALF_WIDTH; sideways <= HALF_WIDTH; sideways++) {
				BlockPos ceilingPos = forwardPos.relative(right, sideways).above(4);
				BlockState ceilingState = serverLevel.getBlockState(ceilingPos);
				if (!ceilingState.getFluidState().isEmpty()) {
					serverLevel.setBlockAndUpdate(ceilingPos, Blocks.STONE.defaultBlockState());
				}
			}

			// Seal left wall face (full height)
			for (int y = 0; y <= 3; y++) {
				BlockPos leftWallPos = forwardPos.relative(right, -HALF_WIDTH - 1).above(y);
				BlockState leftState = serverLevel.getBlockState(leftWallPos);
				if (!leftState.getFluidState().isEmpty()) {
					serverLevel.setBlockAndUpdate(leftWallPos, Blocks.STONE.defaultBlockState());
				}
			}

			// Seal right wall face (full height)
			for (int y = 0; y <= 3; y++) {
				BlockPos rightWallPos = forwardPos.relative(right, HALF_WIDTH + 1).above(y);
				BlockState rightState = serverLevel.getBlockState(rightWallPos);
				if (!rightState.getFluidState().isEmpty()) {
					serverLevel.setBlockAndUpdate(rightWallPos, Blocks.STONE.defaultBlockState());
				}
			}
		}

		// Cleanup: destroy any fluids that leaked into the tunnel during sealing
		for (int forward = 0; forward < LENGTH; forward++) {
			for (int sideways = -HALF_WIDTH; sideways <= HALF_WIDTH; sideways++) {
				for (int y = 0; y <= 3; y++) {
					BlockPos targetPos = startPos
						.relative(facing, forward)
						.relative(right, sideways)
						.above(y);
					BlockState state = serverLevel.getBlockState(targetPos);
					if (!state.getFluidState().isEmpty()) {
						serverLevel.setBlockAndUpdate(targetPos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}

		// Play a satisfying sound effect
		if (removedCount > 0) {
			level.playSound(null, playerPos, SoundEvents.GENERIC_EXPLODE.value(),
				SoundSource.PLAYERS, 0.5f, 1.2f);
		}

		// Damage/consume the item after use
		stack.shrink(1);

		// Cooldown to prevent spam (40 ticks = 2 seconds)
		player.getCooldowns().addCooldown(stack, 40);

		return InteractionResult.SUCCESS;
	}

	/**
	 * Checks if a block state represents an ore.
	 * Uses multiple detection methods for maximum compatibility.
	 */
	private boolean isOreBlock(BlockState state) {
		// Check vanilla ore tag (minecraft:ores)
		if (state.is(VANILLA_ORES_TAG)) {
			return true;
		}

		// Check conventional ore tag (c:ores)
		if (state.is(CONVENTIONAL_ORES_TAG)) {
			return true;
		}

		// Fallback: check against known vanilla ore blocks
		return isKnownOre(state.getBlock());
	}

	/**
	 * Hardcoded fallback for known vanilla ore blocks.
	 * This ensures ores are preserved even without proper tagging.
	 */
	private boolean isKnownOre(Block block) {
		return block == Blocks.COAL_ORE
			|| block == Blocks.DEEPSLATE_COAL_ORE
			|| block == Blocks.IRON_ORE
			|| block == Blocks.DEEPSLATE_IRON_ORE
			|| block == Blocks.COPPER_ORE
			|| block == Blocks.DEEPSLATE_COPPER_ORE
			|| block == Blocks.GOLD_ORE
			|| block == Blocks.DEEPSLATE_GOLD_ORE
			|| block == Blocks.REDSTONE_ORE
			|| block == Blocks.DEEPSLATE_REDSTONE_ORE
			|| block == Blocks.EMERALD_ORE
			|| block == Blocks.DEEPSLATE_EMERALD_ORE
			|| block == Blocks.LAPIS_ORE
			|| block == Blocks.DEEPSLATE_LAPIS_ORE
			|| block == Blocks.DIAMOND_ORE
			|| block == Blocks.DEEPSLATE_DIAMOND_ORE
			|| block == Blocks.NETHER_GOLD_ORE
			|| block == Blocks.NETHER_QUARTZ_ORE
			|| block == Blocks.ANCIENT_DEBRIS;
	}

	/**
	 * Checks if a block is a natural block found in caves and mines.
	 * Only these blocks get removed - everything else (planks, chests, spawners, bricks, etc.) is preserved.
	 */
	private boolean isMineBlock(Block block) {
		return block == Blocks.STONE
			|| block == Blocks.DEEPSLATE
			|| block == Blocks.GRANITE
			|| block == Blocks.DIORITE
			|| block == Blocks.ANDESITE
			|| block == Blocks.TUFF
			|| block == Blocks.DIRT
			|| block == Blocks.COARSE_DIRT
			|| block == Blocks.ROOTED_DIRT
			|| block == Blocks.GRAVEL
			|| block == Blocks.CLAY
			|| block == Blocks.SAND
			|| block == Blocks.RED_SAND
			|| block == Blocks.MOSS_BLOCK
			|| block == Blocks.PACKED_MUD
			|| block == Blocks.MUD
			|| block == Blocks.MANGROVE_ROOTS
			|| block == Blocks.MUD_BRICKS
			|| block == Blocks.END_STONE
			// Nether blocks
			|| block == Blocks.NETHERRACK
			|| block == Blocks.SOUL_SAND
			|| block == Blocks.SOUL_SOIL
			|| block == Blocks.BASALT
			|| block == Blocks.SMOOTH_BASALT
			|| block == Blocks.BLACKSTONE
			|| block == Blocks.POLISHED_BLACKSTONE
			|| block == Blocks.GLOWSTONE
			|| block == Blocks.MAGMA_BLOCK
			|| block == Blocks.NETHER_WART_BLOCK
			|| block == Blocks.WARPED_WART_BLOCK
			|| block == Blocks.CRIMSON_NYLIUM
			|| block == Blocks.WARPED_NYLIUM
			|| block == Blocks.CRIMSON_STEM
			|| block == Blocks.WARPED_STEM
			|| block == Blocks.CRIMSON_HYPHAE
			|| block == Blocks.WARPED_HYPHAE
			|| block == Blocks.SHROOMLIGHT;
	}
}
