package org.jeecg.modules.wms.wave;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.wms.wave.entity.WmsWaveMaster;
import org.jeecg.modules.wms.wave.mapper.WmsWaveMasterMapper;
import org.jeecg.modules.wms.wave.service.IWmsWaveMasterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 波次管理 - 待发货(PACKED)数据查询测试
 *
 * 测试目的：验证后端是否能正确查询到 status='PACKED' 的波次数据
 * 如果测试通过（能查到数据），说明后端接口正常，问题在前端
 * 如果测试失败（查不到数据），需要排查后端问题
 */
@Slf4j
@SpringBootTest(classes = org.jeecg.modules.wms.WmsTestApplication.class)
public class WmsWaveMasterQueryTest {

    @Autowired
    private IWmsWaveMasterService wmsWaveMasterService;

    @Autowired
    private WmsWaveMasterMapper wmsWaveMasterMapper;

    /**
     * 测试1：通过 Service 层查询 status='PACKED' 的数据
     */
    @Test
    public void testQueryPackedByService() {
        log.info("========== 测试1：Service层查询 status='PACKED' ==========");

        WmsWaveMaster queryParam = new WmsWaveMaster();
        queryParam.setStatus("PACKED");

        IPage<WmsWaveMaster> result = wmsWaveMasterService.queryList(queryParam, 1, 10);

        log.info("查询结果总条数：{}", result.getTotal());
        log.info("当前页条数：{}", result.getRecords().size());

        if (result.getRecords().isEmpty()) {
            log.error("❌ Service层查询失败：未找到 status='PACKED' 的波次数据");
        } else {
            log.info("✅ Service层查询成功：找到 {} 条 status='PACKED' 的波次数据", result.getTotal());
            for (WmsWaveMaster wave : result.getRecords()) {
                log.info("   波次编号：{}，状态：{}，仓库ID：{}", wave.getWaveNo(), wave.getStatus(), wave.getWarehouseId());
            }
        }
    }

    /**
     * 测试2：通过 Mapper 层直接查询 status='PACKED' 的数据
     */
    @Test
    public void testQueryPackedByMapper() {
        log.info("========== 测试2：Mapper层查询 status='PACKED' ==========");

        WmsWaveMaster queryParam = new WmsWaveMaster();
        queryParam.setStatus("PACKED");

        List<WmsWaveMaster> list = wmsWaveMasterMapper.queryList(queryParam);

        log.info("查询结果条数：{}", list.size());

        if (list.isEmpty()) {
            log.error("❌ Mapper层查询失败：未找到 status='PACKED' 的波次数据");
        } else {
            log.info("✅ Mapper层查询成功：找到 {} 条 status='PACKED' 的波次数据", list.size());
            for (WmsWaveMaster wave : list) {
                log.info("   波次编号：{}，状态：{}，仓库ID：{}", wave.getWaveNo(), wave.getStatus(), wave.getWarehouseId());
            }
        }
    }

    /**
     * 测试3：查询所有波次数据，查看状态分布
     */
    @Test
    public void testQueryAllWaveStatusDistribution() {
        log.info("========== 测试3：查询所有波次状态分布 ==========");

        WmsWaveMaster queryParam = new WmsWaveMaster();
        List<WmsWaveMaster> allWaves = wmsWaveMasterMapper.queryList(queryParam);

        log.info("波次总数量：{}", allWaves.size());

        long createdCount = allWaves.stream().filter(w -> "CREATED".equals(w.getStatus())).count();
        long pickingCount = allWaves.stream().filter(w -> "PICKING".equals(w.getStatus())).count();
        long pickedCount = allWaves.stream().filter(w -> "PICKED".equals(w.getStatus())).count();
        long packedCount = allWaves.stream().filter(w -> "PACKED".equals(w.getStatus())).count();
        long shippedCount = allWaves.stream().filter(w -> "SHIPPED".equals(w.getStatus())).count();
        long completedCount = allWaves.stream().filter(w -> "COMPLETED".equals(w.getStatus())).count();

        log.info("状态分布：");
        log.info("   CREATED（已创建）：{} 条", createdCount);
        log.info("   PICKING（拣货中）：{} 条", pickingCount);
        log.info("   PICKED（拣货完成）：{} 条", pickedCount);
        log.info("   PACKED（打包完成/待发货）：{} 条", packedCount);
        log.info("   SHIPPED（已发货）：{} 条", shippedCount);
        log.info("   COMPLETED（已完成）：{} 条", completedCount);

        if (packedCount > 0) {
            log.info("✅ 确认：数据库中存在 {} 条 status='PACKED' 的波次数据", packedCount);
        } else {
            log.error("❌ 警告：数据库中没有 status='PACKED' 的波次数据");
        }
    }
}
