package com.example.ragchat.mcp;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AIMCP {

    private final ChatLanguageModel chatModel;

    public AIMCP(@Value("${deepseek.api-key}") String apiKey,
                 @Value("${deepseek.base-url}") String baseUrl,
                 @Value("${deepseek.model}") String model) {
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .model(model)
                .build();
    }

    @CircuitBreaker(name = "llm-api", fallbackMethod = "fallbackGenerate")
    @Retry(name = "llm-api")
    @RateLimiter(name = "llm-api")
    public String generate(String prompt) {
        try {
            log.debug("Calling LLM with prompt (first 100 chars): {}", 
                    prompt.length() > 100 ? prompt.substring(0, 100) + "..." : prompt);
            String response = chatModel.generate(prompt);
            log.debug("LLM response received (first 100 chars): {}", 
                    response.length() > 100 ? response.substring(0, 100) + "..." : response);
            return response;
        } catch (Exception e) {
            log.error("LLM call failed", e);
            throw e;
        }
    }

    @CircuitBreaker(name = "llm-api", fallbackMethod = "fallbackGenerate")
    @Retry(name = "llm-api")
    @RateLimiter(name = "llm-api")
    public String generateWithContext(String context, String question) {
        String prompt = buildRagPrompt(context, question);
        return generate(prompt);
    }

    private String buildRagPrompt(String context, String question) {
        return """
                根据以下上下文信息回答问题：
                
                上下文：
                %s
                
                问题：%s
                
                请基于上述上下文给出准确的回答。如果上下文没有相关信息，请说明无法回答。
                """.formatted(context, question);
    }

    public String fallbackGenerate(String prompt, Throwable t) {
        log.warn("LLM service is unavailable, using fallback: {}", t.getMessage());
        return "抱歉，当前AI服务暂时不可用，请稍后重试。";
    }
}
