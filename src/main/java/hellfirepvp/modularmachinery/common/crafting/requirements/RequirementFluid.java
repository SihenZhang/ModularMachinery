/*******************************************************************************
 * HellFirePvP / Modular Machinery 2018
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package hellfirepvp.modularmachinery.common.crafting.requirements;

import hellfirepvp.modularmachinery.ModularMachinery;
import hellfirepvp.modularmachinery.common.crafting.ComponentType;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentOutputRestrictor;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentRequirement;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.crafting.requirements.jei.JEIComponentHybridFluid;
import hellfirepvp.modularmachinery.common.integration.ingredient.HybridFluid;
import hellfirepvp.modularmachinery.common.integration.ingredient.HybridFluidGas;
import hellfirepvp.modularmachinery.common.integration.recipe.RecipeLayoutPart;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.CopyHandlerHelper;
import hellfirepvp.modularmachinery.common.util.HybridGasTank;
import hellfirepvp.modularmachinery.common.util.HybridTank;
import hellfirepvp.modularmachinery.common.util.ResultChance;
import hellfirepvp.modularmachinery.common.util.nbt.NBTMatchingHelper;
import mekanism.api.gas.GasStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is part of the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: RequirementFluid
 * Created by HellFirePvP
 * Date: 24.02.2018 / 12:28
 */
public class RequirementFluid extends ComponentRequirement<HybridFluid> implements ComponentRequirement.ChancedRequirement {

    public final HybridFluid required;
    public float chance = 1F;

    private HybridFluid requirementCheck;
    private boolean doesntConsumeInput;

    private NBTTagCompound tagMatch = null, tagDisplay = null;

    public RequirementFluid(ComponentType<?> type, MachineComponent.IOType ioType, FluidStack fluid) {
        super(type, ioType);
        this.required = new HybridFluid(fluid);
        this.requirementCheck = this.required.copy();
    }

    private RequirementFluid(ComponentType<?> type, MachineComponent.IOType ioType, HybridFluid required) {
        super(type, ioType);
        this.required = required.copy();
        this.requirementCheck = this.required.copy();
    }

    @Optional.Method(modid = "mekanism")
    public static RequirementFluid createMekanismGasRequirement(ComponentType<?> type, MachineComponent.IOType ioType, GasStack gasStack) {
        return new RequirementFluid(type, ioType, new HybridFluidGas(gasStack));
    }

    @Override
    public ComponentRequirement deepCopy() {
        RequirementFluid fluid = new RequirementFluid(this.getRequiredComponentType(), this.getActionType(), this.required);
        fluid.chance = this.chance;
        fluid.tagMatch = this.tagMatch;
        fluid.tagDisplay = this.tagDisplay;
        return fluid;
    }

    @Override
    public JEIComponent<HybridFluid> provideJEIComponent() {
        return new JEIComponentHybridFluid(this);
    }

    public void setMatchNBTTag(@Nullable NBTTagCompound tag) {
        this.tagMatch = tag;
    }

    @Nullable
    public NBTTagCompound getTagMatch() {
        if(tagMatch == null) {
            return null;
        }
        return tagMatch.copy();
    }

    public void setDisplayNBTTag(@Nullable NBTTagCompound tag) {
        this.tagDisplay = tag;
    }

    @Nullable
    public NBTTagCompound getTagDisplay() {
        if(tagDisplay == null) {
            return null;
        }
        return tagDisplay.copy();
    }

    @Override
    public void setChance(float chance) {
        this.chance = chance;
    }

    @Override
    public void startRequirementCheck(ResultChance contextChance, RecipeCraftingContext context) {
        this.requirementCheck = this.required.copy();
        this.requirementCheck.setAmount(Math.round(context.applyModifiers(this, getActionType(), this.requirementCheck.getAmount(), false)));
        this.doesntConsumeInput = contextChance.canProduce(context.applyModifiers(this, getActionType(), this.chance, true));
    }

    @Override
    public void endRequirementCheck() {
        this.requirementCheck = this.required.copy();
        this.doesntConsumeInput = true;
    }

