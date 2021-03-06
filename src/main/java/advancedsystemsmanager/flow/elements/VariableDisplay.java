package advancedsystemsmanager.flow.elements;

import advancedsystemsmanager.gui.GuiManager;
import advancedsystemsmanager.helpers.CollisionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class VariableDisplay
{

    public static final int VARIABLE_X = 15;
    public static final int VARIABLE_SIZE = 14;
    public static final int ARROW_SRC_X = 18;
    public static final int ARROW_SRC_Y = 20;
    public static final int ARROW_WIDTH = 6;
    public static final int ARROW_HEIGHT = 10;
    public static final int ARROW_X_RIGHT = 38;
    public static final int ARROW_Y = 3;
    public static final int TEXT_X = -40;
    public static final int TEXT_Y = 5;
    public String name;
    public int x;
    public int y;

    public VariableDisplay(String name, int x, int y)
    {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    @SideOnly(Side.CLIENT)
    public void draw(GuiManager gui, int mX, int mY)
    {
        if (name != null)
        {
            gui.drawString(name, x + TEXT_X, y + TEXT_Y, 0x404040);
        }

        gui.getManager().getVariables()[getValue()].draw(gui, x + VARIABLE_X, y);

        for (int i = 0; i < 2; i++)
        {
            int posX = x + (i == 0 ? 0 : ARROW_X_RIGHT);
            int posY = y + ARROW_Y;

            int srcXArrow = i;
            int srcYArrow = CollisionHelper.inBounds(posX, posY, ARROW_WIDTH, ARROW_HEIGHT, mX, mY) ? 1 : 0;

            gui.drawTexture(posX, posY, ARROW_SRC_X + srcXArrow * ARROW_WIDTH, ARROW_SRC_Y + srcYArrow * ARROW_HEIGHT, ARROW_WIDTH, ARROW_HEIGHT);
        }
    }

    public abstract int getValue();

    public abstract void setValue(int val);

    @SideOnly(Side.CLIENT)
    public void drawMouseOver(GuiManager gui, int mX, int mY)
    {
        if (CollisionHelper.inBounds(x + VARIABLE_X, y, VARIABLE_SIZE, VARIABLE_SIZE, mX, mY))
        {
            gui.drawMouseOver(gui.getManager().getVariables()[getValue()].getDescription(gui), mX, mY);
        }
    }

    public void onClick(int mX, int mY)
    {
        for (int i = -1; i <= 1; i += 2)
        {
            int posX = x + (i == 1 ? ARROW_X_RIGHT : 0);
            int posY = y + ARROW_Y;


            if (CollisionHelper.inBounds(posX, posY, ARROW_WIDTH, ARROW_HEIGHT, mX, mY))
            {
                int val = getValue();
                val += i;
                if (val < 0)
                {
                    val = VariableColor.values().length - 1;
                } else if (val == VariableColor.values().length)
                {
                    val = 0;
                }
                setValue(val);
                onUpdate();
                break;
            }
        }
    }

    public abstract void onUpdate();
}
