/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.lbroudoux.microcks.util;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * This is a helper for extracting and building dispatch criteria from many sources.
 * @author laurent
 */
public class DispatchCriteriaHelper{
   
   /**
    * Extract a dispatch rule string from URI pattern (containing variable parts within
    * {}) in order to explain which parts are variables.
    * @param pattern The URI pattern containing variables parts ({})
    * @return A string representing dispatch rules for the corrsponding incoming request.
    */
   public static String extractPartsFromURIPattern(String pattern){
      if (pattern.contains("{") && pattern.contains("}")){
         // Build a pattern for extracting parts from pattern.
         String partsPattern = pattern.replaceAll("(\\{[^\\}]+\\})", "\\\\{(.+)\\\\}");
         Pattern partsP = Pattern.compile(partsPattern);
         Matcher partsM = partsP.matcher(pattern);
         
         if (partsM.matches()){
            StringBuilder parts = new StringBuilder();
            for (int i=1; i<partsM.groupCount()+1; i++){
               parts.append(partsM.group(i));
               if (i<partsM.groupCount()){
                  parts.append(" && ");
               }
            }
            return parts.toString();
         }
      }
      return "";
   }
   
   /**
    * Extract and build a dispatch criteria string from URI pattern (containing variable parts within
    * {}), projected onto a real instanciated URI.
    * @param pattern The URI pattern containing variables parts ({})
    * @param realURI The real URI that should match pattern.
    * @return A string representing dispatch criteria for the corrsponding incoming request.
    */
   public static String extractFromURIPattern(String pattern, String realURI){
      Map<String, String> criteriaMap = new TreeMap<String, String>();
      
      // Build a pattern for extracting parts from pattern.
      String partsPattern = pattern.replaceAll("(\\{[^\\}]+\\})", "\\\\{(.+)\\\\}");
      Pattern partsP = Pattern.compile(partsPattern);
      Matcher partsM = partsP.matcher(pattern);
      
      // Build a pattern for extracting values from realURI.
      String valuesPattern = pattern.replaceAll("(\\{[^\\}]+\\})", "(.+)");
      Pattern valuesP = Pattern.compile(valuesPattern);
      Matcher valuesM = valuesP.matcher(realURI);
      
      // Both should match and have the same group count.
      if (valuesM.matches() && partsM.matches() 
            && valuesM.groupCount() == partsM.groupCount()){
         for (int i=1; i<partsM.groupCount()+1; i++){
            criteriaMap.put(partsM.group(i), valuesM.group(i));
         }
      }
      
      // Just appends sorted entries, separating them with /.
      StringBuilder result = new StringBuilder();
      for (String criteria : criteriaMap.keySet()){
         result.append("/").append(criteria).append("=").append(criteriaMap.get(criteria));
      }
      return result.toString();
   }
}
