package com.example.nebullamasearch.search;

public record SearchFilters(
    String objectType,
    String agency,
    String status,
    String wavelengthBand,
    String journal,
    String nationality,
    Integer yearFrom,
    Integer yearTo) {}
