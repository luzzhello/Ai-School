package org.ruoyi.service.draw.impl;

import org.ruoyi.domain.dto.response.ErEdgeVo;
import org.ruoyi.domain.dto.response.ErNodeVo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 陈氏 ER 图布局 — 参照教材图 4.6：左(用户) · 中(业务实体) · 右(管理员) + 关系菱形
 */
final class ChenErLayoutBuilder {

    private static final double ENTITY_W = 100;
    private static final double ENTITY_H = 50;
    private static final double REL_SIZE = 44;
    private static final double ATTR_RX = 45;
    private static final double ATTR_RY = 15;

    private static final double LEFT_X = 80;
    private static final double MID_X = 400;
    private static final double RIGHT_X = 760;
    private static final double REL_LEFT_X = 220;
    private static final double REL_RIGHT_X = 620;
    private static final double START_Y = 90;
    private static final double MID_GAP_Y = Math.max(130, ENTITY_H + REL_SIZE + 40);
    private static final double SHAPE_MIN_GAP = 10;
    private static final double ATTR_RADIUS = 130;
    private static final double ATTR_GAP = 24;
    private static final double ATTR_ROW_GAP = 56;
    private static final double MAX_ATTR_ARC = Math.PI * 0.88;
    private static final double FAN_MID_ANGLE = Math.PI / 2;
    private static final double ATTR_FONT_SIZE = 12;
    private static final int TWO_ROW_MIN = 8;

    private ChenErLayoutBuilder() {
    }

    record EntityDef(String name, List<String> attributes) {
    }

    record RelationshipDef(String name, String entityA, String entityB, String cardA, String cardB) {
    }

    record ChenDiagram(List<ErNodeVo> nodes, List<ErEdgeVo> edges) {
    }

