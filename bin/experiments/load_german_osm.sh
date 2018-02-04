#!/bin/bash
#*******************************************************************************
#
#    Copyright (C) 2015-2018 the BBoxDB project
#  
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#  
#      http://www.apache.org/licenses/LICENSE-2.0
#  
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License. 
#    
#*******************************************************************************

# The base url
baseurl="http://download.geofabrik.de/europe/germany/"

# The regions to download
regions="baden-wuerttemberg bayern berlin brandenburg bremen hamburg hessen mecklenburg-vorpommern niedersachsen nordrhein-westfalen rheinland-pfalz saarland sachs
en sachsen-anhalt schleswig-holstein thueringen"

# workfolder
workfolder="/tmp/work"

cd $workfolder

# Import the regions
for region in $regions; do
   # Download the region if needed
   if [ ! -d $region ]; then
      mkdir $region
      cd $region
      filename="$region-latest.shp.zip"
      echo "Downloading region $region"
      wget $baseurl/$filename
      unzip $filename
      cd ..
   fi
done

