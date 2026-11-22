package com.score.controller;

import com.score.common.Result;
import com.score.dto.ExamRequest;
import com.score.entity.Exam;
import com.score.service.ExamService;
import com.score.vo.ExamSessionOptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @GetMapping
    public Result<List<Exam>> listByCourse(@RequestParam Long courseId) {
        return Result.ok(examService.listByCourse(courseId));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<List<ExamSessionOptionVO>> sessions() {
        return Result.ok(examService.sessions());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Exam> create(@Valid @RequestBody ExamRequest request) {
        return Result.ok(examService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Exam> update(@PathVariable Long id, @Valid @RequestBody ExamRequest request) {
        return Result.ok(examService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<Void> delete(@PathVariable Long id) {
        examService.delete(id);
        return Result.ok();
    }
}
