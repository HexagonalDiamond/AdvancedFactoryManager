package advancedsystemsmanager.api.tileentities;

import advancedsystemsmanager.util.ClusterMethodRegistration;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.EnumSet;

public interface IClusterElement
{
    ItemStack getItemStack();

    boolean isPartOfCluster();

    void setPartOfCluster(boolean partOfCluster);

    void setMetaData(int meta);

    void writeContentToNBT(NBTTagCompound tagCompound);

    void readContentFromNBT(NBTTagCompound tagCompound);

    EnumSet<ClusterMethodRegistration> getRegistrations();
}
