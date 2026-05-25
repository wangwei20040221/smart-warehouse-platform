package org.jeecg.modules.wms.ai.chat.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.modules.wms.ai.chat.vo.ShipmentTrackingInfo;
import org.jeecg.modules.wms.goods.entity.WmsCarrier;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.goods.service.IWmsCarrierService;
import org.jeecg.modules.wms.goods.service.IWmsProductsService;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersService;
import org.jeecg.modules.wms.shipment.entity.WmsShipment;
import org.jeecg.modules.wms.shipment.entity.WmsShipmentDetail;
import org.jeecg.modules.wms.shipment.service.IWmsShipmentDetailService;
import org.jeecg.modules.wms.shipment.service.IWmsShipmentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Mr.M
 * @version 1.0
 * @description 发货/快递追踪工具类
 * @date 2025/05/25
 */
@Component
public class WmsShipmentTools {

    @Autowired
    private IWmsProductsService wmsProductsService;

    @Autowired
    private IWmsShipmentDetailService wmsShipmentDetailService;

    @Autowired
    private IWmsShipmentService wmsShipmentService;

    @Autowired
    private IWmsCarrierService wmsCarrierService;

    @Autowired
    private IWmsOutOrdersService wmsOutOrdersService;

    /**
     * 根据商品名称或编码查询该商品的发货快递单号和物流公司信息
     *
     * @param productNameOrCode 商品名称或编码（支持模糊匹配）
     * @return 快递追踪信息列表
     */
    @Tool(description = "根据商品名称或编码查询该商品已发货的快递单号、物流公司、包裹状态等追踪信息。用户询问\"我的XXX发货了吗\"、\"查XXX的快递\"、\"XXX的物流信息\"时使用此工具")
    public List<ShipmentTrackingInfo> queryShipmentTracking(
            @ToolParam(description = "商品名称或编码，支持模糊匹配") String productNameOrCode) {

        if (!StringUtils.hasText(productNameOrCode)) {
            return Collections.emptyList();
        }

        // 1. 根据名称或编码模糊查询商品
        QueryWrapper<WmsProducts> productQuery = new QueryWrapper<>();
        productQuery.and(wrapper -> wrapper
                .like("product_name", productNameOrCode)
                .or()
                .like("product_code", productNameOrCode));
        List<WmsProducts> products = wmsProductsService.getBaseMapper().selectList(productQuery);

        if (CollectionUtils.isEmpty(products)) {
            return Collections.emptyList();
        }

        // 构建商品ID -> 商品信息映射
        Map<String, WmsProducts> productMap = products.stream()
                .collect(Collectors.toMap(WmsProducts::getId, p -> p, (p1, p2) -> p1));
        List<String> productIds = new ArrayList<>(productMap.keySet());

        // 2. 根据商品ID查询包裹明细
        QueryWrapper<WmsShipmentDetail> detailQuery = new QueryWrapper<>();
        detailQuery.in("sku_id", productIds);
        List<WmsShipmentDetail> details = wmsShipmentDetailService.getBaseMapper().selectList(detailQuery);

        if (CollectionUtils.isEmpty(details)) {
            return Collections.emptyList();
        }

        // 获取唯一的包裹ID列表
        List<String> shipmentIds = details.stream()
                .map(WmsShipmentDetail::getShipmentId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 查询包裹信息（仅查询已发货的）
        List<WmsShipment> shipments = wmsShipmentService.getBaseMapper().selectBatchIds(shipmentIds);
        if (CollectionUtils.isEmpty(shipments)) {
            return Collections.emptyList();
        }

        // 过滤掉没有快递单号的包裹
        shipments = shipments.stream()
                .filter(s -> StringUtils.hasText(s.getTrackingNo()))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(shipments)) {
            return Collections.emptyList();
        }

        // 4. 查询承运商信息
        List<String> carrierIds = shipments.stream()
                .map(WmsShipment::getCarrierId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> carrierNameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(carrierIds)) {
            List<WmsCarrier> carriers = wmsCarrierService.getBaseMapper().selectBatchIds(carrierIds);
            carrierNameMap = carriers.stream()
                    .collect(Collectors.toMap(WmsCarrier::getId, WmsCarrier::getCarrierName, (c1, c2) -> c1));
        }

        // 5. 查询出库单号
        List<String> orderIds = shipments.stream()
                .map(WmsShipment::getOrderId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> orderNoMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(orderIds)) {
            List<WmsOutOrders> orders = wmsOutOrdersService.getBaseMapper().selectBatchIds(orderIds);
            orderNoMap = orders.stream()
                    .collect(Collectors.toMap(WmsOutOrders::getId, WmsOutOrders::getOrderNo, (o1, o2) -> o1));
        }

        // 6. 组装结果：每个包裹对应一个追踪记录
        List<ShipmentTrackingInfo> result = new ArrayList<>();
        for (WmsShipment shipment : shipments) {
            // 找出该包裹关联的商品名称
            List<String> relatedProductIds = details.stream()
                    .filter(d -> d.getShipmentId().equals(shipment.getId()))
                    .map(WmsShipmentDetail::getSkuId)
                    .collect(Collectors.toList());

            for (String skuId : relatedProductIds) {
                WmsProducts product = productMap.get(skuId);
                if (product == null) continue;

                ShipmentTrackingInfo info = new ShipmentTrackingInfo();
                info.setProductName(product.getProductName());
                info.setProductCode(product.getProductCode());
                info.setTrackingNo(shipment.getTrackingNo());
                info.setCarrierName(carrierNameMap.getOrDefault(shipment.getCarrierId(), "未知"));
                info.setShipmentNo(shipment.getShipmentNo());
                info.setOrderNo(orderNoMap.getOrDefault(shipment.getOrderId(), ""));
                info.setStatus(shipment.getStatus());
                info.setToAddress(shipment.getToAddress());
                result.add(info);
            }
        }

        return result;
    }
}
