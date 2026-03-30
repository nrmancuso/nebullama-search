package com.example.nebullamasearch.search;

import java.util.List;

public record SearchResponse(long total, List<SearchHit> hits) {}
