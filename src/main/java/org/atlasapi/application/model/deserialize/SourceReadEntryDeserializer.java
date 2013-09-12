package org.atlasapi.application.model.deserialize;

import java.lang.reflect.Type;

import org.atlasapi.application.SourceStatus;
import org.atlasapi.application.model.SourceReadEntry;
import org.atlasapi.media.entity.Publisher;

import com.google.common.base.Optional;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;


public class SourceReadEntryDeserializer implements JsonDeserializer<SourceReadEntry> {
    @Override
    public SourceReadEntry deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            Optional<Publisher> publisher = Publisher.fromPossibleKey(obj.getAsJsonPrimitive("key")
                    .getAsString());
            SourceStatus.SourceState sourceState = SourceStatus.SourceState.valueOf(obj.getAsJsonPrimitive("state")
                    .getAsString()
                    .toUpperCase());
            SourceStatus sourceStatus = new SourceStatus(
                    sourceState,
                    obj.getAsJsonPrimitive("enabled").getAsBoolean());
        return new SourceReadEntry(publisher.get(), sourceStatus);
    }
}
