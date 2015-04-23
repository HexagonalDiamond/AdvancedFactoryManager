package advancedsystemsmanager.flow.execution;


import advancedsystemsmanager.api.IConditionStuffMenu;
import advancedsystemsmanager.api.execution.IItemBufferElement;
import advancedsystemsmanager.api.execution.IItemBufferSubElement;
import advancedsystemsmanager.flow.Connection;
import advancedsystemsmanager.flow.FlowComponent;
import advancedsystemsmanager.flow.elements.*;
import advancedsystemsmanager.flow.menus.*;
import advancedsystemsmanager.flow.setting.ItemSetting;
import advancedsystemsmanager.flow.setting.LiquidSetting;
import advancedsystemsmanager.flow.setting.Setting;
import advancedsystemsmanager.registry.ConnectionOption;
import advancedsystemsmanager.tileentities.TileEntityCreative;
import advancedsystemsmanager.tileentities.manager.TileEntityManager;
import advancedsystemsmanager.util.ConnectionBlock;
import advancedsystemsmanager.util.ConnectionBlockType;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import java.util.*;

public class CommandExecutor
{

    public TileEntityManager manager;
    List<ItemBufferElement> itemBuffer;
    List<CraftingBufferFluidElement> craftingBufferHigh;
    List<CraftingBufferFluidElement> craftingBufferLow;
    List<LiquidBufferElement> liquidBuffer;
    public List<Integer> usedCommands;

    public static final int MAX_FLUID_TRANSFER = 10000000;


    public CommandExecutor(TileEntityManager manager)
    {
        this.manager = manager;
        itemBuffer = new ArrayList<ItemBufferElement>();
        craftingBufferHigh = new ArrayList<CraftingBufferFluidElement>();
        craftingBufferLow = new ArrayList<CraftingBufferFluidElement>();
        liquidBuffer = new ArrayList<LiquidBufferElement>();
        usedCommands = new ArrayList<Integer>();
    }

    public CommandExecutor(TileEntityManager manager, List<ItemBufferElement> itemBufferSplit, List<CraftingBufferFluidElement> craftingBufferHighSplit, List<CraftingBufferFluidElement> craftingBufferLowSplit, List<LiquidBufferElement> liquidBufferSplit, List<Integer> usedCommandCopy)
    {
        this.manager = manager;
        this.itemBuffer = itemBufferSplit;
        this.craftingBufferHigh = craftingBufferHighSplit;
        this.craftingBufferLow = craftingBufferLowSplit;
        this.usedCommands = usedCommandCopy;
        this.liquidBuffer = liquidBufferSplit;
    }

    public void executeTriggerCommand(FlowComponent command, EnumSet<ConnectionOption> validTriggerOutputs)
    {
        for (Variable variable : manager.getVariables())
        {
            if (variable.isValid())
            {
                if (!variable.hasBeenExecuted() || ((MenuVariable)variable.getDeclaration().getMenus().get(0)).getVariableMode() == MenuVariable.VariableMode.LOCAL)
                {
                    executeCommand(variable.getDeclaration(), 0);
                    variable.setExecuted(true);
                }
            }
        }

        executeChildCommands(command, validTriggerOutputs);
    }

    public void executeChildCommands(FlowComponent command, EnumSet<ConnectionOption> validTriggerOutputs)
    {
        for (int i = 0; i < command.getConnectionSet().getConnections().length; i++)
        {
            Connection connection = command.getConnection(i);
            ConnectionOption option = command.getConnectionSet().getConnections()[i];
            if (connection != null && !option.isInput() && validTriggerOutputs.contains(option))
            {
                executeCommand(manager.getFlowItems().get(connection.getComponentId()), connection.getConnectionId());
            }
        }
    }


