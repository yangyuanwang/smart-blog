package com.blog.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.blog.common.Texts;
import com.blog.entity.Setting;
import com.blog.mapper.SettingMapper;

/**
 * 站点设置服务：KV 读写 + Redis 缓存（保存即失效，前台 5 秒内可见）。
 */
@Service
public class SettingService {

    /** 全量设置缓存 key */
    private static final String CACHE_KEY = "blog:setting:all";

    private final SettingMapper settingMapper;
    private final StringRedisTemplate redis;

    /**
     * 构造设置服务。
     *
     * @param settingMapper 设置 Mapper
     * @param redis         Redis 客户端
     */
    public SettingService(SettingMapper settingMapper, StringRedisTemplate redis) {
        this.settingMapper = settingMapper;
        this.redis = redis;
    }

    /**
     * 读取全部设置（Redis 缓存优先，未命中回源并回填）。
     *
     * @return 键值对
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, String> all() {
        Object cached = redis.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(String.valueOf(cached), HashMap.class);
            } catch (Exception ex) {
                // 缓存损坏时回源重建
                redis.delete(CACHE_KEY);
            }
        }
        Map<String, String> map = new HashMap<String, String>();
        for (Setting setting : settingMapper.selectList(null)) {
            map.put(setting.getSkey(), setting.getSvalue());
        }
        persistCache(map);
        return map;
    }

    /**
     * 读取单个设置。
     *
     * @param key 设置键
     * @return 设置值；不存在返回 null
     */
    public String get(String key) {
        return all().get(key);
    }

    /**
     * 开关判断（"1"/"true" 视为开启）。
     *
     * @param key          设置键
     * @param defaultValue 默认值
     * @return 是否开启
     */
    public boolean enabled(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    /**
     * 分页大小（1~50，默认 6）。
     *
     * @return 每页条数
     */
    public int pageSize() {
        try {
            int size = Integer.parseInt(Texts.trim(get("page_size")));
            if (size < 1 || size > 50) {
                return 6;
            }
            return size;
        } catch (Exception ex) {
            return 6;
        }
    }

    /**
     * 批量保存设置并失效缓存。
     *
     * @param values 键值对
     */
    public void save(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Setting existing = settingMapper.selectById(entry.getKey());
            if (existing == null) {
                Setting setting = new Setting();
                setting.setSkey(entry.getKey());
                setting.setSvalue(entry.getValue());
                settingMapper.insert(setting);
            } else {
                settingMapper.update(null, new UpdateWrapper<Setting>()
                        .eq("skey", entry.getKey())
                        .set("svalue", entry.getValue()));
            }
        }
        redis.delete(CACHE_KEY);
    }

    /**
     * 不存在才写入（种子场景，写后失效缓存）。
     *
     * @param key   设置键
     * @param value 设置值
     */
    public void putIfAbsent(String key, String value) {
        if (settingMapper.selectById(key) == null) {
            Setting setting = new Setting();
            setting.setSkey(key);
            setting.setSvalue(value);
            settingMapper.insert(setting);
            redis.delete(CACHE_KEY);
        }
    }

    /**
     * 将设置写入 Redis 缓存（JSON 序列化，失败仅记日志不影响读链路）。
     *
     * @param map 设置键值
     */
    private void persistCache(Map<String, String> map) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
            redis.opsForValue().set(CACHE_KEY, json, java.time.Duration.ofMinutes(10));
        } catch (Exception ignored) {
            // 缓存写入失败不影响业务正确性
        }
    }
}
