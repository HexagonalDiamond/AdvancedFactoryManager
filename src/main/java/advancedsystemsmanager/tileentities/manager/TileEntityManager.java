package advancedsystemsmanager.tileentities.manager;

import advancedsystemsmanager.api.tileentities.ISystemListener;
import advancedsystemsmanager.api.ISystemType;
import advancedsystemsmanager.api.tileentities.ITileEntityInterface;
import advancedsystemsmanager.api.gui.IManagerButton;
import advancedsystemsmanager.api.gui.ManagerButtonList;
import advancedsystemsmanager.flow.Connection;
import advancedsystemsmanager.flow.FlowComponent;
import advancedsystemsmanager.flow.Point;
import advancedsystemsmanager.flow.elements.Variable;
import advancedsystemsmanager.flow.elements.VariableColor;
import advancedsystemsmanager.flow.execution.Executor;
import advancedsystemsmanager.flow.execution.TriggerHelper;
import advancedsystemsmanager.flow.execution.TriggerHelperBUD;
import advancedsystemsmanager.flow.execution.TriggerHelperRedstone;
import advancedsystemsmanager.flow.menus.Menu;
import advancedsystemsmanager.flow.menus.MenuInterval;
import advancedsystemsmanager.flow.menus.MenuVariable;
import advancedsystemsmanager.gui.ContainerManager;
import advancedsystemsmanager.gui.GuiManager;
import advancedsystemsmanager.gui.IInterfaceRenderer;
import advancedsystemsmanager.network.*;
import advancedsystemsmanager.registry.*;
import advancedsystemsmanager.settings.Settings;
import advancedsystemsmanager.tileentities.TileEntityBUD;
import advancedsystemsmanager.tileentities.TileEntityCluster;
import advancedsystemsmanager.tileentities.TileEntityClusterElement;
import advancedsystemsmanager.tileentities.TileEntityReceiver;
import advancedsystemsmanager.util.StevesHooks;
import advancedsystemsmanager.util.SystemCoord;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.*;

import static advancedsystemsmanager.api.execution.ICommand.CommandType;

public class TileEntityManager extends TileEntity implements ITileEntityInterface
{
    public static final TriggerHelperRedstone redstoneTrigger = new TriggerHelperRedstone(3, 4);
    public static final TriggerHelperRedstone redstoneCondition = new TriggerHelperRedstone(1, 2);
    public static final TriggerHelperBUD budTrigger = new TriggerHelperBUD();

    public static final int BUTTON_SIZE_W = 14;
    public static final int BUTTON_SIZE_H = 14;
    public static final int BUTTON_SRC_X = 242;
    public static final int BUTTON_SRC_Y = 0;
    public static final int BUTTON_INNER_SRC_X = 230;
    public static final int BUTTON_INNER_SRC_Y = 0;
    public static final int MAX_CABLE_LENGTH = 128;
    public static final int MAX_COMPONENT_AMOUNT = 511;
    public static final int MAX_CONNECTED_INVENTORIES = 1023;
    private static final String NBT_MAX_ID = "maxID";
    private static final String NBT_TIMER = "Timer";
    public static final String NBT_COMPONENTS = "Components";
    private static final String NBT_VARIABLES = "Variables";
    public List<FlowComponent> triggers;
    public ManagerButtonList buttons;
    public boolean justSentServerComponentRemovalPacket;
    public TIntObjectHashMap<FlowComponent> components;
    public TLongObjectHashMap<SystemCoord> network;
    public FlowComponent selectedGroup;
    @SideOnly(Side.CLIENT)
    public IInterfaceRenderer specialRenderer;
    private Connection currentlyConnecting;
    private List<FlowComponent> zLevelRenderingList;
    private Variable[] variables;
    private int maxID;
    private int triggerOffset;
    private List<Integer> removedIds;
    private boolean firstInventoryUpdate = true;
    private boolean firstCommandExecution = true;
    private int timer = 0;
    private TileEntityManager self = this;
    private boolean usingUnlimitedInventories;
    public TileEntityManager()
    {
        zLevelRenderingList = new ArrayList<FlowComponent>();
        buttons = ManagerButtonRegistry.getButtons(this);
        removedIds = new ArrayList<Integer>();
        variables = new Variable[VariableColor.values().length];
        components = new TIntObjectHashMap<FlowComponent>();
        network = new TLongObjectHashMap<SystemCoord>();
        for (int i = 0; i < variables.length; i++)
        {
            variables[i] = new Variable(i);
        }
        this.triggerOffset = (((173 + xCoord) << 8 + yCoord) << 8 + zCoord) % 20;
    }

