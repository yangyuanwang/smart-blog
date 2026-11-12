package com.blog;

import com.blog.common.BizException;
import com.blog.common.Fmt;
import com.blog.entity.Article;
import com.blog.service.ArticleService;
import com.blog.service.SmartService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArticleBatch1Test {

    @Test
    public void applyStatusWritesBothFields() {
        ArticleService service = new ArticleService(null, null, null, null, null, null, new Fmt());
        Article article = new Article();
        service.applyStatus(article, "0");
        assertEquals("0", article.getPublishStatus());
        assertEquals("0", article.getIsOpen());
        service.applyStatus(article, "1");
        assertEquals("1", article.getPublishStatus());
        assertEquals("1", article.getIsOpen());
        service.applyStatus(article, "2");
        assertEquals("2", article.getPublishStatus());
        assertEquals("0", article.getIsOpen());
    }

    @Test
    public void illegalStatusDoesNotChangeFields() {
        ArticleService service = new ArticleService(null, null, null, null, null, null, new Fmt());
        Article article = new Article();
        article.setPublishStatus("1");
        article.setIsOpen("1");
        assertThrows(BizException.class, () -> service.applyStatus(article, "9"));
        assertThrows(BizException.class, () -> service.applyStatus(article, null));
        assertEquals("1", article.getPublishStatus());
        assertEquals("1", article.getIsOpen());
    }

    @Test
    public void headingsGetUniqueAnchorsAndHtmlStaysEscaped() {
        SmartService smart = new SmartService(new Fmt());
        SmartService.Rendered rendered = smart.renderWithToc("# 相同\n\n# 相同\n\n###### 六级\n\n# <script>alert(1)</script>\n");
        assertEquals(4, rendered.getToc().size());
        assertEquals("toc-1", rendered.getToc().get(0).getId());
        assertEquals("toc-2", rendered.getToc().get(1).getId());
        assertEquals(6, rendered.getToc().get(2).getLevel());
        assertEquals("toc-4", rendered.getToc().get(3).getId());
        assertTrue(rendered.getToc().get(3).getText().contains("<script>"));
        assertTrue(rendered.getHtml().contains("id=\"toc-1\""));
        assertTrue(rendered.getHtml().contains("id=\"toc-2\""));
        assertTrue(rendered.getHtml().contains("id=\"toc-4\""));
        assertFalse(rendered.getHtml().contains("<script"));
        assertTrue(rendered.getHtml().contains("&lt;script&gt;"));
    }

    @Test
    public void noHeadingOmitsToc() {
        SmartService smart = new SmartService(new Fmt());
        SmartService.Rendered rendered = smart.renderWithToc("只有段落，没有标题。");
        assertTrue(rendered.getToc().isEmpty());
        assertFalse(rendered.getHtml().contains("id=\"toc-"));
    }
}
