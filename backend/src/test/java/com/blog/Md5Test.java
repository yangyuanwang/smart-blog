package com.blog;

import com.blog.common.Texts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Md5Test {

    @Test
    public void adminPasswordMatchesSeed() {
        assertEquals("21232f297a57a5a743894a0e4a801fc3", Texts.md5("admin"));
    }
}
