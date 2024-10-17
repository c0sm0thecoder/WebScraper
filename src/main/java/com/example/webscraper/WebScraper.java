package com.example.webscraper;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class WebScraper {

    private final ExecutorService executorService;
    private final EducationProgramRepository educationProgramRepository;

    @Autowired
    public WebScraper(EducationProgramRepository educationProgramRepository) {
        this.executorService = Executors.newFixedThreadPool(4);
        this.educationProgramRepository = educationProgramRepository;
    }

    public void startScraping() {
        String[] urls = {
                "https://www.educations.com/bachelors-degree",
                "https://www.educations.com/masters-degrees",
                "https://www.educations.com/mba",
                "https://www.educations.com/phd"
        };

        // Submit tasks for program listings scraping
        for (String url : urls) {
            executorService.submit(new ListProgramTask(url, this.educationProgramRepository));
        }

        executorService.shutdown(); // Shut down after all tasks have been submitted
        try {
            // Wait for all program list scraping tasks to finish
            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
                executorService.shutdownNow(); // Cancel currently executing tasks
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            executorService.shutdownNow(); // Re-cancel if current thread also interrupted
        }
    }

    private record ListProgramTask(String url,
                                   EducationProgramRepository educationProgramRepository) implements Runnable {

        @Override
        public void run() {
            WebDriver driver = null;
            try {
                driver = createWebDriver();
                int pageNumber = 1;
                int retries = 3;

                while (retries > 0) {
                    try {
                        // Step 1: Scrape and store the basic program data
                        List<Map<String, String>> programs = scrapePage(driver, url, pageNumber);
                        if (programs.isEmpty()) {
                            break;
                        }

                        // Step 2: Visit each program URL and scrape the tuition fee
                        for (Map<String, String> programData : programs) {
                            String tuitionFee = extractTuitionFee(driver, programData.get("programUrl"));
                            programData.put("tuitionFee", tuitionFee);

                            // Step 3: Create the EducationProgram entity and save it
                            saveProgramData(programData);
                        }

                        System.out.println("here");

                        if (!hasNextPage(driver, url, pageNumber)) {
                            break;
                        }

                        pageNumber++;
                        retries = 3; // Reset retries on successful processing
                        System.out.println("Going to page " + pageNumber + " for URL: " + url);
                    } catch (StaleElementReferenceException e) {
                        System.out.println("Stale element encountered. Retrying...");
                        retries--;
                        driver.navigate().refresh(); // Refresh page if retries are left
                    } catch (Exception e) {
                        System.err.println("Error processing page " + pageNumber + ": " + e.getMessage());
                        break;
                    }
                }

            } catch (Exception e) {
                System.err.println("Error occurred while scraping: " + url);
                e.printStackTrace();
            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }
        }

        private List<Map<String, String>> scrapePage(WebDriver driver, String baseUrl, int pageNumber) {
            String pageUrl = baseUrl + "?page=" + pageNumber;
            driver.get(pageUrl);

            WebDriverWait wait = new WebDriverWait(driver, Duration.of(20, ChronoUnit.SECONDS));
            List<WebElement> programElements;

            try {
                programElements = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("#results > li")));
            } catch (TimeoutException e) {
                System.err.println("No results found on page " + pageNumber + " for URL: " + baseUrl);
                return List.of(); // Return empty list if no results
            }

            // Step 1: Extract basic data and store it in a list of maps
            List<Map<String, String>> programs = new ArrayList<>();

            for (WebElement programElement : programElements) {
                try {
                    Map<String, String> programData = new HashMap<>();
                    programData.put("programUrl", extractProgramUrl(programElement, wait));
                    programData.put("university", extractUniversity(programElement, wait));
                    programData.put("programTitle", extractProgramTitle(programElement, wait));
                    programData.put("location", extractLocation(programElement, wait));

                    // Extract other details like degree, pace, etc.
                    programData.putAll(extractProgramDetails(programElement, wait));

                    programs.add(programData);

                } catch (StaleElementReferenceException e) {
                    System.out.println("Stale element encountered. Refreshing page and retrying...");
                    driver.navigate().refresh();
                    wait = new WebDriverWait(driver, Duration.of(20, ChronoUnit.SECONDS));
                    programElements = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("#results > li")));
                } catch (Exception e) {
                    System.err.println("Error processing program: " + e.getMessage());
                }
            }

            return programs;
        }

        // Step 2: Scrape the tuition fee by visiting the program URL
        private String extractTuitionFee(WebDriver driver, String programUrl) {
            driver.get(programUrl);
            WebDriverWait wait = new WebDriverWait(driver, Duration.of(20, ChronoUnit.SECONDS));

            try {
                return wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".max-w-40"))).getText();
            } catch (Exception e) {
                return "Not Available";
            }
        }

        // Step 3: Save the program data to the repository
        private void saveProgramData(Map<String, String> programData) {
            EducationProgram program = EducationProgram.builder()
                    .programTitle(programData.get("programTitle"))
                    .university(programData.get("university"))
                    .location(programData.get("location"))
                    .programUrl(programData.get("programUrl"))
                    .degree(programData.get("degree"))
                    .pace(programData.get("pace"))
                    .studyFormat(programData.get("studyFormat"))
                    .duration(programData.get("duration"))
                    .languages(programData.get("languages"))
                    .tuitionFee(programData.get("tuitionFee"))
                    .build();

            educationProgramRepository.save(program);
        }

        private String extractProgramUrl(WebElement programElement, WebDriverWait wait) {
            return wait.until(ExpectedConditions.presenceOfNestedElementLocatedBy(programElement, By.cssSelector("a.block.cursor-pointer")))
                    .getAttribute("href");
        }

        private String extractUniversity(WebElement programElement, WebDriverWait wait) {
            return wait.until(ExpectedConditions.presenceOfNestedElementLocatedBy(programElement, By.cssSelector("p.sm.mb-2.md\\:mr-16")))
                    .getText();
        }

        private String extractProgramTitle(WebElement programElement, WebDriverWait wait) {
            return wait.until(ExpectedConditions.presenceOfNestedElementLocatedBy(programElement, By.tagName("h3")))
                    .getText();
        }

        private String extractLocation(WebElement programElement, WebDriverWait wait) {
            return wait.until(ExpectedConditions.presenceOfNestedElementLocatedBy(programElement, By.className("location")))
                    .findElement(By.tagName("li")).getText();
        }

        private Map<String, String> extractProgramDetails(WebElement programElement, WebDriverWait wait) {
            Map<String, String> info = new HashMap<>();
            WebElement programInfoDiv = wait.until(ExpectedConditions.presenceOfNestedElementLocatedBy(programElement, By.className("program-info")));
            List<WebElement> infoItems = programInfoDiv.findElements(By.className("info"));

            for (WebElement infoItem : infoItems) {
                WebElement divElement = infoItem.findElement(By.tagName("div"));
                String ariaLabel = divElement.getAttribute("aria-label");
                String value = infoItem.findElement(By.tagName("p")).getText();

                switch (ariaLabel) {
                    case "Degree type" -> info.put("degree", value);
                    case "Study pace" -> info.put("pace", value);
                    case "Duration" -> info.put("duration", value);
                    case "Study format" -> info.put("studyFormat", value);
                    case "Language" -> info.put("languages", value);
                }
            }

            return info;
        }

        private boolean hasNextPage(WebDriver driver, String baseUrl, int pageNumber) {
            // Navigate back to the listing page
            String pageUrl = baseUrl + "?page=" + pageNumber;
            driver.get(pageUrl);

            // add wait logic to List<WebElement> elements = driver.findElements(By.cssSelector("a.flex:nth-child(9)"));
            WebDriverWait wait = new WebDriverWait(driver, Duration.of(20, ChronoUnit.SECONDS));
            List<WebElement> elements = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("a.flex:nth-child(9)")));
            System.out.println("Next page: " + elements.isEmpty() + " url: " + elements.get(0).getAttribute("href"));
//
//            // If the next page link is available, navigate to it
//            if (!elements.isEmpty()) {
//                WebElement nextPageLink = elements.get(0);
//                String nextPageUrl = nextPageLink.getAttribute("href");
//                driver.get(nextPageUrl);
//            }


            return !elements.isEmpty();
        }

        public static WebDriver createWebDriver() {
            WebDriverManager.chromedriver().driverVersion("129.0.6668.58").setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
//            options.addArguments("--disable-gpu");
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            return new ChromeDriver(options);
        }
    }
}