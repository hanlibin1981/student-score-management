package com.score.controller;

import com.score.common.Result;
import com.score.dto.ScoreBatchRequest;
import com.score.service.ScoreService;
import com.score.vo.ExamScoreRowVO;
import com.score.vo.MyCourseVO;
import com.score.vo.SessionScoreVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<List<ExamScoreRowVO>> listByExam(
            @RequestParam Long examId,
            @RequestParam(required = false) String className) {
        return Result.ok(scoreService.listByExam(examId, className));
    }

    @GetMapping("/session")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<SessionScoreVO> sessionScores(
            @RequestParam String examName,
            @RequestParam(required = false) String className) {
        return Result.ok(scoreService.sessionScores(examName, className));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Integer> saveBatch(@Valid @RequestBody ScoreBatchRequest request) {
        return Result.ok(scoreService.saveBatch(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Void> delete(@PathVariable Long id) {
        scoreService.delete(id);
        return Result.ok();
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<MyCourseVO>> myCourses() {
        return Result.ok(scoreService.myCourses());
    }
}
