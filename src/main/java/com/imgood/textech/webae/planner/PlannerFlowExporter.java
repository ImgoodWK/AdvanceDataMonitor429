package com.imgood.textech.webae.planner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.craft.CraftTreeCalculator;
import com.imgood.textech.webae.craft.CraftTreeNodeDto;
import com.imgood.textech.webae.dto.RecipeDto;
import com.imgood.textech.webae.recipe.RecipeCacheStore;

/**
 * Builds Factory Flow / gtnh-flow compatible export JSON from craft-tree roots (Phase 4.3).
 */
public final class PlannerFlowExporter {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private PlannerFlowExporter() {}

    public static String export(String ownerUuid, int networkId, List<FlowRoot> roots, String format) {
        String fmt = format == null || format.trim()
            .isEmpty() ? "gtnh-flow-v1" : format.trim();
        if ("factory-flow-v1".equalsIgnoreCase(fmt)) {
            return exportFactoryFlow(ownerUuid, networkId, roots);
        }
        return exportGtnhFlow(ownerUuid, networkId, roots);
    }

    private static String exportGtnhFlow(String ownerUuid, int networkId, List<FlowRoot> roots) {
        Map<String, Object> doc = new HashMap<String, Object>();
        doc.put("format", "gtnh-flow-v1");
        doc.put("version", Integer.valueOf(1));
        doc.put("exportedAt", Long.valueOf(System.currentTimeMillis()));
        doc.put("source", "TeXTech-WebAE");
        List<Map<String, Object>> rootList = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> machines = new ArrayList<Map<String, Object>>();
        int nodeIndex = 0;
        for (FlowRoot root : roots) {
            if (root == null || root.itemId == null
                || root.itemId.trim()
                    .isEmpty()) {
                continue;
            }
            long amount = root.amount <= 0 ? 1L : root.amount;
            Map<String, Object> rootEntry = new HashMap<String, Object>();
            rootEntry.put("itemId", root.itemId.trim());
            rootEntry.put("amount", Long.valueOf(amount));
            rootList.add(rootEntry);
            CraftTreeNodeDto tree = CraftTreeCalculator.build(ownerUuid, networkId, root.itemId.trim(), amount, 8);
            if (tree != null) {
                flattenGtnhMachines(tree, machines, "root-" + nodeIndex, nodeIndex == 0);
            }
            nodeIndex++;
        }
        doc.put("roots", rootList);
        doc.put("machines", machines);
        return GSON.toJson(doc);
    }

    private static void flattenGtnhMachines(CraftTreeNodeDto node, List<Map<String, Object>> machines, String id,
        boolean isTarget) {
        if (node == null) {
            return;
        }
        Map<String, Object> machine = new HashMap<String, Object>();
        machine.put("id", id);
        machine
            .put("itemId", node.registryName != null && !node.registryName.isEmpty() ? node.registryName : node.itemId);
        machine.put("displayName", node.displayName);
        machine.put("required", Long.valueOf(node.required));
        machine.put("inStock", Long.valueOf(node.inStock > 0 ? node.inStock : node.available));
        machine.put("toCraft", Long.valueOf(node.toCraft > 0 ? node.toCraft : node.missing));
        if (node.recipeHandlerId != null && !node.recipeHandlerId.isEmpty()) {
            machine.put("recipeHandlerId", node.recipeHandlerId);
            machine.put("recipeIndex", Integer.valueOf(node.recipeIndex));
            RecipeDto recipe = findRecipe(node.recipeHandlerId, node.recipeIndex);
            if (recipe != null) {
                if (recipe.euPerTick != null) {
                    machine.put("eut", recipe.euPerTick);
                }
                if (recipe.durationTicks != null) {
                    machine.put("duration", recipe.durationTicks);
                }
                if (recipe.voltageTier != null) {
                    machine.put("voltageTier", recipe.voltageTier);
                }
                if (recipe.requiresCleanroom != null) {
                    machine.put("requiresCleanroom", recipe.requiresCleanroom);
                }
            }
        }
        if (isTarget && node.required > 0) {
            machine.put(
                "target",
                (node.registryName != null && !node.registryName.isEmpty() ? node.registryName : node.itemId) + ":"
                    + node.required);
        }
        List<Map<String, Object>> inputs = new ArrayList<Map<String, Object>>();
        if (node.children != null) {
            int childIdx = 0;
            for (CraftTreeNodeDto child : node.children) {
                if (child == null) {
                    continue;
                }
                Map<String, Object> in = new HashMap<String, Object>();
                in.put(
                    "itemId",
                    child.registryName != null && !child.registryName.isEmpty() ? child.registryName : child.itemId);
                in.put("amount", Long.valueOf(child.required));
                inputs.add(in);
                flattenGtnhMachines(child, machines, id + "-c" + childIdx, false);
                childIdx++;
            }
        }
        machine.put("inputs", inputs);
        machines.add(machine);
    }

