/*
 * Project: Train-Motion
 * Copyright (C) 2021 alf.labs gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alfray.trainmotion.dagger;

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
