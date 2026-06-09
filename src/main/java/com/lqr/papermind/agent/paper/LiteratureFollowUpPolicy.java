package com.lqr.papermind.agent.paper;

import com.lqr.papermind.literature.model.LiteratureSearchContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LiteratureFollowUpPolicy {

    private final LiteratureContextPolicy contextPolicy;

    /**
     * 判断当前问题是否命中文献搜索追问规则。
     *
     * @param question 用户当前问题
     * @param context  最近一次文献搜索上下文
     * @return 是否匹配追问规则
     */
    public boolean matches(String question, LiteratureSearchContext context) {
        return contextPolicy.isFollowUp(question, context);
    }

    /**
     * 将追问语义合并到文献搜索工具输入中。
     *
     * @param input    待补全的工具输入参数
     * @param question 用户当前问题
     * @param context  最近一次文献搜索上下文
     */
    public void applyTo(Map<String, Object> input, String question, LiteratureSearchContext context) {
        contextPolicy.applySearchHints(input, question, context);
    }
}