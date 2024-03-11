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
package io.github.microcks.util.grpc;

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ServiceRepository;

import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.Status;
import io.grpc.reflection.v1alpha.ErrorResponse;
import io.grpc.reflection.v1alpha.FileDescriptorResponse;
import io.grpc.reflection.v1alpha.ListServiceResponse;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.reflection.v1alpha.ServiceResponse;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Provides a reflection service for GRPC services (excluding the reflection service itself) registered into this
 * Microcks instance.
 * @author laurent
 */
public class ProtoReflectionService extends ServerReflectionGrpc.ServerReflectionImplBase {

   final ServiceRepository serviceRepository;
   final ResourceRepository resourceRepository;

   /**
    * Build a new ProtoReflectionService using application repositories.
    * @param serviceRepository  Repository to get access to the list of GRPC services.
    * @param resourceRepository Repository to get access to the list of Protobuf resources for services.
    */
   public ProtoReflectionService(ServiceRepository serviceRepository, ResourceRepository resourceRepository) {
      this.serviceRepository = serviceRepository;
      this.resourceRepository = resourceRepository;
   }

   /**
    * Build a new ProtoReflectionService using application repositories.
    * @param serviceRepository  Repository to get access to the list of GRPC services.
    * @param resourceRepository Repository to get access to the list of Protobuf resources for services.
    */
   public static BindableService newInstance(ServiceRepository serviceRepository,
         ResourceRepository resourceRepository) {
      return new ProtoReflectionService(serviceRepository, resourceRepository);
   }

   @Override
   public StreamObserver<ServerReflectionRequest> serverReflectionInfo(
         StreamObserver<ServerReflectionResponse> responseObserver) {
      final ServerCallStreamObserver<ServerReflectionResponse> serverCallStreamObserver = (ServerCallStreamObserver<ServerReflectionResponse>) responseObserver;
      ProtoReflectionStreamObserver requestObserver = new ProtoReflectionStreamObserver(serviceRepository,
            resourceRepository, serverCallStreamObserver);
      serverCallStreamObserver.setOnReadyHandler(requestObserver);
      serverCallStreamObserver.disableAutoRequest();
      serverCallStreamObserver.request(1);
      return requestObserver;
   }

   private static class ProtoReflectionStreamObserver implements Runnable, StreamObserver<ServerReflectionRequest> {

      private final ServiceRepository serviceRepository;
      private final ResourceRepository resourceRepository;
      private final ServerCallStreamObserver<ServerReflectionResponse> serverCallStreamObserver;
      private boolean closeAfterSend = false;
      private ServerReflectionRequest request;

      ProtoReflectionStreamObserver(ServiceRepository serviceRepository, ResourceRepository resourceRepository,
            ServerCallStreamObserver<ServerReflectionResponse> serverCallStreamObserver) {
         this.serviceRepository = serviceRepository;
         this.resourceRepository = resourceRepository;
         this.serverCallStreamObserver = checkNotNull(serverCallStreamObserver, "observer");
      }

      @Override
      public void run() {
         if (request != null) {
            handleReflectionRequest();
         }
      }

      @Override
      public void onNext(ServerReflectionRequest request) {
         checkState(this.request == null);
         this.request = checkNotNull(request);
         handleReflectionRequest();
      }

      @Override
      public void onCompleted() {
         if (request != null) {
            closeAfterSend = true;
         } else {
            serverCallStreamObserver.onCompleted();
         }
      }

      @Override
      public void onError(Throwable cause) {
         serverCallStreamObserver.onError(cause);
      }

      private void handleReflectionRequest() {
         if (serverCallStreamObserver.isReady()) {
            switch (request.getMessageRequestCase()) {
               case FILE_BY_FILENAME:
                  getFileByName(request);
                  break;
               case FILE_CONTAINING_SYMBOL:
                  getFileContainingSymbol(request);
                  break;
               case FILE_CONTAINING_EXTENSION:
                  getFileByExtension(request);
                  break;
               case ALL_EXTENSION_NUMBERS_OF_TYPE:
                  getAllExtensions(request);
                  break;
               case LIST_SERVICES:
                  listServices(request);
                  break;
               default:
                  sendErrorResponse(request, Status.Code.UNIMPLEMENTED,
                        "not implemented " + request.getMessageRequestCase());
            }
            request = null;
            if (closeAfterSend) {
               serverCallStreamObserver.onCompleted();
            } else {
               serverCallStreamObserver.request(1);
            }
         }
      }

      private void getFileByName(ServerReflectionRequest request) {
         sendErrorResponse(request, Status.Code.UNIMPLEMENTED, "not implemented " + request.getMessageRequestCase());
      }

