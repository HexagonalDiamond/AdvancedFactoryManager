package advancedsystemsmanager.util;

import advancedsystemsmanager.api.IJsonWritable;
import advancedsystemsmanager.api.ISystemType;
import advancedsystemsmanager.api.gui.IContainerSelection;
import advancedsystemsmanager.flow.elements.Variable;
import advancedsystemsmanager.flow.menus.MenuContainer;
import advancedsystemsmanager.gui.GuiManager;
import advancedsystemsmanager.reference.Names;
import advancedsystemsmanager.registry.SystemTypeRegistry;
import advancedsystemsmanager.tileentities.manager.TileEntityManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SystemBlock implements IContainerSelection<GuiManager>, IJsonWritable
{
    private TileEntity tileEntity;
    private Set<ISystemType> types;
    private int id;
    private int cableDistance;

    public SystemBlock(TileEntity tileEntity, int cableDistance)
    {
        this.tileEntity = tileEntity;
        types = new HashSet<ISystemType>();
        this.cableDistance = cableDistance;
    }

    public void addType(ISystemType type)
    {
        types.add(type);
    }

    public boolean isOfAnyType(Collection<ISystemType> types)
    {
        for (ISystemType type : types)
        {
            if (isOfType(type))
            {
                return true;
            }
        }

        return false;
    }

    public boolean isOfType(ISystemType type)
    {
        return isOfType(this.types, type);
    }

    public static boolean isOfType(Set<ISystemType> types, ISystemType type)
    {
        return type == null || types.contains(type) || (type == SystemTypeRegistry.NODE && (types.contains(SystemTypeRegistry.RECEIVER) || types.contains(SystemTypeRegistry.EMITTER)));
    }

    public TileEntity getTileEntity()
    {
        return tileEntity;
    }

    @Override
    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    @Override
    public void draw(GuiManager gui, int x, int y)
    {
        gui.drawBlock(tileEntity, x, y);
    }

    @Override
    public String getDescription(GuiManager gui)
    {
        String str = StevesHooks.fixToolTip(gui.getBlockName(tileEntity), tileEntity);

        str += getVariableTag(gui);

        str += "\n" + StatCollector.translateToLocalFormatted(Names.LOCATION, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
        int distance = getDistance(gui.getManager());
        str += "\n" + StatCollector.translateToLocalFormatted(distance > 1 ? Names.BLOCKS_AWAY : Names.BLOCK_AWAY, distance);
        str += "\n" + StatCollector.translateToLocalFormatted(cableDistance > 1 ? Names.CABLES_AWAY : Names.CABLE_AWAY, cableDistance);

        return str;
    }


    public int getDistance(TileEntityManager manager)
    {
        return (int)Math.round(Math.sqrt(manager.getDistanceFrom(tileEntity.xCoord + 0.5, tileEntity.yCoord + 0.5, tileEntity.zCoord + 0.5)));
    }

    @Override
    public String getName(GuiManager gui)
    {
        return gui.getBlockName(tileEntity);
    }

    @Override
    public boolean isVariable()
    {
        return false;
    }

    @SideOnly(Side.CLIENT)
    private String getVariableTag(GuiManager gui)
    {
        int count = 0;
        String result = "";

        if (GuiScreen.isShiftKeyDown())
        {
            for (Variable variable : gui.getManager().getVariables())
            {
                if (isPartOfVariable(variable))
                {
                    result += "\n" + variable.getDescription(gui);
                    count++;
                }
            }
        }

        return count == 0 ? "" : result;
    }

    @SideOnly(Side.CLIENT)
    public boolean isPartOfVariable(Variable variable)
    {
        return variable.isValid() && ((MenuContainer)variable.getDeclaration().getMenus().get(2)).getSelectedInventories().contains(id);
    }

    public int getCableDistance()
    {
        return cableDistance;
    }

    @Override
    public JsonObject writeToJson()
    {
        JsonObject object = new JsonObject();
        object.add("Position", new JsonPrimitive(tileEntity.xCoord + ", " + tileEntity.yCoord + ", " + tileEntity.zCoord));
        object.add("Name", new JsonPrimitive(new GuiManager(null, null).getBlockName(tileEntity)));
        return object;
    }
}