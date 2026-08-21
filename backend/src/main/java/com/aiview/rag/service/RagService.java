package com.aiview.rag.service;

import com.aiview.agent.ai.EmbeddingClient;
import com.aiview.common.BizException;
import com.aiview.common.ResultCode;
import com.aiview.rag.dto.RagDtos;
import com.aiview.rag.entity.KnowledgeBase;
import com.aiview.rag.entity.KnowledgeChunk;
import com.aiview.rag.mapper.KnowledgeBaseMapper;
import com.aiview.rag.mapper.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public List<RagDtos.KnowledgeBaseVO> listKbs(Long userId) {
        return kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, userId)
                        .orderByDesc(KnowledgeBase::getId))
                .stream().map(kb -> new RagDtos.KnowledgeBaseVO(
                        kb.getId(), kb.getName(), kb.getDescription(), kb.getChunkCount(), kb.getCreatedAt()))
                .toList();
    }

    public RagDtos.KnowledgeBaseVO createKb(Long userId, RagDtos.CreateKbRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "知识库名称不能为空");
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        kb.setName(req.getName().trim());
        kb.setDescription(req.getDescription() == null ? "" : req.getDescription().trim());
        kb.setChunkCount(0);
        kbMapper.insert(kb);
        return new RagDtos.KnowledgeBaseVO(
                kb.getId(), kb.getName(), kb.getDescription(), 0, kb.getCreatedAt());
    }

    @Transactional
    public void deleteKb(Long userId, Long kbId) {
        KnowledgeBase kb = requireOwnedKb(userId, kbId);
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getKbId, kbId));
        kbMapper.deleteById(kbId);
    }

    public List<RagDtos.ChunkVO> listChunks(Long userId, Long kbId) {
        requireOwnedKb(userId, kbId);
        return chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getKbId, kbId)
                        .orderByDesc(KnowledgeChunk::getId))
                .stream().map(c -> new RagDtos.ChunkVO(
                        c.getId(), c.getKbId(), c.getTitle(), c.getContent(), c.getCreatedAt()))
                .toList();
    }

    @Transactional
    public List<RagDtos.ChunkVO> addContent(Long userId, Long kbId, RagDtos.AddContentRequest req) {
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "内容不能为空");
        }
        KnowledgeBase kb = requireOwnedKb(userId, kbId);
        String title = req.getTitle() == null ? "" : req.getTitle().trim();

        List<String> pieces = chunkText(req.getContent());
        List<float[]> vectors = embeddingClient.embedAll(pieces);
        List<KnowledgeChunk> rows = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setKbId(kbId);
            chunk.setTitle(title);
            chunk.setContent(pieces.get(i));
            chunk.setEmbedding(toJson(vectors.get(i)));
            chunkMapper.insert(chunk);
            rows.add(chunk);
        }
        kb.setChunkCount(kb.getChunkCount() + rows.size());
        kbMapper.updateById(kb);
        log.info("知识库 {} 新增 {} 个分块", kb.getName(), rows.size());
        return rows.stream().map(c -> new RagDtos.ChunkVO(
                c.getId(), c.getKbId(), c.getTitle(), c.getContent(), c.getCreatedAt())).toList();
    }

    @Transactional
    public void deleteChunk(Long userId, Long chunkId) {
        KnowledgeChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            throw new BizException(ResultCode.NOT_FOUND, "知识分块不存在");
        }
        KnowledgeBase kb = kbMapper.selectById(chunk.getKbId());
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "知识库不存在");
        }
        chunkMapper.deleteById(chunkId);
        kb.setChunkCount(Math.max(0, kb.getChunkCount() - 1));
        kbMapper.updateById(kb);
    }

    public List<RagDtos.SearchHitVO> search(Long userId, RagDtos.SearchRequest req) {
        String query = req.getQuery();
        if (query == null || query.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "检索关键词不能为空");
        }
        int topK = req.getTopK() == null ? 3 : Math.min(Math.max(req.getTopK(), 1), 10);
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>()
                        .inSql(KnowledgeChunk::getKbId,
                                "SELECT id FROM knowledge_base WHERE user_id = " + userId
                                        + " AND deleted = 0"));
        if (chunks.isEmpty()) {
            return List.of();
        }
        float[] queryVec = embeddingClient.embed(query);
        List<KnowledgeBase> kbs = kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId));
        var kbNameById = kbs.stream().collect(java.util.stream.Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));

        List<RagDtos.SearchHitVO> hits = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            float[] vec = parseVector(chunk.getEmbedding());
            if (vec == null || vec.length != queryVec.length) {
                continue;
            }
            double score = cosine(queryVec, vec);
            hits.add(new RagDtos.SearchHitVO(chunk.getId(), kbNameById.getOrDefault(chunk.getKbId(), ""),
                    chunk.getTitle(), chunk.getContent(), Math.round(score * 1000) / 1000.0));
        }
        hits.sort(Comparator.comparingDouble(RagDtos.SearchHitVO::getScore).reversed());
        return hits.stream().limit(topK).toList();
    }

    /**
     * 面试检索增强：返回格式化后的知识片段，无命中时返回空字符串。
     */
    public String retrieveContext(Long userId, String query, int topK) {
        RagDtos.SearchRequest req = new RagDtos.SearchRequest();
        req.setQuery(query);
        req.setTopK(topK);
        List<RagDtos.SearchHitVO> hits = search(userId, req);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是候选人知识库中检索到的相关片段（可信参考，可据此追问或验证掌握程度）：\n");
        for (RagDtos.SearchHitVO hit : hits) {
            sb.append("--- ").append(hit.getTitle().isBlank() ? hit.getKbName() : hit.getTitle())
                    .append(" ---\n").append(hit.getContent()).append("\n");
        }
        return sb.toString();
    }

    private KnowledgeBase requireOwnedKb(Long userId, Long kbId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "知识库不存在");
        }
        return kb;
    }

    private List<String> chunkText(String content) {
        String normalized = content.replace("\r\n", "\n").trim();
        String[] paragraphs = normalized.split("\n\\s*\n");
        List<String> pieces = new ArrayList<>();
        for (String para : paragraphs) {
            String p = para.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= CHUNK_SIZE) {
                pieces.add(p);
                continue;
            }
            int start = 0;
            while (start < p.length()) {
                int end = Math.min(start + CHUNK_SIZE, p.length());
                pieces.add(p.substring(start, end).trim());
                if (end == p.length()) {
                    break;
                }
                start = Math.max(end - CHUNK_OVERLAP, start + 1);
            }
        }
        return pieces.isEmpty() ? List.of(normalized) : pieces;
    }

    private float[] parseVector(String json) {
        try {
            var node = objectMapper.readTree(json);
            float[] vec = new float[node.size()];
            for (int i = 0; i < node.size(); i++) {
                vec[i] = (float) node.get(i).asDouble();
            }
            return vec;
        } catch (Exception e) {
            log.warn("parse embedding failed: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(float[] vec) {
        try {
            return objectMapper.writeValueAsString(vec);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "向量序列化失败");
        }
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}