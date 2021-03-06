/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.client.impl.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.schema.SchemaDefinition;
import org.apache.pulsar.client.api.schema.SchemaReader;
import org.apache.pulsar.client.impl.schema.reader.JsonReader;
import org.apache.pulsar.client.impl.schema.writer.JsonWriter;
import org.apache.pulsar.common.schema.SchemaInfo;
import org.apache.pulsar.common.schema.SchemaType;

import java.util.Map;

/**
 * A schema implementation to deal with json data.
 */
@Slf4j
public class JSONSchema<T> extends StructSchema<T> {
    // Cannot use org.apache.pulsar.common.util.ObjectMapperFactory.getThreadLocal() because it does not
    // return shaded version of object mapper
    private static final ThreadLocal<ObjectMapper> JSON_MAPPER = ThreadLocal.withInitial(() -> {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    });

    private final Class<T> pojo;

    private JSONSchema(SchemaInfo schemaInfo, Class<T> pojo) {
        super(schemaInfo);
        this.pojo = pojo;
        setWriter(new JsonWriter<>(JSON_MAPPER.get()));
        setReader(new JsonReader<>(JSON_MAPPER.get(), pojo));
    }

    @Override
    protected SchemaReader<T> loadReader(byte[] schemaVersion) {
        throw new RuntimeException("JSONSchema don't support schema versioning");
    }

    /**
     * Implemented for backwards compatibility reasons
     * since the original schema generated by JSONSchema was based off the json schema standard
     * since then we have standardized on Avro
     *
     * @return
     */
    public SchemaInfo getBackwardsCompatibleJsonSchemaInfo() {
        SchemaInfo backwardsCompatibleSchemaInfo;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(objectMapper);
            JsonSchema jsonBackwardsCompatibleSchema = schemaGen.generateSchema(pojo);
            backwardsCompatibleSchemaInfo = new SchemaInfo();
            backwardsCompatibleSchemaInfo.setName("");
            backwardsCompatibleSchemaInfo.setProperties(schemaInfo.getProperties());
            backwardsCompatibleSchemaInfo.setType(SchemaType.JSON);
            backwardsCompatibleSchemaInfo.setSchema(objectMapper.writeValueAsBytes(jsonBackwardsCompatibleSchema));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
        return backwardsCompatibleSchemaInfo;
    }

    public static <T> JSONSchema<T> of(SchemaDefinition<T> schemaDefinition) {
        return new JSONSchema<>(parseSchemaInfo(schemaDefinition, SchemaType.JSON), schemaDefinition.getPojo());
    }

    public static <T> JSONSchema<T> of(Class<T> pojo) {
        return JSONSchema.of(SchemaDefinition.<T>builder().withPojo(pojo).build());
    }

    public static <T> JSONSchema<T> of(Class<T> pojo, Map<String, String> properties) {
        return JSONSchema.of(SchemaDefinition.<T>builder().withPojo(pojo).withProperties(properties).build());
    }

}
