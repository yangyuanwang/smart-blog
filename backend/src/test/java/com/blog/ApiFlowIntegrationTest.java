package com.blog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.blog.entity.ArticleTag;
import com.blog.mapper.ArticleTagMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全链路集成测试：真实 MySQL + Redis 容器内验证认证、权限边界、发文与标签关联、点赞去重。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class ApiFlowIntegrationTest {

    static {
        // 国内网络拉取 ryuk 守护镜像不稳定，本地开发测试禁用（容器由 JVM 生命周期管理）
        System.setProperty("ryuk.disabled", "true");
    }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("blog").withUsername("root").withPassword("root");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /**
     * 把容器连接信息注入 Spring 数据源与 Redis 配置。
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.flyway.clean-disabled", () -> "true");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ArticleTagMapper articleTagMapper;

    @Autowired
    private com.blog.mapper.UserMapper userMapper;

    @Autowired
    private com.blog.mapper.CategoryMapper categoryMapper;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /**
     * 确保种子用户存在（不存在则创建并按需授予管理员角色）。
     *
     * @param username 用户名
     * @param password 明文密码
     * @param role     角色
     */
    private void ensureUser(String username, String password, String role) {
        Long count = userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.blog.entity.User>()
                .eq("username", username));
        if (count == null || count == 0) {
            com.blog.entity.User user = new com.blog.entity.User();
            user.setUsername(username);
            user.setNickname(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(role);
            user.setState("1");
            user.setLoginCount(0);
            userMapper.insert(user);
        }
    }

    /**
     * 确保种子栏目存在并返回其 ID。
     *
     * @return 栏目 ID
     */
    private int ensureCategory() {
        Long count = categoryMapper.selectCount(null);
        if (count == null || count == 0) {
            com.blog.entity.Category category = new com.blog.entity.Category();
            category.setCname("编程");
            categoryMapper.insert(category);
            return category.getCid();
        }
        return categoryMapper.selectList(null).get(0).getCid();
    }

    /**
     * 构造 JSON 请求头。
     *
     * @param token 可选 Bearer 令牌
     * @return 请求头
     */
    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    /**
     * 登录并返回 accessToken。
     *
     * @param username 用户名
     * @param password 密码
     * @param admin    是否走管理员登录端点
     * @return 访问令牌
     */
    private String login(String username, String password, boolean admin) {
        String path = admin ? "/api/v1/auth/admin-login" : "/api/v1/auth/login";
        ResponseEntity<Map> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", password), headers(null)), Map.class);
        assertEquals(200, response.getStatusCode().value(), "登录应成功");
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        return String.valueOf(data.get("accessToken"));
    }

    /**
     * 管理员发文 -> 前台可见 -> 标签关联表同步。
     */
    @SuppressWarnings("unchecked")
    @Test
    public void adminPublishesArticleWithTagSync() {
        ensureUser("admin", "admin123", "0");
        int cid = ensureCategory();
        String token = login("admin", "admin123", true);
        Map<String, Object> form = Map.of(
                "title", "集成测试文章",
                "content", "# 集成测试\n\n正文内容。",
                "cid", cid,
                "tagNames", "测试, 集成",
                "publishStatus", "1");
        ResponseEntity<Map> created = rest.exchange("/api/v1/admin/articles", HttpMethod.POST,
                new HttpEntity<>(form, headers(token)), Map.class);
        assertEquals(0, created.getBody().get("code"), "发文应成功");
        double aid = ((Number) ((Map<String, Object>) created.getBody().get("data")).get("aid")).doubleValue();

        // 前台可见
        ResponseEntity<Map> detail = rest.exchange("/api/v1/articles/" + (int) aid, HttpMethod.GET,
                new HttpEntity<>(headers(null)), Map.class);
        assertEquals(0, detail.getBody().get("code"));
        Map<String, Object> data = (Map<String, Object>) detail.getBody().get("data");
        assertNotNull(data.get("html"), "详情应返回渲染 HTML");
        assertTrue(String.valueOf(data.get("html")).contains("toc-"), "TOC 锚点应生成");

        // 标签关联落库
        List<ArticleTag> relations = articleTagMapper.selectList(
                new QueryWrapper<ArticleTag>().eq("article_id", (int) aid));
        assertEquals(2, relations.size(), "两个标签应写入关联表");
    }

    /**
     * 权限边界：匿名访问管理端 403/401，普通用户 403，管理员 200。
     */
    @Test
    public void adminEndpointsEnforceRole() {
        ensureUser("admin", "admin123", "0");
        ensureUser("user", "user123", "1");
        ResponseEntity<Map> anonymous = rest.exchange("/api/v1/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(headers(null)), Map.class);
        assertTrue(anonymous.getStatusCode().value() == 401 || anonymous.getStatusCode().value() == 403,
                "匿名访问管理端应被拒绝");

        ResponseEntity<Map> normal = rest.exchange("/api/v1/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(headers(login("user", "user123", false))), Map.class);
        assertEquals(403, normal.getStatusCode().value(), "普通用户访问管理端应 403");

        String adminToken = login("admin", "admin123", true);
        ResponseEntity<Map> admin = rest.exchange("/api/v1/admin/dashboard", HttpMethod.GET,
                new HttpEntity<>(headers(adminToken)), Map.class);
        assertEquals(200, admin.getStatusCode().value(), "管理员可访问仪表盘");
        assertEquals(0, admin.getBody().get("code"));
    }

    /**
     * 点赞去重：同一浏览器会话第二次点赞不增加计数。
     */
    @SuppressWarnings("unchecked")
    @Test
    public void likeDedupePerSession() {
        ensureUser("admin", "admin123", "0");
        int cid = ensureCategory();
        String token = login("admin", "admin123", true);
        Map<String, Object> form = Map.of("title", "点赞去重文章", "content", "body", "cid", cid,
                "tagNames", "", "publishStatus", "1");
        ResponseEntity<Map> created = rest.exchange("/api/v1/admin/articles", HttpMethod.POST,
                new HttpEntity<>(form, headers(token)), Map.class);
        int aid = ((Number) ((Map<String, Object>) created.getBody().get("data")).get("aid")).intValue();

        ResponseEntity<Map> first = rest.exchange("/api/v1/articles/" + aid + "/like", HttpMethod.POST,
                new HttpEntity<>(Map.of("sessionId", "sess-A"), headers(null)), Map.class);
        assertEquals(Boolean.TRUE, ((Map<String, Object>) first.getBody().get("data")).get("first"));

        ResponseEntity<Map> again = rest.exchange("/api/v1/articles/" + aid + "/like", HttpMethod.POST,
                new HttpEntity<>(Map.of("sessionId", "sess-A"), headers(null)), Map.class);
        assertEquals(Boolean.FALSE, ((Map<String, Object>) again.getBody().get("data")).get("first"));

        ResponseEntity<Map> other = rest.exchange("/api/v1/articles/" + aid + "/like", HttpMethod.POST,
                new HttpEntity<>(Map.of("sessionId", "sess-B"), headers(null)), Map.class);
        assertEquals(Boolean.TRUE, ((Map<String, Object>) other.getBody().get("data")).get("first"));
    }

    /**
     * 未登录互动端点可用（留言受审核流约束），恶意超长内容被校验拦截。
     */
    @Test
    public void guestMessageValidation() {
        String longContent = "x".repeat(1200);
        ResponseEntity<Map> rejected = rest.exchange("/api/v1/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("nickname", "guest", "content", longContent), headers(null)), Map.class);
        assertNotEquals(0, ((Number) rejected.getBody().get("code")).intValue(), "超长留言应被拒绝");
        assertFalse(rejected.getBody().get("message") == null);
    }

    private static void assertNotEquals(int unexpected, int actual, String message) {
        org.junit.jupiter.api.Assertions.assertNotEquals(Integer.valueOf(unexpected), Integer.valueOf(actual), message);
    }
}
