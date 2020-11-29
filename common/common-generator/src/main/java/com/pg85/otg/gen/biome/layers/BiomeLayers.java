package com.pg85.otg.gen.biome.layers;

import java.util.List;
import java.util.function.LongFunction;

import com.pg85.otg.constants.SettingsEnums.BiomeMode;
import com.pg85.otg.constants.SettingsEnums.ImageMode;
import com.pg85.otg.gen.biome.NewBiomeData;
import com.pg85.otg.gen.biome.layers.util.CachingLayerContext;
import com.pg85.otg.gen.biome.layers.util.CachingLayerSampler;
import com.pg85.otg.gen.biome.layers.util.LayerFactory;
import com.pg85.otg.gen.biome.layers.util.LayerSampleContext;
import com.pg85.otg.gen.biome.layers.util.LayerSampler;
import com.pg85.otg.logging.ILogger;

/**
 * Holds the factory and utils needed for OTG's biome layers to work.
 */
public class BiomeLayers
{
	// Bit masks for biome generation

	// The land bit marks whether a sample is land or not. This is used to place biomes.
	public static final int LAND_BIT = (1 << 29);
	public static final int ISLAND_BIT = (1 << 30); // TODO: Do we really need this?
	public static final int ICE_BIT = (1 << 31);

	public static final int GROUP_SHIFT = 22;

	// The marker for biome groups
	static final int GROUP_BITS = (127 << GROUP_SHIFT);

	// This is the amount of bits we & the sample at the end to get the correct biome id.
	public static final int BIOME_BITS = (1 << GROUP_SHIFT) - 1;

	static boolean isLand(int sample) {
		return (sample & LAND_BIT) != 0;
	}

	static int getGroupId(int sample)
	{
		return (sample & GROUP_BITS) >> GROUP_SHIFT;
	}

	private static <T extends LayerSampler, C extends LayerSampleContext<T>> LayerFactory<T> build(BiomeLayerData data, LongFunction<C> contextProvider, ILogger logger)
	{
		// Create an empty layer to start the biome generation
		LayerFactory<T> factory = new InitializationLayer().create(contextProvider.apply(1L));;
		
		if(!(data.biomeMode == BiomeMode.FromImage && data.imageMode != ImageMode.ContinueNormal))
		{			
			// Iterate through the depth, manipulating the factory at specific points
			for (int depth = 0; depth < data.generationDepth; depth++)
			{
				// Scale the factory by 2x before adding more transformations
				factory = new ScaleLayer().create(contextProvider.apply(2000L + depth), factory);
				// TODO: probably should add smooth layer here
	
				// If we're at the land size, initialize the land layer with the provided rarity.
				if (depth == data.landSize) {
					factory = new LandLayer(data.landRarity).create(contextProvider.apply(1L), factory);
					factory = new FuzzyScaleLayer().create(contextProvider.apply(2000L), factory);
				}
	
				// If the depth is between landSize and landFuzzy, add islands to fuzz the ocean/land border.
				if (depth < (data.landSize + data.landFuzzy))
				{
					factory = new AddIslandsLayer().create(contextProvider.apply(depth), factory);
				}
	
				if(data.biomeMode == BiomeMode.Normal)
				{
					if (data.groups.containsKey(depth))
					{
						factory = new BiomeGroupLayer(data.groups.get(depth), data.freezeGroups).create(contextProvider.apply(depth), factory);
					}
		
					if (data.biomeDepths.contains(depth))
					{
						factory = new BiomeLayer(data, depth).create(contextProvider.apply(depth), factory);
					}
				}
				else if(data.biomeMode == BiomeMode.BeforeGroups)
				{
					if (data.biomeDepths.contains(depth))
					{
						factory = new BeforeGroupsLayer(data, depth).create(contextProvider.apply(depth), factory);
					}
				}
	
	            if (depth == 3) // TODO: Why 3?
	            {
	            	factory = new IceLayer(data).create(contextProvider.apply(depth), factory);
	            }
	            
	            List<NewBiomeData> isleBiomes = data.isleBiomesAtDepth.get(depth);
	            if(isleBiomes != null && isleBiomes.size() > 0)
	            {
	                BiomeIsleLayer.IslesList islesAtCurrentDepth = new BiomeIsleLayer.IslesList();
		            for (NewBiomeData biome : isleBiomes)
		            {
		                boolean[] biomeCanSpawnIn = new boolean[1024];
		                boolean inOcean = false;
		                for (int islandInBiome : biome.isleInBiomes)
		                {
		                    if (islandInBiome == data.oceanBiomeData.id)
		                    {
		                    	inOcean = true;
		                    } else {
		                    	biomeCanSpawnIn[islandInBiome] = true;
		                    }
		                }
		                int chance = (data.biomeRarityScale + 1) - biome.rarity;
		                islesAtCurrentDepth.addIsle(biome.id, chance, biomeCanSpawnIn, inOcean);
		            }	
	                factory = new BiomeIsleLayer(islesAtCurrentDepth).create(contextProvider.apply(depth), factory);               
	            }
	            
	            List<NewBiomeData> borderBiomes = data.borderBiomesAtDepth.get(depth);
	            if(borderBiomes != null && borderBiomes.size() > 0)
	            {
	            	BiomeBorderLayer.BordersList bordersAtCurrentDepth = new BiomeBorderLayer.BordersList();
		            for (NewBiomeData biome : borderBiomes)
		            {
		                for(int targetBiomeId : biome.borderInBiomes)
		                {
			                bordersAtCurrentDepth.addBorder(biome.id, targetBiomeId, biome.notBorderNearBiomes);	                	
		                }
		            }
	                factory = new BiomeBorderLayer(bordersAtCurrentDepth).create(contextProvider.apply(depth), factory);               
	            }            
			}

			// Add ocean biomes. This only adds the regular ocean at the moment, soon it will add others.
			factory = new ApplyOceanLayer(data).create(contextProvider.apply(3L), factory);
			
			// Finalize the biome data
			factory = new FinalizeLayer().create(contextProvider.apply(1L), factory);
		}
		
		if(data.biomeMode == BiomeMode.FromImage)
		{			
        	factory = new FromImageLayer(data, logger).create(contextProvider.apply(0), factory);
		}

		return factory;
	}

	// Create a sampler that can get a biome at a position
	public static CachingLayerSampler create(long seed, BiomeLayerData data, ILogger logger)
	{
		LayerFactory<CachingLayerSampler> factory = build(data, salt -> new CachingLayerContext(25, seed, salt), logger);
		return factory.make();
	}
}