    public void executeCommand(FlowComponent command, int connectionId)
    {
        //a loop has occurred
        if (usedCommands.contains(command.getId()))
        {
            return;
        }

        try
        {
            usedCommands.add(command.getId());
            switch (command.getType().getId())
            {
                case 0:
                    List<SlotInventoryHolder> inputInventory = getInventories(command.getMenus().get(0));
                    if (inputInventory != null)
                    {
                        getValidSlots(command.getMenus().get(1), inputInventory);
                        getItems(command.getMenus().get(2), inputInventory);
                    }
                    break;
                case 1:
                    List<SlotInventoryHolder> outputInventory = getInventories(command.getMenus().get(0));
                    if (outputInventory != null)
                    {
                        getValidSlots(command.getMenus().get(1), outputInventory);
                        insertItems(command.getMenus().get(2), outputInventory);
                    }
                    break;
                case 2:
                    List<SlotInventoryHolder> conditionInventory = getInventories(command.getMenus().get(0));
                    if (conditionInventory != null)
                    {
                        getValidSlots(command.getMenus().get(1), conditionInventory);
                        if (searchForStuff(command.getMenus().get(2), conditionInventory, false))
                        {
                            executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_TRUE));
                        } else
                        {
                            executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_FALSE));
                        }
                    }
                    return;
                case 3:
                    List<SlotInventoryHolder> inputTank = getTanks(command.getMenus().get(0));
                    if (inputTank != null)
                    {
                        getValidTanks(command.getMenus().get(1), inputTank);
                        getLiquids(command.getMenus().get(2), inputTank);
                    }
                    break;
                case 4:
                    List<SlotInventoryHolder> outputTank = getTanks(command.getMenus().get(0));
                    if (outputTank != null)
                    {
                        getValidTanks(command.getMenus().get(1), outputTank);
                        insertLiquids(command.getMenus().get(2), outputTank);
                    }
                    break;
                case 5:
                    List<SlotInventoryHolder> conditionTank = getTanks(command.getMenus().get(0));
                    if (conditionTank != null)
                    {
                        getValidTanks(command.getMenus().get(1), conditionTank);
                        if (searchForStuff(command.getMenus().get(2), conditionTank, true))
                        {
                            executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_TRUE));
                        } else
                        {
                            executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_FALSE));
                        }
                    }
                    return;
                case 6:
                    if (MenuSplit.isSplitConnection(command))
                    {
                        if (splitFlow(command.getMenus().get(0)))
                        {
                            return;
                        }
                    }
                    break;
                case 7:
                    List<SlotInventoryHolder> emitters = getEmitters(command.getMenus().get(0));
                    if (emitters != null)
                    {
                        for (SlotInventoryHolder emitter : emitters)
                        {
                            emitter.getEmitter().updateState((MenuRedstoneSidesEmitter)command.getMenus().get(1), (MenuRedstoneOutput)command.getMenus().get(2), (MenuPulse)command.getMenus().get(3));
                        }
                    }
                    break;
                case 8:
                    List<SlotInventoryHolder> nodes = getNodes(command.getMenus().get(0));
                    if (nodes != null)
                    {
                        if (evaluateRedstoneCondition(nodes, command))
                        {
                            executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_TRUE));
                        } else
                        {
                            executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_FALSE));
                        }
                    }

                    return;
                case 9:
                    List<SlotInventoryHolder> tiles = getTiles(command.getMenus().get(2));
                    if (tiles != null)
                    {
                        updateVariable(tiles, (MenuVariable)command.getMenus().get(0), (MenuListOrder)command.getMenus().get(3));
                    }
                    break;
                case 10:
                    updateForLoop(command, (MenuVariableLoop)command.getMenus().get(0), (MenuContainerTypes)command.getMenus().get(1), (MenuListOrder)command.getMenus().get(2));
                    executeChildCommands(command, EnumSet.of(ConnectionOption.STANDARD_OUTPUT));
                    return;
                case 11:
                    CraftingBufferFluidElement element = new CraftingBufferFluidElement(this, (MenuCrafting)command.getMenus().get(0), (MenuContainerScrap)command.getMenus().get(2));
                    if (((MenuCraftingPriority)command.getMenus().get(1)).shouldPrioritizeCrafting())
                    {
                        craftingBufferHigh.add(element);
                    } else
                    {
                        craftingBufferLow.add(element);
                    }
                    break;
                case 12:
                    if (connectionId < command.getChildrenInputNodes().size())
                    {
                        executeChildCommands(command.getChildrenInputNodes().get(connectionId), EnumSet.allOf(ConnectionOption.class));
                    }
                    return;
                case 13:
                    FlowComponent parent = command.getParent();
                    if (parent != null)
                    {
                        for (int i = 0; i < parent.getChildrenOutputNodes().size(); i++)
                        {
                            if (command.equals(parent.getChildrenOutputNodes().get(i)))
                            {
                                Connection connection = parent.getConnection(parent.getConnectionSet().getInputCount() + i);
                                if (connection != null)
                                {
                                    executeCommand(manager.getFlowItems().get(connection.getComponentId()), connection.getConnectionId());
                                }
                                break;
                            }
                        }
                    }
                    return;
                case 14:
                    List<SlotInventoryHolder> camouflage = getCamouflage(command.getMenus().get(0));
                    if (camouflage != null)
                    {
                        MenuCamouflageShape shape = (MenuCamouflageShape)command.getMenus().get(1);
                        MenuCamouflageInside inside = (MenuCamouflageInside)command.getMenus().get(2);
                        MenuCamouflageSides sides = (MenuCamouflageSides)command.getMenus().get(3);
                        MenuCamouflageItems items = (MenuCamouflageItems)command.getMenus().get(4);

                        if (items.isFirstRadioButtonSelected() || items.getSettings().get(0).isValid())
                        {
                            ItemStack itemStack = items.isFirstRadioButtonSelected() ? null : ((ItemSetting)items.getSettings().get(0)).getItem();
                            for (SlotInventoryHolder slotInventoryHolder : camouflage)
                            {
                                slotInventoryHolder.getCamouflage().setBounds(shape);
                                for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
                                {
                                    if (sides.isSideRequired(i))
                                    {
                                        slotInventoryHolder.getCamouflage().setItem(itemStack, i, inside.getCurrentType());
                                    }
                                }
                            }
                        }
                    }
                    break;
                case 15:
                    List<SlotInventoryHolder> sign = getSign(command.getMenus().get(0));
                    if (sign != null)
                    {
                        for (SlotInventoryHolder slotInventoryHolder : sign)
                        {
                            slotInventoryHolder.getSign().updateSign((MenuSignText)command.getMenus().get(1));
                        }
                    }
            }


            executeChildCommands(command, EnumSet.allOf(ConnectionOption.class));

        } finally
        {
            usedCommands.remove((Integer)command.getId());
        }
    }


    public List<SlotInventoryHolder> getEmitters(Menu menu)
    {
        return getContainers(manager, menu, ConnectionBlockType.EMITTER);
    }

    public List<SlotInventoryHolder> getInventories(Menu menu)
    {
        return getContainers(manager, menu, ConnectionBlockType.INVENTORY);
    }

    public List<SlotInventoryHolder> getTanks(Menu menu)
    {
        return getContainers(manager, menu, ConnectionBlockType.TANK);
    }

    public List<SlotInventoryHolder> getNodes(Menu menu)
    {
        return getContainers(manager, menu, ConnectionBlockType.NODE);
    }

    public List<SlotInventoryHolder> getCamouflage(Menu menu)
    {
        return getContainers(manager, menu, ConnectionBlockType.CAMOUFLAGE);
    }

    public List<SlotInventoryHolder> getSign(Menu menu)
    {
        return getContainers(manager, menu, ConnectionBlockType.SIGN);
    }

    public List<SlotInventoryHolder> getTiles(Menu menu)
    {
        return getContainers(manager, menu, null);
    }

    public static List<SlotInventoryHolder> getContainers(TileEntityManager manager, Menu menu, ConnectionBlockType type)
    {
        MenuContainer menuContainer = (MenuContainer)menu;

        if (menuContainer.getSelectedInventories().size() == 0)
        {
            return null;
        }

        List<SlotInventoryHolder> ret = new ArrayList<SlotInventoryHolder>();
        List<ConnectionBlock> inventories = manager.getConnectedInventories();
        Variable[] variables = manager.getVariables();
        for (int i = 0; i < variables.length; i++)
        {
            Variable variable = variables[i];
            if (variable.isValid())
            {

                for (int val : menuContainer.getSelectedInventories())
                {
                    if (val == i)
                    {
                        List<Integer> selection = variable.getContainers();

                        for (int selected : selection)
                        {
                            addContainer(inventories, ret, selected, menuContainer, type, ((MenuContainerTypes)variable.getDeclaration().getMenus().get(1)).getValidTypes());
                        }
                        break;
                    }
                }
            }
        }

        for (int i = 0; i < menuContainer.getSelectedInventories().size(); i++)
        {
            int selected = menuContainer.getSelectedInventories().get(i) - VariableColor.values().length;

            addContainer(inventories, ret, selected, menuContainer, type, EnumSet.allOf(ConnectionBlockType.class));
        }

        if (ret.isEmpty())
        {
            return null;
        } else
        {
            return ret;
        }
    }

    public static void addContainer(List<ConnectionBlock> inventories, List<SlotInventoryHolder> ret, int selected, MenuContainer menuContainer, ConnectionBlockType requestType, EnumSet<ConnectionBlockType> variableType)
    {
        if (selected >= 0 && selected < inventories.size())
        {
            ConnectionBlock connection = inventories.get(selected);

            if (connection.isOfType(requestType) && connection.isOfAnyType(variableType) && !connection.getTileEntity().isInvalid() && !containsTe(ret, connection.getTileEntity()))
            {
                ret.add(new SlotInventoryHolder(selected, connection.getTileEntity(), menuContainer.getOption()));
            }

        }
    }

    public static boolean containsTe(List<SlotInventoryHolder> lst, TileEntity te)
    {
        for (SlotInventoryHolder slotInventoryHolder : lst)
        {
            if (slotInventoryHolder.getTile().xCoord == te.xCoord && slotInventoryHolder.getTile().yCoord == te.yCoord && slotInventoryHolder.getTile().zCoord == te.zCoord && slotInventoryHolder.getTile().getClass().equals(te.getClass()))
            {
                return true;
            }
        }
        return false;
    }

    public void getValidSlots(Menu menu, List<SlotInventoryHolder> inventories)
    {
        MenuTargetInventory menuTarget = (MenuTargetInventory)menu;

        for (int i = 0; i < inventories.size(); i++)
        {
            IInventory inventory = inventories.get(i).getInventory();
            Map<Integer, SlotSideTarget> validSlots = inventories.get(i).getValidSlots();

            for (int side = 0; side < MenuTarget.directions.length; side++)
            {
                if (menuTarget.isActive(side))
                {
                    int[] inventoryValidSlots;
                    if (inventory instanceof ISidedInventory)
                    {
                        inventoryValidSlots = ((ISidedInventory)inventory).getAccessibleSlotsFromSide(side);
                    } else
                    {
                        inventoryValidSlots = new int[inventory.getSizeInventory()];
                        for (int j = 0; j < inventoryValidSlots.length; j++)
                        {
                            inventoryValidSlots[j] = j;
                        }
                    }
                    int start;
                    int end;
                    if (menuTarget.useAdvancedSetting(side))
                    {
                        start = menuTarget.getStart(side);
                        end = menuTarget.getEnd(side);
                    } else
                    {
                        start = 0;
                        end = inventory.getSizeInventory();
                    }

                    if (start > end)
                    {
                        continue;
                    }


                    for (int inventoryValidSlot : inventoryValidSlots)
                    {
                        if (inventoryValidSlot >= start && inventoryValidSlot <= end)
                        {
                            SlotSideTarget target = validSlots.get(inventoryValidSlot);
                            if (target == null)
                            {
                                validSlots.put(inventoryValidSlot, new SlotSideTarget(inventoryValidSlot, side));
                            } else
                            {
                                target.addSide(side);
                            }
                        }
                    }
                }
            }

        }

    }

    public void getValidTanks(Menu menu, List<SlotInventoryHolder> tanks)
    {
        MenuTargetTank menuTarget = (MenuTargetTank)menu;

        for (int i = 0; i < tanks.size(); i++)
        {
            IFluidHandler tank = tanks.get(i).getTank();
            Map<Integer, SlotSideTarget> validTanks = tanks.get(i).getValidSlots();

            for (int side = 0; side < MenuTarget.directions.length; side++)
            {
                if (menuTarget.isActive(side))
                {
                    if (menuTarget.useAdvancedSetting(side))
                    {
                        boolean empty = true;
                        for (FluidTankInfo fluidTankInfo : tank.getTankInfo(MenuTarget.directions[side]))
                        {
                            if (fluidTankInfo.fluid != null && fluidTankInfo.fluid.amount > 0)
                            {
                                empty = false;
                                break;
                            }
                        }

                        if (empty != menuTarget.requireEmpty(side))
                        {
                            continue;
                        }
                    }


                    SlotSideTarget target = validTanks.get(0);
                    if (target == null)
                    {
                        validTanks.put(0, new SlotSideTarget(0, side));
                    } else
                    {
                        target.addSide(side);
                    }


                }
            }

        }

    }

    public boolean isSlotValid(IInventory inventory, ItemStack item, SlotSideTarget slot, boolean isInput)
    {
        if (item == null)
        {
            return false;
        } else if (inventory instanceof ISidedInventory)
        {
            boolean hasValidSide = false;
            for (int side : slot.getSides())
            {
                if (isInput && ((ISidedInventory)inventory).canExtractItem(slot.getSlot(), item, side))
                {
                    hasValidSide = true;
                    break;
                } else if (!isInput && ((ISidedInventory)inventory).canInsertItem(slot.getSlot(), item, side))
                {
                    hasValidSide = true;
                    break;
                }
            }

            if (!hasValidSide)
            {
                return false;
            }
        }

        return isInput || inventory.isItemValidForSlot(slot.getSlot(), item);
    }

    public void getItems(Menu menu, List<SlotInventoryHolder> inventories)
    {
        for (SlotInventoryHolder inventory : inventories)
        {

            MenuStuff menuItem = (MenuStuff)menu;
            if (inventory.getInventory() instanceof TileEntityCreative)
            {
                if (menuItem.useWhiteList())
                {
                    for (SlotSideTarget slot : inventory.getValidSlots().values())
                    {
                        for (Setting setting : menuItem.getSettings())
                        {
                            ItemStack item = ((ItemSetting)setting).getItem();
                            if (item != null)
                            {
                                item = item.copy();
                                item.stackSize = setting.isLimitedByAmount() ? setting.getAmount() : setting.getDefaultAmount();
                                addItemToBuffer(menuItem, inventory, setting, item, slot);
                            }
                        }
                    }
                }
            } else
            {
                for (SlotSideTarget slot : inventory.getValidSlots().values())
                {
                    ItemStack itemStack = inventory.getInventory().getStackInSlot(slot.getSlot());

                    if (!isSlotValid(inventory.getInventory(), itemStack, slot, true))
                    {
                        continue;
                    }


                    Setting setting = isItemValid(menu, itemStack);
                    addItemToBuffer(menuItem, inventory, setting, itemStack, slot);
                }
            }
        }
    }

    public void addItemToBuffer(MenuStuff menuItem, SlotInventoryHolder inventory, Setting setting, ItemStack itemStack, SlotSideTarget slot)
    {

        if ((menuItem.useWhiteList() == (setting != null)) || (setting != null && setting.isLimitedByAmount()))
        {
            FlowComponent owner = menuItem.getParent();
            SlotStackInventoryHolder target = new SlotStackInventoryHolder(itemStack, inventory.getInventory(), slot.getSlot());

            boolean added = false;
            for (ItemBufferElement itemBufferElement : itemBuffer)
            {
                if (itemBufferElement.addTarget(owner, setting, inventory, target))
                {
                    added = true;
                    break;
                }
            }

            if (!added)
            {
                ItemBufferElement itemBufferElement = new ItemBufferElement(owner, setting, inventory, menuItem.useWhiteList(), target);
                itemBuffer.add(itemBufferElement);
            }

        }
    }

    public void getLiquids(Menu menu, List<SlotInventoryHolder> tanks)
    {
        for (SlotInventoryHolder tank : tanks)
        {
            MenuStuff menuItem = (MenuStuff)menu;
            if (tank.getTank() instanceof TileEntityCreative)
            {
                if (menuItem.useWhiteList())
                {
                    for (SlotSideTarget slot : tank.getValidSlots().values())
                    {
                        for (Setting setting : menuItem.getSettings())
                        {
                            Fluid fluid = ((LiquidSetting)setting).getFluid();
                            if (fluid != null)
                            {
                                FluidStack liquid = new FluidStack(fluid, setting.isLimitedByAmount() ? setting.getAmount() : setting.getDefaultAmount());

                                if (liquid != null)
                                {
                                    addLiquidToBuffer(menuItem, tank, setting, liquid, 0);
                                }
                            }
                        }
                    }
                }
            } else
            {
                for (SlotSideTarget slot : tank.getValidSlots().values())
                {
                    List<FluidTankInfo> tankInfos = new ArrayList<FluidTankInfo>();
                    for (int side : slot.getSides())
                    {
                        FluidTankInfo[] currentTankInfos = tank.getTank().getTankInfo(ForgeDirection.VALID_DIRECTIONS[side]);
                        if (currentTankInfos == null)
                        {
                            continue;
                        }
                        for (FluidTankInfo fluidTankInfo : currentTankInfos)
                        {
                            if (fluidTankInfo == null)
                            {
                                continue;
                            }

                            boolean alreadyUsed = false;
                            for (FluidTankInfo tankInfo : tankInfos)
                            {
                                if (FluidStack.areFluidStackTagsEqual(tankInfo.fluid, fluidTankInfo.fluid) && tankInfo.capacity == fluidTankInfo.capacity)
                                {
                                    alreadyUsed = true;
                                }
                            }

                            if (alreadyUsed)
                            {
                                continue;
                            }

                            FluidStack fluidStack = fluidTankInfo.fluid;

                            if (fluidStack == null)
                            {
                                continue;
                            }

                            fluidStack = fluidStack.copy();

                            Setting setting = isLiquidValid(menu, fluidStack);
                            addLiquidToBuffer(menuItem, tank, setting, fluidStack, side);
                        }

                        for (FluidTankInfo fluidTankInfo : tank.getTank().getTankInfo(ForgeDirection.VALID_DIRECTIONS[side]))
                        {
                            if (fluidTankInfo != null)
                            {
                                tankInfos.add(fluidTankInfo);
                            }
                        }
                    }
                }
            }
        }
    }


    public void addLiquidToBuffer(MenuStuff menuItem, SlotInventoryHolder tank, Setting setting, FluidStack fluidStack, int side)
    {
        if ((menuItem.useWhiteList() == (setting != null)) || (setting != null && setting.isLimitedByAmount()))
        {
            FlowComponent owner = menuItem.getParent();
            StackTankHolder target = new StackTankHolder(fluidStack, tank.getTank(), ForgeDirection.VALID_DIRECTIONS[side]);

            boolean added = false;
            for (LiquidBufferElement liquidBufferElement : liquidBuffer)
            {
                if (liquidBufferElement.addTarget(owner, setting, tank, target))
                {
                    added = true;
                    break;
                }
            }

            if (!added)
            {
                LiquidBufferElement itemBufferElement = new LiquidBufferElement(owner, setting, tank, menuItem.useWhiteList(), target);
                liquidBuffer.add(itemBufferElement);
            }

        }
    }

    public Setting isItemValid(Menu menu, ItemStack itemStack)
    {
        MenuStuff menuItem = (MenuStuff)menu;

        for (Setting setting : menuItem.getSettings())
        {
            if (((ItemSetting)setting).isEqualForCommandExecutor(itemStack))
            {
                return setting;
            }
        }

        return null;
    }

    public Setting isLiquidValid(Menu menu, FluidStack fluidStack)
    {
        MenuStuff menuItem = (MenuStuff)menu;

        if (fluidStack != null)
        {
            int fluidId = fluidStack.fluidID;
            for (Setting setting : menuItem.getSettings())
            {
                if (setting.isValid() && ((LiquidSetting)setting).getLiquidId() == fluidId)
                {
                    return setting;
                }
            }
        }

        return null;
    }

    public void insertItems(Menu menu, List<SlotInventoryHolder> inventories)
    {
        MenuStuff menuItem = (MenuStuff)menu;

        List<OutputItemCounter> outputCounters = new ArrayList<OutputItemCounter>();
        for (SlotInventoryHolder inventoryHolder : inventories)
        {
            if (!inventoryHolder.isShared())
            {
                outputCounters.clear();
            }

            for (CraftingBufferFluidElement craftingBufferFluidElement : craftingBufferHigh)
            {
                insertItemsFromInputBufferElement(menuItem, inventories, outputCounters, inventoryHolder, craftingBufferFluidElement);
            }
            for (ItemBufferElement itemBufferElement : itemBuffer)
            {
                insertItemsFromInputBufferElement(menuItem, inventories, outputCounters, inventoryHolder, itemBufferElement);
            }
            for (CraftingBufferFluidElement craftingBufferFluidElement : craftingBufferLow)
            {
                insertItemsFromInputBufferElement(menuItem, inventories, outputCounters, inventoryHolder, craftingBufferFluidElement);
            }
        }
    }

    public void insertItemsFromInputBufferElement(MenuStuff menuItem, List<SlotInventoryHolder> inventories, List<OutputItemCounter> outputCounters, SlotInventoryHolder inventoryHolder, IItemBufferElement itemBufferElement)
    {
        IInventory inventory = inventoryHolder.getInventory();

        IItemBufferSubElement subElement;
        itemBufferElement.prepareSubElements();
        while ((subElement = itemBufferElement.getSubElement()) != null)
        {
            ItemStack itemStack = subElement.getValue();

            Setting setting = isItemValid(menuItem, itemStack);

            if ((menuItem.useWhiteList() == (setting == null)) && (setting == null || !setting.isLimitedByAmount()))
            {
                continue;
            }

            OutputItemCounter outputItemCounter = null;
            for (OutputItemCounter e : outputCounters)
            {
                if (e.areSettingsSame(setting))
                {
                    outputItemCounter = e;
                    break;
                }
            }

            if (outputItemCounter == null)
            {
                outputItemCounter = new OutputItemCounter(itemBuffer, inventories, inventory, setting, menuItem.useWhiteList());
                outputCounters.add(outputItemCounter);
            }

            for (SlotSideTarget slot : inventoryHolder.getValidSlots().values())
            {
                if (!isSlotValid(inventory, itemStack, slot, false))
                {
                    continue;
                }

                ItemStack itemInSlot = inventory.getStackInSlot(slot.getSlot());
                boolean newItem = itemInSlot == null;
                if (newItem || (itemInSlot.isItemEqual(itemStack) && ItemStack.areItemStackTagsEqual(itemStack, itemInSlot) && itemStack.isStackable()))
                {
                    int itemCountInSlot = newItem ? 0 : itemInSlot.stackSize;

                    int moveCount = Math.min(subElement.getSizeLeft(), Math.min(inventory.getInventoryStackLimit(), itemStack.getMaxStackSize()) - itemCountInSlot);

                    moveCount = outputItemCounter.retrieveItemCount(moveCount);
                    moveCount = itemBufferElement.retrieveItemCount(moveCount);
                    if (moveCount > 0)
                    {

                        if (newItem)
                        {
                            itemInSlot = itemStack.copy();
                            itemInSlot.stackSize = 0;
                        }

                        itemBufferElement.decreaseStackSize(moveCount);
                        outputItemCounter.modifyStackSize(moveCount);
                        itemInSlot.stackSize += moveCount;
                        subElement.reduceBufferAmount(moveCount);

                        if (newItem)
                        {
                            inventory.setInventorySlotContents(slot.getSlot(), itemInSlot);
                        }

                        boolean done = false;
                        if (subElement.getSizeLeft() == 0)
                        {
                            subElement.remove();
                            itemBufferElement.removeSubElement();
                            done = true;
                        }

                        inventory.markDirty();
                        subElement.onUpdate();

                        if (done)
                        {
                            break;
                        }
                    }
                }

            }
        }
        itemBufferElement.releaseSubElements();
    }


    public void insertLiquids(Menu menu, List<SlotInventoryHolder> tanks)
    {
        MenuStuff menuItem = (MenuStuff)menu;

        List<OutputLiquidCounter> outputCounters = new ArrayList<OutputLiquidCounter>();
        for (SlotInventoryHolder tankHolder : tanks)
        {
            if (!tankHolder.isShared())
            {
                outputCounters.clear();
            }

            IFluidHandler tank = tankHolder.getTank();
            Iterator<LiquidBufferElement> bufferIterator = liquidBuffer.iterator();
            while (bufferIterator.hasNext())
            {
                LiquidBufferElement liquidBufferElement = bufferIterator.next();


                Iterator<StackTankHolder> liquidIterator = liquidBufferElement.getHolders().iterator();
                while (liquidIterator.hasNext())
                {
                    StackTankHolder holder = liquidIterator.next();
                    FluidStack fluidStack = holder.getFluidStack();

                    Setting setting = isLiquidValid(menu, fluidStack);

                    if ((menuItem.useWhiteList() == (setting == null)) && (setting == null || !setting.isLimitedByAmount()))
                    {
                        continue;
                    }

                    OutputLiquidCounter outputLiquidCounter = null;
                    for (OutputLiquidCounter e : outputCounters)
                    {
                        if (e.areSettingsSame(setting))
                        {
                            outputLiquidCounter = e;
                            break;
                        }
                    }

                    if (outputLiquidCounter == null)
                    {
                        outputLiquidCounter = new OutputLiquidCounter(liquidBuffer, tanks, tankHolder, setting, menuItem.useWhiteList());
                        outputCounters.add(outputLiquidCounter);
                    }

                    for (SlotSideTarget slot : tankHolder.getValidSlots().values())
                    {

                        for (int side : slot.getSides())
                        {
                            FluidStack temp = fluidStack.copy();
                            temp.amount = holder.getSizeLeft();
                            int amount = tank.fill(ForgeDirection.VALID_DIRECTIONS[side], temp, false);
                            amount = liquidBufferElement.retrieveItemCount(amount);
                            amount = outputLiquidCounter.retrieveItemCount(amount);

                            if (amount > 0)
                            {
                                FluidStack resource = fluidStack.copy();
                                resource.amount = amount;

                                resource = holder.getTank().drain(holder.getSide(), resource, true);
                                if (resource != null && resource.amount > 0)
                                {
                                    tank.fill(ForgeDirection.VALID_DIRECTIONS[side], resource, true);
                                    liquidBufferElement.decreaseStackSize(resource.amount);
                                    outputLiquidCounter.modifyStackSize(resource.amount);
                                    holder.reduceAmount(resource.amount);
                                    if (holder.getSizeLeft() == 0)
                                    {
                                        liquidIterator.remove();
                                        break;
                                    }
                                }
                            }

                        }

                    }
                }

            }

        }

    }

    public boolean searchForStuff(Menu menu, List<SlotInventoryHolder> inventories, boolean useLiquids)
    {
        if (inventories.get(0).isShared())
        {
            Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap = new HashMap<Integer, ConditionSettingChecker>();
            for (int i = 0; i < inventories.size(); i++)
            {
                calculateConditionData(menu, inventories.get(i), conditionSettingCheckerMap, useLiquids);
            }
            return checkConditionResult(menu, conditionSettingCheckerMap);
        } else
        {
            boolean useAnd = inventories.get(0).getSharedOption() == 1;
            for (int i = 0; i < inventories.size(); i++)
            {
                Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap = new HashMap<Integer, ConditionSettingChecker>();
                calculateConditionData(menu, inventories.get(i), conditionSettingCheckerMap, useLiquids);

                if (checkConditionResult(menu, conditionSettingCheckerMap))
                {
                    if (!useAnd)
                    {
                        return true;
                    }
                } else if (useAnd)
                {
                    return false;
                }
            }
            return useAnd;
        }
    }

    public void calculateConditionData(Menu menu, SlotInventoryHolder inventoryHolder, Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap, boolean useLiquid)
    {
        if (useLiquid)
        {
            calculateConditionDataLiquid(menu, inventoryHolder, conditionSettingCheckerMap);
        } else
        {
            calculateConditionDataItem(menu, inventoryHolder, conditionSettingCheckerMap);
        }
    }

    public void calculateConditionDataItem(Menu menu, SlotInventoryHolder inventoryHolder, Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap)
    {
        for (SlotSideTarget slot : inventoryHolder.getValidSlots().values())
        {
            ItemStack itemStack = inventoryHolder.getInventory().getStackInSlot(slot.getSlot());

            if (!isSlotValid(inventoryHolder.getInventory(), itemStack, slot, true))
            {
                continue;
            }

            Setting setting = isItemValid(menu, itemStack);
            if (setting != null)
            {
                ConditionSettingChecker conditionSettingChecker = conditionSettingCheckerMap.get(setting.getId());
                if (conditionSettingChecker == null)
                {
                    conditionSettingCheckerMap.put(setting.getId(), conditionSettingChecker = new ConditionSettingChecker(setting));
                }
                conditionSettingChecker.addCount(itemStack.stackSize);
            }
        }
    }

    public void calculateConditionDataLiquid(Menu menu, SlotInventoryHolder tank, Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap)
    {
        for (SlotSideTarget slot : tank.getValidSlots().values())
        {
            List<FluidTankInfo> tankInfos = new ArrayList<FluidTankInfo>();
            for (int side : slot.getSides())
            {
                FluidTankInfo[] currentTankInfos = tank.getTank().getTankInfo(ForgeDirection.VALID_DIRECTIONS[side]);
                if (currentTankInfos == null)
                {
                    continue;
                }
                for (FluidTankInfo fluidTankInfo : currentTankInfos)
                {
                    if (fluidTankInfo == null)
                    {
                        continue;
                    }
                    boolean alreadyUsed = false;
                    for (FluidTankInfo tankInfo : tankInfos)
                    {
                        if (FluidStack.areFluidStackTagsEqual(tankInfo.fluid, fluidTankInfo.fluid) && tankInfo.capacity == fluidTankInfo.capacity)
                        {
                            alreadyUsed = true;
                        }
                    }

                    if (alreadyUsed)
                    {
                        continue;
                    }

                    FluidStack fluidStack = fluidTankInfo.fluid;
                    Setting setting = isLiquidValid(menu, fluidStack);
                    if (setting != null)
                    {
                        ConditionSettingChecker conditionSettingChecker = conditionSettingCheckerMap.get(setting.getId());
                        if (conditionSettingChecker == null)
                        {
                            conditionSettingCheckerMap.put(setting.getId(), conditionSettingChecker = new ConditionSettingChecker(setting));
                        }
                        conditionSettingChecker.addCount(fluidStack.amount);
                    }
                }
                for (FluidTankInfo fluidTankInfo : tank.getTank().getTankInfo(ForgeDirection.VALID_DIRECTIONS[side]))
                {
                    if (fluidTankInfo != null)
                    {
                        tankInfos.add(fluidTankInfo);
                    }
                }
            }

        }
    }


    public boolean checkConditionResult(Menu menu, Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap)
    {
        MenuStuff menuItem = (MenuStuff)menu;
        IConditionStuffMenu menuCondition = (IConditionStuffMenu)menu;
        for (Setting setting : menuItem.getSettings())
        {
            if (setting.isValid())
            {
                ConditionSettingChecker conditionSettingChecker = conditionSettingCheckerMap.get(setting.getId());

                if (conditionSettingChecker != null && conditionSettingChecker.isTrue())
                {
                    if (!menuCondition.requiresAll())
                    {
                        return true;
                    }
                } else if (menuCondition.requiresAll())
                {
                    return false;
                }
            }
        }

        return menuCondition.requiresAll();
    }


    public boolean splitFlow(Menu menu)
    {
        MenuSplit split = (MenuSplit)menu;
        if (split.useSplit())
        {
            int amount = menu.getParent().getConnectionSet().getOutputCount();
            if (!split.useEmpty())
            {
                ConnectionOption[] connections = menu.getParent().getConnectionSet().getConnections();
                for (int i = 0; i < connections.length; i++)
                {
                    ConnectionOption connectionOption = connections[i];
                    if (!connectionOption.isInput() && menu.getParent().getConnection(i) == null)
                    {
                        amount--;
                    }
                }
            }

            int usedId = 0;
            ConnectionOption[] connections = menu.getParent().getConnectionSet().getConnections();
            for (int i = 0; i < connections.length; i++)
            {
                ConnectionOption connectionOption = connections[i];
                Connection connection = menu.getParent().getConnection(i);
                if (!connectionOption.isInput() && connection != null)
                {
                    List<ItemBufferElement> itemBufferSplit = new ArrayList<ItemBufferElement>();
                    List<LiquidBufferElement> liquidBufferSplit = new ArrayList<LiquidBufferElement>();

                    for (ItemBufferElement element : itemBuffer)
                    {
                        itemBufferSplit.add(element.getSplitElement(amount, usedId, split.useFair()));
                    }

                    for (LiquidBufferElement element : liquidBuffer)
                    {
                        liquidBufferSplit.add(element.getSplitElement(amount, usedId, split.useFair()));
                    }

                    List<Integer> usedCommandCopy = new ArrayList<Integer>();
                    for (int usedCommand : usedCommands)
                    {
                        usedCommandCopy.add(usedCommand);
                    }

                    CommandExecutor newExecutor = new CommandExecutor(manager, itemBufferSplit, new ArrayList<CraftingBufferFluidElement>(craftingBufferHigh), new ArrayList<CraftingBufferFluidElement>(craftingBufferLow), liquidBufferSplit, usedCommandCopy);
                    newExecutor.executeCommand(manager.getFlowItems().get(connection.getComponentId()), connection.getConnectionId());
                    usedId++;
                }
            }

            return true;
        }

        return false;
    }

    public boolean evaluateRedstoneCondition(List<SlotInventoryHolder> nodes, FlowComponent component)
    {
        return TileEntityManager.redstoneCondition.isTriggerPowered(nodes, component, true);
    }

    public void updateVariable(List<SlotInventoryHolder> tiles, MenuVariable menuVariable, MenuListOrder menuOrder)
    {

        MenuVariable.VariableMode mode = menuVariable.getVariableMode();
        Variable variable = manager.getVariables()[menuVariable.getSelectedVariable()];

        if (variable.isValid())
        {
            boolean remove = mode == MenuVariable.VariableMode.REMOVE;
            if (!remove && mode != MenuVariable.VariableMode.ADD)
            {
                variable.clearContainers();
            }

            List<Integer> idList = new ArrayList<Integer>();
            for (SlotInventoryHolder tile : tiles)
            {
                idList.add(tile.getId());
            }

            if (!menuVariable.isDeclaration())
            {
                idList = applyOrder(idList, menuOrder);
            }

            List<ConnectionBlock> inventories = manager.getConnectedInventories();
            EnumSet<ConnectionBlockType> validTypes = ((MenuContainerTypes)variable.getDeclaration().getMenus().get(1)).getValidTypes();
            for (int id : idList)
            {
                if (remove)
                {
                    variable.remove(id);
                } else if (id >= 0 && id < inventories.size() && inventories.get(id).isOfAnyType(validTypes))
                {
                    variable.add(id);
                }
            }

        }
    }

    public void updateForLoop(FlowComponent command, MenuVariableLoop variableMenu, MenuContainerTypes typesMenu, MenuListOrder orderMenu)
    {
        Variable list = variableMenu.getListVariable();
        Variable element = variableMenu.getElementVariable();

        if (!list.isValid() || !element.isValid())
        {
            return;
        }

        List<Integer> selection = applyOrder(list.getContainers(), orderMenu);

        EnumSet<ConnectionBlockType> validTypes = typesMenu.getValidTypes();
        validTypes.addAll(((MenuContainerTypes)element.getDeclaration().getMenus().get(1)).getValidTypes());
        List<ConnectionBlock> inventories = manager.getConnectedInventories();
        for (Integer selected : selection)
        {
            //Should always be true, simply making sure if the inventories have changed
            if (selected >= 0 && selected < inventories.size())
            {
                ConnectionBlock inventory = inventories.get(selected);
                if (inventory.isOfAnyType(validTypes))
                {
                    element.clearContainers();
                    element.add(selected);
                    executeChildCommands(command, EnumSet.of(ConnectionOption.FOR_EACH));
                }
            }
        }
    }

    public List<Integer> applyOrder(List<Integer> original, MenuListOrder orderMenu)
    {
        List<Integer> ret = new ArrayList<Integer>(original);
        if (orderMenu.getOrder() == MenuListOrder.LoopOrder.RANDOM)
        {
            Collections.shuffle(ret);
        } else if (orderMenu.getOrder() == MenuListOrder.LoopOrder.NORMAL)
        {
            if (!orderMenu.isReversed())
            {
                Collections.reverse(ret);
            }
        } else
        {
            Collections.sort(ret, orderMenu.getComparator());
        }

        if (!orderMenu.useAll())
        {
            int len = orderMenu.getAmount();
            while (ret.size() > len)
            {
                ret.remove(ret.size() - 1);
            }
        }

        return ret;
    }
}
