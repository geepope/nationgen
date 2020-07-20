package nationGen.naming;

import nationGen.NationGenAssets;
import nationGen.entities.Filter;
import nationGen.misc.ChanceIncHandler;
import nationGen.misc.Command;
import nationGen.nation.Nation;
import nationGen.units.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import com.elmokki.Generic;


public class NationDescriber {
	private Nation n;
	private Random random;
	private NationGenAssets assets;
	private ChanceIncHandler chandler;
	
	public LinkedHashMap<String,String> descDictionary = new LinkedHashMap<>(); 
	public Filter superlative = null;
	
	public NationDescriber(Nation n, NationGenAssets assets)
	{
		random = new Random(n.random.nextInt());
		this.n = n;
		this.assets = assets;
		this.chandler = new ChanceIncHandler(n);
		describeTroops();
		describeCommanders();
	}
	
	//if a filter has multiple possible description keys, this randomly selects one and logs it
	//previously logged synonyms are reused within the same nation for consistency
	public String getRandomDescription(Filter f)
	{
		if (!f.tags.containsName("uniquedescription") && this.descDictionary.containsKey(f.name))
			return " " + this.descDictionary.get(f.name);
		else if (f.thesaurus.size() > 0)
		{
			this.descDictionary.put(f.name, f.thesaurus.get(random.nextInt(f.thesaurus.size())));
			return " " + this.descDictionary.get(f.name);
		}
		else if (!f.tags.getString("description").isEmpty())
			return f.tags.getString("description").map(s -> " " + s).orElse(""); 
		else
			return "";
	}
	
	public StringBuilder expandPrevDesc(StringBuilder compiledDesc, Filter currentFilter, Unit currentUnit, List<Filter> descs, List<Filter> bridge)
	{
		if (!currentFilter.prevDesc.isEmpty())
		{							
			
			/*
			//insert up to 1 random superlative													
			if (superlative == null && descSets.get(k).size() == 1 && tempFilter.tags.containsName("allowsuperlative"))
				superlative = tempFilter;
			
			if (superlative == tempFilter)
			{
				possibles = descs.stream().filter(f -> f.name.equals("superlative")).collect(Collectors.toList());
				desc.insert(currentPosition, getRandomDescription(chandler.handleChanceIncs(u,possibles).getRandom(random)));
			}
			*/
			
			//final String target = currentFilter.prevDesc;														
									
			currentFilter = chandler.handleChanceIncs(currentUnit, currentFilter.prevDesc).getRandom(random);
			if (currentFilter != null)
				return expandPrevDesc(compiledDesc, currentFilter, currentUnit, descs, bridge).append(getRandomDescription(currentFilter));
			else
				return compiledDesc;
		}	
		else
		{
			if (bridge != null)
			{
				currentFilter = chandler.handleChanceIncs(currentUnit, bridge).getRandom(random);
				if (currentFilter != null)
					return compiledDesc;
				else
					return compiledDesc;
			}
			else				
				return compiledDesc;
		}
	}
	
	public StringBuilder expandNextDesc(StringBuilder compiledDesc, Filter currentFilter, Unit currentUnit, List<Filter> descs, int position)
	{
		if (!currentFilter.nextDesc.isEmpty())
		{							
			currentFilter = chandler.handleChanceIncs(currentUnit, currentFilter.nextDesc).getRandom(random);
			if (currentFilter != null)				
				return expandNextDesc(compiledDesc, currentFilter, currentUnit, descs, position).insert(position,getRandomDescription(currentFilter));
			else
				return compiledDesc;
		}	
		else
			return compiledDesc;
	}
	
	private void describeUnits(DescriptionReplacer dr, List<Unit> units, Filter descf, List<Filter> descs)
	{
		dr.calibrate(units);
		
		for (Unit u : units)
			describeUnit(dr, u, descf, descs);
	}
	
