package advancedsystemsmanager.flow.execution;


import advancedsystemsmanager.api.execution.IItemBufferSubElement;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class SlotStackInventoryHolder implements IItemBufferSubElement
{
    public ItemStack itemStack;
    public IInventory inventory;
    public int slot;
    public int sizeLeft;

    public SlotStackInventoryHolder(ItemStack itemStack, IInventory inventory, int slot)
    {
        this.itemStack = itemStack;
        this.inventory = inventory;
        this.slot = slot;
        this.sizeLeft = itemStack.stackSize;
    }

    public ItemStack getItemStack()
    {
        return itemStack;
    }

    @Override
    public void remove()
    {
        if (itemStack.stackSize == 0)
        {
            getInventory().setInventorySlotContents(getSlot(), null);
        }
    }

    public IInventory getInventory()
    {
        return inventory;
    }

    @Override
    public void onUpdate()
    {
        getInventory().markDirty();
    }

    public int getSizeLeft()
    {
        return Math.min(itemStack.stackSize, sizeLeft);
    }

    @Override
    public int reduceBufferAmount(int amount)
    {
        itemStack.stackSize -= amount;
        sizeLeft -= amount;
        return amount;
    }

    @Override
    public ItemStack getKey()
    {
        return itemStack;
    }

    @Override
    public IInventory getContainer()
    {
        return inventory;
    }

    public SlotStackInventoryHolder getSplitElement(int elementAmount, int id, boolean fair)
    {
        SlotStackInventoryHolder element = new SlotStackInventoryHolder(this.itemStack, this.inventory, this.slot);
        int oldAmount = getSizeLeft();
        int amount = oldAmount / elementAmount;
        if (!fair)
        {
            int amountLeft = oldAmount % elementAmount;
            if (id < amountLeft)
            {
                amount++;
            }
        }

        element.sizeLeft = amount;
        return element;
    }

    public int getSlot()
    {
        return slot;
    }

    public void reduceAmount(int val)
    {
        itemStack.stackSize -= val;
        sizeLeft -= val;
    }
}
