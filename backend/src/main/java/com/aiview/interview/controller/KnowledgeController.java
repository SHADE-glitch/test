package com.aiview.interview.controller;

import com.aiview.common.Result;
import com.aiview.common.UserContext;
import com.aiview.interview.dto.KnowledgeMapVO;
import com.aiview.interview.service.KnowledgeMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeMapService knowledgeMapService;

    @GetMapping("/knowledge/map")
    public Result<KnowledgeMapVO> map() {
        return Result.ok(knowledgeMapService.buildMap(UserContext.currentUserId()));
    }
}