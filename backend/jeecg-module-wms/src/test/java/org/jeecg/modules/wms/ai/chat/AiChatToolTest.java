package org.jeecg.modules.wms.ai.chat;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.wms.WmsTestApplication;
import org.jeecg.modules.wms.ai.chat.tools.WmsInventoryTools;
import org.jeecg.modules.wms.ai.chat.vo.WmsInventoryQuery;
import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * AI 工具直接调用测试 —— 验证 WMS 业务查询方法能正常工作
 *
 * 这些测试不依赖 LLM，直接调用工具方法验证 Service/DB 层。
 * 如果这些测试全部通过，说明后端工具方法本身没问题。
 *
 * 要验证 LLM + Tool Calling 全链路（即 AI 是否能自动调用这些工具），
 * 需要运行 jeecg-module-system 中的 AiChatFullStackTest（需要完整 Spring 上下文和 API Key）。
 */
@Slf4j
@SpringBootTest(classes = WmsTestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AiChatToolTest {

    @Autowired
    private WmsInventoryTools wmsInventoryTools;

    /**
     * 测试1：查询仓库
     */
    @Test
    @Order(1)
    public void test1_queryWarehouses() {
        log.info("========== 测试1：直接调用 queryWarehouses() ==========");
        try {
            List<WmsWarehouses> result = wmsInventoryTools.queryWarehouses();
            log.info("✅ 方法执行成功，返回 {} 条仓库记录", result.size());
            for (WmsWarehouses w : result) {
                log.info("   ID={}, 名称={}, 编码={}", w.getId(), w.getWarehouseName(), w.getWarehouseCode());
            }
            if (result.isEmpty()) {
                log.warn("⚠️ 仓库表无数据，但方法本身执行正常");
            }
        } catch (Exception e) {
            log.error("❌ 方法执行失败：{}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 测试2：查询商品
     */
    @Test
    @Order(2)
    public void test2_queryProducts() {
        log.info("========== 测试2：直接调用 queryProducts() ==========");
        try {
            List<WmsProducts> result = wmsInventoryTools.queryProducts();
            log.info("✅ 方法执行成功，返回 {} 条商品记录", result.size());
            for (WmsProducts p : result) {
                log.info("   ID={}, 名称={}, 编码={}", p.getId(), p.getProductName(), p.getProductCode());
            }
            if (result.isEmpty()) {
                log.warn("⚠️ 商品表无数据，但方法本身执行正常");
            }
        } catch (Exception e) {
            log.error("❌ 方法执行失败：{}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 测试3：查询货主
     */
    @Test
    @Order(3)
    public void test3_queryOwnerInfo() {
        log.info("========== 测试3：直接调用 queryOwnerInfo() ==========");
        try {
            List<WmsCargoOwners> result = wmsInventoryTools.queryOwnerInfo();
            log.info("✅ 方法执行成功，返回 {} 条货主记录", result.size());
            for (WmsCargoOwners o : result) {
                log.info("   ID={}, 名称={}, 编码={}", o.getId(), o.getOwnerName(), o.getOwnerCode());
            }
            if (result.isEmpty()) {
                log.warn("⚠️ 货主表无数据，但方法本身执行正常");
            }
        } catch (Exception e) {
            log.error("❌ 方法执行失败：{}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 测试4：条件查询库存
     */
    @Test
    @Order(4)
    public void test4_queryInventory() {
        log.info("========== 测试4：直接调用 queryInventory() ==========");
        try {
            // 不设条件，查第一页
            WmsInventoryQuery query = new WmsInventoryQuery();
            List<WmsInventory> result = wmsInventoryTools.queryInventory(query, 1);
            log.info("✅ 方法执行成功，返回 {} 条库存记录（第1页）", result.size());
            for (WmsInventory inv : result) {
                log.info("   商品={}, 货主={}, 仓库={}, 储位={}, 库存数量={}",
                        inv.getProductName(), inv.getOwnerName(),
                        inv.getWarehouseName(), inv.getLocationCode(), inv.getStockQuantity());
            }
            if (result.isEmpty()) {
                log.warn("⚠️ 库存表无数据，但方法本身执行正常");
            }
        } catch (Exception e) {
            log.error("❌ 方法执行失败：{}", e.getMessage(), e);
            throw e;
        }
    }
}
