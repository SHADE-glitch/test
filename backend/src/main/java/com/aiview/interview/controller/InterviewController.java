package com.aiview.interview.controller;

import com.aiview.common.BizException;
import com.aiview.common.Result;
import com.aiview.common.ResultCode;
import com.aiview.common.UserContext;
import com.aiview.interview.dto.CreateInterviewRequest;
import com.aiview.interview.dto.InterviewResultVO;
import com.aiview.interview.dto.InterviewSessionVO;
import com.aiview.interview.dto.MessageVO;
import com.aiview.interview.dto.TopicVO;
import com.aiview.interview.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping("/topics")
    public Result<List<TopicVO>> topics() {
        return Result.ok(interviewService.listTopics());
    }

    @PostMapping("/interviews")
    public Result<InterviewSessionVO> create(@Valid @RequestBody CreateInterviewRequest request) {
        return Result.ok(interviewService.create(UserContext.currentUserId(), request));
    }

    @GetMapping("/interviews")
    public Result<List<InterviewSessionVO>> list() {
        return Result.ok(interviewService.listByUser(UserContext.currentUserId()));
    }

    @GetMapping("/interviews/{id}")
    public Result<InterviewSessionVO> detail(@PathVariable Long id) {
        return Result.ok(interviewService.detail(UserContext.currentUserId(), id));
    }

    @GetMapping("/interviews/{id}/result")
    public Result<InterviewResultVO> result(@PathVariable Long id) {
        return Result.ok(interviewService.result(UserContext.currentUserId(), id));
    }

    @PostMapping("/interviews/{id}/answer")
    public Result<MessageVO> answer(@PathVariable Long id,
                                    @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "答案不能为空");
        }
        return Result.ok(interviewService.answer(UserContext.currentUserId(), id, content));
    }

    @PostMapping(value = "/interviews/{id}/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answerStream(@PathVariable Long id,
                                   @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "答案不能为空");
        }
        return interviewService.answerStream(UserContext.currentUserId(), id, content);
    }
}