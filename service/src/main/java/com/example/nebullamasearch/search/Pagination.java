package com.example.nebullamasearch.search;

public record Pagination(int from, int size) {

  public Pagination {
    if (size <= 0) size = 10;
  }

  public static Pagination defaultPagination() {
    return new Pagination(0, 10);
  }
}