    static ChenDiagram build(List<EntityDef> entities, List<RelationshipDef> relationships) {
        List<ErNodeVo> nodes = new ArrayList<>();
        List<ErEdgeVo> edges = new ArrayList<>();
        Map<String, String> entityNameToId = new LinkedHashMap<>();
        Map<String, ErNodeVo> entityNodes = new LinkedHashMap<>();

        int entityIndex = 0;
        for (EntityDef entity : entities) {
            String entityId = "e_" + sanitize(entity.name()) + "_" + entityIndex++;
            entityNameToId.put(normalize(entity.name()), entityId);
            ErNodeVo entityNode = new ErNodeVo();
            entityNode.setId(entityId);
            entityNode.setLabel(entity.name());
            entityNode.setType("entity");
            entityNodes.put(entityId, entityNode);
            nodes.add(entityNode);
        }

        String leftId = detectLeftEntityId(entities, entityNameToId);
        String rightId = detectRightEntityId(entities, entityNameToId, leftId);
        List<String> middleIds = new ArrayList<>();
        for (String id : entityNodes.keySet()) {
            if (!id.equals(leftId) && !id.equals(rightId)) {
                middleIds.add(id);
            }
        }

        double centerY = START_Y + Math.max(0, middleIds.size() - 1) * MID_GAP_Y / 2.0;
        if (leftId != null) {
            placeEntity(entityNodes.get(leftId), LEFT_X, centerY);
        }
        if (rightId != null) {
            placeEntity(entityNodes.get(rightId), RIGHT_X, centerY);
        }
        for (int i = 0; i < middleIds.size(); i++) {
            placeEntity(entityNodes.get(middleIds.get(i)), MID_X, START_Y + i * MID_GAP_Y);
        }

        Map<String, Integer> pairStack = new HashMap<>();
        double bottomRelY = START_Y + middleIds.size() * MID_GAP_Y + 36;
        int relIndex = 0;

        for (RelationshipDef rel : relationships) {
            String aId = entityNameToId.get(normalize(rel.entityA()));
            String bId = entityNameToId.get(normalize(rel.entityB()));
            if (aId == null || bId == null) {
                continue;
            }

            String fromId = aId;
            String toId = bId;
            String cardFrom = blankToDefault(rel.cardA(), "1");
            String cardTo = blankToDefault(rel.cardB(), "n");

            double relX;
            double relY;

            if (leftId != null && rightId != null && isPair(fromId, toId, leftId, rightId)) {
                relX = (LEFT_X + RIGHT_X) / 2 - REL_SIZE / 2;
                relY = bottomRelY;
                bottomRelY += REL_SIZE + 20;
            }
            else if (leftId != null && involves(middleIds, toId) && fromId.equals(leftId)) {
                int stack = pairStack.merge(leftId + "|" + toId, 1, Integer::sum) - 1;
                relX = REL_LEFT_X - REL_SIZE / 2 - stack * (REL_SIZE + 10);
                relY = sideRelationshipY(entityNodes.get(toId).getY(), stack);
            }
            else if (leftId != null && involves(middleIds, fromId) && toId.equals(leftId)) {
                int stack = pairStack.merge(leftId + "|" + fromId, 1, Integer::sum) - 1;
                relX = REL_LEFT_X - REL_SIZE / 2 - stack * (REL_SIZE + 10);
                relY = sideRelationshipY(entityNodes.get(fromId).getY(), stack);
                fromId = bId;
                toId = aId;
                cardFrom = blankToDefault(rel.cardB(), "1");
                cardTo = blankToDefault(rel.cardA(), "n");
            }
            else if (rightId != null && involves(middleIds, fromId) && toId.equals(rightId)) {
                int stack = pairStack.merge(fromId + "|" + rightId, 1, Integer::sum) - 1;
                relX = REL_RIGHT_X - REL_SIZE / 2 + stack * (REL_SIZE + 10);
                relY = sideRelationshipY(entityNodes.get(fromId).getY(), stack);
            }
            else if (rightId != null && involves(middleIds, toId) && fromId.equals(rightId)) {
                int stack = pairStack.merge(toId + "|" + rightId, 1, Integer::sum) - 1;
                relX = REL_RIGHT_X - REL_SIZE / 2 + stack * (REL_SIZE + 10);
                relY = sideRelationshipY(entityNodes.get(toId).getY(), stack);
                fromId = bId;
                toId = aId;
                cardFrom = blankToDefault(rel.cardB(), "1");
                cardTo = blankToDefault(rel.cardA(), "n");
            }
            else {
                ErNodeVo fromNode = entityNodes.get(fromId);
                ErNodeVo toNode = entityNodes.get(toId);
                if (fromNode == null || toNode == null) {
                    continue;
                }
                boolean bothMiddle = involves(middleIds, fromId) && involves(middleIds, toId);
                String pairKey = fromId.compareTo(toId) <= 0 ? fromId + "|" + toId : toId + "|" + fromId;
                int stack = pairStack.merge(pairKey, 1, Integer::sum) - 1;
                if (bothMiddle) {
                    relX = MID_X + ENTITY_W / 2 - REL_SIZE / 2 + stack * (REL_SIZE + 12);
                }
                else {
                    relX = (fromNode.getX() + toNode.getX()) / 2 - REL_SIZE / 2 + stack * (REL_SIZE + 12);
                }
                relY = (rowCenterY(fromNode) + rowCenterY(toNode)) / 2 - REL_SIZE / 2 + stack * (REL_SIZE + 12);
            }

            String relId = "r_" + sanitize(rel.name()) + "_" + relIndex++;
            ErNodeVo relNode = new ErNodeVo();
            relNode.setId(relId);
            relNode.setLabel(rel.name());
            relNode.setType("relationship");
            relNode.setX(relX);
            relNode.setY(relY);
            nodes.add(relNode);

            edges.add(buildEdge("l_" + relId + "_a", fromId, relId, cardFrom));
            edges.add(buildEdge("l_" + relId + "_b", relId, toId, cardTo));
        }

        for (ErNodeVo entityNode : entityNodes.values()) {
            EntityDef def = findEntityDef(entities, entityNode.getLabel());
            if (def != null) {
                layoutAttributesArc(entityNode.getId(), entityNode.getX(), entityNode.getY(),
                    def.attributes(), nodes, edges);
            }
        }

        resolveEntityRelationshipOverlaps(nodes);

        return new ChenDiagram(nodes, edges);
    }

    private static double rowCenterY(ErNodeVo node) {
        return node.getY() + ENTITY_H / 2;
    }

    private static double sideRelationshipY(double entityY, int stack) {
        if (stack % 2 == 0) {
            return entityY - REL_SIZE - SHAPE_MIN_GAP;
        }
        return entityY + ENTITY_H + SHAPE_MIN_GAP;
    }

    private record Rect(double x, double y, double w, double h) {
    }

    private static boolean rectsOverlap(Rect a, Rect b, double gap) {
        return a.x < b.x + b.w + gap
            && a.x + a.w + gap > b.x
            && a.y < b.y + b.h + gap
            && a.y + a.h + gap > b.y;
    }

