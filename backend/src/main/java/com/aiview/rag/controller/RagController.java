package com.aiview.rag.controller;

import com.aiview.common.Result;
import com.aiview.common.UserContext;
import com.aiview.rag.dto.RagDtos;
import com.aiview.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @GetMapping("/knowledge-bases")
    public Result<List<RagDtos.KnowledgeBaseVO>> listKbs() {
        return Result.ok(ragService.listKbs(UserContext.currentUserId()));
    }

    @PostMapping("/knowledge-bases")
    public Result<RagDtos.KnowledgeBaseVO> createKb(@RequestBody RagDtos.CreateKbRequest req) {
        return Result.ok(ragService.createKb(UserContext.currentUserId(), req));
    }

    @DeleteMapping("/knowledge-bases/{id}")
    public Result<Void> deleteKb(@PathVariable Long id) {
        ragService.deleteKb(UserContext.currentUserId(), id);
        return Result.ok(null);
    }

    @GetMapping("/knowledge-bases/{id}/chunks")
    public Result<List<RagDtos.ChunkVO>> listChunks(@PathVariable Long id) {
        return Result.ok(ragService.listChunks(UserContext.currentUserId(), id));
    }

    @PostMapping("/knowledge-bases/{id}/chunks")
    public Result<List<RagDtos.ChunkVO>> addContent(@PathVariable Long id,
                                                    @RequestBody RagDtos.AddContentRequest req) {
        return Result.ok(ragService.addContent(UserContext.currentUserId(), id, req));
    }

    @DeleteMapping("/knowledge-chunks/{id}")
    public Result<Void> deleteChunk(@PathVariable Long id) {
        ragService.deleteChunk(UserContext.currentUserId(), id);
        return Result.ok(null);
    }

    @PostMapping("/knowledge/search")
    public Result<List<RagDtos.SearchHitVO>> search(@RequestBody RagDtos.SearchRequest req) {
        return Result.ok(ragService.search(UserContext.currentUserId(), req));
    }
}