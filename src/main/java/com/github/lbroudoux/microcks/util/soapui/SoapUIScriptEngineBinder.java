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
package com.github.lbroudoux.microcks.util.soapui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.servlet.http.HttpServletRequest;
/**
 * Utility class that holds methods for creating binding environments for
 * a JSR 233 ScriptEngine.
 * @author laurent
 */
public class SoapUIScriptEngineBinder{

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SoapUIScriptEngineBinder.class);
   
   /**
    * Create and bind a SoapUI environment for a ScriptEngine.
    * @param engine The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    */
   public static void bindSoapUIEnvironment(ScriptEngine engine, String requestContent){
      
      // Build a fake request container.
      FakeSoapUIMockRequest mockRequest = new FakeSoapUIMockRequest(requestContent, null);
      
      // Create bindings and put content according to SoapUI binding environment.
      Bindings bindings = engine.createBindings();
      bindings.put("mockRequest", mockRequest);
      bindings.put("log", log);
      engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
   }
   
   /**
    * Create and bind a SoapUI environment for a ScriptEngine.
    * @param engine The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param request The wrapped incoming servlet request.
    */
   public static void bindSoapUIEnvironment(ScriptEngine engine, String requestContent, HttpServletRequest request){
      // Build a fake request container.
      FakeSoapUIMockRequest mockRequest = new FakeSoapUIMockRequest(requestContent, null);
      mockRequest.setRequest(request);
      
      // Create bindings and put content according to SoapUI binding environment.
      Bindings bindings = engine.createBindings();
      bindings.put("mockRequest", mockRequest);
      bindings.put("log", log);
      engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
   }
}
