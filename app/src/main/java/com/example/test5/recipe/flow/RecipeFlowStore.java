package com.example.test5.recipe.flow;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 本地 JSON 存储自定义流程（按菜谱 Id 关联，不影响 DishsConfig.xml 格式）。 */
public final class RecipeFlowStore {

    private static final String FILE_NAME = "recipe_flows.json";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, RecipeFlow>>() {
    }.getType();

    private static volatile Map<String, RecipeFlow> cache;

    private RecipeFlowStore() {
    }

    public static synchronized RecipeFlow getByRecipeId(Context context, String recipeId) {
        ensureLoaded(context);
        RecipeFlow flow = cache.get(recipeId);
        return flow != null ? flow.copy() : null;
    }

    public static synchronized void save(Context context, RecipeFlow flow) {
        ensureLoaded(context);
        if (flow.recipeId == null || flow.recipeId.isEmpty()) {
            throw new IllegalArgumentException("recipeId 不能为空");
        }
        cache.put(flow.recipeId, flow.copy());
        persist(context.getApplicationContext());
    }

    public static synchronized void delete(Context context, String recipeId) {
        ensureLoaded(context);
        cache.remove(recipeId);
        persist(context.getApplicationContext());
    }

    public static synchronized boolean hasFlow(Context context, String recipeId) {
        ensureLoaded(context);
        return cache.containsKey(recipeId);
    }

    public static synchronized int stepCount(Context context, String recipeId) {
        RecipeFlow flow = getByRecipeId(context, recipeId);
        return flow != null ? flow.nodes.size() : 0;
    }

    public static synchronized void reload(Context context) {
        cache = null;
        ensureLoaded(context);
    }

    private static void ensureLoaded(Context context) {
        if (cache != null) {
            return;
        }
        File file = getFile(context.getApplicationContext());
        if (!file.exists()) {
            cache = new HashMap<>();
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            Map<String, RecipeFlow> loaded = GSON.fromJson(reader, MAP_TYPE);
            cache = loaded != null ? new HashMap<>(loaded) : new HashMap<>();
        } catch (Exception e) {
            cache = new HashMap<>();
        }
    }

    private static void persist(Context context) {
        File file = getFile(context);
        File temp = new File(file.getAbsolutePath() + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            GSON.toJson(cache, MAP_TYPE, writer);
        } catch (Exception e) {
            throw new IllegalStateException("保存流程失败", e);
        }
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("无法覆盖旧流程文件");
        }
        if (!temp.renameTo(file)) {
            throw new IllegalStateException("无法写入流程文件");
        }
    }

    private static File getFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    /** 按连线拓扑排序；无连线时按 Y 坐标。 */
    public static List<FlowNode> executionOrder(RecipeFlow flow) {
        if (flow.nodes.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, FlowNode> nodeMap = new HashMap<>();
        for (FlowNode node : flow.nodes) {
            nodeMap.put(node.id, node);
        }
        if (flow.edges.isEmpty()) {
            List<FlowNode> sorted = new ArrayList<>(flow.nodes);
            sorted.sort((a, b) -> {
                int cmp = Float.compare(a.y, b.y);
                return cmp != 0 ? cmp : Float.compare(a.x, b.x);
            });
            return sorted;
        }

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (FlowNode node : flow.nodes) {
            inDegree.put(node.id, 0);
            adjacency.put(node.id, new ArrayList<>());
        }
        for (FlowEdge edge : flow.edges) {
            if (!nodeMap.containsKey(edge.fromNodeId) || !nodeMap.containsKey(edge.toNodeId)) {
                continue;
            }
            adjacency.get(edge.fromNodeId).add(edge.toNodeId);
            inDegree.put(edge.toNodeId, inDegree.get(edge.toNodeId) + 1);
        }

        List<FlowNode> queue = new ArrayList<>();
        for (FlowNode node : flow.nodes) {
            if (inDegree.get(node.id) == 0) {
                queue.add(node);
            }
        }
        queue.sort((a, b) -> Float.compare(a.y, b.y));

        List<FlowNode> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            FlowNode current = queue.remove(0);
            result.add(current);
            for (String nextId : adjacency.get(current.id)) {
                int degree = inDegree.get(nextId) - 1;
                inDegree.put(nextId, degree);
                if (degree == 0) {
                    queue.add(nodeMap.get(nextId));
                }
            }
            queue.sort((a, b) -> Float.compare(a.y, b.y));
        }

        if (result.size() < flow.nodes.size()) {
            List<FlowNode> fallback = new ArrayList<>(flow.nodes);
            fallback.sort((a, b) -> Float.compare(a.y, b.y));
            return fallback;
        }
        return result;
    }
}
