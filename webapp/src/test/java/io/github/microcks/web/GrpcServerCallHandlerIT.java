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
package io.github.microcks.web;

import org.junit.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test case for the GrpcServerCallHandler.
 * @author laurent
 */
public class GrpcServerCallHandlerIT extends AbstractBaseIT {

   private static final String GRPCURL_IMAGE = "quay.io/microcks/grpcurl:v1.8.9-alpine";

   @Test
   public void testGrpcMocking() {
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/hello-v1.proto", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/HelloService.postman.json", false);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/HelloService.metadata.yml", false);

      Testcontainers.exposeHostPorts(9090);

      GenericContainer grpcurl = new GenericContainer(GRPCURL_IMAGE).withAccessToHost(true);

      try {
         grpcurl.start();
         Container.ExecResult result = grpcurl.execInContainer("/bin/grpcurl", "-plaintext", "-d", """
               {"firstname": "Laurent", "lastname": "Broudoux"}
               """, "host.testcontainers.internal:9090", "io.github.microcks.grpc.hello.v1.HelloService/greeting");

         assertTrue(result.getStdout().contains("\"greeting\": \"Hello Laurent Broudoux !\""));
      } catch (Exception e) {
         fail("No exception should be thrown");
      } finally {
         grpcurl.stop();
      }
   }

   @Test
   public void testGrpcReflection() {
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/hello-v1.proto", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/HelloService.postman.json", false);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/HelloService.metadata.yml", false);

      Testcontainers.exposeHostPorts(9090);

      GenericContainer grpcurl = new GenericContainer(GRPCURL_IMAGE).withAccessToHost(true);

      try {
         grpcurl.start();
         Container.ExecResult result = grpcurl.execInContainer("/bin/grpcurl", "-plaintext",
               "host.testcontainers.internal:9090", "list");

         assertTrue(result.getStdout().contains("io.github.microcks.grpc.hello.v1.HelloService"));

         result = grpcurl.execInContainer("/bin/grpcurl", "-plaintext", "host.testcontainers.internal:9090", "describe",
               "io.github.microcks.grpc.hello.v1.HelloService.greeting");

         assertTrue(result.getStdout().contains("io.github.microcks.grpc.hello.v1.HelloService.greeting is a method:"));
         assertTrue(result.getStdout().contains(
               "rpc greeting ( .io.github.microcks.grpc.hello.v1.HelloRequest ) returns ( .io.github.microcks.grpc.hello.v1.HelloResponse );"));
      } catch (Exception e) {
         fail("No exception should be thrown");
      } finally {
         grpcurl.stop();
      }
   }
}