    @Override
    public CraftCheck canStartCrafting(MachineComponent component, RecipeCraftingContext context, List<ComponentOutputRestrictor> restrictions) {
        if(!component.getComponentType().equals(this.getRequiredComponentType()) ||
                !(component instanceof MachineComponent.FluidHatch) ||
                component.getIOType() != getActionType()) return CraftCheck.INVALID_SKIP;
        HybridTank handler = (HybridTank) context.getProvidedCraftingComponent(component);

        if(ModularMachinery.isMekanismLoaded) {
            return checkStartCraftingWithMekanism(component, context, handler, restrictions);
        }

        switch (getActionType()) {
            case INPUT:
                //If it doesn't consume the item, we only need to see if it's actually there.
                FluidStack drained = handler.drainInternal(this.requirementCheck.copy().asFluidStack(), false);
                if(drained == null) {
                    return CraftCheck.FAILURE_MISSING_INPUT;
                }
                if(!NBTMatchingHelper.matchNBTCompound(this.tagMatch, drained.tag)) {
                    return CraftCheck.FAILURE_MISSING_INPUT;
                }
                this.requirementCheck.setAmount(Math.max(this.requirementCheck.getAmount() - drained.amount, 0));
                if(this.requirementCheck.getAmount() <= 0) {
                    return CraftCheck.SUCCESS;
                }
                break;
            case OUTPUT:
                handler = CopyHandlerHelper.copyTank(handler);

                for (ComponentOutputRestrictor restrictor : restrictions) {
                    if(restrictor instanceof ComponentOutputRestrictor.RestrictionTank) {
                        ComponentOutputRestrictor.RestrictionTank tank = (ComponentOutputRestrictor.RestrictionTank) restrictor;

                        if(tank.exactComponent.equals(component)) {
                            handler.fillInternal(tank.inserted == null ? null : tank.inserted.copy().asFluidStack(), true);
                        }
                    }
                }
                int filled = handler.fillInternal(this.requirementCheck.copy().asFluidStack(), false); //True or false doesn't really matter tbh
                boolean didFill = filled >= this.requirementCheck.getAmount();
                if(didFill) {
                    context.addRestriction(new ComponentOutputRestrictor.RestrictionTank(this.requirementCheck.copy(), component));
                }
                if(didFill) {
                    return CraftCheck.SUCCESS;
                }
                break;
        }
        return CraftCheck.FAILURE_MISSING_INPUT;
    }

    @Optional.Method(modid = "mekanism")
    private CraftCheck checkStartCraftingWithMekanism(MachineComponent component, RecipeCraftingContext context,
                                                      HybridTank handler, List<ComponentOutputRestrictor> restrictions) {
        if(handler instanceof HybridGasTank) {
            HybridGasTank gasTank = (HybridGasTank) handler;
            switch (getActionType()) {
                case INPUT:
                    if(this.requirementCheck instanceof HybridFluidGas) {
                        GasStack drained = gasTank.drawGas(EnumFacing.UP, this.requirementCheck.getAmount(), false);
                        if(drained == null) {
                            return CraftCheck.FAILURE_MISSING_INPUT;
                        }
                        if(drained.getGas() != ((HybridFluidGas) this.requirementCheck).asGasStack().getGas()) {
                            return CraftCheck.FAILURE_MISSING_INPUT;
                        }
                        this.requirementCheck.setAmount(Math.max(this.requirementCheck.getAmount() - drained.amount, 0));
                        if(this.requirementCheck.getAmount() <= 0) {
                            return CraftCheck.SUCCESS;
                        }
                    }
                    break;
                case OUTPUT:
                    if(this.requirementCheck instanceof HybridFluidGas) {
                        gasTank = (HybridGasTank) CopyHandlerHelper.copyTank(gasTank);

                        for (ComponentOutputRestrictor restrictor : restrictions) {
                            if(restrictor instanceof ComponentOutputRestrictor.RestrictionTank) {
                                ComponentOutputRestrictor.RestrictionTank tank = (ComponentOutputRestrictor.RestrictionTank) restrictor;

                                if(tank.exactComponent.equals(component) && tank.inserted instanceof HybridFluidGas) {
                                    gasTank.receiveGas(EnumFacing.UP, ((HybridFluidGas) this.requirementCheck).asGasStack(), true);
                                }
                            }
                        }
                        int gasFilled = gasTank.receiveGas(EnumFacing.UP, ((HybridFluidGas) this.requirementCheck).asGasStack(), false);
                        boolean didFill = gasFilled >= this.requirementCheck.getAmount();
                        if(didFill) {
                            context.addRestriction(new ComponentOutputRestrictor.RestrictionTank(this.requirementCheck.copy(), component));
                        }
                        if(didFill) {
                            return CraftCheck.SUCCESS;
                        }
                    }
            }
        }
        switch (getActionType()) {
            case INPUT:
                //If it doesn't consume the item, we only need to see if it's actually there.
                FluidStack drained = handler.drainInternal(this.requirementCheck.copy().asFluidStack(), false);
                if(drained == null) {
                    return CraftCheck.FAILURE_MISSING_INPUT;
                }
                if(!NBTMatchingHelper.matchNBTCompound(this.tagMatch, drained.tag)) {
                    return CraftCheck.FAILURE_MISSING_INPUT;
                }
                this.requirementCheck.setAmount(Math.max(this.requirementCheck.getAmount() - drained.amount, 0));
                if(this.requirementCheck.getAmount() <= 0) {
                    return CraftCheck.SUCCESS;
                }
                break;
            case OUTPUT:
                handler = CopyHandlerHelper.copyTank(handler);

                for (ComponentOutputRestrictor restrictor : restrictions) {
                    if(restrictor instanceof ComponentOutputRestrictor.RestrictionTank) {
                        ComponentOutputRestrictor.RestrictionTank tank = (ComponentOutputRestrictor.RestrictionTank) restrictor;

                        if(tank.exactComponent.equals(component) && !(tank.inserted instanceof HybridFluidGas)) {
                            handler.fillInternal(tank.inserted == null ? null : tank.inserted.copy().asFluidStack(), true);
                        }
                    }
                }
                int filled = handler.fillInternal(this.requirementCheck.copy().asFluidStack(), false); //True or false doesn't really matter tbh
                boolean didFill = filled >= this.requirementCheck.getAmount();
                if(didFill) {
                    context.addRestriction(new ComponentOutputRestrictor.RestrictionTank(this.requirementCheck.copy(), component));
                }
                if(didFill) {
                    return CraftCheck.SUCCESS;
                }
        }
        return CraftCheck.FAILURE_MISSING_INPUT;
    }

