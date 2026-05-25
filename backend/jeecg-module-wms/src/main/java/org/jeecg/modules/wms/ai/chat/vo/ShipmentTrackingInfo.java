package org.jeecg.modules.wms.ai.chat.vo;

import lombok.Data;

/**
 * @author Mr.M
 * @version 1.0
 * @description 快递追踪信息VO
 * @date 2025/05/25
 */
@Data
public class ShipmentTrackingInfo {
    /** 商品名称 */
    private String productName;
    /** 商品编码 */
    private String productCode;
    /** 快递单号 */
    private String trackingNo;
    /** 物流公司名称 */
    private String carrierName;
    /** 包裹编码 */
    private String shipmentNo;
    /** 出库单号 */
    private String orderNo;
    /** 包裹状态 */
    private String status;
    /** 收货地址 */
    private String toAddress;
}
