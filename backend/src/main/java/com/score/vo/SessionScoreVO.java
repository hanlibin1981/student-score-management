package com.score.vo;

import java.util.List;

public record SessionScoreVO(String examName, List<SessionExamVO> exams,
                             List<SessionScoreRowVO> rows) {
}
