package com.blog.service;

import com.blog.common.Fmt;
import com.blog.common.Texts;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SmartService {

    private static final Set<String> STOP = new HashSet<String>(Arrays.asList(
            "一个", "我们", "你们", "他们", "这个", "那个", "什么", "怎么", "因为", "所以",
            "如果", "但是", "而且", "或者", "已经", "没有", "不是", "还是", "可以", "自己",
            "以及", "进行", "通过", "需要", "就是", "还要", "然后", "这样", "这些", "那些",
            "时候", "现在", "今天", "文章", "内容", "问题", "方法", "一下", "一种", "不会",
            "可能", "比较", "非常", "其实", "当然", "虽然", "于是", "为了", "还有", "只是"
    ));

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Fmt fmt;

    public SmartService(Fmt fmt) {
        this.fmt = fmt;
        List<Extension> extensions = Collections.singletonList(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true).build();
    }

    public String render(String markdown) {
        String source = markdown == null ? "" : markdown;
        Node document = parser.parse(source);
        return renderer.render(document);
    }

    public String digest(String content) {
        String plain = plain(content);
        if (plain.length() <= 140) {
            return plain;
        }
        return plain.substring(0, 140) + "…";
    }

    public int minutes(String content) {
        int length = plain(content).length();
        return Math.max(1, (length + 399) / 400);
    }

    public List<String> keywords(String title, String content, String tagNames, List<String> dictionary) {
        String text = (title == null ? "" : title) + "\n" + (content == null ? "" : content);
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        if (dictionary != null) {
            List<String> dict = new ArrayList<String>(dictionary);
            Collections.sort(dict, new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return b.length() - a.length();
                }
            });
            for (String word : dict) {
                if (!Texts.blank(word) && text.contains(word)) {
                    result.add(word);
                }
            }
        }
        for (String tag : fmt.tags(tagNames)) {
            result.add(tag);
        }
        if (result.size() < 3) {
            for (String word : topGrams(text, 3)) {
                result.add(word);
                if (result.size() >= 4) {
                    break;
                }
            }
        }
        List<String> list = new ArrayList<String>(result);
        if (list.size() > 6) {
            return list.subList(0, 6);
        }
        return list;
    }

    public int overlap(String tagNames, Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (String tag : fmt.tags(tagNames)) {
            if (tags.contains(tag)) {
                score++;
            }
        }
        return score;
    }

    private List<String> topGrams(String text, int limit) {
        Map<String, Integer> freq = new HashMap<String, Integer>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isHan(ch)) {
                run.append(ch);
            } else if (run.length() > 0) {
                collect(run.toString(), freq);
                run.setLength(0);
            }
        }
        if (run.length() > 0) {
            collect(run.toString(), freq);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(freq.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        List<String> words = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : entries) {
            if (entry.getValue() < 2) {
                continue;
            }
            words.add(entry.getKey());
            if (words.size() >= limit) {
                break;
            }
        }
        return words;
    }

    private void collect(String run, Map<String, Integer> freq) {
        if (run.length() < 2) {
            return;
        }
        for (int i = 0; i + 2 <= run.length(); i++) {
            String word = run.substring(i, i + 2);
            if (STOP.contains(word)) {
                continue;
            }
            Integer count = freq.get(word);
            freq.put(word, count == null ? 1 : count + 1);
        }
    }

    private boolean isHan(char ch) {
        return ch >= 0x4E00 && ch <= 0x9FFF;
    }

    private String plain(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("\\[[^\\]]+\\]\\([^)]*\\)", " ")
                .replaceAll("[#>*_`|\\-\\[\\]()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
