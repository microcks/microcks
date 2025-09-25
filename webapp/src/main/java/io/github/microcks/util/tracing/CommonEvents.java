package io.github.microcks.util.tracing;

public enum CommonEvents {
   INVOCATION_RECEIVED("invocation_received"),
   DISPATCHER_SELECTED("dispatcher_selected"),
   PARAMETER_CONSTRAINT_VIOLATED("parameter_constraint_violated");

   private final String eventName;

   CommonEvents(String eventName) {
      this.eventName = eventName;
   }

   public String getEventName() {
      return eventName;
   }
}
