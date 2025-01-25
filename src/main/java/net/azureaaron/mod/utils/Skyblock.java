package net.azureaaron.mod.utils;

import static net.azureaaron.mod.codecs.EnchantmentCodec.MAX_ENCHANTMENTS_CODEC;
import static net.azureaaron.mod.codecs.EnchantmentCodec.MAX_LEVEL_ENCHANTMENTS;
import static net.azureaaron.mod.codecs.LootCodec.RARE_LOOT_CODEC;
import static net.azureaaron.mod.codecs.LootCodec.RARE_LOOT_ITEMS;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.*;
import net.azureaaron.mod.utils.cache.CacheService;
import net.azureaaron.mod.utils.exceptions.LoadDataException;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;

import net.azureaaron.mod.Main;
import net.azureaaron.mod.commands.skyblock.MagicalPowerCommand;
import net.azureaaron.mod.config.AaronModConfigManager;
import net.azureaaron.mod.utils.Http.ApiResponse;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.Identifier;

public class Skyblock {
    private static final Logger LOGGER = LogUtils.getLogger();
    //TODO refactor the codecs into their own classes

    private static final Map<String, MagicalPowerCommand.MagicalPowerData> MAGICAL_POWERS = new HashMap<>();
    private static final Map<String, MagicalPowerCommand.Accessory> ACCESSORIES = new HashMap<>();

    private static final int RETRY_TIME_MINUTES = 3;

    private static boolean loaded;
    private static boolean enchantsLoaded;

    public static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(Skyblock::reloadData);
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static void reloadData(MinecraftClient client) {
        reloadData(client, false);
    }

    public static void reloadData(MinecraftClient client, boolean force) {
        if (!loaded || force) {
            if (force) {
                // Reset flag if we force a reload
                loaded = false;
                enchantsLoaded = false;
            }
            CompletableFuture.allOf(
                    loadRareLootItems(client),
                    loadMaxEnchants(false),
                    loadMagicalPowers(),
                    loadAccessories()
            ).whenComplete((_result, throwable) -> {
                if (throwable == null) {
                    LOGGER.info("[Aaron's Mod] Loaded all data");
                    loaded = true;
                    if (!MAX_LEVEL_ENCHANTMENTS.isEmpty()) {
                        CacheService.writeToCache("maxenchantments", MAX_ENCHANTMENTS_CODEC, MAX_LEVEL_ENCHANTMENTS);
                    }
                    if (!MAGICAL_POWERS.isEmpty()) {
                        CacheService.writeToCache("magicalpowers", MagicalPowerCommand.MagicalPowerData.MAP_CODEC, MAGICAL_POWERS);
                    }
                    if (!ACCESSORIES.isEmpty()) {
                        CacheService.writeToCache("accessories", MagicalPowerCommand.Accessory.MAP_CODEC, ACCESSORIES);
                    }
                } else {
                    LOGGER.warn("[Aaron's Mod] Failed to load some data will try to read from cache and retry later.");
                    if (MAX_LEVEL_ENCHANTMENTS.isEmpty()) {
                        CacheService.readFromCache("maxenchantments", MAX_ENCHANTMENTS_CODEC)
                                .ifSuccess(MAX_LEVEL_ENCHANTMENTS::addAll);
                    }
                    if (MAGICAL_POWERS.isEmpty()) {
                        CacheService.readFromCache("magicalpowers", MagicalPowerCommand.MagicalPowerData.MAP_CODEC)
                                .ifSuccess(MAGICAL_POWERS::putAll);
                    }
                    if (ACCESSORIES.isEmpty()) {
                        CacheService.readFromCache("accessories", MagicalPowerCommand.Accessory.MAP_CODEC)
                                .ifSuccess(ACCESSORIES::putAll);
                    }
                    // Something wasn't loaded so we retry it later
                    Scheduler.schedule(() -> reloadData(client), RETRY_TIME_MINUTES, TimeUnit.MINUTES);
                }
            });
        }
    }

