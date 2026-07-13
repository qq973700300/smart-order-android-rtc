package com.example.test5.ui.flow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.test5.recipe.flow.FlowActionDef;
import com.example.test5.recipe.flow.FlowDeviceType;
import com.example.test5.recipe.flow.FlowEdge;
import com.example.test5.recipe.flow.FlowNode;
import com.example.test5.recipe.flow.FlowPort;
import com.example.test5.recipe.flow.RecipeFlow;

import java.util.HashSet;
import java.util.Set;

/**
 * 流程画布：拖动节点、连线；空白处拖动画布；双指缩放。
 */
public final class FlowCanvasView extends View {

    public interface Listener {
        void onNodeSelected(@Nullable FlowNode node);

        void onFlowChanged();
    }

    private static final float NODE_WIDTH = 272f;
    private static final float NODE_HEIGHT = 124f;
    private static final float PORT_RADIUS = 18f;
    private static final float ARROW_SIZE = 18f;
    private static final float MIN_SCALE = 0.35f;
    private static final float MAX_SCALE = 2.5f;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paramPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint portPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint portStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint runOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF nodeRect = new RectF();

    private boolean runMode;
    private final Set<String> activeNodeIds = new HashSet<>();
    private final Set<String> completedNodeIds = new HashSet<>();
    @Nullable
    private String failedNodeId;

    private RecipeFlow flow = new RecipeFlow();
    private Listener listener;

    private float scale = 1f;
    private float offsetX;
    private float offsetY;

    private FlowNode draggingNode;
    private float dragOffsetX;
    private float dragOffsetY;

    private FlowNode connectingFrom;
    private String connectingFromPort;
    private float previewWorldX;
    private float previewWorldY;

    private FlowNode selectedNode;

    private boolean panning;
    private float panStartScreenX;
    private float panStartScreenY;
    private float panStartOffsetX;
    private float panStartOffsetY;

    private boolean scaling;
    private float pinchStartSpan;
    private float pinchStartScale;

    private boolean pendingFitToView;

    public FlowCanvasView(Context context) {
        super(context);
        init();
    }