      private void getFileContainingSymbol(ServerReflectionRequest request) {
         String serviceName = request.getFileContainingSymbol();
         String version = getVersionFromServiceName(serviceName);

         Service service = serviceRepository.findByNameAndVersion(serviceName, version);
         if (service != null) {
            sendFileDescriptorForService(service, request);
         } else {
            // This could be a 'describe' request on a method or inner type like:
            // grpcurl -plaintext localhost:9090 describe io.github.microcks.grpc.hello.v1.HelloService.greeting
            serviceName = serviceName.substring(0, serviceName.lastIndexOf("."));
            version = getVersionFromServiceName(serviceName);

            service = serviceRepository.findByNameAndVersion(serviceName, version);
            if (service != null) {
               sendFileDescriptorForService(service, request);
            } else {
               sendErrorResponse(request, Status.Code.NOT_FOUND, "Symbol not found");
            }
         }
      }

      private void sendFileDescriptorForService(Service service, ServerReflectionRequest request) {
         List<Resource> resources = resourceRepository.findByServiceIdAndType(service.getId(),
               ResourceType.PROTOBUF_DESCRIPTOR);
         if (!resources.isEmpty()) {
            Resource resource = resources.get(0);

            // Now we may have serviceName as being the FQDN. We have to find short version to later findServiceByName().
            String shortServiceName = service.getName();
            if (service.getName().contains(".")) {
               shortServiceName = service.getName().substring(service.getName().lastIndexOf(".") + 1);
            }

            try {
               Descriptors.FileDescriptor fd = GrpcUtil.findFileDescriptorBySymbol(resource.getContent(),
                     shortServiceName);
               serverCallStreamObserver.onNext(createServerReflectionResponse(request, fd));
            } catch (Exception e) {
               sendErrorResponse(request, Status.Code.INTERNAL, "Unreadable protobuf descriptor");
            }
         } else {
            sendErrorResponse(request, Status.Code.INTERNAL, "No protobuf descriptor found");
         }
      }

      private void getFileByExtension(ServerReflectionRequest request) {
         sendErrorResponse(request, Status.Code.UNIMPLEMENTED, "not implemented " + request.getMessageRequestCase());
      }

      private void getAllExtensions(ServerReflectionRequest request) {
         sendErrorResponse(request, Status.Code.UNIMPLEMENTED, "not implemented " + request.getMessageRequestCase());
      }

      private void listServices(ServerReflectionRequest request) {
         ListServiceResponse.Builder builder = ListServiceResponse.newBuilder();

         List<Service> services = serviceRepository.findByType(ServiceType.GRPC);
         for (Service service : services) {
            builder.addService(ServiceResponse.newBuilder().setName(service.getName()));
         }

         serverCallStreamObserver.onNext(ServerReflectionResponse.newBuilder().setValidHost(request.getHost())
               .setOriginalRequest(request).setListServicesResponse(builder).build());
      }

      private void sendErrorResponse(ServerReflectionRequest request, Status.Code code, String message) {
         ServerReflectionResponse response = ServerReflectionResponse.newBuilder().setValidHost(request.getHost())
               .setOriginalRequest(request)
               .setErrorResponse(ErrorResponse.newBuilder().setErrorCode(code.value()).setErrorMessage(message))
               .build();
         serverCallStreamObserver.onNext(response);
      }

      private ServerReflectionResponse createServerReflectionResponse(ServerReflectionRequest request,
            Descriptors.FileDescriptor fd) {
         FileDescriptorResponse.Builder fdRBuilder = FileDescriptorResponse.newBuilder();

         Set<String> seenFiles = new HashSet<>();
         Queue<Descriptors.FileDescriptor> frontier = new ArrayDeque<>();
         seenFiles.add(fd.getName());
         frontier.add(fd);
         while (!frontier.isEmpty()) {
            Descriptors.FileDescriptor nextFd = frontier.remove();
            fdRBuilder.addFileDescriptorProto(nextFd.toProto().toByteString());
            for (Descriptors.FileDescriptor dependencyFd : nextFd.getDependencies()) {
               if (!seenFiles.contains(dependencyFd.getName())) {
                  seenFiles.add(dependencyFd.getName());
                  frontier.add(dependencyFd);
               }
            }
         }
         return ServerReflectionResponse.newBuilder().setValidHost(request.getHost()).setOriginalRequest(request)
               .setFileDescriptorResponse(fdRBuilder).build();
      }

      private String getVersionFromServiceName(String serviceName) {
         // Retrieve version from package name.
         // org.acme package => org.acme version
         // org.acme.v1 package => v1 version
         String packageName = serviceName.substring(0, serviceName.lastIndexOf('.'));
         String[] parts = packageName.split("\\.");
         return (parts.length > 2 ? parts[parts.length - 1] : packageName);
      }
   }
}
