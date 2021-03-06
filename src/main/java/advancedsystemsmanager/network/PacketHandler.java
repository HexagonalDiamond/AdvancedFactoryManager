package advancedsystemsmanager.network;

import advancedsystemsmanager.api.tileentities.ITileEntityInterface;
import advancedsystemsmanager.flow.Connection;
import advancedsystemsmanager.flow.FlowComponent;
import advancedsystemsmanager.flow.Point;
import advancedsystemsmanager.flow.menus.Menu;
import advancedsystemsmanager.gui.ContainerBase;
import advancedsystemsmanager.gui.ContainerManager;
import advancedsystemsmanager.tileentities.manager.TileEntityManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.tileentity.TileEntity;


public class PacketHandler
{
    public static final double BLOCK_UPDATE_RANGE = 128;

    public static void sendDataToServer(DataWriter dw)
    {
        dw.sendServerPacket();
        dw.close();
    }

    public static void sendAllData(Container container, ICrafting crafting, ITileEntityInterface te)
    {
        DataWriter dw = new DataWriter();

        dw.writeBoolean(true); //use container
        dw.writeByte(container.windowId);
        dw.writeBoolean(false); //all data
        te.writeAllData(dw);

        sendDataToPlayer(crafting, dw);
        dw.close();
    }

    public static void sendDataToPlayer(ICrafting crafting, DataWriter dw)
    {
        if (crafting instanceof EntityPlayerMP)
        {
            EntityPlayerMP player = (EntityPlayerMP)crafting;

            dw.sendPlayerPacket(player);
        }
    }

    public static DataWriter getWriterForUpdate(Container container)
    {
        DataWriter dw = new DataWriter();

        dw.writeBoolean(true); //use container
        dw.writeByte(container.windowId);
        dw.writeBoolean(true); //updated data

        return dw;
    }



   /* public static void readBlockPacket(DataReader data) {
        int x = data.readData(DataBitHelper.WORLD_COORDINATE);
        int y = data.readData(DataBitHelper.WORLD_COORDINATE);
        int z = data.readData(DataBitHelper.WORLD_COORDINATE);

        World world = Minecraft.getMinecraft().theWorld;
        if (world.getBlockId(x, y, z) == Blocks.blockCable.blockID) {
            Blocks.blockCable.update(world, x, y, z);
        }
    }*/

    @SideOnly(Side.CLIENT)
    public static DataWriter getWriterForServerActionPacket()
    {
        DataWriter dw = getBaseWriterForServerPacket();

        dw.writeBoolean(true); //action

        return dw;
    }

    @SideOnly(Side.CLIENT)
    private static DataWriter getBaseWriterForServerPacket()
    {
        Container container = Minecraft.getMinecraft().thePlayer.openContainer;
        if (container != null)
        {
            DataWriter dw = new DataWriter();
            dw.writeBoolean(true); //use container
            dw.writeByte(container.windowId);

            return dw;
        } else
        {
            return null;
        }
    }

    public static void sendUpdateInventoryPacket(ContainerManager container)
    {
        DataWriter dw = PacketHandler.getWriterForSpecificData(container);
        createNonComponentPacket(dw);
        dw.writeBoolean(true);
        sendDataToListeningClients(container, dw);
    }

    public static void sendDataToListeningClients(ContainerBase container, DataWriter dw)
    {
        dw.sendPlayerPackets(container);
        dw.close();
    }

    private static DataWriter getWriterForSpecificData(Container container)
    {
        DataWriter dw = new DataWriter();

        dw.writeBoolean(true); //use container
        dw.writeByte(container.windowId);
        dw.writeBoolean(true); //updated data
        dw.writeBoolean(false); //not new

        return dw;
    }

    private static void createNonComponentPacket(DataWriter dw)
    {
        dw.writeBoolean(false); //this is a packet that has nothing to do with a specific FlowComponent
    }

    public static DataWriter getWriterForServerComponentPacket(FlowComponent component, Menu menu)
    {
        DataWriter dw = PacketHandler.getWriterForServerPacket();
        createComponentPacket(dw, component, menu);
        return dw;
    }

    @SideOnly(Side.CLIENT)
    public static DataWriter getWriterForServerPacket()
    {
        DataWriter dw = getBaseWriterForServerPacket();

        dw.writeBoolean(false); //no action

        return dw;
    }