    @Override
    public boolean startCrafting(MachineComponent component, RecipeCraftingContext context, ResultChance chance) {
        if(!component.getComponentType().equals(this.getRequiredComponentType()) ||
                !(component instanceof MachineComponent.FluidHatch) ||
                component.getIOType() != getActionType()) return false;
        HybridTank handler = (HybridTank) context.getProvidedCraftingComponent(component);
        switch (getActionType()) {
            case INPUT:
                if(ModularMachinery.isMekanismLoaded) {
                    return startCraftingWithMekanismHandling(handler, chance);
                }

                //If it doesn't consume the item, we only need to see if it's actually there.
                FluidStack drainedSimulated = handler.drainInternal(this.requirementCheck.copy().asFluidStack(), false);
                if(drainedSimulated == null) {
                    return false;
                }
                if(!NBTMatchingHelper.matchNBTCompound(this.tagMatch, drainedSimulated.tag)) {
                    return false;
                }
                if(this.doesntConsumeInput) {
                    this.requirementCheck.setAmount(Math.max(this.requirementCheck.getAmount() - drainedSimulated.amount, 0));
                    return this.requirementCheck.getAmount() <= 0;
                }
                FluidStack actualDrained = handler.drainInternal(this.requirementCheck.copy().asFluidStack(), true);
                if(actualDrained == null) {
                    return false;
                }
                if(!NBTMatchingHelper.matchNBTCompound(this.tagMatch, actualDrained.tag)) {
                    return false;
                }
                this.requirementCheck.setAmount(Math.max(this.requirementCheck.getAmount() - actualDrained.amount, 0));
                return this.requirementCheck.getAmount() <= 0;
        }
        return false;
    }

