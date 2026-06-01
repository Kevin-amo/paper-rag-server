package com.lqr.paperragserver.agent.paper;

import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LiteratureFollowUpPolicy {

    private final LiteratureContextPolicy contextPolicy;

    public boolean matches(String question, LiteratureSearchContext context) {
        return contextPolicy.isFollowUp(question, context);
    }

    public void applyTo(Map<String, Object> input, String question, LiteratureSearchContext context) {
        contextPolicy.applySearchHints(input, question, context);
    }
}