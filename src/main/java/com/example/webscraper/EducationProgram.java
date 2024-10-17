package com.example.webscraper;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "education_program")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class EducationProgram {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "program_title")
    private String programTitle;

    @Column(name = "degree")
    private String degree;

    @Column(name = "university")
    private String university;

    @Column(name = "location")
    private String location;

    @Column(name = "pace")
    private String pace;

    @Column(name = "study_format")
    private String studyFormat;

    @Column(name = "duration")
    private String duration;

    @Column(name = "languages")
    private String languages;

    @Column(name = "tuition_fee")
    private String tuitionFee;

    @Column(name = "program_url")
    private String programUrl;
}