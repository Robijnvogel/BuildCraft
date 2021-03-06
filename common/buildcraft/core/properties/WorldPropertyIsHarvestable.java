/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt */
package buildcraft.core.properties;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;

import buildcraft.api.crops.CropManager;

public class WorldPropertyIsHarvestable extends WorldProperty {

    @Override
    public boolean get(IBlockAccess blockAccess, IBlockState state, BlockPos pos) {
        return CropManager.isMature(blockAccess, state, pos);
    }
}
