package com.example.test5.device.opcua;

import android.util.Log;

import com.example.test5.net.NetworkDiagnostics;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Eclipse Milo OPC UA 客户端封装（调试页用，SecurityPolicy=None）。 */
public final class OpcUaClientHelper {

    /** Logcat 过滤此 tag，复制日志发给我分析。 */
    public static final String LOG_TAG = "DrumPotOpc";

    private static final long DEFAULT_TIMEOUT_MS = 10_000L;
    private static final int BATCH_READ_CHUNK = 40;

    private OpcUaClient client;
    private volatile boolean sessionConnected;
    private final Map<String, String> browseNameToNodeId = new LinkedHashMap<>();
    /** 浏览时缓存 NodeId → DataType，写入时按 PLC 类型编码。 */
    private final Map<String, String> nodeIdToDataType = new HashMap<>();

    public synchronized void connect(String endpointUrl, long timeoutMs) throws Exception {
        disconnect();
        sessionConnected = false;
        browseNameToNodeId.clear();
        nodeIdToDataType.clear();
        String url = endpointUrl.trim();
        Log.i(LOG_TAG, "[connect] start url=" + url + " timeoutMs=" + timeoutMs);
        try {
            URI uri = URI.create(url);
            NetworkDiagnostics.logBeforeDeviceTcp(LOG_TAG, uri.getHost(), uri.getPort());
        } catch (Exception ignored) {
            NetworkDiagnostics.logSnapshot("opc_connect " + url);
        }

        URI uri = URI.create(url);
        List<EndpointDescription> endpoints = DiscoveryClient.getEndpoints(url)
                .get(timeoutMs, TimeUnit.MILLISECONDS);
        Log.i(LOG_TAG, "[connect] GetEndpoints ok, count=" + endpoints.size());
        for (int i = 0; i < endpoints.size(); i++) {
            EndpointDescription ep = endpoints.get(i);
            Log.i(LOG_TAG, "[connect]   endpoint[" + i + "] policy=" + ep.getSecurityPolicyUri()
                    + " url=" + ep.getEndpointUrl());
        }

        if (endpoints.isEmpty()) {
            throw new IllegalStateException("未发现 Endpoint");
        }
        Optional<EndpointDescription> nonePolicy = endpoints.stream()
                .filter(endpoint -> SecurityPolicy.None.getUri().equals(endpoint.getSecurityPolicyUri()))
                .findFirst();
        EndpointDescription endpoint = nonePolicy.orElse(endpoints.get(0));
        Log.i(LOG_TAG, "[connect] selected policy=" + endpoint.getSecurityPolicyUri()
                + " url=" + endpoint.getEndpointUrl());

        OpcUaClientConfig config = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english("SmartOrder OPC Debug"))
                .setApplicationUri("urn:smart-order:opc-debug")
                .setEndpoint(EndpointUtil.updateUrl(endpoint, uri.getHost(), uri.getPort()))
                .build();

