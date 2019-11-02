package me.desht.pneumaticcraft.common.thirdparty.jei;

import me.desht.pneumaticcraft.api.PneumaticRegistry;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;
import me.desht.pneumaticcraft.client.gui.widget.IGuiWidget;
import me.desht.pneumaticcraft.client.gui.widget.WidgetTank;
import me.desht.pneumaticcraft.client.gui.widget.WidgetTemperature;
import me.desht.pneumaticcraft.client.util.GuiUtils;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
import me.desht.pneumaticcraft.lib.Names;
import me.desht.pneumaticcraft.lib.Textures;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.gui.*;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.gui.elements.DrawableResource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.FMLClientHandler;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class PneumaticCraftCategory<T extends IRecipeWrapper> implements IRecipeCategory<T> {
    private final IGuiHelper guiHelper;
    private final ResourceDrawable background = getGuiTexture();
    private static ITickTimer tickTimer;

    public PneumaticCraftCategory(IJeiHelpers jeiHelpers) {
        this.guiHelper = jeiHelpers.getGuiHelper();
        tickTimer = guiHelper.createTickTimer(60, 60, false);
    }

    @Nonnull
    @Override
    public String getModName() {
        return Names.MOD_NAME;
    }

    public static class MultipleInputOutputRecipeWrapper implements IRecipeWrapper {
        private final List<PositionedStack> input = new ArrayList<>();
        private final List<PositionedStack> output = new ArrayList<>();
        private final List<WidgetTank> inputLiquids = new ArrayList<>();
        private final List<WidgetTank> outputLiquids = new ArrayList<>();
        private final List<IGuiWidget> tooltipWidgets = new ArrayList<>();
        private float pressure;
        private float maxPressure;
        private float dangerPressure;
        private boolean drawPressureGauge;
        private int gaugeX, gaugeY;
        private WidgetTemperature tempWidget;
        private IHeatExchangerLogic heatExchanger;

        @Override
        public void getIngredients(@Nonnull IIngredients ingredients) {
            ingredients.setInputLists(VanillaTypes.ITEM, input.stream().map(PositionedStack::getStacks).collect(Collectors.toList()));
            ingredients.setInputs(VanillaTypes.FLUID, inputLiquids.stream().map(WidgetTank::getFluid).collect(Collectors.toList()));
            ingredients.setOutputLists(VanillaTypes.ITEM, output.stream().map(PositionedStack::getStacks).collect(Collectors.toList()));
            ingredients.setOutputs(VanillaTypes.FLUID, outputLiquids.stream().map(WidgetTank::getFluid).collect(Collectors.toList()));
        }

        void addIngredient(PositionedStack stack) {
            input.add(stack);
        }

        public void addIngredient(PositionedStack[] stacks) {
            Collections.addAll(input, stacks);
        }

        void addOutput(PositionedStack stack) {
            output.add(stack);
        }

        void addInputLiquid(FluidStack liquid, int x, int y) {
            addInputLiquid(new WidgetTank(x, y, liquid));
        }

        void addInputLiquid(WidgetTank tank) {
            inputLiquids.add(tank);
            recalculateTankSizes();
        }

        void addOutputLiquid(FluidStack liquid, int x, int y) {
            addOutputLiquid(new WidgetTank(x, y, liquid));
        }

        void addOutputLiquid(WidgetTank tank) {
            outputLiquids.add(tank);
            recalculateTankSizes();
        }

        private void recalculateTankSizes() {
            int maxFluid = 0;
            for (WidgetTank w : inputLiquids) {
                maxFluid = Math.max(maxFluid, w.getTank().getFluidAmount());
            }
            for (WidgetTank w : outputLiquids) {
                maxFluid = Math.max(maxFluid, w.getTank().getFluidAmount());
            }

//            if (maxFluid <= 10) {
//                maxFluid = 10;
//            } else if (maxFluid <= 100) {
//                maxFluid = 100;
//            } else if (maxFluid <= 1000) {
//                maxFluid = 1000;
//            } else {
//                maxFluid = 16000;
//            }
            for (WidgetTank w : inputLiquids) {
                w.getTank().setCapacity(maxFluid);
            }
            for (WidgetTank w : outputLiquids) {
                w.getTank().setCapacity(maxFluid);
            }
        }

        protected void addWidget(IGuiWidget widget) {
            tooltipWidgets.add(widget);
        }

        void setUsedPressure(int x, int y, float pressure, float maxPressure, float dangerPressure) {
            this.drawPressureGauge = true;
            this.pressure = pressure;
            this.maxPressure = maxPressure;
            this.dangerPressure = dangerPressure;
            this.gaugeX = x;
            this.gaugeY = y;
        }

        void setUsedTemperature(int x, int y, double temperature) {
            tempWidget = new WidgetTemperature(0, x, y, 273, 673,
                    heatExchanger = PneumaticRegistry.getInstance().getHeatRegistry().getHeatExchangerLogic(), (int) temperature) {
                @Override
                public void addTooltip(int mouseX, int mouseY, List<String> curTip, boolean shift) {
                    if(logic.getTemperatureAsInt()<10000){
                        curTip.add("Required Temperature: " + (logic.getTemperatureAsInt() - 273) + "\u00b0C");
                    }
                    else {
                        curTip.add("Required Temperature: " + (logic.getTemperatureAsInt()%10000 - 273) + "\u00b0C ~ "+ (Math.floorDiv(logic.getTemperatureAsInt(),10000) - 273) + "\u00b0C");
                    }
                }
            };
        }

        @Override
        public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
            for (IGuiWidget widget : tooltipWidgets) {
                widget.render(0, 0, 0);
            }
            if (drawPressureGauge) {
                drawAnimatedPressureGauge(gaugeX, gaugeY, -1, pressure, 5, 7);
            }
            if (tempWidget != null) {
                heatExchanger.setTemperature(tickTimer.getValue() * (tempWidget.getScales()[0] - 273.0) / tickTimer.getMaxValue() + 273.0);
                tempWidget.render(0, 0, 0);
            }
        }

        @Nonnull
        @Override
        public List<String> getTooltipStrings(int mouseX, int mouseY) {
            List<String> currenttip = new ArrayList<>();

            Point mouse = new Point(mouseX, mouseY);
            for (IGuiWidget widget : tooltipWidgets) {
                if (widget.getBounds().contains(mouse)) {
                    widget.addTooltip(mouse.x, mouse.y, currenttip, false);
                }
            }
            if (tempWidget != null) {
                if (tempWidget.getBounds().contains(mouse)) {
                    heatExchanger.setTemperature(tempWidget.getScales()[0]);
                    tempWidget.addTooltip(mouse.x, mouse.y, currenttip, false);
                }
            }

            if (drawPressureGauge
                    && mouseX >= gaugeX - GuiUtils.PRESSURE_GAUGE_RADIUS && mouseX <= gaugeX + GuiUtils.PRESSURE_GAUGE_RADIUS
                    && mouseY >= gaugeY - GuiUtils.PRESSURE_GAUGE_RADIUS && mouseY <= gaugeY + GuiUtils.PRESSURE_GAUGE_RADIUS) {
                currenttip.add(this.pressure + " bar");
            }

            return currenttip;
        }

        @Override
        public boolean handleClick(Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
            return false;
        }

    }

    private static void drawAnimatedPressureGauge(int x, int y, float minPressure, float minWorkingPressure, float dangerPressure, float maxPressure) {
        float p2 = minWorkingPressure * ((float) tickTimer.getValue() / tickTimer.getMaxValue());
        GuiUtils.drawPressureGauge(FMLClientHandler.instance().getClient().fontRenderer, minPressure, maxPressure, dangerPressure, minWorkingPressure, p2, x, y, 90);
    }

    public abstract ResourceDrawable getGuiTexture();

    @Nonnull
    @Override
    public IDrawable getBackground() {
        return background;
    }

    void drawProgressBar(int x, int y, int u, int v, int width, int height, IDrawableAnimated.StartDirection startDirection) {
        IDrawableStatic drawable = guiHelper.createDrawable(background.getResource(), u, v, width, height);
        IDrawableAnimated animation = guiHelper.createAnimatedDrawable(drawable, 60, startDirection, false);
        animation.draw(Minecraft.getMinecraft(), x, y);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, IRecipeWrapper recipeWrapper, IIngredients ingredients) {
        if (recipeWrapper instanceof PneumaticCraftCategory.MultipleInputOutputRecipeWrapper) {
            MultipleInputOutputRecipeWrapper recipe = (MultipleInputOutputRecipeWrapper) recipeWrapper;

            recipeLayout.getItemStacks().addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
                String key = slotIndex < recipe.input.size() ?
                        recipe.input.get(slotIndex).getTooltipKey() :
                        recipe.output.get(slotIndex - recipe.input.size()).getTooltipKey();
                if (key != null) {
                    tooltip.addAll(PneumaticCraftUtils.convertStringIntoList(I18n.format(key)));
                }
            });

            for (int i = 0; i < ingredients.getInputs(VanillaTypes.ITEM).size(); i++) {
                recipeLayout.getItemStacks().init(i, true, recipe.input.get(i).getX() - 1, recipe.input.get(i).getY() - 1);
                recipeLayout.getItemStacks().set(i, recipe.input.get(i).getStacks());
            }

            for (int i = 0; i < ingredients.getOutputs(VanillaTypes.ITEM).size(); i++) {
                recipeLayout.getItemStacks().init(i + recipe.input.size(), false, recipe.output.get(i).getX() - 1, recipe.output.get(i).getY() - 1);
                recipeLayout.getItemStacks().set(i + recipe.input.size(), recipe.output.get(i).getStacks());
            }

            for (int i = 0; i < ingredients.getInputs(VanillaTypes.FLUID).size(); i++) {
                WidgetTank tank = recipe.inputLiquids.get(i);
                IDrawable tankOverlay = new DrawableResource(Textures.WIDGET_TANK, 0, 0, tank.getBounds().width, tank.getBounds().height, 0, 0, 0, 0, tank.getBounds().width, tank.getBounds().height);
                recipeLayout.getFluidStacks().init(i, true, tank.x, tank.y, tank.getBounds().width, tank.getBounds().height, tank.getTank().getCapacity(), false, tankOverlay);
                recipeLayout.getFluidStacks().set(i, tank.getFluid());
            }

            for (int i = 0; i < ingredients.getOutputs(VanillaTypes.FLUID).size(); i++) {
                WidgetTank tank = recipe.outputLiquids.get(i);
                IDrawable tankOverlay = new DrawableResource(Textures.WIDGET_TANK, 0, 0, tank.getBounds().width, tank.getBounds().height, 0, 0, 0, 0, tank.getBounds().width, tank.getBounds().height);
                recipeLayout.getFluidStacks().init(recipe.inputLiquids.size() + i, false, tank.x, tank.y, tank.getBounds().width, tank.getBounds().height, tank.getTank().getCapacity(), false, tankOverlay);
                recipeLayout.getFluidStacks().set(recipe.inputLiquids.size() + i, tank.getFluid());
            }

        }
    }
}