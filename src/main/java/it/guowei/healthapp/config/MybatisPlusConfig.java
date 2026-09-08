package it.guowei.healthapp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置
 * 分页插件（PaginationInnerInterceptor）必须显式注册，否则 selectPage 不生效：
 * 不会追加 LIMIT，也不会回填 total —— 管理端所有分页查询依赖此插件。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L); // 单页上限，防止恶意大分页拖垮数据库
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