    public void removeFlowComponent(int idToRemove, TIntObjectHashMap<FlowComponent> componentMap)
    {

        componentMap.remove(idToRemove);

        if (selectedGroup != null && selectedGroup.getId() == idToRemove)
        {
            selectedGroup = null;
        }

        for (FlowComponent component : componentMap.valueCollection())
            component.updateConnectionIdsAtRemoval(idToRemove);
        //do this afterwards so the new ids won't mess anything up
//        for (int i = idToRemove; i < items.size(); i++)
//        {
//            items.get(i).decreaseId();
//        }
    }

    public void removeFlowComponent(int idToRemove)
    {
        removeFlowComponent(idToRemove, components);
        if (!worldObj.isRemote)
        {
            removedIds.add(idToRemove);
        } else
        {
            for (int i = 0; i < zLevelRenderingList.size(); i++)
            {
                if (zLevelRenderingList.get(i).getId() == idToRemove)
                {
                    zLevelRenderingList.remove(i);
                    break;
                }
            }
        }
        updateVariables();
    }

    public FlowComponent getFlowItem(int i)
    {
        return components.get(i);
    }

    public TLongObjectHashMap<SystemCoord> getNetwork()
    {
        return network;
    }

    public List<SystemCoord> getConnectedInventories()
    {
        List<SystemCoord> result = new ArrayList<SystemCoord>(network.valueCollection());
        Collections.sort(result);
        return result;
    }

    public Connection getCurrentlyConnecting()
    {
        return currentlyConnecting;
    }

    public void setCurrentlyConnecting(Connection currentlyConnecting)
    {
        this.currentlyConnecting = currentlyConnecting;
    }

    public void updateFirst()
    {
        if (firstCommandExecution)
        {
            updateInventories();
            updateVariables();

            firstCommandExecution = false;
        }
    }

    public void activateTrigger(FlowComponent component, EnumSet<ConnectionOption> validTriggerOutputs)
    {
        updateFirst();
        for (SystemCoord inventory : network.valueCollection())
        {
            if (inventory.tileEntity.isInvalid())
            {
                updateInventories();
                break;
            }
        }
        new Executor(this).executeTriggerCommand(component, validTriggerOutputs);
    }

    public void triggerRedstone(TileEntityReceiver inputTrigger)
    {
        for (FlowComponent item : getFlowItems())
        {
            if (item.getType().getCommandType() == CommandType.TRIGGER && item.getConnectionSet() == ConnectionSet.REDSTONE)
            {
                redstoneTrigger.onRedstoneTrigger(item, inputTrigger);
            }
        }
    }

    public Collection<FlowComponent> getFlowItems()
    {
        return components.valueCollection();
    }

    public void triggerChat()
    {
        for (FlowComponent item : getFlowItems())
        {
            if (item.getType().getCommandType() == CommandType.TRIGGER && item.getConnectionSet() == ConnectionSet.CHAT)
            {
                activateTrigger(item, EnumSet.allOf(ConnectionOption.class));
            }
        }
    }

    public void readGenericData(DataReader dr)
    {
        if (worldObj.isRemote)
        {
            if (dr.readBoolean())
            {
                updateInventories();
            } else
            {
                removeFlowComponent(dr.readComponentId());
            }
        } else
        {
            int buttonId = dr.readData(DataBitHelper.GUI_BUTTON_ID);
            if (buttonId >= 0 && buttonId < buttons.size())
            {
                IManagerButton button = buttons.get(buttonId);
                if (button.isVisible())
                {
                    button.onClick(dr);
                }
            }
        }
    }

    public List<Integer> getRemovedIds()
    {
        return removedIds;
    }

