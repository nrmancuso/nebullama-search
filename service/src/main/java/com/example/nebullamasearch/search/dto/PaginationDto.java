package com.example.nebullamasearch.search.dto;

public record PaginationDto(Integer from, Integer size) {

  public int resolvedFrom() {
    return from != null ? from : 0;
  }

  public int resolvedSize() {
    return size != null ? size : 10;
  }
}
