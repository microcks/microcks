/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.artifact.arazzo;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * The Arazzo reader is a kernel shared by the Microcks webapp and the components that run workflows. It knows the
 * Arazzo specification only, so that sharing it couples neither of them to the other.
 * @author SebastienDegodez
 */
@AnalyzeClasses(packages = "io.microcks.artifact.arazzo", importOptions = ImportOption.DoNotIncludeTests.class)
class ArazzoSharedKernelTest {

   @ArchTest
   static final ArchRule theArazzoReaderDependsOnTheSpecificationParsingOnly = classes().should()
         .onlyDependOnClassesThat()
         .resideInAnyPackage("java..", "com.fasterxml.jackson..", "io.microcks.artifact.arazzo..");
}
