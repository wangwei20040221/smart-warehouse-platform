package org.jeecg.modules.wms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * WMS模块测试专用启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.jeecg.modules.wms"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "org\\.jeecg\\.modules\\.wms\\.ai\\.chat\\.controller\\..*"
    ))
@MapperScan(value = {"org.jeecg.modules.wms.**.mapper*"})
public class WmsTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(WmsTestApplication.class, args);
    }
}
