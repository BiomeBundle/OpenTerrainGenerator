package com.pg85.otg.forge.presets;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.pg85.otg.config.biome.BiomeConfig;
import com.pg85.otg.config.biome.BiomeGroup;
import com.pg85.otg.config.world.WorldConfig;
import com.pg85.otg.constants.SettingsEnums.BiomeMode;
import com.pg85.otg.forge.biome.ForgeBiome;
import com.pg85.otg.presets.LocalPresetLoader;
import com.pg85.otg.presets.Preset;
import com.pg85.otg.util.biome.BiomeResourceLocation;
import com.pg85.otg.gen.biome.layers.BiomeLayerData;
import com.pg85.otg.gen.biome.layers.NewBiomeGroup;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;

import com.pg85.otg.gen.biome.NewBiomeData;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraftforge.registries.ForgeRegistries;

public class ForgePresetLoader extends LocalPresetLoader
{
	private final HashMap<String, Int2ObjectMap<BiomeConfig>> globalIdMapping = new HashMap<>();
	// Using a ref is much faster than using an object
	private final HashMap<String, Reference2IntMap<BiomeConfig>> reverseIdMapping = new HashMap<>();
	private final Map<ResourceLocation, BiomeConfig> biomeConfigsByRegistryKey = new HashMap<>();
	private final Map<String, List<RegistryKey<Biome>>> biomesByPresetName = new LinkedHashMap<>();
	private final Map<String, BiomeLayerData> presetGenerationData = new HashMap<>();
	
	public ForgePresetLoader(Path otgRootFolder)
	{
		super(otgRootFolder);
	}

	@Override
	public BiomeConfig getBiomeConfig(String resourceLocationString)
	{
		return this.biomeConfigsByRegistryKey.get(new ResourceLocation(resourceLocationString));
	}
	
	public List<RegistryKey<Biome>> getBiomeRegistryKeys(String presetName)
	{
		return this.biomesByPresetName.get(presetName);
	}

	public Int2ObjectMap<BiomeConfig> getGlobalIdMapping(String presetName)
	{
		return globalIdMapping.get(presetName);
	}

	public Map<String, BiomeLayerData> getPresetGenerationData()
	{
		Map<String, BiomeLayerData> clonedData = new HashMap<>();
		for(Entry<String, BiomeLayerData> entry : this.presetGenerationData.entrySet())
		{
			clonedData.put(entry.getKey(), new BiomeLayerData(entry.getValue()));
		}
		return clonedData;
	}

