package com.blog.ai;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.blog.entity.Article;
import com.blog.mapper.ArticleMapper;
import com.blog.service.SmartService;

import lombok.extern.slf4j.Slf4j;

/**
 * 文章 AI 摘要服务：发布后异步生成 100~150 字摘要并落库。
 * 未配置 LLM、调用失败或超时均只记日志，绝不阻塞发布主流程（优雅降级）。
 */
@Slf4j
@Service
public class ArticleAiService {

    private static final String SYSTEM_PROMPT =
            "你是中文技术博客的编辑。根据文章内容写一段 100 到 150 字的摘要，"
            + "概括核心观点与收获，客观、平实，不要出现\"本文\"字样，不要使用 Markdown 格式。";

    private final ArticleMapper articleMapper;
    private final LlmClient llmClient;
    private final AiProperties aiProperties;
    private final SmartService smartService;

    /**
     * 构造 AI 摘要服务。
     *
     * @param articleMapper 文章 Mapper
     * @param llmClient     LLM 客户端
     * @param aiProperties  AI 配置
     * @param smartService  Markdown 工具（截取正文纯文本）
     */
    public ArticleAiService(ArticleMapper articleMapper, LlmClient llmClient,
                            AiProperties aiProperties, SmartService smartService) {
        this.articleMapper = articleMapper;
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
        this.smartService = smartService;
    }

    /**
     * 异步生成并保存文章摘要。
     *
     * @param articleId 文章 ID
     */
    @Async
    public void generateSummary(Integer articleId) {
        try {
            Article article = articleMapper.selectById(articleId);
            if (article == null || article.getContent() == null || article.getContent().isBlank()) {
                return;
            }
            if (!aiProperties.enabled()) {
                log.info("AI 未配置，跳过文章 {} 摘要生成", articleId);
                return;
            }
            String plain = smartService.digest(article.getContent());
            String input = "标题：" + article.getTitle() + "\n正文：" + truncate(plain, 2000);
            String summary = llmClient.complete(SYSTEM_PROMPT, input).content();
            if (summary == null || summary.isBlank()) {
                log.warn("文章 {} AI 摘要为空，跳过落库", articleId);
                return;
            }
            articleMapper.update(null, new UpdateWrapper<Article>()
                    .eq("aid", articleId)
                    .set("ai_summary", truncate(summary.trim(), 300))
                    .set("ai_generated_at", LocalDateTime.now()));
            log.info("文章 {} AI 摘要生成完成", articleId);
        } catch (Exception ex) {
            // 优雅降级：AI 失败不影响文章发布与展示
            log.warn("文章 {} AI 摘要生成失败：{}", articleId, ex.getMessage());
        }
    }

    /**
     * 截断文本到指定长度。
     *
     * @param text 原文
     * @param max  最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text == null ? "" : text;
        }
        return text.substring(0, max) + "…";
    }
}
