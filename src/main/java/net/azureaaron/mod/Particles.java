package net.azureaaron.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.google.gson.JsonObject;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.azureaaron.mod.util.Functions;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class Particles {
	//TODO Eventually transform this into a map of records or something to deduplicate the data
	public static final Object2ObjectOpenHashMap<Identifier, State> PARTICLE_STATES = new Object2ObjectOpenHashMap<>();
	public static final Object2FloatOpenHashMap<Identifier> PARTICLE_SCALES = new Object2FloatOpenHashMap<>();
	private static final Reference2ObjectOpenHashMap<ParticleType<?>, String> PARTICLE_DESCRIPTIONS = Util.make(new Reference2ObjectOpenHashMap<>(), descriptions -> {
		descriptions.put(ParticleTypes.ASH, "Ash particles naturally generate in soul sand valleys.");
		descriptions.put(ParticleTypes.BLOCK_MARKER, "Block Marker particles are the particles you see for the light and barrier blocks for example.");
		descriptions.put(ParticleTypes.CHERRY_LEAVES, "The leaves that fall from cherry trees.");
		descriptions.put(ParticleTypes.CRIT, "These particles can be seen when a critical hit is dealt against an enemy.");
		descriptions.put(ParticleTypes.DUST, "Dust particles can come in any colour! One example of their usage is the dust emitted by redstone torches.");
		descriptions.put(ParticleTypes.ENTITY_EFFECT, "The particles seen when an entity has an active potion effect.");
		descriptions.put(ParticleTypes.ENCHANTED_HIT, "Enchanted Hit particles can be seen when dealing damage with a weapon thats enchanted.");
		descriptions.put(ParticleTypes.FLASH, "Flash particles are the flash of colour you see in the air when a firework explodes.");
		descriptions.put(ParticleTypes.POOF, "The particles that appear after entity deaths.");
		descriptions.put(ParticleTypes.RAIN, "The small splashes of water you see on the ground when it rains.");
		descriptions.put(ParticleTypes.SPIT, "Don't let the llamas disrespect you.");
		descriptions.put(ParticleTypes.SPORE_BLOSSOM_AIR, "The particles that float around in the air near spore blossoms.");
		descriptions.put(ParticleTypes.FALLING_SPORE_BLOSSOM, "The particles that fall down beneath spore blossoms.");
		descriptions.put(ParticleTypes.WHITE_ASH, "White Ash can be frequently found in the Basalt Deltas!");
	});
	
	public static final ParticleType<?> BLOCK_BREAKING = FabricParticleTypes.simple();
	
	public enum State {
		FULL,
		NONE;
	}
	
	static void init(JsonObject config) {
		try {
			//Particle States
			JsonObject particleStates = config.has("particles") ? config.get("particles").getAsJsonObject() : null;
			
			if (particleStates != null) {
				for (String particleKey : particleStates.keySet()) {
					PARTICLE_STATES.put(new Identifier(migrateIdentifierFormat(particleKey)), State.valueOf(particleStates.get(particleKey).getAsString()));
				}
			}
			
			//Particle Scales
			JsonObject particleScales = config.has("particleScaling") ? config.get("particleScaling").getAsJsonObject() : null;
			
			if (particleScales != null) {
				for (String particleKey : particleScales.keySet()) {
					PARTICLE_SCALES.put(new Identifier(migrateIdentifierFormat(particleKey)), particleScales.get(particleKey).getAsFloat());
				}
			}
		} catch (Throwable t) {
			Main.LOGGER.error("[Aaron's Mod] Failed to load particle config!");
			t.printStackTrace();
		}
	}
	
	private static String migrateIdentifierFormat(String key) {
		return key.replace("minecraft_", "minecraft:");
	}
	
	private static String getParticleDisplayName(String id) {
		return Functions.titleCase(id.toString().replace("_", " "));
	}
	
	/**
	 * Registers "synthetic" particles
	 * 
	 * Currently only used for registering a fake block breaking particle, since the implementation of that is custom;
	 * the actual particle itself won't do anything, just in there for config purposes
	 */
	static void registerSyntheticParticles() {
		//Registered under minecraft's name space because they're a vanilla feature
		//Its also registered with an "empty" particle type so that other stuff won't behave weirdly if its unexpectedly null
		Registry.register(Registries.PARTICLE_TYPE, new Identifier("minecraft", "block_breaking"), BLOCK_BREAKING);
	}
	
	static List<OptionGroup> getOptionGroups() {
		List<OptionGroup> list = new ArrayList<>();
		List<Entry<RegistryKey<ParticleType<?>>, ParticleType<?>>> entryList = new ArrayList<>(Registries.PARTICLE_TYPE.getEntrySet());
		
		//Alphabetically sort the entries for logical ordering
		entryList.sort((o1, o2) -> {
			String o1Name = getParticleDisplayName(Registries.PARTICLE_TYPE.getId(o1.getValue()).toString());
			String o2Name = getParticleDisplayName(Registries.PARTICLE_TYPE.getId(o2.getValue()).toString());
			
			return o1Name.compareTo(o2Name);
		});
		
		for (Entry<RegistryKey<ParticleType<?>>, ParticleType<?>> entry : entryList) {
			ParticleType<?> particleType = entry.getValue();
			Identifier id = Registries.PARTICLE_TYPE.getId(particleType);
			
			String name = getParticleDisplayName(id.getPath());
			String namespaceName = getParticleDisplayName(id.getNamespace());
			OptionDescription description = PARTICLE_DESCRIPTIONS.containsKey(particleType) ? OptionDescription.of(Text.literal(PARTICLE_DESCRIPTIONS.get(particleType))) : OptionDescription.EMPTY;
			
			list.add(OptionGroup.createBuilder()
					.name(Text.literal(name + " Particles (" + namespaceName + ")"))
					.description(description)
					.collapsed(true)
					
					//Toggle
					.option(Option.<State>createBuilder()
							.name(Text.literal(name + " State"))
							.binding(State.FULL,
									() -> PARTICLE_STATES.getOrDefault(id, State.FULL),
									newValue -> PARTICLE_STATES.put(id, newValue))
							.available(!Main.OPTIFABRIC_LOADED)
							.controller(Config::createCyclingListController4Enum)
							.build())
					
					//Scale Multiplier
					.option(Option.<Float>createBuilder()
							.name(Text.literal(name + " Scale Multiplier"))
							.binding(1f,
									() -> PARTICLE_SCALES.getOrDefault(id, 1f),
									newValue -> PARTICLE_SCALES.put(id, newValue.floatValue()))
							.available(!Main.OPTIFABRIC_LOADED)
							.controller(opt -> FloatFieldControllerBuilder.create(opt).range(0f, 2f))
							.build())
					.build());
		}
		
		return list;
	}
}
