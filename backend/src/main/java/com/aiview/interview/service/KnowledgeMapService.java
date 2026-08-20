package com.aiview.interview.service;

import com.aiview.interview.dto.KnowledgeMapVO;
import com.aiview.interview.entity.InterviewSession;
import com.aiview.interview.entity.KnowledgePoint;
import com.aiview.interview.mapper.InterviewSessionMapper;
import com.aiview.interview.mapper.KnowledgePointMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeMapService {

    private final KnowledgePointMapper knowledgePointMapper;
    private final InterviewSessionMapper sessionMapper;

    public KnowledgeMapVO buildMap(Long userId) {
        List<KnowledgePoint> points = knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>().orderByAsc(KnowledgePoint::getSortOrder));

        List<InterviewSession> finished = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .eq(InterviewSession::getStatus, InterviewStatus.FINISHED.name())
                        .isNotNull(InterviewSession::getTotalScore));

        Map<String, double[]> topicAgg = new HashMap<>();
        for (InterviewSession s : finished) {
            double[] agg = topicAgg.computeIfAbsent(s.getTopic(), k -> new double[]{0, 0});
            agg[0] += s.getTotalScore() == null ? 0 : s.getTotalScore();
            agg[1] += 1;
        }

        Map<String, Integer> topicMastery = new HashMap<>();
        topicAgg.forEach((topic, agg) -> {
            int avg = agg[1] == 0 ? 0 : (int) Math.round(agg[0] / agg[1] / 40.0 * 100);
            topicMastery.put(topic, avg);
        });

        Map<Long, List<KnowledgePoint>> childrenByParent = new LinkedHashMap<>();
        Map<Long, KnowledgePoint> roots = new LinkedHashMap<>();
        for (KnowledgePoint p : points) {
            if (p.getParentId() == null) {
                roots.put(p.getId(), p);
            } else {
                childrenByParent.computeIfAbsent(p.getParentId(), k -> new ArrayList<>()).add(p);
            }
        }

        List<KnowledgeMapVO.NodeVO> nodes = new ArrayList<>();
        List<KnowledgeMapVO.LinkVO> links = new ArrayList<>();

        for (KnowledgePoint root : roots.values()) {
            int rootMastery = topicMastery.getOrDefault(root.getTopic(), 0);
            nodes.add(new KnowledgeMapVO.NodeVO(
                    "t-" + root.getId(), root.getName(), "topic", rootMastery, 1));

            List<KnowledgePoint> children = childrenByParent.get(root.getId());
            if (children == null) continue;
            for (KnowledgePoint child : children) {
                int childMastery = Math.max(0, Math.min(100,
                        (int) Math.round(rootMastery * difficultyFactor(child.getDifficulty()))));
                nodes.add(new KnowledgeMapVO.NodeVO(
                        "p-" + child.getId(), child.getName(), "point", childMastery, child.getDifficulty()));
                links.add(new KnowledgeMapVO.LinkVO("t-" + root.getId(), "p-" + child.getId()));
            }
        }

        return new KnowledgeMapVO(nodes, links);
    }

    private double difficultyFactor(int difficulty) {
        return switch (difficulty) {
            case 1 -> 1.1;
            case 3 -> 0.85;
            default -> 1.0;
        };
    }
}