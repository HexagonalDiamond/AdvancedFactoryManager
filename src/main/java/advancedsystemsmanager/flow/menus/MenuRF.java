package advancedsystemsmanager.flow.menus;

import advancedsystemsmanager.api.ISystemType;
import advancedsystemsmanager.flow.FlowComponent;
import advancedsystemsmanager.reference.Names;
import advancedsystemsmanager.tileentities.TileEntityRFNode;
import advancedsystemsmanager.util.SystemCoord;

import java.util.List;

public abstract class MenuRF extends MenuContainer
{
    public MenuRF(FlowComponent parent, ISystemType validType)
    {
        super(parent, validType);
    }

    public boolean isSelected(TileEntityRFNode node)
    {
        for (SystemCoord block : getParent().getManager().getConnectedInventories())
        {
            if (block.tileEntity == node) return getSelectedInventories().contains(block.getId());
        }
        return false;
    }

    @Override
    public void addErrors(List<String> errors)
    {
        if (this.selectedInventories.isEmpty() && this.isVisible())
        {
            errors.add(Names.NO_RF_ERROR);
        }
    }

    public void updateConnectedNodes()
    {
        if (!getParent().getManager().getWorldObj().isRemote)
        {
            for (SystemCoord connection : getParent().getManager().getConnectedInventories())
            {
                if (connection.tileEntity instanceof TileEntityRFNode)
                    ((TileEntityRFNode)connection.tileEntity).update(getParent());
            }
        }
    }
}
