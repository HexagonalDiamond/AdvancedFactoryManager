package advancedsystemsmanager.items;

import advancedsystemsmanager.AdvancedSystemsManager;
import advancedsystemsmanager.naming.NameRegistry;
import advancedsystemsmanager.reference.Names;
import advancedsystemsmanager.registry.ItemRegistry;
import advancedsystemsmanager.tileentities.TileEntityClusterElement;
import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

public class ItemLabeler extends ItemBase
{
    public ItemLabeler()
    {
        super(Names.LABELER);
    }

    public static List<String> getSavedStrings(ItemStack stack)
    {
        List<String> result = new ArrayList<String>();
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) return result;
        NBTTagList tagList = tagCompound.getTagList("saved", 8);
        for (int i = 0; i < tagList.tagCount(); i++)
        {
            result.add(tagList.getStringTagAt(i));
        }
        return result;
    }

    public static void setLabel(ItemStack stack, String string)
    {
        stack.getTagCompound().setString("Label", string);
    }

    public static void saveStrings(ItemStack stack, List<String> strings)
    {
        if (!stack.hasTagCompound())
        {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tagCompound = stack.getTagCompound();
        NBTTagList tagList = new NBTTagList();
        for (String string : strings)
            tagList.appendTag(new NBTTagString(string));
        tagCompound.setTag("saved", tagList);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
    {
        if (world.isRemote)
        {
            player.openGui(AdvancedSystemsManager.INSTANCE, 0, world, player.chunkCoordX, player.chunkCoordY, player.chunkCoordZ);
        }
        return super.onItemRightClick(stack, world, player);
    }

    @Override
    @SuppressWarnings(value = "unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean extra)
    {
        super.addInformation(stack, player, list, extra);
        String label = getLabel(stack);
        if (label.isEmpty()) list.add("Clear Label");
        else list.add("Label: " + label);
    }

    @SideOnly(Side.CLIENT)
    @SuppressWarnings(value = "unchecked")
    public void getSubItems(Item item, CreativeTabs tab, List list)
    {
        list.add(ItemRegistry.defaultLabeler);
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player)
    {
        if (isValidTile(player.getEntityWorld(), x, y, z))
        {
            if (player.getEntityWorld().isRemote)
            {
                String label = ItemLabeler.getLabel(stack);
                if (label.isEmpty())
                {
                    if (NameRegistry.removeName(player.getEntityWorld(), x, y, z))
                    {
                        player.addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocal("asm.chat.cleared")));
                    }
                } else
                {
                    NameRegistry.saveName(player.getEntityWorld(), x, y, z, label);
                    player.addChatComponentMessage(new ChatComponentText(StatCollector.translateToLocalFormatted("asm.chat.saved", label)));
                }
            }
            return true;
        }
        return false;
    }

    public static boolean isValidTile(World world, int x, int y, int z)
    {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof IInventory || te instanceof IFluidHandler || te instanceof IEnergyProvider || te instanceof IEnergyReceiver || te instanceof TileEntityClusterElement;
    }

    public static String getLabel(ItemStack stack)
    {
        return stack.hasTagCompound() ? stack.getTagCompound().getString("Label") : "";
    }
}
