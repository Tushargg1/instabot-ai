package com.example.instagramreelai.ai;

import com.example.instagramreelai.dto.AiSummaryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Primary
@Profile("dev")
public class MockSummaryService implements SummaryService {

    @Override
    public AiSummaryResult summarize(String transcript) {
        log.info("[MOCK] Summarizing transcript of length={}", transcript.length());

        AiSummaryResult result = new AiSummaryResult();
        result.setTitle("Productivity Tips: Time Blocking & Deep Work");
        result.setSummary("The speaker shares productivity techniques including time blocking " +
                "and the Pomodoro technique. They emphasize the importance of deep work sessions " +
                "for complex tasks and recommend eliminating distractions during focused periods. " +
                "The book Deep Work by Cal Newport is recommended as further reading.");
        result.setKeyPoints(List.of(
                "Time blocking improves focus and productivity",
                "Pomodoro technique: 25 min work, 5 min break",
                "Deep work requires eliminating all distractions"
        ));
        result.setActionItems(List.of(
                "Try time blocking for one week",
                "Read Deep Work by Cal Newport"
        ));
        result.setTopics(List.of("productivity", "time management", "deep work"));
        result.setToolsOrProducts(List.of("Pomodoro timer", "Deep Work (book)"));
        result.setPeopleOrCompanies(List.of("Cal Newport"));
        result.setClaimsToVerify(List.of(
                "Time blocking increases productivity by 40%"
        ));
        result.setLanguage("en");
        result.setConfidence(0.85);

        return result;
    }
}
