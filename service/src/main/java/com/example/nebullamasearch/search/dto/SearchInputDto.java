package com.example.nebullamasearch.search.dto;

public record SearchInputDto(String query, SearchFiltersDto filters, PaginationDto pagination) {}
