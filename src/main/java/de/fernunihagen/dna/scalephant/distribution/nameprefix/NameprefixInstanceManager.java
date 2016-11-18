/*******************************************************************************
 *
 *    Copyright (C) 2015-2016
 *  
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License. 
 *    
 *******************************************************************************/
package de.fernunihagen.dna.scalephant.distribution.nameprefix;

import java.util.HashMap;
import java.util.Map;

import de.fernunihagen.dna.scalephant.distribution.DistributionGroupName;

public class NameprefixInstanceManager {
	
	/**
	 * The local mappings for a distribution group
	 */
	protected final static Map<DistributionGroupName, NameprefixMapper> instances;
	
	static {
		instances = new HashMap<DistributionGroupName, NameprefixMapper>();
	}
	
	/**
	 * Get the instance 
	 * @param distributionGroupName
	 */
	public static synchronized NameprefixMapper getInstance(final DistributionGroupName distributionGroupName) {
		if(! instances.containsKey(distributionGroupName)) {
			instances.put(distributionGroupName, new NameprefixMapper());
		}
		
		return instances.get(distributionGroupName);
	}
}
