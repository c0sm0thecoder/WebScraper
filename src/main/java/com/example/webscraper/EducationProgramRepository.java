package com.example.webscraper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface EducationProgramRepository extends JpaRepository<EducationProgram, Long> {
    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE education_program", nativeQuery = true)
    void truncateTable();
}