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
import com.example.test5.recipe.flow.RecipeFlow;

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
    private static final float PORT_RADIUS = 12f;
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
    private final RectF nodeRect = new RectF();

    private RecipeFlow flow = new RecipeFlow();
    private Listener listener;

    private float scale = 1f;
    private float offsetX;
    private float offsetY;

    private FlowNode draggingNode;
    private float dragOffsetX;
    private float dragOffsetY;

    private FlowNode connectingFrom;
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
        portStrokePaint.setStrokeWidth(3f);

        previewPaint.setColor(Color.parseColor("#94A3B8"));
        previewPaint.setStrokeWidth(4f);
        previewPaint.setStyle(Paint.Style.STROKE);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setFlow(RecipeFlow flow) {
        this.flow = flow != null ? flow : new RecipeFlow();
        selectedNode = null;
        connectingFrom = null;
        invalidate();
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
            drawEdge(canvas, outputX(from), outputY(from), inputX(to), inputY(to), linePaint);
        }
        if (connectingFrom != null) {
            drawEdge(canvas, outputX(connectingFrom), outputY(connectingFrom),
                    previewWorldX, previewWorldY, previewPaint);
        }
        for (FlowNode node : flow.nodes) {
            drawNode(canvas, node, node == selectedNode);
        }
        canvas.restore();
    }

    private void drawNode(Canvas canvas, FlowNode node, boolean selected) {
        float left = node.x;
        float top = node.y;
        nodeRect.set(left, top, left + NODE_WIDTH, top + NODE_HEIGHT);
        nodePaint.setColor(deviceColor(node.deviceId));
        canvas.drawRoundRect(nodeRect, 16f, 16f, nodePaint);
        nodeStrokePaint.setColor(selected ? Color.parseColor("#FACC15") : Color.parseColor("#1E293B"));
        canvas.drawRoundRect(nodeRect, 16f, 16f, nodeStrokePaint);

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

        canvas.drawCircle(inputX(node), inputY(node), PORT_RADIUS, portPaint);
        canvas.drawCircle(inputX(node), inputY(node), PORT_RADIUS, portStrokePaint);
        canvas.drawCircle(outputX(node), outputY(node), PORT_RADIUS, portPaint);
        canvas.drawCircle(outputX(node), outputY(node), PORT_RADIUS, portStrokePaint);
    }

    private void drawEdge(Canvas canvas, float x1, float y1, float x2, float y2, Paint paint) {
        Path path = new Path();
        float cx = (x1 + x2) / 2f;
        path.moveTo(x1, y1);
        path.cubicTo(cx, y1, cx, y2, x2, y2);
        canvas.drawPath(path, paint);
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
        float worldX = toWorldX(screenX);
        float worldY = toWorldY(screenY);

        FlowNode portNode = findPortNode(worldX, worldY, true);
        if (portNode != null) {
            connectingFrom = portNode;
            previewWorldX = worldX;
            previewWorldY = worldY;
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
        if (connectingFrom != null) {
            float worldX = toWorldX(screenX);
            float worldY = toWorldY(screenY);
            FlowNode target = findPortNode(worldX, worldY, false);
            if (target != null && !target.id.equals(connectingFrom.id)) {
                addOrReplaceEdge(connectingFrom.id, target.id);
            }
            connectingFrom = null;
            invalidate();
            return true;
        }
        draggingNode = null;
        panning = false;
        return true;
    }

    private void addOrReplaceEdge(String fromId, String toId) {
        if (fromId.equals(toId)) {
            return;
        }
        flow.edges.removeIf(edge -> edge.fromNodeId.equals(fromId) && edge.toNodeId.equals(toId));
        flow.edges.add(new FlowEdge(fromId, toId));
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
    private FlowNode findPortNode(float worldX, float worldY, boolean output) {
        float hitRadius = PORT_RADIUS * 2f;
        for (int i = flow.nodes.size() - 1; i >= 0; i--) {
            FlowNode node = flow.nodes.get(i);
            float cx = output ? outputX(node) : inputX(node);
            float cy = output ? outputY(node) : inputY(node);
            float dx = worldX - cx;
            float dy = worldY - cy;
            if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                return node;
            }
        }
        return null;
    }

    private static float inputX(FlowNode node) {
        return node.x;
    }

    private static float inputY(FlowNode node) {
        return node.y + NODE_HEIGHT / 2f;
    }

    private static float outputX(FlowNode node) {
        return node.x + NODE_WIDTH;
    }

    private static float outputY(FlowNode node) {
        return node.y + NODE_HEIGHT / 2f;
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
