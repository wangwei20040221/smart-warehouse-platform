package org.jeecg.modules.wms.wave.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.goods.service.IWmsProductsService;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryService;
//import org.jeecg.modules.wms.inventory.service.impl.WmsInventoryTransByPick;
import org.jeecg.modules.wms.inventory.service.impl.WmsInventoryTransByPick;
import org.jeecg.modules.wms.inventory.vo.WmsInventoryTransParam;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;
import org.jeecg.modules.wms.outorder.mapper.WmsOutOrdersAllocationMapper;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersAllocationService;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersItemsService;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersService;
import org.jeecg.modules.wms.pickroute.MultiProductPickingRouteOptimized;
import org.jeecg.modules.wms.pickroute.dto.Location;
import org.jeecg.modules.wms.pickroute.dto.Node;
import org.jeecg.modules.wms.pickroute.dto.Product;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageLocations;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageZones;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageLocationsService;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageZonesService;
import org.jeecg.modules.wms.wave.entity.WmsShortageRegistration;
import org.jeecg.modules.wms.wave.entity.WmsWaveMaster;
import org.jeecg.modules.wms.wave.entity.WmsWaveSkuSummary;
import org.jeecg.modules.wms.wave.mapper.WmsWaveMasterMapper;
import org.jeecg.modules.wms.wave.service.IPickingTasksService;
import org.jeecg.modules.wms.wave.service.IWmsShortageRegistrationService;
import org.jeecg.modules.wms.wave.service.IWmsWaveMasterService;
import org.jeecg.modules.wms.wave.service.IWmsWaveSkuSummaryService;
import org.jeecg.modules.wms.wmstask.entity.WmsTasks;
import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksRecordsService;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksService;
import org.jeecg.modules.wms.wmstask.service.impl.WmsTasksRecordsServiceImpl;
import org.jeecg.modules.wms.wmstask.service.impl.WmsTasksServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Mr.M
 * @version 1.0
 * @description 拣货服务类
 * @date 2025/6/6 8:31
 */
@Service
public class PickingTasksServiceImpl implements IPickingTasksService {
    @Autowired
    private IWmsTasksService wmsTasksService;
    @Autowired
    private IWmsWaveMasterService wmsWaveMasterService;
    @Autowired
    private IWmsOutOrdersService wmsOutOrdersService;

    @Autowired
    private IWmsProductsService wmsProductsService;

    @Autowired
    private IWmsOutOrdersItemsService wmsOutOrdersItemsService;

    @Autowired
    private WmsOutOrdersAllocationMapper wmsOutOrdersAllocationMapper;

    @Autowired
    private IWmsOutOrdersAllocationService wmsOutOrdersAllocationService;

    @Autowired
    private IWmsWaveSkuSummaryService wmsWaveSkuSummaryService;
    @Autowired
    private WmsInventoryTransByPick inventoryTransByPick;


//    @Autowired
//    private WmsInventoryTransByPick inventoryTransByPick;

    @Autowired
    private IWmsTasksRecordsService wmsTasksRecordsService;

    @Autowired
    private IWmsShortageRegistrationService wmsShortageRegistrationService;

    @Autowired
    private IWmsStorageLocationsService wmsStorageLocationsService;

    //储区service
    @Autowired
    private IWmsStorageZonesService wmsStorageZonesService;

