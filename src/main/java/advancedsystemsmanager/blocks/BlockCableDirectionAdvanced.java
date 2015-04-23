package advancedsystemsmanager.blocks;

import advancedsystemsmanager.AdvancedSystemsManager;
import advancedsystemsmanager.reference.Reference;
import advancedsystemsmanager.tileentities.TileEntityCluster;
import advancedsystemsmanager.tileentities.TileEntityClusterElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;


public abstract class BlockCableDirectionAdvanced extends BlockClusterElement
{
    public BlockCableDirectionAdvanced(String name)
    {
        super(name);
    }

    @SideOnly(Side.CLIENT)
    private IIcon activeIcon;
    @SideOnly(Side.CLIENT)
    private IIcon advancedActiveIcon;
    @SideOnly(Side.CLIENT)
    private IIcon inactiveIcon;
    @SideOnly(Side.CLIENT)
    private IIcon advancedInactiveIcon;

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register)
    {
        activeIcon = register.registerIcon(Reference.RESOURCE_LOCATION + ":" + getFrontTextureName(false));
        advancedActiveIcon = register.registerIcon(Reference.RESOURCE_LOCATION + ":" + getFrontTextureName(true));
        inactiveIcon = register.registerIcon(Reference.RESOURCE_LOCATION + ":" + getSideTextureName(false));
        advancedInactiveIcon = register.registerIcon(Reference.RESOURCE_LOCATION + ":" + getSideTextureName(true));
    }

    protected abstract String getFrontTextureName(boolean isAdvanced);

    protected abstract String getSideTextureName(boolean isAdvanced);

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta)
    {
        //pretend the meta is 3
        return getIconFromSideAndMeta(side, addAdvancedMeta(3, meta));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side)
    {
        int meta = world.getBlockMetadata(x, y, z);

        return getIconFromSideAndMeta(side, meta);
    }

    @SideOnly(Side.CLIENT)
    private IIcon getIconFromSideAndMeta(int side, int meta)
    {
        return side == (getSideMeta(meta) % ForgeDirection.VALID_DIRECTIONS.length) ? isAdvanced(meta) ? advancedActiveIcon : activeIcon : isAdvanced(meta) ? advancedInactiveIcon : inactiveIcon;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack item)
    {
        int meta = addAdvancedMeta(BlockPistonBase.determineOrientation(world, x, y, z, entity), item.getItemDamage());

        TileEntityClusterElement element = TileEntityCluster.getTileEntity(getTeClass(), world, x, y, z);
        if (element != null)
        {
            element.setMetaData(meta);
        }
    }

    protected abstract Class<? extends TileEntityClusterElement> getTeClass();

    @Override
    @SuppressWarnings(value = "unchecked")
    public void getSubBlocks(Item item, CreativeTabs tabs, List list)
    {
        list.add(new ItemStack(item, 1, 0));
        list.add(new ItemStack(item, 1, 8));
    }

    public boolean isAdvanced(int meta)
    {
        return (meta & 8) != 0;
    }

    public int getSideMeta(int meta)
    {
        return meta & 7;
    }

    private int addAdvancedMeta(int meta, int advancedMeta)
    {
        return meta | (advancedMeta & 8);
    }

    private int getAdvancedMeta(int meta)
    {
        return addAdvancedMeta(0, meta);
    }

    @Override
    public int damageDropped(int meta)
    {
        return getAdvancedMeta(meta);
    }

}