	private void describeUnit(DescriptionReplacer dr, Unit u, Filter descf, List<Filter> descs)
	{			
		LinkedHashMap<String,ArrayList<Filter>> descSets = new LinkedHashMap<>();
			
		dr.calibrate(u);
		
		StringBuilder desc = new StringBuilder(u.race.tags.getString("description").map(s -> s + " ").orElse(""));
		
		if(descf != null)
		{
			if (!descf.prevDesc.isEmpty())
				desc = expandPrevDesc(desc, descf, u, descs, null);
			
			desc.append(getRandomDescription(descf));
			
			if (!descf.nextDesc.isEmpty())
				desc = expandNextDesc(desc, descf, u, descs, desc.length());
		}
		
		desc.append(u.slotmap.items()
			.map(i -> i.tags.getString("description"))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(Collectors.joining(" "))).append(" ");
			
		
		desc = new StringBuilder(dr.replace(desc.toString().trim()));
		
		
		Command tmpDesc = null;
		

		for(Command c : u.getCommands())
		{
			if(c.command.equals("#descr"))
				tmpDesc = c;
		}
		
		
		if(tmpDesc != null)
		{
			desc.append(" ").append(tmpDesc.args.get(0));
		}
		
		for(Filter f : u.appliedFilters)
		{
			if(f != null)
			{
				String tempSet = "misc";
				if (!f.descSet.isEmpty()) tempSet = f.descSet;
					
				if (!descSets.containsKey(tempSet))						
					descSets.put(tempSet, new ArrayList<Filter>());
				
				descSets.get(tempSet).add(f);
			}
		}
					
		descs = ChanceIncHandler.retrieveFilters("filterdescriptions", "filterdescs", assets.descriptions, null, u.race);
					
		for (String k : descSets.keySet())
		{
			List<Filter> sorted = new ArrayList<>();
			
			sorted.addAll(descSets.get(k).stream().filter(f -> !f.tags.containsName("negative") && (f.tags.containsName("description") || f.tags.containsName("synonym"))).collect(Collectors.toList()));
			sorted.addAll(descSets.get(k).stream().filter(f -> f.tags.containsName("negative") && (f.tags.containsName("description") || f.tags.containsName("synonym"))).collect(Collectors.toList()));
			
			descSets.get(k).clear();
			descSets.get(k).addAll(sorted);
		}
					
		List<Filter> bridge = null;
		String[] descCategories = {"mage","army role","physique","terrain","immunity","vulnerability","senses","lineage","wondrous","misc","death"};
		
		for (String k : descCategories)
		{
			if (descSets.containsKey(k) && descSets.get(k).size() > 0)
			{
				//ChanceIncHandler chandler = new ChanceIncHandler(n);				
				Filter tempFilter = descSets.get(k).get(0); 
				boolean hasDesc = !getRandomDescription(tempFilter).isEmpty();
				boolean negative = false;
					
				if (!tempFilter.prevDesc.isEmpty())
					desc = expandPrevDesc(desc, tempFilter, u, descs, bridge);
				bridge = null;
				
				/*
				while (!tempFilter.prevDesc.isEmpty())
				{
					List<Filter> possibles = new ArrayList<>();
					
					//insert up to 1 random superlative													
					if (superlative == null && descSets.get(k).size() == 1 && tempFilter.tags.containsName("allowsuperlative"))
						superlative = tempFilter;
					
					if (superlative == tempFilter)
					{
						possibles = descs.stream().filter(f -> f.name.equals("superlative")).collect(Collectors.toList());
						desc.insert(currentPosition, getRandomDescription(chandler.handleChanceIncs(u,possibles).getRandom(random)));
					}
					
					final String target = tempFilter.prevDesc;														
											
					possibles = descs.stream().filter(f -> f.name.equals(target)).collect(Collectors.toList());
																
					if (possibles.size() > 0 && possibles.get(0).prevDesc.isEmpty() && !bridge.isEmpty())
					{						
						final String bridgeTarget = bridge;				
						bridge = "";
						
						possibles = descs.stream().filter(f -> f.name.equals(bridgeTarget)).collect(Collectors.toList());
					}
														
					if (possibles.size() > 0)
					{	
						tempFilter = chandler.handleChanceIncs(u, possibles).getRandom(random);							
						desc.insert(currentPosition, getRandomDescription(tempFilter));
					}
				}					
				*/
				
				for (int i=0; i < descSets.get(k).size(); i++)
				{	
					if (!getRandomDescription(descSets.get(k).get(i)).equals("")) 
					{
						hasDesc = true;
						desc.append(getRandomDescription(descSets.get(k).get(i)));
						if (i < descSets.get(k).size() - 1)
						{
							if (!negative && descSets.get(k).size() > 2)
								desc.append(",");
							
							if (!negative && descSets.get(k).get(i+1).tags.containsName("negative"))
							{
								negative = true;
								desc.append(" but");
							}
							else if (i == descSets.get(k).size() - 2)
								desc.append(" and");
						}					
						else
						{							
							tempFilter = descSets.get(k).get(i);	
							if (tempFilter.bridgeDesc.size() > 0)
								bridge = tempFilter.bridgeDesc;
																						
							if (!tempFilter.nextDesc.isEmpty())
								desc = expandNextDesc(desc, tempFilter, u, descs, desc.length());
							
							if (descSets.values().stream().flatMap(x -> x.stream()).collect(Collectors.toList()).size() == 1)
								desc.append(tempFilter.tags.getString("extendeddescription").map(s -> " " + s).orElse(""));								
							
							/*
							while (!tempFilter.nextDesc.isEmpty())
							{
								List<Filter> possibles = new ArrayList<>();
								
								for (Filter f : descs)
								{						
									if (f.name.equals(tempFilter.nextDesc))
										possibles.add(f);						
								}
																	
								if (possibles.size() > 0)
								{	
									tempFilter = chandler.handleChanceIncs(u, possibles).getRandom(random);
									desc.append(getRandomDescription(tempFilter));
									bridge = tempFilter.bridgeDesc;
								}
							}		
							*/
						}
					}
				}
				
				if (hasDesc && bridge == null) 
					desc.append(".");
			}
		}
		
		//if we get here it means the last filter on the list was looking for another description to segue into and didn't have one, so cap it with a period
		if (bridge != null)
			desc.append(".");
	
		
		if(u.tags.containsName("montagunit"))
		{
			if(desc.length() > 0)
				desc.append("\n\n");
			
			desc.append("When recruited, one unit of this category will appear instead of the unit shown here.");
		}
		
		String description = dr.replace(desc.toString()).replaceAll("\"", "");

		if(tmpDesc != null)
			u.setCommandValue("#descr", description);
		else
			u.commands.add(Command.args("#descr", description));
	}
	
