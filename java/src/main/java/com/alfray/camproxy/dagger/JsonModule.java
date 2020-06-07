package com.alfray.camproxy.dagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public abstract class JsonModule {

    @Provides
    @Singleton
    static ObjectMapper providesJsonObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Pretty print makes it easier to debug & for unit tests.
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Sorting technically not needed due to already using a sorted TreeMap.
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        // We don't want the order of serialized objects to change (for unit tests).
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        // Remove null values in entries.
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper;
    }
}