    private static CompletableFuture<Void> loadRareLootItems(MinecraftClient client) {
        if (!RARE_LOOT_ITEMS.isEmpty()) {
            // Don't need to reload data from the file
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = client.getResourceManager().openAsReader(Identifier.of(Main.NAMESPACE, "skyblock/rare_loot_items.json"))) {
                RegistryOps<JsonElement> ops = ItemUtils.getRegistryLookup().getOps(JsonOps.INSTANCE);

                return RARE_LOOT_CODEC.parse(ops, JsonParser.parseReader(reader)).getOrThrow();
            } catch (Exception e) {
                throw new LoadDataException("Failed to load rare loot items file", e);
            }
        }).thenAccept((result) -> {
            RARE_LOOT_ITEMS.clear();
            RARE_LOOT_ITEMS.putAll(result);
        });
    }

    //Maybe load the enchants from file as backup?
    public static CompletableFuture<Void> loadMaxEnchants(boolean loadAnyways) {
        return CompletableFuture.supplyAsync(() -> {
                    if ((AaronModConfigManager.get().rainbowifyMaxSkyblockEnchantments || AaronModConfigManager.get().enableSkyblockCommands || loadAnyways) && !enchantsLoaded) {
                        try {
                            ApiResponse response = Http.sendAaronRequest("skyblock/maxenchantments");
                            return MAX_ENCHANTMENTS_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(response.content())).getOrThrow();
                        } catch (Exception e) {
                            LOGGER.error("[Aaron's Mod] Failed to load max enchantments file!", e);

                            throw new LoadDataException("Failed to load max enchantments file!", e);
                        }
                    } else {
                        return List.<String>of();
                    }
                })
                .thenAccept((result) -> {
                    MAX_LEVEL_ENCHANTMENTS.clear();
                    MAX_LEVEL_ENCHANTMENTS.addAll(result);
                })
                .thenRun(() -> Functions.runIf(() -> enchantsLoaded = true, () -> !MAX_LEVEL_ENCHANTMENTS.isEmpty()));
    }

    private static CompletableFuture<Void> loadMagicalPowers() {
        return CompletableFuture.supplyAsync(() -> {
            if (AaronModConfigManager.get().enableSkyblockCommands) {
                try {
                    ApiResponse response = Http.sendAaronRequest("skyblock/magicalpowers");
                    return MagicalPowerCommand.MagicalPowerData.MAP_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(response.content())).getOrThrow();
                } catch (Exception e) {
                    LOGGER.error("[Aaron's Mod] Failed to load magical powers file!", e);

                    throw new LoadDataException("Failed to load magical powers file!", e);
                }
            } else {
                return Map.<String, MagicalPowerCommand.MagicalPowerData>of();
            }
        }).thenAccept((result) -> {
            MAGICAL_POWERS.clear();
            MAGICAL_POWERS.putAll(result);
        });
    }

    private static CompletableFuture<Void> loadAccessories() {
        return CompletableFuture.supplyAsync(() -> {
            if (AaronModConfigManager.get().enableSkyblockCommands) {
                try {
                    ApiResponse response = Http.sendAaronRequest("skyblock/accessories");
                    return MagicalPowerCommand.Accessory.MAP_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(response.content())).getOrThrow();
                } catch (Exception e) {
                    LOGGER.error("[Aaron's Mod] Failed to load accessories!", e);

                    throw new LoadDataException("Failed to load accessories!", e);
                }
            } else {
                return Map.<String, MagicalPowerCommand.Accessory>of();
            }
        }).thenAccept((result) -> {
            ACCESSORIES.clear();
            ACCESSORIES.putAll(result);
        });
    }

    public static Map<String, ItemStack> getRareLootItems() {
        return RARE_LOOT_ITEMS;
    }

    public static List<String> getMaxEnchants() {
        return MAX_LEVEL_ENCHANTMENTS;
    }

    public static Map<String, MagicalPowerCommand.MagicalPowerData> getMagicalPowers() {
        return MAGICAL_POWERS;
    }

    public static Map<String, MagicalPowerCommand.Accessory> getAccessories() {
        return ACCESSORIES;
    }

    public static JsonObject getSelectedProfile2(String profiles) throws IllegalStateException {
        if (profiles == null) return null;

        JsonObject skyblockData = JsonParser.parseString(profiles).getAsJsonObject();

        if (skyblockData.get("profiles").isJsonNull())
            throw new IllegalStateException(Messages.NO_SKYBLOCK_PROFILES_ERROR.get().getString()); //If the player's profile hasn't been migrated or they got wiped

        JsonArray profilesArray = skyblockData.getAsJsonArray("profiles");

        for (JsonElement profile : profilesArray) {
            JsonObject iteratedProfile = profile.getAsJsonObject();
            if (iteratedProfile.get("selected").getAsBoolean()) return iteratedProfile;
        }

        throw new IllegalStateException(Messages.PROFILES_NOT_MIGRATED_ERROR.get().getString()); //After the migration players can apparently have no selected profile
    }

    public static boolean isInventoryApiEnabled(JsonObject inventoryData) {
        return inventoryData != null && inventoryData.has("inv_contents");
    }

    public static boolean isSkillsApiEnabled(JsonObject profile) {
        return profile.getAsJsonObject("player_data").has("experience");
    }

    public static String getDojoGrade(int score) {
        return switch ((Integer) score) {
            case Integer ignored5 when score == 0 -> "None";
            case Integer ignored4 when score >= 1000 -> "S";
            case Integer ignored3 when score >= 800 -> "A";
            case Integer ignored2 when score >= 600 -> "B";
            case Integer ignored1 when score >= 400 -> "C";
            case Integer ignored when score >= 200 -> "D";
            default -> "F";
        };
    }

    public static int calculateProfileSocialXp(JsonObject profile) {
        int socialXp = 0;
        JsonObject members = profile.getAsJsonObject("members");

        for (String uuid : members.keySet()) {
            JsonObject member = members.getAsJsonObject(uuid);
            socialXp += JsonHelper.getInt(member, "player_data.experience.SKILL_SOCIAL").orElse(0);
        }

        return socialXp;
    }
}
