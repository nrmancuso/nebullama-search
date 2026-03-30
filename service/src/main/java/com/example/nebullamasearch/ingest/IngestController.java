package com.example.nebullamasearch.ingest;

import com.example.nebullamasearch.domain.ResourceType;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingest")
class IngestController {

  private final IngestService ingestService;

  public IngestController(IngestService ingestService) {
    this.ingestService = ingestService;
  }

  @PostMapping("/{resourceType}")
  public ResponseEntity<IngestResult> ingestSingle(
      @PathVariable String resourceType, @RequestBody Map<String, Object> doc) {
    try {
      ResourceType type = ResourceType.fromValue(resourceType);
      IngestResult result = ingestService.ingestOne(type, doc);
      return new ResponseEntity<>(result, HttpStatus.CREATED);
    } catch (IllegalArgumentException ex) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/{resourceType}/bulk")
  public ResponseEntity<List<IngestResult>> ingestBulk(
      @PathVariable String resourceType, @RequestBody List<Map<String, Object>> docs) {
    try {
      ResourceType type = ResourceType.fromValue(resourceType);
      List<IngestResult> results = ingestService.ingestBulk(type, docs);
      return new ResponseEntity<>(results, HttpStatus.MULTI_STATUS);
    } catch (IllegalArgumentException ex) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }
}
