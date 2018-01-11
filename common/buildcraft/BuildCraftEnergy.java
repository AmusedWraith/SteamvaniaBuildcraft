/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft;

import java.util.Set;

import org.apache.logging.log4j.Level;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.minecraft.world.biome.BiomeGenBase;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.JavaTools;
import buildcraft.api.core.StackKey;
import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.StatementManager;
import buildcraft.core.BCRegistry;
import buildcraft.core.BlockSpring;
import buildcraft.core.DefaultProps;
import buildcraft.core.InterModComms;
import buildcraft.core.Version;
import buildcraft.core.config.ConfigManager;
import buildcraft.core.config.ConfigManager.RestartRequirement;
import buildcraft.core.lib.block.BlockBuildCraftFluid;
import buildcraft.core.lib.engines.TileEngineBase;
import buildcraft.core.lib.engines.TileEngineBase.EnergyStage;
import buildcraft.core.lib.network.ChannelHandler;
import buildcraft.core.lib.network.PacketHandler;
import buildcraft.energy.BucketHandler;
import buildcraft.energy.EnergyGuiHandler;
import buildcraft.energy.EnergyProxy;
import buildcraft.energy.IMCHandlerEnergy;
import buildcraft.energy.ItemBucketBuildcraft;
import buildcraft.energy.TileEngineCreative;
import buildcraft.energy.TileEngineIron;
import buildcraft.energy.TileEngineStone;
import buildcraft.energy.fuels.CoolantManager;
import buildcraft.energy.fuels.FuelManager;
import buildcraft.energy.statements.EnergyStatementProvider;
import buildcraft.energy.statements.TriggerCoolantBelowThreshold;
import buildcraft.energy.statements.TriggerEngineHeat;
import buildcraft.energy.statements.TriggerFuelBelowThreshold;


@Mod(name = "BuildCraft Energy", version = Version.VERSION, useMetadata = false, modid = "BuildCraft|Energy", dependencies = DefaultProps.DEPENDENCY_CORE)
public class BuildCraftEnergy extends BuildCraftMod {

	@Mod.Instance("BuildCraft|Energy")
	public static BuildCraftEnergy instance;


	public static Achievement engineAchievement2;
	public static Achievement engineAchievement3;

	public static ITriggerExternal triggerBlueEngineHeat = new TriggerEngineHeat(EnergyStage.BLUE);
	public static ITriggerExternal triggerGreenEngineHeat = new TriggerEngineHeat(EnergyStage.GREEN);
	public static ITriggerExternal triggerYellowEngineHeat = new TriggerEngineHeat(EnergyStage.YELLOW);
	public static ITriggerExternal triggerRedEngineHeat = new TriggerEngineHeat(EnergyStage.RED);
	public static ITriggerExternal triggerEngineOverheat = new TriggerEngineHeat(EnergyStage.OVERHEAT);

	public static ITriggerExternal triggerFuelBelow25 = new TriggerFuelBelowThreshold(0.25F);
	public static ITriggerExternal triggerFuelBelow50 = new TriggerFuelBelowThreshold(0.50F);

