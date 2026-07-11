package techreborn.items.tool;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import reborncore.common.powerSystem.RcEnergyItem;
import reborncore.common.powerSystem.RcEnergyTier;
import reborncore.common.util.ItemUtils;

import java.util.*;

public class OreProspectorItem extends Item implements RcEnergyItem {
	/** Number of closest chunks to scan, including the player's current chunk */
	private final int maxChunks;
	private final int maxEnergy;
	private final int energyCost;
	private final RcEnergyTier tier;

	private static final TagKey<Block> ORES_TAG = TagKey.of(net.minecraft.registry.RegistryKeys.BLOCK, Identifier.of("c", "ores"));

	public OreProspectorItem(int maxChunks, int maxEnergy, int energyCost, RcEnergyTier tier) {
		super(new Item.Settings().maxCount(1));
		this.maxChunks = maxChunks;
		this.maxEnergy = maxEnergy;
		this.energyCost = energyCost;
		this.tier = tier;
	}

	// Item
	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);

		if (world.isClient) {
			return new TypedActionResult<>(ActionResult.SUCCESS, stack);
		}

		if (getStoredEnergy(stack) < energyCost) {
			player.sendMessage(Text.translatable("tooltip.techreborn.ore_prospector.no_energy").formatted(Formatting.RED), true);
			return new TypedActionResult<>(ActionResult.FAIL, stack);
		}

		tryUseEnergy(stack, energyCost);

		Map<String, Integer> oreCounts = scanOres(world, player.getBlockPos());

		sendResults((ServerPlayerEntity) player, oreCounts);

		return new TypedActionResult<>(ActionResult.SUCCESS, stack);
	}

	/**
	 * Collect the chunk offsets closest to the center, sorted by squared distance,
	 * then pick the nearest {@code maxChunks} chunks (including the center chunk).
	 */
	private static List<ChunkPos> getClosestChunks(ChunkPos center, int maxChunks) {
		// Search radius large enough to always cover the requested count
		int searchRadius = (int) Math.ceil(Math.sqrt(maxChunks)) + 2;
		List<ChunkPos> all = new ArrayList<>();
		for (int dx = -searchRadius; dx <= searchRadius; dx++) {
			for (int dz = -searchRadius; dz <= searchRadius; dz++) {
				all.add(new ChunkPos(center.x + dx, center.z + dz));
			}
		}
		all.sort(Comparator.comparingInt(p -> {
			int cx = p.x - center.x;
			int cz = p.z - center.z;
			return cx * cx + cz * cz;
		}));
		return all.subList(0, Math.min(maxChunks, all.size()));
	}

	private Map<String, Integer> scanOres(World world, BlockPos playerPos) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		ChunkPos centerChunk = new ChunkPos(playerPos);
		List<ChunkPos> chunksToScan = getClosestChunks(centerChunk, maxChunks);

		int minY = world.getBottomY();
		int maxY = world.getTopY() - 1;

		for (ChunkPos chunk : chunksToScan) {
			int startX = chunk.x << 4;
			int startZ = chunk.z << 4;

			for (int dx = 0; dx < 16; dx++) {
				for (int dz = 0; dz < 16; dz++) {
					for (int y = minY; y <= maxY; y++) {
						BlockPos pos = new BlockPos(startX + dx, y, startZ + dz);
						BlockState state = world.getBlockState(pos);
						if (state.isIn(ORES_TAG)) {
							String name = Registries.BLOCK.getId(state.getBlock()).toString();
							counts.merge(name, 1, Integer::sum);
						}
					}
				}
			}
		}
		return counts;
	}

	private void sendResults(ServerPlayerEntity player, Map<String, Integer> oreCounts) {
		if (oreCounts.isEmpty()) {
			player.sendMessage(Text.translatable("tooltip.techreborn.ore_prospector.no_ores")
				.formatted(Formatting.GOLD), false);
			return;
		}

		player.sendMessage(Text.translatable("tooltip.techreborn.ore_prospector.header", maxChunks)
			.formatted(Formatting.AQUA, Formatting.BOLD), false);

		// Sort by count descending, then by name
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(oreCounts.entrySet());
		sorted.sort((a, b) -> {
			int cmp = b.getValue().compareTo(a.getValue());
			if (cmp != 0) return cmp;
			return a.getKey().compareTo(b.getKey());
		});

		for (Map.Entry<String, Integer> entry : sorted) {
			MutableText line = Text.literal("  • ")
				.formatted(Formatting.GRAY)
				.append(Text.translatable("block." +
					entry.getKey().replace(":", "."))
					.formatted(Formatting.WHITE))
				.append(Text.literal(": " + entry.getValue())
					.formatted(Formatting.YELLOW));
			player.sendMessage(line, false);
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.techreborn.ore_prospector.chunks", maxChunks)
			.formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.techreborn.ore_prospector.cost", energyCost)
			.formatted(Formatting.GRAY));
	}

	// ItemDurabilityExtensions
	@Override
	public int getItemBarStep(ItemStack stack) {
		return ItemUtils.getPowerForDurabilityBar(stack);
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		return ItemUtils.getColorForDurabilityBar(stack);
	}

	// RcEnergyItem
	@Override
	public long getEnergyCapacity(ItemStack stack) {
		return maxEnergy;
	}

	@Override
	public RcEnergyTier getTier() {
		return tier;
	}

	@Override
	public long getEnergyMaxOutput(ItemStack stack) {
		return 0;
	}
}
