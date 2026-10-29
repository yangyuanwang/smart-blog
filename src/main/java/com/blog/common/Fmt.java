package com.blog.common;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component("fmt")
public class Fmt {

    public String day(String time) {
        if (time == null || time.length() < 10) {
            return "--";
        }
        return time.substring(8, 10);
    }

    public String month(String time) {
        if (time == null || time.length() < 7) {
            return "";
        }
        return String.valueOf(Integer.parseInt(time.substring(5, 7)));
    }

    public String year(String time) {
        if (time == null || time.length() < 4) {
            return "";
        }
        return time.substring(0, 4);
    }

    public boolean logoOk(String logo) {
        if (Texts.blank(logo)) {
            return false;
        }
        String value = logo.toLowerCase();
        return !value.contains("localhost") && !logo.contains("\\");
    }

    public String blurb(String name) {
        if (name == null) {
            return "这个栏目下的文章";
        }
        if ("运动".equals(name)) {
            return "户外、球类，以及想动一动的日子";
        }
        if ("旅游".equals(name)) {
            return "路线、见闻，和还没去成的地方";
        }
        if ("购物".equals(name)) {
            return "用过、想买、值得记一笔的东西";
        }
        if ("编程".equals(name)) {
            return "学习路线、代码和踩过的坑";
        }
        return "这个栏目下的文章";
    }

    public List<String> tags(String tagNames) {
        if (Texts.blank(tagNames)) {
            return Collections.emptyList();
        }
        String text = tagNames.trim();
        if ("null".equalsIgnoreCase(text) || "[]".equals(text)) {
            return Collections.emptyList();
        }
        text = text.replace("[", "").replace("]", "");
        String[] parts = text.split("[,，]");
        java.util.ArrayList<String> list = new java.util.ArrayList<String>();
        for (String part : parts) {
            String item = part.trim();
            if (!item.isEmpty() && !"null".equalsIgnoreCase(item)) {
                list.add(item);
            }
        }
        return list;
    }

    public String roleName(String role, String username) {
        if ("admin".equalsIgnoreCase(username) || "0".equals(role)) {
            return "管理员";
        }
        return "普通用户";
    }

    public String stateName(String state) {
        return "0".equals(state) ? "已锁定" : "正常";
    }

    public List<String> moods() {
        return Arrays.asList("随记", "开工", "想走", "读完", "高兴", "发呆");
    }
}
