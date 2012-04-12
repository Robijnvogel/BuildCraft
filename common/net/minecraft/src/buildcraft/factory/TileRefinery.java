/** 
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 * 
 * BuildCraft is distributed under the terms of the Minecraft Mod Public 
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package net.minecraft.src.buildcraft.factory;

import java.util.LinkedList;

import net.minecraft.src.BuildCraftCore;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IInventory;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.buildcraft.api.API;
import net.minecraft.src.buildcraft.api.APIProxy;
import net.minecraft.src.buildcraft.api.ILiquidContainer;
import net.minecraft.src.buildcraft.api.IPowerReceptor;
import net.minecraft.src.buildcraft.api.Orientations;
import net.minecraft.src.buildcraft.api.PowerFramework;
import net.minecraft.src.buildcraft.api.PowerProvider;
import net.minecraft.src.buildcraft.api.SafeTimeTracker;
import net.minecraft.src.buildcraft.api.TileNetworkData;
import net.minecraft.src.buildcraft.core.IMachine;

public class TileRefinery extends TileMachine implements ILiquidContainer,
		IPowerReceptor, IInventory, IMachine {	

	public static LinkedList <RefineryRecipe> recipes = new LinkedList <RefineryRecipe> ();
	
	public static int LIQUID_PER_SLOT = API.BUCKET_VOLUME * 4;	
	
	public static class Slot {
		@TileNetworkData public int liquidId = 0;
		@TileNetworkData public int quantity = 0;
		
		public int fill(Orientations from, int amount, int id, boolean doFill) {
			if (quantity != 0 && liquidId != id) {
				return 0;
			} else if (quantity + amount <= LIQUID_PER_SLOT) {
				if (doFill) {
					quantity = quantity + amount;
				}
				
				liquidId = id;
				return amount;
			} else {
				int used = LIQUID_PER_SLOT - quantity;
				
				if (doFill) {
					quantity = LIQUID_PER_SLOT;
				}
				
				liquidId = id;
				return used;				
			}			
		}				
		
		public void writeFromNBT(NBTTagCompound nbttagcompound) {
			nbttagcompound.setInteger("liquidId", liquidId);
			nbttagcompound.setInteger("quantity", quantity);
		}
		
		public void readFromNBT(NBTTagCompound nbttagcompound) {
			liquidId = nbttagcompound.getInteger("liquidId");
			
			if (liquidId != 0) {
				quantity = nbttagcompound.getInteger("quantity");
			} else {
				quantity = 0;
			}
		}
	}
	
	@TileNetworkData public Slot slot1 = new Slot ();
	@TileNetworkData public Slot slot2 = new Slot ();
	@TileNetworkData public Slot result = new Slot ();
	@TileNetworkData public float animationSpeed = 1;
	private int animationStage = 0;
	
	SafeTimeTracker time = new SafeTimeTracker();
	
	SafeTimeTracker updateNetworkTime = new SafeTimeTracker();
	
	PowerProvider powerProvider;

	private boolean isActive;
	
	public TileRefinery () {
		powerProvider = PowerFramework.currentFramework.createPowerProvider();
		powerProvider.configure(20, 25, 25, 25, 1000);
	}
	
	@Override
	public int fill(Orientations from, int quantity, int id, boolean doFill) {
		if (id != BuildCraftCore.refineryInput) {
			return 0;
		}
		
		int used = slot1.fill(from, quantity, id, doFill);
		used += slot2.fill(from, quantity - used, id, doFill);
		
		if (doFill && used > 0) {
			updateNetworkTime.markTime(worldObj);
			sendNetworkUpdate();
		}
				
		return used;
	}

	@Override
	public int empty(int quantityMax, boolean doEmpty) {
		int res = 0;
		
		if (result.quantity >= quantityMax) {
			res = quantityMax;
			
			if (doEmpty) {
				result.quantity -= quantityMax;
			}
		} else {
			res = result.quantity;
			
			if (doEmpty) {
				result.quantity = 0;
			}
		}
		
		if (doEmpty && res > 0) {
			updateNetworkTime.markTime(worldObj);
			sendNetworkUpdate();
		}
				
		return res;
	}

	@Override
	public int getLiquidQuantity() {
		return result.quantity;
	}

	@Override
	public int getCapacity() {
		return API.BUCKET_VOLUME * 3;
	}

	@Override
	public int getLiquidId() {
		return result.liquidId;
	}

	@Override
	public int getSizeInventory() {
		return 0;
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		return null;
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {
		return null;
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack) {
		
	}

	@Override
	public String getInvName() {
		return null;
	}

	@Override
	public int getInventoryStackLimit() {
		return 0;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer entityplayer) {
		return false;
	}

	@Override
	public void setPowerProvider(PowerProvider provider) {
		powerProvider = provider;		
	}

	@Override
	public PowerProvider getPowerProvider() {
		return powerProvider;
	}

	@Override
	public void doWork() {
		
	}
	
	@Override
	public void updateEntity () {
		if (APIProxy.isClient(worldObj)) {
			simpleAnimationIterate();
		} else if (APIProxy.isServerSide()
				&& updateNetworkTime.markTimeIfDelay(worldObj, 2 * BuildCraftCore.updateFactor)) {
			sendNetworkUpdate();
		}
		
		isActive = false;
		
		RefineryRecipe currentRecipe = null;
		Slot src1 = null, src2 = null;
		
		for (RefineryRecipe r : recipes) {					
			if (r.sourceId1 == this.slot1.liquidId && this.slot1.quantity >= r.sourceQty1) {
				src1 = slot1;
				src2 = slot2;
			} else if (r.sourceId1 == this.slot2.liquidId && this.slot2.quantity >= r.sourceQty1) {
				src1 = slot2;
				src2 = slot1;				
			}
			
			if (src1 == null) {
				continue;
			}
			
			if (r.sourceQty2 > 0) {
				if (r.sourceId2 != src2.liquidId || src2.quantity < r.sourceQty2) {
					continue;
				}	
			} else {
				src2 = null;
			}
			
			currentRecipe = r;
			break;
		}
		
		if (currentRecipe == null) {
			decreaseAnimation();
			return;
		}
		
		if (result.quantity != 0 && result.liquidId != currentRecipe.resultId) {
			decreaseAnimation();
			return;
		}
		
		if (result.quantity + currentRecipe.resultQty > LIQUID_PER_SLOT) {
			decreaseAnimation();
			return;
		}
		
		isActive = true;
		
		if (powerProvider.energyStored >= currentRecipe.energy) {
			increaseAnimation();
		} else {
			decreaseAnimation();
		}
		
		if (!time.markTimeIfDelay(worldObj, currentRecipe.delay)) {
			return;
		}
		
		int energyUsed = powerProvider.useEnergy(currentRecipe.energy,
				currentRecipe.energy, true);
		
		if (energyUsed != 0) {
			result.liquidId = currentRecipe.resultId;
			result.quantity += currentRecipe.resultQty;
			src1.quantity -= currentRecipe.sourceQty1;
			
			if (src2 != null) {
				src2.quantity -= currentRecipe.sourceQty2;	
			}
		}
	}

	@Override
	public boolean isActive() {
		return isActive;
	}

	@Override
	public boolean manageLiquids() {
		return true;
	}

	@Override
	public boolean manageSolids() {
		return true;
	}
	
	public static void addRecipe (RefineryRecipe r) {
		recipes.add(r);
	}
	
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		
		if (nbttagcompound.hasKey("slot1")) {
			slot1.readFromNBT(nbttagcompound.getCompoundTag("slot1"));
			slot2.readFromNBT(nbttagcompound.getCompoundTag("slot2"));
			result.readFromNBT(nbttagcompound.getCompoundTag("result"));
		}
				
		animationStage = nbttagcompound.getInteger("animationStage");	
		animationSpeed = nbttagcompound.getFloat("animationSpeed");
		
		PowerFramework.currentFramework.loadPowerProvider(this, nbttagcompound);
		powerProvider.configure(20, 25, 25, 25, 1000);
    }

	@Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);
		
		NBTTagCompound NBTslot1 = new NBTTagCompound();
		NBTTagCompound NBTslot2 = new NBTTagCompound();
		NBTTagCompound NBTresult = new NBTTagCompound();
		
		slot1.writeFromNBT(NBTslot1);
		slot2.writeFromNBT(NBTslot2);
		result.writeFromNBT(NBTresult);
		
		nbttagcompound.setTag("slot1", NBTslot1);
		nbttagcompound.setTag("slot2", NBTslot2);
		nbttagcompound.setTag("result", NBTresult);
		
		nbttagcompound.setInteger("animationStage", animationStage);
		nbttagcompound.setFloat("animationSpeed", animationSpeed);
		PowerFramework.currentFramework.savePowerProvider(this, nbttagcompound);
	}
	
	public int getAnimationStage () {
		return animationStage ;
	}	
	
	/**
	 * Used to iterate the animation without computing the speed
	 */
	public void simpleAnimationIterate () {
		if (animationSpeed > 1) {
			animationStage += animationSpeed;
			
			if (animationStage > 300) {
				animationStage = 100;
			}
		} else if (animationStage > 0) {
			animationStage--;
		}
	}
	
	public void increaseAnimation () {
		if (animationSpeed < 2) {
			animationSpeed = 2;
		} else if (animationSpeed <= 5) {
			animationSpeed += 0.1;
		}
		
		animationStage += animationSpeed;
		
		if (animationStage > 300) {
			animationStage = 100;
		}
	}
	
	public void decreaseAnimation () {
		if (animationSpeed >= 1) {
			animationSpeed -= 0.1;
			
			animationStage += animationSpeed;
			
			if (animationStage > 300) {
				animationStage = 100;
			}
		} else {
			if (animationStage > 0) {
				animationStage--;
			}
		}
	}

	@Override
	public void openChest() {
		
	}

	@Override
	public void closeChest() {
		
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int var1){
		return null;
	}
}