        client = OpcUaClient.create(config);
        client.connect().get(timeoutMs, TimeUnit.MILLISECONDS);
        sessionConnected = true;
        Log.i(LOG_TAG, "[connect] session established OK");
    }

    public synchronized void disconnect() {
        sessionConnected = false;
        if (client == null) {
            Log.i(LOG_TAG, "[disconnect] already disconnected");
            return;
        }
        try {
            client.disconnect().get(3, TimeUnit.SECONDS);
            Log.i(LOG_TAG, "[disconnect] OK");
        } catch (Exception e) {
            Log.w(LOG_TAG, "[disconnect] error", e);
        } finally {
            client = null;
            browseNameToNodeId.clear();
            nodeIdToDataType.clear();
        }
    }

    public synchronized boolean hasNodeMap() {
        return !browseNameToNodeId.isEmpty();
    }

    public synchronized boolean hasBrowseName(String browseName) {
        return browseNameToNodeId.containsKey(browseName);
    }

    /** 浏览 服务器接口_1 后，将 PLC 变量转为调试页条目（顺序与 browse 返回一致）。 */
    public synchronized List<DrumPotOpcKnownNodes.Entry> getInterfaceVariableEntries() {
        List<DrumPotOpcKnownNodes.Entry> list = new ArrayList<>(browseNameToNodeId.size());
        for (Map.Entry<String, String> item : browseNameToNodeId.entrySet()) {
            String label = item.getKey();
            String nodeId = item.getValue();
            String dataType = nodeIdToDataType.getOrDefault(nodeId, "");
            list.add(DrumPotOpcVariableRules.toEntry(label, nodeId, dataType));
        }
        return list;
    }

    /** 浏览服务器接口根节点，建立 BrowseName → NodeId 映射（供快捷按钮使用）。 */
    public synchronized String loadInterfaceNodeMap(String interfaceNodeId) throws Exception {
        ensureConnected();
        browseNameToNodeId.clear();
        nodeIdToDataType.clear();
        Log.i(LOG_TAG, "[loadMap] browse root=" + interfaceNodeId);

        NodeId root = parseNodeId(interfaceNodeId, null);
        List<ReferenceDescription> refs = client.getAddressSpace().browse(root);
        Log.i(LOG_TAG, "[loadMap] child count=" + refs.size());

        StringBuilder sb = new StringBuilder();
        sb.append("根节点 ").append(interfaceNodeId).append(" 子节点 ").append(refs.size()).append(" 个");
        sb.append("（Status / Value / DataType 同 UaExpert）:\n");
        appendReferenceDetails(sb, refs, "[loadMap]");
        return sb.toString().trim();
    }

    public synchronized String resolveNodeId(String browseName, int fallbackNamespace) {
        String mapped = browseNameToNodeId.get(browseName);
        if (mapped != null) {
            Log.d(LOG_TAG, "[resolve] " + browseName + " -> " + mapped);
            return mapped;
        }
        throw new IllegalStateException(
                "PLC 中无 BrowseName「" + browseName + "」，请先 loadInterfaceNodeMap");
    }

    /** 非阻塞，供 UI 轮询；勿在连接/浏览等长耗时 synchronized 方法持锁时调用。 */
    public boolean isConnected() {
        return sessionConnected;
    }

    public synchronized String browse(String nodeIdText) throws Exception {
        ensureConnected();
        String target = (nodeIdText == null || nodeIdText.trim().isEmpty())
                ? "ObjectsFolder" : nodeIdText.trim();
        Log.i(LOG_TAG, "[browse] target=" + target);

        NodeId nodeId = parseNodeId(nodeIdText, Identifiers.ObjectsFolder);
        List<ReferenceDescription> refs = client.getAddressSpace().browse(nodeId);
        Log.i(LOG_TAG, "[browse] result count=" + refs.size());

        if (refs.isEmpty()) {
            return "(无子节点)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Status / Value / DataType:\n");
        appendReferenceDetails(sb, refs, "[browse]");
        return sb.toString().trim();
    }

    /** 读单个节点：Status、Value、DataType（与 UaExpert Attributes 窗格一致）。 */
    public synchronized String read(String nodeIdText) throws Exception {
        ensureConnected();
        Log.i(LOG_TAG, "[read] nodeId=" + nodeIdText);
        NodeId nodeId = parseNodeId(nodeIdText, null);
        NodeDetails details = readNodeDetails(nodeId);
        String result = formatNodeDetailsLine(nodeId.toParseableString(), details);
        Log.i(LOG_TAG, "[read] result " + result);
        return result;
    }

    public synchronized String write(String nodeIdText, String rawValue) throws Exception {
        ensureConnected();
        String key = nodeIdText.trim();
        Log.i(LOG_TAG, "[write] nodeId=" + key + " value=" + rawValue);
        NodeId nodeId = parseNodeId(key, null);
        String dataType = resolveDataType(key, nodeId);
        Log.i(LOG_TAG, "[write] dataType=" + dataType);

        Variant variant = buildWriteVariant(rawValue, dataType);
        Log.i(LOG_TAG, "[write] try variant=" + variant + " javaType="
                + (variant.getValue() == null ? "null" : variant.getValue().getClass().getSimpleName()));
        StatusCode status = client.writeValue(nodeId, DataValue.valueOnly(variant))
                .get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        if (!status.isGood() && isNumericText(rawValue)) {
            int iv = Integer.parseInt(rawValue.trim());
            Variant fallback = writeNumericWithFallback(nodeId, iv, dataType);
            if (fallback != null) {
                variant = fallback;
            } else {
                Log.e(LOG_TAG, "[write] failed all types status=" + status + " dataType=" + dataType);
                throw new IllegalStateException("写入失败(类型不匹配): " + status + " dataType=" + dataType);
            }
        } else if (!status.isGood()) {
            Log.e(LOG_TAG, "[write] failed status=" + status);
            throw new IllegalStateException("写入失败: " + status);
        }
        String result = "OK -> " + variant;
        Log.i(LOG_TAG, "[write] result " + result);
        return result;
    }

    /** 文档：置 true 触发，PLC 自动清零（Boolean 节点）。 */
    public synchronized String pulseTrue(String nodeIdText) throws Exception {
        ensureConnected();
        Log.i(LOG_TAG, "[pulse] nodeId=" + nodeIdText);
        NodeId nodeId = parseNodeId(nodeIdText, null);
        Variant variant = new Variant(Boolean.TRUE);
        StatusCode status = client.writeValue(nodeId, DataValue.valueOnly(variant))
                .get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!status.isGood()) {
            Log.e(LOG_TAG, "[pulse] failed status=" + status);
            throw new IllegalStateException("脉冲写入失败: " + status);
        }
        Log.i(LOG_TAG, "[pulse] OK Boolean=true -> " + nodeIdText);
        return "pulse true -> " + nodeIdText;
    }

    public static String toNodeId(int namespaceIndex, String browseName) {
        return "ns=" + namespaceIndex + ";s=" + browseName;
    }

    public static NodeId parseNodeId(String nodeIdText, NodeId defaultNodeId) {
        if (nodeIdText == null || nodeIdText.trim().isEmpty()) {
            if (defaultNodeId == null) {
                throw new IllegalArgumentException("NodeId 不能为空");
            }
            return defaultNodeId;
        }
        String text = nodeIdText.trim();
        if (text.startsWith("ns=")) {
            return NodeId.parse(text);
        }
        int colon = text.indexOf(':');
        if (colon > 0) {
            try {
                int ns = Integer.parseInt(text.substring(0, colon).trim());
                String id = text.substring(colon + 1).trim();
                if (id.startsWith("s=")) {
                    return new NodeId(ns, id.substring(2));
                }
                if (id.startsWith("i=")) {
                    return new NodeId(ns, Integer.parseInt(id.substring(2)));
                }
                return new NodeId(ns, id);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalArgumentException("NodeId 格式无效: " + text);
    }

    private void appendReferenceDetails(StringBuilder sb, List<ReferenceDescription> refs, String logPrefix)
            throws Exception {
        List<ResolvedReference> resolved = new ArrayList<>();
        for (ReferenceDescription ref : refs) {
            String name = ref.getBrowseName().getName();
            String nodeIdText = ref.getNodeId().toParseableString();
            browseNameToNodeId.put(name, nodeIdText);

            Optional<NodeId> expanded = ref.getNodeId().toNodeId(client.getNamespaceTable());
            if (expanded.isEmpty()) {
                String line = name + " | " + nodeIdText + " | (NodeId 无法展开)";
                sb.append(line).append('\n');
                Log.i(LOG_TAG, logPrefix + "   " + line);
                continue;
            }
            resolved.add(new ResolvedReference(name, nodeIdText, expanded.get(), ref.getNodeClass()));
        }

        Map<NodeId, NodeDetails> detailsMap = batchReadNodeDetails(resolved);
        for (ResolvedReference item : resolved) {
            NodeDetails details = detailsMap.get(item.nodeId);
            if (details == null) {
                details = new NodeDetails(item.nodeClass.name(), "-", "-", "-");
            }
            nodeIdToDataType.put(item.nodeIdText, details.dataType);
            String line = item.name + " | " + item.nodeIdText + " | " + formatNodeDetailsLine("", details).trim();
            sb.append(line).append('\n');
            Log.i(LOG_TAG, logPrefix + "   " + line);
        }
    }

    private Map<NodeId, NodeDetails> batchReadNodeDetails(List<ResolvedReference> items) throws Exception {
        Map<NodeId, NodeDetails> result = new HashMap<>();
        List<ReadValueId> reads = new ArrayList<>();
        List<NodeId> variableNodeIds = new ArrayList<>();

        for (ResolvedReference item : items) {
            NodeDetails nonVar = new NodeDetails(
                    item.nodeClass.name(),
                    "-",
                    "-",
                    "-");
            if (item.nodeClass != NodeClass.Variable) {
                result.put(item.nodeId, nonVar);
                continue;
            }
            variableNodeIds.add(item.nodeId);
            reads.add(new ReadValueId(
                    item.nodeId, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE));
            reads.add(new ReadValueId(
                    item.nodeId, AttributeId.DataType.uid(), null, QualifiedName.NULL_VALUE));
        }

        for (int offset = 0; offset < reads.size(); offset += BATCH_READ_CHUNK) {
            int end = Math.min(offset + BATCH_READ_CHUNK, reads.size());
            List<ReadValueId> chunk = reads.subList(offset, end);
            DataValue[] values = client.read(0.0, TimestampsToReturn.Both, chunk)
                    .get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .getResults();

            for (int i = 0; i < values.length; i += 2) {
                int varIndex = (offset + i) / 2;
                if (varIndex >= variableNodeIds.size()) {
                    break;
                }
                NodeId nodeId = variableNodeIds.get(varIndex);
                DataValue valueDv = values[i];
                DataValue typeDv = values[i + 1];
                result.put(nodeId, new NodeDetails(
                        NodeClass.Variable.name(),
                        formatStatus(valueDv.getStatusCode()),
                        formatValue(valueDv.getValue()),
                        formatDataType(typeDv.getValue())));
            }
        }
        return result;
    }

    private NodeDetails readNodeDetails(NodeId nodeId) throws Exception {
        List<ReadValueId> reads = List.of(
                new ReadValueId(nodeId, AttributeId.NodeClass.uid(), null, QualifiedName.NULL_VALUE),
                new ReadValueId(nodeId, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE),
                new ReadValueId(nodeId, AttributeId.DataType.uid(), null, QualifiedName.NULL_VALUE));
        DataValue[] values = client.read(0.0, TimestampsToReturn.Both, reads)
                .get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .getResults();

        String nodeClass = formatNodeClass(values[0].getValue());
        if (!NodeClass.Variable.name().equals(nodeClass) && !"Variable".equals(nodeClass)) {
            return new NodeDetails(nodeClass, "-", "-", "-");
        }
        return new NodeDetails(
                nodeClass,
                formatStatus(values[1].getStatusCode()),
                formatValue(values[1].getValue()),
                formatDataType(values[2].getValue()));
    }

    private static String formatNodeDetailsLine(String nodeIdText, NodeDetails details) {
        StringBuilder sb = new StringBuilder();
        if (nodeIdText != null && !nodeIdText.isEmpty()) {
            sb.append(nodeIdText).append(" | ");
        }
        sb.append("Class=").append(details.nodeClass)
                .append(" | Status=").append(details.status)
                .append(" | Value=").append(details.value)
                .append(" | DataType=").append(details.dataType);
        return sb.toString();
    }

    private static String formatStatus(StatusCode statusCode) {
        if (statusCode == null) {
            return "?";
        }
        if (statusCode.isGood()) {
            return "Good";
        }
        if (statusCode.isBad()) {
            return "Bad(" + statusCode.getValue() + ")";
        }
        return "Uncertain(" + statusCode.getValue() + ")";
    }

    private static String formatValue(Variant variant) {
        if (variant == null || variant.isNull()) {
            return "null";
        }
        Object raw = variant.getValue();
        if (raw == null) {
            return "null";
        }
        if (raw instanceof Boolean) {
            return raw.toString();
        }
        return String.valueOf(raw);
    }

    private static String formatNodeClass(Variant variant) {
        if (variant == null || variant.isNull()) {
            return "?";
        }
        Object raw = variant.getValue();
        if (raw instanceof Integer) {
            NodeClass nc = NodeClass.from((Integer) raw);
            return nc != null ? nc.name() : String.valueOf(raw);
        }
        if (raw instanceof UInteger) {
            NodeClass nc = NodeClass.from(((UInteger) raw).intValue());
            return nc != null ? nc.name() : String.valueOf(raw);
        }
        return String.valueOf(raw);
    }

    /** 将 DataType 属性（NodeId）格式化为 UaExpert 可读名称。 */
    private static String formatDataType(Variant variant) {
        if (variant == null || variant.isNull()) {
            return "-";
        }
        Object raw = variant.getValue();
        if (!(raw instanceof NodeId)) {
            return String.valueOf(raw);
        }
        NodeId dataTypeId = (NodeId) raw;
        if (dataTypeId.getNamespaceIndex().intValue() == 0 && dataTypeId.getIdentifier() instanceof UInteger) {
            int id = ((UInteger) dataTypeId.getIdentifier()).intValue();
            String name = builtinDataTypeName(id);
            if (name != null) {
                return name;
            }
        }
        return dataTypeId.toParseableString();
    }

    private static String builtinDataTypeName(int id) {
        switch (id) {
            case 1: return "Boolean";
            case 2: return "SByte";
            case 3: return "Byte";
            case 4: return "Int16";
            case 5: return "UInt16";
            case 6: return "Int32";
            case 7: return "UInt32";
            case 8: return "Int64";
            case 9: return "UInt64";
            case 10: return "Float";
            case 11: return "Double";
            case 12: return "String";
            case 13: return "DateTime";
            case 14: return "Guid";
            case 15: return "ByteString";
            default: return null;
        }
    }

    private void ensureConnected() {
        if (client == null) {
            throw new IllegalStateException("未连接 OPC UA");
        }
    }

    private String resolveDataType(String nodeIdText, NodeId nodeId) throws Exception {
        String cached = nodeIdToDataType.get(nodeIdText);
        if (cached != null && !"-".equals(cached)) {
            return cached;
        }
        NodeDetails details = readNodeDetails(nodeId);
        String dataType = details.dataType;
        nodeIdToDataType.put(nodeIdText, dataType);
        return dataType;
    }

    private static boolean isNumericText(String rawValue) {
        if (rawValue == null) {
            return false;
        }
        String text = rawValue.trim();
        if (text.isEmpty() || text.contains(".")) {
            return false;
        }
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 按 DataType 构造写入值；西门子 ns=3;i=3002 多为 Word(UInt16)。 */
    private static Variant buildWriteVariant(String rawValue, String dataType) {
        String text = rawValue == null ? "" : rawValue.trim();
        if ("true".equalsIgnoreCase(text)) {
            return new Variant(Boolean.TRUE);
        }
        if ("false".equalsIgnoreCase(text)) {
            return new Variant(Boolean.FALSE);
        }
        if (dataType != null && dataType.contains("Boolean")) {
            try {
                return new Variant(Integer.parseInt(text) != 0);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        try {
            if (text.contains(".")) {
                return new Variant(Double.parseDouble(text));
            }
            int iv = Integer.parseInt(text);
            if (dataType != null && dataType.contains("i=3006")) {
                return new Variant(iv);
            }
            if (dataType != null && dataType.contains("i=3002")) {
                return new Variant(UShort.valueOf(iv));
            }
            if (dataType != null && dataType.contains("Int16")) {
                return new Variant((short) iv);
            }
            if (dataType != null && (dataType.contains("Float") || dataType.contains("Double"))) {
                return new Variant((float) iv);
            }
            return new Variant(UShort.valueOf(iv));
        } catch (NumberFormatException e) {
            return new Variant(text);
        }
    }

    private Variant writeNumericWithFallback(NodeId nodeId, int value, String dataType) throws Exception {
        List<Variant> candidates = new ArrayList<>();
        if (dataType != null && dataType.contains("i=3006")) {
            candidates.add(new Variant(value));
            candidates.add(new Variant((long) value));
            candidates.add(new Variant(UShort.valueOf(value)));
            candidates.add(new Variant((short) value));
        } else if (dataType != null && dataType.contains("i=3002")) {
            candidates.add(new Variant(UShort.valueOf(value)));
            candidates.add(new Variant((short) value));
            candidates.add(new Variant(value));
            candidates.add(new Variant((byte) value));
        } else if (dataType != null && dataType.contains("Int16")) {
            candidates.add(new Variant((short) value));
            candidates.add(new Variant(UShort.valueOf(value)));
            candidates.add(new Variant(value));
        } else {
            candidates.add(new Variant(UShort.valueOf(value)));
            candidates.add(new Variant((short) value));
            candidates.add(new Variant(value));
            candidates.add(new Variant((long) value));
            candidates.add(new Variant((byte) value));
        }
        StatusCode lastStatus = null;
        for (Variant candidate : candidates) {
            Object raw = candidate.getValue();
            Log.i(LOG_TAG, "[write] retry javaType="
                    + (raw == null ? "null" : raw.getClass().getSimpleName()));
            StatusCode status = client.writeValue(nodeId, DataValue.valueOnly(candidate))
                    .get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            lastStatus = status;
            if (status.isGood()) {
                Log.i(LOG_TAG, "[write] fallback OK " + raw.getClass().getSimpleName());
                return candidate;
            }
        }
        Log.w(LOG_TAG, "[write] all fallbacks failed last=" + lastStatus + " dataType=" + dataType);
        return null;
    }

    private static final class ResolvedReference {
        final String name;
        final String nodeIdText;
        final NodeId nodeId;
        final NodeClass nodeClass;

        ResolvedReference(String name, String nodeIdText, NodeId nodeId, NodeClass nodeClass) {
            this.name = name;
            this.nodeIdText = nodeIdText;
            this.nodeId = nodeId;
            this.nodeClass = nodeClass;
        }
    }

    private static final class NodeDetails {
        final String nodeClass;
        final String status;
        final String value;
        final String dataType;

        NodeDetails(String nodeClass, String status, String value, String dataType) {
            this.nodeClass = nodeClass;
            this.status = status;
            this.value = value;
            this.dataType = dataType;
        }
    }
}
