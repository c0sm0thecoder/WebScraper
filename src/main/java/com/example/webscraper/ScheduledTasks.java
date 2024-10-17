package com.example.webscraper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private final WebScraper webScraper;
    private final EducationProgramRepository repository;

    @Autowired
    public ScheduledTasks(WebScraper webScraper, EducationProgramRepository repository) {
        this.webScraper = webScraper;
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 02 1 * ?")
    public void scheduledScraping() {
        repository.truncateTable();
        webScraper.startScraping();
    }
}