package advancedsystemsmanager.tileentities;

import advancedsystemsmanager.api.tileentities.ISystemListener;
import advancedsystemsmanager.flow.FlowComponent;
import advancedsystemsmanager.flow.menus.Menu;
import advancedsystemsmanager.flow.menus.MenuRF;
import advancedsystemsmanager.flow.menus.MenuRFInput;
import advancedsystemsmanager.flow.menus.MenuTargetRF;
import advancedsystemsmanager.network.MessageHandler;
import advancedsystemsmanager.network.message.RFNodeUpdateMessage;
import advancedsystemsmanager.tileentities.manager.TileEntityManager;
import advancedsystemsmanager.util.ClusterMethodRegistration;
import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class TileEntityRFNode extends TileEntityClusterElement implements IEnergyProvider, IEnergyReceiver, ISystemListener
{
    public static final int MAX_BUFFER = 96000;
    private static final String STORED = "Stored";
    private boolean[] inputSides = new boolean[6];
    private boolean[] outputSides = new boolean[6];
    private Set<TileEntityManager> managers = new HashSet<TileEntityManager>();
    private Set<FlowComponent> components = new HashSet<FlowComponent>();
    private boolean updated = true;
    private int stored;

    @Override
    public void updateEntity()
    {
        super.updateEntity();
        if (!worldObj.isRemote)
        {
            if (!managers.isEmpty())
            {
                for (TileEntityManager manager : managers)
                    for (FlowComponent component : manager.getFlowItems()) update(component);
                managers.clear();
            }
            if (!this.isPartOfCluster() && updated) sendUpdatePacket();
            for (int i = 0; i < inputSides.length; i++)
            {
                ForgeDirection dir = ForgeDirection.getOrientation(i);
                TileEntity te = getTileEntity(dir);
                if (inputSides[i] && te instanceof IEnergyProvider)
                {
                    int amount = ((IEnergyProvider)te).extractEnergy(dir.getOpposite(), MAX_BUFFER - stored, false);
                    this.receiveEnergy(dir, amount, false);
                }
                if (outputSides[i] && te instanceof IEnergyReceiver)
                {
                    int amount = ((IEnergyReceiver)te).receiveEnergy(dir.getOpposite(), stored, false);
                    this.receiveEnergy(dir, amount, false);
                }
            }
        }
    }

    private TileEntity getTileEntity(ForgeDirection dir)
    {
        return worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
    }

    private void sendUpdatePacket()
    {
        MessageHandler.INSTANCE.sendToAll(new RFNodeUpdateMessage(this));
        updated = false;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate)
    {
        if (inputSides[from.ordinal()])
        {
            int toReceive = Math.min(maxReceive, MAX_BUFFER - stored);
            if (!simulate) stored += toReceive;
            return toReceive;
        }
        return 0;
    }

    @Override
    public Packet getDescriptionPacket()
    {
        writeToNBT(new NBTTagCompound());
        return MessageHandler.INSTANCE.getPacketFrom(new RFNodeUpdateMessage(this));
    }

    public void update(FlowComponent component)
    {
        Menu menu = component.getMenus().get(0);
        if (menu instanceof MenuRF)
        {
            if (((MenuRF)menu).isSelected(this))
            {
                if (!components.contains(component))
                {
                    components.add(component);
                    updateConnections();
                }
            } else
            {
                if (components.contains(component))
                {
                    components.remove(component);
                    updateConnections();
                }
            }
        }
    }

    private void updateConnections()
    {
        if (components.isEmpty())
        {
            this.updated = true;
            this.inputSides = new boolean[6];
            this.outputSides = new boolean[6];
        } else
        {
            for (FlowComponent component : components)
            {
                boolean[] array = getSides(component.getMenus().get(0) instanceof MenuRFInput);
                MenuTargetRF target = (MenuTargetRF)component.getMenus().get(1);
                for (int i = 0; i < 6; i++)
                {
                    boolean active = target.isActive(i);
                    if (active != array[i]) updated = true;
                    array[i] = active;
                }
            }
        }
    }

    private boolean[] getSides(boolean input)
    {
        return input ? inputSides : outputSides;
    }

    @Override
    public void readContentFromNBT(NBTTagCompound tagCompound)
    {
        stored = tagCompound.getInteger(STORED);
    }

    @Override
    public void writeContentToNBT(NBTTagCompound tagCompound)
    {
        tagCompound.setInteger(STORED, stored);
    }

    @Override
    protected EnumSet<ClusterMethodRegistration> getRegistrations()
    {
        return EnumSet.of(ClusterMethodRegistration.CONNECT_ENERGY, ClusterMethodRegistration.EXTRACT_ENERGY, ClusterMethodRegistration.RECEIVE_ENERGY, ClusterMethodRegistration.STORED_ENERGY);
    }

    @Override
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate)
    {
        if (outputSides[from.ordinal()])
        {
            int toExtract = Math.min(maxExtract, stored);
            if (!simulate) stored -= toExtract;
            return toExtract;
        }
        return 0;
    }

    @Override
    public int getEnergyStored(ForgeDirection from)
    {
        return stored;
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from)
    {
        return MAX_BUFFER;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from)
    {
        return outputSides[from.ordinal()] || inputSides[from.ordinal()];
    }

    @Override
    public void added(TileEntityManager tileEntityManager)
    {
        managers.add(tileEntityManager);
    }

    @Override
    public void removed(TileEntityManager tileEntityManager)
    {
//        managers.remove(tileEntityManager);
        for (Iterator<FlowComponent> itr = components.iterator(); itr.hasNext(); )
        {
            if (itr.next().getManager() == tileEntityManager) itr.remove();
        }
    }

    public boolean isInput(int side)
    {
        return inputSides[side];
    }

    public boolean isOutput(int side)
    {
        return outputSides[side];
    }

    public void setOutputSides(boolean[] outputSides)
    {
        this.outputSides = outputSides;
    }

    public void setInputSides(boolean[] inputSides)
    {
        this.inputSides = inputSides;
    }

    public boolean[] getOutputs()
    {
        return outputSides;
    }

    public boolean[] getInputs()
    {
        return inputSides;
    }
}