    private static void resolveEntityRelationshipOverlaps(List<ErNodeVo> nodes) {
        List<Rect> entityRects = new ArrayList<>();
        for (ErNodeVo node : nodes) {
            if ("entity".equals(node.getType())) {
                entityRects.add(new Rect(node.getX(), node.getY(), ENTITY_W, ENTITY_H));
            }
        }

        List<ErNodeVo> relNodes = new ArrayList<>();
        for (ErNodeVo node : nodes) {
            if ("relationship".equals(node.getType())) {
                relNodes.add(node);
            }
        }
        relNodes.sort((a, b) -> {
            int cmp = Double.compare(a.getY(), b.getY());
            return cmp != 0 ? cmp : Double.compare(a.getX(), b.getX());
        });

        List<Rect> placedRels = new ArrayList<>();
        for (ErNodeVo rel : relNodes) {
            double x = rel.getX();
            double y = rel.getY();
            for (int attempt = 0; attempt < 48; attempt++) {
                Rect rect = new Rect(x, y, REL_SIZE, REL_SIZE);
                boolean moved = false;
                for (Rect er : entityRects) {
                    if (rectsOverlap(rect, er, SHAPE_MIN_GAP)) {
                        double below = er.y + er.h + SHAPE_MIN_GAP;
                        double above = er.y - rect.h - SHAPE_MIN_GAP;
                        y = Math.abs(y - above) <= Math.abs(below - y) ? above : below;
                        rect = new Rect(x, y, REL_SIZE, REL_SIZE);
                        moved = true;
                    }
                }
                for (Rect pr : placedRels) {
                    if (rectsOverlap(rect, pr, SHAPE_MIN_GAP)) {
                        y = pr.y + pr.h + SHAPE_MIN_GAP;
                        rect = new Rect(x, y, REL_SIZE, REL_SIZE);
                        moved = true;
                    }
                }
                if (!moved) {
                    break;
                }
            }
            placedRels.add(new Rect(x, y, REL_SIZE, REL_SIZE));
            rel.setX(x);
            rel.setY(y);
        }
    }

    private static boolean involves(List<String> ids, String id) {
        return ids.contains(id);
    }

    private static boolean isPair(String a, String b, String left, String right) {
        return (a.equals(left) && b.equals(right)) || (a.equals(right) && b.equals(left));
    }

    private static String detectLeftEntityId(List<EntityDef> entities, Map<String, String> nameToId) {
        for (EntityDef entity : entities) {
            if (matchesLeftRole(entity.name())) {
                return nameToId.get(normalize(entity.name()));
            }
        }
        return nameToId.values().stream().findFirst().orElse(null);
    }

    private static String detectRightEntityId(List<EntityDef> entities, Map<String, String> nameToId, String leftId) {
        for (EntityDef entity : entities) {
            if (matchesRightRole(entity.name())) {
                String id = nameToId.get(normalize(entity.name()));
                if (id != null && !id.equals(leftId)) {
                    return id;
                }
            }
        }
        List<String> ids = new ArrayList<>(nameToId.values());
        for (int i = ids.size() - 1; i >= 0; i--) {
            if (!ids.get(i).equals(leftId)) {
                return ids.get(i);
            }
        }
        return null;
    }

    private static boolean matchesLeftRole(String name) {
        if (name == null) {
            return false;
        }
        return Set.of("用户", "会员", "学生", "客户", "读者", "顾客").stream().anyMatch(name::contains);
    }

    private static boolean matchesRightRole(String name) {
        return name != null && name.contains("管理员");
    }

    private static ErEdgeVo buildEdge(String id, String from, String to, String label) {
        ErEdgeVo edge = new ErEdgeVo();
        edge.setId(id);
        edge.setFrom(from);
        edge.setTo(to);
        edge.setLabel(label);
        return edge;
    }

    private static void placeEntity(ErNodeVo node, double x, double y) {
        node.setX(x);
        node.setY(y);
    }

    private static EntityDef findEntityDef(List<EntityDef> entities, String label) {
        for (EntityDef entity : entities) {
            if (entity.name().equals(label)) {
                return entity;
            }
        }
        return null;
    }