	@Override
	public void registerBiomes()
	{
		for(Preset preset : this.presets.values())
		{
			// Start at 1, 0 is the fallback for the biome generator (the world's ocean biome).
			int currentId = 1;
			
			List<RegistryKey<Biome>> presetBiomes = new ArrayList<>();
			this.biomesByPresetName.put(preset.getName(), presetBiomes);
			WorldConfig worldConfig = preset.getWorldConfig();
			BiomeConfig oceanBiomeConfig = null;
			
			Int2ObjectMap<BiomeConfig> presetIdMapping = new Int2ObjectLinkedOpenHashMap<>();
			Reference2IntMap<BiomeConfig> presetReverseIdMapping = new Reference2IntLinkedOpenHashMap<>();
			
			Map<Integer, List<NewBiomeData>> isleBiomesAtDepth = new HashMap<>();
			Map<Integer, List<NewBiomeData>> borderBiomesAtDepth = new HashMap<>();
			
			Map<String, Integer> worldBiomes = new HashMap<>();
			
			for(BiomeConfig biomeConfig : preset.getAllBiomeConfigs())
			{
				// DeferredRegister for Biomes doesn't appear to be working atm, biomes are never registered :(
				//RegistryObject<Biome> registryObject = OTGPlugin.BIOMES.register(biomeConfig.getRegistryKey().getResourcePath(), () -> createOTGBiome(biomeConfig));
				
				Biome biome = ForgeBiome.createOTGBiome(preset.getWorldConfig(), biomeConfig);
 				ForgeRegistries.BIOMES.register(biome);
 				
 				// Store registry key (resourcelocation) so we can look up biomeconfigs via RegistryKey<Biome> later.
 				ResourceLocation resourceLocation = new ResourceLocation(biomeConfig.getRegistryKey().toResourceLocationString());
				System.out.println(resourceLocation);
 				this.biomeConfigsByRegistryKey.put(resourceLocation, biomeConfig);
 				
 				presetBiomes.add(RegistryKey.func_240903_a_(Registry.field_239720_u_, resourceLocation));
 				
 				presetIdMapping.put(currentId, biomeConfig);
 				presetReverseIdMapping.put(biomeConfig, currentId);

 				// Biome id 0 is reserved for ocean, used when a land column has 
 				// no biome assigned, which can happen due to biome group rarity.
 				if(biomeConfig.getName().equals(preset.getWorldConfig().getDefaultOceanBiome()))
 				{
 					// TODO: Can't map the same biome to 2 int keys for the reverse map
 					// make sure this doesn't cause problems :/.
 					oceanBiomeConfig = biomeConfig;
 					presetIdMapping.put(0, biomeConfig);
 				}

 				worldBiomes.put(biomeConfig.getName(), currentId);
 				
 				// Make a list of isle and border biomes per generation depth
 				if(biomeConfig.isIsleBiome())
 				{
					// Make or get a list for this group depth, then add
					List<NewBiomeData> biomesAtDepth = isleBiomesAtDepth.getOrDefault(biomeConfig.getBiomeSize(), new ArrayList<>());
					biomesAtDepth.add(
						new NewBiomeData(
							currentId, 
							biomeConfig.getName(), 
							worldConfig.getBiomeMode() == BiomeMode.BeforeGroups ? biomeConfig.getBiomeRarity() : biomeConfig.getBiomeRarityWhenIsle(),
							worldConfig.getBiomeMode() == BiomeMode.BeforeGroups ? biomeConfig.getBiomeSize() : biomeConfig.getBiomeSizeWhenIsle(), 
							biomeConfig.getBiomeTemperature(), 
							biomeConfig.getIsleInBiomes(), 
							biomeConfig.getBorderInBiomes(), 
							biomeConfig.getNotBorderNearBiomes()
						)
					);
					isleBiomesAtDepth.put(worldConfig.getBiomeMode() == BiomeMode.BeforeGroups ? biomeConfig.getBiomeSize() : biomeConfig.getBiomeSizeWhenIsle(), biomesAtDepth);
 				}

 				if(biomeConfig.isBorderBiome())
 				{
					// Make or get a list for this group depth, then add
					List<NewBiomeData> biomesAtDepth = borderBiomesAtDepth.getOrDefault(biomeConfig.getBiomeSize(), new ArrayList<>());
					biomesAtDepth.add(
						new NewBiomeData(
							currentId, 
							biomeConfig.getName(), 
							biomeConfig.getBiomeRarity(),
							worldConfig.getBiomeMode() == BiomeMode.BeforeGroups ? biomeConfig.getBiomeSize() : biomeConfig.getBiomeSizeWhenBorder(), 
							biomeConfig.getBiomeTemperature(), 
							biomeConfig.getIsleInBiomes(), 
							biomeConfig.getBorderInBiomes(), 
							biomeConfig.getNotBorderNearBiomes()
						)
					);
					borderBiomesAtDepth.put(worldConfig.getBiomeMode() == BiomeMode.BeforeGroups ? biomeConfig.getBiomeSize() : biomeConfig.getBiomeSizeWhenBorder(), biomesAtDepth);
 				}

 				currentId++;
			}
			
			this.globalIdMapping.put(preset.getName(), presetIdMapping);
			this.reverseIdMapping.put(preset.getName(), presetReverseIdMapping);

			// Set the base data
			BiomeLayerData data = new BiomeLayerData(worldConfig, oceanBiomeConfig);
			
			Set<Integer> biomeDepths = new HashSet<>();
			Map<Integer, List<NewBiomeGroup>> groupDepths = new HashMap<>();

			// Iterate through the groups and add it to the layer data
			for (BiomeGroup group : worldConfig.biomeGroupManager.getGroups())
			{
				// Initialize biome group data
				NewBiomeGroup bg = new NewBiomeGroup();
				bg.id = group.getGroupId();
				bg.rarity = group.getGroupRarity();

		        float totalTemp = 0;
				
				// Add each biome to the group
				for (String biome : group.biomes.keySet())
				{
					ResourceLocation location = new ResourceLocation(new BiomeResourceLocation(preset.getName(), biome).toResourceLocationString());
					BiomeConfig config = this.biomeConfigsByRegistryKey.get(location);

					// Make and add the generation data
					NewBiomeData newBiomeData = new NewBiomeData(presetReverseIdMapping.getInt(config), config.getName(), config.getBiomeRarity(), config.getBiomeSize(), config.getBiomeTemperature(), config.getIsleInBiomes(), config.getBorderInBiomes(), config.getNotBorderNearBiomes());
					bg.biomes.add(newBiomeData);

					// Add the biome size- if it's already there, nothing is done
					biomeDepths.add(config.getBiomeSize());
					
		            totalTemp += config.getBiomeTemperature();
		            bg.totalGroupRarity += config.getBiomeRarity();
				}
				bg.avgTemp = totalTemp / group.biomes.size();

				int groupSize = group.getGenerationDepth();

				// Make or get a list for this group depth, then add
				List<NewBiomeGroup> groupsAtDepth = groupDepths.getOrDefault(groupSize, new ArrayList<>());
				groupsAtDepth.add(bg);

				// Replace entry
				groupDepths.put(groupSize, groupsAtDepth);

				// Register group id
				data.groupRegistry.put(bg.id, bg);
			}

			// Add the data and process isle/border biomes
			data.init(biomeDepths, groupDepths, isleBiomesAtDepth, borderBiomesAtDepth, worldBiomes);
			
			// Set data for this preset
			this.presetGenerationData.put(preset.getName(), data);
		}
	}
}