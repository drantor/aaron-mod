package net.azureaaron.mod.utils.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.azureaaron.mod.utils.Constants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class CacheService {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CACHE_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("aaron-mod")
            .resolve("cache");

    public static <T> void writeToCache(String cacheFileName, Codec<T> codec, T input) {
        if (!cacheFileName.endsWith(".json")) {
            cacheFileName += ".json";
        }

        DataResult<JsonElement> dataResult = codec.encodeStart(JsonOps.INSTANCE, input);
        if (dataResult.isError()) {
            LOGGER.error(Constants.LOG_PREFIX + "Failed to encode data for {}", cacheFileName);
            return;
        }

        Path cacheFile = CACHE_PATH.resolve(cacheFileName);
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(
                    cacheFile,
                    GSON.toJson(dataResult.getOrThrow()),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException ioe) {
            LOGGER.error(Constants.LOG_PREFIX + "Failed to save cache file: {}", cacheFile, ioe);
        }
    }

    public static <T> DataResult<T> readFromCache(String cacheFileName, Codec<T> codec) {
        Path cacheFile = CACHE_PATH.resolve(cacheFileName);
        if (!Files.exists(cacheFile)) {
            return DataResult.error(() -> "No cache");
        }
        try (BufferedReader bufferedReader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            JsonElement jsonElement = JsonParser.parseReader(bufferedReader);
            return codec.parse(JsonOps.INSTANCE, jsonElement);
        } catch (IOException ioe) {
            LOGGER.error(Constants.LOG_PREFIX + "Failed to read cache file: {}", cacheFile, ioe);
        }
        return DataResult.error(() -> "Failed to read cache");
    }

}
