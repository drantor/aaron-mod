package net.azureaaron.mod.codecs;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.item.ItemStack;

public class LootCodec {

	public static final Codec<Map<String, ItemStack>> RARE_LOOT_CODEC = Codec.unboundedMap(Codec.STRING, ItemStack.CODEC);
	public static final Map<String, ItemStack> RARE_LOOT_ITEMS = new HashMap<>();

}
