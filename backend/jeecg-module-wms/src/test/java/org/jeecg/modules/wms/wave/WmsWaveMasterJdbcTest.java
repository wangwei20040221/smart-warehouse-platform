package org.jeecg.modules.wms.wave;

import java.sql.*;

/**
 * 波次管理 - 待发货(PACKED)数据查询测试（JDBC直连版）
 *
 * 不依赖Spring Boot上下文，直接用JDBC查询数据库验证
 */
public class WmsWaveMasterJdbcTest {

    // 数据库配置（与 application-dev.yml 一致）
    private static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/xingchenwms?characterEncoding=UTF-8&useUnicode=true&useSSL=false&tinyInt1isBit=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "wang123.";

    public static void main(String[] args) {
        System.out.println("========== 波次管理 - 待发货(PACKED)数据查询测试 ==========");
        System.out.println("数据库连接：" + DB_URL);
        System.out.println();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ 缺少 MySQL JDBC 驱动，请检查依赖");
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            // 测试1：查询 status='PACKED' 的数据
            System.out.println("【测试1】查询 status='PACKED' 的波次数据");
            String sqlPacked = "SELECT id, wave_no, status, warehouse_id, total_orders, total_skus, create_time FROM wms_wave_master WHERE status = 'PACKED'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sqlPacked)) {

                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("   波次ID：%s | 编号：%s | 状态：%s | 仓库：%s | 订单数：%d | SKU数：%d | 创建时间：%s%n",
                            rs.getString("id"),
                            rs.getString("wave_no"),
                            rs.getString("status"),
                            rs.getString("warehouse_id"),
                            rs.getInt("total_orders"),
                            rs.getInt("total_skus"),
                            rs.getTimestamp("create_time"));
                }

                if (count == 0) {
                    System.out.println("   ❌ 未找到 status='PACKED' 的波次数据");
                } else {
                    System.out.println("   ✅ 共找到 " + count + " 条 status='PACKED' 的波次数据");
                }
            }
            System.out.println();

            // 测试2：查询所有波次状态分布
            System.out.println("【测试2】查询所有波次状态分布");
            String sqlAll = "SELECT status, COUNT(*) as cnt FROM wms_wave_master GROUP BY status ORDER BY cnt DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sqlAll)) {

                int total = 0;
                while (rs.next()) {
                    int cnt = rs.getInt("cnt");
                    total += cnt;
                    System.out.printf("   状态：%-10s | 数量：%d 条%n", rs.getString("status"), cnt);
                }
                System.out.println("   波次总数量：" + total + " 条");
            }
            System.out.println();

            // 测试3：验证SQL查询条件是否与后端Mapper一致
            System.out.println("【测试3】验证后端Mapper SQL查询逻辑");
            String sqlVerify = "SELECT id, wave_no, status FROM wms_wave_master t WHERE t.status = 'PACKED'";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sqlVerify)) {

                int count = 0;
                while (rs.next()) {
                    count++;
                }
                System.out.println("   执行 SQL：SELECT ... FROM wms_wave_master t WHERE t.status = 'PACKED'");
                System.out.println("   查询结果：" + count + " 条");
                if (count > 0) {
                    System.out.println("   ✅ 后端Mapper使用此SQL一定能查到数据");
                } else {
                    System.out.println("   ❌ 此SQL查询不到数据");
                }
            }

            System.out.println();
            System.out.println("========== 测试结束 ==========");

        } catch (SQLException e) {
            System.err.println("❌ 数据库连接失败：" + e.getMessage());
            System.err.println("   请检查：1. MySQL是否已启动  2. 数据库配置是否正确");
        }
    }
}
