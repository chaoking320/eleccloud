package com.retry.platform.server.template;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 场景模板服务 - 提供开箱即用的配置模板
 * 
 * <p>降低用户配置难度，提供常见场景的推荐配置。
 * 
 * @since 1.1.0
 */
@Service
public class SceneTemplateService {
    
    private final List<SceneTemplate> templates;
    
    public SceneTemplateService() {
        this.templates = initializeTemplates();
    }
    
    /**
     * 获取所有模板
     */
    public List<SceneTemplate> getAllTemplates() {
        return new ArrayList<>(templates);
    }
    
    /**
     * 根据模板ID获取模板
     */
    public SceneTemplate getTemplate(String templateId) {
        return templates.stream()
                .filter(t -> t.getTemplateId().equals(templateId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 根据分类获取模板
     */
    public List<SceneTemplate> getTemplatesByCategory(String category) {
        return templates.stream()
                .filter(t -> t.getDescription().contains(category))
                .collect(Collectors.toList());
    }
    
    /**
     * 初始化内置模板
     */
    private List<SceneTemplate> initializeTemplates() {
        List<SceneTemplate> list = new ArrayList<>();
        
        // 模板1: 支付退款场景
        list.add(new SceneTemplate(
                "payment-refund",
                "支付退款场景",
                "适用于第三方支付接口调用（支付宝、微信、银联等）",
                "支付退款、结算、提现等需要查询第三方状态的场景",
                "CUSTOM",
                null,
                "1,5,10,30,60",
                5,
                3600,
                false,
                "【退避间隔】1分钟、5分钟、10分钟、30分钟、60分钟\n" +
                "【最大重试】5次\n" +
                "【超时时间】1小时\n" +
                "【Hook要求】需要实现checkStatus和doQuery，查询第三方状态",
                "@RetryableTask(sceneType = 1001, idempotentKey = \"#transId\")\n" +
                "public void refund(String transId, Double amount) {\n" +
                "    alipayApi.refund(transId, amount);\n" +
                "}"
        ));
        
        // 模板2: 库存同步场景
        list.add(new SceneTemplate(
                "inventory-sync",
                "库存同步场景",
                "适用于定时同步、批量操作等场景",
                "库存扣减、库存同步、数据同步等无需查询第三方状态的场景",
                "FIXED",
                60,
                null,
                10,
                0,
                true,
                "【退避间隔】固定60秒\n" +
                "【最大重试】10次\n" +
                "【超时时间】不限\n" +
                "【Hook要求】✅ 可使用默认Hook（零配置）",
                "@RetryableTask(sceneType = 1002, idempotentKey = \"#skuId\")\n" +
                "public void syncStock(String skuId, Integer quantity) {\n" +
                "    warehouseApi.updateStock(skuId, quantity);\n" +
                "}"
        ));
        
        // 模板3: 消息推送场景
        list.add(new SceneTemplate(
                "message-push",
                "消息推送场景",
                "适用于短信、邮件、App推送等消息发送场景",
                "短信发送、邮件发送、App推送、站内信等",
                "EXPONENTIAL",
                30,
                null,
                8,
                7200,
                true,
                "【退避间隔】指数退避，30秒基数（30s, 1min, 2min, 4min, 8min...）\n" +
                "【最大重试】8次\n" +
                "【超时时间】2小时\n" +
                "【Hook要求】✅ 可使用默认Hook（零配置）",
                "@RetryableTask(sceneType = 1003, idempotentKey = \"#mobile + '-' + #templateCode\")\n" +
                "public void sendSms(String mobile, String templateCode, Map<String, String> params) {\n" +
                "    smsApi.send(mobile, templateCode, params);\n" +
                "}"
        ));
        
        // 模板4: 订单创建场景
        list.add(new SceneTemplate(
                "order-create",
                "订单创建场景",
                "适用于需要查询下游订单号的场景",
                "订单创建、结算单创建、工单创建等需要查询下游生成的唯一标识",
                "CUSTOM",
                null,
                "1,3,5,10,20",
                5,
                1800,
                false,
                "【退避间隔】1分钟、3分钟、5分钟、10分钟、20分钟\n" +
                "【最大重试】5次\n" +
                "【超时时间】30分钟\n" +
                "【Hook要求】需要实现doQuery，查询下游订单状态和订单号",
                "@RetryableTask(sceneType = 1004, idempotentKey = \"#requestId\")\n" +
                "public void createOrder(String requestId, OrderDTO order) {\n" +
                "    partnerApi.createOrder(requestId, order);\n" +
                "}"
        ));
        
        // 模板5: 数据归档场景
        list.add(new SceneTemplate(
                "data-archive",
                "数据归档场景",
                "适用于低频大批量数据处理",
                "数据归档、日志清理、报表生成等批量后台任务",
                "LINEAR",
                300,
                null,
                20,
                0,
                true,
                "【退避间隔】线性递增，5分钟基数（5min, 10min, 15min, 20min...）\n" +
                "【最大重试】20次\n" +
                "【超时时间】不限\n" +
                "【Hook要求】✅ 可使用默认Hook（零配置）",
                "@RetryableTask(sceneType = 1005, idempotentKey = \"#batchId\")\n" +
                "public void archiveData(String batchId, String tableName, LocalDate date) {\n" +
                "    archiveService.archive(tableName, date);\n" +
                "}"
        ));
        
        // 模板6: 文件上传场景
        list.add(new SceneTemplate(
                "file-upload",
                "文件上传场景",
                "适用于文件/对象存储上传",
                "文件上传OSS、图片上传CDN、大文件分片上传等",
                "CUSTOM",
                null,
                "2,5,10,20,40",
                5,
                3600,
                true,
                "【退避间隔】2分钟、5分钟、10分钟、20分钟、40分钟\n" +
                "【最大重试】5次\n" +
                "【超时时间】1小时\n" +
                "【Hook要求】✅ 可使用默认Hook（零配置）",
                "@RetryableTask(sceneType = 1006, idempotentKey = \"#fileId\")\n" +
                "public void uploadFile(String fileId, byte[] content, String bucketName) {\n" +
                "    ossClient.putObject(bucketName, fileId, content);\n" +
                "}"
        ));
        
        // 模板7: 异步回调场景
        list.add(new SceneTemplate(
                "async-callback",
                "异步回调场景",
                "适用于等待第三方回调通知的场景",
                "支付回调、物流回调、认证回调等需要等待异步通知",
                "CUSTOM",
                null,
                "5,10,30,60,120",
                5,
                7200,
                false,
                "【退避间隔】5分钟、10分钟、30分钟、1小时、2小时\n" +
                "【最大重试】5次\n" +
                "【超时时间】2小时\n" +
                "【Hook要求】需要实现checkStatus和doQuery，检查回调是否已到达",
                "@RetryableTask(sceneType = 1007, idempotentKey = \"#callbackId\", preSubmit = true)\n" +
                "public void waitCallback(String callbackId, String bizId) {\n" +
                "    // 方法可以立即返回，重试时通过Hook检查回调是否已到达\n" +
                "    log.info(\"Waiting callback: {}\", callbackId);\n" +
                "}"
        ));
        
        // 模板8: API限流重试场景
        list.add(new SceneTemplate(
                "api-rate-limit",
                "API限流重试场景",
                "适用于有QPS限制的第三方API调用",
                "第三方API调用遇到429限流错误时的重试",
                "EXPONENTIAL",
                60,
                null,
                10,
                3600,
                true,
                "【退避间隔】指数退避，60秒基数（1min, 2min, 4min, 8min...）\n" +
                "【最大重试】10次\n" +
                "【超时时间】1小时\n" +
                "【Hook要求】✅ 可使用默认Hook（零配置）\n" +
                "【特殊说明】指数退避适合应对限流，逐步降低请求频率",
                "@RetryableTask(sceneType = 1008, idempotentKey = \"#requestId\")\n" +
                "public void callRateLimitedApi(String requestId, ApiRequest request) {\n" +
                "    // 如果遇到429限流，会指数退避重试\n" +
                "    apiClient.call(request);\n" +
                "}"
        ));
        
        return list;
    }
    
    /**
     * 获取模板统计信息
     */
    public Map<String, Object> getStatistics() {
        long totalCount = templates.size();
        long zeroHookCount = templates.stream().filter(SceneTemplate::getUseDefaultHook).count();
        long customHookCount = totalCount - zeroHookCount;
        
        return Map.of(
                "totalTemplates", totalCount,
                "zeroHookTemplates", zeroHookCount,
                "customHookTemplates", customHookCount,
                "templateCategories", List.of("支付类", "同步类", "消息类", "文件类", "回调类")
        );
    }
}
