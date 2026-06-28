package com.retry.platform.agentscope;


import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * TODO: Description of the class
 *
 * @author Wang Chao
 * @date 2026/3/24
 */
public class Test {

    public static void main(String[] args) throws UnsupportedEncodingException {
        // 修复控制台输出乱码：设置系统输出流编码为 UTF-8
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");
        // 创建智能体并内联配置模型

        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("Qwen3-Next-80B-A3B-Instruct")
                .baseUrl("https://openai.jinkosolar.com/v1")
                .build();

        ReActAgent agent = ReActAgent.builder()
                .name("Assistant")
                .sysPrompt("你是一个有帮助的 AI 助手。")
//                .model(DashScopeChatModel.builder()
//                        // 优先使用环境变量，如果没有配置则使用配置文件中的值
//                        .apiKey(getApiKey())
//                        .modelName("qwen3-max-2026-01-23")
//                        .build())
                .model(model)
                .build();

        Msg response = agent.call(Msg.builder()
                .textContent("你好！")
                .build()).block();
        
        // 直接输出即可，前面已经设置了编码
        String content = response.getTextContent();
        System.out.println(content);
    }
    
    /**
     * 获取 API Key
     * 优先级：环境变量 > 配置文件 > 硬编码
     */
    private static String getApiKey() {
        // 1. 优先从环境变量获取
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            System.out.println("使用环境变量中的 API Key");
            return apiKey;
        }
        
        // 2. 从系统属性获取（可以通过 -D 参数设置）
        apiKey = System.getProperty("dashscope.api-key");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            System.out.println("使用系统属性中的 API Key");
            return apiKey;
        }
        
        // 3. 使用默认值（生产环境不应该这样）
        System.out.println("警告：使用硬编码的 API Key，建议配置环境变量或配置文件");
        return "YOUR_DASHSCOPE_API_KEY";
    }

}
