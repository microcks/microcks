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
package io.microcks.web.grpc.vertx;

import io.microcks.web.AbstractBaseIT;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for the GrpcServerCallHandler.
 * @author laurent
 */
@QuarkusTest
class VertxGrpcMockServerIT extends AbstractBaseIT {

   private static final String GRPCURL_IMAGE = "quay.io/microcks/grpcurl:v1.8.9-alpine";

   @Test
   void testGrpcMockingWithQueryArgs() {
      uploadArtifactFile("target/test-classes/io/microcks/artifact/grpc/hello-v1.proto", true);
      uploadArtifactFile("target/test-classes/io/microcks/artifact/grpc/HelloService.postman.json", false);

      Testcontainers.exposeHostPorts(getServerPort());

      GenericContainer grpcurl = new GenericContainer(GRPCURL_IMAGE).withAccessToHost(true);

      try {
         grpcurl.start();
         Container.ExecResult result = grpcurl.execInContainer("/bin/grpcurl", "-plaintext", "-d", """
               {"firstname": "Laurent", "lastname": "Broudoux"}
               """, grpcTarget(), "io.microcks.grpc.hello.v1.HelloService/greeting");

         assertTrue(result.getStdout().contains("\"greeting\": \"Hello Laurent Broudoux !\""));

         result = grpcurl.execInContainer("/bin/grpcurl", "-plaintext", "-d", """
               {"firstname": "Philippe", "lastname": "Huet"}
               """, grpcTarget(), "io.microcks.grpc.hello.v1.HelloService/greeting");

         assertTrue(result.getStdout().contains("\"greeting\": \"Hello Philippe Huet !\""));
      } catch (Exception e) {
         fail("No exception should be thrown");
      } finally {
         grpcurl.stop();
      }
   }

   @Test
   void testGrpcMockingWithQueryArgsErrors() {
      uploadArtifactFile("target/test-classes/io/microcks/artifact/grpc/hello-v1.proto", true);
      uploadArtifactFile("target/test-classes/io/microcks/artifact/grpc/hello-v1-examples-errors.yml", false);

      Testcontainers.exposeHostPorts(getServerPort());

      GenericContainer grpcurl = new GenericContainer(GRPCURL_IMAGE).withAccessToHost(true);

      try {
         grpcurl.start();
         Container.ExecResult result = grpcurl.execInContainer("/bin/grpcurl", "-plaintext", "-d", """
               {"firstname": "Laurent", "lastname": "Broudoux"}
               """, grpcTarget(), "io.microcks.grpc.hello.v1.HelloService/greeting");

         // Should be ok but print a warning as we specified a 200 Http code.
         assertTrue(result.getStdout().contains("\"greeting\": \"Hello Laurent Broudoux !\""));

         result = grpcurl.execInContainer("/bin/grpcurl", "-plaintext", "-d", """
               {"firstname": "John", "lastname": "Doe"}
               """, grpcTarget(), "io.microcks.grpc.hello.v1.HelloService/greeting");

         // Should return a NotFound status error.
         assertNotEquals(0, result.getExitCode());
         assertTrue(result.getStdout().trim().isEmpty());
         assertTrue(result.getStderr().contains("Code: NotFound"));
         assertTrue(result.getStderr().contains("{\"greeting\":\"Unknown user\"}"));
      } catch (Exception e) {
         fail("No exception should be thrown");
      } finally {
         grpcurl.stop();
      }
   }


   @Test
   void testGrpcReflection() {
      uploadArtifactFile("target/test-classes/io/microcks/artifact/grpc/hello-v1.proto", true);
      uploadArtifactFile("target/test-classes/io/microcks/artifact/grpc/HelloService.postman.json", false);
      uploadArtifactFile("target/test-classes/io/microcks/artifact/grpc/HelloService.metadata.yml", false);

      Testcontainers.exposeHostPorts(getServerPort());

      GenericContainer grpcurl = new GenericContainer(GRPCURL_IMAGE).withAccessToHost(true);

      try {
         grpcurl.start();
         Container.ExecResult result = grpcurl.execInContainer("/bin/grpcurl", "-plaintext", grpcTarget(), "list");

         assertTrue(result.getStdout().contains("io.microcks.grpc.hello.v1.HelloService"));

         result = grpcurl.execInContainer("/bin/grpcurl", "-plaintext", grpcTarget(), "describe",
               "io.microcks.grpc.hello.v1.HelloService.greeting");

         assertTrue(result.getStdout().contains("io.microcks.grpc.hello.v1.HelloService.greeting is a method:"));
         assertTrue(result.getStdout().contains(
               "rpc greeting ( .io.microcks.grpc.hello.v1.HelloRequest ) returns ( .io.microcks.grpc.hello.v1.HelloResponse );"));
      } catch (Exception e) {
         fail("No exception should be thrown");
      } finally {
         grpcurl.stop();
      }
   }

   private String grpcTarget() {
      return "host.testcontainers.internal:" + getServerPort();
   }
}
