/*******************************************************************************
 * HellFirePvP / Modular Machinery 2018
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package hellfirepvp.modularmachinery.common.crafting.requirements;

import com.google.common.collect.Lists;
import hellfirepvp.modularmachinery.common.crafting.ComponentType;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentOutputRestrictor;
import hellfirepvp.modularmachinery.common.crafting.helper.ComponentRequirement;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.crafting.requirements.jei.JEIComponentEnergy;
import hellfirepvp.modularmachinery.common.integration.recipe.RecipeLayoutPart;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.IEnergyHandler;
import hellfirepvp.modularmachinery.common.util.ResultChance;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.List;

/**
 * This class is part of the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: RequirementEnergy
 * Created by HellFirePvP
 * Date: 24.02.2018 / 12:26
 */
public class RequirementEnergy extends ComponentRequirement.PerTick<Long> {

    public final int requirementPerTick;
    private int activeIO;

    public RequirementEnergy(MachineComponent.IOType ioType, int requirementPerTick) {
        super(ComponentType.Registry.getComponent("energy"), ioType);
        this.requirementPerTick = requirementPerTick;
        this.activeIO = this.requirementPerTick;
    }

    @Override
    public ComponentRequirement deepCopy() {
        RequirementEnergy energy = new RequirementEnergy(this.getActionType(), this.requirementPerTick);
        energy.activeIO = this.activeIO;
        return energy;
    }

    @Override
    public void startRequirementCheck(ResultChance contextChance, RecipeCraftingContext context) {}

    @Override
    public void endRequirementCheck() {}

    public int getRequiredEnergyPerTick() {
        return requirementPerTick;
    }

    @Override
    public JEIComponent<Long> provideJEIComponent() {
        return new JEIComponentEnergy(this);
    }

    @Override
    public CraftCheck canStartCrafting(MachineComponent component, RecipeCraftingContext context, List<ComponentOutputRestrictor> restrictions) {
        if(!component.getComponentType().equals(this.getRequiredComponentType()) ||
                !(component instanceof MachineComponent.EnergyHatch) ||
                component.getIOType() != getActionType()) return CraftCheck.INVALID_SKIP;
        IEnergyHandler handler = (IEnergyHandler) context.getProvidedCraftingComponent(component);
        switch (getActionType()) {
            case INPUT:
                if(handler.getCurrentEnergy() >= context.applyModifiers(this, getActionType(), this.requirementPerTick, false)) {
                    return CraftCheck.SUCCESS;
                }
                break;
            case OUTPUT:
                return CraftCheck.SUCCESS;
        }
        return CraftCheck.FAILURE_MISSING_INPUT;
    }

    @Override
    public boolean startCrafting(MachineComponent component, RecipeCraftingContext context, ResultChance chance) {
        return canStartCrafting(component, context, Lists.newArrayList()) == CraftCheck.SUCCESS;
    }

    @Override
    public boolean finishCrafting(MachineComponent component, RecipeCraftingContext context, ResultChance chance) {
        return true;
    }

    @Override
    public void startIOTick(RecipeCraftingContext context, float durationMultiplier) {
        this.activeIO = Math.round(context.applyModifiers(this, getActionType(), this.activeIO , false) * durationMultiplier);
    }

    @Override
    public void resetIOTick(RecipeCraftingContext context) {
        this.activeIO = this.requirementPerTick;
    }

    @Override
    @Nonnull
    public CraftCheck doIOTick(MachineComponent component, RecipeCraftingContext context) {
        if(!component.getComponentType().equals(this.getRequiredComponentType()) ||
                !(component instanceof MachineComponent.EnergyHatch) ||
                component.getIOType() != getActionType()) return CraftCheck.INVALID_SKIP;
        IEnergyHandler handler = (IEnergyHandler) context.getProvidedCraftingComponent(component);
        switch (getActionType()) {
            case INPUT:
                if(handler.getCurrentEnergy() >= this.activeIO) {
                    handler.setCurrentEnergy(handler.getCurrentEnergy() - this.activeIO);
                    this.activeIO = 0;
                    return CraftCheck.SUCCESS;
                } else {
                    this.activeIO -= handler.getCurrentEnergy();
                    handler.setCurrentEnergy(0);
                    return CraftCheck.PARTIAL_SUCCESS;
                }
            case OUTPUT:
                int remaining = handler.getRemainingCapacity();
                if(remaining - this.activeIO < 0) {
                    handler.setCurrentEnergy(handler.getMaxEnergy());
                    this.activeIO -= remaining;
                    return CraftCheck.PARTIAL_SUCCESS;
                }
                handler.setCurrentEnergy(Math.min(handler.getCurrentEnergy() + this.activeIO, handler.getMaxEnergy()));
                this.activeIO = 0;
                return CraftCheck.SUCCESS;
        }
        //This is neither input nor output? when do we actually end up in this case down here?
        return CraftCheck.INVALID_SKIP;
    }
}