	public static ITriggerExternal triggerCoolantBelow25 = new TriggerCoolantBelowThreshold(0.25F);
	public static ITriggerExternal triggerCoolantBelow50 = new TriggerCoolantBelowThreshold(0.50F);

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent evt) {
		BuildcraftFuelRegistry.fuel = FuelManager.INSTANCE;
		BuildcraftFuelRegistry.coolant = CoolantManager.INSTANCE;

		BuildCraftCore.mainConfiguration.save();

		BiomeGenBase[] biomeGenArray = BiomeGenBase.getBiomeGenArray();



		reloadConfig(ConfigManager.RestartRequirement.GAME);

		MinecraftForge.EVENT_BUS.register(BucketHandler.INSTANCE);

		BuildcraftFuelRegistry.coolant.addCoolant(FluidRegistry.WATER, 0.0023f);
		BuildcraftFuelRegistry.coolant.addSolidCoolant(StackKey.stack(Blocks.ice), StackKey.fluid(FluidRegistry.WATER), 1.5f);
		BuildcraftFuelRegistry.coolant.addSolidCoolant(StackKey.stack(Blocks.packed_ice), StackKey.fluid(FluidRegistry.WATER), 2.0f);

		BuildCraftCore.engineBlock.registerTile(TileEngineStone.class, 1, "tile.engineStone", "buildcraftenergy:engineStone");
		BuildCraftCore.engineBlock.registerTile(TileEngineIron.class, 2, "tile.engineIron", "buildcraftenergy:engineIron");
		BuildCraftCore.engineBlock.registerTile(TileEngineCreative.class, 3, "tile.engineCreative", "buildcraftenergy:engineCreative");

		InterModComms.registerHandler(new IMCHandlerEnergy());

		FMLCommonHandler.instance().bus().register(this);
		MinecraftForge.EVENT_BUS.register(this);
	}

	public void reloadConfig(ConfigManager.RestartRequirement restartType) {
		if (restartType == ConfigManager.RestartRequirement.GAME) {
			reloadConfig(ConfigManager.RestartRequirement.WORLD);
		} else if (restartType == ConfigManager.RestartRequirement.WORLD) {
			reloadConfig(ConfigManager.RestartRequirement.NONE);
		} else {


			if (BuildCraftCore.mainConfiguration.hasChanged()) {
				BuildCraftCore.mainConfiguration.save();
			}
		}
	}

	@SubscribeEvent
	public void onConfigChanged(ConfigChangedEvent.PostConfigChangedEvent event) {
		if ("BuildCraft|Core".equals(event.modID)) {
			reloadConfig(event.isWorldRunning ? ConfigManager.RestartRequirement.NONE : ConfigManager.RestartRequirement.WORLD);
		}
	}

	private void setBiomeList(Set<Integer> list, Property configuration) {
		for (String id : configuration.getStringList()) {
			String strippedId = JavaTools.stripSurroundingQuotes(id.trim());

			if (strippedId.length() > 0) {
				if (strippedId.matches("-?\\d+(\\.\\d+)?")) {
					try {
						list.add(Integer.parseInt(strippedId));
					} catch (NumberFormatException ex) {
						BCLog.logger.log
								(Level.WARN,
										configuration.getName() + ": Could not find biome id: "
												+ strippedId + " ; Skipping!");
					}
				} else {
					boolean found = false;
					String biomeName = strippedId.toUpperCase();

					for (BiomeDictionary.Type t : BiomeDictionary.Type.values()) {
						String biomeType = t.name().toUpperCase();

						for (BiomeGenBase b : BiomeDictionary.getBiomesForType(t)) {
							if (b.biomeName.toUpperCase().equals(biomeName)
									|| biomeType.toUpperCase().equals(biomeName)) {
								list.add(b.biomeID);
								found = true;
							}
						}
					}


					if (!found) {
						BCLog.logger.log
								(Level.WARN,
										configuration.getName() + ": Could not find biome id: "
												+ strippedId + " ; Skipping!");
					}
				}
			}
		}
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent evt) {
		channels = NetworkRegistry.INSTANCE.newChannel
				(DefaultProps.NET_CHANNEL_NAME + "-ENERGY", new ChannelHandler(), new PacketHandler());

		NetworkRegistry.INSTANCE.registerGuiHandler(instance, new EnergyGuiHandler());

		StatementManager.registerTriggerProvider(new EnergyStatementProvider());

		if (BuildCraftCore.loadDefaultRecipes) {
			loadRecipes();
		}

		EnergyProxy.proxy.registerBlockRenderers();
		EnergyProxy.proxy.registerTileEntities();

		engineAchievement2 = BuildCraftCore.achievementManager.registerAchievement(new Achievement("achievement.stirlingEngine", "engineAchievement2", 3, -2, new ItemStack(BuildCraftCore.engineBlock, 1, 1), BuildCraftCore.engineRedstoneAchievement));
		engineAchievement3 = BuildCraftCore.achievementManager.registerAchievement(new Achievement("achievement.combustionEngine", "engineAchievement3", 5, -2, new ItemStack(BuildCraftCore.engineBlock, 1, 2), engineAchievement2));
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent evt) {
		if (BuildCraftCore.modifyWorld) {

		}
	}

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void textureHook(TextureStitchEvent.Post event) {
		if (event.map.getTextureType() == 0) {

		}
	}

	public static void loadRecipes() {
		BCRegistry.INSTANCE.addCraftingRecipe(new ItemStack(BuildCraftCore.engineBlock, 1, 1),
				"www", " g ", "GpG", 'w', "cobblestone",
				'g', "blockGlass", 'G', "gearStone", 'p', Blocks.piston);
		BCRegistry.INSTANCE.addCraftingRecipe(new ItemStack(BuildCraftCore.engineBlock, 1, 2),
				"www", " g ", "GpG", 'w', "ingotIron",
				'g', "blockGlass", 'G', "gearIron", 'p', Blocks.piston);
	}

	private int findUnusedBiomeID(String biomeName) {
		int freeBiomeID;
		// code to find a free biome
		for (int i = 1; i < 256; i++) {
			if (BiomeGenBase.getBiomeGenArray()[i] == null) {
				freeBiomeID = i;
				return freeBiomeID;
			}
		}
		// failed to find any free biome IDs
		class BiomeIdLimitException extends RuntimeException {
			private static final long serialVersionUID = 1L;

			public BiomeIdLimitException(String biome) {
				super(String.format("You have run out of free Biome ID spaces for %s - free more Biome IDs or disable the biome by setting the ID to 0!", biome));
			}
		}

		throw new BiomeIdLimitException(biomeName);
	}

	@Mod.EventHandler
	public void processIMCRequests(FMLInterModComms.IMCEvent event) {
		InterModComms.processIMC(event);
	}

	@Mod.EventHandler
	public void whiteListAppliedEnergetics(FMLInitializationEvent event) {
		FMLInterModComms.sendMessage("appliedenergistics2", "whitelist-spatial",
				TileEngineBase.class.getCanonicalName());
	}

	@Mod.EventHandler
	public void remap(FMLMissingMappingsEvent event) {
		for (FMLMissingMappingsEvent.MissingMapping mapping : event.get()) {
			if (mapping.name.equals("BuildCraft|Energy:engineBlock")) {
				if (mapping.type == GameRegistry.Type.BLOCK) {
					mapping.remap(BuildCraftCore.engineBlock);
				} else if (mapping.type == GameRegistry.Type.ITEM) {
					mapping.remap(Item.getItemFromBlock(BuildCraftCore.engineBlock));
				}
			}
		}
	}
}
