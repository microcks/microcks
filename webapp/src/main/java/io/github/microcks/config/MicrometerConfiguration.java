package io.github.microcks.config;

import java.util.Map;

import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

@Component
public class MicrometerConfiguration extends DefaultServerRequestObservationConvention {

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        // here, we just want to have an additional KeyValue to the observation, keeping
        // the default values
        return super.getLowCardinalityKeyValues(context).and(additionalTags(context));
    }

    protected KeyValues additionalTags(ServerRequestObservationContext context) {
        KeyValues keyValues = KeyValues.empty();

        Map<String, String> pathVariables = (Map<String, String>) context.getCarrier()
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables != null) {
            String service = pathVariables.get("service");
            String version = pathVariables.get("version");
            if (service != null) {
                keyValues = keyValues.and(KeyValue.of("service", service));
            }
            if (version != null) {
                keyValues = keyValues.and(KeyValue.of("version", version));
            }
        }
        return keyValues;
    }
}