    /**
     * 生成拣货任务
     * @param waveIds 波次id集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPickTask(List<String> waveIds) {
        //遍历波次id集合，调用创建拣货任务方法
        for (String waveId : waveIds) {
            createPickTask(waveId);
        }
    }

    /**
     * 根据波次统计出库单的商品分配数量,添加到波次拣货明细表
     * @param waveId 波次id
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public void createPickTask(String waveId) {
        //查询波次
        WmsWaveMaster waveMaster = wmsWaveMasterService.getById(waveId);
        //更新波次的状态由创建状态到拣货中
        //sql update wms_wave_master set status = ? where id = #{waveId} and status = ?
        LambdaUpdateWrapper<WmsWaveMaster> set = new LambdaUpdateWrapper<WmsWaveMaster>()
                .eq(WmsWaveMaster::getId, waveId)
                .eq(WmsWaveMaster::getStatus, WarehouseDictEnum.WAVE_CREATED.getCode())
                .set(WmsWaveMaster::getStatus, WarehouseDictEnum.WAVE_PICKING.getCode());
        boolean update = wmsWaveMasterService.update(null, set);
        if (!update) {
            throw new JeecgBootException("波次状态更新失败");
        }

        //统计波次下的出库单的库存分配明细  先查询第一页
        IPage<WmsOutOrdersAllocation> wmsOutOrdersAllocationIPage = wmsOutOrdersAllocationService.selectAllocatedQuantityByWaveId(waveId, 1, 100);
        //如果wmsOutOrdersAllocationIPage结果集为空直接返回
        List<WmsOutOrdersAllocation> records = wmsOutOrdersAllocationIPage.getRecords();
        if (records.size() == 0) {
            throw  new JeecgBootException("没有找到波次下的库存分配明细");
        }
        //定义一 个页码
        int pageNo = 1;
        while(true){
            //将records 转成 List<WmsWaveSkuSummary>
            List<WmsWaveSkuSummary> wmsWaveSkuSummaries = records.stream().map(wmsOutOrdersAllocation -> {
                WmsWaveSkuSummary wmsWaveSkuSummary = new WmsWaveSkuSummary();
                //波次id
                wmsWaveSkuSummary.setWaveId(waveId);
                //商品id
                wmsWaveSkuSummary.setSkuId(wmsOutOrdersAllocation.getSkuId());
                //货主id
                wmsWaveSkuSummary.setOwnerId(wmsOutOrdersAllocation.getOwnerId());
                //商品条码
                wmsWaveSkuSummary.setProductBarcode(wmsOutOrdersAllocation.getProductBarcode());
                //储位编码
                wmsWaveSkuSummary.setLocationCode(wmsOutOrdersAllocation.getLocationCode());
                //批次号
                wmsWaveSkuSummary.setBatchNumber(wmsOutOrdersAllocation.getBatchNumber());
                //分配数量
                wmsWaveSkuSummary.setAllocatedQuantity(wmsOutOrdersAllocation.getAllocatedQuantity());
                //拣货数量 为0
                wmsWaveSkuSummary.setPickedQuantity(0);
                //状态 拣货中
                wmsWaveSkuSummary.setStatus(WarehouseDictEnum.WAVE_PICKING.getCode());

                return wmsWaveSkuSummary;
            }).collect(Collectors.toList());

            //根据统计的库存分配明细向波次拣货明细wms_wave_sku_summary 插入记录
            boolean b = wmsWaveSkuSummaryService.saveBatch(wmsWaveSkuSummaries);
            if (!b) {
                throw new JeecgBootException("波次拣货明细表插入失败");
            }
            //将wmsWaveSkuSummaries 转成 List<WmsTask>
            List<WmsTasks> wmsTaskList = wmsWaveSkuSummaries.stream().map(wmsWaveSkuSummary -> {
                //创建收货任务
                WmsTasks wmsTasks = new WmsTasks();
                //任务类型,拣货任务
                wmsTasks.setTaskType(WarehouseDictEnum.TASK_TYPE_PICKING.getCode());
                //任务状态，已创建
                wmsTasks.setTaskStatus(WarehouseDictEnum.TASK_STATUS_CREATED.getCode());
                //任务创建时间
                wmsTasks.setCreateTime(new Date());
                //任务号
                wmsTasks.setTaskNumber(wmsTasksService.generateTaskCode());
                //波次id
                wmsTasks.setWaveOrderId(waveId);
                //商品id
                wmsTasks.setProductId(wmsWaveSkuSummary.getSkuId());
                //商品待拣货数量
                wmsTasks.setQuantity(wmsWaveSkuSummary.getAllocatedQuantity());
                //来源储位编码
                wmsTasks.setSourceLocationCode(wmsWaveSkuSummary.getLocationCode());
                //来源仓库
                wmsTasks.setSourceWarehouseId(waveMaster.getWarehouseId());
                //目的仓库
                wmsTasks.setTargetWarehouseId(waveMaster.getWarehouseId());
                //商品批次号
                wmsTasks.setBatchNumber(wmsWaveSkuSummary.getBatchNumber());
                //目的储位编码
                wmsTasks.setTargetLocationCode("");
                //波次拣货明细id
                wmsTasks.setWaveSkuSummaryId(wmsWaveSkuSummary.getId());
                //完成数量为0
                wmsTasks.setCompletedQuantity(0);
                return wmsTasks;
            }).collect(Collectors.toList());

            //根据波次拣货明细向任务表插入拣货任务
            boolean b1 = wmsTasksService.saveBatch(wmsTaskList);
            if (!b1) {
                throw new JeecgBootException("任务表插入失败");
            }

            //继续查询库存分配明细统计结果的下一页
            wmsOutOrdersAllocationIPage = wmsOutOrdersAllocationService.selectAllocatedQuantityByWaveId(waveId, ++pageNo, 100);
            records = wmsOutOrdersAllocationIPage.getRecords();
            if (records.size() == 0) {
                break;
            }
        }

        //更新该波次下的出库单的状态为拣货中 由已分配变为拣货中
        LambdaUpdateWrapper<WmsOutOrders> set1 = new LambdaUpdateWrapper<WmsOutOrders>()
                .eq(WmsOutOrders::getWaveId, waveId)
                .eq(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_ALLOCATED.getCode())
                .set(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_PICKING.getCode());
        boolean update2 = wmsOutOrdersService.update(set1);
        if (!update2) {
            throw new JeecgBootException("更新出库单状态失败");
        }

        //根据波次id查询下的出库单 由已分配变为拣货中
        List<WmsOutOrders> wmsOutOrders = wmsOutOrdersService.selectByWaveId(waveId);
        //抽取出库单id
        List<String> orderIds = wmsOutOrders.stream().map(WmsOutOrders::getId).collect(Collectors.toList());
        //更新出库单明细状态为拣货中
        LambdaUpdateWrapper<WmsOutOrdersItems> set2 = new LambdaUpdateWrapper<WmsOutOrdersItems>()
                .in(WmsOutOrdersItems::getOrderId, orderIds)
                .eq(WmsOutOrdersItems::getStatus, WarehouseDictEnum.OUTBOUND_DETAIL_ALLOCATED.getCode())
                .set(WmsOutOrdersItems::getStatus, WarehouseDictEnum.OUTBOUND_DETAIL_PICKING.getCode());
        boolean update3 = wmsOutOrdersItemsService.update(set2);
        if (!update3) {
            throw new JeecgBootException("更新出库单明细状态失败");
        }


    }
    /**
     * 完成拣货
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePickTask(List<String> waveIds){
        for (String waveId : waveIds) {
            completePickTask(waveId);
        }
    }
    /**
     * 完成拣货
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePickTask(String waveId){
        //查询该波次下的拣货任务列表
        LambdaQueryWrapper<WmsTasks> eq = new LambdaQueryWrapper<WmsTasks>()
                .eq(WmsTasks::getWaveOrderId, waveId);
        List<WmsTasks> wmsTasks = wmsTasksService.list(eq);

        //找没有完成的拣货任务
        List<WmsTasks> wmsTasks1 = wmsTasks.stream().filter(wmsTask -> !WarehouseDictEnum.TASK_STATUS_COMPLETED.getCode().equals(wmsTask.getTaskStatus())).collect(Collectors.toList());
        //遍历未完成任务列表，判断是否进行缺货登记
        for (WmsTasks wmsTask : wmsTasks1) {
            if (wmsTask.getCompletedQuantity() < wmsTask.getQuantity()) {
                //剩余拣货数量
                int remainingQuantity = wmsTask.getQuantity() - wmsTask.getCompletedQuantity();
                //查询缺货登记
                WmsShortageRegistration shortageRegistration = wmsShortageRegistrationService.getById(wmsTask.getId());
                if (shortageRegistration == null || shortageRegistration.getShortageQuantity()==null) {
                    throw new JeecgBootException("任务"+wmsTask.getTaskNumber()+"未进行缺货登记");
                }
                //如果缺货登记数量不等于剩余拣货数量
                if (shortageRegistration.getShortageQuantity() != remainingQuantity) {
                    throw new JeecgBootException("任务"+wmsTask.getTaskNumber()+"的缺货登记数量与剩余拣货数量不一致");
                }
                //将任务状态更新为已拣货
                wmsTask.setTaskStatus(WarehouseDictEnum.TASK_STATUS_COMPLETED.getCode());
                boolean update = wmsTasksService.updateById(wmsTask);
                if(! update){
                    throw new JeecgBootException("更新任务状态失败");
                }
            }
        }
        //只要存在一个未拣货完成则抛出异常
        boolean b = wmsTasks.stream().anyMatch(wmsTask -> !WarehouseDictEnum.TASK_STATUS_COMPLETED.getCode().equals(wmsTask.getTaskStatus()));
        if (b) {
            throw new RuntimeException("波次下存在未完成拣货任务");
        }
        //更新波次下拣货明细表的状态为已拣货
        wmsWaveSkuSummaryService.updatePickedStatus(waveId);
        //更新波次主表的拣货状态，如果波次下拣货明细的状态为已拣货，则更新波次主表的拣货状态为拣货完成
        wmsWaveMasterService.updatePickStatus(waveId);
    }

    /**
     * 拣货校验：已拣货数量+缺货数量不能大于计划数量
     * @param pickTaskId 拣货任务id
     */
    @Override
    public void checkPickedQuantity(String pickTaskId) {
        //根据任务id查询任务
        WmsTasks wmsTasks = wmsTasksService.getById(pickTaskId);
        //根据任务id查询任务记录表
        List<WmsTasksRecords> wmsTasksRecords1 = wmsTasksRecordsService.getBaseMapper().selectList(new LambdaQueryWrapper<WmsTasksRecords>().eq(WmsTasksRecords::getTaskId, pickTaskId));
        //获取已拣货总数量
        Integer pickedQuantity = wmsTasksRecords1.stream().mapToInt(WmsTasksRecords::getExecQuantity).sum();
        //根据任务id查询缺货记录
        List<WmsShortageRegistration> wmsShortageRegistrations = wmsShortageRegistrationService.getBaseMapper().selectList(new LambdaQueryWrapper<WmsShortageRegistration>().eq(WmsShortageRegistration::getTaskId, pickTaskId));
        //获取缺货总数量
        Integer shortageQuantity = wmsShortageRegistrations.stream().mapToInt(WmsShortageRegistration::getShortageQuantity).sum();
        if (pickedQuantity + shortageQuantity > wmsTasks.getQuantity()) {
            throw new JeecgBootException("已拣货数量+缺货数量不能大于计划拣货数量");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pick(WmsTasksRecords wmsTasksRecords) {
        //执行任务
        WmsTasks wmsTasks = wmsTasksService.execute(wmsTasksRecords);
        //校验缺货数量+拣货数量不能大于计划数量
        checkPickedQuantity(wmsTasksRecords.getTaskId());
        //执行数量
        Integer execQuantity = wmsTasksRecords.getExecQuantity();

        //计划数量
        Integer quantity = wmsTasks.getQuantity();

        //波次拣货明细id
        String waveSkuSummaryId = wmsTasks.getWaveSkuSummaryId();
        //根据波次拣货明细id更新波次拣货数量
        updatePickedQuantityByWaveSkuSummaryId(waveSkuSummaryId, quantity,execQuantity);
        //更新波次主表的拣货状态，如果波次下拣货明细的状态为已拣货，则更新波次主表的拣货状态为拣货完成
        wmsWaveMasterService.updatePickStatus(wmsTasks.getWaveOrderId());
        //向库存表中添加库存记录
        WmsInventoryTransParam inventoryTransParam = new WmsInventoryTransParam();
        inventoryTransParam.setProductId(wmsTasksRecords.getProductId());
        inventoryTransParam.setExecQuantity(wmsTasksRecords.getExecQuantity());
        inventoryTransParam.setSourceLocationCode(wmsTasksRecords.getSourceLocationCode());
        inventoryTransParam.setTargetLocationCode(wmsTasksRecords.getTargetLocationCode());
        inventoryTransParam.setWarehouseId(wmsTasksRecords.getTargetWarehouseId());
        inventoryTransParam.setBatchNumber(wmsTasksRecords.getBatchNumber());
        inventoryTransParam.setExpiryDate(wmsTasksRecords.getExpiryDate());
        inventoryTransParam.setTransactionType(WarehouseDictEnum.INVENTORY_PICKING.getCode());
        //上架时间
        inventoryTransParam.setOperationTime(wmsTasksRecords.getOperationTime());
        inventoryTransByPick.transfer(inventoryTransParam);
    }
    /**
     * 根据波次拣货明细id更新拣货数量
     * @param waveSkuSummaryId 波次拣货明细id
     * @param quantity 计划拣货数量
     * @param pickedQuantity 拣货数量
     */
    public void updatePickedQuantityByWaveSkuSummaryId(String waveSkuSummaryId, Integer quantity,Integer pickedQuantity) {
        //更新波次拣货明细的拣货数量
        //sql="update wms_wave_sku_summary set picked_quantity = picked_quantity+"+pickedQuantity+" where id = '"+waveSkuSummaryId+"' and picked_quantity <="+quantity-pickedQuantity;
        LambdaUpdateWrapper<WmsWaveSkuSummary> waveSkuSummaryLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        waveSkuSummaryLambdaUpdateWrapper.setSql("picked_quantity = picked_quantity+"+pickedQuantity)
                .eq(WmsWaveSkuSummary::getId,waveSkuSummaryId)
                .le(WmsWaveSkuSummary::getPickedQuantity,quantity-pickedQuantity);
        int update2 = wmsWaveSkuSummaryService.getBaseMapper().update(null, waveSkuSummaryLambdaUpdateWrapper);
        if(update2 <= 0){
            throw new JeecgBootException("波次拣货明细的拣货数量不能大于分配数量!");
        }
        //查询波次拣货明细
        WmsWaveSkuSummary wmsWaveSkuSummary = wmsWaveSkuSummaryService.getBaseMapper().selectById(waveSkuSummaryId);
        //如果拣货数量等于计划拣货数量，则更新波次拣货明细的拣货状态为已拣货
        if (wmsWaveSkuSummary.getPickedQuantity().equals(quantity)) {
            wmsWaveSkuSummary.setStatus(WarehouseDictEnum.WAVESKU_PICKED.getCode());
            wmsWaveSkuSummaryService.updateById(wmsWaveSkuSummary);
        }
    }

    /**
     * 完成分拣
     * @param wmsOutOrdersItemsList 出库单明细
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void completeSorting(List<WmsOutOrdersItems> wmsOutOrdersItemsList) {
        //转成数组
//        String[] outOrderItemIdArray = outOrderItemIds.split(",");
        //出库单idList
        List<String> outOrderIds = new ArrayList<>();
        //波次id List
        List<String> waveIds = new ArrayList<>();
        //遍历出库单明细id
        for (WmsOutOrdersItems items : wmsOutOrdersItemsList) {
            //根据出库单明细id查询出库单明细
            WmsOutOrdersItems wmsOutOrdersItems = wmsOutOrdersItemsService.getById(items.getId());
            if (wmsOutOrdersItems == null) {
                throw new RuntimeException("未找到对应的出库单明细,id:"+items.getId());
            }
            //商品id
            String skuId = wmsOutOrdersItems.getSkuId();
            //拣货数量,页面传入
            Integer pickedQuantity = items.getPickedQuantity();
            //商品信息
            WmsProducts wmsProducts = wmsProductsService.getById(skuId);
            //如果状态为分拣完成状态，则不允许分拣
            if (WarehouseDictEnum.OUTBOUND_DETAIL_PICKED.getCode().equals(wmsOutOrdersItems.getStatus())) {
                throw new RuntimeException("出库单商品"+wmsProducts.getProductName()+"为拣货完成状态,不允许分拣");
            }
            //根据出库单id查询出库单
            WmsOutOrders wmsOutOrders = wmsOutOrdersService.getById(wmsOutOrdersItems.getOrderId());
            if (wmsOutOrders == null) {
                throw new RuntimeException("未找到对应的出库单,id:"+wmsOutOrdersItems.getOrderId());
            }
            outOrderIds.add(wmsOutOrders.getId());
            //波次id
            String waveId = wmsOutOrders.getWaveId();
            waveIds.add(waveId);
            //向波次表增加分拣数量
            wmsWaveMasterService.addSortingQuantity(waveId, pickedQuantity);
            //更新出库单明细的拣货数量及状态更新为分拣完成
            wmsOutOrdersItemsService.updatePickedQuantityAndStatus(items.getId(), pickedQuantity);
        }
        //更新出库单状态为分拣完成
        wmsOutOrdersService.updatePickStatus(outOrderIds);
        //更新该波次完成分拣订单的数量
        wmsWaveMasterService.updateCompleteSortingOrderQuantity(waveIds);

    }

    @Override
    public String viewPickPath(String waveId) {

        WmsWaveMaster wmsWaveMaster = wmsWaveMasterService.getById(waveId);
        if (wmsWaveMaster == null) {
            throw new RuntimeException("未找到对应的波次,id:"+waveId);
        }
        //如果状态是已创建则不允许查询拣货路径
        if (WarehouseDictEnum.WAVE_CREATED.getCode().equals(wmsWaveMaster.getStatus())) {
            throw new RuntimeException("波次"+wmsWaveMaster.getWaveNo()+"未完成拣货,不允许查询拣货路径");
        }
        //如果存在拣货路径图片，则直接返回
        if (StringUtils.isNotBlank(wmsWaveMaster.getPickPathImg())) {
            return wmsWaveMaster.getPickPathImg();
        }
        //根据波次id查询波次拣货明细记录
        List<WmsWaveSkuSummary> wmsWaveSkuSummaries = wmsWaveSkuSummaryService.selectByWaveId(waveId);

        //调用A星算法工具生成拣货路径
        // 起始位置
        Node startNode = new Node(0, 0);

        List<Product> products = wmsWaveSkuSummaries.stream().map(wmsWaveSkuSummary -> {
            //商品id
            String skuId = wmsWaveSkuSummary.getSkuId();

            //商品信息
            WmsProducts wmsProducts = wmsProductsService.getById(skuId);
            //储位编码
            String locationCode = wmsWaveSkuSummary.getLocationCode();
            //根据储位编码查询储位信息
            WmsStorageLocations storageLocationsByLocationCode = wmsStorageLocationsService.getStorageLocationsByLocationCode(locationCode);
            //从储位对象拿储区的id
            String zoneId = storageLocationsByLocationCode.getZoneId();
            //查询储区信息
            WmsStorageZones storageZones = wmsStorageZonesService.getById(zoneId);
            Location location = new Location(storageZones.getZoneName(), Integer.parseInt(storageLocationsByLocationCode.getLocationLine()), Integer.parseInt(storageLocationsByLocationCode.getLocationRank()), storageLocationsByLocationCode.getLocationCode());
            Product product = new Product(wmsProducts.getId(), wmsProducts.getProductName(), location);
            return product;


        }).collect(Collectors.toList());

        // 多个商品 - 测试相邻行和非相邻行的情况
//        List<Product> products = Arrays.asList(
//                new Product("P003", "商品3", new Location("A", 1, 4,"Z1A-1-4")),
//                new Product("P004", "商品4", new Location("A", 3, 4,"Z1A-3-4")),
//                new Product("P005", "商品5", new Location("A", 3, 5,"Z1A-3-5")),
//                new Product("P006", "商品6", new Location("C", 4, 5,"Z1C-4-5")) // 与前两个商品不相邻
//        );

        // 计算带商品信息的最优路径
        MultiProductPickingRouteOptimized.RouteWithProducts routeWithProducts = MultiProductPickingRouteOptimized.calculateOptimalPickingRouteWithProducts(startNode, products);


        // 生成图形数据
        System.out.println("\nSVG图形数据:");
        String svgData = MultiProductPickingRouteOptimized.generateSVGData(routeWithProducts, startNode, products);

        //将图形代码保存数据库
        WmsWaveMaster wmsWaveMaster1 = new WmsWaveMaster();
        wmsWaveMaster1.setId(waveId);
        wmsWaveMaster1.setPickPathImg(svgData);
        wmsWaveMasterService.updateById(wmsWaveMaster1);
        return svgData;


    }


}
