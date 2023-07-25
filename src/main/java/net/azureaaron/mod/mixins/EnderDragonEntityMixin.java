package net.azureaaron.mod.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.cbyrne.betterinject.annotations.Inject;
import net.azureaaron.mod.Config;
import net.azureaaron.mod.features.BoundingBoxes.Dragons;
import net.azureaaron.mod.util.Cache;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

@Mixin(EnderDragonEntity.class)
public class EnderDragonEntityMixin extends MobEntity implements Monster {
	private static final int POWER_COLOUR = 0xe02b2b;
	private static final int FLAME_COLOUR = 0xe87c46;
	private static final int APEX_COLOUR = 0x168a16;
	private static final int ICE_COLOUR = 0x18d2db;
	private static final int SOUL_COLOUR = 0x8d18db;
	
	protected EnderDragonEntityMixin(EntityType<? extends MobEntity> entityType, World world) {
		super(entityType, world);
	}
	
	@Inject(method = "onSpawnPacket", at = @At("TAIL"))
	private void aaronMod$determineDragonColour() {
		if(Cache.inM7Phase5) {
			if(doesAnotherDragonHaveTheSameEntityId(this.getId())) return;
			
			Box dragonBoundingBox = this.calculateBoundingBox();
			
			if(dragonBoundingBox.intersects(Dragons.POWER.box)) {
				Cache.powerDragonId = this.getId();
				Cache.powerSpawnStart = 0L;
			}
			
			if(dragonBoundingBox.intersects(Dragons.FLAME.box)) {
				Cache.flameDragonId = this.getId();
				Cache.flameSpawnStart = 0L;	
			}
			
			if(dragonBoundingBox.intersects(Dragons.APEX.box)) {
				Cache.apexDragonId = this.getId();
				Cache.apexSpawnStart = 0L;
			}
			
			if(dragonBoundingBox.intersects(Dragons.ICE.box)) {
				Cache.iceDragonId = this.getId();
				Cache.iceSpawnStart = 0L;
			}
			
			if(dragonBoundingBox.intersects(Dragons.SOUL.box)) {
				Cache.soulDragonId = this.getId();
				Cache.soulSpawnStart = 0L;
			}
		}
	}
	
	/**
	 * @return whether any dragon has the same entity id cached
	 */
	private static boolean doesAnotherDragonHaveTheSameEntityId(int id) {
		if(Cache.powerDragonId == id || Cache.flameDragonId == id || Cache.apexDragonId == id 
				|| Cache.iceDragonId == id || Cache.soulDragonId == id) return true;
		
		return false;
	}
	
	@Override
	public int getTeamColorValue() {
		if(Cache.inM7Phase5) {
			if(this.getId() == Cache.powerDragonId) return POWER_COLOUR;
			if(this.getId() == Cache.flameDragonId) return FLAME_COLOUR;
			if(this.getId() == Cache.apexDragonId) return APEX_COLOUR;
			if(this.getId() == Cache.iceDragonId) return ICE_COLOUR;
			if(this.getId() == Cache.soulDragonId) return SOUL_COLOUR;
		}
		return super.getTeamColorValue();
	}
	
	@Override
	public boolean isGlowing() {
		if(Cache.inM7Phase5 && Config.glowingM7Dragons) return true;
		return super.isGlowing();
	}
}
