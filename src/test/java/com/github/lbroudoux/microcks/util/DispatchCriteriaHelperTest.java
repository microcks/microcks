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

import static org.junit.Assert.*;

import org.junit.Test;
/**
 * This is a test case for DispatchCriteriaHelper class.
 * @author laurent
 */
public class DispatchCriteriaHelperTest {

   @Test
   public void testExtractFromURIPattern(){
      // Check with parts sorted in natural order.
      String requestPath = "/deployment/byComponent/myComp/1.2";
      String operationName = "/deployment/byComponent/{component}/{version}";
      
      // Dispatch string parts are sorted.
      String dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operationName, requestPath);
      assertEquals("/component=myComp/version=1.2", dispatchCriteria);
   }
   
   @Test
   public void testExtractFromURIPatternUnsorted(){
      // Check with parts not sorted in natural order.
      String requestPath = "/deployment/byComponent/1.2/myComp";
      String operationName = "/deployment/byComponent/{version}/{component}";
      
      // Dispatch string parts are sorted.
      String dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operationName, requestPath);
      assertEquals("/component=myComp/version=1.2", dispatchCriteria);
   }
   
   @Test
   public void testExtractFromURIPatternWithExtension(){
      // Check with parts sorted in natural order.
      String requestPath = "/deployment/byComponent/myComp/1.2.json";
      String operationName = "/deployment/byComponent/{component}/{version}.json";
      
      // Dispatch string parts are sorted.
      String dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operationName, requestPath);
      assertEquals("/component=myComp/version=1.2", dispatchCriteria);
   }
}