    @Optional.Method(modid = "mekanism")
    private boolean startCraftingWithMekanismHandling(HybridTank handler, ResultChance chance) {
        if(this.requirementCheck instanceof HybridFluidGas && handler instanceof HybridGasTank) {
            HybridGasTank gasHandler = (HybridGasTank) handler;

            GasStack drainSimulated = gasHandler.drawGas(EnumFacing.UP, this.requirementCheck.getAmount(), false);
            if(drainSimulated == null) {
                return false;
            }
            if(drainSimulated.getGas() != ((HybridFluidGas) this.requirementCheck).asGasStack().getGas()) {
                return false;
            }
            if(this.doesntConsumeInput) {
                this.requirementCheck.setAmount(Math.max(this.requirementCheck.getAmount() - drainSimulated.amount, 0));
                return this.requirementCheck.getAmount() <= 0;
            }
            GasStack actualDrain = gasHandler.drawGas(EnumFacing.UP, this.requirementCheck.getAmount(), true);
            if(actualDrain == null) {
                return false;
            }
            if(actualDrain.getGas() != ((HybridFluidGas) this.requirementCheck).asGasStack().getGas()) {
                return false;
            }
            this.requirementCheck.setAmount(Math.max(this.requirementCheck.getAmount() - actualDrain.amount, 0));
            return this.requirementCheck.getAmount() <= 0;
        } else {
            FluidStack drainedSimulated = handler.drainInternal(this.requirementCheck.copy().asFluidStack(), false);
            if(drainedSimulated == null) {
                return false;
            }
            if(!NBTMatchingHelper.matchNBTCompound(this.tagMatch, drainedSimulated.tag)) {
                return false;
            }
            if(this.doesntConsumeInput) {
                this.requirementCheck.setAmount(Math.max(this.requirementCheck.getAmount() - drainedSimulated.amount, 0));
                return this.requirementCheck.getAmount() <= 0;
            }
            FluidStack actualDrained = handler.drainInternal(this.requirementCheck.copy().asFluidStack(), true);
            if(actualDrained == null) {
                return false;
            }
            if(!NBTMatchingHelper.matchNBTCompound(this.tagMatch, actualDrained.tag)) {
                return false;
            }
            this.requirementCheck.setAmount(Math.max(this.requirementCheck.getAmount() - actualDrained.amount, 0));
            return this.requirementCheck.getAmount() <= 0;
        }
    }

    @Override
    public boolean finishCrafting(MachineComponent component, RecipeCraftingContext context, ResultChance chance) {
        if(!component.getComponentType().equals(this.getRequiredComponentType()) ||
                !(component instanceof MachineComponent.FluidHatch) ||
                component.getIOType() != getActionType()) return false;
        HybridTank handler = (HybridTank) context.getProvidedCraftingComponent(component);
        switch (getActionType()) {
            case OUTPUT:
                if(ModularMachinery.isMekanismLoaded) {
                    return finishWithMekanismHandling(handler, chance);
                } else {
                    FluidStack outStack = this.requirementCheck.asFluidStack();
                    if(outStack != null) {
                        int fillableAmount = handler.fillInternal(outStack.copy(), false);
                        if(chance.canProduce(this.chance)) {
                            return fillableAmount >= outStack.amount;
                        }
                        FluidStack copyOut = outStack.copy();
                        if(this.tagDisplay != null ){
                            copyOut.tag = this.tagDisplay.copy();
                        }
                        return fillableAmount >= outStack.amount && handler.fillInternal(copyOut.copy(), true) >= copyOut.amount;
                    }
                }
        }
        return false;
    }

    @Optional.Method(modid = "mekanism")
    private boolean finishWithMekanismHandling(HybridTank handler, ResultChance chance) {
        if(this.requirementCheck instanceof HybridFluidGas && handler instanceof HybridGasTank) {
            GasStack gasOut = ((HybridFluidGas) this.requirementCheck).asGasStack();
            HybridGasTank gasTankHandler = (HybridGasTank) handler;
            int fillableGas = gasTankHandler.receiveGas(EnumFacing.UP, gasOut, false);
            if(chance.canProduce(this.chance)) {
                return fillableGas >= gasOut.amount;
            }
            return fillableGas >= gasOut.amount && gasTankHandler.receiveGas(EnumFacing.UP, gasOut, true) >= gasOut.amount;
        } else {
            FluidStack outStack = this.requirementCheck.asFluidStack();
            if(outStack != null) {
                int fillableAmount = handler.fillInternal(outStack.copy(), false);
                if(chance.canProduce(this.chance)) {
                    return fillableAmount >= outStack.amount;
                }
                FluidStack copyOut = outStack.copy();
                if(this.tagDisplay != null ){
                    copyOut.tag = this.tagDisplay.copy();
                }
                return fillableAmount >= outStack.amount && handler.fillInternal(copyOut.copy(), true) >= copyOut.amount;
            }
        }
        return false;
    }

}
