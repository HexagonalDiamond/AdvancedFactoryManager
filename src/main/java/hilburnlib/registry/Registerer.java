package hilburnlib.registry;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModAPIManager;
import cpw.mods.fml.common.registry.GameRegistry;
import hilburnlib.utils.LogHelper;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.IItemRenderer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class Registerer
{
    private LogHelper log;
    private IRenderRegistry renderRegistry;

    public Registerer(LogHelper log, IRenderRegistry renderRegistry)
    {
        this.log = log;
        this.renderRegistry = renderRegistry;
    }

    public void scan(Class<?> targetClass)
    {
        for (Field field : targetClass.getFields())
        {
            Register annotation = field.getAnnotation(Register.class);
            if (annotation == null) continue;
            if (!annotation.dependency().isEmpty() && !Loader.isModLoaded(annotation.dependency()) && !ModAPIManager.INSTANCE.hasAPI(annotation.dependency())) continue;
            Class clazz = field.getType();
            if (Modifier.isStatic(field.getModifiers()))
            {
                if (Item.class.isAssignableFrom(clazz))
                {
                    registerItem(field, annotation, clazz);
                } else if (Block.class.isAssignableFrom(clazz))
                {
                    registerBlock(field, annotation, clazz);
                } else
                {
                    log.warn("Can only register Blocks and Items - " + field.getName() + " unrecognised");
                }
            } else
            {
                log.warn("Can't register non-static field " + field.getName());
            }
        }
    }

    private void registerItem(Field field, Register annotation, Class<? extends Item> clazz)
    {
        try
        {
            Item item;
            if ((item = (Item)field.get(null)) == null)
            {
                item = getConstructed(clazz);
                field.set(null, item);
            }
            if (!annotation.unlocalizedName().isEmpty()) item.setUnlocalizedName(annotation.unlocalizedName());
            GameRegistry.registerItem(item, getName(annotation).isEmpty() ? item.getUnlocalizedName() : getName(annotation));
            if (annotation.IItemRenderer() != IItemRenderer.class)
                renderRegistry.registerItemRenderer(item, annotation.IItemRenderer().newInstance());
        } catch (Exception e)
        {
            log.warn("Failed to register item " + annotation.name());
        }
    }

    private static String getName(Register annotation)
    {
        return annotation.name().isEmpty() ? annotation.unlocalizedName() : annotation.name();
    }

    private void registerBlock(Field field, Register annotation, Class<? extends Block> clazz)
    {
        try
        {
            Block block;
            if ((block = (Block)field.get(null)) == null)
            {
                block = getConstructed(clazz);
                field.set(null, block);
            }
            if (!annotation.unlocalizedName().isEmpty()) block.setBlockName(annotation.unlocalizedName());
            GameRegistry.registerBlock(block, annotation.itemBlock(), getName(annotation).isEmpty() ? block.getUnlocalizedName() : getName(annotation));
            if (annotation.tileEntity() != TileEntity.class)
                GameRegistry.registerTileEntity(annotation.tileEntity(), annotation.name());
            if (annotation.SBRH() != ISimpleBlockRenderingHandler.class)
            {
                ISimpleBlockRenderingHandler handler = annotation.SBRH().newInstance();
                renderRegistry.registerSimpleBlockRenderer(block.getRenderType(), handler);
            }
            else if (annotation.tileEntity() != TileEntity.class && annotation.TESR() != TileEntitySpecialRenderer.class)
                renderRegistry.registerTileEntityRenderer(annotation.tileEntity(), annotation.TESR().newInstance());
            if (annotation.IItemRenderer() != IItemRenderer.class)
                renderRegistry.registerItemRenderer(Item.getItemFromBlock(block), annotation.IItemRenderer().newInstance());
        } catch (Exception e)
        {
            log.warn("Failed to register block " + annotation.name());
        }
    }

    private static <T> T getConstructed(Class clazz)
    {
        try
        {
            return (T)clazz.newInstance();

        } catch (Exception e)
        {
            return null;
        }
    }
}
