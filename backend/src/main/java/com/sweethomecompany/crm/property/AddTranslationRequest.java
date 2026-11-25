package com.sweethomecompany.crm.property;

/**
 * For adding or updating a non-Catalan translation only - locale must be
 * es, fr, or en (400 for "ca" or anything else). The ca translation is
 * created atomically with the property and edited only through
 * PATCH /api/properties/{id}.
 */
public record AddTranslationRequest(String locale, String title, String description) {
}
