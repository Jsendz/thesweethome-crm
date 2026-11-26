package com.sweethomecompany.crm.property;

import java.util.Map;
import java.util.UUID;

public record MediaResponse(UUID id, MediaType mediaType, Map<String, String> variants, int sortOrder) {

    public static MediaResponse from(PropertyMedia media) {
        return new MediaResponse(media.getId(), media.getMediaType(), media.getVariants(), media.getSortOrder());
    }
}
