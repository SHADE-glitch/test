package com.aiview.interview.controller;

import com.aiview.common.Result;
import com.aiview.common.UserContext;
import com.aiview.interview.dto.DashboardVO;
import com.aiview.interview.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public Result<DashboardVO> summary() {
        return Result.ok(dashboardService.build(UserContext.currentUserId()));
    }
}