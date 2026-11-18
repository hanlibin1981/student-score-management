package com.score.controller;

import com.score.common.PageResult;
import com.score.common.Result;
import com.score.dto.StudentRequest;
import com.score.service.StudentService;
import com.score.vo.StudentVO;
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

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<PageResult<StudentVO>> page(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String className,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(studentService.page(keyword, className, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public Result<StudentVO> get(@PathVariable Long id) {
        return Result.ok(studentService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<StudentVO> create(@Valid @RequestBody StudentRequest request) {
        return Result.ok(studentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<StudentVO> update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return Result.ok(studentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/account")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<StudentVO> openAccount(@PathVariable Long id) {
        return Result.ok(studentService.openAccount(id));
    }
}
