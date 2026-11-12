package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.blog.common.Texts;
import com.blog.entity.Setting;
import com.blog.mapper.SettingMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SettingService {

    private final SettingMapper settingMapper;

    public SettingService(SettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    public Map<String, String> all() {
        Map<String, String> map = new HashMap<String, String>();
        for (Setting setting : settingMapper.selectList(null)) {
            map.put(setting.getSkey(), setting.getSvalue());
        }
        return map;
    }

    public String get(String key) {
        Setting setting = settingMapper.selectById(key);
        return setting == null ? null : setting.getSvalue();
    }

    public boolean enabled(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

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
    }

    public void putIfAbsent(String key, String value) {
        if (settingMapper.selectById(key) == null) {
            Setting setting = new Setting();
            setting.setSkey(key);
            setting.setSvalue(value);
            settingMapper.insert(setting);
        }
    }
}