    private static String exportFactoryFlow(String ownerUuid, int networkId, List<FlowRoot> roots) {
        Map<String, Object> doc = new HashMap<String, Object>();
        doc.put("format", "factory-flow-v1");
        doc.put("version", Integer.valueOf(1));
        doc.put("exportedAt", Long.valueOf(System.currentTimeMillis()));
        doc.put("source", "TeXTech-WebAE");
        List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> edges = new ArrayList<Map<String, Object>>();
        int x = 0;
        int y = 0;
        int edgeIdx = 0;
        for (FlowRoot root : roots) {
            if (root == null || root.itemId == null
                || root.itemId.trim()
                    .isEmpty()) {
                continue;
            }
            long amount = root.amount <= 0 ? 1L : root.amount;
            CraftTreeNodeDto tree = CraftTreeCalculator.build(ownerUuid, networkId, root.itemId.trim(), amount, 8);
            if (tree == null) {
                continue;
            }
            String rootId = "n-root-" + x;
            addFactoryNode(nodes, rootId, tree, x * 280, 0);
            if (tree.children != null) {
                int childY = 1;
                for (CraftTreeNodeDto child : tree.children) {
                    if (child == null) {
                        continue;
                    }
                    String childId = rootId + "-c" + childY;
                    addFactoryNode(nodes, childId, child, x * 280, childY * 120);
                    Map<String, Object> edge = new HashMap<String, Object>();
                    edge.put("id", "e" + edgeIdx);
                    edge.put("source", childId);
                    edge.put("target", rootId);
                    edge.put("sourceHandle", "output-0");
                    edge.put("targetHandle", "input-" + (childY - 1));
                    edges.add(edge);
                    edgeIdx++;
                    childY++;
                }
            }
            x++;
            y += 200;
        }
        doc.put("nodes", nodes);
        doc.put("edges", edges);
        return GSON.toJson(doc);
    }

    private static void addFactoryNode(List<Map<String, Object>> nodes, String id, CraftTreeNodeDto node, int px,
        int py) {
        Map<String, Object> n = new HashMap<String, Object>();
        n.put("id", id);
        n.put("type", "recipe");
        Map<String, Object> pos = new HashMap<String, Object>();
        pos.put("x", Integer.valueOf(px));
        pos.put("y", Integer.valueOf(py));
        n.put("position", pos);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("itemId", node.registryName != null && !node.registryName.isEmpty() ? node.registryName : node.itemId);
        data.put("displayName", node.displayName);
        data.put("amount", Long.valueOf(node.required));
        data.put("required", Long.valueOf(node.required));
        data.put("inStock", Long.valueOf(node.inStock > 0 ? node.inStock : node.available));
        data.put("toCraft", Long.valueOf(node.toCraft > 0 ? node.toCraft : node.missing));
        if (node.recipeHandlerId != null && !node.recipeHandlerId.isEmpty()) {
            data.put("recipeHandlerId", node.recipeHandlerId);
            data.put("recipeIndex", Integer.valueOf(node.recipeIndex));
        }
        n.put("data", data);
        nodes.add(n);
    }

    private static RecipeDto findRecipe(String handlerId, int recipeIndex) {
        if (handlerId == null || handlerId.isEmpty() || recipeIndex < 0) {
            return null;
        }
        RecipeCacheStore.instance()
            .ensureLoaded();
        return RecipeCacheStore.instance()
            .getRecipe(handlerId, recipeIndex);
    }

    public static final class FlowRoot {

        public String itemId = "";
        public long amount = 1L;
    }
}
