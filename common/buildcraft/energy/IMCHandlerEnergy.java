package buildcraft.energy;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.biome.BiomeGenBase;

import cpw.mods.fml.common.event.FMLInterModComms.IMCEvent;
import cpw.mods.fml.common.event.FMLInterModComms.IMCMessage;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import buildcraft.api.core.BCLog;
import buildcraft.api.fuels.ICoolant;
import buildcraft.core.IMCHandler;
import buildcraft.energy.fuels.CoolantManager;


public class IMCHandlerEnergy extends IMCHandler {
	@Override
	public void processIMCEvent(IMCEvent event, IMCMessage m) {
		 if (m.key.equals("add-coolant")) {
			processCoolantAddIMC(event, m);
		} else if (m.key.equals("remove-coolant")) {
			processCoolantRemoveIMC(event, m);
		}
	}



	public static void processCoolantAddIMC(IMCEvent event, IMCMessage m) {
		boolean failed = false;
		if (!m.isNBTMessage()) {
			failed = true;
		} else {
			NBTTagCompound tag = m.getNBTValue();
			if (!tag.hasKey("coolant") || !tag.hasKey("degrees", 3)) {
				failed = true;
			} else {
				Fluid coolant = FluidRegistry.getFluid(tag.getString("coolant"));
				if (coolant != null) {
					CoolantManager.INSTANCE.addCoolant(coolant, tag.getInteger("degrees"));
				} else {
					failed = true;
				}
			}
		}
		if (failed) {
			BCLog.logger.warn("Received invalid coolant IMC message from mod %s!", m.getSender());
		}
	}

	public static void processCoolantRemoveIMC(IMCEvent event, IMCMessage m) {
		boolean failed = false;
		if (m.isStringMessage()) {
			ICoolant coolant = CoolantManager.INSTANCE.getCoolant(FluidRegistry.getFluid(m.getStringValue()));
			if (coolant != null) {
				CoolantManager.INSTANCE.getCoolants().remove(coolant);
			} else {
				failed = true;
			}
		} else {
			failed = true;
		}
		if (failed) {
			BCLog.logger.warn("Received invalid coolant IMC message from mod %s!", m.getSender());
		}
	}
}
