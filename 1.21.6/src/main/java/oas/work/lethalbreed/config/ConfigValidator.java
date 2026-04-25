package oas.work.lethalbreed.config;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ConfigValidator {
    public static boolean isValid(JsonObject json, Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!json.has(field.getName())) return false;
            
            Class<?> type = field.getType();
            if (isModelClass(type)) {
                if (!isValid(json.getAsJsonObject(field.getName()), type)) return false;
            }
        }
        return true;
    }

    private static boolean isModelClass(Class<?> type) {
        String name = type.getName();
        return name.startsWith("oas.work.lethalbreed.config.model.") || 
               name.equals("oas.work.lethalbreed.config.ModConfig");
    }
}




