package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.CategoryCountDto;
import com.example.instagramreelai.dto.ExtractedKnowledgeItemDto;
import com.example.instagramreelai.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping
    public ResponseEntity<Page<ExtractedKnowledgeItemDto>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subCategory,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(100, size)),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return ResponseEntity.ok(knowledgeService.search(query, category, subCategory, pageRequest));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryCountDto>> getCategories() {
        return ResponseEntity.ok(knowledgeService.getCategoriesHierarchy());
    }

    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill() {
        int count = knowledgeService.backfillFromSummaries();
        return ResponseEntity.ok(Map.of("message", "Backfill complete", "createdCount", count));
    }
}
