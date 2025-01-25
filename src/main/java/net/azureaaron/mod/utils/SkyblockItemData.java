package net.azureaaron.mod.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.azureaaron.mod.config.AaronModConfigManager;
import net.azureaaron.mod.utils.networth.NetworthDataSuppliers;

public class SkyblockItemData {
	private static final Logger LOGGER = LogUtils.getLogger();

	private static final int RETRY_TIME_MINUTES = 3;

	public static void init() {
		if (!AaronModConfigManager.get().enableSkyblockCommands) return;

		CompletableFuture.supplyAsync(() -> {
			try {
				JsonObject itemsResponse = JsonParser.parseString(Http.sendGetRequest("https://api.hypixel.net/v2/resources/skyblock/items")).getAsJsonObject();
				return JsonHelper.getArray(itemsResponse, "items").orElseGet(JsonArray::new);
			} catch (Exception e) {
				LOGGER.error("[Aaron's Mod Skyblock Item Data Loader] Failed to load Skyblock Item Data.", e);
			}

			//Complete the future exceptionally so that the other things don't run
			throw new IllegalStateException();
		}).thenAcceptAsync(items -> {
			Cache.populate(items);
			NetworthDataSuppliers.updateSkyblockItemData(items);
		}).whenComplete((_result, throwable) -> {
			if (throwable != null) {
				LOGGER.warn("[Aaron's Mod Skyblock Item Data Loader] Will retry to load data later");
				Scheduler.schedule(SkyblockItemData::init, RETRY_TIME_MINUTES, TimeUnit.MINUTES);
			}
		});
	}
}
