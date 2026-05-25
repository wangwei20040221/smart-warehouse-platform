package org.jeecg.modules.wms.inventory.service.impl;

import org.jeecg.modules.wms.inventory.entity.WmsInventoryTrans;
import org.jeecg.modules.wms.inventory.mapper.WmsInventoryTransMapper;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryTransService;
import org.jeecg.modules.wms.inventory.vo.WmsInventoryTransParam;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 库存变更表
 * @Author: jeecg-boot
 * @Date:   2025-08-30
 * @Version: V1.0
 */
@Service
@Primary
public class WmsInventoryTransServiceImpl extends ServiceImpl<WmsInventoryTransMapper, WmsInventoryTrans> implements IWmsInventoryTransService {

    @Override
    public void transfer(WmsInventoryTransParam inventoryTransParam) {
        throw new UnsupportedOperationException("Use strategy-specific implementations instead");
    }
}
