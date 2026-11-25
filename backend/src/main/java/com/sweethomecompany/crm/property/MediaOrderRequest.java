package com.sweethomecompany.crm.property;

import java.util.List;
import java.util.UUID;

/**
 * Must be exactly the property's current set of media ids (same size, no
 * duplicates, no unknown ids) - 400 otherwise. Each id's new sortOrder is
 * its index in this list.
 */
public record MediaOrderRequest(List<UUID> mediaIdsInOrder) {
}