    @Override
    public Container getContainer(TileEntity te, InventoryPlayer inv)
    {
        return new ContainerManager((TileEntityManager)te, inv);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public GuiScreen getGui(TileEntity te, InventoryPlayer inv)
    {
        return new GuiManager((TileEntityManager)te, inv);
    }

    @Override
    public void readAllData(DataReader dr, EntityPlayer player)
    {
        updateInventories();
        int flowControlCount = dr.readComponentId();
        components.clear();
        getZLevelRenderingList().clear();
        for (int i = 0; i < flowControlCount; i++)
        {
            readAllComponentData(dr);
        }
        for (FlowComponent item : getFlowItems())
        {
            item.linkParentAfterLoad();
        }

        if (Settings.isAutoCloseGroup())
        {
            selectedGroup = null;
        } else
        {
            while (selectedGroup != null && !findNewSelectedComponent(selectedGroup.getId()))
            {
                selectedGroup = selectedGroup.getParent();
            }
        }
    }

    private boolean findNewSelectedComponent(int id)
    {
        if (components.containsKey(id))
        {
            selectedGroup = components.get(id);
            return true;
        }
        return false;
    }

    public List<FlowComponent> getZLevelRenderingList()
    {
        return zLevelRenderingList;
    }

    public void updateInventories()
    {
        usingUnlimitedInventories = false;
        SystemCoord[] oldCoordinates = network.values(new SystemCoord[network.size()]);

        List<SystemCoord> visited = new ArrayList<SystemCoord>();
        network.clear();
        Queue<SystemCoord> queue = new PriorityQueue<SystemCoord>();
        SystemCoord start = new SystemCoord(xCoord, yCoord, zCoord, worldObj.provider.dimensionId, 0, this);
        queue.add(start);
        visited.add(start);
        addInventory(start);

        while (!queue.isEmpty())
        {
            SystemCoord element = queue.poll();

            for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
            {
                SystemCoord target = new SystemCoord(element, direction);

                if (!visited.contains(target) && (Settings.isLimitless(this) || network.size() < MAX_CONNECTED_INVENTORIES))
                {
                    visited.add(target);

                    if ((Settings.isLimitless(this) || element.depth < MAX_CABLE_LENGTH) && BlockRegistry.blockCable.isCable(worldObj.getBlock(target.x, target.y, target.z), worldObj.getBlockMetadata(target.x, target.y, target.z)))
                    {
                        queue.add(target);
                    }

                    TileEntity te = worldObj.getTileEntity(target.x, target.y, target.z);
                    if (te == null) continue;
                    if (te instanceof TileEntityCluster)
                    {
                        for (TileEntityClusterElement tileEntityClusterElement : ((TileEntityCluster)te).getElements())
                        {
                            ((TileEntityCluster)te).setWorldObject(tileEntityClusterElement);
                            SystemCoord coord = target.copy();
                            coord.setTileEntity(tileEntityClusterElement);
                            addInventory(target);
                        }
                    } else
                    {
                        target.setTileEntity(te);
                        addInventory(target);
                    }
                }
            }

        }

        if (!firstInventoryUpdate)
        {
            for (SystemCoord oldCoordinate : oldCoordinates)
            {
                if (oldCoordinate.tileEntity instanceof ISystemListener)
                {
                    if (!network.containsKey(oldCoordinate.key))
                    {
                        ((ISystemListener)oldCoordinate.tileEntity).removed(this);
                    }
                }
            }
        }
        firstInventoryUpdate = false;
    }

    private void addInventory(SystemCoord target)
    {
        boolean isValidConnection = false;

        target.types = new HashSet<ISystemType>();
        for (ISystemType connectionBlockType : SystemTypeRegistry.getTypes())
        {
            if (connectionBlockType.isInstance(this, target.tileEntity))
            {
                isValidConnection = true;
                target.addType(connectionBlockType);
            }
        }

        if (isValidConnection)
        {
            if (target.depth >= MAX_CABLE_LENGTH || network.size() >= MAX_CONNECTED_INVENTORIES)
            {
                usingUnlimitedInventories = true;
            }
            network.put(target.key, target);
            if (target.tileEntity instanceof ISystemListener)
            {
                ((ISystemListener)target.tileEntity).added(this);
            }
        }
    }

    private void readAllComponentData(DataReader dr)
    {
        int x = dr.readData(DataBitHelper.FLOW_CONTROL_X);
        int y = dr.readData(DataBitHelper.FLOW_CONTROL_Y);
        int type = dr.readData(DataBitHelper.FLOW_CONTROL_TYPE_ID);
        int id = dr.readComponentId();

        FlowComponent flowComponent = new FlowComponent(this, x, y, id, CommandRegistry.getCommand(type));
        flowComponent.setComponentName(dr.readString(DataBitHelper.NAME_LENGTH));

        boolean hasParent = dr.readBoolean();
        if (hasParent)
        {
            flowComponent.setParentLoadId(dr.readComponentId());
        } else
        {
            flowComponent.setParentLoadId(-1);
        }

        for (Menu menu : flowComponent.getMenus())
        {
            menu.readData(dr);
        }

        flowComponent.clearConnections();
        for (int i = 0; i < flowComponent.getConnectionSet().getConnections().length; i++)
        {
            boolean hasConnection = dr.readBoolean();

            if (hasConnection)
            {
                Connection connection = new Connection(dr.readComponentId(), dr.readData(DataBitHelper.CONNECTION_ID));
                flowComponent.setConnection(i, connection);


                int length = dr.readData(DataBitHelper.NODE_ID);
                for (int j = 0; j < length; j++)
                {
                    connection.getNodes().add(new Point(dr.readData(DataBitHelper.FLOW_CONTROL_X), dr.readData(DataBitHelper.FLOW_CONTROL_Y)));
                }
            }
        }

        getZLevelRenderingList().add(0, flowComponent);
        addNewComponent(flowComponent);
        flowComponent.linkParentAfterLoad();
        updateVariables();
    }

    public boolean addNewComponent(FlowComponent component)
    {
        return components.put(component.getId(), component) != null;
    }

    public void updateVariables()
    {
        for (Variable variable : variables)
        {
            variable.setDeclaration(null);
        }

        for (FlowComponent item : getFlowItems())
        {
            if (item.getType() == CommandRegistry.VARIABLE && item.getConnectionSet() == ConnectionSet.EMPTY)
            {
                int selectedVariable = ((MenuVariable)item.getMenus().get(0)).getSelectedVariable();
                variables[selectedVariable].setDeclaration(item);
            }
        }
    }

    @Override
    public void readUpdatedData(DataReader dr, EntityPlayer player)
    {
        if (!worldObj.isRemote && dr.readBoolean())
        {
            boolean val = dr.readBoolean();
            if ((val || !isUsingUnlimitedStuff()) && player.capabilities.isCreativeMode)
            {
                Settings.setLimitless(this, val);
            }
            //TODO use ids for different actions
            /*System.out.println("ACTION");
            for (FlowComponent item : items) {
                item.adjustEverythingToGridRaw();
            }
            for (FlowComponent item : items) {
                item.adjustEverythingToGridFine();
            } */
            return;
        }

        boolean isNew = worldObj.isRemote && dr.readBoolean();
        if (isNew)
        {
            readAllComponentData(dr);
        } else
        {
            boolean isSpecificComponent = dr.readBoolean();
            if (isSpecificComponent)
            {

                INetworkReader nr = getNetworkReaderForComponentPacket(dr, this);

                if (nr != null)
                {
                    nr.readNetworkComponent(dr);
                }
            } else
            {
                readGenericData(dr);
            }
        }
    }

    @Override
    public void writeAllData(DataWriter dw)
    {
        dw.writeComponentId(this, components.size());
        for (FlowComponent flowComponent : getFlowItems())
        {
            PacketHandler.writeAllComponentData(dw, flowComponent);
        }
    }

    public int getNextFreeID()
    {
        while (components.containsKey(++maxID) || maxID < 0) if (maxID < 0) maxID = 0;
        return maxID;
    }

    public boolean isUsingUnlimitedStuff()
    {
        return components.size() > MAX_COMPONENT_AMOUNT || usingUnlimitedInventories;
    }

    private INetworkReader getNetworkReaderForComponentPacket(DataReader dr, TileEntityManager jam)
    {

        int componentId = dr.readComponentId();

        FlowComponent component = jam.getFlowItem(componentId);

        if (dr.readBoolean())
        {
            int menuId = dr.readData(DataBitHelper.FLOW_CONTROL_MENU_COUNT);
            if (menuId >= 0 && menuId < component.getMenus().size())
            {
                return component.getMenus().get(menuId);
            }
        } else
        {
            return component;
        }

        return null;
    }

    public Variable[] getVariables()
    {
        return variables;
    }

    public void triggerBUD(TileEntityBUD tileEntityBUD)
    {
        for (FlowComponent item : getFlowItems())
        {
            if (item.getType().getCommandType() == CommandType.TRIGGER && item.getConnectionSet() == ConnectionSet.BUD)
            {
                budTrigger.triggerBUD(item, tileEntityBUD);
            }
        }
    }

    public FlowComponent getSelectedGroup()
    {
        return selectedGroup;
    }

    public void setSelectedGroup(FlowComponent selectedGroup)
    {
        this.selectedGroup = selectedGroup;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTagCompound)
    {
        super.readFromNBT(nbtTagCompound);

        readContentFromNBT(nbtTagCompound, false);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbtTagCompound)
    {
        super.writeToNBT(nbtTagCompound);

        writeContentToNBT(nbtTagCompound, false);
    }

    @Override
    public void updateEntity()
    {
        justSentServerComponentRemovalPacket = false;
        if (!worldObj.isRemote)
        {
            StevesHooks.tickTriggers(this);
            if (timer++ % 20 == triggerOffset)
            {
                for (FlowComponent item : getFlowItems())
                {
                    if (item.getType().getCommandType() == CommandType.TRIGGER)
                    {
                        MenuInterval componentMenuInterval = (MenuInterval)item.getMenus().get(TriggerHelper.TRIGGER_INTERVAL_ID);
                        int interval = componentMenuInterval.getInterval();
                        if (interval == 0)
                        {
                            continue;
                        }
                        item.setCurrentInterval(item.getCurrentInterval() + 1);
                        if (item.getCurrentInterval() >= interval)
                        {
                            item.setCurrentInterval(0);

                            EnumSet<ConnectionOption> valid = EnumSet.of(ConnectionOption.INTERVAL);
                            if (item.getConnectionSet() == ConnectionSet.REDSTONE)
                            {
                                redstoneTrigger.onTrigger(item, valid);
                            } else if (item.getConnectionSet() == ConnectionSet.BUD)
                            {
                                budTrigger.onTrigger(item, valid);
                            }
                            activateTrigger(item, valid);
                        }
                    }
                }
            }
        }
    }

    public void writeContentToNBT(NBTTagCompound nbtTagCompound, boolean pickup)
    {
        nbtTagCompound.setByte(NBT_TIMER, (byte)(timer % 20));
        nbtTagCompound.setInteger(NBT_MAX_ID, maxID);
        NBTTagList components = new NBTTagList();
        for (FlowComponent item : getFlowItems())
        {
            NBTTagCompound component = new NBTTagCompound();
            item.writeToNBT(component, pickup);
            components.appendTag(component);
        }
        nbtTagCompound.setTag(NBT_COMPONENTS, components);


        NBTTagList variablesTag = new NBTTagList();
        for (Variable variable : variables)
        {
            NBTTagCompound variableTag = new NBTTagCompound();
            variable.writeToNBT(variableTag);
            variablesTag.appendTag(variableTag);
        }
        nbtTagCompound.setTag(NBT_VARIABLES, variablesTag);
    }

    public void readContentFromNBT(NBTTagCompound nbtTagCompound, boolean pickup)
    {
        timer = nbtTagCompound.getByte(NBT_TIMER);
        maxID = nbtTagCompound.getInteger(NBT_MAX_ID);
        NBTTagList components = nbtTagCompound.getTagList(NBT_COMPONENTS, 10);
        for (int i = 0; i < components.tagCount(); i++)
        {
            NBTTagCompound component = components.getCompoundTagAt(i);
            FlowComponent flowComponent = FlowComponent.readFromNBT(this, component, 12, pickup);
            this.components.put(flowComponent.getId(), flowComponent);
        }

        for (FlowComponent item : getFlowItems())
        {
            item.linkParentAfterLoad();
        }

        NBTTagList variablesTag = nbtTagCompound.getTagList(NBT_VARIABLES, 10);
        for (int i = 0; i < variablesTag.tagCount(); i++)
        {
            NBTTagCompound variableTag = variablesTag.getCompoundTagAt(i);
            variables[i].readFromNBT(variableTag);
        }

    }

    public String getUniqueComponentName(FlowComponent component)
    {
        String name = getComponentName(component);
        int modifier = 0;
        for (int i : components.keys())
        {
            FlowComponent other = components.get(i);
            if (getComponentName(other).equals(name)) modifier++;
        }
        if (modifier > 0) name += " [" + modifier + "]";
        return name;
    }

    public String getComponentName(FlowComponent component)
    {
        String name = component.getComponentName();
        if (name == null) name = component.getType().getName();
        return name;
    }

    public boolean hasInventory(long key)
    {
        return network.containsKey(key);
    }

    public SystemCoord getInventory(long selected)
    {
        return network.get(selected);
    }
}