    private static double estimateAttrHalfWidth(String label) {
        if (label == null || label.isBlank()) {
            return ATTR_RX;
        }
        double textWidth = 0;
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            textWidth += ch >= 0x4E00 && ch <= 0x9FA5 ? ATTR_FONT_SIZE : ATTR_FONT_SIZE * 0.58;
        }
        return Math.max(ATTR_RX + 10, textWidth / 2.0 + 20);
    }

    private static List<String> orderAttrsForFan(List<String> attributes) {
        List<String> sorted = new ArrayList<>(attributes);
        sorted.sort((a, b) -> Double.compare(estimateAttrHalfWidth(b), estimateAttrHalfWidth(a)));
        List<String> ordered = new ArrayList<>(Collections.nCopies(sorted.size(), ""));
        int left = 0;
        int right = sorted.size() - 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (i % 2 == 0) {
                ordered.set(left++, sorted.get(i));
            } else {
                ordered.set(right--, sorted.get(i));
            }
        }
        return ordered;
    }

    private static List<Double> computeArcSteps(List<Double> halfWidths, double radius, double gapBoost) {
        List<Double> steps = new ArrayList<>();
        for (int i = 0; i < halfWidths.size() - 1; i++) {
            double w = Math.max(halfWidths.get(i), halfWidths.get(i + 1));
            steps.add(2 * Math.asin(Math.min(1, (w + ATTR_GAP + gapBoost) / radius)));
        }
        return steps;
    }

    private static double totalSpan(List<Double> steps) {
        return steps.stream().mapToDouble(Double::doubleValue).sum();
    }

    private static List<Double> fitArcSteps(List<Double> halfWidths, double radius, double gapBoost) {
        List<Double> steps = computeArcSteps(halfWidths, radius, gapBoost);
        double span = totalSpan(steps);
        if (span > MAX_ATTR_ARC && span > 0) {
            double scale = MAX_ATTR_ARC / span;
            List<Double> scaled = new ArrayList<>();
            for (Double step : steps) {
                scaled.add(step * scale);
            }
            return scaled;
        }
        return steps;
    }

    private static void layoutAttributesArcFan(String entityId, double ex, double entityTop,
                                               List<String> attributes, double lineLength,
                                               int attrOffset, List<ErNodeVo> nodes, List<ErEdgeVo> edges) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        double cx = ex + ENTITY_W / 2;
        List<String> ordered = orderAttrsForFan(attributes);
        List<Double> halfWidths = new ArrayList<>();
        for (String label : ordered) {
            halfWidths.add(estimateAttrHalfWidth(label));
        }
        double radius = Math.max(lineLength, 60);
        double gapBoost = 0;
        List<Double> steps = fitArcSteps(halfWidths, radius, gapBoost);
        double totalSpan = totalSpan(steps);
        double[] angles = new double[ordered.size()];
        angles[0] = ordered.size() == 1 ? FAN_MID_ANGLE : FAN_MID_ANGLE + totalSpan / 2;
        for (int i = 1; i < ordered.size(); i++) {
            angles[i] = angles[i - 1] - steps.get(i - 1);
        }
        for (int i = 0; i < ordered.size(); i++) {
            double ax = cx + radius * Math.cos(angles[i]) - ATTR_RX;
            double ay = entityTop - radius * Math.sin(angles[i]) - ATTR_RY;
            String attrId = entityId + "_a" + (attrOffset + i);
            ErNodeVo attrNode = new ErNodeVo();
            attrNode.setId(attrId);
            attrNode.setLabel(ordered.get(i));
            attrNode.setType("attribute");
            attrNode.setX(ax);
            attrNode.setY(ay);
            nodes.add(attrNode);

            ErEdgeVo edge = new ErEdgeVo();
            edge.setId("l_" + attrId);
            edge.setFrom(entityId);
            edge.setTo(attrId);
            edges.add(edge);
        }
    }

    private static void layoutAttributesArc(String entityId, double ex, double ey,
                                              List<String> attributes, List<ErNodeVo> nodes, List<ErEdgeVo> edges) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        double entityTop = ey;
        if (attributes.size() >= TWO_ROW_MIN) {
            int split = (attributes.size() + 1) / 2;
            layoutAttributesArcFan(entityId, ex, entityTop, attributes.subList(0, split), ATTR_RADIUS, 0, nodes, edges);
            layoutAttributesArcFan(entityId, ex, entityTop, attributes.subList(split, attributes.size()),
                ATTR_RADIUS + ATTR_ROW_GAP, split, nodes, edges);
            return;
        }
        layoutAttributesArcFan(entityId, ex, entityTop, attributes, ATTR_RADIUS, 0, nodes, edges);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String name) {
        return normalize(name).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "_");
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
