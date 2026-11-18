package com.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.score.entity.Score;
import com.score.vo.CourseComparisonVO;
import com.score.vo.DistributionItemVO;
import com.score.vo.ExamScoreRowVO;
import com.score.vo.FailingStudentVO;
import com.score.vo.OverallStatsVO;
import com.score.vo.StudentCourseTotalVO;
import com.score.vo.StudentTotalVO;
import com.score.vo.TrendPointVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ScoreMapper extends BaseMapper<Score> {

    List<ExamScoreRowVO> selectExamScoreRows(@Param("examId") Long examId,
                                             @Param("className") String className);

    List<StudentTotalVO> selectCourseTotals(@Param("courseId") Long courseId);

    List<StudentCourseTotalVO> selectStudentCourseTotals(@Param("studentId") Long studentId);

    List<DistributionItemVO> selectDistribution(@Param("examId") Long examId);

    List<FailingStudentVO> selectFailing(@Param("examId") Long examId,
                                         @Param("threshold") BigDecimal threshold);

    OverallStatsVO selectOverallStats(@Param("teacherId") Long teacherId);

    List<CourseComparisonVO> selectCourseComparison(@Param("teacherId") Long teacherId);

    List<TrendPointVO> selectTrend(@Param("studentId") Long studentId,
                                   @Param("courseId") Long courseId);
}