    public FlowCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(Color.parseColor("#F3F4F6"));
        linePaint.setColor(Color.parseColor("#64748B"));
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);

        nodePaint.setStyle(Paint.Style.FILL);
        nodeStrokePaint.setStyle(Paint.Style.STROKE);
        nodeStrokePaint.setStrokeWidth(3f);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setFakeBoldText(true);

        subTextPaint.setColor(Color.parseColor("#F8FAFC"));
        subTextPaint.setTextSize(22f);

        paramPaint.setColor(Color.parseColor("#E2E8F0"));
        paramPaint.setTextSize(20f);

        portPaint.setColor(Color.WHITE);
        portPaint.setStyle(Paint.Style.FILL);
        portStrokePaint.setColor(Color.parseColor("#334155"));
        portStrokePaint.setStyle(Paint.Style.STROKE);
        portStrokePaint.setStrokeWidth(4f);

        previewPaint.setColor(Color.parseColor("#94A3B8"));
        previewPaint.setStrokeWidth(4f);
        previewPaint.setStyle(Paint.Style.STROKE);

        runOverlayPaint.setStyle(Paint.Style.FILL);
    }

    /** 运行监视模式：只读，高亮当前/已完成步骤。 */
    public void setRunMode(boolean runMode) {
        this.runMode = runMode;
        if (runMode) {
            selectedNode = null;
            connectingFrom = null;
            connectingFromPort = null;
            draggingNode = null;
        }
        invalidate();
    }

    public void clearExecutionState() {
        activeNodeIds.clear();
        completedNodeIds.clear();
        failedNodeId = null;
        invalidate();
    }

    public void setNodeActive(@Nullable String nodeId, boolean active) {
        if (nodeId == null) {
            return;
        }
        if (active) {
            activeNodeIds.add(nodeId);
        } else {
            activeNodeIds.remove(nodeId);
        }
        invalidate();
    }

    public void markNodeCompleted(@Nullable String nodeId) {
        if (nodeId == null) {
            return;
        }
        activeNodeIds.remove(nodeId);
        completedNodeIds.add(nodeId);
        invalidate();
    }

    public void markNodeFailed(@Nullable String nodeId) {
        failedNodeId = nodeId;
        if (nodeId != null) {
            activeNodeIds.remove(nodeId);
        }
        invalidate();
    }

    /** 将流程居中缩放以适应视口。 */
    public void fitFlowInView() {
        if (flow.nodes.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;
        for (FlowNode node : flow.nodes) {
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
            maxX = Math.max(maxX, node.x + NODE_WIDTH);
            maxY = Math.max(maxY, node.y + NODE_HEIGHT);
        }
        float flowW = maxX - minX;
        float flowH = maxY - minY;
        float pad = 48f;
        float viewW = getWidth();
        float viewH = getHeight();
        scale = Math.min((viewW - pad * 2f) / flowW, (viewH - pad * 2f) / flowH);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        offsetX = (viewW - flowW * scale) / 2f - minX * scale;
        offsetY = (viewH - flowH * scale) / 2f - minY * scale;
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setFlow(RecipeFlow flow) {
        this.flow = flow != null ? flow : new RecipeFlow();
        selectedNode = null;
        connectingFrom = null;
        connectingFromPort = null;
        invalidate();
    }

    /** 设置流程并自动缩放居中，使全部节点一进入即可见。 */
    public void setFlowAndFitToView(RecipeFlow flow) {
        setFlow(flow);
        pendingFitToView = true;
        post(this::tryFitFlowInView);
    }

    private void tryFitFlowInView() {
        if (!pendingFitToView) {
            return;
        }
        if (getWidth() > 0 && getHeight() > 0 && !flow.nodes.isEmpty()) {
            fitFlowInView();
            pendingFitToView = false;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        tryFitFlowInView();
    }

    public RecipeFlow getFlow() {
        return flow;
    }

    public void addNode(FlowActionDef def, float x, float y) {
        FlowNode node = FlowNode.create(def, x, y);
        flow.nodes.add(node);
        selectedNode = node;
        notifyChanged();
        invalidate();
    }

    /** 在当前可见区域中心添加节点（考虑平移与缩放）。 */
    public void addNodeAtViewportCenter(FlowActionDef def) {
        float viewW = getWidth() > 0 ? getWidth() : 800f;
        float viewH = getHeight() > 0 ? getHeight() : 600f;
        float worldCenterX = (viewW / 2f - offsetX) / scale;
        float worldCenterY = (viewH / 2f - offsetY) / scale;
        int n = flow.nodes.size();
        float staggerX = (n % 3) * 24f;
        float staggerY = (n / 3) * 24f;
        float x = worldCenterX - NODE_WIDTH / 2f + staggerX;
        float y = worldCenterY - NODE_HEIGHT / 2f + staggerY;
        addNode(def, x, y);
    }

    public void removeSelectedNode() {
        if (selectedNode == null) {
            return;
        }
        String id = selectedNode.id;
        flow.nodes.removeIf(node -> node.id.equals(id));
        flow.edges.removeIf(edge -> edge.fromNodeId.equals(id) || edge.toNodeId.equals(id));
        selectedNode = null;
        notifyChanged();
        invalidate();
    }

    public void clearAll() {
        flow.nodes.clear();
        flow.edges.clear();
        selectedNode = null;
        connectingFrom = null;
        connectingFromPort = null;
        notifyChanged();
        invalidate();
    }

    public void resetViewport() {
        scale = 1f;
        offsetX = 0f;
        offsetY = 0f;
        invalidate();
    }

    public void zoomIn() {
        applyZoomAt(getWidth() / 2f, getHeight() / 2f, 1.25f);
    }

    public void zoomOut() {
        applyZoomAt(getWidth() / 2f, getHeight() / 2f, 1f / 1.25f);
    }

    private void applyZoomAt(float focusX, float focusY, float factor) {
        float previousScale = scale;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        float scaleFactor = scale / previousScale;
        offsetX = focusX - (focusX - offsetX) * scaleFactor;
        offsetY = focusY - (focusY - offsetY) * scaleFactor;
        invalidate();
    }

    @Nullable
    public FlowNode getSelectedNode() {
        return selectedNode;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        for (FlowEdge edge : flow.edges) {
            FlowNode from = findNode(edge.fromNodeId);
            FlowNode to = findNode(edge.toNodeId);
            if (from == null || to == null) {
                continue;
            }
            String fromPort = resolveFromPort(edge, from, to);
            String toPort = resolveToPort(edge, from, to);
            float[] p1 = portPosition(from, fromPort);
            float[] p2 = portPosition(to, toPort);
            drawEdgeWithArrow(canvas, p1[0], p1[1], p2[0], p2[1], linePaint);
        }
        if (connectingFrom != null && connectingFromPort != null) {
            float[] start = portPosition(connectingFrom, connectingFromPort);
            drawEdge(canvas, start[0], start[1], previewWorldX, previewWorldY, previewPaint);
        }
        for (FlowNode node : flow.nodes) {
            drawNode(canvas, node, !runMode && node == selectedNode);
        }
        canvas.restore();
    }

    private void drawNode(Canvas canvas, FlowNode node, boolean selected) {
        float left = node.x;
        float top = node.y;
        nodeRect.set(left, top, left + NODE_WIDTH, top + NODE_HEIGHT);

        boolean isActive = activeNodeIds.contains(node.id);
        boolean isDone = completedNodeIds.contains(node.id);
        boolean isFailed = node.id.equals(failedNodeId);

        int baseColor = deviceColor(node.deviceId);
        if (runMode && isDone) {
            baseColor = blendColor(baseColor, Color.parseColor("#22C55E"), 0.35f);
        } else if (runMode && isFailed) {
            baseColor = blendColor(baseColor, Color.parseColor("#EF4444"), 0.35f);
        } else if (runMode && isActive) {
            baseColor = blendColor(baseColor, Color.parseColor("#F59E0B"), 0.25f);
        }

        nodePaint.setColor(baseColor);
        canvas.drawRoundRect(nodeRect, 16f, 16f, nodePaint);

        if (runMode && isActive) {
            nodeStrokePaint.setColor(Color.parseColor("#F59E0B"));
            nodeStrokePaint.setStrokeWidth(6f);
        } else if (runMode && isDone) {
            nodeStrokePaint.setColor(Color.parseColor("#16A34A"));
            nodeStrokePaint.setStrokeWidth(5f);
        } else if (runMode && isFailed) {
            nodeStrokePaint.setColor(Color.parseColor("#DC2626"));
            nodeStrokePaint.setStrokeWidth(6f);
        } else {
            nodeStrokePaint.setColor(selected ? Color.parseColor("#FACC15") : Color.parseColor("#1E293B"));
            nodeStrokePaint.setStrokeWidth(3f);
        }
        canvas.drawRoundRect(nodeRect, 16f, 16f, nodeStrokePaint);

        if (runMode && isActive) {
            runOverlayPaint.setColor(Color.parseColor("#33F59E0B"));
            canvas.drawRoundRect(nodeRect, 16f, 16f, runOverlayPaint);
        }

        float padding = 14f;
        float lineGap = 30f;
        float textY = top + 28f;
        canvas.drawText(node.deviceLabel(), left + padding, textY, textPaint);
        textY += lineGap;
        canvas.drawText(node.label, left + padding, textY, subTextPaint);

        String summary = node.paramSummary();
        if (!summary.isEmpty()) {
            textY += lineGap;
            canvas.drawText(summary, left + padding, textY, paramPaint);
        }

        if (!runMode) {
            for (String port : FlowPort.all()) {
                float[] pos = portPosition(node, port);
                canvas.drawCircle(pos[0], pos[1], PORT_RADIUS, portPaint);
                canvas.drawCircle(pos[0], pos[1], PORT_RADIUS, portStrokePaint);
            }
        }
    }

    private static int blendColor(int base, int overlay, float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int r = (int) (Color.red(base) * (1f - ratio) + Color.red(overlay) * ratio);
        int g = (int) (Color.green(base) * (1f - ratio) + Color.green(overlay) * ratio);
        int b = (int) (Color.blue(base) * (1f - ratio) + Color.blue(overlay) * ratio);
        return Color.rgb(r, g, b);
    }

    private void drawEdgeWithArrow(Canvas canvas, float x1, float y1, float x2, float y2, Paint paint) {
        float cx = (x1 + x2) / 2f;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.cubicTo(cx, y1, cx, y2, x2, y2);
        canvas.drawPath(path, paint);

        float t = 0.92f;
        float u = 1f - t;
        float nearX = u * u * u * x1 + 3f * u * u * t * cx + 3f * u * t * t * cx + t * t * t * x2;
        float nearY = u * u * u * y1 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y2;
        float inDx = x2 - nearX;
        float inDy = y2 - nearY;
        if (Math.hypot(inDx, inDy) < 1f) {
            inDx = x2 - x1;
            inDy = y2 - y1;
        }
        float angle = (float) Math.atan2(inDy, inDx);
        drawArrowHead(canvas, x2, y2, angle, paint);
    }

    private void drawEdge(Canvas canvas, float x1, float y1, float x2, float y2, Paint paint) {
        Path path = new Path();
        float cx = (x1 + x2) / 2f;
        path.moveTo(x1, y1);
        path.cubicTo(cx, y1, cx, y2, x2, y2);
        canvas.drawPath(path, paint);
    }

    private void drawArrowHead(Canvas canvas, float tipX, float tipY, float angle, Paint strokePaint) {
        float wing = ARROW_SIZE;
        float a1 = angle + (float) Math.toRadians(150);
        float a2 = angle + (float) Math.toRadians(210);
        Path arrow = new Path();
        arrow.moveTo(tipX, tipY);
        arrow.lineTo(tipX + wing * (float) Math.cos(a1), tipY + wing * (float) Math.sin(a1));
        arrow.lineTo(tipX + wing * (float) Math.cos(a2), tipY + wing * (float) Math.sin(a2));
        arrow.close();
        Paint fill = new Paint(strokePaint);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, strokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        final int pointerCount = event.getPointerCount();

        if (pointerCount >= 2) {
            handlePinch(event);
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            scaling = false;
        }

        float screenX = event.getX();
        float screenY = event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return handleDown(screenX, screenY);
            case MotionEvent.ACTION_MOVE:
                return handleMove(screenX, screenY);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return handleUp(screenX, screenY);
            default:
                return super.onTouchEvent(event);
        }
    }

    private void handlePinch(MotionEvent event) {
        panning = false;
        draggingNode = null;
        connectingFrom = null;
        connectingFromPort = null;

        float span = pinchSpan(event);
        float focusX = pinchFocusX(event);
        float focusY = pinchFocusY(event);
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_DOWN) {
            scaling = true;
            pinchStartSpan = Math.max(span, 8f);
            pinchStartScale = scale;
            return;
        }

        if (!scaling || pinchStartSpan < 8f) {
            return;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            float targetScale = pinchStartScale * (span / pinchStartSpan);
            targetScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, targetScale));
            float scaleFactor = targetScale / scale;
            offsetX = focusX - (focusX - offsetX) * scaleFactor;
            offsetY = focusY - (focusY - offsetY) * scaleFactor;
            scale = targetScale;
            invalidate();
        } else if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerCount() - 1 < 2) {
            scaling = false;
        }
    }

    private static float pinchSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    private static float pinchFocusX(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return event.getX();
        }
        return (event.getX(0) + event.getX(1)) * 0.5f;
    }

    private static float pinchFocusY(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return event.getY();
        }
        return (event.getY(0) + event.getY(1)) * 0.5f;
    }

    private boolean handleDown(float screenX, float screenY) {
        if (runMode) {
            panning = true;
            panStartScreenX = screenX;
            panStartScreenY = screenY;
            panStartOffsetX = offsetX;
            panStartOffsetY = offsetY;
            return true;
        }

        float worldX = toWorldX(screenX);
        float worldY = toWorldY(screenY);

        PortHit portHit = findPortHit(worldX, worldY);
        if (portHit != null) {
            connectingFrom = portHit.node;
            connectingFromPort = portHit.port;
            float[] start = portPosition(portHit.node, portHit.port);
            previewWorldX = start[0];
            previewWorldY = start[1];
            panning = false;
            invalidate();
            return true;
        }

        FlowNode hit = findNodeAt(worldX, worldY);
        if (hit != null) {
            draggingNode = hit;
            dragOffsetX = worldX - hit.x;
            dragOffsetY = worldY - hit.y;
            panning = false;
            selectNode(hit);
            return true;
        }

        panning = true;
        panStartScreenX = screenX;
        panStartScreenY = screenY;
        panStartOffsetX = offsetX;
        panStartOffsetY = offsetY;
        selectNode(null);
        return true;
    }

    private boolean handleMove(float screenX, float screenY) {
        if (panning) {
            offsetX = panStartOffsetX + (screenX - panStartScreenX);
            offsetY = panStartOffsetY + (screenY - panStartScreenY);
            invalidate();
            return true;
        }

        float worldX = toWorldX(screenX);
        float worldY = toWorldY(screenY);

        if (draggingNode != null) {
            draggingNode.x = worldX - dragOffsetX;
            draggingNode.y = worldY - dragOffsetY;
            notifyChanged();
            invalidate();
            return true;
        }
        if (connectingFrom != null) {
            previewWorldX = worldX;
            previewWorldY = worldY;
            invalidate();
            return true;
        }
        return false;
    }

    private boolean handleUp(float screenX, float screenY) {
        if (connectingFrom != null && !runMode) {
            float worldX = toWorldX(screenX);
            float worldY = toWorldY(screenY);
            PortHit target = findPortHit(worldX, worldY);
            if (target != null && !target.node.id.equals(connectingFrom.id) && connectingFromPort != null) {
                addOrReplaceEdge(connectingFrom.id, connectingFromPort, target.node.id, target.port);
            }
            connectingFrom = null;
            connectingFromPort = null;
            invalidate();
            return true;
        }
        draggingNode = null;
        panning = false;
        return true;
    }

    private void addOrReplaceEdge(String fromId, String fromPort, String toId, String toPort) {
        if (fromId.equals(toId)) {
            return;
        }
        flow.edges.removeIf(edge -> edge.fromNodeId.equals(fromId) && edge.toNodeId.equals(toId));
        flow.edges.add(new FlowEdge(fromId, fromPort, toId, toPort));
        notifyChanged();
    }

    private void selectNode(@Nullable FlowNode node) {
        selectedNode = node;
        if (listener != null) {
            listener.onNodeSelected(node);
        }
        invalidate();
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onFlowChanged();
        }
    }

    private float toWorldX(float screenX) {
        return (screenX - offsetX) / scale;
    }

    private float toWorldY(float screenY) {
        return (screenY - offsetY) / scale;
    }

    @Nullable
    private FlowNode findNode(String id) {
        for (FlowNode node : flow.nodes) {
            if (node.id.equals(id)) {
                return node;
            }
        }
        return null;
    }

    @Nullable
    private FlowNode findNodeAt(float worldX, float worldY) {
        for (int i = flow.nodes.size() - 1; i >= 0; i--) {
            FlowNode node = flow.nodes.get(i);
            if (worldX >= node.x && worldX <= node.x + NODE_WIDTH
                    && worldY >= node.y && worldY <= node.y + NODE_HEIGHT) {
                return node;
            }
        }
        return null;
    }

    @Nullable
    private PortHit findPortHit(float worldX, float worldY) {
        float hitRadius = PORT_RADIUS * 2.5f;
        float hitR2 = hitRadius * hitRadius;
        for (int i = flow.nodes.size() - 1; i >= 0; i--) {
            FlowNode node = flow.nodes.get(i);
            for (String port : FlowPort.all()) {
                float[] pos = portPosition(node, port);
                float dx = worldX - pos[0];
                float dy = worldY - pos[1];
                if (dx * dx + dy * dy <= hitR2) {
                    return new PortHit(node, port);
                }
            }
        }
        return null;
    }

    private static float[] portPosition(FlowNode node, String port) {
        float x;
        float y;
        switch (port) {
            case FlowPort.LEFT:
                x = node.x;
                y = node.y + NODE_HEIGHT / 2f;
                break;
            case FlowPort.TOP:
                x = node.x + NODE_WIDTH / 2f;
                y = node.y;
                break;
            case FlowPort.BOTTOM:
                x = node.x + NODE_WIDTH / 2f;
                y = node.y + NODE_HEIGHT;
                break;
            case FlowPort.RIGHT:
            default:
                x = node.x + NODE_WIDTH;
                y = node.y + NODE_HEIGHT / 2f;
                break;
        }
        return new float[]{x, y};
    }

    private static String resolveFromPort(FlowEdge edge, FlowNode from, FlowNode to) {
        if (FlowPort.isValid(edge.fromPort)) {
            return edge.fromPort;
        }
        return pickDefaultFromPort(from, to);
    }

    private static String resolveToPort(FlowEdge edge, FlowNode from, FlowNode to) {
        if (FlowPort.isValid(edge.toPort)) {
            return edge.toPort;
        }
        return pickDefaultToPort(from, to);
    }

    private static String pickDefaultFromPort(FlowNode from, FlowNode to) {
        float fx = from.x + NODE_WIDTH / 2f;
        float fy = from.y + NODE_HEIGHT / 2f;
        float tx = to.x + NODE_WIDTH / 2f;
        float ty = to.y + NODE_HEIGHT / 2f;
        float dx = tx - fx;
        float dy = ty - fy;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? FlowPort.RIGHT : FlowPort.LEFT;
        }
        return dy > 0 ? FlowPort.BOTTOM : FlowPort.TOP;
    }

    private static String pickDefaultToPort(FlowNode from, FlowNode to) {
        float fx = from.x + NODE_WIDTH / 2f;
        float fy = from.y + NODE_HEIGHT / 2f;
        float tx = to.x + NODE_WIDTH / 2f;
        float ty = to.y + NODE_HEIGHT / 2f;
        float dx = tx - fx;
        float dy = ty - fy;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? FlowPort.LEFT : FlowPort.RIGHT;
        }
        return dy > 0 ? FlowPort.TOP : FlowPort.BOTTOM;
    }

    private static final class PortHit {
        final FlowNode node;
        final String port;

        PortHit(FlowNode node, String port) {
            this.node = node;
            this.port = port;
        }
    }

    private static int deviceColor(String deviceId) {
        FlowDeviceType type = FlowDeviceType.fromId(deviceId);
        if (type == FlowDeviceType.STOCK_BIN) {
            return Color.parseColor("#16A34A");
        }
        if (type == FlowDeviceType.YUEJIANG) {
            return Color.parseColor("#2563EB");
        }
        if (type == FlowDeviceType.DRUM_POT) {
            return Color.parseColor("#EA580C");
        }
        if (type == FlowDeviceType.FLOW_CONTROL) {
            return Color.parseColor("#7C3AED");
        }
        return Color.parseColor("#475569");
    }
}
