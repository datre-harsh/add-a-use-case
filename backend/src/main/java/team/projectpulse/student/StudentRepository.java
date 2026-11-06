package team.projectpulse.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>, JpaSpecificationExecutor<Student> {

    // Find all students in a section
    List<Student> findBySectionSectionId(Integer sectionId);

    List<Student> findByTeamTeamId(Integer teamId);

    @Query("""
            select distinct student from Student student
            left join student.section.instructors instructor
            where student.id = :studentId
              and (instructor.id = :instructorId or student.section.course.courseAdmin.id = :instructorId)
            """)
    Optional<Student> findManageableStudentById(@Param("studentId") Integer studentId,
                                               @Param("instructorId") Integer instructorId);

    @Query("""
            select distinct student from Student student
            left join student.section.instructors instructor
            where student.id = :studentId and
              (student.id = :callerId or (:canManage = true and
                (instructor.id = :callerId or student.section.course.courseAdmin.id = :callerId)))
            """)
    Optional<Student> findReadableStudentById(@Param("studentId") Integer studentId,
                                             @Param("callerId") Integer callerId,
                                             @Param("canManage") boolean canManage);
}