    private static void createComponentPacket(DataWriter dw, FlowComponent component, Menu menu)
    {
        dw.writeBoolean(true); //this is a packet for a specific FlowComponent
        dw.writeComponentId(component.getManager(), component.getId());

        if (menu != null)
        {
            dw.writeBoolean(true); //this is packet for a specific menu
            dw.writeData(menu.getId(), DataBitHelper.FLOW_CONTROL_MENU_COUNT);
        } else
        {
            dw.writeBoolean(false); //this is a packet that has nothing to do with a menu
        }
    }

    public static DataWriter getWriterForClientComponentPacket(ContainerManager container, FlowComponent component, Menu menu)
    {
        DataWriter dw = PacketHandler.getWriterForSpecificData(container);
        createComponentPacket(dw, component, menu);
        return dw;
    }

    public static DataWriter getButtonPacketWriter()
    {
        DataWriter dw = getWriterForServerPacket();
        createNonComponentPacket(dw);
        return dw;
    }

    public static void sendNewFlowComponent(ContainerManager container, FlowComponent component)
    {
        DataWriter dw = new DataWriter();

        dw.writeBoolean(true); //use container
        dw.writeByte(container.windowId);
        dw.writeBoolean(true); //updated data
        dw.writeBoolean(true); //new data;

        writeAllComponentData(dw, component);
        PacketHandler.sendDataToListeningClients(container, dw);

        dw.close();
    }

    public static void writeAllComponentData(DataWriter dw, FlowComponent flowComponent)
    {
        dw.writeData(flowComponent.getX(), DataBitHelper.FLOW_CONTROL_X);
        dw.writeData(flowComponent.getY(), DataBitHelper.FLOW_CONTROL_Y);
        dw.writeData(flowComponent.getType().getId(), DataBitHelper.FLOW_CONTROL_TYPE_ID);
        dw.writeComponentId(flowComponent.manager, flowComponent.getId());
        dw.writeString(flowComponent.getComponentName(), DataBitHelper.NAME_LENGTH);
        if (flowComponent.getParent() != null)
        {
            dw.writeBoolean(true);
            dw.writeComponentId(flowComponent.getManager(), flowComponent.getParent().getId());
        } else
        {
            dw.writeBoolean(false);
        }
        for (Menu menu : flowComponent.getMenus())
        {
            menu.writeData(dw);
        }

        for (int i = 0; i < flowComponent.getConnectionSet().getConnections().length; i++)
        {
            Connection connection = flowComponent.getConnection(i);
            dw.writeBoolean(connection != null);
            if (connection != null)
            {
                dw.writeComponentId(flowComponent.getManager(), connection.getComponentId());
                dw.writeData(connection.getConnectionId(), DataBitHelper.CONNECTION_ID);

                dw.writeData(connection.getNodes().size(), DataBitHelper.NODE_ID);
                for (Point point : connection.getNodes())
                {
                    dw.writeData(point.getX(), DataBitHelper.FLOW_CONTROL_X);
                    dw.writeData(point.getY(), DataBitHelper.FLOW_CONTROL_Y);
                }
            }
        }

        flowComponent.getManager().updateVariables();
    }

    public static void sendRemovalPacket(ContainerManager container, int idToRemove)
    {
        DataWriter dw = PacketHandler.getWriterForSpecificData(container);
        createNonComponentPacket(dw);
        dw.writeBoolean(false);
        dw.writeComponentId((TileEntityManager)container.getTileEntity(), idToRemove);
        sendDataToListeningClients(container, dw);

        dw.close();
    }

    public static void sendBlockPacket(IPacketBlock block, EntityPlayer player, int id)
    {
        if (block instanceof TileEntity)
        {
            TileEntity te = (TileEntity)block;
            boolean onServer = player == null || !player.worldObj.isRemote;

            DataWriter dw = new DataWriter();
            dw.writeBoolean(false); //no container
            dw.writeData(te.xCoord, DataBitHelper.XZ_COORDINATE);
            dw.writeData(te.yCoord, DataBitHelper.Y_COORDINATE);
            dw.writeData(te.zCoord, DataBitHelper.XZ_COORDINATE);
            int length = block.infoBitLength(onServer);
            if (length != 0)
            {
                dw.writeData(id, length);
            }
            block.writeData(dw, player, onServer, id);

            if (!onServer)
            {
                dw.sendServerPacket();
            } else if (player != null)
            {
                dw.sendPlayerPacket((EntityPlayerMP)player);
            } else
            {
                dw.sendPlayerPackets(te.xCoord + 0.5, te.yCoord, te.zCoord, BLOCK_UPDATE_RANGE, te.getWorldObj().provider.dimensionId);
            }

            dw.close();
        }
    }
}
