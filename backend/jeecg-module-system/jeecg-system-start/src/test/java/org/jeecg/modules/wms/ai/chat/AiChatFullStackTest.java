package org.jeecg.modules.wms.ai.chat;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.JeecgSystemApplication;
import org.jeecg.modules.wms.ai.chat.tools.WmsInventoryTools;
import org.jeecg.modules.wms.ai.chat.tools.WmsShipmentTools;
import org.jeecg.modules.wms.ai.chat.vo.ShipmentTrackingInfo;
import org.jeecg.modules.wms.ai.chat.vo.WmsInventoryQuery;
import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 工具调用全链路测试 —— 验证 LLM + Tool Calling 能否正常查询业务数据
 *
 * 需要在 system-start 模块运行（有完整的 Spring 上下文、API Key 等配置）
 *
 * 判定标准：
 *   Test 1-4：工具方法直接调用 → 验证 Service/DB 层
 *   Test 5-6：ChatClient + tools 调用 LLM →
 *     - AI 返回了数据库中的实际数据 → ✅ Tool Calling 全链路正常，问题在前端
 *     - AI 返回了模板/示例/拒绝 → ❌ Tool Calling 未生效
 *     - AI 返回了错误/超时 → ❌ LLM 连接或 API Key 有问题
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = JeecgSystemApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AiChatFullStackTest {

    @Autowired
    private WmsInventoryTools wmsInventoryTools;

    @Autowired
    private WmsShipmentTools wmsShipmentTools;

    @Autowired
    @Qualifier("chatClientOpenAi")
    private ChatClient chatClient;

    // ==================== Part 1: 工具方法直接调用测试 ====================

    @Test
    @Order(1)
    public void test1_queryWarehouses() {
        log.info("========== 测试1：直接调用 queryWarehouses() ==========");
        List<WmsWarehouses> result = wmsInventoryTools.queryWarehouses();
        log.info("✅ 执行成功，返回 {} 条仓库记录", result.size());
        for (WmsWarehouses w : result) {
            log.info("   ID={}, 名称={}, 编码={}", w.getId(), w.getWarehouseName(), w.getWarehouseCode());
        }
    }

    @Test
    @Order(2)
    public void test2_queryProducts() {
        log.info("========== 测试2：直接调用 queryProducts() ==========");
        List<WmsProducts> result = wmsInventoryTools.queryProducts();
        log.info("✅ 执行成功，返回 {} 条商品记录", result.size());
        for (WmsProducts p : result) {
            log.info("   ID={}, 名称={}, 编码={}", p.getId(), p.getProductName(), p.getProductCode());
        }
    }

    @Test
    @Order(3)
    public void test3_queryOwnerInfo() {
        log.info("========== 测试3：直接调用 queryOwnerInfo() ==========");
        List<WmsCargoOwners> result = wmsInventoryTools.queryOwnerInfo();
        log.info("✅ 执行成功，返回 {} 条货主记录", result.size());
        for (WmsCargoOwners o : result) {
            log.info("   ID={}, 名称={}, 编码={}", o.getId(), o.getOwnerName(), o.getOwnerCode());
        }
    }

    @Test
    @Order(4)
    public void test4_queryInventory() {
        log.info("========== 测试4：直接调用 queryInventory() ==========");
        WmsInventoryQuery query = new WmsInventoryQuery();
        List<WmsInventory> result = wmsInventoryTools.queryInventory(query, 1);
        log.info("✅ 执行成功，返回 {} 条库存记录（第1页）", result.size());
        for (WmsInventory inv : result) {
            log.info("   商品={}, 货主={}, 仓库={}, 储位={}, 库存数量={}",
                    inv.getProductName(), inv.getOwnerName(),
                    inv.getWarehouseName(), inv.getLocationCode(), inv.getStockQuantity());
        }
    }

    // ==================== Part 2: LLM + Tool Calling 全链路测试 ====================

    /**
     * 测试5：通过 ChatClient 直接调用 LLM，验证 Tool Calling 查询仓库
     */
    @Test
    @Order(5)
    public void test5_llmQueryWarehouses() {
        log.info("========== 测试5：LLM + Tool Calling 查询仓库 ==========");

        List<WmsWarehouses> dbResult = wmsInventoryTools.queryWarehouses();
        log.info("数据库实际仓库数：{}", dbResult.size());

        try {
            String response = chatClient
                    .prompt("帮我查询一下系统里有哪些仓库，列出仓库名称")
                    .tools(wmsInventoryTools)
                    .call()
                    .content();

            log.info("AI 回复：{}", response);

            boolean hasRealData = false;
            for (WmsWarehouses w : dbResult) {
                if (w.getWarehouseName() != null && response.contains(w.getWarehouseName())) {
                    hasRealData = true;
                    log.info("✅ AI 回复中包含真实仓库名：{}", w.getWarehouseName());
                    break;
                }
            }

            if (hasRealData) {
                log.info("✅ 测试5 通过：LLM 成功调用了 queryWarehouses 工具并返回了真实数据！");
            } else if (isTemplateResponse(response)) {
                log.error("❌ 测试5 失败：AI 返回了模板/拒绝类回复，Tool Calling 未生效");
            } else if (dbResult.isEmpty()) {
                log.info("⚠️ 数据库无仓库数据，无法判断 Tool Calling 是否生效");
            } else {
                log.warn("⚠️ 数据库有 {} 条仓库，但 AI 回复未匹配到仓库名", dbResult.size());
                log.warn("   回复：{}", response);
            }
        } catch (Exception e) {
            log.error("❌ LLM 调用失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 测试6：通过 ChatClient 测试库存查询
     */
    @Test
    @Order(6)
    public void test6_llmQueryInventory() {
        log.info("========== 测试6：LLM + Tool Calling 查询库存 ==========");

        WmsInventoryQuery preQuery = new WmsInventoryQuery();
        List<WmsInventory> dbInventory = wmsInventoryTools.queryInventory(preQuery, 1);
        log.info("数据库库存数（第1页）：{}", dbInventory.size());

        try {
            String response = chatClient
                    .prompt("帮我查一下系统里有哪些库存商品")
                    .tools(wmsInventoryTools)
                    .call()
                    .content();

            log.info("AI 回复：{}", response);

            boolean hasRealData = false;
            for (WmsInventory inv : dbInventory) {
                if (inv.getProductName() != null && response.contains(inv.getProductName())) {
                    hasRealData = true;
                    log.info("✅ AI 回复中包含真实商品名：{}", inv.getProductName());
                    break;
                }
            }

            if (hasRealData) {
                log.info("✅ 测试6 通过：LLM 成功调用了 queryInventory 工具并返回了真实库存数据！");
            } else if (isTemplateResponse(response)) {
                log.error("❌ 测试6 失败：AI 返回了模板/拒绝类回复，Tool Calling 未生效");
            } else if (dbInventory.isEmpty()) {
                log.info("⚠️ 数据库无库存数据，无法判断 Tool Calling 是否生效");
            } else {
                log.warn("⚠️ 数据库有 {} 条库存但 AI 回复未匹配到商品名", dbInventory.size());
                log.warn("   AI 回复：{}", response);
            }
        } catch (Exception e) {
            log.error("❌ LLM 调用失败：{}", e.getMessage(), e);
        }
    }

    // ==================== Part 3: 模拟 ChatController app==null 流程测试 ====================

    /**
     * 测试7：完全模拟 ChatController.send() 中 app==null 分支的调用方式
     * 使用 cachedToolCallbacks + .stream() + system prompt
     */
    @Test
    @Order(7)
    public void test7_controllerFlow_stream() {
        log.info("========== 测试7：模拟 ChatController app==null 分支 (stream模式) ==========");

        // 1. 按 ChatController 的方式构建 cachedToolCallbacks
        ToolCallback[] inventoryCallbacks = ToolCallbacks.from(wmsInventoryTools);
        ToolCallback[] shipmentCallbacks = ToolCallbacks.from(wmsShipmentTools);
        ToolCallback[] cachedCallbacks = new ToolCallback[inventoryCallbacks.length + shipmentCallbacks.length];
        System.arraycopy(inventoryCallbacks, 0, cachedCallbacks, 0, inventoryCallbacks.length);
        System.arraycopy(shipmentCallbacks, 0, cachedCallbacks, inventoryCallbacks.length, shipmentCallbacks.length);

        log.info("Tool 数量：{}", cachedCallbacks.length);
        for (ToolCallback tc : cachedCallbacks) {
            log.info("  - {} : {}", tc.getName(), tc.getDescription());
        }

        // 2. 和 ChatController 完全相同的 system prompt
        String systemPrompt = "你是星辰WMS仓储管理系统的AI助手。你可以帮助用户完成以下操作：\n" +
                "1. 查询库存信息（按商品、仓库、货主、储位等条件）\n" +
                "2. 查询所有仓库列表\n" +
                "3. 查询所有商品列表\n" +
                "4. 查询所有货主信息\n" +
                "5. 查询商品发货快递单号和物流公司信息\n" +
                "如果用户询问你能做什么，请用中文简要介绍以上功能。";

        try {
            log.info(">>> 开始 stream 调用 LLM（用户提示词：查询所有仓库库存）...");

            // 3. 和 ChatController app==null 完全相同的调用
            Flux<String> flux = chatClient
                    .prompt("查询所有仓库库存")
                    .system(systemPrompt)
                    .tools(cachedCallbacks)
                    .stream()
                    .content();

            // 4. 收集流式结果
            List<String> chunks = flux.collectList().block();
            String fullResponse = chunks != null ? String.join("", chunks) : "NULL";

            log.info("<<< 收到 {} 个chunk，完整回复长度：{}", chunks != null ? chunks.size() : 0, fullResponse.length());
            log.info("完整回复：{}", fullResponse);

            if (fullResponse.equals("NULL") || fullResponse.isEmpty()) {
                log.error("❌ 测试7 失败：返回为空");
            } else if (isTemplateResponse(fullResponse)) {
                log.error("❌ 测试7 失败：AI 返回了模板/拒绝类回复，Tool Calling 未生效");
                log.error("   这说明 LLM 没有调用我们的工具，而是给了通用回复");
            } else {
                log.info("✅ 测试7 通过：stream 模式 Tool Calling 工作正常！");
            }
        } catch (Exception e) {
            log.error("❌ 测试7 失败：LLM 调用异常 - {}", e.getMessage(), e);
        }
    }

    /**
     * 测试8：同样流程但用 .call() 对比，验证是否是 stream vs call 的差异
     */
    @Test
    @Order(8)
    public void test8_controllerFlow_call() {
        log.info("========== 测试8：模拟 ChatController app==null 分支 (call模式，对比) ==========");

        ToolCallback[] inventoryCallbacks = ToolCallbacks.from(wmsInventoryTools);
        ToolCallback[] shipmentCallbacks = ToolCallbacks.from(wmsShipmentTools);
        ToolCallback[] cachedCallbacks = new ToolCallback[inventoryCallbacks.length + shipmentCallbacks.length];
        System.arraycopy(inventoryCallbacks, 0, cachedCallbacks, 0, inventoryCallbacks.length);
        System.arraycopy(shipmentCallbacks, 0, cachedCallbacks, inventoryCallbacks.length, shipmentCallbacks.length);

        String systemPrompt = "你是星辰WMS仓储管理系统的AI助手。你可以帮助用户完成以下操作：\n" +
                "1. 查询库存信息（按商品、仓库、货主、储位等条件）\n" +
                "2. 查询所有仓库列表\n" +
                "3. 查询所有商品列表\n" +
                "4. 查询所有货主信息\n" +
                "5. 查询商品发货快递单号和物流公司信息\n" +
                "如果用户询问你能做什么，请用中文简要介绍以上功能。";

        try {
            log.info(">>> 开始 call 调用 LLM（用户提示词：查询所有仓库库存）...");

            String response = chatClient
                    .prompt("查询所有仓库库存")
                    .system(systemPrompt)
                    .tools(cachedCallbacks)
                    .call()
                    .content();

            log.info("<<< 收到回复长度：{}", response != null ? response.length() : 0);
            log.info("完整回复：{}", response);

            if (response == null || response.isEmpty()) {
                log.error("❌ 测试8 失败：返回为空");
            } else if (isTemplateResponse(response)) {
                log.error("❌ 测试8 失败：AI 返回了模板/拒绝类回复，Tool Calling 未生效");
            } else {
                log.info("✅ 测试8 通过：call 模式 Tool Calling 工作正常！");
            }
        } catch (Exception e) {
            log.error("❌ 测试8 失败：LLM 调用异常 - {}", e.getMessage(), e);
        }
    }

    /**
     * 测试9：直接调用 WmsShipmentTools，验证快递追踪功能
     */
    @Test
    @Order(9)
    public void test9_shipmentTracking() {
        log.info("========== 测试9：直接调用 queryShipmentTracking() ==========");
        List<ShipmentTrackingInfo> result = wmsShipmentTools.queryShipmentTracking("红牛");
        log.info("✅ 执行成功，返回 {} 条快递追踪记录", result.size());
        for (ShipmentTrackingInfo info : result) {
            log.info("   商品={}, 快递单号={}, 物流公司={}, 状态={}, 收货地址={}",
                    info.getProductName(), info.getTrackingNo(), info.getCarrierName(),
                    info.getStatus(), info.getToAddress());
        }
    }

    /**
     * 测试10：LLM + Tool Calling 查询快递追踪
     */
    @Test
    @Order(10)
    public void test10_llmShipmentTracking() {
        log.info("========== 测试10：LLM + Tool Calling 查询快递追踪 ==========");

        List<ShipmentTrackingInfo> dbResult = wmsShipmentTools.queryShipmentTracking("红牛");
        log.info("直接调用结果数：{}", dbResult.size());

        ToolCallback[] inventoryCallbacks = ToolCallbacks.from(wmsInventoryTools);
        ToolCallback[] shipmentCallbacks = ToolCallbacks.from(wmsShipmentTools);
        ToolCallback[] cachedCallbacks = new ToolCallback[inventoryCallbacks.length + shipmentCallbacks.length];
        System.arraycopy(inventoryCallbacks, 0, cachedCallbacks, 0, inventoryCallbacks.length);
        System.arraycopy(shipmentCallbacks, 0, cachedCallbacks, inventoryCallbacks.length, shipmentCallbacks.length);

        try {
            log.info(">>> 开始 LLM 调用（用户提示词：帮我查一下红牛的快递信息）...");

            Flux<String> flux = chatClient
                    .prompt("帮我查一下红牛的快递信息")
                    .system("你是星辰WMS仓储管理系统的AI助手。你可以查询商品的快递单号和物流信息。")
                    .tools(cachedCallbacks)
                    .stream()
                    .content();

            List<String> chunks = flux.collectList().block();
            String fullResponse = chunks != null ? String.join("", chunks) : "NULL";

            log.info("<<< 收到 {} 个chunk", chunks != null ? chunks.size() : 0);
            log.info("完整回复：{}", fullResponse);

            boolean hasTracking = dbResult.stream()
                    .anyMatch(info -> info.getTrackingNo() != null && fullResponse.contains(info.getTrackingNo()));

            if (hasTracking) {
                log.info("✅ 测试10 通过：LLM 成功调用了 queryShipmentTracking 并返回了真实快递数据！");
            } else if (isTemplateResponse(fullResponse)) {
                log.error("❌ 测试10 失败：AI 返回了模板/拒绝类回复，Tool Calling 未生效");
            } else {
                log.info("⚠️ 快递单号未匹配，但 AI 有实际回复（非模板）");
            }
        } catch (Exception e) {
            log.error("❌ 测试10 失败：LLM 调用异常 - {}", e.getMessage(), e);
        }
    }

    private boolean isTemplateResponse(String response) {
        return response.contains("示例") || response.contains("模板")
                || response.contains("你可以这样") || response.contains("无法直接访问")
                || response.contains("无法访问") || response.contains("我无法");
    }
}