	private void describeTroops()
	{
		String[] roles = {"ranged", "infantry", "mounted", "chariot", "sacred"};
		
		List<Filter> descs = ChanceIncHandler.retrieveFilters("troopdescriptions", "troopdescs", assets.descriptions, null, n.races.get(0));
		
		DescriptionReplacer dr = new DescriptionReplacer(n);
		for(String role : roles)
		{
			
			List<Unit> units = n.listTroops(role);
			if(units.isEmpty())
				continue;
				
				
			if(!role.equals("mounted"))
				dr.descs.put("%role%", role);
			else
				dr.descs.put("%role%", "cavalry");
	
			if(role.equals("chariot"))
				dr.descs.put("%role%", role + "s");
			if(role.equals("ranged") || role.equals("sacred"))
				dr.descs.put("%role%", role + " units");
	
			
			List<Filter> possibles = new ArrayList<>();
			for(Filter f : descs)
				if(ChanceIncHandler.suitableFor(units.get(0), f, n))
					possibles.add(f);
			
			Filter descf = null;
			if(possibles.size() > 0)
			{
				descf = chandler.handleChanceIncs(units.get(0), possibles).getRandom(random);

			}

			
			describeUnits(dr, units, descf, descs);
			
		}
		
	}

	private void describeCommanders()
	{
		String[] roles = {"commanders", "priests","mages"};
		
		//ChanceIncHandler chandler = new ChanceIncHandler(n);
		//List<Filter> descs = ChanceIncHandler.retrieveFilters("troopdescriptions", "troopdescs", n.nationGen.descriptions, null, n.races.get(0));
				
		List<Filter> descs = ChanceIncHandler.retrieveFilters("commanderdescriptions", "commanderdescs", assets.descriptions, null, n.races.get(0));

		DescriptionReplacer dr = new DescriptionReplacer(n);
		
		for(String role : roles)
		{
			List<Unit> units = n.listCommanders(role);
	
			dr.calibrate(units);

			for(Unit u : units)
			{
				dr.calibrate(u);

				/*
				StringBuilder desc = new StringBuilder(u.race.tags.getString("description").map(s -> s + " ").orElse(""));
	
	
				Command tmpDesc = null;
				

				for(Command c : u.getCommands())
				{
					if(c.command.equals("#descr"))
						tmpDesc = c;
				}
				
				if(tmpDesc != null)
				{
					desc.append(tmpDesc.args.get(0));
				}
											
				for(Filter f : u.appliedFilters)
				{
					if(f != null)
					{
						desc.append(f.tags.getValue("description").map(s -> " " + s).orElse(""));
					}
				}			
				
				
				String description = dr.replace(desc.toString()).replaceAll("\"", "");

				if(tmpDesc != null)
					u.setCommandValue("#descr", description);
				else
					u.commands.add(Command.args("#descr", description));
			
			*/				
				
				List<Filter> possibles = new ArrayList<>();
				for(Filter f : descs.stream().filter(f -> f.name.equals("commander start")).collect(Collectors.toList()))
					if(ChanceIncHandler.suitableFor(units.get(0), f, n))
						possibles.add(f);
				
				Filter descf = null;
				if(possibles.size() > 0)
				{
					descf = chandler.handleChanceIncs(units.get(0), possibles).getRandom(random);
				}
				
				describeUnit(dr, u, descf, descs);
			}
		}
		
	}

	
}
