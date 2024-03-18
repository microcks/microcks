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
package io.github.microcks.event;

import org.springframework.context.ApplicationEvent;

import java.util.Date;

/**
 * Simple bean representing an invocation event on a Mock.
 * @author laurent
 */
public class MockInvocationEvent extends ApplicationEvent {

   /** */
   private final String serviceName;
   /** */
   private final String serviceVersion;
   /** */
   private final String mockResponse;
   /** */
   private final Date invocationTimestamp;
   /** */
   private final long duration;

   /**
    * Create a new mock invocation event.
    * @param source              Source object for event
    * @param serviceName         Name of invoked service
    * @param serviceVersion      Version of invoked service
    * @param mockResponse        Mock response returned during invocation
    * @param invocationTimestamp Timestamp of invocation
    * @param duration            Duration of invocation
    */
   public MockInvocationEvent(Object source, String serviceName, String serviceVersion, String mockResponse,
         Date invocationTimestamp, long duration) {
      super(source);
      this.serviceName = serviceName;
      this.serviceVersion = serviceVersion;
      this.mockResponse = mockResponse;
      this.invocationTimestamp = invocationTimestamp;
      this.duration = duration;
   }

   public String getServiceName() {
      return serviceName;
   }

   public String getServiceVersion() {
      return serviceVersion;
   }

   public String getMockResponse() {
      return mockResponse;
   }

   public Date getInvocationTimestamp() {
      return invocationTimestamp;
   }

   public long getDuration() {
      return duration;
   }
}
