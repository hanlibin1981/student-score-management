package com.score.controller;

import com.score.common.Result;
import com.score.service.AnalysisService;
import com.score.vo.CourseComparisonVO;
import com.score.vo.DistributionItemVO;
import com.score.vo.FailingStudentVO;
import com.score.vo.OverviewVO;
import com.score.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<OverviewVO> overview() {
        return Result.ok(analysisService.overview());
    }

    @GetMapping("/distribution")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<List<DistributionItemVO>> distribution(@RequestParam Long examId) {
        return Result.ok(analysisService.distribution(examId));
    }

    @GetMapping("/failing")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<List<FailingStudentVO>> failing(
            @RequestParam Long examId,
            @RequestParam(defaultValue = "60") BigDecimal threshold) {
        return Result.ok(analysisService.failing(examId, threshold));
    }

    @GetMapping("/course-comparison")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<List<CourseComparisonVO>> courseComparison() {
        return Result.ok(analysisService.courseComparison());
    }

    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(
            @RequestParam(required = false) Long studentId,
            @RequestParam Long courseId) {
        return Result.ok(analysisService.trend(studentId, courseId));
    }
}
